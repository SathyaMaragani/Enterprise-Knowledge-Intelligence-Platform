package texthack.graph;

/**
 * Maximum bipartite matching by Kuhn's augmenting-path algorithm.
 *
 * <p>Given two disjoint vertex sets and edges only between them, finds the
 * largest set of edges sharing no endpoint.
 *
 * <h2>How it works</h2>
 * Each left vertex is tried in turn. A depth-first search looks for an
 * <em>augmenting path</em>: an unmatched right vertex, or a matched one whose
 * current partner can be rehoused elsewhere. Finding one increases the matching
 * by exactly one edge. Berge's theorem supplies the guarantee -- a matching is
 * maximum precisely when no augmenting path exists -- so running out of paths
 * means the answer is optimal, not merely locally stuck.
 *
 * <h2>Relationship to max flow</h2>
 * This is solvable by reduction: add a source joined to every left vertex, a
 * sink joined from every right vertex, give every edge capacity 1, and the
 * maximum flow equals the maximum matching. Kuhn's algorithm is essentially
 * that reduction with the flow bookkeeping stripped out, which is why it is
 * shorter and allocates less.
 *
 * <p>Both routes are implemented in this package and the test suite checks they
 * agree on random bipartite graphs. Two independent derivations of the same
 * quantity is a far better correctness signal than either alone.
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>Time: O(V·E) -- one DFS costing O(E) per left vertex.</li>
 *   <li>Space: O(V) for the matching and visited marks.</li>
 * </ul>
 */
public final class BipartiteMatching {

    /** No partner. */
    public static final int UNMATCHED = -1;

    private final int leftCount;
    private final int rightCount;
    private final int[] adjacencyHead;
    private int[] adjacencyTo;
    private int[] adjacencyNext;
    private int edgeCount;

    public BipartiteMatching(int leftCount, int rightCount) {
        if (leftCount < 0 || rightCount < 0) {
            throw new IllegalArgumentException("side sizes must not be negative");
        }
        this.leftCount = leftCount;
        this.rightCount = rightCount;
        this.adjacencyHead = new int[leftCount];
        for (int v = 0; v < leftCount; v++) {
            adjacencyHead[v] = -1;
        }
        this.adjacencyTo = new int[8];
        this.adjacencyNext = new int[8];
        this.edgeCount = 0;
    }

    /** Adds an edge between a left and a right vertex. */
    public void addEdge(int left, int right) {
        if (left < 0 || left >= leftCount) {
            throw new IllegalArgumentException("left vertex " + left + " out of range");
        }
        if (right < 0 || right >= rightCount) {
            throw new IllegalArgumentException("right vertex " + right + " out of range");
        }
        if (edgeCount == adjacencyTo.length) {
            int size = adjacencyTo.length * 2;
            int[] newTo = new int[size];
            int[] newNext = new int[size];
            for (int i = 0; i < edgeCount; i++) {
                newTo[i] = adjacencyTo[i];
                newNext[i] = adjacencyNext[i];
            }
            adjacencyTo = newTo;
            adjacencyNext = newNext;
        }
        adjacencyTo[edgeCount] = right;
        adjacencyNext[edgeCount] = adjacencyHead[left];
        adjacencyHead[left] = edgeCount;
        edgeCount++;
    }

    public int leftCount() {
        return leftCount;
    }

    public int rightCount() {
        return rightCount;
    }

    /**
     * Computes a maximum matching.
     *
     * @return for each right vertex, the left vertex it is matched to, or
     *         {@link #UNMATCHED}
     */
    public int[] solve() {
        int[] matchOfRight = new int[rightCount];
        for (int r = 0; r < rightCount; r++) {
            matchOfRight[r] = UNMATCHED;
        }

        boolean[] visited = new boolean[rightCount];
        for (int left = 0; left < leftCount; left++) {
            for (int r = 0; r < rightCount; r++) {
                visited[r] = false;
            }
            tryAugment(left, matchOfRight, visited);
        }

        return matchOfRight;
    }

    /** Size of a maximum matching. */
    public int size() {
        int[] matchOfRight = solve();
        int count = 0;
        for (int partner : matchOfRight) {
            if (partner != UNMATCHED) {
                count++;
            }
        }
        return count;
    }

    private boolean tryAugment(int left, int[] matchOfRight, boolean[] visited) {
        for (int e = adjacencyHead[left]; e != -1; e = adjacencyNext[e]) {
            int right = adjacencyTo[e];
            if (visited[right]) {
                continue;
            }
            visited[right] = true;

            // Either the right vertex is free, or its current partner can move.
            if (matchOfRight[right] == UNMATCHED
                    || tryAugment(matchOfRight[right], matchOfRight, visited)) {
                matchOfRight[right] = left;
                return true;
            }
        }
        return false;
    }

    /**
     * The same problem expressed as a flow network, for cross-checking.
     *
     * <p>Vertex 0 is the source, 1..leftCount the left side,
     * leftCount+1..leftCount+rightCount the right side, and the last vertex the
     * sink. Every edge has capacity 1, so the maximum flow is the maximum
     * matching.
     */
    public FlowNetwork toFlowNetwork() {
        int source = 0;
        int sink = leftCount + rightCount + 1;
        FlowNetwork network = new FlowNetwork(sink + 1);

        for (int left = 0; left < leftCount; left++) {
            network.addEdge(source, 1 + left, 1L);
            for (int e = adjacencyHead[left]; e != -1; e = adjacencyNext[e]) {
                network.addEdge(1 + left, 1 + leftCount + adjacencyTo[e], 1L);
            }
        }
        for (int right = 0; right < rightCount; right++) {
            network.addEdge(1 + leftCount + right, sink, 1L);
        }

        return network;
    }

    /** Source vertex id in {@link #toFlowNetwork()}. */
    public int flowSource() {
        return 0;
    }

    /** Sink vertex id in {@link #toFlowNetwork()}. */
    public int flowSink() {
        return leftCount + rightCount + 1;
    }
}
