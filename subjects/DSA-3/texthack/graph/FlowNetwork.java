package texthack.graph;

/**
 * A directed graph with capacities, stored as a residual network.
 *
 * <h2>Representation</h2>
 * Edges live in parallel arrays rather than objects, and adjacency is a linked
 * list threaded through them: {@code head[v]} is the first edge leaving v, and
 * {@code nextEdge[e]} is the next edge from the same tail. This is the classic
 * forward-star layout. It needs no {@code java.util} collections, allocates one
 * int per edge instead of an object per edge, and keeps every traversal on
 * contiguous arrays.
 *
 * <h2>Paired edges</h2>
 * Every edge is added together with a reverse edge, so edge {@code e} and edge
 * {@code e ^ 1} are always a forward/backward pair. Pushing flow along {@code e}
 * subtracts from its capacity and adds the same amount to {@code e ^ 1}. That
 * single trick is what makes augmenting-path algorithms able to *undo* earlier
 * decisions: sending flow backwards along a reverse edge cancels flow previously
 * pushed forwards, which is exactly why greedy path-finding converges on the
 * true maximum rather than getting stuck.
 *
 * <h2>Mutation</h2>
 * The max-flow algorithms in this package consume the network in place, leaving
 * it in its residual state. That is deliberate: the residual graph is what a
 * min-cut is read from, so destroying it would throw away the more interesting
 * half of the result. Callers that need the original should pass {@link #copy()}.
 *
 * <p>Capacities are {@code long} so that summing many edges cannot overflow on
 * networks where individual capacities are already large ints.
 */
public final class FlowNetwork {

    private static final int NO_EDGE = -1;

    private final int vertexCount;
    private final int[] head;

    private int[] edgeTo;
    private long[] capacity;
    private int[] nextEdge;
    private int edgeCount;

    public FlowNetwork(int vertexCount) {
        if (vertexCount < 0) {
            throw new IllegalArgumentException("vertexCount must not be negative");
        }
        this.vertexCount = vertexCount;
        this.head = new int[vertexCount];
        for (int v = 0; v < vertexCount; v++) {
            head[v] = NO_EDGE;
        }
        this.edgeTo = new int[8];
        this.capacity = new long[8];
        this.nextEdge = new int[8];
        this.edgeCount = 0;
    }

    /**
     * Adds a directed edge and its reverse.
     *
     * @return the id of the forward edge; its reverse is {@code id ^ 1}
     * @throws IllegalArgumentException on an out-of-range endpoint or negative
     *         capacity
     */
    public int addEdge(int from, int to, long edgeCapacity) {
        checkVertex(from);
        checkVertex(to);
        if (edgeCapacity < 0) {
            throw new IllegalArgumentException("capacity must not be negative");
        }

        int forward = append(to, edgeCapacity, head[from]);
        head[from] = forward;

        // The reverse edge starts empty; it only gains capacity when flow is
        // pushed forwards, which is what makes that flow cancellable.
        int backward = append(from, 0L, head[to]);
        head[to] = backward;

        return forward;
    }

    private int append(int to, long cap, int next) {
        if (edgeCount == edgeTo.length) {
            grow();
        }
        edgeTo[edgeCount] = to;
        capacity[edgeCount] = cap;
        nextEdge[edgeCount] = next;
        return edgeCount++;
    }

    private void grow() {
        int size = edgeTo.length * 2;
        int[] newEdgeTo = new int[size];
        long[] newCapacity = new long[size];
        int[] newNextEdge = new int[size];
        for (int i = 0; i < edgeCount; i++) {
            newEdgeTo[i] = edgeTo[i];
            newCapacity[i] = capacity[i];
            newNextEdge[i] = nextEdge[i];
        }
        edgeTo = newEdgeTo;
        capacity = newCapacity;
        nextEdge = newNextEdge;
    }

    private void checkVertex(int v) {
        if (v < 0 || v >= vertexCount) {
            throw new IllegalArgumentException("vertex " + v + " out of range 0.." + (vertexCount - 1));
        }
    }

    public int vertexCount() {
        return vertexCount;
    }

    /** Total stored edges, counting each reverse edge separately. */
    public int edgeCount() {
        return edgeCount;
    }

    /** First edge leaving {@code v}, or -1. */
    public int firstEdge(int v) {
        checkVertex(v);
        return head[v];
    }

    /** Next edge sharing a tail with {@code e}, or -1. */
    public int nextEdge(int e) {
        return nextEdge[e];
    }

    /** Head vertex of edge {@code e}. */
    public int to(int e) {
        return edgeTo[e];
    }

    /** Remaining residual capacity of edge {@code e}. */
    public long residual(int e) {
        return capacity[e];
    }

    /** The paired reverse edge. */
    public static int reverse(int e) {
        return e ^ 1;
    }

    /** Pushes {@code amount} along {@code e}, crediting its reverse. */
    public void push(int e, long amount) {
        capacity[e] -= amount;
        capacity[e ^ 1] += amount;
    }

    /** Sentinel meaning "no edge", returned by {@link #firstEdge} and {@link #nextEdge}. */
    public static int noEdge() {
        return NO_EDGE;
    }

    /** An independent copy, so an algorithm's mutation cannot be seen here. */
    public FlowNetwork copy() {
        FlowNetwork clone = new FlowNetwork(vertexCount);
        clone.edgeTo = new int[edgeTo.length];
        clone.capacity = new long[capacity.length];
        clone.nextEdge = new int[nextEdge.length];
        for (int i = 0; i < edgeCount; i++) {
            clone.edgeTo[i] = edgeTo[i];
            clone.capacity[i] = capacity[i];
            clone.nextEdge[i] = nextEdge[i];
        }
        clone.edgeCount = edgeCount;
        for (int v = 0; v < vertexCount; v++) {
            clone.head[v] = head[v];
        }
        return clone;
    }
}
