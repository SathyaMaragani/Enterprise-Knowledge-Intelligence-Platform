package texthack.string;

/**
 * Longest-common-prefix array construction by Kasai's algorithm.
 *
 * <p>Given a string and its suffix array, {@code lcp[i]} is the length of the
 * longest common prefix shared by the suffixes at ranks {@code i-1} and
 * {@code i}. By convention {@code lcp[0]} is 0, since the first suffix has no
 * predecessor to share a prefix with.
 *
 * <h2>Why this is linear</h2>
 * Computing each adjacent pair independently costs O(n) per pair and O(n²)
 * overall. Kasai's insight is to walk the suffixes in <em>text</em> order rather
 * than rank order, and to observe that removing the first character of a suffix
 * can shorten its LCP with its predecessor by at most one. So the running length
 * {@code h} decreases by at most 1 per text position and increases only when a
 * character actually matches. Both the total increase and total decrease are
 * bounded by n, giving O(n) overall even though the inner loop looks quadratic.
 *
 * <h2>Repeated and overlapping prefixes</h2>
 * Highly repetitive input is where a broken implementation shows up. In
 * {@code "aaaa"} the suffixes are nested, and the LCP array is {@code [0,1,2,3]}
 * -- each suffix shares everything but one character with the next. The carried
 * {@code h} handles this without rescanning: it is exactly the overlap being
 * reused.
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>Time: O(n).</li>
 *   <li>Space: O(n) for the rank array and the result.</li>
 * </ul>
 */
public final class LcpArray {

    private LcpArray() {
    }

    /**
     * Builds the LCP array for {@code s} given its suffix array.
     *
     * @param s the text
     * @param suffixArray the suffix array of {@code s}, as produced by
     *        {@link SuffixArray#build(String)}
     * @return an array of the same length, where index i holds the LCP of the
     *         suffixes at ranks i-1 and i, and index 0 is 0
     * @throws IllegalArgumentException if either argument is null or their
     *         lengths disagree
     */
    public static int[] build(String s, int[] suffixArray) {
        if (s == null || suffixArray == null) {
            throw new IllegalArgumentException("text and suffix array must not be null");
        }
        int n = s.length();
        if (suffixArray.length != n) {
            throw new IllegalArgumentException(
                "suffix array length " + suffixArray.length + " does not match text length " + n);
        }
        if (n == 0) {
            return new int[0];
        }

        // rank is the inverse permutation: where each suffix sits in the order.
        int[] rank = new int[n];
        for (int i = 0; i < n; i++) {
            rank[suffixArray[i]] = i;
        }

        int[] lcp = new int[n];
        int h = 0;

        for (int i = 0; i < n; i++) {
            if (rank[i] > 0) {
                int previousSuffix = suffixArray[rank[i] - 1];
                while (i + h < n && previousSuffix + h < n
                        && s.charAt(i + h) == s.charAt(previousSuffix + h)) {
                    h++;
                }
                lcp[rank[i]] = h;
                if (h > 0) {
                    // Dropping the leading character can cost at most one
                    // character of overlap, so the rest is carried forward
                    // rather than recomputed. This is what makes it O(n).
                    h--;
                }
            } else {
                // Rank 0 has no predecessor; nothing is carried into the next
                // text position.
                h = 0;
            }
        }

        return lcp;
    }
}
