package texthack.string;

import texthack.core.IntList;
import texthack.core.StringMatcher;

/**
 * Knuth-Morris-Pratt pattern matching.
 *
 * <p>The naive matcher throws away everything it learned on a failed alignment
 * and restarts one character later. KMP keeps it. When a mismatch happens after
 * matching {@code j} characters, those {@code j} characters are known text, so
 * the algorithm asks a question about the <em>pattern</em> alone: what is the
 * longest proper prefix of {@code pattern[0..j)} that is also a suffix of it?
 * That prefix is already aligned, so the pattern can slide forward without the
 * text pointer ever moving backwards.
 *
 * <p>Because the text index never retreats, the search is O(n) regardless of
 * input -- there is no pathological case, which is the practical reason to
 * prefer KMP over naive matching.
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>Time: O(n + m) worst case, with no bad inputs.</li>
 *   <li>Space: O(m) for the failure function.</li>
 * </ul>
 */
public final class KmpSearch implements StringMatcher {

    @Override
    public String name() {
        return "KMP";
    }

    @Override
    public int[] findAll(String text, String pattern) {
        if (StringMatcher.isTriviallyEmpty(text, pattern)) {
            return new int[0];
        }

        int n = text.length();
        int m = pattern.length();
        int[] failure = buildFailureFunction(pattern);
        IntList matches = new IntList();

        int matched = 0; // characters of the pattern currently matched
        for (int i = 0; i < n; i++) {
            // On mismatch, fall back through the failure chain rather than
            // rewinding i. Each fallback strictly decreases `matched`, which is
            // what bounds the total work at O(n).
            while (matched > 0 && text.charAt(i) != pattern.charAt(matched)) {
                matched = failure[matched - 1];
            }
            if (text.charAt(i) == pattern.charAt(matched)) {
                matched++;
            }
            if (matched == m) {
                matches.add(i - m + 1);
                // Fall back instead of resetting to 0, so overlapping
                // occurrences are still found.
                matched = failure[matched - 1];
            }
        }

        return matches.toArray();
    }

    /**
     * failure[i] = length of the longest proper prefix of pattern[0..i] that is
     * also a suffix of it.
     *
     * <p>Built by matching the pattern against itself with the same fallback
     * logic used in the search.
     *
     * <p>Public because it is a useful result on its own, not just an internal
     * step: the failure function yields the shortest period of a string
     * ({@code m - failure[m-1]}) and is reused when detecting repetitions.
     *
     * <p>Time: O(m). Space: O(m).
     */
    public static int[] buildFailureFunction(String pattern) {
        int m = pattern.length();
        int[] failure = new int[m];
        int length = 0;

        for (int i = 1; i < m; i++) {
            while (length > 0 && pattern.charAt(i) != pattern.charAt(length)) {
                length = failure[length - 1];
            }
            if (pattern.charAt(i) == pattern.charAt(length)) {
                length++;
            }
            failure[i] = length;
        }

        return failure;
    }
}
