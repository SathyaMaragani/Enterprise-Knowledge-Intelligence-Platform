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

| Module | Implemented | Verified |
|---|---|---|
| String matching | Naive, KMP, Z-Algorithm, Rabin-Karp | 96 assertions, 0 failures |
| String matching | Aho-Corasick, Suffix Array, LCP/Kasai | not started |
| Dynamic programming | — | not started |
| Network flow | — | not started |
| Approximation | — | not started |
| Randomized | — | not started |

4 of the 20 listed algorithms are implemented.

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
