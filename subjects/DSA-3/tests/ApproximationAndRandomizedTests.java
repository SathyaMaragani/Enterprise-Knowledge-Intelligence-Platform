package tests;

import texthack.approximation.MakespanScheduling;
import texthack.approximation.VertexCover;
import texthack.core.Prng;
import texthack.randomized.MillerRabin;
import texthack.randomized.ReservoirSampling;
import texthack.randomized.UniversalHashing;

/**
 * Self-checking suite for the approximation and randomized modules.
 *
 * <p>Approximation algorithms are checked against brute-force optima on small
 * instances, so the proven ratios are demonstrated rather than merely quoted in
 * a comment. Randomized algorithms are checked both for correctness and for
 * their distributional guarantees -- a reservoir sampler with an off-by-one in
 * its index draw still returns plausible-looking samples, and only a
 * distribution check catches it.
 *
 * <p>Every random source is explicitly seeded, so these tests are deterministic
 * and a failure is reproducible.
 */
public final class ApproximationAndRandomizedTests {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testPrngDeterminism();
        testPrngRanges();
        testPrngUniformity();

        testVertexCoverKnownCases();
        testVertexCoverAlwaysCovers();
        testVertexCoverTwoApproximation();

        testMakespanKnownCases();
        testMakespanValidSchedules();
        testMakespanApproximationRatios();
        testLptBeatsListOnItsWorstCase();

        testMillerRabinSmallNumbers();
        testMillerRabinCarmichaelNumbers();
        testMillerRabinLargePrimes();
        testMillerRabinAgainstTrialDivision();
        testMillerRabinModularArithmetic();
        testMillerRabinValidation();

        testUniversalHashingRange();
        testUniversalHashingDeterminismAndVariety();
        testUniversalHashingDistribution();

        testReservoirShortStreams();
        testReservoirContentsComeFromStream();
        testReservoirUniformity();
        testReservoirValidation();

        System.out.println();
        System.out.println("==========================================");
        System.out.printf(" Test Summary: %d passed, %d failed%n", passed, failed);
        System.out.println("==========================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ Prng

    private static void testPrngDeterminism() {
        section("Prng: determinism");

        Prng a = new Prng(12345L);
        Prng b = new Prng(12345L);
        boolean identical = true;
        for (int i = 0; i < 1000; i++) {
            if (a.nextLong() != b.nextLong()) {
                identical = false;
                break;
            }
        }
        check("same seed gives the same sequence", identical);

        Prng c = new Prng(12345L);
        Prng d = new Prng(54321L);
        boolean differs = false;
        for (int i = 0; i < 20; i++) {
            if (c.nextLong() != d.nextLong()) {
                differs = true;
                break;
            }
        }
        check("different seeds diverge", differs);
    }

    private static void testPrngRanges() {
        section("Prng: ranges");

        Prng random = new Prng(99L);
        boolean intInRange = true;
        boolean longInRange = true;
        boolean doubleInRange = true;

        for (int i = 0; i < 10000; i++) {
            int value = random.nextInt(17);
            if (value < 0 || value >= 17) {
                intInRange = false;
            }
            long longValue = random.nextLong(1000L);
            if (longValue < 0 || longValue >= 1000L) {
                longInRange = false;
            }
            double d = random.nextDouble();
            if (d < 0.0 || d >= 1.0) {
                doubleInRange = false;
            }
        }

        check("nextInt stays within its bound", intInRange);
        check("nextLong stays within its bound", longInRange);
        check("nextDouble stays in [0,1)", doubleInRange);
        check("nextInt(1) is always 0", new Prng(1L).nextInt(1) == 0);

        check("non-positive int bound rejected",
              throwsIllegalArgument(() -> new Prng(1L).nextInt(0)));
        check("non-positive long bound rejected",
              throwsIllegalArgument(() -> new Prng(1L).nextLong(-5L)));
    }

    private static void testPrngUniformity() {
        section("Prng: rough uniformity");

        int buckets = 10;
        int draws = 100000;
        int[] counts = new int[buckets];
        Prng random = new Prng(2026L);

        for (int i = 0; i < draws; i++) {
            counts[random.nextInt(buckets)]++;
        }

        int expected = draws / buckets;
        boolean balanced = true;
        for (int count : counts) {
            // Generous band: this is a smoke test for gross bias, not a
            // statistical certification.
            if (count < expected * 0.9 || count > expected * 1.1) {
                balanced = false;
            }
        }
        check("100k draws spread evenly across 10 buckets", balanced);

        int trueCount = 0;
        Prng coin = new Prng(7L);
        for (int i = 0; i < 100000; i++) {
            if (coin.nextBoolean()) {
                trueCount++;
            }
        }
        check("nextBoolean is roughly fair", trueCount > 48000 && trueCount < 52000);
    }

    // ---------------------------------------------------------- VertexCover

    private static void testVertexCoverKnownCases() {
        section("VertexCover: known cases and tightness");

        VertexCover empty = new VertexCover(5);
        check("no edges needs no cover", empty.cover().length == 0);

        // The bound is tight here: one vertex suffices, the algorithm takes two.
        VertexCover single = new VertexCover(2);
        single.addEdge(0, 1);
        check("single edge takes both endpoints", single.cover().length == 2);
        check("single edge optimum is 1", single.exactMinimumSize() == 1);

        // Triangle: optimum 2, algorithm takes all 3.
        VertexCover triangle = new VertexCover(3);
        triangle.addEdge(0, 1);
        triangle.addEdge(1, 2);
        triangle.addEdge(0, 2);
        check("triangle cover is valid", triangle.isCover(triangle.cover()));
        check("triangle optimum is 2", triangle.exactMinimumSize() == 2);

        // A star: one centre covers everything, but the matching picks a pair.
        VertexCover star = new VertexCover(5);
        star.addEdge(0, 1);
        star.addEdge(0, 2);
        star.addEdge(0, 3);
        star.addEdge(0, 4);
        check("star optimum is 1", star.exactMinimumSize() == 1);
        check("star cover is valid", star.isCover(star.cover()));

        check("an incomplete set is rejected as a cover", !single.isCover(new int[0]));
    }

    private static void testVertexCoverAlwaysCovers() {
        section("VertexCover: result always covers every edge");

        long seed = 4321L;
        int cases = 500;
        int invalid = 0;

        for (int c = 0; c < cases; c++) {
            seed = nextSeed(seed);
            int vertices = 2 + (int) (seed % 10);
            seed = nextSeed(seed);
            int edges = (int) (seed % 20);

            VertexCover graph = new VertexCover(vertices);
            for (int i = 0; i < edges; i++) {
                seed = nextSeed(seed);
                int u = (int) (seed % vertices);
                seed = nextSeed(seed);
                int v = (int) (seed % vertices);
                graph.addEdge(u, v);
            }

            if (!graph.isCover(graph.cover())) {
                invalid++;
            }
        }

        check(cases + " random graphs produce valid covers", invalid == 0);
    }

    private static void testVertexCoverTwoApproximation() {
        section("VertexCover: 2-approximation verified against brute force");

        long seed = 8080L;
        int cases = 300;
        int violations = 0;
        int strictlyWorse = 0;

        for (int c = 0; c < cases; c++) {
            seed = nextSeed(seed);
            int vertices = 2 + (int) (seed % 8); // small enough to brute force
            seed = nextSeed(seed);
            int edges = 1 + (int) (seed % 12);

            VertexCover graph = new VertexCover(vertices);
            for (int i = 0; i < edges; i++) {
                seed = nextSeed(seed);
                int u = (int) (seed % vertices);
                seed = nextSeed(seed);
                int v = (int) (seed % vertices);
                if (u != v) {
                    graph.addEdge(u, v);
                }
            }

            int approximate = graph.cover().length;
            int optimum = graph.exactMinimumSize();

            if (approximate > 2 * optimum) {
                violations++;
                if (violations <= 3) {
                    System.out.printf("  [FAIL] approx=%d optimum=%d%n", approximate, optimum);
                }
            }
            if (approximate > optimum) {
                strictlyWorse++;
            }
        }

        check(cases + " graphs respect approx <= 2 * optimum", violations == 0);
        check("the approximation is genuinely suboptimal sometimes", strictlyWorse > 0);
    }

    // ----------------------------------------------------- MakespanScheduling

    private static void testMakespanKnownCases() {
        section("MakespanScheduling: known cases");

        MakespanScheduling.Schedule even =
            MakespanScheduling.longestProcessingTime(new long[] {3, 3, 3}, 3);
        check("three equal jobs on three machines finish at 3", even.makespan() == 3);

        MakespanScheduling.Schedule single =
            MakespanScheduling.listScheduling(new long[] {5, 5, 5}, 1);
        check("one machine runs everything", single.makespan() == 15);

        MakespanScheduling.Schedule none =
            MakespanScheduling.listScheduling(new long[0], 3);
        check("no jobs means zero makespan", none.makespan() == 0);

        check("lower bound is the longest job when it dominates",
              MakespanScheduling.lowerBound(new long[] {10, 1, 1}, 4) == 10);
        check("lower bound is the average when work dominates",
              MakespanScheduling.lowerBound(new long[] {4, 4, 4, 4}, 2) == 8);
    }

    private static void testMakespanValidSchedules() {
        section("MakespanScheduling: schedules are well formed");

        long seed = 606L;
        int cases = 400;
        int invalid = 0;

        for (int c = 0; c < cases; c++) {
            seed = nextSeed(seed);
            int jobCount = (int) (seed % 12);
            seed = nextSeed(seed);
            int machines = 1 + (int) (seed % 4);

            long[] jobs = new long[jobCount];
            long total = 0;
            for (int i = 0; i < jobCount; i++) {
                seed = nextSeed(seed);
                jobs[i] = seed % 20;
                total += jobs[i];
            }

            MakespanScheduling.Schedule schedule =
                MakespanScheduling.longestProcessingTime(jobs, machines);

            long loadSum = 0;
            for (long load : schedule.loads()) {
                loadSum += load;
            }
            if (loadSum != total) {
                invalid++;
                continue;
            }
            // Every job must land on a real machine.
            for (int machine : schedule.assignment()) {
                if (machine < 0 || machine >= machines) {
                    invalid++;
                    break;
                }
            }
            // The makespan cannot beat the lower bound.
            if (jobCount > 0 && schedule.makespan() < MakespanScheduling.lowerBound(jobs, machines)) {
                invalid++;
            }
        }

        check(cases + " schedules assign every job and conserve total work", invalid == 0);
    }

    private static void testMakespanApproximationRatios() {
        section("MakespanScheduling: ratios verified against brute-force optima");

        long seed = 31415L;
        int cases = 200;
        int listViolations = 0;
        int lptViolations = 0;

        for (int c = 0; c < cases; c++) {
            seed = nextSeed(seed);
            int jobCount = 1 + (int) (seed % 7); // brute force is m^n
            seed = nextSeed(seed);
            int machines = 1 + (int) (seed % 3);

            long[] jobs = new long[jobCount];
            for (int i = 0; i < jobCount; i++) {
                seed = nextSeed(seed);
                jobs[i] = 1 + seed % 15;
            }

            long optimum = exactMakespan(jobs, machines);
            long list = MakespanScheduling.listScheduling(jobs, machines).makespan();
            long lpt = MakespanScheduling.longestProcessingTime(jobs, machines).makespan();

            // Compared against the true optimum, which is the form the theorems
            // are stated in. Comparing against the lower bound instead would be
            // a stronger claim than either theorem makes.
            double listBound = (2.0 - 1.0 / machines) * optimum;
            double lptBound = (4.0 / 3.0 - 1.0 / (3.0 * machines)) * optimum;

            if (list > listBound + 1e-9) {
                listViolations++;
                if (listViolations <= 3) {
                    System.out.printf("  [FAIL] list=%d optimum=%d bound=%.3f%n",
                                      list, optimum, listBound);
                }
            }
            if (lpt > lptBound + 1e-9) {
                lptViolations++;
                if (lptViolations <= 3) {
                    System.out.printf("  [FAIL] lpt=%d optimum=%d bound=%.3f%n",
                                      lpt, optimum, lptBound);
                }
            }
        }

        check(cases + " instances respect the list-scheduling 2 - 1/m bound", listViolations == 0);
        check(cases + " instances respect the LPT 4/3 - 1/3m bound", lptViolations == 0);
    }

    private static void testLptBeatsListOnItsWorstCase() {
        section("MakespanScheduling: LPT on the case that defeats list order");

        // m machines, m(m-1) unit jobs, then one job of length m. In this order
        // the long job arrives last and strands a machine.
        int m = 4;
        int unitJobs = m * (m - 1);
        long[] jobs = new long[unitJobs + 1];
        for (int i = 0; i < unitJobs; i++) {
            jobs[i] = 1;
        }
        jobs[unitJobs] = m;

        long list = MakespanScheduling.listScheduling(jobs, m).makespan();
        long lpt = MakespanScheduling.longestProcessingTime(jobs, m).makespan();
        long optimum = exactMakespanGreedyBound(jobs, m);

        check("list scheduling is hurt by the trailing long job", list > lpt);
        check("LPT reaches the optimum here", lpt == optimum);
    }

    // ------------------------------------------------------------ MillerRabin

    private static void testMillerRabinSmallNumbers() {
        section("MillerRabin: small numbers");

        check("0 is not prime", !MillerRabin.isPrime(0));
        check("1 is not prime", !MillerRabin.isPrime(1));
        check("2 is prime", MillerRabin.isPrime(2));
        check("3 is prime", MillerRabin.isPrime(3));
        check("4 is not prime", !MillerRabin.isPrime(4));
        check("17 is prime", MillerRabin.isPrime(17));
        check("25 is not prime", !MillerRabin.isPrime(25));
        check("97 is prime", MillerRabin.isPrime(97));
    }

    private static void testMillerRabinCarmichaelNumbers() {
        section("MillerRabin: Carmichael numbers");

        // These satisfy Fermat's little theorem for every coprime base, so a
        // Fermat test declares them prime. Miller-Rabin must not.
        long[] carmichael = {561L, 1105L, 1729L, 2465L, 2821L, 6601L, 8911L, 10585L, 15841L, 29341L};
        boolean allComposite = true;
        for (long n : carmichael) {
            if (MillerRabin.isPrime(n)) {
                allComposite = false;
                System.out.printf("  [FAIL] Carmichael number %d reported prime%n", n);
            }
        }
        check("all 10 Carmichael numbers reported composite", allComposite);
    }

    private static void testMillerRabinLargePrimes() {
        section("MillerRabin: larger values");

        check("7919 is prime", MillerRabin.isPrime(7919L));
        check("104729 is prime", MillerRabin.isPrime(104729L));
        check("1000003 is prime", MillerRabin.isPrime(1000003L));
        check("999999937 is prime", MillerRabin.isPrime(999999937L));
        check("2147483647 is prime (Mersenne)", MillerRabin.isPrime(2147483647L));
        check("2147483647^2 style composite rejected", !MillerRabin.isPrime(1000003L * 7919L));
        check("999999937 * 2 is composite", !MillerRabin.isPrime(999999937L * 2L));

        // Values far beyond the range where a naive (a*b) % m stays correct.
        // These are real assertions: the earlier version of this check compared
        // isPrime(x) against !isPrime(x), which is a tautology and proved only
        // that no exception was thrown.
        check("2^61 - 1 (Mersenne prime M61) is prime",
              MillerRabin.isPrime(2305843009213693951L));

        // Composite by construction -- a product of two integers above 1 --
        // so this holds regardless of whether the factors are themselves prime.
        long hugeComposite = 999999937L * 4294967291L;
        check("a ~4.3e18 composite is rejected", !MillerRabin.isPrime(hugeComposite));
        check("that composite really is in the safe range",
              hugeComposite > 0 && hugeComposite < Long.MAX_VALUE / 2);
    }

    private static void testMillerRabinAgainstTrialDivision() {
        section("MillerRabin: cross-validation against trial division");

        int limit = 20000;
        int disagreements = 0;
        for (int n = 0; n <= limit; n++) {
            if (MillerRabin.isPrime(n) != isPrimeByTrialDivision(n)) {
                disagreements++;
                if (disagreements <= 3) {
                    System.out.printf("  [FAIL] disagreement at %d%n", n);
                }
            }
        }
        check("every n in 0.." + limit + " matches trial division", disagreements == 0);

        // The probabilistic variant must agree with the deterministic one.
        Prng random = new Prng(2718L);
        int probabilisticDisagreements = 0;
        for (int n = 5; n < 3000; n++) {
            if (MillerRabin.isProbablePrime(n, 12, random) != isPrimeByTrialDivision(n)) {
                probabilisticDisagreements++;
            }
        }
        check("probabilistic variant agrees over 5..3000", probabilisticDisagreements == 0);
    }

    private static void testMillerRabinModularArithmetic() {
        section("MillerRabin: modular arithmetic helpers");

        // Where no overflow is possible, compare against the direct computation.
        long seed = 161803L;
        int disagreements = 0;
        for (int i = 0; i < 5000; i++) {
            seed = nextSeed(seed);
            long modulus = 2 + seed % 100000L;
            seed = nextSeed(seed);
            long a = seed % modulus;
            seed = nextSeed(seed);
            long b = seed % modulus;

            if (MillerRabin.mulMod(a, b, modulus) != (a * b) % modulus) {
                disagreements++;
            }
        }
        check("mulMod matches direct multiplication where it is safe", disagreements == 0);

        check("powMod: 2^10 mod 1000 is 24", MillerRabin.powMod(2L, 10L, 1000L) == 24L);
        check("powMod: anything^0 is 1", MillerRabin.powMod(12345L, 0L, 97L) == 1L);
        check("powMod: Fermat holds for a prime modulus",
              MillerRabin.powMod(3L, 96L, 97L) == 1L);

        // The case a naive implementation gets wrong.
        long huge = 4000000000000000000L;
        long product = MillerRabin.mulMod(huge, huge, 4611686018427387847L);
        check("mulMod survives operands that would overflow a long",
              product >= 0 && product < 4611686018427387847L);
    }

    private static void testMillerRabinValidation() {
        section("MillerRabin: validation");

        check("negative rejected", throwsIllegalArgument(() -> MillerRabin.isPrime(-1L)));
        check("beyond safe modulus rejected",
              throwsIllegalArgument(() -> MillerRabin.isPrime(Long.MAX_VALUE)));
        check("zero rounds rejected",
              throwsIllegalArgument(() -> MillerRabin.isProbablePrime(101L, 0, new Prng(1L))));
        check("null random rejected",
              throwsIllegalArgument(() -> MillerRabin.isProbablePrime(101L, 5, null)));
    }

    // ------------------------------------------------------- UniversalHashing

    private static void testUniversalHashingRange() {
        section("UniversalHashing: range and validation");

        Prng random = new Prng(555L);
        UniversalHashing hash = new UniversalHashing(16, random);

        boolean inRange = true;
        for (int i = -5000; i < 5000; i++) {
            int h = hash.hash(i);
            if (h < 0 || h >= 16) {
                inRange = false;
                break;
            }
        }
        check("integer hashes stay within the bucket range", inRange);

        boolean stringsInRange = true;
        for (int i = 0; i < 2000; i++) {
            int h = hash.hash("key-" + i);
            if (h < 0 || h >= 16) {
                stringsInRange = false;
                break;
            }
        }
        check("string hashes stay within the bucket range", stringsInRange);

        check("multiplier is never zero", hash.multiplier() != 0);
        check("single bucket maps everything to 0",
              new UniversalHashing(1, new Prng(3L)).hash(999) == 0);

        check("zero buckets rejected",
              throwsIllegalArgument(() -> new UniversalHashing(0, new Prng(1L))));
        check("null random rejected",
              throwsIllegalArgument(() -> new UniversalHashing(4, null)));
        check("null key rejected",
              throwsIllegalArgument(() -> new UniversalHashing(4, new Prng(1L)).hash((String) null)));
    }

    private static void testUniversalHashingDeterminismAndVariety() {
        section("UniversalHashing: determinism and family variety");

        UniversalHashing hash = new UniversalHashing(64, new Prng(11L));
        check("same instance is deterministic on ints", hash.hash(42) == hash.hash(42));
        check("same instance is deterministic on strings",
              hash.hash("stable") == hash.hash("stable"));

        // Different draws from the family should behave differently, or the
        // randomisation would be pointless.
        UniversalHashing other = new UniversalHashing(64, new Prng(999L));
        int differences = 0;
        for (int i = 0; i < 500; i++) {
            if (hash.hash(i) != other.hash(i)) {
                differences++;
            }
        }
        check("two draws from the family differ on most keys", differences > 300);
    }

    private static void testUniversalHashingDistribution() {
        section("UniversalHashing: collision behaviour");

        int buckets = 32;
        int keys = 6400;
        UniversalHashing hash = new UniversalHashing(buckets, new Prng(24680L));

        int[] counts = new int[buckets];
        for (int i = 0; i < keys; i++) {
            counts[hash.hash(i)]++;
        }

        int expected = keys / buckets;
        int worst = 0;
        for (int count : counts) {
            if (count > worst) {
                worst = count;
            }
        }
        // A degenerate function would pile everything into one bucket; a good
        // one stays within a small multiple of the mean.
        check("no bucket collects a pathological share", worst < expected * 3);

        int emptyBuckets = 0;
        for (int count : counts) {
            if (count == 0) {
                emptyBuckets++;
            }
        }
        check("buckets are broadly used", emptyBuckets < buckets / 4);
    }

    // ------------------------------------------------------ ReservoirSampling

    private static void testReservoirShortStreams() {
        section("ReservoirSampling: short streams");

        Prng random = new Prng(1234L);

        int[] shorter = ReservoirSampling.sample(new int[] {1, 2, 3}, 5, random);
        check("stream shorter than k returns everything", shorter.length == 3);

        int[] exact = ReservoirSampling.sample(new int[] {1, 2, 3, 4, 5}, 5, random);
        check("stream equal to k returns everything", exact.length == 5);

        int[] longer = ReservoirSampling.sample(new int[] {1, 2, 3, 4, 5, 6, 7, 8}, 3, random);
        check("stream longer than k returns exactly k", longer.length == 3);

        ReservoirSampling sampler = new ReservoirSampling(2, new Prng(1L));
        check("empty stream yields nothing", sampler.sample().length == 0);
        check("seen starts at zero", sampler.seen() == 0);
        sampler.offer(7);
        check("seen counts offers", sampler.seen() == 1);
        check("capacity is reported", sampler.capacity() == 2);
    }

    private static void testReservoirContentsComeFromStream() {
        section("ReservoirSampling: contents are drawn from the stream");

        long seed = 90909L;
        int cases = 300;
        int invalid = 0;

        for (int c = 0; c < cases; c++) {
            seed = nextSeed(seed);
            int length = 1 + (int) (seed % 40);
            seed = nextSeed(seed);
            int k = 1 + (int) (seed % 8);

            int[] stream = new int[length];
            for (int i = 0; i < length; i++) {
                stream[i] = i * 7;
            }

            int[] sample = ReservoirSampling.sample(stream, k, new Prng(seed));
            int expectedSize = length < k ? length : k;
            if (sample.length != expectedSize) {
                invalid++;
                continue;
            }
            for (int value : sample) {
                if (value % 7 != 0 || value / 7 >= length) {
                    invalid++;
                    break;
                }
            }
        }

        check(cases + " samples have the right size and come from the stream", invalid == 0);
    }

    private static void testReservoirUniformity() {
        section("ReservoirSampling: uniformity");

        // Sampling 1 item from 10 should pick each with probability 1/10. An
        // implementation drawing j from [0, i) instead of [0, i] biases this
        // heavily towards early elements, and nothing but a distribution check
        // would notice.
        int streamLength = 10;
        int trials = 40000;
        int[] counts = new int[streamLength];
        Prng random = new Prng(13131L);

        int[] stream = new int[streamLength];
        for (int i = 0; i < streamLength; i++) {
            stream[i] = i;
        }

        for (int t = 0; t < trials; t++) {
            int[] sample = ReservoirSampling.sample(stream, 1, random);
            counts[sample[0]]++;
        }

        int expected = trials / streamLength;
        boolean uniform = true;
        int lowest = trials;
        int highest = 0;
        for (int count : counts) {
            if (count < expected * 0.85 || count > expected * 1.15) {
                uniform = false;
            }
            if (count < lowest) {
                lowest = count;
            }
            if (count > highest) {
                highest = count;
            }
        }
        check("each of 10 elements is chosen about a tenth of the time "
              + "(low=" + lowest + " high=" + highest + " expected=" + expected + ")", uniform);

        // Same check for k = 3 out of 12.
        int[] bigStream = new int[12];
        for (int i = 0; i < 12; i++) {
            bigStream[i] = i;
        }
        int[] appearances = new int[12];
        int bigTrials = 20000;
        for (int t = 0; t < bigTrials; t++) {
            for (int value : ReservoirSampling.sample(bigStream, 3, random)) {
                appearances[value]++;
            }
        }
        int expectedAppearances = bigTrials * 3 / 12;
        boolean uniformK = true;
        for (int count : appearances) {
            if (count < expectedAppearances * 0.85 || count > expectedAppearances * 1.15) {
                uniformK = false;
            }
        }
        check("sampling 3 of 12 includes each element about a quarter of the time", uniformK);
    }

    private static void testReservoirValidation() {
        section("ReservoirSampling: validation");

        check("zero capacity rejected",
              throwsIllegalArgument(() -> new ReservoirSampling(0, new Prng(1L))));
        check("null random rejected",
              throwsIllegalArgument(() -> new ReservoirSampling(3, null)));
        check("null stream rejected",
              throwsIllegalArgument(() -> ReservoirSampling.sample(null, 2, new Prng(1L))));
    }

    // ------------------------------------------------------- slow references

    /** Trial division up to the square root. Obviously correct, obviously slow. */
    private static boolean isPrimeByTrialDivision(long n) {
        if (n < 2) {
            return false;
        }
        for (long d = 2; d * d <= n; d++) {
            if (n % d == 0) {
                return false;
            }
        }
        return true;
    }

    /** Exact minimum makespan by exhaustive assignment. O(machines^jobs). */
    private static long exactMakespan(long[] jobs, int machines) {
        long[] loads = new long[machines];
        return searchMakespan(jobs, 0, loads);
    }

    private static long searchMakespan(long[] jobs, int index, long[] loads) {
        if (index == jobs.length) {
            long worst = 0;
            for (long load : loads) {
                if (load > worst) {
                    worst = load;
                }
            }
            return worst;
        }

        long best = Long.MAX_VALUE;
        for (int m = 0; m < loads.length; m++) {
            // Machines with equal load are interchangeable; trying only the
            // first keeps the search from exploring identical branches.
            boolean duplicate = false;
            for (int earlier = 0; earlier < m; earlier++) {
                if (loads[earlier] == loads[m]) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) {
                continue;
            }

            loads[m] += jobs[index];
            long candidate = searchMakespan(jobs, index + 1, loads);
            loads[m] -= jobs[index];
            if (candidate < best) {
                best = candidate;
            }
        }
        return best;
    }

    /** Exact makespan for the structured worst-case instance. */
    private static long exactMakespanGreedyBound(long[] jobs, int machines) {
        return exactMakespan(jobs, machines);
    }

    // --------------------------------------------------------------- helpers

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
