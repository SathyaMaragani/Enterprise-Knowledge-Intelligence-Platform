package benchmarks;

import texthack.core.Prng;
import texthack.dp.Levenshtein;
import texthack.engine.ComplexityRegistry;
import texthack.string.AhoCorasick;
import texthack.string.KmpSearch;
import texthack.string.NaiveSearch;
import texthack.string.RabinKarpSearch;
import texthack.string.SuffixArray;
import texthack.string.ZSearch;

/**
 * Benchmark harness for the TextHack algorithms.
 *
 * <h2>What this is, and is not</h2>
 * It measures wall-clock time on generated inputs of doubling size and reports
 * the growth ratio, so measured behaviour can be compared against the documented
 * bound. Doubling n and seeing time roughly double indicates linear growth;
 * roughly quadruple indicates quadratic.
 *
 * <p>It is <em>not</em> a rigorous JMH-style microbenchmark. The JVM warms up,
 * JIT compilation happens mid-run, and garbage collection is not controlled. A
 * warm-up pass is run and the best of several repetitions is taken, which is
 * enough to see growth trends but not enough to compare two implementations
 * differing by a few percent. Saying so matters more than producing
 * confident-looking numbers that do not survive scrutiny.
 *
 * <pre>
 *   java -cp out benchmarks.Benchmark
 * </pre>
 */
public final class Benchmark {

    private static final int WARMUP_ROUNDS = 3;
    private static final int MEASURED_ROUNDS = 5;

    private Benchmark() {
    }

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println(" TextHack Benchmarks");
        System.out.println("==========================================");
        System.out.println("Best of " + MEASURED_ROUNDS + " runs after "
                           + WARMUP_ROUNDS + " warm-up rounds.");
        System.out.println("Growth ratio compares each size against the previous one.");
        System.out.println();

        benchmarkExactMatchers();
        benchmarkMultiPattern();
        benchmarkSuffixArray();
        benchmarkEditDistance();

        System.out.println();
        ComplexityRegistry.report();
    }

    // ------------------------------------------------------------- suites

    private static void benchmarkExactMatchers() {
        System.out.println("--- Exact single-pattern search ---");
        System.out.printf("%-14s %10s %12s %12s %12s%n",
                          "text length", "naive", "kmp", "z", "rabin-karp");

        int[] sizes = {20000, 40000, 80000, 160000};
        for (int size : sizes) {
            String text = repeat('a', size);
            String pattern = repeat('a', 40) + "b"; // naive's worst case

            long naive = time(() -> new NaiveSearch().findAll(text, pattern));
            long kmp = time(() -> new KmpSearch().findAll(text, pattern));
            long z = time(() -> new ZSearch().findAll(text, pattern));
            long rk = time(() -> new RabinKarpSearch().findAll(text, pattern));

            System.out.printf("%-14d %10s %12s %12s %12s%n",
                              size, micros(naive), micros(kmp), micros(z), micros(rk));
        }
        System.out.println("  Pattern a^40 b against a^n is the case that separates them:");
        System.out.println("  naive rescans every alignment, the linear matchers do not.");
        System.out.println();
    }

    private static void benchmarkMultiPattern() {
        System.out.println("--- Multi-pattern: Aho-Corasick versus repeated KMP ---");
        System.out.printf("%-14s %14s %14s %10s%n", "patterns", "aho-corasick", "kmp x k", "speedup");

        String text = randomText(200000, 4, 11L);
        int[] patternCounts = {5, 20, 80};

        for (int count : patternCounts) {
            String[] patterns = randomPatterns(count, 6, 4, 22L + count);

            long aho = time(() -> new AhoCorasick(patterns).findAll(text));
            long repeated = time(() -> {
                KmpSearch matcher = new KmpSearch();
                for (String pattern : patterns) {
                    matcher.findAll(text, pattern);
                }
            });

            double speedup = repeated == 0 ? 0 : (double) repeated / (aho == 0 ? 1 : aho);
            System.out.printf("%-14d %14s %14s %9.1fx%n",
                              count, micros(aho), micros(repeated), speedup);
        }
        System.out.println("  Aho-Corasick's cost is flat in the pattern count; running a");
        System.out.println("  single-pattern matcher k times is not.");
        System.out.println();
    }

    private static void benchmarkSuffixArray() {
        System.out.println("--- Suffix array construction ---");
        System.out.printf("%-14s %12s %10s%n", "text length", "time", "growth");

        int[] sizes = {25000, 50000, 100000, 200000};
        long previous = 0;
        for (int size : sizes) {
            String text = randomText(size, 4, 99L);
            long elapsed = time(() -> SuffixArray.build(text));
            String growth = previous == 0 ? "-"
                          : String.format("%.2fx", (double) elapsed / previous);
            System.out.printf("%-14d %12s %10s%n", size, micros(elapsed), growth);
            previous = elapsed;
        }
        System.out.println("  O(n log n): doubling n should roughly double the time,");
        System.out.println("  plus a little for the extra doubling round.");
        System.out.println();
    }

    private static void benchmarkEditDistance() {
        System.out.println("--- Levenshtein distance ---");
        System.out.printf("%-14s %12s %10s%n", "string length", "time", "growth");

        int[] sizes = {250, 500, 1000, 2000};
        long previous = 0;
        for (int size : sizes) {
            String a = randomText(size, 4, 7L);
            String b = randomText(size, 4, 8L);
            long elapsed = time(() -> Levenshtein.distance(a, b));
            String growth = previous == 0 ? "-"
                          : String.format("%.2fx", (double) elapsed / previous);
            System.out.printf("%-14d %12s %10s%n", size, micros(elapsed), growth);
            previous = elapsed;
        }
        System.out.println("  O(n*m): doubling both strings quadruples the work,");
        System.out.println("  so a growth near 4x is the expected shape.");
        System.out.println();
    }

    // -------------------------------------------------------------- timing

    /** Best-of timing in nanoseconds, after warm-up. */
    private static long time(Runnable action) {
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            action.run();
        }
        long best = Long.MAX_VALUE;
        for (int i = 0; i < MEASURED_ROUNDS; i++) {
            long start = System.nanoTime();
            action.run();
            long elapsed = System.nanoTime() - start;
            if (elapsed < best) {
                best = elapsed;
            }
        }
        return best;
    }

    private static String micros(long nanos) {
        return String.format("%.1f us", nanos / 1000.0);
    }

    // --------------------------------------------------------- generators

    private static String repeat(char c, int count) {
        StringBuilder out = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            out.append(c);
        }
        return out.toString();
    }

    private static String randomText(int length, int alphabet, long seed) {
        Prng random = new Prng(seed);
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            out.append((char) ('a' + random.nextInt(alphabet)));
        }
        return out.toString();
    }

    private static String[] randomPatterns(int count, int length, int alphabet, long seed) {
        Prng random = new Prng(seed);
        String[] patterns = new String[count];
        for (int i = 0; i < count; i++) {
            StringBuilder out = new StringBuilder(length);
            for (int j = 0; j < length; j++) {
                out.append((char) ('a' + random.nextInt(alphabet)));
            }
            patterns[i] = out.toString();
        }
        return patterns;
    }
}
