package texthack.core;

/**
 * A deterministic pseudo-random generator (SplitMix64).
 *
 * <p>The randomized algorithms in this subject need a source of randomness, and
 * {@code java.util.Random} is barred. That turns out to be an improvement rather
 * than a constraint: {@code Random} is a 48-bit linear congruential generator
 * whose low bits are notoriously weak, and Miller-Rabin choosing poor witnesses
 * is precisely the failure mode that makes a primality test quietly wrong.
 *
 * <p>SplitMix64 is a counter-based generator: state advances by a fixed odd
 * increment and the output is a strong mixing function applied to it. It passes
 * BigCrush, needs no warm-up, has a full 2^64 period by construction, and is
 * about fifteen lines.
 *
 * <p>Every instance is seeded explicitly. Nothing here reads a clock or any
 * ambient entropy, so a run that fails can always be reproduced from its seed --
 * which is the property the test suites depend on.
 *
 * <p>Not thread-safe, and not cryptographically secure. Neither is required
 * here, and claiming otherwise would be worse than saying so.
 */
public final class Prng {

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long MIX_A = 0xBF58476D1CE4E5B9L;
    private static final long MIX_B = 0x94D049BB133111EBL;

    private long state;

    public Prng(long seed) {
        this.state = seed;
    }

    /** Uniform over the full 64-bit range. */
    public long nextLong() {
        state += GOLDEN_GAMMA;
        long z = state;
        z = (z ^ (z >>> 30)) * MIX_A;
        z = (z ^ (z >>> 27)) * MIX_B;
        return z ^ (z >>> 31);
    }

    /** Uniform over 0 (inclusive) to {@code bound} (exclusive). */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }

        // Rejection sampling. Taking a plain remainder would bias the low values
        // whenever bound does not divide the range evenly -- small bias, but it
        // is exactly the kind that makes a randomized algorithm's guarantees
        // stop holding.
        int bits;
        int value;
        do {
            bits = (int) (nextLong() >>> 33);
            value = bits % bound;
        } while (bits - value + (bound - 1) < 0);
        return value;
    }

    /** Uniform over 0 (inclusive) to {@code bound} (exclusive). */
    public long nextLong(long bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        long bits;
        long value;
        do {
            bits = nextLong() >>> 1;
            value = bits % bound;
        } while (bits - value + (bound - 1) < 0);
        return value;
    }

    /** Uniform over 0.0 (inclusive) to 1.0 (exclusive). */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    public boolean nextBoolean() {
        return nextLong() < 0;
    }
}
