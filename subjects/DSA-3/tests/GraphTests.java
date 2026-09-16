package tests;

import texthack.graph.BipartiteMatching;
import texthack.graph.Dinic;
import texthack.graph.EdmondsKarp;
import texthack.graph.FlowNetwork;
import texthack.graph.FordFulkerson;

/**
 * Self-checking suite for the graph and network-flow module.
 *
 * <p>The strongest check here is that three independently written max-flow
 * algorithms must agree on every randomly generated network. They share only the
 * {@link FlowNetwork} structure, so a disagreement localises the bug immediately
 * -- and agreement across three different strategies is much harder to achieve
 * by coincidence than any single implementation passing fixed cases.
 *
 * <p>Beyond that, every computed flow is checked against two properties that
 * hold for any correct answer regardless of algorithm: flow conservation at
 * intermediate vertices, and the max-flow min-cut theorem.
 */
public final class GraphTests {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testFlowNetworkBasics();
        testKnownMaxFlow();
        testFordFulkersonPathologicalCase();
        testAlgorithmsAgreeOnRandomNetworks();
        testFlowConservation();
        testMaxFlowMinCut();
        testDegenerateNetworks();
        testBipartiteKnownCases();
        testBipartiteMatchesMaxFlow();
        testInputValidation();

        System.out.println();
        System.out.println("==========================================");
        System.out.printf(" Test Summary: %d passed, %d failed%n", passed, failed);
        System.out.println("==========================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------- FlowNetwork

    private static void testFlowNetworkBasics() {
        section("FlowNetwork");

        FlowNetwork network = new FlowNetwork(3);
        int e = network.addEdge(0, 1, 5L);

        check("adding an edge creates a reverse pair", network.edgeCount() == 2);
        check("forward capacity", network.residual(e) == 5L);
        check("reverse starts empty", network.residual(FlowNetwork.reverse(e)) == 0L);
        check("reverse of reverse is the original", FlowNetwork.reverse(FlowNetwork.reverse(e)) == e);
        check("edge head", network.to(e) == 1);

        network.push(e, 2L);
        check("pushing reduces forward capacity", network.residual(e) == 3L);
        check("pushing credits the reverse", network.residual(FlowNetwork.reverse(e)) == 2L);

        // A copy must not observe later mutation of the original.
        FlowNetwork clone = network.copy();
        network.push(e, 1L);
        check("copy is independent", clone.residual(e) == 3L);

        check("vertex count", network.vertexCount() == 3);
        check("isolated vertex has no edges", network.firstEdge(2) == FlowNetwork.noEdge());
    }

    private static void testKnownMaxFlow() {
        section("Known max-flow values");

        // CLRS figure 26.1: the standard six-vertex example, max flow 23.
        long clrs = runAll(clrsNetwork(), 0, 5, "CLRS example");
        check("CLRS network max flow is 23", clrs == 23L);

        // A single edge.
        FlowNetwork single = new FlowNetwork(2);
        single.addEdge(0, 1, 7L);
        check("single edge flow is its capacity", runAll(single, 0, 1, "single edge") == 7L);

        // Two parallel paths, each limited by its narrowest edge.
        FlowNetwork parallel = new FlowNetwork(4);
        parallel.addEdge(0, 1, 3L);
        parallel.addEdge(1, 3, 2L);
        parallel.addEdge(0, 2, 5L);
        parallel.addEdge(2, 3, 4L);
        check("parallel paths sum their bottlenecks",
              runAll(parallel, 0, 3, "parallel") == 6L);

        // A chain is limited by its narrowest link.
        FlowNetwork chain = new FlowNetwork(4);
        chain.addEdge(0, 1, 10L);
        chain.addEdge(1, 2, 3L);
        chain.addEdge(2, 3, 8L);
        check("chain is limited by its bottleneck", runAll(chain, 0, 3, "chain") == 3L);
    }

    private static void testFordFulkersonPathologicalCase() {
        section("Ford-Fulkerson: the case that motivates Edmonds-Karp");

        // The classic diamond: two wide paths joined by a capacity-1 edge. All
        // three algorithms must still get the right answer; only the work done
        // differs.
        FlowNetwork diamond = new FlowNetwork(4);
        diamond.addEdge(0, 1, 1000L);
        diamond.addEdge(0, 2, 1000L);
        diamond.addEdge(1, 2, 1L);
        diamond.addEdge(1, 3, 1000L);
        diamond.addEdge(2, 3, 1000L);

        check("diamond max flow is 2000", runAll(diamond, 0, 3, "diamond") == 2000L);
    }

    private static void testAlgorithmsAgreeOnRandomNetworks() {
        section("Three algorithms agree on random networks");

        long seed = 90210L;
        int cases = 600;
        int disagreements = 0;

        for (int c = 0; c < cases; c++) {
            seed = nextSeed(seed);
            int vertices = 2 + (int) (seed % 7);
            seed = nextSeed(seed);
            int edges = (int) (seed % 15);

            FlowNetwork base = new FlowNetwork(vertices);
            for (int i = 0; i < edges; i++) {
                seed = nextSeed(seed);
                int from = (int) (seed % vertices);
                seed = nextSeed(seed);
                int to = (int) (seed % vertices);
                seed = nextSeed(seed);
                long capacity = seed % 12;
                if (from != to) {
                    base.addEdge(from, to, capacity);
                }
            }

            int sink = vertices - 1;
            long ff = FordFulkerson.maxFlow(base.copy(), 0, sink);
            long ek = EdmondsKarp.maxFlow(base.copy(), 0, sink);
            long dinic = Dinic.maxFlow(base.copy(), 0, sink);

            if (ff != ek || ek != dinic) {
                disagreements++;
                if (disagreements <= 3) {
                    System.out.printf("  [FAIL] v=%d e=%d ff=%d ek=%d dinic=%d%n",
                                      vertices, edges, ff, ek, dinic);
                }
            }
        }

        check(cases + " random networks: all three algorithms agree", disagreements == 0);
    }

    private static void testFlowConservation() {
        section("Flow conservation");

        long seed = 13579L;
        int cases = 300;
        int violations = 0;

        for (int c = 0; c < cases; c++) {
            seed = nextSeed(seed);
            int vertices = 3 + (int) (seed % 5);
            seed = nextSeed(seed);
            int edges = 2 + (int) (seed % 12);

            FlowNetwork base = new FlowNetwork(vertices);
            for (int i = 0; i < edges; i++) {
                seed = nextSeed(seed);
                int from = (int) (seed % vertices);
                seed = nextSeed(seed);
                int to = (int) (seed % vertices);
                seed = nextSeed(seed);
                long capacity = 1 + seed % 10;
                if (from != to) {
                    base.addEdge(from, to, capacity);
                }
            }

            int sink = vertices - 1;
            FlowNetwork original = base.copy();
            FlowNetwork residual = base.copy();
            long value = EdmondsKarp.maxFlow(residual, 0, sink);

            // Flow on a forward edge is how much its reverse gained.
            for (int v = 0; v < vertices; v++) {
                if (v == 0 || v == sink) {
                    continue;
                }
                long net = 0;
                for (int e = 0; e < original.edgeCount(); e += 2) {
                    long flow = residual.residual(FlowNetwork.reverse(e))
                              - original.residual(FlowNetwork.reverse(e));
                    if (flow == 0) {
                        continue;
                    }
                    int to = original.to(e);
                    int from = original.to(FlowNetwork.reverse(e));
                    if (to == v) {
                        net += flow;
                    }
                    if (from == v) {
                        net -= flow;
                    }
                }
                if (net != 0) {
                    violations++;
                    break;
                }
            }

            // Whatever leaves the source must equal the reported value.
            long outOfSource = 0;
            for (int e = 0; e < original.edgeCount(); e += 2) {
                int from = original.to(FlowNetwork.reverse(e));
                if (from == 0) {
                    outOfSource += residual.residual(FlowNetwork.reverse(e))
                                 - original.residual(FlowNetwork.reverse(e));
                }
                int to = original.to(e);
                if (to == 0) {
                    outOfSource -= residual.residual(FlowNetwork.reverse(e))
                                 - original.residual(FlowNetwork.reverse(e));
                }
            }
            if (outOfSource != value) {
                violations++;
            }
        }

        check(cases + " networks conserve flow and match the reported value", violations == 0);
    }

    private static void testMaxFlowMinCut() {
        section("Max-flow min-cut theorem");

        long seed = 2468L;
        int cases = 300;
        int violations = 0;

        for (int c = 0; c < cases; c++) {
            seed = nextSeed(seed);
            int vertices = 2 + (int) (seed % 6);
            seed = nextSeed(seed);
            int edges = 1 + (int) (seed % 12);

            FlowNetwork base = new FlowNetwork(vertices);
            for (int i = 0; i < edges; i++) {
                seed = nextSeed(seed);
                int from = (int) (seed % vertices);
                seed = nextSeed(seed);
                int to = (int) (seed % vertices);
                seed = nextSeed(seed);
                long capacity = 1 + seed % 9;
                if (from != to) {
                    base.addEdge(from, to, capacity);
                }
            }

            int sink = vertices - 1;
            FlowNetwork original = base.copy();
            FlowNetwork residual = base.copy();
            long value = EdmondsKarp.maxFlow(residual, 0, sink);

            boolean[] sourceSide = EdmondsKarp.minCutSourceSide(residual, 0);

            // Sum the original capacity of every edge crossing the cut.
            long cutCapacity = 0;
            for (int e = 0; e < original.edgeCount(); e += 2) {
                int to = original.to(e);
                int from = original.to(FlowNetwork.reverse(e));
                if (sourceSide[from] && !sourceSide[to]) {
                    cutCapacity += original.residual(e);
                }
            }

            if (cutCapacity != value || sourceSide[sink]) {
                violations++;
                if (violations <= 3) {
                    System.out.printf("  [FAIL] flow=%d cut=%d%n", value, cutCapacity);
                }
            }
        }

        check(cases + " networks: min cut capacity equals max flow", violations == 0);
    }

    private static void testDegenerateNetworks() {
        section("Degenerate networks");

        FlowNetwork empty = new FlowNetwork(2);
        check("no edges means no flow", runAll(empty, 0, 1, "no edges") == 0L);

        FlowNetwork zero = new FlowNetwork(2);
        zero.addEdge(0, 1, 0L);
        check("zero capacity means no flow", runAll(zero, 0, 1, "zero capacity") == 0L);

        FlowNetwork disconnected = new FlowNetwork(4);
        disconnected.addEdge(0, 1, 5L);
        disconnected.addEdge(2, 3, 5L);
        check("disconnected sink is unreachable",
              runAll(disconnected, 0, 3, "disconnected") == 0L);

        FlowNetwork sameVertex = new FlowNetwork(3);
        sameVertex.addEdge(0, 1, 4L);
        check("source equal to sink is zero",
              FordFulkerson.maxFlow(sameVertex.copy(), 1, 1) == 0L
                      && EdmondsKarp.maxFlow(sameVertex.copy(), 1, 1) == 0L
                      && Dinic.maxFlow(sameVertex.copy(), 1, 1) == 0L);

        FlowNetwork backward = new FlowNetwork(3);
        backward.addEdge(0, 1, 3L);
        backward.addEdge(2, 1, 3L);
        check("edges pointing away from the sink give no flow",
              runAll(backward, 0, 2, "backward") == 0L);
    }

    // -------------------------------------------------- bipartite matching

    private static void testBipartiteKnownCases() {
        section("Bipartite matching: known cases");

        // Perfect matching on three pairs.
        BipartiteMatching perfect = new BipartiteMatching(3, 3);
        perfect.addEdge(0, 0);
        perfect.addEdge(1, 1);
        perfect.addEdge(2, 2);
        check("three disjoint pairs match fully", perfect.size() == 3);

        // Every left vertex wants the same right vertex.
        BipartiteMatching contested = new BipartiteMatching(3, 1);
        contested.addEdge(0, 0);
        contested.addEdge(1, 0);
        contested.addEdge(2, 0);
        check("one contested vertex matches once", contested.size() == 1);

        // Requires rehousing an earlier choice to reach the optimum.
        BipartiteMatching augmenting = new BipartiteMatching(2, 2);
        augmenting.addEdge(0, 0);
        augmenting.addEdge(0, 1);
        augmenting.addEdge(1, 0);
        check("augmenting path finds the maximum", augmenting.size() == 2);

        BipartiteMatching none = new BipartiteMatching(3, 3);
        check("no edges means no matching", none.size() == 0);

        int[] result = perfect.solve();
        boolean valid = true;
        for (int right = 0; right < result.length; right++) {
            if (result[right] != BipartiteMatching.UNMATCHED && result[right] != right) {
                valid = false;
            }
        }
        check("matching pairs are the expected ones", valid);
    }

    private static void testBipartiteMatchesMaxFlow() {
        section("Bipartite matching agrees with the max-flow reduction");

        long seed = 555L;
        int cases = 400;
        int disagreements = 0;

        for (int c = 0; c < cases; c++) {
            seed = nextSeed(seed);
            int left = 1 + (int) (seed % 5);
            seed = nextSeed(seed);
            int right = 1 + (int) (seed % 5);

            BipartiteMatching matching = new BipartiteMatching(left, right);
            for (int l = 0; l < left; l++) {
                for (int r = 0; r < right; r++) {
                    seed = nextSeed(seed);
                    if (seed % 3 == 0) {
                        matching.addEdge(l, r);
                    }
                }
            }

            int kuhn = matching.size();
            long flow = Dinic.maxFlow(matching.toFlowNetwork(),
                                      matching.flowSource(), matching.flowSink());

            if (kuhn != flow) {
                disagreements++;
                if (disagreements <= 3) {
                    System.out.printf("  [FAIL] left=%d right=%d kuhn=%d flow=%d%n",
                                      left, right, kuhn, flow);
                }
            }
        }

        check(cases + " bipartite graphs: Kuhn equals the max-flow reduction", disagreements == 0);
    }

    private static void testInputValidation() {
        section("Input validation");

        FlowNetwork network = new FlowNetwork(3);
        check("negative capacity rejected",
              throwsIllegalArgument(() -> network.addEdge(0, 1, -1L)));
        check("out of range vertex rejected",
              throwsIllegalArgument(() -> network.addEdge(0, 9, 1L)));
        check("negative vertex count rejected",
              throwsIllegalArgument(() -> new FlowNetwork(-1)));
        check("null network rejected",
              throwsIllegalArgument(() -> FordFulkerson.maxFlow(null, 0, 1)));
        check("out of range sink rejected",
              throwsIllegalArgument(() -> EdmondsKarp.maxFlow(network.copy(), 0, 9)));
        check("bipartite left out of range",
              throwsIllegalArgument(() -> new BipartiteMatching(2, 2).addEdge(5, 0)));
        check("bipartite right out of range",
              throwsIllegalArgument(() -> new BipartiteMatching(2, 2).addEdge(0, 5)));
    }

    // --------------------------------------------------------------- helpers

    /** Runs all three algorithms on copies and asserts they agree. */
    private static long runAll(FlowNetwork network, int source, int sink, String label) {
        long ff = FordFulkerson.maxFlow(network.copy(), source, sink);
        long ek = EdmondsKarp.maxFlow(network.copy(), source, sink);
        long dinic = Dinic.maxFlow(network.copy(), source, sink);

        if (ff != ek || ek != dinic) {
            System.out.printf("  [FAIL] %s disagreement ff=%d ek=%d dinic=%d%n",
                              label, ff, ek, dinic);
            failed++;
        } else {
            passed++;
            System.out.println("  [PASS] " + label + ": three algorithms agree (" + ff + ")");
        }
        return ff;
    }

    /** CLRS figure 26.1, whose maximum flow is 23. */
    private static FlowNetwork clrsNetwork() {
        FlowNetwork network = new FlowNetwork(6);
        network.addEdge(0, 1, 16L);
        network.addEdge(0, 2, 13L);
        network.addEdge(1, 2, 10L);
        network.addEdge(2, 1, 4L);
        network.addEdge(1, 3, 12L);
        network.addEdge(3, 2, 9L);
        network.addEdge(2, 4, 14L);
        network.addEdge(4, 3, 7L);
        network.addEdge(3, 5, 20L);
        network.addEdge(4, 5, 4L);
        return network;
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
