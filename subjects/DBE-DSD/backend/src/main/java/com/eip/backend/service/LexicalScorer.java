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
 * Lexical relevance scoring backed by the DSA-3 TextHack engine.
 *
 * <p>Three TextHack algorithms, each for the job it suits:
 *
 * <ul>
 *   <li><b>KMP</b> ({@link KmpSearch}) finds the whole query as a phrase: a
 *       single-pattern search, where KMP's linear bound is the right tool.</li>
 *   <li><b>Aho-Corasick</b> ({@link AhoCorasick}) finds every query term in a
 *       field in one pass, instead of rescanning the field once per term.</li>
 *   <li><b>Damerau-Levenshtein, OSA variant</b>
 *       ({@link DamerauLevenshtein#optimalStringAlignment}) credits misspelled
 *       terms. OSA counts a swapped pair ("recieve") as one edit, not two.</li>
 * </ul>
 *
 * <h2>Score, 0..1</h2>
 * The larger of a phrase score and a term-coverage score, so a score reads as how
 * completely the document contains what was typed:
 * <ul>
 *   <li><b>Phrase:</b> the whole query, at word boundaries, in the title
 *       ({@value #TITLE_PHRASE_SCORE}), description ({@value #DESCRIPTION_PHRASE_SCORE})
 *       or text ({@value #BODY_PHRASE_SCORE}). A phrase whose last word is only
 *       begun ("remote work" in "remote working") earns {@value #PARTIAL_PHRASE_FACTOR}
 *       of that. "port" inside "report" is not a phrase match.</li>
 *   <li><b>Coverage:</b> the average per-term credit, at most
 *       {@value #COVERAGE_CEILING} so reordered terms rank just below the exact
 *       phrase. A term earns 1.0 as a whole word; a word it begins (3+ letters
 *       typed) earns {@link #prefixCredit}; a misspelling earns {@link #typoCredit}.
 *       Description terms count {@value #DESCRIPTION_WEIGHT} and text terms
 *       {@value #BODY_WEIGHT}, so the title ranks first among equal matches.</li>
 * </ul>
 *
 * <h2>Typo budget</h2>
 * Allowed edits scale with term length, as in Elasticsearch's AUTO fuzziness:
 * none up to 3 letters, 1 for 4-5, 2 for 6 or more. At 3 letters one edit is most
 * of the word: "nba" is one edit from "nda", but they are not the same query.
 *
 * <p>Stateless and thread-safe.
 */
@Component
public class LexicalScorer {

    /** The outcome of scoring one document against one query. */
    public record Match(double score, boolean fuzzy, int matchedTerms, int totalTerms) {
        static final Match NONE = new Match(0.0, false, 0, 0);
    }

    /**
     * What the document text contains, found in MongoDB by {@link BodyTextMatcher}:
     * whether it holds the whole query as a phrase, and a credit per term of
     * {@link #terms}: 1.0 for the whole word, {@value #BODY_PREFIX_CREDIT} for a word
     * the term begins, 0 for neither.
     */
    public record BodyEvidence(boolean phrase, double[] credits) {
    }

    public static final double TITLE_PHRASE_SCORE = 1.0;
    public static final double DESCRIPTION_PHRASE_SCORE = 0.95;
    public static final double BODY_PHRASE_SCORE = 0.9;
    public static final double PARTIAL_PHRASE_FACTOR = 0.85;
    public static final double COVERAGE_CEILING = 0.95;
    public static final double DESCRIPTION_WEIGHT = 0.95;
    public static final double BODY_WEIGHT = 0.9;
    /** A word the term begins, in the text; the full word's length is not known there. */
    public static final double BODY_PREFIX_CREDIT = 0.8;
    /** Fewer letters than this match only whole words: "re" begins too many. */
    public static final int MIN_PREFIX_LENGTH = 3;

    /**
     * Share of a long query's terms a document found only by the TextHack scan
     * must match to count as a hit; queries of one or two terms need every term.
     */
    public static final double MIN_TERM_COVERAGE = 0.6;

    /** Whether a match covers enough of the query to be a hit. */
    public static boolean coversEnough(Match match) {
        int total = match.totalTerms();
        int required = total <= 2 ? total : (int) Math.ceil(MIN_TERM_COVERAGE * total);
        return match.score() > 0 && match.matchedTerms() >= required;
    }

    /** Credit for a misspelling: how much of the longer word is right, e.g. 1 edit in 6 letters = 0.83. */
    public static double typoCredit(int distance, int termLength, int wordLength) {
        return 1.0 - (double) distance / Math.max(termLength, wordLength);
    }

    /** Credit for a word begun but not finished: 0.5 plus half the share typed, e.g. "secur" of "security" = 0.81. */
    public static double prefixCredit(int typedLength, int wordLength) {
        return 0.5 + 0.5 * typedLength / wordLength;
    }

    private static final int MIN_TERM_LENGTH = 2;

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "can", "do", "does",
            "for", "from", "how", "i", "if", "in", "into", "is", "it", "its", "me",
            "my", "of", "on", "or", "our", "should", "so", "that", "the", "their",
            "then", "there", "these", "this", "to", "was", "we", "what", "when",
            "where", "which", "who", "why", "will", "with", "you", "your");

    private enum PhraseFit { NONE, PARTIAL, EXACT }

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
     * when {@code allowFuzzy} is false only whole words earn credit (no
     * misspellings, no partly typed words).
     */
    public Match score(String query, String title, String description, boolean allowFuzzy) {
        return score(query, title, description, null, allowFuzzy);
    }

    /**
     * As {@link #score(String, String, String, boolean)}, also crediting what the
     * document text contains. {@code body} is null for a text with no query term.
     */
    public Match score(String query, String title, String description, BodyEvidence body, boolean allowFuzzy) {
        if (query == null) {
            return Match.NONE;
        }
        String phrase = normalise(query);
        if (phrase.isEmpty()) {
            return Match.NONE;
        }

        String titleText = title == null ? "" : title.toLowerCase(Locale.ROOT);
        String descriptionText = description == null ? "" : description.toLowerCase(Locale.ROOT);
        List<String> terms = terms(phrase);

        PhraseFit inTitle = phraseFit(titleText, phrase, allowFuzzy);
        PhraseFit inDescription = phraseFit(descriptionText, phrase, allowFuzzy);
        double titlePhrase = phraseScore(inTitle, TITLE_PHRASE_SCORE);
        double descriptionPhrase = phraseScore(inDescription, DESCRIPTION_PHRASE_SCORE);
        double bodyPhrase = body != null && body.phrase() ? BODY_PHRASE_SCORE : 0.0;
        double phraseScore = Math.max(titlePhrase, Math.max(descriptionPhrase, bodyPhrase));
        // A phrase whose last word is only begun is an inexact match.
        boolean phraseInexact = phraseScore > 0 && phraseScore > bodyPhrase
                && (titlePhrase >= descriptionPhrase ? inTitle : inDescription) == PhraseFit.PARTIAL;
        if (phraseScore == TITLE_PHRASE_SCORE || terms.isEmpty()) {
            return new Match(phraseScore, phraseInexact, phraseScore > 0 ? terms.size() : 0, terms.size());
        }

        TermCredits fromTitle = credit(terms, titleText, allowFuzzy);
        TermCredits fromDescription = credit(terms, descriptionText, allowFuzzy);

        double total = 0.0;
        int matched = 0;
        boolean fuzzyUsed = false;

        for (int i = 0; i < terms.size(); i++) {
            double best = fromTitle.credit[i];
            boolean bestWasFuzzy = fromTitle.fuzzy[i];
            double descriptionCredit = DESCRIPTION_WEIGHT * fromDescription.credit[i];
            if (descriptionCredit > best) {
                best = descriptionCredit;
                bestWasFuzzy = fromDescription.fuzzy[i];
            }
            double bodyCredit = body != null && i < body.credits().length ? body.credits()[i] : 0.0;
            if (BODY_WEIGHT * bodyCredit > best) {
                best = BODY_WEIGHT * bodyCredit;
                bestWasFuzzy = bodyCredit < 1.0;
            }

            if (best > 0.0) {
                matched++;
                fuzzyUsed |= bestWasFuzzy;
            }
            total += best;
        }

        double coverageScore = COVERAGE_CEILING * (total / terms.size());

        if (phraseScore >= coverageScore) {
            return new Match(phraseScore, phraseInexact, terms.size(), terms.size());
        }
        return new Match(coverageScore, fuzzyUsed, matched, terms.size());
    }

    private static double phraseScore(PhraseFit fit, double exactScore) {
        return switch (fit) {
            case EXACT -> exactScore;
            case PARTIAL -> exactScore * PARTIAL_PHRASE_FACTOR;
            case NONE -> 0.0;
        };
    }

    /**
     * Where the phrase occurs in the text: at word boundaries (EXACT), starting at
     * one and ending inside a word (PARTIAL, only when inexact matches are allowed
     * and enough was typed), or not as a phrase at all.
     */
    private PhraseFit phraseFit(String text, String phrase, boolean allowPartial) {
        if (text.isEmpty()) {
            return PhraseFit.NONE;
        }
        PhraseFit best = PhraseFit.NONE;
        for (int start : phraseMatcher.findAll(text, phrase)) {
            int end = start + phrase.length();
            if (!startsWord(text, start)) {
                continue; // "port" inside "report"
            }
            if (endsWord(text, end)) {
                return PhraseFit.EXACT;
            }
            if (allowPartial && lastWordLength(phrase) >= MIN_PREFIX_LENGTH) {
                best = PhraseFit.PARTIAL;
            }
        }
        return best;
    }

    private static int lastWordLength(String phrase) {
        List<String> words = tokens(phrase);
        return words.isEmpty() ? 0 : words.get(words.size() - 1).length();
    }

    /** Per-term credit within one field, and whether it came from an inexact match. */
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
        // where the occurrence is a whole word.
        AhoCorasick automaton = new AhoCorasick(terms.toArray(new String[0]));
        for (AhoCorasick.Match occurrence : automaton.findAll(field)) {
            if (startsWord(field, occurrence.start()) && endsWord(field, occurrence.end())) {
                result.credit[occurrence.patternIndex()] = 1.0;
            }
        }
        if (!allowFuzzy) {
            return result;
        }

        List<String> fieldTokens = tokens(field);
        for (int i = 0; i < terms.size(); i++) {
            if (result.credit[i] == 1.0) {
                continue;
            }
            String term = terms.get(i);
            int budget = fuzzyThreshold(term.length());
            double best = 0.0;
            for (String token : fieldTokens) {
                // A word the term begins: "secur" in "security".
                if (term.length() >= MIN_PREFIX_LENGTH && token.length() > term.length() && token.startsWith(term)) {
                    best = Math.max(best, prefixCredit(term.length(), token.length()));
                }
                // A misspelling. Each edit changes the length by at most one, so a
                // larger gap cannot be within budget; skipping it avoids the O(n*m)
                // distance computation for most tokens.
                if (budget > 0 && Math.abs(token.length() - term.length()) <= budget) {
                    int distance = DamerauLevenshtein.optimalStringAlignment(term, token);
                    if (distance <= budget) {
                        best = Math.max(best, typoCredit(distance, term.length(), token.length()));
                    }
                }
            }
            if (best > 0.0) {
                result.credit[i] = best;
                result.fuzzy[i] = true;
            }
        }
        return result;
    }

    private static boolean startsWord(String text, int start) {
        return start == 0 || !Character.isLetterOrDigit(text.charAt(start - 1));
    }

    private static boolean endsWord(String text, int end) {
        return end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
    }

    /** Allowed edits for a term of the given length. */
    static int fuzzyThreshold(int length) {
        if (length <= 3) {
            return 0;
        }
        if (length <= 5) {
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

    /** The query as every matcher sees it: lowercased, whitespace collapsed. */
    static String normalise(String query) {
        return collapseWhitespace(query.toLowerCase(Locale.ROOT));
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
