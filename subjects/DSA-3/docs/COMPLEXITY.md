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

`IntList` exists because `java.util.*` is barred from the algorithm
implementations. It is also the right structure regardless: `ArrayList<Integer>`
boxes every match position, and match positions are dense primitive data.

## Planned

Not yet implemented. Listed so the table has one home as the module grows.

| Algorithm | Category | Expected Time | Expected Space |
|---|---|---|---|
| Aho-Corasick | String | O(n + m + z) for z matches | O(m·Σ) |
| Suffix Array | String | O(n log n) construction | O(n) |
| LCP / Kasai | String | O(n) given suffix array | O(n) |
| Levenshtein | DP | O(n·m) | O(min(n, m)) rolling |
| Damerau-Levenshtein | DP | O(n·m) | O(n·m) |
| Needleman-Wunsch | DP | O(n·m) | O(n·m) |
| Smith-Waterman | DP | O(n·m) | O(n·m) |
| Ford-Fulkerson | Graph | O(E·maxflow) | O(V + E) |
| Edmonds-Karp | Graph | O(V·E²) | O(V + E) |
| Dinic | Graph | O(V²·E) | O(V + E) |
| Bipartite Matching | Graph | O(V·E) | O(V + E) |
| Vertex Cover (2-approx) | Approximation | O(V + E) | O(V) |
| Interval Scheduling | Approximation | O(n log n) | O(n) |
| Miller-Rabin | Randomized | O(k·log³n) | O(1) |
| Randomized Hashing | Randomized | O(n) | O(1) |
| Reservoir Sampling | Randomized | O(n) | O(k) |
