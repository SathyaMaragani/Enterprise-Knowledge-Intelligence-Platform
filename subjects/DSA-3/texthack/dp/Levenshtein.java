package texthack.dp;

/**
 * Levenshtein edit distance: the minimum number of single-character
 * insertions, deletions and substitutions that turn one string into another.
 *
 * <p>This is the measure fuzzy search is built on. "recieve" is distance 1 from
 * "receive", so a threshold of 1 catches the typo without dragging in unrelated
 * words.
 *
 * <h2>Recurrence</h2>
 * For prefixes a[0..i) and b[0..j), the distance is the cheapest of three moves:
 * <pre>
 *   d[i][j] = min( d[i-1][j]   + 1,              delete a[i-1]
 *                  d[i][j-1]   + 1,              insert b[j-1]
 *                  d[i-1][j-1] + cost )          substitute, cost 0 if equal
 * </pre>
 *
 * <h2>Space</h2>
 * The full matrix is O(n*m), but each row depends only on the row above, so two
 * rows suffice. The shorter string is placed on the row axis, making the cost
 * O(min(n, m)) rather than O(n). For a 10-character query against a 5000-word
 * document that is the difference between kilobytes and megabytes of scratch.
 *
 * <p>The tradeoff is that the alignment itself cannot be recovered -- traceback
 * needs the whole matrix. When the alignment is wanted rather than just the
 * distance, {@link NeedlemanWunsch} keeps the matrix and reconstructs it.
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>Time: O(n*m).</li>
 *   <li>Space: O(min(n, m)).</li>
 * </ul>
 */
public final class Levenshtein {

    private Levenshtein() {
    }

    /**
     * Edit distance between {@code a} and {@code b}.
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
        if (a.isEmpty()) {
            return b.length();
        }
        if (b.isEmpty()) {
            return a.length();
        }

        // Put the shorter string on the row axis so the rolling arrays are as
        // small as possible. Edit distance is symmetric, so this is free.
        String shorter = a.length() <= b.length() ? a : b;
        String longer = a.length() <= b.length() ? b : a;

        int n = shorter.length();
        int[] previous = new int[n + 1];
        int[] current = new int[n + 1];

        for (int i = 0; i <= n; i++) {
            previous[i] = i;
        }

        for (int j = 1; j <= longer.length(); j++) {
            current[0] = j;
            char bj = longer.charAt(j - 1);

            for (int i = 1; i <= n; i++) {
                int cost = (shorter.charAt(i - 1) == bj) ? 0 : 1;
                int deletion = previous[i] + 1;
                int insertion = current[i - 1] + 1;
                int substitution = previous[i - 1] + cost;

                int best = deletion < insertion ? deletion : insertion;
                current[i] = best < substitution ? best : substitution;
            }

            int[] swap = previous;
            previous = current;
            current = swap;
        }

        return previous[n];
    }

    /**
     * Distance normalised to 0..1, where 1 is identical and 0 shares nothing.
     *
     * <p>Raw distance is not comparable across lengths: a distance of 2 is
     * near-identity for a 40-character title and unrecognisable for a
     * 3-character one. Dividing by the longer length makes the score usable as
     * a ranking signal.
     */
    public static double similarity(String a, String b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("inputs must not be null");
        }
        int longest = a.length() >= b.length() ? a.length() : b.length();
        if (longest == 0) {
            return 1.0; // two empty strings are identical
        }
        return 1.0 - ((double) distance(a, b) / longest);
    }

    /**
     * True when the distance is at most {@code maxDistance}.
     *
     * <p>Short-circuits on the length difference first: two strings whose
     * lengths differ by more than the threshold cannot possibly be within it,
     * since each edit changes the length by at most one. That check costs
     * nothing and skips the whole matrix for most non-candidates, which is what
     * makes threshold filtering over a corpus practical.
     */
    public static boolean within(String a, String b, int maxDistance) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("inputs must not be null");
        }
        if (maxDistance < 0) {
            throw new IllegalArgumentException("maxDistance must not be negative");
        }
        int lengthGap = a.length() - b.length();
        if (lengthGap < 0) {
            lengthGap = -lengthGap;
        }
        if (lengthGap > maxDistance) {
            return false;
        }
        return distance(a, b) <= maxDistance;
    }
}
