package texthack.graph;

/**
 * Edmonds-Karp maximum flow: Ford-Fulkerson with the augmenting path chosen by
 * breadth-first search.
 *
 * <p>The only change from {@link FordFulkerson} is picking the shortest
 * augmenting path by edge count rather than whichever one a depth-first search
 * stumbles into. That single substitution removes the dependence on the flow
 * value entirely.
 *
 * <h2>Why BFS fixes the bound</h2>
 * Under BFS the distance from the source to any vertex in the residual graph
 * never decreases as augmentations proceed, and every augmentation saturates at
 * least one edge on the chosen path. An edge can only be saturated again after
 * the distance to its tail has strictly increased, which can happen O(V) times
 * per edge. That gives O(V·E) augmentations, each costing O(E) to find, hence
 * O(V·E²) -- a bound in the size of the graph alone.
 *
 * <p>Concretely: the diamond graph that makes Ford-Fulkerson augment a million
 * times is solved by Edmonds-Karp in two augmentations, because the two-edge
 * paths are shorter than the three-edge path through the bottleneck and are
 * therefore taken first.
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>Time: O(V·E²), independent of capacities.</li>
 *   <li>Space: O(V) for the queue and parent arrays.</li>
 * </ul>
 */
public final class EdmondsKarp {

    private EdmondsKarp() {
    }

    /**
     * Computes the maximum flow, leaving {@code network} in its residual state.
     */
    public static long maxFlow(FlowNetwork network, int source, int sink) {
        if (network == null) {
            throw new IllegalArgumentException("network must not be null");
        }
        int n = network.vertexCount();
        if (source < 0 || source >= n || sink < 0 || sink >= n) {
            throw new IllegalArgumentException("source or sink out of range");
        }
        if (source == sink) {
            return 0L;
        }

        int[] queue = new int[n];
        int[] parentEdge = new int[n];
        long total = 0L;

        while (true) {
            for (int v = 0; v < n; v++) {
                parentEdge[v] = FlowNetwork.noEdge();
            }

            // BFS over edges with spare capacity. A plain int array is enough:
            // each vertex is enqueued at most once per search.
            int headIndex = 0;
            int tailIndex = 0;
            queue[tailIndex++] = source;
            boolean[] seen = new boolean[n];
            seen[source] = true;

            while (headIndex < tailIndex && !seen[sink]) {
                int vertex = queue[headIndex++];
                for (int e = network.firstEdge(vertex); e != FlowNetwork.noEdge();
                     e = network.nextEdge(e)) {
                    int next = network.to(e);
                    if (!seen[next] && network.residual(e) > 0) {
                        seen[next] = true;
                        parentEdge[next] = e;
                        queue[tailIndex++] = next;
                    }
                }
            }

            if (!seen[sink]) {
                break; // sink unreachable: the flow is maximum
            }

            // Walk the path backwards to find the bottleneck, then again to push.
            long bottleneck = Long.MAX_VALUE;
            for (int v = sink; v != source; ) {
                int e = parentEdge[v];
                long residual = network.residual(e);
                if (residual < bottleneck) {
                    bottleneck = residual;
                }
                v = network.to(FlowNetwork.reverse(e));
            }
            for (int v = sink; v != source; ) {
                int e = parentEdge[v];
                network.push(e, bottleneck);
                v = network.to(FlowNetwork.reverse(e));
            }

            total += bottleneck;
        }

        return total;
    }

    /**
     * Vertices reachable from the source in the residual graph.
     *
     * <p>Run after {@link #maxFlow}, this is the source side of a minimum cut.
     * The max-flow min-cut theorem says its capacity equals the flow value, and
     * the test suite checks exactly that -- an independent confirmation that the
     * computed flow really is maximum, rather than merely self-consistent.
     */
    public static boolean[] minCutSourceSide(FlowNetwork residual, int source) {
        boolean[] reachable = new boolean[residual.vertexCount()];
        int[] queue = new int[residual.vertexCount()];
        int headIndex = 0;
        int tailIndex = 0;

        reachable[source] = true;
        queue[tailIndex++] = source;

        while (headIndex < tailIndex) {
            int vertex = queue[headIndex++];
            for (int e = residual.firstEdge(vertex); e != FlowNetwork.noEdge();
                 e = residual.nextEdge(e)) {
                int next = residual.to(e);
                if (!reachable[next] && residual.residual(e) > 0) {
                    reachable[next] = true;
                    queue[tailIndex++] = next;
                }
            }
        }

        return reachable;
    }
}
