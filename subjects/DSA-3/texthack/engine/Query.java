package texthack.engine;

/**
 * A parsed TextHack query.
 *
 * <p>Deliberately a plain value object with no behaviour: parsing and execution
 * are separate stages, so a query can be inspected, logged or rejected before
 * anything touches a corpus.
 */
public final class Query {

    /** What the query asks for. */
    public enum Kind {
        /** Exact single-pattern search. */
        FIND,
        /** Multi-pattern search in one pass (Aho-Corasick). */
        FIND_ALL,
        /** Edit-distance search within a threshold. */
        FUZZY,
        /** Normalised similarity between two strings. */
        SIMILAR,
        /** Primality of an integer. */
        PRIME
    }

    private final Kind kind;
    private final String[] terms;
    private final int threshold;
    private final long number;

    Query(Kind kind, String[] terms, int threshold, long number) {
        this.kind = kind;
        this.terms = terms;
        this.threshold = threshold;
        this.number = number;
    }

    public Kind kind() {
        return kind;
    }

    /** Quoted terms, in the order written. */
    public String[] terms() {
        String[] copy = new String[terms.length];
        for (int i = 0; i < terms.length; i++) {
            copy[i] = terms[i];
        }
        return copy;
    }

    public int termCount() {
        return terms.length;
    }

    public String term(int index) {
        return terms[index];
    }

    /** Edit-distance threshold, meaningful only for {@link Kind#FUZZY}. */
    public int threshold() {
        return threshold;
    }

    /** Integer operand, meaningful only for {@link Kind#PRIME}. */
    public long number() {
        return number;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder(kind.toString());
        for (String term : terms) {
            out.append(" \"").append(term).append('"');
        }
        if (kind == Kind.FUZZY) {
            out.append(" ~").append(threshold);
        }
        if (kind == Kind.PRIME) {
            out.append(' ').append(number);
        }
        return out.toString();
    }
}
