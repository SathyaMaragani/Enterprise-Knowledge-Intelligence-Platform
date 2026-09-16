package texthack.engine;

import texthack.core.IntList;
import texthack.dp.Alignment;
import texthack.dp.Levenshtein;
import texthack.dp.NeedlemanWunsch;
import texthack.dp.SmithWaterman;
import texthack.randomized.MillerRabin;
import texthack.string.AhoCorasick;
import texthack.string.KmpSearch;
import texthack.string.LcpArray;
import texthack.string.SuffixArray;

/**
 * The public TextHack API: one entry point over the algorithm modules, plus a
 * query engine that binds them to a corpus.
 *
 * <p>Everything underneath is usable directly; this exists so a caller does not
 * have to know that exact search is KMP, multi-pattern is Aho-Corasick and
 * fuzzy matching is Levenshtein. That indirection is the point -- phase 1.7C can
 * swap the scorer behind {@link #similarity} without the caller changing.
 *
 * <p>No Spring, no HTTP, no persistence. This is a library the API layer calls
 * into, which keeps the DSA-3 subject independently testable.
 */
public final class TextHack {

    /** A fuzzy match: which candidate, and how far from the query. */
    public static final class FuzzyHit {
        private final int index;
        private final String value;
        private final int distance;
        private final double similarity;

        FuzzyHit(int index, String value, int distance, double similarity) {
            this.index = index;
            this.value = value;
            this.distance = distance;
            this.similarity = similarity;
        }

        public int index() {
            return index;
        }

        public String value() {
            return value;
        }

        public int distance() {
            return distance;
        }

        public double similarity() {
            return similarity;
        }

        @Override
        public String toString() {
            return value + " (distance " + distance + ")";
        }
    }

    private final String[] documents;

    /** Binds the engine to a corpus for {@link #execute}. */
    public TextHack(String[] documents) {
        if (documents == null) {
            throw new IllegalArgumentException("documents must not be null");
        }
        this.documents = new String[documents.length];
        for (int i = 0; i < documents.length; i++) {
            if (documents[i] == null) {
                throw new IllegalArgumentException("document " + i + " is null");
            }
            this.documents[i] = documents[i];
        }
    }

    public int documentCount() {
        return documents.length;
    }

    public String document(int index) {
        return documents[index];
    }

    // ------------------------------------------------------- pattern search

    /** Exact occurrences of {@code pattern} in {@code text}. Uses KMP: O(n+m). */
    public static int[] search(String text, String pattern) {
        return new KmpSearch().findAll(text, pattern);
    }

    /** Every occurrence of every pattern, in one pass. */
    public static AhoCorasick.Match[] searchAll(String text, String[] patterns) {
        return new AhoCorasick(patterns).findAll(text);
    }

    /** Indices of corpus documents containing {@code pattern}. */
    public int[] documentsContaining(String pattern) {
        IntList hits = new IntList();
        KmpSearch matcher = new KmpSearch();
        for (int i = 0; i < documents.length; i++) {
            if (matcher.findAll(documents[i], pattern).length > 0) {
                hits.add(i);
            }
        }
        return hits.toArray();
    }

    // -------------------------------------------------------------- fuzzy

    /**
     * Candidates within {@code maxDistance} edits of {@code query}, nearest
     * first.
     *
     * <p>Ties are broken by candidate order, so the result is deterministic.
     */
    public static FuzzyHit[] fuzzy(String[] candidates, String query, int maxDistance) {
        if (candidates == null || query == null) {
            throw new IllegalArgumentException("candidates and query must not be null");
        }
        if (maxDistance < 0) {
            throw new IllegalArgumentException("maxDistance must not be negative");
        }

        IntList indices = new IntList();
        IntList distances = new IntList();

        for (int i = 0; i < candidates.length; i++) {
            if (candidates[i] == null) {
                throw new IllegalArgumentException("candidate " + i + " is null");
            }
            // within() short-circuits on the length gap before computing the
            // matrix, which is what makes scanning a large candidate list cheap.
            if (Levenshtein.within(candidates[i], query, maxDistance)) {
                indices.add(i);
                distances.add(Levenshtein.distance(candidates[i], query));
            }
        }

        // Insertion sort by (distance, index). Small result sets, and it keeps
        // the ordering stable without a library sort.
        int n = indices.size();
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        for (int i = 1; i < n; i++) {
            int current = order[i];
            int j = i - 1;
            while (j >= 0 && worseThan(distances, indices, order[j], current)) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = current;
        }

        FuzzyHit[] hits = new FuzzyHit[n];
        for (int i = 0; i < n; i++) {
            int slot = order[i];
            int candidateIndex = indices.get(slot);
            String value = candidates[candidateIndex];
            int distance = distances.get(slot);
            hits[i] = new FuzzyHit(candidateIndex, value, distance,
                                   Levenshtein.similarity(value, query));
        }
        return hits;
    }

    private static boolean worseThan(IntList distances, IntList indices, int a, int b) {
        int da = distances.get(a);
        int db = distances.get(b);
        if (da != db) {
            return da > db;
        }
        return indices.get(a) > indices.get(b);
    }

    // --------------------------------------------------------- similarity

    /** Normalised edit similarity in 0..1, where 1 is identical. */
    public static double similarity(String a, String b) {
        return Levenshtein.similarity(a, b);
    }

    /** Global alignment of two strings end to end. */
    public static Alignment align(String a, String b) {
        return NeedlemanWunsch.align(a, b);
    }

    /** Best-scoring shared region of two strings. */
    public static Alignment localAlign(String a, String b) {
        return SmithWaterman.align(a, b);
    }

    // ----------------------------------------------------- suffix structures

    /** Suffix array of {@code text}. */
    public static int[] suffixArray(String text) {
        return SuffixArray.build(text);
    }

    /** LCP array of {@code text}, built from its suffix array. */
    public static int[] lcp(String text) {
        return LcpArray.build(text, SuffixArray.build(text));
    }

    /**
     * Longest substring occurring at least twice, or an empty string.
     *
     * <p>A direct payoff of having both structures: the answer is the largest
     * LCP entry, since adjacent suffixes in sorted order share the longest
     * prefixes.
     */
    public static String longestRepeatedSubstring(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        int[] sa = SuffixArray.build(text);
        int[] lcp = LcpArray.build(text, sa);

        int best = 0;
        int bestIndex = 0;
        for (int i = 1; i < lcp.length; i++) {
            if (lcp[i] > best) {
                best = lcp[i];
                bestIndex = sa[i];
            }
        }
        return best == 0 ? "" : text.substring(bestIndex, bestIndex + best);
    }

    // ------------------------------------------------------------ numbers

    /** Exact primality for any non-negative long in the supported range. */
    public static boolean isPrime(long n) {
        return MillerRabin.isPrime(n);
    }

    // ------------------------------------------------------- query engine

    /**
     * Runs a parsed query against the bound corpus.
     *
     * @return human-readable result lines
     */
    public String[] execute(Query query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }

        switch (query.kind()) {
            case FIND: {
                int[] hits = documentsContaining(query.term(0));
                String[] lines = new String[hits.length + 1];
                lines[0] = "find \"" + query.term(0) + "\": " + hits.length + " document(s)";
                for (int i = 0; i < hits.length; i++) {
                    int count = search(documents[hits[i]], query.term(0)).length;
                    lines[i + 1] = "  doc " + hits[i] + ": " + count + " occurrence(s)";
                }
                return lines;
            }
            case FIND_ALL: {
                AhoCorasick automaton = new AhoCorasick(query.terms());
                IntList perDocument = new IntList();
                int total = 0;
                for (String document : documents) {
                    int count = automaton.findAll(document).length;
                    perDocument.add(count);
                    total += count;
                }
                String[] lines = new String[documents.length + 1];
                lines[0] = "findall: " + total + " match(es) across "
                         + documents.length + " document(s)";
                for (int i = 0; i < documents.length; i++) {
                    lines[i + 1] = "  doc " + i + ": " + perDocument.get(i) + " match(es)";
                }
                return lines;
            }
            case FUZZY: {
                FuzzyHit[] hits = fuzzy(documents, query.term(0), query.threshold());
                String[] lines = new String[hits.length + 1];
                lines[0] = "fuzzy \"" + query.term(0) + "\" ~" + query.threshold()
                         + ": " + hits.length + " hit(s)";
                for (int i = 0; i < hits.length; i++) {
                    lines[i + 1] = "  doc " + hits[i].index()
                                 + ": distance " + hits[i].distance();
                }
                return lines;
            }
            case SIMILAR: {
                double score = similarity(query.term(0), query.term(1));
                return new String[] {
                    "similar: " + score
                };
            }
            case PRIME: {
                boolean prime = isPrime(query.number());
                return new String[] {
                    query.number() + " is " + (prime ? "prime" : "composite")
                };
            }
            default:
                throw new IllegalArgumentException("unsupported query kind: " + query.kind());
        }
    }

    /** Convenience: parse and run in one call. */
    public String[] run(String queryText) {
        return execute(QueryParser.parse(queryText));
    }
}
