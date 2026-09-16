# TextHack — Complexity Reference

Time and space complexity for every implemented algorithm. `n` is the text
length, `m` the pattern length, and output space (the match list) is excluded
from the space column since every matcher pays it equally.

## String Matching

| Algorithm | Preprocess | Search (worst) | Search (expected) | Space | Notes |
|---|---|---|---|---|---|
| Naive | — | O(n·m) | O(n) | O(1) | Reference implementation |
| KMP | O(m) | O(n) | O(n) | O(m) | No pathological input |
| Z-Algorithm | O(m) | O(n) | O(n) | O(m) | No sentinel, no concatenation |
| Rabin-Karp | O(m) | O(n·m) | O(n) | O(1) | Worst case needs adversarial collisions |
| Aho-Corasick | O(m) | O(n + z) | O(n + z) | O(m) | Multi-pattern; m = total pattern length, z = matches |

### Multi-pattern matching

**Aho-Corasick** searches for all k patterns in one pass. Running a single-pattern
matcher once per pattern costs O(k·n); Aho-Corasick costs O(n + z) regardless of
k, which is the entire reason to build the automaton.

The +z term is not avoidable overhead — it is the cost of emitting z matches, and
any algorithm reporting them all pays it. Output links keep it *proportional* to
z rather than to failure-chain depth: without them, reporting matches at a
position means walking the whole failure chain each time, even through nodes that
terminate nothing.

Nested patterns are the case that exposes a broken implementation. With
`{he, she, his, hers}` over `"ushers"`, `he` occurs inside both `she` and `hers`,
and it is reachable only via the output-link chain, because the automaton is
sitting at a deeper node when the shorter match ends.

## Suffix Structures

| Algorithm | Time | Space | Notes |
|---|---|---|---|
| Suffix array (prefix doubling) | O(n log n) | O(n) | Counting sort per round, no comparison sort |
| Suffix array (brute force) | O(n² log n) | O(n) | Test reference only |
| LCP array (Kasai) | O(n) | O(n) | Requires the suffix array |
| LCP array (pairwise) | O(n²) | O(n) | Test reference only |

**Suffix array by prefix doubling.** Sorting n suffixes with a comparison sort
costs O(n² log n), since comparing two suffixes is itself O(n). Prefix doubling
sorts by the first 2^k characters per round, and the ranks from round k let round
k+1 compare 2^(k+1) characters in constant time — each suffix's second half is
another suffix whose rank is already known. Each round sorts pairs with counting
sort in O(n), giving O(n log n) across log n rounds.

Counting sort also sidesteps needing any sorting utility, which `java.util` would
have supplied and the subject rules bar.

**Determinism.** All suffixes of a string are distinct, so the ordering is total
and unique — there are no ties to break, and output is fully determined by input.

**LCP by Kasai.** Computing each adjacent pair independently is O(n) per pair and
O(n²) overall. Kasai walks suffixes in *text* order instead of rank order and
carries the match length forward: removing the leading character of a suffix can
shorten its LCP with its predecessor by at most one. Total increase and total
decrease are each bounded by n, so it is O(n) despite the nested loop.

Repetitive input is where this matters. For `a^n` the suffixes are fully nested
and the LCP array is `[0, 1, 2, ..., n-1]`; the carried length is exactly the
overlap being reused instead of rescanned.

### Why the worst cases differ

**Naive** degrades when alignments share long prefixes. Text `aaaa…a` against
pattern `a…ab` compares `m-1` characters at every one of `n-m+1` alignments
before failing on the last. The test suite exercises this directly with a
2000-character run of `a`.

**KMP** never moves the text pointer backwards. On a mismatch it consults the
failure function and slides the pattern instead. Each fallback strictly
decreases the matched length, and the matched length only ever grows one step
per character consumed, so total work is linear with no bad inputs.

**Z-Algorithm** maintains a window `[left, right)` of text already known to match
a pattern prefix. `right` never decreases, which bounds total comparisons the
same way.

**Rabin-Karp** is expected-linear but degrades to O(n·m) if every window's hash
collides with the pattern's. Reaching that requires constructing input against a
known base and modulus, so it does not arise on natural text — but the
verification step that makes it *correct* is not optional (see below).

### Correctness details worth stating

**Overlapping matches are reported.** Searching `aaaa` for `aa` yields
`[0, 1, 2]`. In KMP this requires falling back through the failure function
after a hit rather than resetting the matched length to zero; resetting silently
drops every overlapping occurrence.

**Rabin-Karp verifies every hash hit.** Equal hashes do not imply equal strings.
Reporting a match on hash equality alone passes small tests and then produces
false positives at corpus scale, because collisions against a ~10⁹ modulus stop
being rare once the window count is large. Verification costs O(m) per hit,
which is why the expected bound depends on hits being rare.

**The Z matcher does not concatenate.** The textbook form builds the Z-array of
`pattern + sentinel + text` and needs a sentinel absent from both inputs. Over
arbitrary document text no such character can be promised — choosing `'\0'`
breaks on any text containing it. Computing the Z-array of the pattern alone and
running the window logic against the text removes the assumption entirely and
drops preprocessing space from O(n + m) to O(m). The suite includes a text
containing `\u0000` to keep this honest.

## Supporting Structures

| Structure | Operation | Complexity | Notes |
|---|---|---|---|
| `IntList` | `add` | O(1) amortised | Growth by doubling |
| `IntList` | `get` | O(1) | Bounds-checked |
| `IntList` | `toArray` | O(n) | Trimmed copy |
| `CharMap` | `get` | O(log k) | Binary search over k children |
| `CharMap` | `put` | O(k) | Insertion shift keeps keys sorted |

`IntList` exists because `java.util.*` is barred from the algorithm
implementations. It is also the right structure regardless: `ArrayList<Integer>`
boxes every match position, and match positions are dense primitive data.

## Planned

Not yet implemented. Listed so the table has one home as the module grows.

| Algorithm | Category | Expected Time | Expected Space |
|---|---|---|---|
*(All previously planned algorithms are now implemented; see the sections above
and below.)*

## Dynamic Programming

| Algorithm | Time | Space | Notes |
|---|---|---|---|
| Levenshtein | O(n·m) | O(min(n, m)) | Rolling rows; shorter string on the row axis |
| Damerau-Levenshtein (unrestricted) | O(n·m) | O(n·m) | Full matrix required by the transposition term |
| Damerau-Levenshtein (OSA, restricted) | O(n·m) | O(n·m) | Different answers from the above; see below |
| Needleman-Wunsch (global) | O(n·m) | O(n·m) | Matrix retained for traceback |
| Smith-Waterman (local) | O(n·m) | O(n·m) | Scores floored at zero |

**Levenshtein trades alignment for space.** Each row depends only on the row
above, so two rows suffice and the shorter string goes on the row axis, giving
O(min(n,m)). The cost is that the alignment itself cannot be recovered —
traceback needs the whole matrix, which is why the two alignment algorithms keep
it.

**Restricted and unrestricted Damerau-Levenshtein are different functions.** OSA
permits a transposition only of characters adjacent in both strings and never
edits a substring twice; the unrestricted version has no such limit and is a
true metric. On `"CA"` → `"ABC"` OSA gives 3 and unrestricted gives 2. Both are
implemented, and the suite pins that disagreement, because a codebase calling
one by the other's name will eventually mislead someone.

**Local beats global when agreement is partial.** Two documents sharing one
quoted paragraph and nothing else score poorly under Needleman-Wunsch, whose gap
penalties accumulate across the non-matching remainder. Smith-Waterman's zero
floor lets the alignment restart, isolating the shared passage.

## Network Flow

| Algorithm | Time | Space | Notes |
|---|---|---|---|
| Ford-Fulkerson (DFS) | O(E · maxflow) | O(V) | Bound depends on capacities |
| Edmonds-Karp (BFS) | O(V·E²) | O(V) | Bound depends only on the graph |
| Dinic | O(V²·E) | O(V) | O(E·√E) on unit capacities |
| Bipartite matching (Kuhn) | O(V·E) | O(V) | Equivalent to unit-capacity max flow |

**Why the Ford-Fulkerson bound mentions the flow value.** DFS makes no promise
about path length. The classic bad case is two wide paths joined by a
capacity-1 edge: choosing the path through the bottleneck augments one unit at a
time, so a four-vertex graph can take a million iterations. Edmonds-Karp removes
exactly that by always taking the shortest augmenting path, which is why its
bound is in V and E alone.

**Reverse edges are what make augmenting paths correct.** Every edge is stored
with a paired reverse edge; pushing flow forward credits the reverse. Sending
flow back along it cancels an earlier decision, so no greedy choice permanently
traps the algorithm below the true maximum.

**Dinic's current-arc optimisation is not optional.** Without the per-vertex
cursor, each phase rescans exhausted edges and costs O(V·E) instead of O(E).

## Approximation

| Algorithm | Time | Space | Ratio |
|---|---|---|---|
| Vertex cover (maximal matching) | O(V + E) | O(V) | 2 |
| List scheduling | O(n·m) | O(n + m) | 2 − 1/m |
| LPT scheduling | O(n log n + n·m) | O(n + m) | 4/3 − 1/(3m) |

**Correction to an earlier version of this table.** It previously listed
"Interval Scheduling" under Approximation. Earliest-finish-time interval
scheduling is **exact**, not an approximation, so it was the wrong algorithm for
this category. Makespan minimisation on identical machines is implemented
instead — genuinely NP-hard, with the two ratios above.

**Taking both endpoints is why the vertex-cover bound is provable.** The edges
picked share no endpoints, so they form a matching M; any cover must contain an
endpoint of each, giving optimum ≥ |M|, while the algorithm returns exactly
2|M|. The "obvious" alternative of repeatedly taking the highest-degree vertex
has no constant-factor guarantee at all — it is Θ(log n) in the worst case, and
is a standard trap because it looks better on typical inputs.

**LPT differs from list scheduling only in job order**, and that alone improves
the guarantee from 2 to 4/3. Long jobs scheduled last land on an already-full
machine and extend the makespan by their whole duration; scheduled first they
are absorbed while every machine is empty.

**Heapsort, not quicksort**, for the LPT ordering: job lists frequently arrive
pre-sorted, which is precisely the input that degrades naive quicksort to O(n²).

## Randomized

| Algorithm | Time | Space | Notes |
|---|---|---|---|
| Miller-Rabin (deterministic witnesses) | O(k · log³n) | O(1) | Exact for all 64-bit n |
| Miller-Rabin (random witnesses) | O(k · log³n) | O(1) | Error < 4^−k, one-sided |
| Universal hashing (integer) | O(1) | O(1) | Collision probability ≤ 1/m |
| Universal hashing (string) | O(L) | O(1) | Collision probability ≤ L/p |
| Reservoir sampling (Algorithm R) | O(n) | O(k) | One pass, unknown stream length |
| SplitMix64 PRNG | O(1) | O(1) | Deterministic, seeded, not cryptographic |

**Why log³ and not log².** The obvious `(a * b) % m` overflows a signed long
once m exceeds roughly 3 × 10⁹ — silently, producing wrong answers rather than
an error. `BigInteger.modPow` would avoid it but would be replacing the
algorithm with a library call, which the subject rules bar. Multiplication is
therefore done by Russian-peasant doubling, adding an O(log n) factor inside
each of the O(log n) squarings.

**Deterministic for 64-bit inputs.** The witness set {2, 3, 5, …, 37} is proven
sufficient below 3.3 × 10²⁴, which covers every `long`. So `isPrime` is exact,
not probable; the randomised variant exists because the subject asks for a
randomized algorithm and because it is what generalises beyond 64 bits.

**Carmichael numbers are the test that matters.** 561, 1105, 1729 and their
relatives satisfy Fermat's little theorem for every coprime base, so a Fermat
test calls them prime. Miller-Rabin does not, and the suite checks ten of them.

**Universal hashing removes the fixed worst case.** Any deterministic hash has
some input set that collides badly; if that set is attacker-chosen the structure
degrades. Drawing h at random from a family with a proven collision bound makes
the guarantee hold in expectation over the choice of h, whatever the inputs. The
string family is the Rabin-Karp construction with a randomly chosen base — which
is exactly what removes Rabin-Karp's adversarial case.

**Reservoir sampling's index draw must be inclusive.** Item i is accepted with
probability k/(i+1), which requires drawing from [0, i] and not [0, i). The
off-by-one still produces plausible-looking samples and is invisible to any
check short of a distribution test, so the suite runs 40,000 trials and asserts
each of ten elements is chosen about a tenth of the time.
