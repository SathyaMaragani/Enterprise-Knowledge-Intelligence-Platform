package texthack.approximation;

import texthack.core.IntList;

/**
 * 2-approximate minimum vertex cover by maximal matching.
 *
 * <p>A vertex cover is a set of vertices touching every edge. Finding the
 * smallest one is NP-hard, so this returns a cover guaranteed to be at most
 * twice the optimum -- which is achievable in linear time.
 *
 * <h2>The algorithm, and why the bound holds</h2>
 * Repeatedly pick any uncovered edge and take <em>both</em> its endpoints. The
 * edges picked this way share no endpoints, so they form a matching M.
 *
 * <p>The proof is the appealing part. Any cover must include at least one
 * endpoint of every edge in M, and those edges are disjoint, so the optimum is
 * at least |M|. This algorithm returns exactly 2|M| vertices. Therefore
 * {@code returned <= 2 * optimum}.
 *
 * <p>Taking both endpoints looks wasteful and is the reason the bound is
 * provable. The "obvious" greedy alternative -- repeatedly take the
 * highest-degree vertex -- has no constant-factor guarantee at all; it is
 * Θ(log n) in the worst case, and it is a standard trap precisely because it
 * behaves better on typical inputs while being far worse on adversarial ones.
 *
 * <h2>Tightness</h2>
 * The factor 2 is achieved, not merely feared: on a single edge the algorithm
 * returns both endpoints when one suffices. On a triangle it returns all three
 * when two suffice. The test suite pins both.
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>Time: O(V + E).</li>
 *   <li>Space: O(V).</li>
 * </ul>
 */
public final class VertexCover {

    private final int vertexCount;
    private final IntList edgeFrom = new IntList();
    private final IntList edgeTo = new IntList();

    public VertexCover(int vertexCount) {
        if (vertexCount < 0) {
            throw new IllegalArgumentException("vertexCount must not be negative");
        }
        this.vertexCount = vertexCount;
    }

    /** Adds an undirected edge. */
    public void addEdge(int u, int v) {
        checkVertex(u);
        checkVertex(v);
        edgeFrom.add(u);
        edgeTo.add(v);
    }

    private void checkVertex(int v) {
        if (v < 0 || v >= vertexCount) {
            throw new IllegalArgumentException("vertex " + v + " out of range");
        }
    }

    public int vertexCount() {
        return vertexCount;
    }

    public int edgeCount() {
        return edgeFrom.size();
    }

    /**
     * Computes a 2-approximate cover.
     *
     * @return vertices in the cover, ascending
     */
    public int[] cover() {
        boolean[] chosen = new boolean[vertexCount];

        for (int i = 0; i < edgeFrom.size(); i++) {
            int u = edgeFrom.get(i);
            int v = edgeTo.get(i);
            // Already covered by an earlier pick; skip.
            if (chosen[u] || chosen[v]) {
                continue;
            }
            // Self-loops need only their single endpoint.
            chosen[u] = true;
            chosen[v] = true;
        }

        IntList result = new IntList();
        for (int v = 0; v < vertexCount; v++) {
            if (chosen[v]) {
                result.add(v);
            }
        }
        return result.toArray();
    }

    /** True when every edge has at least one endpoint in {@code candidate}. */
    public boolean isCover(int[] candidate) {
        boolean[] inCover = new boolean[vertexCount];
        for (int v : candidate) {
            checkVertex(v);
            inCover[v] = true;
        }
        for (int i = 0; i < edgeFrom.size(); i++) {
            if (!inCover[edgeFrom.get(i)] && !inCover[edgeTo.get(i)]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Exact minimum cover by exhaustive search over subsets.
     *
     * <p>O(2^V · E) and therefore only usable on tiny graphs, which is exactly
     * what a test reference needs: the approximation is checked against the true
     * optimum on small inputs to confirm the factor-2 guarantee actually holds
     * rather than being merely asserted in a comment.
     *
     * @throws IllegalStateException if the graph is too large to brute force
     */
    public int exactMinimumSize() {
        if (vertexCount > 20) {
            throw new IllegalStateException(
                "exhaustive search refuses graphs beyond 20 vertices; this is a test reference");
        }
        int best = vertexCount;
        int subsets = 1 << vertexCount;

        for (int mask = 0; mask < subsets; mask++) {
            int size = Integer.bitCount(mask);
            if (size >= best) {
                continue;
            }
            boolean covers = true;
            for (int i = 0; i < edgeFrom.size(); i++) {
                int u = edgeFrom.get(i);
                int v = edgeTo.get(i);
                if (((mask >> u) & 1) == 0 && ((mask >> v) & 1) == 0) {
                    covers = false;
                    break;
                }
            }
            if (covers) {
                best = size;
            }
        }
        return best;
    }
}
