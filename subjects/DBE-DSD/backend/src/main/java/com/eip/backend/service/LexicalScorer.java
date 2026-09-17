package com.eip.backend.service;

import org.springframework.stereotype.Component;
import texthack.dp.DamerauLevenshtein;
import texthack.string.AhoCorasick;
import texthack.string.KmpSearch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Phase 1.7C -- lexical relevance scoring backed by the DSA-3 TextHack engine.
 *
 * <p>Replaces the 1.7A placeholder, which scored a keyword hit 1.0 if the whole
 * query appeared in the title and 0.5 otherwise. That could not tell a document
 * using every query term from one using a single term, and a one-character typo
 * scored nothing at all. This scorer uses three TextHack algorithms, each for
 * the job it is actually suited to:
 *
 * <ul>
 *   <li><b>KMP</b> ({@link KmpSearch}) detects the whole query as a phrase. That
 *       is a single-pattern search, where KMP's guaranteed linear bound is the
 *       right tool.</li>
 *   <li><b>Aho-Corasick</b> ({@link AhoCorasick}) finds every query term in a
 *       field in one pass. Running a single-pattern matcher once per term would
 *       rescan the field k times.</li>
 *   <li><b>Damerau-Levenshtein, OSA variant</b>
 *       ({@link DamerauLevenshtein#optimalStringAlignment}) credits terms that
 *       are near-misses. OSA rather than plain Levenshtein because transposed
 *       letters ("recieve") are among the most common typing errors, and plain
 *       Levenshtein charges them two edits instead of one.</li>
 * </ul>
 *
 * <h2>Score, 0..1</h2>
 * <ol>
 *   <li>Whole query contained in the title: {@value #TITLE_PHRASE_SCORE}. This
 *       is deliberately unchanged from 1.7A, so an exact title match still
 *       normalises to the top of the range.</li>
 *   <li>Otherwise the larger of: whole query contained in the description
 *       ({@value #DESCRIPTION_PHRASE_SCORE}), or term coverage scaled to at most
 *       {@value #COVERAGE_CEILING}.</li>
 * </ol>
 *
 * <p>Term coverage averages a per-term credit: 1.0 for an exact whole-token
 * match, {@value #ONE_EDIT_CREDIT} one edit away, {@value #TWO_EDIT_CREDIT} two
 * edits away. Description matches are weighted by {@value #DESCRIPTION_WEIGHT},
 * since a term in a title says more about a document than one in its summary.
 *
 * <p>The coverage ceiling sits below 1.0 so that "all terms, reordered" never
 * ties with "the exact phrase in the title".
 *
 * <h2>Phrase containment versus term matching</h2>
 * Phrase detection is substring-based, matching PostgreSQL's {@code ILIKE}
 * semantics so both keyword paths agree on what a phrase match is. Term matching
 * is whole-token: "policy" does not exactly match inside "policyholder".
 *
 * <h2>Fuzzy thresholds</h2>
 * Allowed edits scale with term length, as in Elasticsearch's AUTO fuzziness:
 * none for terms of up to 3 characters, 1 for 4-7, 2 for 8 or more. Short terms
 * get no tolerance because at that length one edit is most of the word -- "nba"
 * is one edit from "nda", but they are not the same query.
 *
 * <p>Stateless and thread-safe.
 */
@Component
public class LexicalScorer {

    /** The outcome of scoring one document against one query. */
    public record Match(double score, boolean fuzzy, int matchedTerms, int totalTerms) {
        static final Match NONE = new Match(0.0, false, 0, 0);
    }

    public static final double TITLE_PHRASE_SCORE = 1.0;
    public static final double DESCRIPTION_PHRASE_SCORE = 0.75;
    public static final double COVERAGE_CEILING = 0.9;
    public static final double DESCRIPTION_WEIGHT = 0.6;
    public static final double ONE_EDIT_CREDIT = 0.7;
    public static final double TWO_EDIT_CREDIT = 0.5;

    /**
     * Minimum score for a document found only by the TextHack scan to count as a
     * hit. Documents found by the SQL phrase match are always kept; they score at
     * least {@value #DESCRIPTION_PHRASE_SCORE} by construction.
     */
    public static final double MIN_SCAN_SCORE = 0.5;

    private static final int MIN_TERM_LENGTH = 2;

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "can", "do", "does",
            "for", "from", "how", "i", "if", "in", "into", "is", "it", "its", "me",
            "my", "of", "on", "or", "our", "should", "so", "that", "the", "their",
            "then", "there", "these", "this", "to", "was", "we", "what", "when",
            "where", "which", "who", "why", "will", "with", "you", "your");

    private final KmpSearch phraseMatcher = new KmpSearch();

    /**
     * Scores a document's title and description against a query.
     *
     * <p>Null or blank inputs score zero rather than throwing, since a document
     * with no description is ordinary data, not an error.
     */
    public Match score(String query, String title, String description) {
        return score(query, title, description, true);
    }

    /**
     * As {@link #score(String, String, String)}, with typo tolerance switchable:
     * when {@code allowFuzzy} is false only exact whole-token terms earn credit.
     */
    public Match score(String query, String title, String description, boolean allowFuzzy) {
        if (query == null) {
            return Match.NONE;
        }
        String phrase = collapseWhitespace(query.toLowerCase(Locale.ROOT));
        if (phrase.isEmpty()) {
            return Match.NONE;
        }

        String titleText = title == null ? "" : title.toLowerCase(Locale.ROOT);
        String descriptionText = description == null ? "" : description.toLowerCase(Locale.ROOT);
        List<String> terms = terms(phrase);

        if (contains(titleText, phrase)) {
            return new Match(TITLE_PHRASE_SCORE, false, terms.size(), terms.size());
        }
        double phraseScore = contains(descriptionText, phrase) ? DESCRIPTION_PHRASE_SCORE : 0.0;

        if (terms.isEmpty()) {
            return new Match(phraseScore, false, 0, 0);
        }

        TermCredits fromTitle = credit(terms, titleText, allowFuzzy);
        TermCredits fromDescription = credit(terms, descriptionText, allowFuzzy);

        double total = 0.0;
        int matched = 0;
        boolean fuzzyUsed = false;

        for (int i = 0; i < terms.size(); i++) {
            double titleCredit = fromTitle.credit[i];
            double descriptionCredit = DESCRIPTION_WEIGHT * fromDescription.credit[i];

            double best;
            boolean bestWasFuzzy;
            if (titleCredit >= descriptionCredit) {
                best = titleCredit;
                bestWasFuzzy = fromTitle.fuzzy[i];
            } else {
                best = descriptionCredit;
                bestWasFuzzy = fromDescription.fuzzy[i];
            }

            if (best > 0.0) {
                matched++;
                fuzzyUsed |= bestWasFuzzy;
            }
            total += best;
        }

        double coverageScore = COVERAGE_CEILING * (total / terms.size());

        // A phrase match is exact evidence; it is only reported as fuzzy when the
        // term route both won and actually needed an inexact match to do so.
        if (phraseScore >= coverageScore) {
            return new Match(phraseScore, false, matched, terms.size());
        }
        return new Match(coverageScore, fuzzyUsed, matched, terms.size());
    }

    /** Per-term credit within one field, and whether it came from a fuzzy match. */
    private static final class TermCredits {
        final double[] credit;
        final boolean[] fuzzy;

        TermCredits(int size) {
            this.credit = new double[size];
            this.fuzzy = new boolean[size];
        }
    }

    private static TermCredits credit(List<String> terms, String field, boolean allowFuzzy) {
        TermCredits result = new TermCredits(terms.size());
        if (field.isEmpty()) {
            return result;
        }

        // Exact matches for every term in a single Aho-Corasick pass, kept only
        // where the occurrence is a whole token.
        AhoCorasick automaton = new AhoCorasick(terms.toArray(new String[0]));
        for (AhoCorasick.Match occurrence : automaton.findAll(field)) {
            if (isWholeToken(field, occurrence.start(), occurrence.end())) {
                result.credit[occurrence.patternIndex()] = 1.0;
            }
        }

        List<String> fieldTokens = null;
        for (int i = 0; i < terms.size(); i++) {
            if (result.credit[i] == 1.0) {
                continue;
            }
            String term = terms.get(i);
            int threshold = allowFuzzy ? fuzzyThreshold(term.length()) : 0;
            if (threshold == 0) {
                continue;
            }
            if (fieldTokens == null) {
                fieldTokens = tokens(field);
            }

            int best = Integer.MAX_VALUE;
            for (String token : fieldTokens) {
                // Each edit changes the length by at most one, so a larger length
                // gap cannot be within the threshold. Skipping it avoids the
                // O(n*m) distance computation for most tokens.
                if (Math.abs(token.length() - term.length()) > threshold) {
                    continue;
                }
                int distance = DamerauLevenshtein.optimalStringAlignment(term, token);
                if (distance < best) {
                    best = distance;
                }
            }

            if (best == 1) {
                result.credit[i] = ONE_EDIT_CREDIT;
                result.fuzzy[i] = true;
            } else if (best == 2 && threshold >= 2) {
                result.credit[i] = TWO_EDIT_CREDIT;
                result.fuzzy[i] = true;
            }
        }

        return result;
    }

    private boolean contains(String text, String pattern) {
        return !text.isEmpty() && phraseMatcher.findAll(text, pattern).length > 0;
    }

    private static boolean isWholeToken(String text, int start, int end) {
        boolean leftEdge = start == 0 || !Character.isLetterOrDigit(text.charAt(start - 1));
        boolean rightEdge = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
        return leftEdge && rightEdge;
    }

    /** Allowed edits for a term of the given length. */
    static int fuzzyThreshold(int length) {
        if (length <= 3) {
            return 0;
        }
        if (length <= 7) {
            return 1;
        }
        return 2;
    }

    /**
     * Distinct query terms in order, dropping stopwords and single characters.
     *
     * <p>Expects already-lowercased input.
     */
    static List<String> terms(String normalizedQuery) {
        Set<String> distinct = new LinkedHashSet<>();
        for (String token : tokens(normalizedQuery)) {
            if (token.length() >= MIN_TERM_LENGTH && !STOPWORDS.contains(token)) {
                distinct.add(token);
            }
        }
        return new ArrayList<>(distinct);
    }

    /** Splits on anything that is not a letter or digit. */
    static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                current.append(c);
            } else if (current.length() > 0) {
                out.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            out.add(current.toString());
        }
        return out;
    }

    /** Trims and collapses internal runs of whitespace to single spaces. */
    private static String collapseWhitespace(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean pendingSpace = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                pendingSpace = out.length() > 0;
            } else {
                if (pendingSpace) {
                    out.append(' ');
                    pendingSpace = false;
                }
                out.append(c);
            }
        }
        return out.toString();
    }
}
