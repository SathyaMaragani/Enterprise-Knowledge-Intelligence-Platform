package com.eip.backend.service;

import com.eip.backend.entity.mongodb.KnowledgeDocument;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Finds the query in each document's extracted text ({@code content.raw_text} in
 * MongoDB), so keyword search also matches words that appear only in the body.
 *
 * <p>The matching runs inside MongoDB, which returns a few flags per matching
 * document and never the text itself: a search does not pull every body across
 * the network. Matching follows {@link LexicalScorer}: the phrase is a
 * case-insensitive substring, each term a whole word.
 */
@Component
public class BodyTextMatcher {

    // ponytail: an unindexed regex scan of every body on each keyword search.
    // Milliseconds for the demo and live corpora (hundreds of documents); past
    // that, take candidates from the content.raw_text text index or Atlas Search.
    private static final int LIMIT = 5000;

    private final MongoTemplate mongoTemplate;

    public BodyTextMatcher(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * @param phrase the query, {@link LexicalScorer#normalise normalised}
     * @param terms  its terms, as {@link LexicalScorer#terms} gives them
     * @return evidence for each document whose body holds at least one term, by PostgreSQL id
     */
    public Map<Integer, LexicalScorer.BodyEvidence> match(String phrase, List<String> terms) {
        if (terms.isEmpty()) {
            return Map.of();
        }
        List<String> words = terms.stream().map(BodyTextMatcher::escape).toList();
        List<Document> termTests = new ArrayList<>();
        for (String word : words) {
            termTests.add(regexMatch("\\b" + word + "\\b"));
        }
        String phraseRegex = Arrays.stream(phrase.split(" ")).map(BodyTextMatcher::escape)
                .collect(Collectors.joining("\\s+"));

        List<Document> pipeline = List.of(
                new Document("$match", new Document("content.raw_text",
                        new Document("$regex", "\\b(?:" + String.join("|", words) + ")\\b").append("$options", "i"))),
                new Document("$limit", LIMIT),
                new Document("$project", new Document("_id", 0)
                        .append("id", "$postgres_document_id")
                        .append("phrase", regexMatch(phraseRegex))
                        .append("terms", termTests)));

        Map<Integer, LexicalScorer.BodyEvidence> found = new HashMap<>();
        String collection = mongoTemplate.getCollectionName(KnowledgeDocument.class);
        for (Document row : mongoTemplate.getCollection(collection).aggregate(pipeline)) {
            if (!(row.get("id") instanceof Number id)) {
                continue;
            }
            List<?> flags = row.getList("terms", Object.class);
            boolean[] present = new boolean[terms.size()];
            for (int i = 0; i < present.length && i < flags.size(); i++) {
                present[i] = Boolean.TRUE.equals(flags.get(i));
            }
            found.put(id.intValue(), new LexicalScorer.BodyEvidence(Boolean.TRUE.equals(row.get("phrase")), present));
        }
        return found;
    }

    private static Document regexMatch(String regex) {
        return new Document("$regexMatch", new Document("input", "$content.raw_text")
                .append("regex", regex)
                .append("options", "i"));
    }

    /** Escapes regular-expression metacharacters, so the query is matched literally. */
    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ("\\^$.|?*+()[]{}".indexOf(c) >= 0) {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }
}
