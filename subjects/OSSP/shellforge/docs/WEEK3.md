# OSSP Week 3 - Parsing Commands

## Concepts

- **Lexical analysis (tokenization):**
  Splitting a stream of characters into meaningful units called tokens. A shell
  tokenizes a command line the same way a compiler tokenizes source code — the
  input is not executed as text, it is first broken into its parts.
- **Why a shell must parse:**
  The kernel has no concept of a command *line*. `execvp()` takes a program name
  and an array of separate arguments, so the shell must convert
  `"ls -l /home"` into those separate pieces before it can execute anything.
- **`argc` and `argv`:**
  `argc` is the number of arguments a program received; `argv` is the array
  holding them. `argv[0]` is conventionally the program's own name, and the
  array is terminated by a `NULL` pointer so the callee can find the end without
  being told the count.
- **`strtok()`:**
  Splits a string on any character from a delimiter set. It returns the first
  token on the initial call, then continues through the same string on each
  subsequent call with `NULL` as the first argument, remembering its position in
  internal state.
- **Delimiter set:**
  `" \t\r\n\a"` — space, tab, carriage return, newline and bell. Carriage return
  matters when input arrives with Windows line endings; consecutive delimiters
  are treated as one separator, so irregular spacing collapses naturally.

## The parsing pipeline

```
User Input
     |
"ls -l /home"
     |
  Tokenizer          strtok() over " \t\r\n\a"
     |
+------+------+--------+
|  ls  |  -l  | /home  |
+------+------+--------+
     |
   argv[]            NULL-terminated, ready for execvp()
```

## Why the tokens are not copies

`strtok()` does not allocate anything. It walks the buffer it is given and
writes a `'\0'` over each delimiter it finds, then returns a pointer to the
start of each token — so every token points *into* the original `line` buffer:

```
before:   l  s  ' '  -  l  ' '  /  h  o  m  e  \0
after:    l  s  \0   -  l  \0   /  h  o  m  e  \0
          ^         ^          ^
          argv[0]   argv[1]    argv[2]
```

This has two consequences the code depends on:

1. `line` must stay alive for as long as the tokens are used. `main()` frees it
   only after it has finished printing them.
2. `free_tokens()` frees only the pointer array, never the strings. Freeing the
   tokens individually would be freeing the middle of somebody else's buffer.

It also means `strtok()` destroys its input: after parsing, `line` no longer
holds the original command as one string.

## Dynamic growth

The vector starts at 64 slots and doubles whenever it fills, exactly as the
input buffer does in Week 2. The capacity check runs *after* the write and
increment, which guarantees at least one free slot remains when the loop ends —
that slot is where the terminating `NULL` goes.

## Modularity

Parsing lives in its own translation unit rather than inside `main()`:

| Module | Responsibility |
|---|---|
| `input.c` | Read a line of arbitrary length from stdin |
| `parser.c` | Turn one line into a NULL-terminated `argv[]` |
| `main.c` | Drive the REPL and sequence the other two |

Each has a header declaring only what callers need. Week 4 can add execution
without any of these three files learning about the others.

## Preparing for `execvp()`

```
"ls -l /home"
      |
   parse_line()
      |
argv[0] = "ls"
argv[1] = "-l"
argv[2] = "/home"
argv[3] = NULL
      |
execvp(argv[0], argv);
```

The vector is already in the exact shape `execvp()` requires, which is why
execution can be added in a later milestone without changing the parser.

## Verification

Built and run in a `gcc:13` container, since this Windows host has no C
toolchain:

```bash
docker run --rm -v "<repo>/subjects/OSSP/shellforge:/src" -w /src gcc:13 sh -c "make clean && make"
```

- Compiles clean under `-Wall -Wextra` with zero warnings.
- 200-token input exercises both growth steps (64 → 128 → 256); `argv[200]` is
  `NULL` as expected.
- Empty lines and whitespace-only lines produce no tokens and no crash.
- Input without a trailing newline is parsed, then EOF exits cleanly.
- AddressSanitizer and UndefinedBehaviorSanitizer report nothing. The leak
  detector was confirmed active by a deliberate control leak first, so the clean
  result is meaningful rather than a silently disabled check.
