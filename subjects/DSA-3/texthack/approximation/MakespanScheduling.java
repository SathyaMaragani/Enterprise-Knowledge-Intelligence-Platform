package texthack.approximation;

/**
 * Approximate makespan minimisation on identical parallel machines.
 *
 * <p>Given job durations and m machines, assign every job so that the time the
 * last machine finishes -- the makespan -- is as small as possible. The decision
 * version is NP-complete (it contains Partition), so these are approximations
 * with proven ratios rather than exact solvers.
 *
 * <h2>Two algorithms</h2>
 * <ul>
 *   <li><b>List scheduling</b> takes jobs in the order given and puts each on
 *       whichever machine is currently least loaded. Ratio: 2 - 1/m.</li>
 *   <li><b>LPT</b> (longest processing time first) sorts jobs descending and
 *       then does exactly the same thing. Ratio: 4/3 - 1/(3m).</li>
 * </ul>
 *
 * <p>The only difference is the order, and that alone improves the worst-case
 * guarantee from 2 to 4/3. The reason is that long jobs are the ones that can
 * strand a machine: scheduled last, a long job lands on top of an already-full
 * machine and extends the makespan by its whole duration. Placed first, it is
 * absorbed while every machine is still empty.
 *
 * <p>The classic case is m machines, m(m-1) jobs of length 1 and one job of
 * length m. In the worst list order the long job arrives last and the makespan
 * is 2m-1; LPT places it first and finishes at m+1 (optimum is m).
 *
 * <h2>Note on the sort</h2>
 * LPT needs a descending sort and {@code java.util.Arrays.sort} is barred, so
 * this uses a hand-written heapsort: O(n log n), in place, and with no worst
 * case (unlike a naive quicksort, whose O(n²) behaviour on already-sorted input
 * would be a real risk here since job lists often arrive pre-ordered).
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>List scheduling: O(n·m), or O(n log m) with a heap over machines.</li>
 *   <li>LPT: O(n log n) to sort, then the same assignment pass.</li>
 *   <li>Space: O(n + m).</li>
 * </ul>
 */
public final class MakespanScheduling {

    /** A schedule: which machine each job went to, and the resulting loads. */
    public static final class Schedule {
        private final int[] assignment;
        private final long[] loads;
        private final long makespan;

        Schedule(int[] assignment, long[] loads, long makespan) {
            this.assignment = assignment;
            this.loads = loads;
            this.makespan = makespan;
        }

        /** Machine index for each job, indexed as the original job array. */
        public int[] assignment() {
            return assignment;
        }

        /** Total duration assigned to each machine. */
        public long[] loads() {
            return loads;
        }

        /** Finish time of the last machine. */
        public long makespan() {
            return makespan;
        }
    }

    private MakespanScheduling() {
    }

    /**
     * List scheduling in the given job order. Ratio 2 - 1/m.
     *
     * @throws IllegalArgumentException on null jobs, a negative duration, or
     *         fewer than one machine
     */
    public static Schedule listScheduling(long[] jobs, int machines) {
        validate(jobs, machines);
        int[] order = new int[jobs.length];
        for (int i = 0; i < jobs.length; i++) {
            order[i] = i;
        }
        return assign(jobs, machines, order);
    }

    /**
     * Longest processing time first. Ratio 4/3 - 1/(3m).
     */
    public static Schedule longestProcessingTime(long[] jobs, int machines) {
        validate(jobs, machines);
        int[] order = descendingOrder(jobs);
        return assign(jobs, machines, order);
    }

    /**
     * A lower bound on the optimal makespan.
     *
     * <p>The optimum is at least the largest single job (it must run somewhere,
     * uninterrupted) and at least the total work divided evenly. Neither bound
     * is always tight, but the larger of the two is enough to verify an
     * approximation ratio without solving the NP-hard problem.
     */
    public static long lowerBound(long[] jobs, int machines) {
        validate(jobs, machines);
        long total = 0;
        long longest = 0;
        for (long job : jobs) {
            total += job;
            if (job > longest) {
                longest = job;
            }
        }
        long average = (total + machines - 1) / machines; // ceiling
        return average > longest ? average : longest;
    }

    private static void validate(long[] jobs, int machines) {
        if (jobs == null) {
            throw new IllegalArgumentException("jobs must not be null");
        }
        if (machines < 1) {
            throw new IllegalArgumentException("machines must be at least 1");
        }
        for (long job : jobs) {
            if (job < 0) {
                throw new IllegalArgumentException("job durations must not be negative");
            }
        }
    }

    /** Places each job, in {@code order}, onto the least loaded machine. */
    private static Schedule assign(long[] jobs, int machines, int[] order) {
        long[] loads = new long[machines];
        int[] assignment = new int[jobs.length];

        for (int index : order) {
            int lightest = 0;
            for (int m = 1; m < machines; m++) {
                if (loads[m] < loads[lightest]) {
                    lightest = m;
                }
            }
            assignment[index] = lightest;
            loads[lightest] += jobs[index];
        }

        long makespan = 0;
        for (long load : loads) {
            if (load > makespan) {
                makespan = load;
            }
        }
        return new Schedule(assignment, loads, makespan);
    }

    /**
     * Indices of {@code jobs} ordered by descending duration, via heapsort.
     *
     * <p>Heapsort rather than quicksort because its O(n log n) bound has no bad
     * input; job lists arriving already sorted is common and is exactly what
     * degrades naive quicksort to O(n²).
     */
    private static int[] descendingOrder(long[] jobs) {
        int n = jobs.length;
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }

        // Build a min-heap by duration, then repeatedly extract; pulling the
        // smallest to the back leaves descending order at the front.
        for (int i = n / 2 - 1; i >= 0; i--) {
            siftDown(jobs, order, i, n);
        }
        for (int end = n - 1; end > 0; end--) {
            int swap = order[0];
            order[0] = order[end];
            order[end] = swap;
            siftDown(jobs, order, 0, end);
        }
        return order;
    }

    private static void siftDown(long[] jobs, int[] order, int root, int size) {
        while (true) {
            int smallest = root;
            int left = 2 * root + 1;
            int right = left + 1;

            if (left < size && jobs[order[left]] < jobs[order[smallest]]) {
                smallest = left;
            }
            if (right < size && jobs[order[right]] < jobs[order[smallest]]) {
                smallest = right;
            }
            if (smallest == root) {
                return;
            }
            int swap = order[root];
            order[root] = order[smallest];
            order[smallest] = swap;
            root = smallest;
        }
    }
}
