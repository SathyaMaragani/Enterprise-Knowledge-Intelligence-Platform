package texthack.randomized;

import texthack.core.Prng;

/**
 * Miller-Rabin primality testing.
 *
 * <p>Writes n-1 as d·2^s with d odd, then for a witness a checks whether
 * a^d ≡ 1 or a^(d·2^r) ≡ -1 for some r &lt; s. A prime always satisfies one of
 * these. A composite satisfies them for at most a quarter of possible witnesses,
 * so each independent witness cuts the error probability by at least 4.
 *
 * <h2>Probabilistic or deterministic, depending on the witnesses</h2>
 * With random witnesses this is a probabilistic test: k rounds leave an error
 * probability below 4^-k, one-sided (it never calls a prime composite).
 *
 * <p>But for bounded inputs, specific small witness sets are <em>proven</em>
 * sufficient. The first 12 primes {2, 3, ..., 37} decide every n below
 * 3.3 × 10^24, which covers the entire signed 64-bit range. So
 * {@link #isPrime(long)} is exact for any {@code long}, not merely probable.
 * The randomised variant is kept alongside it because the subject asks for a
 * randomized algorithm, and because it is what generalises beyond 64 bits.
 *
 * <h2>Overflow</h2>
 * The obvious {@code (a * b) % m} overflows a {@code long} whenever m exceeds
 * about 3 × 10^9 -- silently, producing wrong answers rather than an error.
 * Using {@code BigInteger.modPow} would sidestep it but would also be replacing
 * the algorithm with a library call, which the subject rules bar. So
 * multiplication is done by Russian-peasant doubling, which never exceeds the
 * operand range and costs an extra O(log n) factor. That is where the log³
 * rather than log² in the complexity comes from.
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>Time: O(k · log³ n) for k witnesses, from O(log n) multiplications each
 *       costing O(log n) doublings inside O(log n) squarings.</li>
 *   <li>Space: O(1).</li>
 * </ul>
 */
public final class MillerRabin {

    /**
     * Deterministic for every n that fits in a signed long.
     * (Sufficient below 3,317,044,064,679,887,385,961,981.)
     */
    private static final long[] DETERMINISTIC_WITNESSES = {
        2L, 3L, 5L, 7L, 11L, 13L, 17L, 19L, 23L, 29L, 31L, 37L
    };

    /** Safe modulus ceiling: doubling adds two values below m, so 2m must not overflow. */
    private static final long MAX_MODULUS = Long.MAX_VALUE / 2;

    private MillerRabin() {
    }

    /**
     * Exact primality for any non-negative {@code long}.
     *
     * @throws IllegalArgumentException if n is negative or above the safe modulus
     */
    public static boolean isPrime(long n) {
        return test(n, DETERMINISTIC_WITNESSES);
    }

    /**
     * Probabilistic primality with {@code rounds} random witnesses.
     *
     * <p>Error probability below 4^-rounds, and one-sided: a number reported
     * composite is certainly composite.
     */
    public static boolean isProbablePrime(long n, int rounds, Prng random) {
        if (rounds < 1) {
            throw new IllegalArgumentException("rounds must be at least 1");
        }
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        Boolean quick = trivialCases(n);
        if (quick != null) {
            return quick;
        }

        long[] witnesses = new long[rounds];
        for (int i = 0; i < rounds; i++) {
            // Witnesses are drawn from [2, n-2].
            witnesses[i] = 2L + random.nextLong(n - 3L);
        }
        return test(n, witnesses);
    }

    private static Boolean trivialCases(long n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must not be negative");
        }
        if (n > MAX_MODULUS) {
            throw new IllegalArgumentException(
                "n exceeds the range this implementation can multiply safely");
        }
        if (n < 2) {
            return Boolean.FALSE;
        }
        if (n < 4) {
            return Boolean.TRUE; // 2 and 3
        }
        if ((n & 1L) == 0L) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static boolean test(long n, long[] witnesses) {
        Boolean quick = trivialCases(n);
        if (quick != null) {
            return quick;
        }

        // n - 1 = d * 2^s with d odd.
        long d = n - 1;
        int s = 0;
        while ((d & 1L) == 0L) {
            d >>= 1;
            s++;
        }

        for (long a : witnesses) {
            long base = a % n;
            if (base == 0) {
                continue; // a multiple of n proves nothing
            }
            if (!passesWitness(n, base, d, s)) {
                return false;
            }
        }
        return true;
    }

    private static boolean passesWitness(long n, long a, long d, int s) {
        long x = powMod(a, d, n);
        if (x == 1L || x == n - 1L) {
            return true;
        }
        for (int r = 1; r < s; r++) {
            x = mulMod(x, x, n);
            if (x == n - 1L) {
                return true;
            }
        }
        return false;
    }

    /**
     * Modular exponentiation by repeated squaring.
     *
     * <p>Public because it is a reusable primitive rather than an internal step
     * of this test: modular exponentiation underpins Fermat-style checks,
     * Diffie-Hellman style arithmetic and polynomial hashing.
     *
     * <p>Time: O(log exponent) multiplications. Space: O(1).
     */
    public static long powMod(long base, long exponent, long modulus) {
        long result = 1L;
        long b = base % modulus;
        long e = exponent;
        while (e > 0) {
            if ((e & 1L) == 1L) {
                result = mulMod(result, b, modulus);
            }
            b = mulMod(b, b, modulus);
            e >>= 1;
        }
        return result;
    }

    /**
     * Modular multiplication by doubling, avoiding the overflow that a direct
     * {@code a * b} would hit for large moduli.
     *
     * <p>Public for the same reason as {@link #powMod}: any code doing modular
     * arithmetic above roughly 3e9 needs this rather than the obvious
     * {@code (a * b) % m}, which overflows silently and returns a wrong answer
     * instead of failing.
     *
     * <p>Time: O(log b). Space: O(1). Requires {@code modulus <= Long.MAX_VALUE / 2}.
     */
    public static long mulMod(long a, long b, long modulus) {
        long result = 0L;
        long x = a % modulus;
        long y = b;
        while (y > 0) {
            if ((y & 1L) == 1L) {
                result += x;
                if (result >= modulus) {
                    result -= modulus;
                }
            }
            x <<= 1;
            if (x >= modulus) {
                x -= modulus;
            }
            y >>= 1;
        }
        return result;
    }
}
