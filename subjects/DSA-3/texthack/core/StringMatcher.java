package texthack.core;

/**
 * A pattern-matching algorithm.
 *
 * <p>Every implementation must agree on the same contract, because the test
 * suite cross-validates them against each other: any disagreement is a bug in
 * one of them, which is only a usable signal if the semantics are identical.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>Returns the starting indices of every occurrence, ascending.</li>
 *   <li>Occurrences may overlap. Searching {@code "aaa"} for {@code "aa"}
 *       yields {@code [0, 1]}, not {@code [0]}.</li>
 *   <li>An empty pattern yields no matches. Returning a match at every position
 *       would be equally defensible, so the choice is arbitrary -- but it must
 *       be the <em>same</em> arbitrary choice everywhere.</li>
 *   <li>A pattern longer than the text yields no matches.</li>
 *   <li>Null text or pattern raises {@link IllegalArgumentException}.</li>
 * </ul>
 */
public interface StringMatcher {

    /** Starting indices of every occurrence of {@code pattern} in {@code text}. */
    int[] findAll(String text, String pattern);

    /** Human-readable name, used in benchmark and test output. */
    String name();

    /**
     * Shared argument checking, so all implementations reject the same inputs.
     *
     * @return true when the search can be skipped entirely
     */
    static boolean isTriviallyEmpty(String text, String pattern) {
        if (text == null || pattern == null) {
            throw new IllegalArgumentException("text and pattern must not be null");
        }
        return pattern.isEmpty() || pattern.length() > text.length();
    }
}
