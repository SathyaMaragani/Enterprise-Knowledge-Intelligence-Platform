package texthack.graph;

/**
 * Dinic's maximum flow algorithm.
 *
 * <p>Edmonds-Karp finds one shortest augmenting path per BFS. Dinic gets more
 * out of each BFS: it builds a <em>level graph</em> once, then pushes a
 * <em>blocking flow</em> through it -- saturating many shortest paths before
 * recomputing levels at all.
 *
 * <h2>Two ideas</h2>
 * <ul>
 *   <li><b>Level graph.</b> BFS labels each vertex with its distance from the
 *       source in the residual graph. Only edges stepping from level d to level
 *       d+1 are eligible, which restricts the search to shortest paths without
 *       having to re-run BFS for each one.</li>
 *   <li><b>Current-arc optimisation.</b> Each vertex keeps a cursor into its
 *       adjacency list. When an edge is exhausted, the cursor advances past it
 *       permanently for this phase. Without it a DFS would rescan dead edges
 *       repeatedly and the phase would cost O(V·E) instead of O(E); this is the
 *       detail that makes Dinic actually faster in practice rather than only on
 *       paper.</li>
 * </ul>
 *
 * <p>Each phase strictly increases the source-sink distance, so there are at
 * most V phases.
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>Time: O(V²·E) in general. On unit-capacity graphs it improves to
 *       O(E·sqrt(E)), which is why it is the usual choice for bipartite
 *       matching by reduction.</li>
 *   <li>Space: O(V) for levels and cursors.</li>
 * </ul>
 */
public final class Dinic {

    private Dinic() {
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

        int[] level = new int[n];
        int[] cursor = new int[n];
        int[] queue = new int[n];
        long total = 0L;

        while (buildLevelGraph(network, source, sink, level, queue)) {
            // Reset the current-arc cursors once per phase, not per path.
            for (int v = 0; v < n; v++) {
                cursor[v] = network.firstEdge(v);
            }
            long pushed;
            while ((pushed = blockingFlow(network, source, sink, Long.MAX_VALUE, level, cursor)) > 0) {
                total += pushed;
            }
        }

        return total;
    }

    /** BFS level assignment; returns false when the sink is unreachable. */
    private static boolean buildLevelGraph(FlowNetwork network, int source, int sink,
                                           int[] level, int[] queue) {
        for (int v = 0; v < level.length; v++) {
            level[v] = -1;
        }
        int headIndex = 0;
        int tailIndex = 0;
        level[source] = 0;
        queue[tailIndex++] = source;

        while (headIndex < tailIndex) {
            int vertex = queue[headIndex++];
            for (int e = network.firstEdge(vertex); e != FlowNetwork.noEdge();
                 e = network.nextEdge(e)) {
                int next = network.to(e);
                if (level[next] < 0 && network.residual(e) > 0) {
                    level[next] = level[vertex] + 1;
                    queue[tailIndex++] = next;
                }
            }
        }

        return level[sink] >= 0;
    }

    /**
     * Pushes along one level-respecting path, advancing the per-vertex cursor
     * past edges that turn out to be dead for this phase.
     */
    private static long blockingFlow(FlowNetwork network, int vertex, int sink,
                                     long limit, int[] level, int[] cursor) {
        if (vertex == sink) {
            return limit;
        }

        for (; cursor[vertex] != FlowNetwork.noEdge(); cursor[vertex] = network.nextEdge(cursor[vertex])) {
            int e = cursor[vertex];
            int next = network.to(e);
            long residual = network.residual(e);

            if (residual > 0 && level[next] == level[vertex] + 1) {
                long bottleneck = residual < limit ? residual : limit;
                long pushed = blockingFlow(network, next, sink, bottleneck, level, cursor);
                if (pushed > 0) {
                    network.push(e, pushed);
                    return pushed;
                }
                // No flow could get through here this phase; retiring the vertex
                // stops later searches walking back into it.
                level[next] = -1;
            }
        }

        return 0L;
    }
}
