package tests;

import texthack.core.CharMap;
import texthack.string.AhoCorasick;
import texthack.string.LcpArray;
import texthack.string.NaiveSearch;
import texthack.string.SuffixArray;

/**
 * Self-checking suite for the second half of the string module: Aho-Corasick,
 * suffix array construction and Kasai's LCP algorithm.
 *
 * <p>Kept separate from {@code StringAlgorithmTests} so the original suite stays
 * exactly as it was verified -- these are additions, not edits to a passing
 * regression suite.
 *
 * <p>Same testing strategy that already caught a real bug in part one: every
 * algorithm is cross-validated against a deliberately slow reference over
 * randomised input. Aho-Corasick is checked against running the naive matcher
 * once per pattern, the suffix array against a brute-force suffix sort, and the
 * LCP array against direct pairwise comparison.
 *
 * <pre>
 *   java -cp out tests.SuffixAndMultiPatternTests
 * </pre>
 */
public final class SuffixAndMultiPatternTests {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testCharMap();
        testAhoCorasickClassicCase();
        testAhoCorasickOverlapAndNesting();
        testAhoCorasickDuplicatesAndSingle();
        testAhoCorasickRejectsBadInput();
        testAhoCorasickUnicodeAndNul();
        testAhoCorasickCrossValidation();
        testSuffixArrayKnownCases();
        testSuffixArrayIsAPermutation();
        testSuffixArrayCrossValidation();
        testLcpKnownCases();
        testLcpRepeatedPrefixes();
        testLcpCrossValidation();
        testDeterminism();

        System.out.println();
        System.out.println("==========================================");
        System.out.printf(" Test Summary: %d passed, %d failed%n", passed, failed);
        System.out.println("==========================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------- CharMap

    private static void testCharMap() {
        section("CharMap");

        CharMap map = new CharMap();
        check("empty map returns -1", map.get('a') == -1);
        check("empty map size 0", map.size() == 0);

        // Insert out of order; the map must keep keys ascending.
        map.put('m', 10);
        map.put('a', 1);
        map.put('z', 26);
        map.put('c', 3);

        check("size after 4 inserts", map.size() == 4);
        check("lookup a", map.get('a') == 1);
        check("lookup c", map.get('c') == 3);
        check("lookup m", map.get('m') == 10);
        check("lookup z", map.get('z') == 26);
        check("absent key", map.get('q') == -1);

        boolean ascending = true;
        for (int i = 1; i < map.size(); i++) {
            if (map.keyAt(i - 1) >= map.keyAt(i)) {
                ascending = false;
            }
        }
        check("keys stay in ascending order", ascending);

        map.put('a', 99);
        check("put overwrites existing key", map.get('a') == 99);
        check("overwrite does not change size", map.size() == 4);

        // Force several growth steps.
        CharMap big = new CharMap();
        for (int i = 0; i < 200; i++) {
            big.put((char) ('A' + i), i);
        }
        check("200 entries survive growth", big.size() == 200);
        check("first entry after growth", big.get('A') == 0);
        check("last entry after growth", big.get((char) ('A' + 199)) == 199);
    }

    // -------------------------------------------------------- Aho-Corasick

    private static void testAhoCorasickClassicCase() {
        section("Aho-Corasick: classic {he, she, his, hers}");

        String[] patterns = {"he", "she", "his", "hers"};
        AhoCorasick automaton = new AhoCorasick(patterns);
        AhoCorasick.Match[] matches = automaton.findAll("ushers");

        // "ushers": she@1, he@2, hers@2
        check("three matches in 'ushers'", matches.length == 3);
        check("finds she at 1", containsMatch(matches, 1, 1, 4));
        check("finds he at 2", containsMatch(matches, 0, 2, 4));
        check("finds hers at 2", containsMatch(matches, 3, 2, 6));
        check("does not find his", automaton.findAll("ushers", 2).length == 0);

        check("pattern count", automaton.patternCount() == 4);
        check("node count is sane", automaton.nodeCount() > 4);
    }

    private static void testAhoCorasickOverlapAndNesting() {
        section("Aho-Corasick: overlap and nesting");

        AhoCorasick overlapping = new AhoCorasick(new String[] {"aa"});
        check("overlapping 'aa' in 'aaaa'",
              sameArray(overlapping.findAll("aaaa", 0), new int[] {0, 1, 2}));

        // "a" is nested inside "aa", which is nested inside "aaa".
        AhoCorasick nested = new AhoCorasick(new String[] {"a", "aa", "aaa"});
        check("nested 'a' found 3 times", sameArray(nested.findAll("aaa", 0), new int[] {0, 1, 2}));
        check("nested 'aa' found 2 times", sameArray(nested.findAll("aaa", 1), new int[] {0, 1}));
        check("nested 'aaa' found once", sameArray(nested.findAll("aaa", 2), new int[] {0}));
        check("total nested matches", nested.findAll("aaa").length == 6);

        // A pattern that only surfaces through the output link chain.
        AhoCorasick chain = new AhoCorasick(new String[] {"abcd", "bc", "c"});
        AhoCorasick.Match[] matches = chain.findAll("abcd");
        check("outer pattern found", containsMatch(matches, 0, 0, 4));
        check("inner 'bc' found via output link", containsMatch(matches, 1, 1, 3));
        check("inner 'c' found via output link", containsMatch(matches, 2, 2, 3));

        check("results ordered by end position", endPositionsAscending(matches));
    }

    private static void testAhoCorasickDuplicatesAndSingle() {
        section("Aho-Corasick: duplicates and edge shapes");

        // Duplicate patterns are distinct indices landing on the same node.
        AhoCorasick duplicates = new AhoCorasick(new String[] {"ab", "ab"});
        check("duplicate index 0 reported", sameArray(duplicates.findAll("abab", 0), new int[] {0, 2}));
        check("duplicate index 1 reported", sameArray(duplicates.findAll("abab", 1), new int[] {0, 2}));
        check("duplicates double the match count", duplicates.findAll("abab").length == 4);

        AhoCorasick single = new AhoCorasick(new String[] {"needle"});
        check("no match in unrelated text", single.findAll("haystack").length == 0);
        check("match at position 0", sameArray(single.findAll("needle", 0), new int[] {0}));

        AhoCorasick none = new AhoCorasick(new String[0]);
        check("empty pattern set finds nothing", none.findAll("anything").length == 0);
        check("empty pattern set has no patterns", none.patternCount() == 0);

        AhoCorasick longer = new AhoCorasick(new String[] {"toolongpattern"});
        check("pattern longer than text", longer.findAll("short").length == 0);
        check("empty text", longer.findAll("").length == 0);
    }

    private static void testAhoCorasickRejectsBadInput() {
        section("Aho-Corasick: input validation");

        check("null pattern array rejected", throwsIllegalArgument(() -> new AhoCorasick(null)));
        check("null pattern rejected",
              throwsIllegalArgument(() -> new AhoCorasick(new String[] {"ok", null})));
        check("empty pattern rejected",
              throwsIllegalArgument(() -> new AhoCorasick(new String[] {"ok", ""})));

        AhoCorasick automaton = new AhoCorasick(new String[] {"a"});
        check("null text rejected", throwsIllegalArgument(() -> automaton.findAll(null)));

        boolean threw = false;
        try {
            automaton.findAll("aaa", 5);
        } catch (IndexOutOfBoundsException expected) {
            threw = true;
        }
        check("out of range pattern index rejected", threw);
    }

    private static void testAhoCorasickUnicodeAndNul() {
        section("Aho-Corasick: unicode and NUL");

        AhoCorasick automaton = new AhoCorasick(new String[] {"naïve", "日本語", "\0"});
        String text = "naïve 日本語\0naïve";

        check("non-ASCII pattern", automaton.findAll(text, 0).length == 2);
        check("CJK pattern", automaton.findAll(text, 1).length == 1);
        check("NUL pattern", automaton.findAll(text, 2).length == 1);
    }

    private static void testAhoCorasickCrossValidation() {
        section("Aho-Corasick: cross-validation against naive, per pattern");

        NaiveSearch naive = new NaiveSearch();
        long seed = 20260916L;
        int cases = 1500;
        int disagreements = 0;

        for (int c = 0; c < cases; c++) {
            seed = nextSeed(seed);
            int alphabet = 2 + (int) (seed % 3);
            seed = nextSeed(seed);
            int textLength = 1 + (int) (seed % 50);
            seed = nextSeed(seed);
            int patternCount = 1 + (int) (seed % 4);

            StringBuilder text = new StringBuilder();
            for (int i = 0; i < textLength; i++) {
                seed = nextSeed(seed);
                text.append((char) ('a' + seed % alphabet));
            }

            String[] patterns = new String[patternCount];
            for (int p = 0; p < patternCount; p++) {
                seed = nextSeed(seed);
                int length = 1 + (int) (seed % 4);
                StringBuilder pattern = new StringBuilder();
                for (int i = 0; i < length; i++) {
                    seed = nextSeed(seed);
                    pattern.append((char) ('a' + seed % alphabet));
                }
                patterns[p] = pattern.toString();
            }

            String t = text.toString();
            AhoCorasick automaton = new AhoCorasick(patterns);

            for (int p = 0; p < patternCount; p++) {
                int[] expected = naive.findAll(t, patterns[p]);
                int[] actual = automaton.findAll(t, p);
                if (!sameArray(expected, actual)) {
                    disagreements++;
                    if (disagreements <= 3) {
                        System.out.printf("  [FAIL] text=%s pattern=%s expected=%s got=%s%n",
                                          t, patterns[p], render(expected), render(actual));
                    }
                }
            }
        }

        check(cases + " random multi-pattern cases agree with naive", disagreements == 0);
    }

    // --------------------------------------------------------- Suffix array

    private static void testSuffixArrayKnownCases() {
        section("Suffix array: known cases");

        // banana -> a(5), ana(3), anana(1), banana(0), na(4), nana(2)
        check("suffix array of 'banana'",
              sameArray(SuffixArray.build("banana"), new int[] {5, 3, 1, 0, 4, 2}));
        check("suffix array of 'aaaa'",
              sameArray(SuffixArray.build("aaaa"), new int[] {3, 2, 1, 0}));
        check("suffix array of 'abc'",
              sameArray(SuffixArray.build("abc"), new int[] {0, 1, 2}));
        check("suffix array of 'cba'",
              sameArray(SuffixArray.build("cba"), new int[] {2, 1, 0}));
        check("single character", sameArray(SuffixArray.build("x"), new int[] {0}));
        check("empty string", SuffixArray.build("").length == 0);
        check("null rejected", throwsIllegalArgument(() -> SuffixArray.build(null)));
    }

    private static void testSuffixArrayIsAPermutation() {
        section("Suffix array: structural invariants");

        String text = "mississippi banana mississippi";
        int[] suffixArray = SuffixArray.build(text);

        check("length matches text", suffixArray.length == text.length());

        boolean[] seen = new boolean[text.length()];
        boolean valid = true;
        for (int offset : suffixArray) {
            if (offset < 0 || offset >= text.length() || seen[offset]) {
                valid = false;
                break;
            }
            seen[offset] = true;
        }
        check("every offset appears exactly once", valid);

        boolean sorted = true;
        for (int i = 1; i < suffixArray.length; i++) {
            if (text.substring(suffixArray[i - 1]).compareTo(text.substring(suffixArray[i])) >= 0) {
                sorted = false;
                break;
            }
        }
        check("suffixes are strictly ascending", sorted);
    }

    private static void testSuffixArrayCrossValidation() {
        section("Suffix array: cross-validation against brute-force sort");

        long seed = 31337L;
        int cases = 800;
        int disagreements = 0;

        for (int c = 0; c < cases; c++) {
            seed = nextSeed(seed);
            int alphabet = 2 + (int) (seed % 3);
            seed = nextSeed(seed);
            int length = 1 + (int) (seed % 60);

            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < length; i++) {
                seed = nextSeed(seed);
                builder.append((char) ('a' + seed % alphabet));
            }
            String s = builder.toString();

            if (!sameArray(SuffixArray.build(s), naiveSuffixArray(s))) {
                disagreements++;
                if (disagreements <= 3) {
                    System.out.printf("  [FAIL] suffix array mismatch for %s%n", s);
                    System.out.printf("         expected %s got %s%n",
                                      render(naiveSuffixArray(s)), render(SuffixArray.build(s)));
                }
            }
        }

        check(cases + " random suffix arrays match brute force", disagreements == 0);
    }

    // ------------------------------------------------------------ LCP array

    private static void testLcpKnownCases() {
        section("LCP array: known cases");

        String banana = "banana";
        int[] sa = SuffixArray.build(banana);
        // order: a, ana, anana, banana, na, nana
        check("lcp of 'banana'",
              sameArray(LcpArray.build(banana, sa), new int[] {0, 1, 3, 0, 0, 2}));

        check("lcp of 'abc'",
              sameArray(LcpArray.build("abc", SuffixArray.build("abc")), new int[] {0, 0, 0}));
        check("lcp of single char",
              sameArray(LcpArray.build("x", SuffixArray.build("x")), new int[] {0}));
        check("lcp of empty string", LcpArray.build("", new int[0]).length == 0);

        check("null text rejected", throwsIllegalArgument(() -> LcpArray.build(null, new int[0])));
        check("null suffix array rejected", throwsIllegalArgument(() -> LcpArray.build("a", null)));
        check("length mismatch rejected",
              throwsIllegalArgument(() -> LcpArray.build("abc", new int[] {0})));
    }

    private static void testLcpRepeatedPrefixes() {
        section("LCP array: repeated and overlapping prefixes");

        // Fully nested suffixes: a, aa, aaa, aaaa.
        String repeated = "aaaa";
        check("lcp of 'aaaa'",
              sameArray(LcpArray.build(repeated, SuffixArray.build(repeated)),
                        new int[] {0, 1, 2, 3}));

        String mixed = "aabaab";
        int[] sa = SuffixArray.build(mixed);
        int[] lcp = LcpArray.build(mixed, sa);
        check("lcp length matches text", lcp.length == mixed.length());
        check("lcp[0] is zero by convention", lcp[0] == 0);
        check("lcp matches brute force on 'aabaab'", sameArray(lcp, naiveLcp(mixed, sa)));

        // Long run: every adjacent pair overlaps heavily, which is where a
        // rescanning implementation would be quadratic and a broken carry would
        // give wrong answers.
        StringBuilder longRun = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            longRun.append('a');
        }
        String run = longRun.toString();
        int[] runSa = SuffixArray.build(run);
        int[] runLcp = LcpArray.build(run, runSa);
        // For a^n the suffixes are fully nested, so rank i shares exactly i
        // characters with its predecessor: lcp == [0, 1, 2, ..., n-1].
        boolean ascending = true;
        for (int i = 1; i < runLcp.length; i++) {
            if (runLcp[i] != i) {
                ascending = false;
                break;
            }
        }
        check("lcp of a^500 is 0,1,2,...,499", ascending && runLcp[0] == 0);
    }

    private static void testLcpCrossValidation() {
        section("LCP array: cross-validation against pairwise comparison");

        long seed = 987654321L;
        int cases = 800;
        int disagreements = 0;

        for (int c = 0; c < cases; c++) {
            seed = nextSeed(seed);
            int alphabet = 2 + (int) (seed % 3);
            seed = nextSeed(seed);
            int length = 1 + (int) (seed % 60);

            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < length; i++) {
                seed = nextSeed(seed);
                builder.append((char) ('a' + seed % alphabet));
            }
            String s = builder.toString();
            int[] sa = SuffixArray.build(s);

            if (!sameArray(LcpArray.build(s, sa), naiveLcp(s, sa))) {
                disagreements++;
                if (disagreements <= 3) {
                    System.out.printf("  [FAIL] lcp mismatch for %s%n", s);
                }
            }
        }

        check(cases + " random LCP arrays match pairwise comparison", disagreements == 0);
    }

    private static void testDeterminism() {
        section("Determinism");

        String text = "the quick brown fox the lazy dog the end";
        check("suffix array is reproducible",
              sameArray(SuffixArray.build(text), SuffixArray.build(text)));

        int[] sa = SuffixArray.build(text);
        check("lcp array is reproducible",
              sameArray(LcpArray.build(text, sa), LcpArray.build(text, sa)));

        AhoCorasick first = new AhoCorasick(new String[] {"the", "o", "he"});
        AhoCorasick second = new AhoCorasick(new String[] {"the", "o", "he"});
        AhoCorasick.Match[] a = first.findAll(text);
        AhoCorasick.Match[] b = second.findAll(text);
        boolean identical = a.length == b.length;
        for (int i = 0; identical && i < a.length; i++) {
            identical = a[i].patternIndex() == b[i].patternIndex()
                    && a[i].start() == b[i].start()
                    && a[i].end() == b[i].end();
        }
        check("aho-corasick output is reproducible", identical);
    }

    // ------------------------------------------------------- slow references

    /**
     * Brute-force suffix array: sort every suffix with full string comparison.
     * O(n² log n), which is exactly why the real implementation does not do
     * this -- but it is obviously correct, which is what a reference needs.
     *
     * <p>Insertion sort rather than a library sort, both to honour the from
     * scratch rule and because these inputs are tiny.
     */
    private static int[] naiveSuffixArray(String s) {
        int n = s.length();
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        for (int i = 1; i < n; i++) {
            int current = order[i];
            int j = i - 1;
            while (j >= 0 && s.substring(order[j]).compareTo(s.substring(current)) > 0) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = current;
        }
        return order;
    }

    /** Pairwise LCP by direct character comparison. O(n²). */
    private static int[] naiveLcp(String s, int[] suffixArray) {
        int n = s.length();
        int[] lcp = new int[n];
        for (int i = 1; i < n; i++) {
            int a = suffixArray[i - 1];
            int b = suffixArray[i];
            int length = 0;
            while (a + length < n && b + length < n
                    && s.charAt(a + length) == s.charAt(b + length)) {
                length++;
            }
            lcp[i] = length;
        }
        return lcp;
    }

    // --------------------------------------------------------------- helpers

    private static boolean containsMatch(AhoCorasick.Match[] matches, int patternIndex,
                                         int start, int end) {
        for (AhoCorasick.Match match : matches) {
            if (match.patternIndex() == patternIndex && match.start() == start
                    && match.end() == end) {
                return true;
            }
        }
        return false;
    }

    private static boolean endPositionsAscending(AhoCorasick.Match[] matches) {
        for (int i = 1; i < matches.length; i++) {
            if (matches[i - 1].end() > matches[i].end()) {
                return false;
            }
        }
        return true;
    }

    private static boolean throwsIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    /** Same deterministic LCG used by the part-one suite. */
    private static long nextSeed(long seed) {
        return (seed * 6364136223846793005L + 1442695040888963407L) >>> 1;
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
