package texthack.string;

import texthack.core.IntList;
import texthack.core.StringMatcher;

/**
 * Z-algorithm pattern matching.
 *
 * <p>The Z-array of a string gives, for each position, the length of the longest
 * substring starting there that is also a prefix of the whole string. Computing
 * it for the pattern tells the matcher how much of a partial match it can reuse
 * after a mismatch, in the same spirit as KMP but expressed as prefix lengths
 * rather than a failure function.
 *
 * <h2>Why this does not concatenate</h2>
 * The textbook formulation builds the Z-array of {@code pattern + sentinel + text}
 * and reports every position whose Z-value equals the pattern length. That
 * requires a sentinel character guaranteed not to appear in either input, which
 * is not something a general-purpose matcher over arbitrary text can promise --
 * pick {@code '\0'} and the algorithm silently reports wrong answers on any text
 * containing it.
 *
 * <p>This implementation computes the Z-array of the pattern only, then runs the
 * same window logic directly against the text. No sentinel, no concatenation, no
 * assumption about the alphabet, and it allocates O(m) instead of O(n + m).
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>Time: O(n + m). The window end {@code right} never moves backwards, so
 *       the total number of character comparisons is linear.</li>
 *   <li>Space: O(m) for the pattern's Z-array.</li>
 * </ul>
 */
public final class ZSearch implements StringMatcher {

    @Override
    public String name() {
        return "Z-Algorithm";
    }

    @Override
    public int[] findAll(String text, String pattern) {
        if (StringMatcher.isTriviallyEmpty(text, pattern)) {
            return new int[0];
        }

        int n = text.length();
        int m = pattern.length();
        int[] z = buildZArray(pattern);
        IntList matches = new IntList();

        // [left, right) is the rightmost window of text known to equal a prefix
        // of the pattern.
        int left = 0;
        int right = 0;

        for (int i = 0; i < n; i++) {
            int matched = 0;
            if (i < right) {
                // Reuse what the pattern's own Z-array already proved about this
                // offset, capped by how far the known window extends.
                int remaining = right - i;
                int mirrored = z[i - left];
                matched = remaining < mirrored ? remaining : mirrored;
            }
            while (matched < m && i + matched < n
                    && text.charAt(i + matched) == pattern.charAt(matched)) {
                matched++;
            }
            if (matched == m) {
                matches.add(i);
            }
            if (i + matched > right) {
                left = i;
                right = i + matched;
            }
        }

        return matches.toArray();
    }

    /**
     * z[i] = length of the longest substring starting at i that is also a prefix
     * of {@code s}. By convention z[0] is the whole length.
     *
     * <p>Public because the Z-array is a building block for other algorithms
     * (period detection, tandem-repeat finding), not merely a private step of
     * this matcher.
     *
     * <p>Time: O(n). Space: O(n).
     */
    public static int[] buildZArray(String s) {
        int n = s.length();
        int[] z = new int[n];
        z[0] = n;

        int left = 0;
        int right = 0;

        for (int i = 1; i < n; i++) {
            if (i < right) {
                int remaining = right - i;
                int mirrored = z[i - left];
                z[i] = remaining < mirrored ? remaining : mirrored;
            }
            while (i + z[i] < n && s.charAt(z[i]) == s.charAt(i + z[i])) {
                z[i]++;
            }
            if (i + z[i] > right) {
                left = i;
                right = i + z[i];
            }
        }

        return z;
    }
}
