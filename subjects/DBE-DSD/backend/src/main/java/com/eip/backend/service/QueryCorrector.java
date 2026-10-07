package com.eip.backend.service;

import com.eip.backend.entity.mongodb.KnowledgeDocument;
import com.eip.backend.repository.DocumentRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import texthack.dp.DamerauLevenshtein;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Corrects misspelled query words against the words the documents actually
 * contain, so a typo still finds a word that appears only in a document's text
 * ("dynmo" finds "dynamo"), and the semantic leg embeds what the user meant.
 *
 * <p>A word is corrected only when no document contains it and a document word
 * lies within the edit budget {@link LexicalScorer} uses for typos: one edit for
 * 4-7 letters, two for 8 or more, measured with Damerau-Levenshtein (OSA). The
 * nearest word wins, then the one found in more documents.
 */
@Component
public class QueryCorrector {

    /** The corrected query, and the credit a typo-level match earns. */
    public record Correction(String query, double credit) {
    }

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    // ponytail: the vocabulary is rebuilt from every title, description and body
    // at most every five minutes, so a new upload's words become correctable
    // within that time (exact matches work at once). Fine for hundreds of
    // documents; past that, maintain the vocabulary on upload and delete.
    private static final long REFRESH_MILLIS = 5 * 60_000;

    private final MongoTemplate mongoTemplate;
    private final DocumentRepository documentRepository;
    private Map<String, Integer> vocabulary = Map.of();
    private long builtAt;

    public QueryCorrector(MongoTemplate mongoTemplate, DocumentRepository documentRepository) {
        this.mongoTemplate = mongoTemplate;
        this.documentRepository = documentRepository;
    }

    /** @return the corrected query, or null when every word is already in the documents */
    public Correction correct(String query) {
        return correct(LexicalScorer.normalise(query), vocabulary());
    }

    /** Corrects {@code phrase} (already normalised) against {@code vocabulary}: word to document count. */
    static Correction correct(String phrase, Map<String, Integer> vocabulary) {
        Set<String> terms = new HashSet<>(LexicalScorer.terms(phrase)); // no stopwords
        Matcher m = WORD.matcher(phrase);
        StringBuilder out = new StringBuilder();
        int worst = 0;
        while (m.find()) {
            String word = m.group();
            // Numbers are left alone: "2024" is not a typo for "2025".
            String fix = terms.contains(word) && word.chars().noneMatch(Character::isDigit)
                    ? closest(word, vocabulary) : null;
            if (fix != null) {
                worst = Math.max(worst, DamerauLevenshtein.optimalStringAlignment(word, fix));
            }
            m.appendReplacement(out, Matcher.quoteReplacement(fix != null ? fix : word));
        }
        m.appendTail(out);
        if (worst == 0) {
            return null;
        }
        return new Correction(out.toString(), worst == 1 ? LexicalScorer.ONE_EDIT_CREDIT : LexicalScorer.TWO_EDIT_CREDIT);
    }

    private static String closest(String word, Map<String, Integer> vocabulary) {
        int budget = LexicalScorer.fuzzyThreshold(word.length());
        if (budget == 0 || vocabulary.containsKey(word)) {
            return null;
        }
        String best = null;
        int bestDistance = 0;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : vocabulary.entrySet()) {
            String candidate = entry.getKey();
            int count = entry.getValue();
            if (Math.abs(candidate.length() - word.length()) > budget) {
                continue; // each edit changes the length by at most one
            }
            int distance = DamerauLevenshtein.optimalStringAlignment(word, candidate);
            if (distance > budget) {
                continue;
            }
            // Nearest first, then the word in more documents, then alphabetical so ties are stable.
            boolean better = best == null || distance < bestDistance
                    || distance == bestDistance && (count > bestCount
                        || count == bestCount && candidate.compareTo(best) < 0);
            if (better) {
                best = candidate;
                bestDistance = distance;
                bestCount = count;
            }
        }
        return best;
    }

    private synchronized Map<String, Integer> vocabulary() {
        if (System.currentTimeMillis() - builtAt < REFRESH_MILLIS) {
            return vocabulary;
        }
        Map<Integer, Set<String>> wordsByDocument = new HashMap<>();
        for (com.eip.backend.entity.Document d : documentRepository.findAll()) {
            addWords(wordsByDocument, d.getId(), d.getTitle(), d.getDescription());
        }
        String collection = mongoTemplate.getCollectionName(KnowledgeDocument.class);
        org.bson.Document fields = new org.bson.Document("postgres_document_id", 1).append("content.raw_text", 1);
        for (org.bson.Document d : mongoTemplate.getCollection(collection).find().projection(fields)) {
            Object content = d.get("content");
            Object text = content instanceof org.bson.Document c ? c.get("raw_text") : null;
            if (d.get("postgres_document_id") instanceof Number id && text instanceof String body) {
                addWords(wordsByDocument, id.intValue(), body);
            }
        }
        Map<String, Integer> counts = new HashMap<>();
        for (Set<String> words : wordsByDocument.values()) {
            for (String word : words) {
                counts.merge(word, 1, Integer::sum);
            }
        }
        vocabulary = counts;
        builtAt = System.currentTimeMillis();
        return vocabulary;
    }

    private static void addWords(Map<Integer, Set<String>> wordsByDocument, Integer id, String... texts) {
        Set<String> words = wordsByDocument.computeIfAbsent(id, k -> new HashSet<>());
        for (String text : texts) {
            if (text != null) {
                for (String token : LexicalScorer.tokens(text.toLowerCase(Locale.ROOT))) {
                    if (token.length() >= 4) {
                        words.add(token);
                    }
                }
            }
        }
    }
}
