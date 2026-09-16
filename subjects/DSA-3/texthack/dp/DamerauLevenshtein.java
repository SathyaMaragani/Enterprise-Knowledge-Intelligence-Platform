package texthack.dp;

import texthack.core.CharMap;

/**
 * Damerau-Levenshtein distance: Levenshtein plus transposition of two adjacent
 * characters as a single edit.
 *
 * <p>Transposition matters for real typing. "teh" is two edits from "the" under
 * Levenshtein (substitute, substitute) but one under Damerau-Levenshtein, and
 * transposed letters are among the most common typing errors. For fuzzy search
 * that is the difference between a threshold of 1 catching the typo or not.
 *
 * <h2>Restricted versus unrestricted</h2>
 * Two different algorithms travel under this name and they give different
 * answers:
 *
 * <ul>
 *   <li><b>Optimal String Alignment</b> (OSA, "restricted") allows a
 *       transposition only of characters that are adjacent in both strings, and
 *       forbids editing a substring more than once. It is simpler and uses
 *       O(min(n,m)) space.</li>
 *   <li><b>Unrestricted</b> Damerau-Levenshtein, implemented here, places no
 *       such restriction and is a true metric.</li>
 * </ul>
 *
 * The distinguishing case is {@code "CA"} to {@code "ABC"}: OSA says 3, the
 * unrestricted algorithm says 2 (transpose CA to AC, then insert B). Both are
 * defensible definitions, but they are not interchangeable, and a codebase that
 * calls one by the other's name will eventually surprise someone. The test
 * suite pins this case explicitly.
 *
 * <h2>How the transposition term works</h2>
 * The unrestricted version tracks, for every character, the last row in which
 * it appeared ({@code lastRow}), and for the current row the last column at
 * which the two strings matched ({@code lastMatchColumn}). A transposition
 * jumps back to that earlier position and pays for everything skipped in
 * between. That bookkeeping is why it needs the full matrix rather than two
 * rolling rows.
 *
 * <p>{@link CharMap} supplies the character table, since {@code HashMap} is
 * barred and an array indexed by the whole UTF-16 range would be 256 KB per
 * call.
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>Time: O(n*m), plus O(log k) per cell for the character table lookup.</li>
 *   <li>Space: O(n*m) -- the full matrix is required for the transposition
 *       term, so the rolling-row trick used by {@link Levenshtein} does not
 *       apply.</li>
 * </ul>
 */
public final class DamerauLevenshtein {

    private DamerauLevenshtein() {
    }

    /**
     * Unrestricted Damerau-Levenshtein distance.
     *
     * @throws IllegalArgumentException if either argument is null
     */
    public static int distance(String a, String b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("inputs must not be null");
        }
        if (a.equals(b)) {
            return 0;
        }
        int n = a.length();
        int m = b.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }

        // The matrix is offset by one extra row and column. Those borders hold a
        // sentinel larger than any achievable distance so the transposition term
        // can index "before the start" without a special case.
        int maxDistance = n + m;
        int[][] d = new int[n + 2][m + 2];

        d[0][0] = maxDistance;
        for (int i = 0; i <= n; i++) {
            d[i + 1][0] = maxDistance;
            d[i + 1][1] = i;
        }
        for (int j = 0; j <= m; j++) {
            d[0][j + 1] = maxDistance;
            d[1][j + 1] = j;
        }

        // character -> last row in which it appeared
        CharMap lastRow = new CharMap();

        for (int i = 1; i <= n; i++) {
            int lastMatchColumn = 0;
            char ai = a.charAt(i - 1);

            for (int j = 1; j <= m; j++) {
                char bj = b.charAt(j - 1);

                int rowOfBj = lastRow.get(bj);
                if (rowOfBj < 0) {
                    rowOfBj = 0;
                }
                int k = rowOfBj;
                int l = lastMatchColumn;

                int cost;
                if (ai == bj) {
                    cost = 0;
                    lastMatchColumn = j;
                } else {
                    cost = 1;
                }

                int substitution = d[i][j] + cost;
                int insertion = d[i + 1][j] + 1;
                int deletion = d[i][j + 1] + 1;
                // Jump back to the previous occurrence and pay for the gap.
                int transposition = d[k][l] + (i - k - 1) + 1 + (j - l - 1);

                int best = substitution;
                if (insertion < best) {
                    best = insertion;
                }
                if (deletion < best) {
                    best = deletion;
                }
                if (transposition < best) {
                    best = transposition;
                }
                d[i + 1][j + 1] = best;
            }

            lastRow.put(ai, i);
        }

        return d[n + 1][m + 1];
    }

    /**
     * Optimal String Alignment distance, the restricted variant.
     *
     * <p>Provided alongside the unrestricted version precisely because the two
     * disagree, so callers can pick deliberately rather than by accident. This
     * one keeps the classic three-row structure and never revisits a substring.
     *
     * <p>Time: O(n*m). Space: O(n*m).
     */
    public static int optimalStringAlignment(String a, String b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("inputs must not be null");
        }
        if (a.equals(b)) {
            return 0;
        }
        int n = a.length();
        int m = b.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }

        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            d[0][j] = j;
        }

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;

                int best = d[i - 1][j] + 1;
                int insertion = d[i][j - 1] + 1;
                if (insertion < best) {
                    best = insertion;
                }
                int substitution = d[i - 1][j - 1] + cost;
                if (substitution < best) {
                    best = substitution;
                }

                // Only adjacent-in-both-strings transpositions, and only once.
                if (i > 1 && j > 1
                        && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    int transposition = d[i - 2][j - 2] + 1;
                    if (transposition < best) {
                        best = transposition;
                    }
                }

                d[i][j] = best;
            }
        }

        return d[n][m];
    }
}
