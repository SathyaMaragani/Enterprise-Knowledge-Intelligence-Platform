package texthack.graph;

/**
 * Ford-Fulkerson maximum flow, using depth-first search to find augmenting
 * paths.
 *
 * <p>The method is the foundation the other two build on: while some path from
 * source to sink still has spare capacity, push as much as that path allows,
 * and repeat. Correctness comes from the reverse edges -- pushing flow
 * backwards along a reverse edge cancels an earlier forward decision, so no
 * early choice can permanently trap the algorithm below the true maximum.
 *
 * <h2>Why the bound depends on the flow value</h2>
 * Ford-Fulkerson does not say <em>which</em> augmenting path to take, and DFS
 * makes no promises about path length. Each augmentation raises the flow by at
 * least one unit on integer capacities, so it terminates, but the work is
 * O(E · maxflow) rather than a function of the graph alone. The classic bad case
 * is a diamond with two capacity-1000000 paths joined by a capacity-1 middle
 * edge: if DFS keeps choosing the path through the middle, it augments one unit
 * at a time and runs a million times on a four-vertex graph.
 *
 * <p>{@link EdmondsKarp} removes exactly that weakness by choosing the shortest
 * augmenting path instead, which is why its bound mentions only V and E.
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>Time: O(E · maxflow) on integer capacities.</li>
 *   <li>Space: O(V) for the visited marks and recursion.</li>
 * </ul>
 */
public final class FordFulkerson {

    private FordFulkerson() {
    }

    /**
     * Computes the maximum flow, leaving {@code network} in its residual state.
     *
     * @throws IllegalArgumentException if the endpoints are out of range
     */
    public static long maxFlow(FlowNetwork network, int source, int sink) {
        if (network == null) {
            throw new IllegalArgumentException("network must not be null");
        }
        if (source < 0 || source >= network.vertexCount()
                || sink < 0 || sink >= network.vertexCount()) {
            throw new IllegalArgumentException("source or sink out of range");
        }
        if (source == sink) {
            return 0L;
        }

        boolean[] visited = new boolean[network.vertexCount()];
        long total = 0L;

        while (true) {
            for (int v = 0; v < visited.length; v++) {
                visited[v] = false;
            }
            long pushed = augment(network, source, sink, Long.MAX_VALUE, visited);
            if (pushed == 0L) {
                break;
            }
            total += pushed;
        }

        return total;
    }

    /**
     * Depth-first search for one augmenting path, pushing the bottleneck amount
     * back up the recursion as it unwinds.
     *
     * <p>Recursion depth is bounded by the vertex count, since {@code visited}
     * prevents revisiting.
     */
    private static long augment(FlowNetwork network, int vertex, int sink,
                                long limit, boolean[] visited) {
        if (vertex == sink) {
            return limit;
        }
        visited[vertex] = true;

        for (int e = network.firstEdge(vertex); e != FlowNetwork.noEdge();
             e = network.nextEdge(e)) {
            int next = network.to(e);
            long residual = network.residual(e);

            if (residual > 0 && !visited[next]) {
                long bottleneck = residual < limit ? residual : limit;
                long pushed = augment(network, next, sink, bottleneck, visited);
                if (pushed > 0) {
                    network.push(e, pushed);
                    return pushed;
                }
            }
        }

        return 0L;
    }
}
