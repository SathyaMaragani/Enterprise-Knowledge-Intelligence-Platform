package texthack.engine;

import texthack.graph.Dinic;
import texthack.graph.EdmondsKarp;
import texthack.graph.FlowNetwork;

/**
 * Citation-flow analysis: network flow applied to a citation graph.
 *
 * <p>Documents are vertices and citations are directed edges. Two questions the
 * flow machinery answers directly:
 *
 * <ul>
 *   <li><b>Influence throughput.</b> The maximum flow from one document to
 *       another measures how many edge-disjoint citation chains connect them.
 *       A single shared reference gives a flow of 1; genuinely converging
 *       literature gives more.</li>
 *   <li><b>Bottleneck citations.</b> The minimum cut identifies the smallest
 *       set of citations whose removal severs every path. Those are the papers
 *       the connection actually depends on.</li>
 * </ul>
 *
 * <p>With unit capacities the maximum flow equals the number of edge-disjoint
 * paths, by Menger's theorem -- so the flow value has a concrete reading rather
 * than being an abstract number.
 *
 * <p>This is a demonstration of the graph module applied to the platform's
 * domain, not a bibliometric claim. Citation influence in the literature sense
 * involves weighting and normalisation this does not attempt.
 */
public final class CitationFlow {

    private final int documentCount;
    private final FlowNetwork network;

    /**
     * @param documentCount number of documents; vertices are 0..documentCount-1
     */
    public CitationFlow(int documentCount) {
        if (documentCount < 1) {
            throw new IllegalArgumentException("documentCount must be at least 1");
        }
        this.documentCount = documentCount;
        this.network = new FlowNetwork(documentCount);
    }

    /** Records that {@code from} cites {@code to}, with unit weight. */
    public void addCitation(int from, int to) {
        addCitation(from, to, 1L);
    }

    /** Records a weighted citation. */
    public void addCitation(int from, int to, long weight) {
        if (from == to) {
            throw new IllegalArgumentException("a document cannot cite itself");
        }
        network.addEdge(from, to, weight);
    }

    public int documentCount() {
        return documentCount;
    }

    /**
     * Maximum citation flow from {@code source} to {@code sink}.
     *
     * <p>With unit weights this is the count of edge-disjoint citation paths.
     * Runs on a copy, so the analyser stays reusable.
     */
    public long influence(int source, int sink) {
        return Dinic.maxFlow(network.copy(), source, sink);
    }

    /**
     * The bottleneck set: documents on the source side of a minimum cut.
     *
     * <p>Citations crossing from this set to its complement are exactly those
     * whose removal would sever every path, and their total weight equals the
     * influence value.
     */
    public boolean[] bottleneck(int source, int sink) {
        FlowNetwork residual = network.copy();
        EdmondsKarp.maxFlow(residual, source, sink);
        return EdmondsKarp.minCutSourceSide(residual, source);
    }

    /**
     * Total weight of citations crossing the minimum cut.
     *
     * <p>Equal to {@link #influence} by the max-flow min-cut theorem; computing
     * it separately is a useful self-check rather than a second algorithm.
     */
    public long bottleneckWeight(int source, int sink) {
        boolean[] sourceSide = bottleneck(source, sink);
        long total = 0;
        for (int e = 0; e < network.edgeCount(); e += 2) {
            int to = network.to(e);
            int from = network.to(FlowNetwork.reverse(e));
            if (sourceSide[from] && !sourceSide[to]) {
                total += network.residual(e);
            }
        }
        return total;
    }
}
