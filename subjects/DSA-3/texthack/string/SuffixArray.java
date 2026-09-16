package texthack.string;

/**
 * Suffix array construction by prefix doubling with radix sort.
 *
 * <p>The suffix array of a string is the sorted order of its suffixes, given as
 * their starting offsets. It underpins substring search, longest-repeated-
 * substring, and -- combined with the LCP array -- most of what a text index
 * needs.
 *
 * <h2>Construction</h2>
 * The naive route sorts n suffixes with a comparison sort, and since comparing
 * two suffixes costs O(n) that lands at O(n² log n). Prefix doubling avoids it:
 * suffixes are sorted by their first 2^k characters, and the rank from round k
 * lets round k+1 compare 2^(k+1) characters in constant time, because each
 * suffix's second half is just another suffix whose rank is already known.
 *
 * <p>Each round sorts by the pair (rank[i], rank[i+k]). That is done with two
 * passes of counting sort rather than a comparison sort -- which keeps a round
 * at O(n) and, incidentally, avoids needing any sorting utility from
 * {@code java.util}, which the subject rules bar anyway.
 *
 * <h2>Determinism</h2>
 * Suffixes of a string are all distinct, so the sorted order is total and
 * unique: there are no ties to break and the output is fully determined by the
 * input. Counting sort is also stable, so intermediate rounds are reproducible
 * as well.
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>Time: O(n log n) -- log n doubling rounds, O(n) counting sort each.</li>
 *   <li>Space: O(n) -- a handful of int arrays of length n.</li>
 * </ul>
 */
public final class SuffixArray {

    private SuffixArray() {
    }

    /**
     * Builds the suffix array of {@code s}.
     *
     * @return offsets of all suffixes in ascending lexicographic order; empty
     *         for an empty string
     * @throws IllegalArgumentException if {@code s} is null
     */
    public static int[] build(String s) {
        if (s == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        int n = s.length();
        if (n == 0) {
            return new int[0];
        }
        if (n == 1) {
            return new int[] {0};
        }

        int[] suffixArray = new int[n];
        int[] rank = new int[n];
        int[] nextRank = new int[n];
        int[] bySecondKey = new int[n];

        int classes = initialSort(s, suffixArray, rank);

        for (int k = 1; classes < n; k <<= 1) {
            // Order by the second key first. A suffix whose second half runs off
            // the end has no successor and must sort first, so those are emitted
            // ahead of everything else.
            int index = 0;
            for (int i = n - k; i < n; i++) {
                bySecondKey[index++] = i;
            }
            for (int i = 0; i < n; i++) {
                if (suffixArray[i] >= k) {
                    bySecondKey[index++] = suffixArray[i] - k;
                }
            }

            // Stable counting sort by the first key, preserving the second-key
            // order established above. Stability is what makes the pair sort
            // correct with only one counting pass here.
            int[] count = new int[classes];
            for (int i = 0; i < n; i++) {
                count[rank[i]]++;
            }
            int running = 0;
            for (int c = 0; c < classes; c++) {
                int size = count[c];
                count[c] = running;
                running += size;
            }
            for (int i = 0; i < n; i++) {
                int suffix = bySecondKey[i];
                suffixArray[count[rank[suffix]]++] = suffix;
            }

            // Recompute ranks. Two adjacent suffixes share a rank only when both
            // halves of their pairs agree.
            nextRank[suffixArray[0]] = 0;
            classes = 1;
            for (int i = 1; i < n; i++) {
                int current = suffixArray[i];
                int previous = suffixArray[i - 1];
                if (rank[current] != rank[previous]
                        || secondKey(rank, current, k, n) != secondKey(rank, previous, k, n)) {
                    classes++;
                }
                nextRank[current] = classes - 1;
            }

            int[] swap = rank;
            rank = nextRank;
            nextRank = swap;

            if (k > n) {
                break; // guards against shifting past the string on huge inputs
            }
        }

        return suffixArray;
    }

    /** Rank of the half starting k past {@code i}, or -1 when it runs off the end. */
    private static int secondKey(int[] rank, int i, int k, int n) {
        return (i + k < n) ? rank[i + k] : -1;
    }

    /**
     * Counting sort by the first character, producing the round-0 ordering and
     * rank classes.
     *
     * <p>Counting runs over the full UTF-16 code unit range. That is a fixed
     * 65536-entry array regardless of input size, so it does not affect the
     * asymptotic bound, and it means no assumption is made about the alphabet.
     *
     * @return the number of distinct rank classes after this pass
     */
    private static int initialSort(String s, int[] suffixArray, int[] rank) {
        int n = s.length();
        int alphabet = 65536;
        int[] count = new int[alphabet];

        for (int i = 0; i < n; i++) {
            count[s.charAt(i)]++;
        }
        int running = 0;
        for (int c = 0; c < alphabet; c++) {
            int size = count[c];
            count[c] = running;
            running += size;
        }
        for (int i = 0; i < n; i++) {
            suffixArray[count[s.charAt(i)]++] = i;
        }

        rank[suffixArray[0]] = 0;
        int classes = 1;
        for (int i = 1; i < n; i++) {
            if (s.charAt(suffixArray[i]) != s.charAt(suffixArray[i - 1])) {
                classes++;
            }
            rank[suffixArray[i]] = classes - 1;
        }
        return classes;
    }
}
