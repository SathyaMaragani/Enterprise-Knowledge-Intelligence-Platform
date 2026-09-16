package texthack.dp;

/**
 * The result of a sequence alignment: the two strings padded with gap
 * characters so that corresponding positions line up, plus the score and the
 * region of the originals that was aligned.
 *
 * <p>Global alignment ({@link NeedlemanWunsch}) always spans both strings
 * entirely, so the offsets cover everything. Local alignment
 * ({@link SmithWaterman}) reports only the best-scoring region, and the offsets
 * say where in the originals it sat -- which is the part callers usually need,
 * since "these two documents share this passage" is more useful than a bare
 * score.
 */
public final class Alignment {

    /** Character used to mark a gap in an aligned string. */
    public static final char GAP = '-';

    private final String alignedFirst;
    private final String alignedSecond;
    private final int score;
    private final int firstStart;
    private final int firstEnd;
    private final int secondStart;
    private final int secondEnd;

    Alignment(String alignedFirst, String alignedSecond, int score,
              int firstStart, int firstEnd, int secondStart, int secondEnd) {
        this.alignedFirst = alignedFirst;
        this.alignedSecond = alignedSecond;
        this.score = score;
        this.firstStart = firstStart;
        this.firstEnd = firstEnd;
        this.secondStart = secondStart;
        this.secondEnd = secondEnd;
    }

    /** First input with gaps inserted. */
    public String alignedFirst() {
        return alignedFirst;
    }

    /** Second input with gaps inserted. */
    public String alignedSecond() {
        return alignedSecond;
    }

    public int score() {
        return score;
    }

    /** Start offset in the first input, inclusive. */
    public int firstStart() {
        return firstStart;
    }

    /** End offset in the first input, exclusive. */
    public int firstEnd() {
        return firstEnd;
    }

    /** Start offset in the second input, inclusive. */
    public int secondStart() {
        return secondStart;
    }

    /** End offset in the second input, exclusive. */
    public int secondEnd() {
        return secondEnd;
    }

    /** Number of aligned columns, including gaps. */
    public int length() {
        return alignedFirst.length();
    }

    /** Columns where both strings carry the same character. */
    public int matches() {
        int count = 0;
        for (int i = 0; i < alignedFirst.length(); i++) {
            char x = alignedFirst.charAt(i);
            if (x != GAP && x == alignedSecond.charAt(i)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Fraction of aligned columns that match, 0..1.
     *
     * <p>Zero for an empty alignment rather than a division by zero.
     */
    public double identity() {
        int length = alignedFirst.length();
        return length == 0 ? 0.0 : (double) matches() / length;
    }

    @Override
    public String toString() {
        return alignedFirst + System.lineSeparator() + alignedSecond
                + System.lineSeparator() + "score=" + score;
    }
}
