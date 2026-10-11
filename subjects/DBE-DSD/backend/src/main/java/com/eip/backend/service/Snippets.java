package com.eip.backend.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chooses the passage of a document to show under a search hit, and what to
 * highlight in it: the chunk holding the most query terms, cut to a window
 * around the first of them. Matching follows {@link LexicalScorer}: whole words,
 * or a word a term begins when inexact matches are allowed.
 */
public final class Snippets {

    /** A highlighted span of {@link Snippet#text}: start inclusive, end exclusive. */
    public record Range(int start, int end) {
    }

    public record Snippet(String text, List<Range> highlights) {
    }

    static final int WINDOW = 260;
    /** Text kept before the first match, so the match is read in context. */
    private static final int LEAD = 70;
    private static final String WORD = "[\\p{L}\\p{N}]";
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private Snippets() {
    }

    /**
     * @param matched  chunks that contain at least one term, in document order
     * @param semantic the chunk the vector search ranked best, or null
     * @param phrase   the query, {@link LexicalScorer#normalise normalised}
     * @param terms    its terms, as {@link LexicalScorer#terms} gives them
     * @return the snippet, or null when there is no passage to show
     */
    public static Snippet choose(List<String> matched, String semantic, String phrase, List<String> terms,
                                 boolean allowPrefix) {
        List<Pattern> termPatterns = terms.stream().map(term -> termPattern(term, allowPrefix)).toList();
        Pattern phrasePattern = terms.size() > 1 ? phrasePattern(phrase) : null;

        String best = null;
        int bestScore = 0;
        for (String text : matched) {
            int score = 0;
            for (Pattern pattern : termPatterns) {
                if (pattern.matcher(text).find()) {
                    score++;
                }
            }
            // The whole phrase outranks the same words scattered through a chunk.
            if (score > 0 && phrasePattern != null && phrasePattern.matcher(text).find()) {
                score += terms.size();
            }
            if (score > bestScore) {
                best = text;
                bestScore = score;
            }
        }
        if (best == null) {
            best = semantic;
        }
        if (best == null || best.isBlank()) {
            return null;
        }
        return window(best, anchor(best, phrasePattern, termPatterns), termPatterns);
    }

    /** Where the window is centred: the phrase if it occurs, else the first term, else the start. */
    private static int anchor(String text, Pattern phrasePattern, List<Pattern> termPatterns) {
        if (phrasePattern != null) {
            Matcher m = phrasePattern.matcher(text);
            if (m.find()) {
                return m.start();
            }
        }
        int first = -1;
        for (Pattern pattern : termPatterns) {
            Matcher m = pattern.matcher(text);
            if (m.find() && (first < 0 || m.start() < first)) {
                first = m.start();
            }
        }
        return Math.max(first, 0);
    }

    private static Snippet window(String text, int anchor, List<Pattern> termPatterns) {
        int start = Math.max(0, anchor - LEAD);
        if (start > 0) {
            // Begin at a word, not in the middle of one.
            int space = text.indexOf(' ', start);
            start = space >= 0 && space < anchor ? space + 1 : anchor;
        }
        int end = Math.min(text.length(), start + WINDOW);
        if (end < text.length()) {
            int space = text.lastIndexOf(' ', end);
            if (space > start + WINDOW / 2) {
                end = space;
            }
        }
        String lead = start > 0 ? "…" : "";
        String shown = lead + text.substring(start, end).strip() + (end < text.length() ? "…" : "");

        List<Range> found = new ArrayList<>();
        for (Pattern pattern : termPatterns) {
            Matcher m = pattern.matcher(shown);
            while (m.find()) {
                found.add(new Range(m.start(), m.end()));
            }
        }
        return new Snippet(shown, merge(found));
    }

    /** Sorted, with overlapping and touching ranges joined. */
    private static List<Range> merge(List<Range> ranges) {
        ranges.sort(Comparator.comparingInt(Range::start));
        List<Range> merged = new ArrayList<>();
        for (Range range : ranges) {
            Range last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && range.start() <= last.end()) {
                merged.set(merged.size() - 1, new Range(last.start(), Math.max(last.end(), range.end())));
            } else {
                merged.add(range);
            }
        }
        return merged;
    }

    private static Pattern termPattern(String term, boolean allowPrefix) {
        boolean prefix = allowPrefix && term.length() >= LexicalScorer.MIN_PREFIX_LENGTH;
        return Pattern.compile("(?<!" + WORD + ")" + Pattern.quote(term) + (prefix ? WORD + "*" : "(?!" + WORD + ")"),
                               FLAGS);
    }

    private static Pattern phrasePattern(String phrase) {
        StringBuilder regex = new StringBuilder("(?<!" + WORD + ")");
        String[] words = phrase.split(" ");
        for (int i = 0; i < words.length; i++) {
            regex.append(i == 0 ? "" : "\\s+").append(Pattern.quote(words[i]));
        }
        return Pattern.compile(regex.append("(?!").append(WORD).append(")").toString(), FLAGS);
    }
}
