package texthack.engine;

/**
 * Machine-readable complexity reporting for every implemented algorithm.
 *
 * <p>{@code docs/COMPLEXITY.md} is written for people. This is the same
 * information in a form code can query, so a benchmark run can print the
 * expected bound next to the measured one -- which is what turns a timing table
 * into evidence rather than trivia.
 *
 * <p>Kept deliberately as a static table rather than annotations or reflection:
 * the complexity of an algorithm is a claim about its analysis, not a property
 * discoverable from its bytecode.
 */
public final class ComplexityRegistry {

    /** One algorithm's documented bounds. */
    public static final class Entry {
        private final String name;
        private final String category;
        private final String time;
        private final String space;
        private final String note;

        Entry(String name, String category, String time, String space, String note) {
            this.name = name;
            this.category = category;
            this.time = time;
            this.space = space;
            this.note = note;
        }

        public String name() {
            return name;
        }

        public String category() {
            return category;
        }

        public String time() {
            return time;
        }

        public String space() {
            return space;
        }

        public String note() {
            return note;
        }

        @Override
        public String toString() {
            return String.format("%-26s %-14s time %-18s space %-14s %s",
                                 name, category, time, space, note);
        }
    }

    private static final Entry[] ENTRIES = {
        new Entry("Naive search", "String", "O(n*m)", "O(1)", "Reference implementation"),
        new Entry("KMP", "String", "O(n+m)", "O(m)", "No pathological input"),
        new Entry("Z-Algorithm", "String", "O(n+m)", "O(m)", "No sentinel required"),
        new Entry("Rabin-Karp", "String", "O(n+m) expected", "O(1)", "Every hash hit verified"),
        new Entry("Aho-Corasick", "String", "O(n+z)", "O(m)", "Multi-pattern, one pass"),
        new Entry("Suffix array", "String", "O(n log n)", "O(n)", "Prefix doubling, counting sort"),
        new Entry("LCP (Kasai)", "String", "O(n)", "O(n)", "Requires the suffix array"),

        new Entry("Levenshtein", "DP", "O(n*m)", "O(min(n,m))", "Rolling rows"),
        new Entry("Damerau-Levenshtein", "DP", "O(n*m)", "O(n*m)", "Unrestricted variant"),
        new Entry("Damerau-Levenshtein OSA", "DP", "O(n*m)", "O(n*m)", "Restricted variant"),
        new Entry("Needleman-Wunsch", "DP", "O(n*m)", "O(n*m)", "Global alignment"),
        new Entry("Smith-Waterman", "DP", "O(n*m)", "O(n*m)", "Local alignment"),

        new Entry("Ford-Fulkerson", "Graph", "O(E*maxflow)", "O(V)", "DFS augmenting paths"),
        new Entry("Edmonds-Karp", "Graph", "O(V*E^2)", "O(V)", "BFS augmenting paths"),
        new Entry("Dinic", "Graph", "O(V^2*E)", "O(V)", "O(E*sqrt(E)) on unit capacity"),
        new Entry("Bipartite matching", "Graph", "O(V*E)", "O(V)", "Kuhn's algorithm"),

        new Entry("Vertex cover", "Approximation", "O(V+E)", "O(V)", "Ratio 2"),
        new Entry("List scheduling", "Approximation", "O(n*m)", "O(n+m)", "Ratio 2 - 1/m"),
        new Entry("LPT scheduling", "Approximation", "O(n log n)", "O(n+m)", "Ratio 4/3 - 1/3m"),

        new Entry("Miller-Rabin", "Randomized", "O(k*log^3 n)", "O(1)", "Exact for 64-bit"),
        new Entry("Universal hashing", "Randomized", "O(1) / O(L)", "O(1)", "Collision <= 1/m"),
        new Entry("Reservoir sampling", "Randomized", "O(n)", "O(k)", "One pass, unknown length"),
    };

    private ComplexityRegistry() {
    }

    /** Every registered algorithm. */
    public static Entry[] all() {
        Entry[] copy = new Entry[ENTRIES.length];
        for (int i = 0; i < ENTRIES.length; i++) {
            copy[i] = ENTRIES[i];
        }
        return copy;
    }

    public static int size() {
        return ENTRIES.length;
    }

    /** Lookup by exact name, or null. */
    public static Entry byName(String name) {
        for (Entry entry : ENTRIES) {
            if (entry.name().equals(name)) {
                return entry;
            }
        }
        return null;
    }

    /** All entries in one category. */
    public static Entry[] byCategory(String category) {
        int count = 0;
        for (Entry entry : ENTRIES) {
            if (entry.category().equals(category)) {
                count++;
            }
        }
        Entry[] result = new Entry[count];
        int index = 0;
        for (Entry entry : ENTRIES) {
            if (entry.category().equals(category)) {
                result[index++] = entry;
            }
        }
        return result;
    }

    /** Prints the whole table. */
    public static void report() {
        System.out.println("TextHack algorithm complexity reference");
        System.out.println("=======================================");
        for (Entry entry : ENTRIES) {
            System.out.println(entry);
        }
        System.out.println();
        System.out.println(ENTRIES.length + " algorithms registered");
    }
}
