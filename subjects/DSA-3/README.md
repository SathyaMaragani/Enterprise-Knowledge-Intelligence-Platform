# DSA-3 (Data Structures & Algorithms) Subject Responsibilities

This subject is responsible for the **TextHack advanced algorithm engine**.

## Key Requirements:
- Algorithms must be implemented from scratch.
- Do NOT use java.util.* data structures inside the core algorithm implementations.
- Do NOT replace algorithms with library calls.
- Every algorithm must be accompanied by tests and documented time/space complexity.

## Core Modules:
- **String Algorithms**: Naive, KMP, Z Algorithm, Rabin-Karp, Aho-Corasick, Suffix Array, LCP/Kasai.
- **Dynamic Programming**: Levenshtein Distance, Damerau-Levenshtein, Needleman-Wunsch, Smith-Waterman.
- **Network Flow**: Ford-Fulkerson, Edmonds-Karp, Dinic, Bipartite Matching.
- **Approximation**: Vertex Cover approximation, Scheduling.
- **Randomized Algorithms**: Miller-Rabin, Randomized hashing, Reservoir sampling.

---

## Build and Test

The only prerequisite is a JDK. There is no Maven, no JUnit and nothing to
download — deliberately, since the subject builds its algorithms from scratch and
a dependency-free build is one less thing standing between a grader and a
running test suite.

```bash
sh run-tests.sh
```

That compiles every source under `texthack/` and `tests/` with `-Xlint:all` and
runs the suite. To do it by hand:

```bash
javac -encoding UTF-8 -Xlint:all -d out $(find texthack tests -name '*.java')
java -cp out tests.StringAlgorithmTests
```

Compiled output lands in `out/`, which is gitignored.

## Layout

```
texthack/core/      shared primitives (IntList, StringMatcher)
texthack/string/    string matching algorithms
texthack/dp/        dynamic programming            (not yet implemented)
texthack/graph/     network flow                   (not yet implemented)
texthack/approximation/                            (not yet implemented)
texthack/randomized/                               (not yet implemented)
tests/              self-checking test suite
docs/COMPLEXITY.md  time and space complexity reference
benchmarks/         performance harness            (not yet implemented)
```

## Status

| Module | Implemented | Suite |
|---|---|---|
| String matching | Naive, KMP, Z-Algorithm, Rabin-Karp | 96 assertions |
| Multi-pattern + suffix | Aho-Corasick, suffix array, LCP/Kasai | 75 assertions |
| Dynamic programming | Levenshtein, Damerau-Levenshtein (+OSA), Needleman-Wunsch, Smith-Waterman | 61 assertions |
| Network flow | Ford-Fulkerson, Edmonds-Karp, Dinic, bipartite matching | 45 assertions |
| Approximation + randomized | Vertex cover, list/LPT scheduling, Miller-Rabin, universal hashing, reservoir sampling | 86 assertions |
| Engine | TextHack facade, query parser, citation flow, complexity registry | 89 assertions |

**All 20 listed algorithms are implemented.** 452 assertions across six suites,
zero failures, zero `-Xlint:all` warnings.

```
tests.StringAlgorithmTests             96 passed, 0 failed
tests.SuffixAndMultiPatternTests       75 passed, 0 failed
tests.DpTests                          61 passed, 0 failed
tests.GraphTests                       45 passed, 0 failed
tests.ApproximationAndRandomizedTests  86 passed, 0 failed
tests.EngineTests                      89 passed, 0 failed
-----------------------------------------------------------
total                                 452 passed, 0 failed
```

## Engine and tooling

```bash
java -cp out examples.Demos        # worked demonstrations of every module
java -cp out benchmarks.Benchmark  # timing, growth ratios, complexity table
```

The query language:

```
find "needle"                  exact search
findall "he" "she" "hers"      all patterns in one pass
fuzzy "recieve" ~2             within 2 edits
similar "colour" "color"       normalised similarity
prime 7919                     primality
```

## Not implemented, and why

One item from the original TextHack list is deliberately absent rather than
stubbed:

- **Indian-language Wikipedia corpus** — a data-acquisition and licensing task,
  not an algorithm. It needs a dump selection, a license review and storage
  decisions, in the same way the ML subject's FiQA corpus did.

The platform uses this engine in two places, both wiring rather than a rewrite,
because the engine has no Spring dependency:

- **Search (phase 1.7C).** The DBE-DSD backend compiles this directory's
  `texthack` package as a second source root. It scores keyword search with KMP,
  Aho-Corasick and Damerau-Levenshtein.
- **Workbench.** `/api/texthack/*` and the frontend's TextHack page run pattern
  search, similarity and alignment, and citation flow on user input, and show
  the complexity registry. See `subjects/DBE-DSD/backend/docs/API.md` §6.

Aho-Corasick deliberately does **not** implement `StringMatcher`. That interface
answers "where does this one pattern occur" and returns bare offsets, which
cannot express which of several patterns matched. Forcing it in would mean either
discarding pattern identity or building one automaton per pattern, throwing away
the algorithm's only advantage. It has its own API returning `Match[]`; the four
single-pattern matchers are untouched.

## How the string matchers are tested

Every matcher implements `StringMatcher` and is held to the same contract:
ascending match positions, overlapping occurrences reported, empty pattern
yields nothing, null rejected. Identical semantics are what make the central
test possible.

**Randomised cross-validation.** 4000 generated cases over a 2–4 character
alphabet are run through all four matchers, and every one must agree with the
naive reference. A small alphabet is deliberate: it makes accidental matches and
overlaps common, which is the regime where off-by-one errors actually surface.
Fixed test cases only catch the bugs you thought of.

This is not decorative — it already earned its place. A hand-counted index in a
fixed test case was wrong, and the signal that identified it as a *test* bug
rather than an *algorithm* bug was that all four matchers agreed with each other
and passed all 4000 random cases while disagreeing with the constant.

The random source is a hand-rolled LCG rather than `java.util.Random`, so a
failing case is reproducible from its seed.

Beyond that the suite covers overlapping matches, a 2000-character pathological
repeat (naive's worst case), the KMP failure function and Z-array in isolation,
non-ASCII and CJK patterns, and text containing `\u0000` — which would break any
implementation that concatenates with a NUL sentinel.
