# OSSP Week 8 — Memory Management, Debugging and Valgrind (CO-4)

**Milestone:** eliminate memory leaks and debug ShellForge with professional
tools. No new module; the work is reviewing and proving the existing code.

## Process memory layout

```
 high addresses
 +-----------------------------+
 | argv, environment strings   |
 +-----------------------------+
 | stack (locals, frames)  v   |   cwd[PATH_MAX] in builtin.c, pipefd[2]
 |                             |
 |                         ^   |
 | heap (malloc/free)          |   read_line() buffer, parse_line() vector
 +-----------------------------+
 | BSS (uninitialised data)    |   foreground_running in signals.c
 | data (initialised)          |   PIPE_TOKEN, ENV_KEYS
 +-----------------------------+
 | text (machine code)         |
 +-----------------------------+
 low addresses
```

ShellForge has exactly two heap allocations per command line:

- the input buffer from `read_line()`, grown with `realloc()`;
- the token vector from `parse_line()`, grown with `realloc()`.

The tokens themselves are slices of the input buffer, not separate copies, so
freeing those two blocks frees everything. `free_tokens()` frees only the vector
for that reason.

## Common memory errors and where ShellForge guards against them

| Error | Guard in ShellForge |
|---|---|
| Memory leak | each line's buffer and vector are freed at the end of the loop, on `exit` (unwinds through `main()` rather than calling `exit()` from the built-in), and on EOF |
| Dangling pointer | tokens point into `line`, and `line` is freed only after the vector has been used |
| Buffer overflow | input and token buffers grow *before* they fill; the vector always keeps a slot for its NULL |
| Double free | `free_tokens()` frees the vector only, never the strings it points to |
| Lost `realloc()` result | `realloc()` goes into a temporary; on NULL the old block is freed, not leaked |
| Unchecked allocation | every `malloc`/`realloc` result is tested before use |

## Defensive checks on system calls

| Call | On failure |
|---|---|
| `fork()` | message, pipe ends closed, SIGCHLD unblocked, return to the prompt |
| `execvp()` | child prints the error and `_exit(127)`; `errno` saved before `fprintf()` could change it |
| `pipe()`, `dup2()` | message; the child exits instead of running with the wrong streams |
| `open()` (Week 9) | `ShellForge: file: reason`; the command is not run |
| `waitpid()` | retried on `EINTR`, reported otherwise |
| `getcwd()`, `chdir()` | message; no uninitialised buffer is printed |

## Fixes made during this review

1. **`errno` read after `fprintf()`.** The failed-`execvp()` path printed the
   error and *then* tested `errno == ENOENT` to choose exit code 127.
   `fprintf()` may change `errno`, so it is now saved first.
2. **Uninitialised exit status in pipelines.** The pipeline ignored
   `waitpid()`'s return value and read `status` even if the call had failed.
   Both children are now collected through `wait_child()`, which checks the
   call and retries on `EINTR`.
3. **Duplicated child set-up.** Three copies of the exec-and-report code
   (single command, and each pipeline side) became one function,
   `exec_child()`, so each fix lands once.

## Makefile targets

```make
CFLAGS = -Wall -Wextra -g -Iinclude        # -g: symbols for GDB and Valgrind

asan:      # rebuild with -fsanitize=address,undefined
valgrind:  # valgrind --leak-check=full ... ./bin/shellforge
```

## Valgrind

The gcc:13 image has no Valgrind or GDB, so the tests use an image that adds
them (see `tests/TESTS.md`). The session in `tests/test_week8.sh` exercises
every path that allocates or opens something:

- a 1500-character line and a 150-argument line (both buffers grow);
- every built-in;
- an external command and a missing one;
- pipes;
- all four redirections, a failed `open()` and syntax errors;
- the IPC demo.

```
$ valgrind --leak-check=full --show-leak-kinds=all --child-silent-after-fork=yes ./bin/shellforge < session
    in use at exit: 0 bytes in 0 blocks
  total heap usage: 58 allocs, 58 frees, 29,409 bytes allocated
All heap blocks were freed -- no leaks are possible
ERROR SUMMARY: 0 errors from 0 contexts (suppressed: 0 from 0)
```

`--child-silent-after-fork=yes` keeps the report to the shell itself. A forked
child that has not yet called `exec` is still a copy running under Valgrind,
and would otherwise print its own summary.

**Control.** A clean report only means something if the tool would have caught
a leak. The handbook's example, `char *temp = malloc(200);` with no `free()`,
is compiled separately, and Valgrind reports
`definitely lost: 200 bytes in 1 blocks`.

## GDB

```
$ gdb -batch -ex "break parse_line" -ex "run < input" -ex backtrace \
      -ex "print line" -ex finish -ex next \
      -ex "print tokens[0]" -ex "print tokens[1]" -ex "print tokens[2]" ./bin/shellforge
Breakpoint 1.1, parse_line (line=0x... "echo gdb_probe") at src/parser.c:68
#0  parse_line (line=0x... "echo gdb_probe") at src/parser.c:68
#1  0x... in main (argc=1, argv=0x...) at src/main.c:50
$1 = 0x... "echo gdb_probe"
$3 = 0x... "echo"
$4 = 0x... "gdb_probe"
$5 = 0x0
```

The last line is the NULL that terminates the argument vector, which
`execvp()` requires. Other useful commands: `next`/`step` to single-step,
`continue`, and `print *tokens@3` to print the first three elements.

## AddressSanitizer

`make asan` rebuilds with `-fsanitize=address,undefined`. The same full session
produces no AddressSanitizer, LeakSanitizer or UndefinedBehaviorSanitizer
report. Every suite since Week 2 ends with a sanitizer run.

**Control:** writing one byte past an 8-byte `malloc()` is reported as
`heap-buffer-overflow`, with the file and line of the bad write.

## Memory-management checklist

| Item | Evidence |
|---|---|
| Every `malloc()` has a `free()` | Valgrind: 58 allocs, 58 frees |
| Every `realloc()` result checked before use | 4 of 4 allocation sites checked (`test_week8.sh`) |
| No pointer freed twice | Valgrind and ASan: 0 errors |
| No access after free | Valgrind and ASan: 0 errors |
| Every file descriptor closed | Week 9 test: a child sees only fds 0–3 after redirections and pipes |
| Every child reaped | `ps --ppid <shell>` shows no `Z` children while the shell idles |

## Verification — `tests/test_week8.sh` (22 assertions)

Results:

- with Valgrind and GDB installed: 22 passed;
- in plain `gcc:13`: the 11 Valgrind and GDB checks are reported as skipped, and
  the other 11 pass.
