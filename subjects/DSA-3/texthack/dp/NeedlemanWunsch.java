package texthack.dp;

/**
 * Needleman-Wunsch global sequence alignment.
 *
 * <p>Where {@link Levenshtein} asks "how many edits", this asks "how do these
 * two sequences line up, end to end". Every character of both inputs appears in
 * the result, padded with gaps where one side has no counterpart.
 *
 * <h2>Scoring rather than counting</h2>
 * Edit distance treats every operation as cost 1. Alignment uses a score that
 * can be tuned: a match is rewarded, a mismatch penalised, and a gap penalised
 * separately and usually more heavily. That asymmetry is the point -- for text,
 * one long gap (a deleted clause) is more plausible than many scattered
 * substitutions, and the scoring lets you say so.
 *
 * <p>Defaults here are match +1, mismatch -1, gap -2.
 *
 * <h2>Traceback</h2>
 * The score alone needs only two rows, but reconstructing the alignment needs
 * the whole matrix: traceback walks from the bottom-right corner back to the
 * origin, at each step choosing the neighbour the cell's score came from. That
 * is the reason this keeps O(n*m) space while {@code Levenshtein.distance}
 * does not.
 *
 * <p>Ties are broken in a fixed order -- diagonal, then up, then left -- so the
 * alignment returned is deterministic. Several optimal alignments usually
 * exist; this always returns the same one.
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>Time: O(n*m) to fill, O(n+m) to trace back.</li>
 *   <li>Space: O(n*m).</li>
 * </ul>
 */
public final class NeedlemanWunsch {

    public static final int DEFAULT_MATCH = 1;
    public static final int DEFAULT_MISMATCH = -1;
    public static final int DEFAULT_GAP = -2;

    private NeedlemanWunsch() {
    }

    /** Aligns with the default scoring scheme. */
    public static Alignment align(String a, String b) {
        return align(a, b, DEFAULT_MATCH, DEFAULT_MISMATCH, DEFAULT_GAP);
    }

    /**
     * Aligns {@code a} and {@code b} end to end.
     *
     * @param match reward for identical characters, normally positive
     * @param mismatch penalty for differing characters, normally negative
     * @param gap penalty for a gap, normally negative
     * @throws IllegalArgumentException if either string is null
     */
    public static Alignment align(String a, String b, int match, int mismatch, int gap) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("inputs must not be null");
        }

        int n = a.length();
        int m = b.length();
        int[][] score = new int[n + 1][m + 1];

        // Aligning a prefix against nothing costs one gap per character.
        for (int i = 1; i <= n; i++) {
            score[i][0] = score[i - 1][0] + gap;
        }
        for (int j = 1; j <= m; j++) {
            score[0][j] = score[0][j - 1] + gap;
        }

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int pairScore = (a.charAt(i - 1) == b.charAt(j - 1)) ? match : mismatch;
                int diagonal = score[i - 1][j - 1] + pairScore;
                int up = score[i - 1][j] + gap;
                int left = score[i][j - 1] + gap;

                int best = diagonal;
                if (up > best) {
                    best = up;
                }
                if (left > best) {
                    best = left;
                }
                score[i][j] = best;
            }
        }

        StringBuilder alignedA = new StringBuilder();
        StringBuilder alignedB = new StringBuilder();

        int i = n;
        int j = m;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0) {
                int pairScore = (a.charAt(i - 1) == b.charAt(j - 1)) ? match : mismatch;
                if (score[i][j] == score[i - 1][j - 1] + pairScore) {
                    alignedA.append(a.charAt(i - 1));
                    alignedB.append(b.charAt(j - 1));
                    i--;
                    j--;
                    continue;
                }
            }
            if (i > 0 && score[i][j] == score[i - 1][j] + gap) {
                alignedA.append(a.charAt(i - 1));
                alignedB.append(Alignment.GAP);
                i--;
                continue;
            }
            // Only the left move remains; guarded so a malformed matrix cannot
            // spin here forever.
            if (j > 0) {
                alignedA.append(Alignment.GAP);
                alignedB.append(b.charAt(j - 1));
                j--;
            } else {
                break;
            }
        }

        return new Alignment(alignedA.reverse().toString(), alignedB.reverse().toString(),
                             score[n][m], 0, n, 0, m);
    }
}
