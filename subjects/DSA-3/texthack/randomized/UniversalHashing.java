package texthack.randomized;

import texthack.core.Prng;

/**
 * Universal hash families.
 *
 * <p>A fixed hash function has a fixed worst case: for any deterministic h, some
 * input set collides badly, and if that set is attacker-chosen the structure
 * built on it degrades to a linked list. Universal hashing removes the fixed
 * target by drawing h at random from a family with a proven collision bound, so
 * the guarantee holds <em>in expectation over the choice of h</em> regardless of
 * which inputs arrive.
 *
 * <h2>Integer family (Carter-Wegman)</h2>
 * h(x) = ((a·x + b) mod p) mod m, with p prime and greater than the universe,
 * a drawn from [1, p) and b from [0, p). For any distinct x, y the collision
 * probability is at most 1/m -- the same as ideal random hashing.
 *
 * <p>The condition a ≠ 0 matters: a = 0 maps every key to b, which is why it is
 * excluded from the range rather than merely being unlikely.
 *
 * <h2>String family (randomised polynomial)</h2>
 * Treats the string as polynomial coefficients evaluated at a random base
 * modulo a prime. Two distinct strings of length L collide with probability at
 * most L/p, since a non-zero polynomial of degree L has at most L roots. This
 * is the same construction as the Rabin-Karp rolling hash, with the base chosen
 * randomly instead of fixed -- which is exactly what removes Rabin-Karp's
 * adversarial worst case.
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>Integer hash: O(1).</li>
 *   <li>String hash: O(L).</li>
 *   <li>Space: O(1).</li>
 * </ul>
 */
public final class UniversalHashing {

    /** Mersenne prime 2^31 - 1: comfortably above the int universe. */
    public static final long PRIME = 2147483647L;

    private final long multiplier;
    private final long offset;
    private final long buckets;
    private final long stringBase;

    /**
     * Draws a hash function from the family.
     *
     * @param buckets size of the output range, at least 1
     * @throws IllegalArgumentException on a non-positive bucket count or null source
     */
    public UniversalHashing(int buckets, Prng random) {
        if (buckets < 1) {
            throw new IllegalArgumentException("buckets must be at least 1");
        }
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        this.buckets = buckets;
        // a in [1, p): zero would collapse every key onto b.
        this.multiplier = 1L + random.nextLong(PRIME - 1L);
        this.offset = random.nextLong(PRIME);
        // A base of 0 or 1 destroys positional information in the string hash.
        this.stringBase = 2L + random.nextLong(PRIME - 3L);
    }

    public int buckets() {
        return (int) buckets;
    }

    /** h(x) for an integer key. */
    public int hash(int key) {
        long x = key & 0xFFFFFFFFL;
        long value = (multiplier * (x % PRIME) + offset) % PRIME;
        return (int) (value % buckets);
    }

    /** h(s) for a string key, by randomised polynomial evaluation. */
    public int hash(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        long value = 0L;
        for (int i = 0; i < key.length(); i++) {
            value = (value * stringBase + key.charAt(i)) % PRIME;
        }
        value = (multiplier * value + offset) % PRIME;
        return (int) (value % buckets);
    }

    /** The randomly chosen multiplier, exposed so tests can confirm a != 0. */
    public long multiplier() {
        return multiplier;
    }

    /** The randomly chosen offset. */
    public long offset() {
        return offset;
    }
}
