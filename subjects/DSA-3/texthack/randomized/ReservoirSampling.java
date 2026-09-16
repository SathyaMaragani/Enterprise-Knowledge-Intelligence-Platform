package texthack.randomized;

import texthack.core.Prng;

/**
 * Reservoir sampling (Algorithm R): a uniform sample of k items from a stream
 * whose length is not known in advance.
 *
 * <p>The constraint is what makes it interesting. Sampling k of n uniformly is
 * trivial when n is known; here the stream may be arbitrarily long, can only be
 * read once, and must not be buffered. Log processing and corpus sampling both
 * have exactly that shape.
 *
 * <h2>The algorithm</h2>
 * Keep the first k items. For item i (0-based, i ≥ k), pick j uniformly from
 * [0, i]; if j &lt; k, replace reservoir slot j with the new item.
 *
 * <h2>Why it is uniform</h2>
 * By induction, every item seen so far is in the reservoir with probability
 * k/(i+1). Item i enters with probability k/(i+1) directly. An earlier item
 * survives if the new item is rejected, or is accepted but displaces one of the
 * other k-1 slots -- and those cases multiply out to exactly k/(i+2) at the next
 * step. So every item of an n-element stream ends up present with probability
 * k/n, uniformly, using O(k) memory and one pass.
 *
 * <p>The test suite checks this empirically: sampling 1 of 10 many times should
 * put each element in roughly a tenth of the samples, and a biased
 * implementation (for example one drawing j from [0, i) instead of [0, i])
 * fails that check.
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>Time: O(n) for a stream of n items, O(1) per item.</li>
 *   <li>Space: O(k), independent of stream length.</li>
 * </ul>
 */
public final class ReservoirSampling {

    private final int capacity;
    private final Prng random;
    private final int[] reservoir;
    private long seen;

    /**
     * @param capacity number of items to retain, at least 1
     * @throws IllegalArgumentException on a non-positive capacity or null source
     */
    public ReservoirSampling(int capacity, Prng random) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        this.capacity = capacity;
        this.random = random;
        this.reservoir = new int[capacity];
        this.seen = 0L;
    }

    /** Offers one item from the stream. */
    public void offer(int value) {
        if (seen < capacity) {
            reservoir[(int) seen] = value;
        } else {
            // Inclusive upper bound: j must be able to equal seen, or early
            // items would be over-represented.
            long j = random.nextLong(seen + 1L);
            if (j < capacity) {
                reservoir[(int) j] = value;
            }
        }
        seen++;
    }

    /** Items retained so far; shorter than the capacity on a short stream. */
    public int[] sample() {
        int size = seen < capacity ? (int) seen : capacity;
        int[] result = new int[size];
        for (int i = 0; i < size; i++) {
            result[i] = reservoir[i];
        }
        return result;
    }

    /** Number of items offered. */
    public long seen() {
        return seen;
    }

    public int capacity() {
        return capacity;
    }

    /** Convenience: sample {@code k} items from an array in one pass. */
    public static int[] sample(int[] stream, int k, Prng random) {
        if (stream == null) {
            throw new IllegalArgumentException("stream must not be null");
        }
        ReservoirSampling sampler = new ReservoirSampling(k, random);
        for (int value : stream) {
            sampler.offer(value);
        }
        return sampler.sample();
    }
}
