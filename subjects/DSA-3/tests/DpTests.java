package tests;

import texthack.dp.Alignment;
import texthack.dp.DamerauLevenshtein;
import texthack.dp.Levenshtein;
import texthack.dp.NeedlemanWunsch;
import texthack.dp.SmithWaterman;

/**
 * Self-checking suite for the dynamic programming module.
 *
 * <p>Follows the pattern that has already caught two real bugs in this subject:
 * known cases pin the textbook answers, invariants pin the properties that must
 * hold for any input, and randomised cross-validation compares each optimised
 * implementation against a deliberately naive reference.
 *
 * <p>For edit distance the reference is a full-matrix implementation written
 * plainly, so the rolling-array optimisation in {@link Levenshtein} has
 * something independent to disagree with.
 */
public final class DpTests {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testLevenshteinKnownCases();
        testLevenshteinMetricProperties();
        testLevenshteinSimilarityAndWithin();
        testLevenshteinCrossValidation();
        testDamerauKnownCases();
        testDamerauVersusOsa();
        testDamerauNeverExceedsLevenshtein();
        testNeedlemanWunschKnownCases();
        testNeedlemanWunschInvariants();
        testSmithWatermanKnownCases();
        testSmithWatermanLocality();
        testAlignmentScoresAreConsistent();
        testNullRejection();

        System.out.println();
        System.out.println("==========================================");
        System.out.printf(" Test Summary: %d passed, %d failed%n", passed, failed);
        System.out.println("==========================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    // --------------------------------------------------------- Levenshtein

    private static void testLevenshteinKnownCases() {
        section("Levenshtein: known cases");

        check("kitten -> sitting is 3", Levenshtein.distance("kitten", "sitting") == 3);
        check("saturday -> sunday is 3", Levenshtein.distance("saturday", "sunday") == 3);
        check("flaw -> lawn is 2", Levenshtein.distance("flaw", "lawn") == 2);
        check("identical strings are 0", Levenshtein.distance("abc", "abc") == 0);
        check("empty to empty is 0", Levenshtein.distance("", "") == 0);
        check("empty to abc is 3", Levenshtein.distance("", "abc") == 3);
        check("abc to empty is 3", Levenshtein.distance("abc", "") == 3);
        check("single substitution", Levenshtein.distance("a", "b") == 1);
        check("recieve -> receive is 2", Levenshtein.distance("recieve", "receive") == 2);
    }

    private static void testLevenshteinMetricProperties() {
        section("Levenshtein: metric properties");

        String[] words = {"", "a", "ab", "abc", "kitten", "sitting", "banana", "ananab"};

        boolean symmetric = true;
        boolean identityHolds = true;
        boolean triangleHolds = true;

        for (String x : words) {
            if (Levenshtein.distance(x, x) != 0) {
                identityHolds = false;
            }
            for (String y : words) {
                if (Levenshtein.distance(x, y) != Levenshtein.distance(y, x)) {
                    symmetric = false;
                }
                for (String z : words) {
                    if (Levenshtein.distance(x, z)
                            > Levenshtein.distance(x, y) + Levenshtein.distance(y, z)) {
                        triangleHolds = false;
                    }
                }
            }
        }

        check("d(x,x) == 0", identityHolds);
        check("symmetry: d(x,y) == d(y,x)", symmetric);
        check("triangle inequality holds", triangleHolds);
    }

    private static void testLevenshteinSimilarityAndWithin() {
        section("Levenshtein: similarity and threshold");

        check("identical similarity is 1.0", Levenshtein.similarity("abc", "abc") == 1.0);
        check("two empties are identical", Levenshtein.similarity("", "") == 1.0);
        check("disjoint equal-length similarity is 0.0",
              Levenshtein.similarity("abc", "xyz") == 0.0);

        double typo = Levenshtein.similarity("receive", "recieve");
        check("typo scores high but below 1", typo > 0.6 && typo < 1.0);

        check("within 1 accepts a single typo", Levenshtein.within("teh", "the", 2));
        check("within 0 rejects a typo", !Levenshtein.within("teh", "the", 0));
        check("within accepts identical at 0", Levenshtein.within("abc", "abc", 0));

        // The length short-circuit must not change the answer, only the cost.
        check("length gap beyond threshold rejected",
              !Levenshtein.within("a", "abcdefgh", 3));
        check("length gap within threshold still computed",
              Levenshtein.within("abc", "abcde", 2));

        check("negative threshold rejected",
              throwsIllegalArgument(() -> Levenshtein.within("a", "b", -1)));
    }

    private static void testLevenshteinCrossValidation() {
        section("Levenshtein: cross-validation against full-matrix reference");

        long seed = 5150L;
        int cases = 3000;
        int disagreements = 0;

        for (int c = 0; c < cases; c++) {
            seed = nextSeed(seed);
            int alphabet = 2 + (int) (seed % 3);
            seed = nextSeed(seed);
            int lengthA = (int) (seed % 25);
            seed = nextSeed(seed);
            int lengthB = (int) (seed % 25);

            StringBuilder a = new StringBuilder();
            for (int i = 0; i < lengthA; i++) {
                seed = nextSeed(seed);
                a.append((char) ('a' + seed % alphabet));
            }
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < lengthB; i++) {
                seed = nextSeed(seed);
                b.append((char) ('a' + seed % alphabet));
            }

            String x = a.toString();
            String y = b.toString();
            int expected = naiveLevenshtein(x, y);
            int actual = Levenshtein.distance(x, y);
            if (expected != actual) {
                disagreements++;
                if (disagreements <= 3) {
                    System.out.printf("  [FAIL] d(%s,%s) expected %d got %d%n",
                                      x, y, expected, actual);
                }
            }
        }

        check(cases + " random pairs match the full-matrix reference", disagreements == 0);
    }

    // ---------------------------------------------------- Damerau variants

    private static void testDamerauKnownCases() {
        section("Damerau-Levenshtein: known cases");

        check("teh -> the is 1 (transposition)", DamerauLevenshtein.distance("teh", "the") == 1);
        check("identical is 0", DamerauLevenshtein.distance("abc", "abc") == 0);
        check("empty to abc is 3", DamerauLevenshtein.distance("", "abc") == 3);
        check("abc to empty is 3", DamerauLevenshtein.distance("abc", "") == 3);
        check("ab -> ba is 1", DamerauLevenshtein.distance("ab", "ba") == 1);
        check("a -> b is 1", DamerauLevenshtein.distance("a", "b") == 1);
    }

    private static void testDamerauVersusOsa() {
        section("Damerau-Levenshtein: unrestricted versus OSA");

        // The case that separates the two definitions. Getting these equal would
        // mean one of the implementations is not what its name claims.
        check("OSA(CA, ABC) is 3", DamerauLevenshtein.optimalStringAlignment("CA", "ABC") == 3);
        check("unrestricted(CA, ABC) is 2", DamerauLevenshtein.distance("CA", "ABC") == 2);
        check("the two definitions genuinely differ here",
              DamerauLevenshtein.optimalStringAlignment("CA", "ABC")
                      != DamerauLevenshtein.distance("CA", "ABC"));

        // On a simple adjacent transposition they must agree.
        check("both agree on teh -> the",
              DamerauLevenshtein.optimalStringAlignment("teh", "the")
                      == DamerauLevenshtein.distance("teh", "the"));
    }

    private static void testDamerauNeverExceedsLevenshtein() {
        section("Damerau-Levenshtein: bounded by Levenshtein");

        long seed = 24680L;
        int cases = 2000;
        int violations = 0;
        int strictlyLess = 0;

        for (int c = 0; c < cases; c++) {
            seed = nextSeed(seed);
            int alphabet = 2 + (int) (seed % 3);
            seed = nextSeed(seed);
            int lengthA = (int) (seed % 12);
            seed = nextSeed(seed);
            int lengthB = (int) (seed % 12);

            StringBuilder a = new StringBuilder();
            for (int i = 0; i < lengthA; i++) {
                seed = nextSeed(seed);
                a.append((char) ('a' + seed % alphabet));
            }
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < lengthB; i++) {
                seed = nextSeed(seed);
                b.append((char) ('a' + seed % alphabet));
            }

            String x = a.toString();
            String y = b.toString();
            int levenshtein = Levenshtein.distance(x, y);
            int damerau = DamerauLevenshtein.distance(x, y);
            int osa = DamerauLevenshtein.optimalStringAlignment(x, y);

            // Adding an operation can only ever reduce the cost, never raise it.
            if (damerau > levenshtein || osa > levenshtein || damerau > osa) {
                violations++;
                if (violations <= 3) {
                    System.out.printf("  [FAIL] %s/%s lev=%d osa=%d dl=%d%n",
                                      x, y, levenshtein, osa, damerau);
                }
            }
            if (damerau < levenshtein) {
                strictlyLess++;
            }
        }

        check(cases + " random pairs respect dl <= osa <= levenshtein", violations == 0);
        check("transposition actually helps on some inputs", strictlyLess > 0);
    }

    // ------------------------------------------------------------ alignment

    private static void testNeedlemanWunschKnownCases() {
        section("Needleman-Wunsch: known cases");

        Alignment identical = NeedlemanWunsch.align("GATTACA", "GATTACA");
        check("identical alignment has no gaps",
              identical.alignedFirst().equals("GATTACA")
                      && identical.alignedSecond().equals("GATTACA"));
        check("identical score is 7 matches", identical.score() == 7);
        check("identical identity is 1.0", identical.identity() == 1.0);

        Alignment gapped = NeedlemanWunsch.align("GATTACA", "GATACA");
        check("one deletion introduces one gap",
              gapped.alignedFirst().length() == gapped.alignedSecond().length());
        check("gap appears in the shorter string",
              gapped.alignedSecond().indexOf(Alignment.GAP) >= 0);

        Alignment empty = NeedlemanWunsch.align("", "");
        check("empty against empty", empty.length() == 0 && empty.score() == 0);

        Alignment oneSided = NeedlemanWunsch.align("abc", "");
        check("all gaps against empty", oneSided.alignedSecond().equals("---"));
        check("all-gap score is 3 gap penalties", oneSided.score() == 3 * NeedlemanWunsch.DEFAULT_GAP);
    }

    private static void testNeedlemanWunschInvariants() {
        section("Needleman-Wunsch: invariants");

        long seed = 777L;
        int cases = 500;
        boolean lengthsMatch = true;
        boolean spansBoth = true;

        for (int c = 0; c < cases; c++) {
            seed = nextSeed(seed);
            int lengthA = (int) (seed % 15);
            seed = nextSeed(seed);
            int lengthB = (int) (seed % 15);

            StringBuilder a = new StringBuilder();
            for (int i = 0; i < lengthA; i++) {
                seed = nextSeed(seed);
                a.append((char) ('a' + seed % 3));
            }
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < lengthB; i++) {
                seed = nextSeed(seed);
                b.append((char) ('a' + seed % 3));
            }

            String x = a.toString();
            String y = b.toString();
            Alignment alignment = NeedlemanWunsch.align(x, y);

            if (alignment.alignedFirst().length() != alignment.alignedSecond().length()) {
                lengthsMatch = false;
            }
            // Global alignment must reproduce both inputs once gaps are removed.
            if (!stripGaps(alignment.alignedFirst()).equals(x)
                    || !stripGaps(alignment.alignedSecond()).equals(y)) {
                spansBoth = false;
            }
        }

        check("aligned strings are always equal length", lengthsMatch);
        check("removing gaps recovers both inputs exactly", spansBoth);
    }

    private static void testSmithWatermanKnownCases() {
        section("Smith-Waterman: known cases");

        Alignment shared = SmithWaterman.align("xxxCOMMONxxx", "yyyCOMMONyyy");
        check("finds the shared region", stripGaps(shared.alignedFirst()).equals("COMMON"));
        check("shared region scores 6 matches",
              shared.score() == 6 * SmithWaterman.DEFAULT_MATCH);
        check("offsets locate it in the first input",
              shared.firstStart() == 3 && shared.firstEnd() == 9);
        check("offsets locate it in the second input",
              shared.secondStart() == 3 && shared.secondEnd() == 9);

        Alignment nothing = SmithWaterman.align("aaaa", "bbbb");
        check("no positive-scoring region yields an empty alignment",
              nothing.length() == 0 && nothing.score() == 0);

        Alignment empty = SmithWaterman.align("", "abc");
        check("empty input yields nothing", empty.length() == 0);
    }

    private static void testSmithWatermanLocality() {
        section("Smith-Waterman: locality versus global alignment");

        // Two strings that agree on one island and disagree everywhere else.
        String a = "qqqqqqqqqqMATCHINGqqqqqqqqqq";
        String b = "zzzzzzzzzzMATCHINGzzzzzzzzzz";

        Alignment local = SmithWaterman.align(a, b);
        Alignment global = NeedlemanWunsch.align(a, b);

        check("local alignment isolates the island",
              stripGaps(local.alignedFirst()).equals("MATCHING"));
        check("local alignment is shorter than global",
              local.length() < global.length());
        // The whole point: the global score is dragged down by the mismatching
        // flanks, while the local score is not.
        check("local score exceeds global score", local.score() > global.score());
    }

    private static void testAlignmentScoresAreConsistent() {
        section("Alignment: reported score matches the alignment");

        long seed = 4242L;
        int cases = 400;
        int mismatches = 0;

        for (int c = 0; c < cases; c++) {
            seed = nextSeed(seed);
            int lengthA = 1 + (int) (seed % 14);
            seed = nextSeed(seed);
            int lengthB = 1 + (int) (seed % 14);

            StringBuilder a = new StringBuilder();
            for (int i = 0; i < lengthA; i++) {
                seed = nextSeed(seed);
                a.append((char) ('a' + seed % 4));
            }
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < lengthB; i++) {
                seed = nextSeed(seed);
                b.append((char) ('a' + seed % 4));
            }

            Alignment alignment = NeedlemanWunsch.align(a.toString(), b.toString());
            int recomputed = scoreAlignment(alignment,
                                            NeedlemanWunsch.DEFAULT_MATCH,
                                            NeedlemanWunsch.DEFAULT_MISMATCH,
                                            NeedlemanWunsch.DEFAULT_GAP);
            if (recomputed != alignment.score()) {
                mismatches++;
                if (mismatches <= 3) {
                    System.out.printf("  [FAIL] reported %d but alignment scores %d%n",
                                      alignment.score(), recomputed);
                }
            }
        }

        check(cases + " alignments score exactly as reported", mismatches == 0);
    }

    private static void testNullRejection() {
        section("Null rejection");

        check("Levenshtein null", throwsIllegalArgument(() -> Levenshtein.distance(null, "a")));
        check("Levenshtein similarity null",
              throwsIllegalArgument(() -> Levenshtein.similarity("a", null)));
        check("Damerau null", throwsIllegalArgument(() -> DamerauLevenshtein.distance(null, "a")));
        check("OSA null",
              throwsIllegalArgument(() -> DamerauLevenshtein.optimalStringAlignment("a", null)));
        check("Needleman-Wunsch null", throwsIllegalArgument(() -> NeedlemanWunsch.align(null, "a")));
        check("Smith-Waterman null", throwsIllegalArgument(() -> SmithWaterman.align("a", null)));
    }

    // ------------------------------------------------------- slow reference

    /**
     * Plain full-matrix Levenshtein. O(n*m) space, no rolling-array trick, so it
     * has nothing structurally in common with the implementation it checks.
     */
    private static int naiveLevenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
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
                if (d[i][j - 1] + 1 < best) {
                    best = d[i][j - 1] + 1;
                }
                if (d[i - 1][j - 1] + cost < best) {
                    best = d[i - 1][j - 1] + cost;
                }
                d[i][j] = best;
            }
        }
        return d[n][m];
    }

    /** Recomputes an alignment's score column by column. */
    private static int scoreAlignment(Alignment alignment, int match, int mismatch, int gap) {
        int total = 0;
        String first = alignment.alignedFirst();
        String second = alignment.alignedSecond();
        for (int i = 0; i < first.length(); i++) {
            char x = first.charAt(i);
            char y = second.charAt(i);
            if (x == Alignment.GAP || y == Alignment.GAP) {
                total += gap;
            } else if (x == y) {
                total += match;
            } else {
                total += mismatch;
            }
        }
        return total;
    }

    // --------------------------------------------------------------- helpers

    private static String stripGaps(String aligned) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < aligned.length(); i++) {
            char c = aligned.charAt(i);
            if (c != Alignment.GAP) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean throwsIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static long nextSeed(long seed) {
        return (seed * 6364136223846793005L + 1442695040888963407L) >>> 1;
    }

    private static void section(String title) {
        System.out.println("--- " + title + " ---");
    }

    private static void check(String description, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [PASS] " + description);
        } else {
            failed++;
            System.out.println("  [FAIL] " + description);
        }
    }
}
