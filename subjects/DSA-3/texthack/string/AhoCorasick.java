package texthack.string;

import texthack.core.CharMap;
import texthack.core.IntList;

/**
 * Aho-Corasick multi-pattern matching.
 *
 * <p>Finds every occurrence of every pattern in a single left-to-right pass over
 * the text. Running a single-pattern matcher once per pattern costs O(k·n) for
 * k patterns; Aho-Corasick costs O(n) for the scan regardless of how many
 * patterns there are, which is the entire reason it exists.
 *
 * <h2>Why this does not implement {@code StringMatcher}</h2>
 * {@code StringMatcher} answers "where does this one pattern occur", returning
 * bare offsets. That signature cannot express a multi-pattern result: a caller
 * needs to know <em>which</em> pattern matched, not just where something did.
 * Forcing this class into that interface would mean either returning offsets
 * with the pattern identity discarded, or constructing one automaton per pattern
 * and throwing away the algorithm's only advantage. It gets its own API instead,
 * and the four single-pattern matchers are untouched.
 *
 * <h2>Structure</h2>
 * Three things are built over a trie of the patterns:
 * <ul>
 *   <li><b>goto</b> -- the trie edges themselves, one child map per node.</li>
 *   <li><b>failure links</b> -- from each node, the node representing the
 *       longest proper suffix of its path that is also a prefix of some pattern.
 *       On a mismatch the automaton follows these instead of restarting.</li>
 *   <li><b>output links</b> -- a shortcut chain to the next node up the failure
 *       path that terminates a pattern. Without these, reporting every match at
 *       a position would mean walking the whole failure chain each time; with
 *       them, only nodes that actually carry output are visited.</li>
 * </ul>
 *
 * <h2>Overlapping and nested patterns</h2>
 * Both are reported. The classic case is the set {@code {he, she, his, hers}}
 * against {@code "ushers"}: {@code she} at 1, {@code he} at 2 and {@code hers}
 * at 2 all occur, and {@code he} is a proper substring of both {@code she} and
 * {@code hers}. The output-link chain is what surfaces the nested ones -- a
 * match is reported at the node that terminates it even when the automaton is
 * currently sitting deeper in the trie.
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>Build: O(m) time and O(m) space, m = total length of all patterns.</li>
 *   <li>Search: O(n + z) where z is the number of matches reported. The scan is
 *       linear; the +z is unavoidable since each match must be emitted.</li>
 * </ul>
 */
public final class AhoCorasick {

    /** A single occurrence: which pattern, and where it sits in the text. */
    public static final class Match {
        private final int patternIndex;
        private final int start;
        private final int length;

        Match(int patternIndex, int start, int length) {
            this.patternIndex = patternIndex;
            this.start = start;
            this.length = length;
        }

        /** Index of the matched pattern in the array given to the constructor. */
        public int patternIndex() {
            return patternIndex;
        }

        /** Start offset in the text, inclusive. */
        public int start() {
            return start;
        }

        /** End offset in the text, exclusive. */
        public int end() {
            return start + length;
        }

        public int length() {
            return length;
        }

        @Override
        public String toString() {
            return "Match(pattern=" + patternIndex + ", start=" + start + ", end=" + end() + ")";
        }
    }

    private static final int ROOT = 0;

    private final String[] patterns;

    // Node-indexed automaton state. Parallel arrays rather than a node object
    // per state: no java.util, and the arrays are the natural representation.
    private CharMap[] children;
    private int[] failure;
    private int[] outputLink;      // next node up the failure chain with output, or -1
    private IntList[] outputs;     // pattern indices terminating at a node, or null
    private int nodeCount;

    /**
     * Builds the automaton.
     *
     * @param patterns patterns to search for; must be non-null, non-empty
     *                 strings. Duplicates are permitted and each is reported
     *                 under its own index.
     * @throws IllegalArgumentException on a null array, a null pattern, or an
     *         empty pattern. An empty pattern would match at every position,
     *         which is degenerate rather than useful, so it is rejected
     *         explicitly instead of being silently skipped.
     */
    public AhoCorasick(String[] patterns) {
        if (patterns == null) {
            throw new IllegalArgumentException("patterns must not be null");
        }
        for (int i = 0; i < patterns.length; i++) {
            if (patterns[i] == null) {
                throw new IllegalArgumentException("pattern " + i + " is null");
            }
            if (patterns[i].isEmpty()) {
                throw new IllegalArgumentException("pattern " + i + " is empty");
            }
        }

        this.patterns = new String[patterns.length];
        for (int i = 0; i < patterns.length; i++) {
            this.patterns[i] = patterns[i];
        }

        int capacity = 1;
        for (String p : patterns) {
            capacity += p.length();
        }
        this.children = new CharMap[capacity];
        this.failure = new int[capacity];
        this.outputLink = new int[capacity];
        this.outputs = new IntList[capacity];
        this.nodeCount = 0;

        createNode(); // ROOT
        for (int i = 0; i < this.patterns.length; i++) {
            insert(this.patterns[i], i);
        }
        buildLinks();
    }

    public int patternCount() {
        return patterns.length;
    }

    /** Number of automaton states, including the root. Exposed for tests. */
    public int nodeCount() {
        return nodeCount;
    }

    private int createNode() {
        int node = nodeCount;
        children[node] = new CharMap();
        failure[node] = ROOT;
        outputLink[node] = -1;
        outputs[node] = null;
        nodeCount++;
        return node;
    }

    private void insert(String pattern, int patternIndex) {
        int node = ROOT;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            int next = children[node].get(c);
            if (next == -1) {
                next = createNode();
                children[node].put(c, next);
            }
            node = next;
        }
        if (outputs[node] == null) {
            outputs[node] = new IntList(2);
        }
        // Duplicate patterns land on the same node; each keeps its own index.
        outputs[node].add(patternIndex);
    }

    /**
     * Breadth-first construction of failure and output links.
     *
     * <p>BFS order matters: a node's failure link is computed from its parent's,
     * so every parent must be finished first. The queue is a plain int array
     * sized to the node count, with head and tail cursors -- a node enters it
     * exactly once, so it can never overflow.
     */
    private void buildLinks() {
        int[] queue = new int[nodeCount];
        int head = 0;
        int tail = 0;

        CharMap rootChildren = children[ROOT];
        for (int i = 0; i < rootChildren.size(); i++) {
            int child = rootChildren.valueAt(i);
            failure[child] = ROOT;
            queue[tail++] = child;
        }

        while (head < tail) {
            int node = queue[head++];
            CharMap map = children[node];

            for (int i = 0; i < map.size(); i++) {
                char c = map.keyAt(i);
                int child = map.valueAt(i);

                // Walk the failure chain until a state has an edge on c, or the
                // root is reached.
                int candidate = failure[node];
                while (candidate != ROOT && children[candidate].get(c) == -1) {
                    candidate = failure[candidate];
                }
                int target = children[candidate].get(c);
                failure[child] = (target != -1 && target != child) ? target : ROOT;

                // A node inherits the nearest output-bearing state above it, so
                // nested patterns are reachable in O(number of matches).
                int f = failure[child];
                outputLink[child] = (outputs[f] != null) ? f : outputLink[f];

                queue[tail++] = child;
            }
        }
    }

    /**
     * Finds every occurrence of every pattern.
     *
     * <p>Results are ordered by end position ascending. Within one end position
     * the longest match comes first, because the output chain is walked from the
     * current (deepest) node upwards; ties beyond that follow pattern insertion
     * order. The ordering is fully determined by the input -- the same call
     * always produces the same array.
     *
     * @throws IllegalArgumentException if {@code text} is null
     */
    public Match[] findAll(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }

        IntList flat = new IntList(); // triples: patternIndex, start, length
        int node = ROOT;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            while (node != ROOT && children[node].get(c) == -1) {
                node = failure[node];
            }
            int next = children[node].get(c);
            node = (next == -1) ? ROOT : next;

            for (int out = (outputs[node] != null) ? node : outputLink[node];
                 out != -1;
                 out = outputLink[out]) {
                IntList terminal = outputs[out];
                for (int k = 0; k < terminal.size(); k++) {
                    int patternIndex = terminal.get(k);
                    int length = patterns[patternIndex].length();
                    flat.add(patternIndex);
                    flat.add(i - length + 1);
                    flat.add(length);
                }
            }
        }

        int count = flat.size() / 3;
        Match[] matches = new Match[count];
        for (int i = 0; i < count; i++) {
            matches[i] = new Match(flat.get(i * 3), flat.get(i * 3 + 1), flat.get(i * 3 + 2));
        }
        return matches;
    }

    /** Convenience: start offsets for one pattern index, ascending. */
    public int[] findAll(String text, int patternIndex) {
        if (patternIndex < 0 || patternIndex >= patterns.length) {
            throw new IndexOutOfBoundsException("pattern index " + patternIndex);
        }
        Match[] all = findAll(text);
        IntList starts = new IntList();
        for (Match match : all) {
            if (match.patternIndex() == patternIndex) {
                starts.add(match.start());
            }
        }
        return starts.toArray();
    }
}
