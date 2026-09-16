package texthack.dp;

/**
 * Smith-Waterman local sequence alignment.
 *
 * <p>Finds the best-scoring pair of <em>substrings</em>, rather than forcing the
 * whole of both inputs into one alignment. For document work that is usually the
 * question worth asking: two reports may share one quoted paragraph and agree
 * nowhere else, and a global alignment would drown that paragraph in gap
 * penalties until the score said "unrelated".
 *
 * <h2>Two changes from Needleman-Wunsch</h2>
 * The algorithm is the same recurrence with two differences, and both matter:
 * <ol>
 *   <li>Cell scores are floored at zero. A negative running score means the
 *       alignment so far is worse than starting fresh here, so it restarts.
 *       That floor is what makes the alignment local.</li>
 *   <li>Traceback begins at the highest-scoring cell anywhere in the matrix,
 *       not the bottom-right corner, and stops on reaching a zero.</li>
 * </ol>
 *
 * <p>The first row and column stay zero rather than accumulating gap penalties,
 * since a local alignment may begin anywhere.
 *
 * <h2>Determinism</h2>
 * Where several cells share the maximum, the first found in row-major order
 * wins, and traceback breaks ties diagonal, then up, then left. The same inputs
 * always produce the same alignment.
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>Time: O(n*m).</li>
 *   <li>Space: O(n*m), required for traceback.</li>
 * </ul>
 */
public final class SmithWaterman {

    public static final int DEFAULT_MATCH = 2;
    public static final int DEFAULT_MISMATCH = -1;
    public static final int DEFAULT_GAP = -2;

    private SmithWaterman() {
    }

    /** Aligns with the default scoring scheme. */
    public static Alignment align(String a, String b) {
        return align(a, b, DEFAULT_MATCH, DEFAULT_MISMATCH, DEFAULT_GAP);
    }

    /**
     * Finds the highest-scoring local alignment.
     *
     * @throws IllegalArgumentException if either string is null
     */
    public static Alignment align(String a, String b, int match, int mismatch, int gap) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("inputs must not be null");
        }

        int n = a.length();
        int m = b.length();
        int[][] score = new int[n + 1][m + 1];

        int bestScore = 0;
        int bestI = 0;
        int bestJ = 0;

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int pairScore = (a.charAt(i - 1) == b.charAt(j - 1)) ? match : mismatch;
                int diagonal = score[i - 1][j - 1] + pairScore;
                int up = score[i - 1][j] + gap;
                int left = score[i][j - 1] + gap;

                int best = 0; // the local floor
                if (diagonal > best) {
                    best = diagonal;
                }
                if (up > best) {
                    best = up;
                }
                if (left > best) {
                    best = left;
                }
                score[i][j] = best;

                // Strict comparison keeps the first maximum in row-major order,
                // which is what makes the result deterministic.
                if (best > bestScore) {
                    bestScore = best;
                    bestI = i;
                    bestJ = j;
                }
            }
        }

        if (bestScore == 0) {
            // Nothing scored above the floor: no local alignment exists.
            return new Alignment("", "", 0, 0, 0, 0, 0);
        }

        StringBuilder alignedA = new StringBuilder();
        StringBuilder alignedB = new StringBuilder();

        int i = bestI;
        int j = bestJ;
        while (i > 0 && j > 0 && score[i][j] > 0) {
            int pairScore = (a.charAt(i - 1) == b.charAt(j - 1)) ? match : mismatch;
            if (score[i][j] == score[i - 1][j - 1] + pairScore) {
                alignedA.append(a.charAt(i - 1));
                alignedB.append(b.charAt(j - 1));
                i--;
                j--;
            } else if (score[i][j] == score[i - 1][j] + gap) {
                alignedA.append(a.charAt(i - 1));
                alignedB.append(Alignment.GAP);
                i--;
            } else {
                alignedA.append(Alignment.GAP);
                alignedB.append(b.charAt(j - 1));
                j--;
            }
        }

        return new Alignment(alignedA.reverse().toString(), alignedB.reverse().toString(),
                             bestScore, i, bestI, j, bestJ);
    }
}
