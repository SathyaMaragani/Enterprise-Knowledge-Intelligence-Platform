package texthack.string;

import texthack.core.IntList;
import texthack.core.StringMatcher;

/**
 * Brute-force pattern matching: try every alignment, compare left to right.
 *
 * <p>Slow, but it is the reference implementation. The other matchers are
 * checked against this one on randomised input, so it is worth keeping the
 * logic too simple to be wrong.
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>Time: O(n*m) worst case. The pathological input is a text and pattern
 *       sharing a long repeated prefix, e.g. text {@code "aaaa...a"} and pattern
 *       {@code "aaa...ab"}: every alignment compares m-1 characters before
 *       failing on the last.</li>
 *   <li>Time: O(n) on typical text, where mismatches occur within the first
 *       couple of characters.</li>
 *   <li>Space: O(1) beyond the output.</li>
 * </ul>
 */
public final class NaiveSearch implements StringMatcher {

    @Override
    public String name() {
        return "Naive";
    }

    @Override
    public int[] findAll(String text, String pattern) {
        if (StringMatcher.isTriviallyEmpty(text, pattern)) {
            return new int[0];
        }

        int n = text.length();
        int m = pattern.length();
        IntList matches = new IntList();

        for (int start = 0; start + m <= n; start++) {
            int offset = 0;
            while (offset < m && text.charAt(start + offset) == pattern.charAt(offset)) {
                offset++;
            }
            if (offset == m) {
                matches.add(start);
            }
        }

        return matches.toArray();
    }
}
