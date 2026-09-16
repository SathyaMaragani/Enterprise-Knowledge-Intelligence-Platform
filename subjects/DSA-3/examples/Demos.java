package examples;

import texthack.approximation.MakespanScheduling;
import texthack.approximation.VertexCover;
import texthack.core.Prng;
import texthack.engine.CitationFlow;
import texthack.engine.ComplexityRegistry;
import texthack.engine.TextHack;
import texthack.randomized.MillerRabin;
import texthack.randomized.ReservoirSampling;

/**
 * Worked demonstrations of the TextHack modules.
 *
 * <p>Each one prints what it is doing and why the result is what it is, so the
 * output stands on its own as evidence rather than needing the source open
 * beside it.
 *
 * <pre>
 *   java -cp out examples.Demos
 * </pre>
 */
public final class Demos {

    private Demos() {
    }

    public static void main(String[] args) {
        searchDemo();
        queryEngineDemo();
        schedulingDemo();
        primalityDemo();
        citationFlowDemo();
        samplingDemo();
        vertexCoverDemo();
        System.out.println();
        ComplexityRegistry.report();
    }

    private static void banner(String title) {
        System.out.println();
        System.out.println("==========================================");
        System.out.println(" " + title);
        System.out.println("==========================================");
    }

    private static void searchDemo() {
        banner("Pattern search and fuzzy matching");

        String text = "the quick brown fox jumps over the lazy dog";
        System.out.println("text: " + text);
        int[] hits = TextHack.search(text, "the");
        System.out.print("exact 'the' at offsets:");
        for (int hit : hits) {
            System.out.print(" " + hit);
        }
        System.out.println();

        String[] vocabulary = {"receive", "believe", "retrieve", "deceive", "relieve"};
        System.out.println();
        System.out.println("vocabulary: receive believe retrieve deceive relieve");
        System.out.println("fuzzy query 'recieve' within 2 edits:");
        for (TextHack.FuzzyHit hit : TextHack.fuzzy(vocabulary, "recieve", 2)) {
            System.out.printf("  %-10s distance %d  similarity %.3f%n",
                              hit.value(), hit.distance(), hit.similarity());
        }
        System.out.println("  'relieve' ranks first at distance 1 -- a single l/c");
        System.out.println("  substitution. The intended correction 'receive' is a");
        System.out.println("  transposition, which costs 2 under plain Levenshtein.");
        System.out.println("  Damerau-Levenshtein scores that transposition as 1 and");
        System.out.println("  would rank them level, which is precisely why the two");
        System.out.println("  distances are kept as separate algorithms.");

        System.out.println();
        String repeated = "the rain in spain falls mainly in the plain";
        System.out.println("longest repeated substring of:");
        System.out.println("  " + repeated);
        System.out.println("  -> \"" + TextHack.longestRepeatedSubstring(repeated) + "\"");
        System.out.println("  found from the largest LCP entry, since adjacent suffixes");
        System.out.println("  in sorted order share the longest prefixes.");
    }

    private static void queryEngineDemo() {
        banner("Query engine");

        String[] corpus = {
            "network flow algorithms and maximum matching",
            "string matching with suffix arrays",
            "approximation algorithms for scheduling",
            "randomized primality testing"
        };
        TextHack engine = new TextHack(corpus);

        String[] queries = {
            "find \"algorithms\"",
            "findall \"matching\" \"flow\" \"suffix\"",
            "similar \"colour\" \"color\"",
            "prime 7919"
        };

        for (String query : queries) {
            System.out.println();
            System.out.println("> " + query);
            for (String line : engine.run(query)) {
                System.out.println("  " + line);
            }
        }
    }

    private static void schedulingDemo() {
        banner("Scheduling: list order versus LPT");

        int machines = 4;
        long[] jobs = new long[13];
        for (int i = 0; i < 12; i++) {
            jobs[i] = 1;
        }
        jobs[12] = 4;

        System.out.println("12 jobs of length 1, then one job of length 4, on 4 machines.");
        System.out.println("Total work is 16, so a perfect split finishes at 4.");
        System.out.println();

        MakespanScheduling.Schedule list = MakespanScheduling.listScheduling(jobs, machines);
        MakespanScheduling.Schedule lpt = MakespanScheduling.longestProcessingTime(jobs, machines);

        System.out.println("list scheduling makespan: " + list.makespan());
        printLoads("  loads", list.loads());
        System.out.println("LPT makespan:             " + lpt.makespan());
        printLoads("  loads", lpt.loads());
        System.out.println();
        System.out.println("The long job arrives last in list order and lands on an");
        System.out.println("already-full machine. LPT schedules it first, while every");
        System.out.println("machine is still empty, and reaches the optimum.");
        System.out.println("Lower bound: " + MakespanScheduling.lowerBound(jobs, machines));
    }

    private static void printLoads(String label, long[] loads) {
        System.out.print(label + ":");
        for (long load : loads) {
            System.out.print(" " + load);
        }
        System.out.println();
    }

    private static void primalityDemo() {
        banner("Primality: Miller-Rabin versus a Fermat test");

        long[] carmichael = {561L, 1105L, 1729L, 2465L, 6601L};
        System.out.println("Carmichael numbers satisfy Fermat's little theorem for every");
        System.out.println("coprime base, so a Fermat test calls them prime.");
        System.out.println();
        System.out.printf("%-10s %-14s %-14s%n", "n", "fermat base 2", "miller-rabin");
        for (long n : carmichael) {
            boolean fermat = MillerRabin.powMod(2L, n - 1L, n) == 1L;
            boolean millerRabin = MillerRabin.isPrime(n);
            System.out.printf("%-10d %-14s %-14s%n", n,
                              fermat ? "prime" : "composite",
                              millerRabin ? "prime" : "composite");
        }
        System.out.println();
        System.out.println("Miller-Rabin is not fooled: it checks the square roots of 1,");
        System.out.println("and a composite has non-trivial ones that a prime does not.");

        System.out.println();
        System.out.println("2^61 - 1 = 2305843009213693951 is "
                           + (MillerRabin.isPrime(2305843009213693951L) ? "prime" : "composite"));
        System.out.println("  Exact, not probable: the witness set {2,3,...,37} is proven");
        System.out.println("  sufficient across the whole 64-bit range.");
    }

    private static void citationFlowDemo() {
        banner("Citation flow");

        // 0 and 1 are surveys; 4 is a foundational paper they both reach.
        CitationFlow citations = new CitationFlow(6);
        citations.addCitation(0, 1);
        citations.addCitation(0, 2);
        citations.addCitation(1, 3);
        citations.addCitation(2, 3);
        citations.addCitation(2, 4);
        citations.addCitation(3, 5);
        citations.addCitation(4, 5);

        long influence = citations.influence(0, 5);
        System.out.println("citation graph over 6 documents");
        System.out.println("edge-disjoint citation paths from doc 0 to doc 5: " + influence);
        System.out.println("min-cut weight (should match):                    "
                           + citations.bottleneckWeight(0, 5));

        boolean[] sourceSide = citations.bottleneck(0, 5);
        System.out.print("documents on the source side of the bottleneck:");
        for (int i = 0; i < sourceSide.length; i++) {
            if (sourceSide[i]) {
                System.out.print(" " + i);
            }
        }
        System.out.println();
        System.out.println("Citations leaving that set are the ones the connection");
        System.out.println("actually depends on; removing them severs every path.");
    }

    private static void samplingDemo() {
        banner("Reservoir sampling");

        int[] stream = new int[1000];
        for (int i = 0; i < stream.length; i++) {
            stream[i] = i;
        }

        System.out.println("Sampling 5 items from a 1000-item stream in one pass,");
        System.out.println("using O(k) memory and without knowing the length ahead of time.");
        System.out.println();
        for (int trial = 0; trial < 3; trial++) {
            int[] sample = ReservoirSampling.sample(stream, 5, new Prng(trial * 977L + 1L));
            System.out.print("  sample " + (trial + 1) + ":");
            for (int value : sample) {
                System.out.print(" " + value);
            }
            System.out.println();
        }
        System.out.println();
        System.out.println("Late elements appear as often as early ones; each item ends up");
        System.out.println("retained with probability k/n exactly.");
    }

    private static void vertexCoverDemo() {
        banner("Vertex cover: a 2-approximation, and its tightness");

        VertexCover triangle = new VertexCover(3);
        triangle.addEdge(0, 1);
        triangle.addEdge(1, 2);
        triangle.addEdge(0, 2);

        System.out.println("triangle graph (3 vertices, 3 edges)");
        System.out.println("  approximation returns " + triangle.cover().length + " vertices");
        System.out.println("  true optimum is       " + triangle.exactMinimumSize());
        System.out.println();

        VertexCover single = new VertexCover(2);
        single.addEdge(0, 1);
        System.out.println("single edge");
        System.out.println("  approximation returns " + single.cover().length + " vertices");
        System.out.println("  true optimum is       " + single.exactMinimumSize());
        System.out.println();
        System.out.println("Taking both endpoints of each uncovered edge is what makes the");
        System.out.println("factor-2 bound provable, and it is tight: here it is exactly 2x.");
    }
}
