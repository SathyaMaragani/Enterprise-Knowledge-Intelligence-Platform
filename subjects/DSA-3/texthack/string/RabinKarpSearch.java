package texthack.string;

import texthack.core.IntList;
import texthack.core.StringMatcher;

/**
 * Rabin-Karp pattern matching by rolling hash.
 *
 * <p>Rather than comparing characters at every alignment, this compares a hash
 * of the window against the hash of the pattern. The hash is <em>rolling</em>:
 * advancing one position removes the leading character's contribution and adds
 * the trailing one, so each step is O(1) instead of O(m).
 *
 * <h2>Hash collisions are handled, not assumed away</h2>
 * Equal hashes do not imply equal strings. Every hash hit is confirmed by an
 * explicit character comparison before being reported. Skipping that check is
 * the classic Rabin-Karp bug: it passes every small test and then returns a
 * false positive on real data, because with a 32-bit-ish modulus collisions are
 * not rare at corpus scale.
 *
 * <p>The modulus is a large prime and arithmetic is done in {@code long} to
 * avoid overflow before the modulo is applied.
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>Time: O(n + m) expected. Verification costs O(m) per hash hit, and hits
 *       are rare when the hash is well distributed.</li>
 *   <li>Time: O(n*m) worst case, when every window collides -- achievable only
 *       by an adversary who knows the modulus and base.</li>
 *   <li>Space: O(1) beyond the output.</li>
 * </ul>
 */
public final class RabinKarpSearch implements StringMatcher {

    /** Large prime modulus; small enough that base*hash stays inside a long. */
    private static final long MODULUS = 1_000_000_007L;

    /** Alphabet base. 256 covers the full char range used by charAt. */
    private static final long BASE = 256L;

    @Override
    public String name() {
        return "Rabin-Karp";
    }

    @Override
    public int[] findAll(String text, String pattern) {
        if (StringMatcher.isTriviallyEmpty(text, pattern)) {
            return new int[0];
        }

        int n = text.length();
        int m = pattern.length();
        IntList matches = new IntList();

        // BASE^(m-1) mod MODULUS, the weight of the character leaving the window.
        long highOrder = 1L;
        for (int i = 0; i < m - 1; i++) {
            highOrder = (highOrder * BASE) % MODULUS;
        }

        long patternHash = 0L;
        long windowHash = 0L;
        for (int i = 0; i < m; i++) {
            patternHash = (patternHash * BASE + pattern.charAt(i)) % MODULUS;
            windowHash = (windowHash * BASE + text.charAt(i)) % MODULUS;
        }

        for (int start = 0; start + m <= n; start++) {
            if (windowHash == patternHash && regionMatches(text, start, pattern, m)) {
                matches.add(start);
            }

            if (start + m < n) {
                // Roll: drop text[start], shift left, add text[start + m].
                long leaving = (text.charAt(start) * highOrder) % MODULUS;
                windowHash = (windowHash - leaving + MODULUS) % MODULUS;
                windowHash = (windowHash * BASE + text.charAt(start + m)) % MODULUS;
            }
        }

        return matches.toArray();
    }

    /** Explicit confirmation that a hash hit is a real match. */
    private static boolean regionMatches(String text, int start, String pattern, int m) {
        for (int i = 0; i < m; i++) {
            if (text.charAt(start + i) != pattern.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
