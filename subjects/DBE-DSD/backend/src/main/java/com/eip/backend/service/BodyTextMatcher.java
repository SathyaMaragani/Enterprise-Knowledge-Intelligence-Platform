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
 * the network. Matching follows {@link LexicalScorer}: case-insensitive, the
 * phrase at word boundaries, each term as a whole word or as the start of one.
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
     * @param phrase      the query, {@link LexicalScorer#normalise normalised}
     * @param terms       its terms, as {@link LexicalScorer#terms} gives them
     * @param allowPrefix whether a term may match a word it only begins ("secur" in "security")
     * @return evidence for each document whose text holds at least one term, by PostgreSQL id
     */
    public Map<Integer, LexicalScorer.BodyEvidence> match(String phrase, List<String> terms, boolean allowPrefix) {
        if (terms.isEmpty()) {
            return Map.of();
        }
        List<String> anyTerm = new ArrayList<>();
        List<Document> wholeWord = new ArrayList<>();
        List<Object> wordStart = new ArrayList<>();
        for (String term : terms) {
            String word = escape(term);
            boolean prefix = allowPrefix && term.length() >= LexicalScorer.MIN_PREFIX_LENGTH;
            anyTerm.add(prefix ? "\\b" + word : "\\b" + word + "\\b");
            wholeWord.add(regexMatch("\\b" + word + "\\b"));
            wordStart.add(prefix ? regexMatch("\\b" + word) : new Document("$literal", false));
        }
        // The phrase must sit at word boundaries: "port" inside "report" is not the phrase "port".
        String phraseRegex = "(?<!\\w)" + Arrays.stream(phrase.split(" ")).map(BodyTextMatcher::escape)
                .collect(Collectors.joining("\\s+")) + "(?!\\w)";

        List<Document> pipeline = List.of(
                new Document("$match", new Document("content.raw_text",
                        new Document("$regex", String.join("|", anyTerm)).append("$options", "i"))),
                new Document("$limit", LIMIT),
                new Document("$project", new Document("_id", 0)
                        .append("id", "$postgres_document_id")
                        .append("phrase", regexMatch(phraseRegex))
                        .append("whole", wholeWord)
                        .append("start", wordStart)));

        Map<Integer, LexicalScorer.BodyEvidence> found = new HashMap<>();
        String collection = mongoTemplate.getCollectionName(KnowledgeDocument.class);
        for (Document row : mongoTemplate.getCollection(collection).aggregate(pipeline)) {
            if (!(row.get("id") instanceof Number id)) {
                continue;
            }
            List<?> whole = row.getList("whole", Object.class);
            List<?> start = row.getList("start", Object.class);
            double[] credits = new double[terms.size()];
            for (int i = 0; i < credits.length; i++) {
                if (i < whole.size() && Boolean.TRUE.equals(whole.get(i))) {
                    credits[i] = 1.0;
                } else if (i < start.size() && Boolean.TRUE.equals(start.get(i))) {
                    credits[i] = LexicalScorer.BODY_PREFIX_CREDIT;
                }
            }
            found.put(id.intValue(), new LexicalScorer.BodyEvidence(Boolean.TRUE.equals(row.get("phrase")), credits));
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
