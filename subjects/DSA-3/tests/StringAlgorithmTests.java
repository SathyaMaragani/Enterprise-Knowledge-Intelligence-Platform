package tests;

import texthack.core.IntList;
import texthack.core.StringMatcher;
import texthack.string.KmpSearch;
import texthack.string.NaiveSearch;
import texthack.string.RabinKarpSearch;
import texthack.string.ZSearch;

/**
 * Self-checking test suite for the TextHack string module.
 *
 * <p>No test framework: the subject builds from scratch, and a plain main method
 * with assertions runs anywhere a JDK exists, with no dependency resolution.
 *
 * <p>The important part is the randomised cross-validation. Fixed cases only
 * catch the bugs you thought of; running all four matchers over thousands of
 * random strings and demanding they agree with the naive reference catches the
 * off-by-one errors you did not. The random source is a hand-rolled LCG so runs
 * are reproducible from a seed.
 *
 * <pre>
 *   javac -d out $(find texthack tests -name '*.java')
 *   java -cp out tests.StringAlgorithmTests
 * </pre>
 */
public final class StringAlgorithmTests {

    private static int passed = 0;
    private static int failed = 0;

    private static final StringMatcher REFERENCE = new NaiveSearch();
    private static final StringMatcher[] MATCHERS = {
        new NaiveSearch(),
        new KmpSearch(),
        new ZSearch(),
        new RabinKarpSearch(),
    };

    public static void main(String[] args) {
        testIntList();
        testEmptyAndBoundaryInputs();
        testNullRejected();
        testKnownOccurrences();
        testOverlappingOccurrences();
        testPathologicalRepeats();
        testFailureFunction();
        testZArray();
        testUnicodeAndHashCollisionPressure();
        testRandomisedCrossValidation();

        System.out.println();
        System.out.println("==========================================");
        System.out.printf(" Test Summary: %d passed, %d failed%n", passed, failed);
        System.out.println("==========================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    // ----------------------------------------------------------------- cases

    private static void testIntList() {
        section("IntList");

        IntList list = new IntList(1);
        check("new list is empty", list.isEmpty());
        check("new list size 0", list.size() == 0);

        // Push well past the initial capacity to exercise doubling.
        for (int i = 0; i < 1000; i++) {
            list.add(i * 3);
        }
        check("size after 1000 adds", list.size() == 1000);
        check("first element", list.get(0) == 0);
        check("last element", list.get(999) == 2997);

        int[] array = list.toArray();
        check("toArray length matches size", array.length == 1000);
        check("toArray content", array[500] == 1500);

        boolean threw = false;
        try {
            list.get(1000);
        } catch (IndexOutOfBoundsException expected) {
            threw = true;
        }
        check("get past end throws", threw);
    }

    private static void testEmptyAndBoundaryInputs() {
        section("Empty and boundary inputs");

        for (StringMatcher matcher : MATCHERS) {
            check(matcher.name() + ": empty pattern yields nothing",
                  matcher.findAll("abc", "").length == 0);
            check(matcher.name() + ": pattern longer than text yields nothing",
                  matcher.findAll("ab", "abc").length == 0);
            check(matcher.name() + ": empty text yields nothing",
                  matcher.findAll("", "a").length == 0);
            check(matcher.name() + ": pattern equal to text matches at 0",
                  sameArray(matcher.findAll("abc", "abc"), new int[] {0}));
            check(matcher.name() + ": single character text",
                  sameArray(matcher.findAll("a", "a"), new int[] {0}));
            check(matcher.name() + ": no occurrence",
                  matcher.findAll("abcdef", "xyz").length == 0);
        }
    }

    private static void testNullRejected() {
        section("Null rejection");

        for (StringMatcher matcher : MATCHERS) {
            check(matcher.name() + ": null text throws", throwsIllegalArgument(matcher, null, "a"));
            check(matcher.name() + ": null pattern throws", throwsIllegalArgument(matcher, "a", null));
        }
    }

    private static void testKnownOccurrences() {
        section("Known occurrences");

        String text = "the quick brown fox jumps over the lazy dog, the end";
        for (StringMatcher matcher : MATCHERS) {
            check(matcher.name() + ": finds 'the' three times",
                  sameArray(matcher.findAll(text, "the"), new int[] {0, 31, 45}));
            check(matcher.name() + ": finds 'fox' once",
                  sameArray(matcher.findAll(text, "fox"), new int[] {16}));
            // Derived rather than hand-counted. A literal index here is exactly
            // the off-by-one the randomised cross-validation exists to catch --
            // and it caught one: all four matchers agreed with each other and
            // disagreed with a miscounted constant.
            check(matcher.name() + ": match flush against end of text",
                  sameArray(matcher.findAll(text, "end"),
                            new int[] {text.length() - "end".length()}));
        }
    }

    private static void testOverlappingOccurrences() {
        section("Overlapping occurrences");

        // This is where a matcher that resets to zero after a hit goes wrong.
        for (StringMatcher matcher : MATCHERS) {
            check(matcher.name() + ": 'aa' in 'aaaa' overlaps",
                  sameArray(matcher.findAll("aaaa", "aa"), new int[] {0, 1, 2}));
            check(matcher.name() + ": 'aba' in 'ababa' overlaps",
                  sameArray(matcher.findAll("ababa", "aba"), new int[] {0, 2}));
            check(matcher.name() + ": 'aaa' in 'aaaaa' overlaps",
                  sameArray(matcher.findAll("aaaaa", "aaa"), new int[] {0, 1, 2}));
        }
    }

    private static void testPathologicalRepeats() {
        section("Pathological repeated prefixes");

        // The worst case for naive matching: every alignment compares m-1
        // characters before failing on the last.
        StringBuilder haystack = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            haystack.append('a');
        }
        String text = haystack.toString();

        String missing = "a".repeat(50) + "b";
        String present = "a".repeat(50);

        for (StringMatcher matcher : MATCHERS) {
            check(matcher.name() + ": no match on a^2000 vs a^50 b",
                  matcher.findAll(text, missing).length == 0);
            check(matcher.name() + ": 1951 overlapping matches of a^50",
                  matcher.findAll(text, present).length == 2000 - 50 + 1);
        }
    }

    private static void testFailureFunction() {
        section("KMP failure function");

        // "abab" -> longest proper prefix that is also a suffix, per position.
        check("failure of 'abab'", sameArray(KmpSearch.buildFailureFunction("abab"),
                                             new int[] {0, 0, 1, 2}));
        check("failure of 'aaaa'", sameArray(KmpSearch.buildFailureFunction("aaaa"),
                                             new int[] {0, 1, 2, 3}));
        check("failure of 'abcde'", sameArray(KmpSearch.buildFailureFunction("abcde"),
                                              new int[] {0, 0, 0, 0, 0}));
        check("failure of 'aabaaab'", sameArray(KmpSearch.buildFailureFunction("aabaaab"),
                                                new int[] {0, 1, 0, 1, 2, 2, 3}));
    }

    private static void testZArray() {
        section("Z array");

        check("z of 'aaaa'", sameArray(ZSearch.buildZArray("aaaa"), new int[] {4, 3, 2, 1}));
        check("z of 'abcab'", sameArray(ZSearch.buildZArray("abcab"), new int[] {5, 0, 0, 2, 0}));
        check("z of 'aabxaab'", sameArray(ZSearch.buildZArray("aabxaab"),
                                          new int[] {7, 1, 0, 0, 3, 1, 0}));
    }

    private static void testUnicodeAndHashCollisionPressure() {
        section("Unicode and hash pressure");

        // A NUL character in the text would break any implementation that
        // concatenates with '\0' as a sentinel.
        String withNul = "abc\0def\0abc";
        for (StringMatcher matcher : MATCHERS) {
            check(matcher.name() + ": handles NUL in text",
                  sameArray(matcher.findAll(withNul, "abc"), new int[] {0, 8}));
            check(matcher.name() + ": matches a NUL pattern",
                  sameArray(matcher.findAll(withNul, "\0"), new int[] {3, 7}));
        }

        String unicode = "naïve café naïve";
        for (StringMatcher matcher : MATCHERS) {
            check(matcher.name() + ": non-ASCII pattern",
                  sameArray(matcher.findAll(unicode, "naïve"), new int[] {0, 11}));
        }

        // Characters above 255 exercise the rolling hash beyond a byte alphabet.
        String wide = "日本語テキスト日本語";
        for (StringMatcher matcher : MATCHERS) {
            check(matcher.name() + ": CJK pattern",
                  sameArray(matcher.findAll(wide, "日本語"), new int[] {0, 7}));
        }
    }

    private static void testRandomisedCrossValidation() {
        section("Randomised cross-validation against naive reference");

        long seed = 20260916L;
        int cases = 4000;
        int disagreements = 0;

        for (int i = 0; i < cases; i++) {
            seed = nextSeed(seed);
            // A tiny alphabet makes accidental matches and overlaps common,
            // which is exactly the regime where off-by-one bugs surface.
            int alphabet = 2 + (int) (seed % 3);
            seed = nextSeed(seed);
            int textLength = 1 + (int) (seed % 60);
            seed = nextSeed(seed);
            int patternLength = 1 + (int) (seed % 6);

            StringBuilder text = new StringBuilder();
            for (int c = 0; c < textLength; c++) {
                seed = nextSeed(seed);
                text.append((char) ('a' + seed % alphabet));
            }
            StringBuilder pattern = new StringBuilder();
            for (int c = 0; c < patternLength; c++) {
                seed = nextSeed(seed);
                pattern.append((char) ('a' + seed % alphabet));
            }

            String t = text.toString();
            String p = pattern.toString();
            int[] expected = REFERENCE.findAll(t, p);

            for (StringMatcher matcher : MATCHERS) {
                int[] actual = matcher.findAll(t, p);
                if (!sameArray(actual, expected)) {
                    disagreements++;
                    if (disagreements <= 3) {
                        System.out.printf("  [FAIL] %s disagreed: text=%s pattern=%s%n",
                                          matcher.name(), t, p);
                        System.out.printf("         expected %s got %s%n",
                                          render(expected), render(actual));
                    }
                }
            }
        }

        check(cases + " random cases: all matchers agree with naive", disagreements == 0);
    }

    // --------------------------------------------------------------- helpers

    /**
     * Deterministic linear congruential generator.
     *
     * <p>Hand-rolled rather than java.util.Random so the suite stays dependency
     * free and a failing case can be reproduced exactly from the seed.
     */
    private static long nextSeed(long seed) {
        return (seed * 6364136223846793005L + 1442695040888963407L) >>> 1;
    }

    private static boolean throwsIllegalArgument(StringMatcher matcher, String text, String pattern) {
        try {
            matcher.findAll(text, pattern);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static boolean sameArray(int[] a, int[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    private static String render(int[] values) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(values[i]);
        }
        return out.append(']').toString();
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
