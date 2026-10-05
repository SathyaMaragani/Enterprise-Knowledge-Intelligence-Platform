# ShellForge Tests

## Build Environment

This Windows host has no `gcc`/`make`, so all tests below are executed inside a
container:

```bash
docker run --rm -i -v "$(pwd):/src" -w /src gcc:13 sh -c "make clean && make test"
```

`make test` runs all six automated suites in order, Week 4 to Week 9.

Week 8's Valgrind and GDB checks need those tools, which the gcc:13 image lacks.
Build an image that adds them once, then use it in place of `gcc:13`:

```bash
printf 'FROM gcc:13\nRUN apt-get update && apt-get install -y --no-install-recommends valgrind gdb && rm -rf /var/lib/apt/lists/*\n' | docker build -t shellforge-dev -
docker run --rm -v "$(pwd):/src" -w /src shellforge-dev sh -c "make clean && make test"
```

In plain gcc:13 those checks are reported as skipped, not failed. Week 6 and 8
read `/proc` and use `setsid` and `ps`, so they need Linux.

## Automated Suites

| Suite | Chapter | Assertions |
|---|---|---|
| `tests/test_week4.sh` | Processes and command execution | 21 |
| `tests/test_week5.sh` | Built-in commands and environment variables | 32 |
| `tests/test_week6.sh` | Signals and process control | 21 |
| `tests/test_week7.sh` | Pipes and IPC | 23 |
| `tests/test_week8.sh` | Valgrind, GDB and AddressSanitizer | 22 |
| `tests/test_week9.sh` | File descriptors and I/O redirection | 29 |

Run one suite on its own:

```bash
docker run --rm -i -v "$(pwd):/src" -w /src gcc:13 sh -c "sh tests/test_week5.sh"
```

Each suite ends by rebuilding under `-fsanitize=address,undefined` and asserting
that no AddressSanitizer, LeakSanitizer or UndefinedBehaviorSanitizer report
appears. Before trusting a clean sanitizer result, confirm the leak detector is
actually active — compile a deliberate one-line leak and check it is reported.

## Manual Test Procedure

1. **Program starts successfully.**
   - Action: Run `./bin/shellforge`.
   - Expected: Program launches without errors.
2. **Startup banner appears.**
   - Action: Observe output.
   - Expected: `Welcome to ShellForge Version 9.0` banner is displayed.
3. **A command is tokenized into argv[].**
   - Action: Run with `--debug-tokens` and enter `ls -l /home`.
   - Expected: `argv[0] = ls`, `argv[1] = -l`, `argv[2] = /home`, `argv[3] = NULL`.
4. **Irregular spacing collapses.**
   - Action: Enter `  ls   -l    /home  ` with leading, trailing and repeated spaces, and a tab.
   - Expected: Identical result to test 3 — consecutive delimiters produce no empty tokens.
5. **Empty and whitespace-only input.**
   - Action: Press Enter on an empty line, then a line of only spaces and tabs.
   - Expected: No output, no crash, prompt returns.
6. **External command executes.**
   - Action: Enter `echo hello`.
   - Expected: `hello`, produced by a forked child running `/bin/echo`.
7. **Unknown command is handled.**
   - Action: Enter `nosuchcommand`.
   - Expected: `ShellForge: nosuchcommand: No such file or directory`; shell survives.
8. **`exit` terminates the program.**
   - Action: Enter `exit`.
   - Expected: `Exiting ShellForge...` and a clean exit.
9. **EOF terminates gracefully.**
   - Action: Press Ctrl+D.
   - Expected: No infinite loop; exit code 0.
10. **Project builds using make.**
    - Action: `make clean` then `make`.
    - Expected: Compiles with no warnings under `-Wall -Wextra`.
11. **Long input test.**
    - Action: Enter a command with an argument over 1024 characters.
    - Expected: The input buffer grows without truncation, and memory is freed.
12. **Token vector growth.**
    - Action: Enter a command with more than 64 arguments.
    - Expected: All tokens returned in order, vector `NULL`-terminated correctly.

## Week 5 — Built-in Commands and Environment Variables

The point of these cases is that built-ins run **in the shell process**. Test 2
is the decisive one: if `cd` were forked, the child would change its own working
directory and exit, and the following `pwd` would print the original directory.

1. **`pwd` built-in**: prints the working directory via `getcwd()`.
2. **`cd` persists in the parent**: `cd /tmp` then `pwd` prints `/tmp`; successive
   `cd` calls accumulate.
3. **Bare `cd` follows `$HOME`**: `cd /tmp`, then `cd`, then `pwd` returns to `$HOME`.
4. **`cd` to a missing directory**: reports `ShellForge: cd: <path>: No such file or directory`
   and the shell survives.
5. **`cd` argument count**: more than one argument is rejected.
6. **`PWD` tracks `cd`**: `env` reports `PWD=/tmp` after `cd /tmp`. `PWD` is an
   ordinary environment variable and does not follow `chdir()` on its own.
7. **`env` built-in**: prints `HOME`, `USER`, `PATH`, `SHELL`, `PWD`.
8. **`env` NULL-safety**: run under `env -u USER`; the unset variable renders as
   `USER=(not set)` rather than being passed as `NULL` to `printf("%s")`.
9. **`help` built-in**: lists every built-in.
10. **`clear` built-in**: emits the ANSI erase-display sequence, with no subprocess.
11. **`exit` routes through the parser**: `exit` with surrounding whitespace still
    terminates, and nothing after it runs.
12. **Built-ins do not shadow external programs**: `echo` still reaches `execvp()`,
    and an unknown command fails without taking the shell down.
13. **EOF termination**: empty input exits 0.
14. **Sanitizers**: every built-in path is exercised under ASan + UBSan.

### Week 5 results

Executed in `gcc:13`:

| Test category | Result |
|---|---|
| Build under `-Wall -Wextra` | Clean, 0 warnings |
| `pwd` built-in | Passed |
| `cd` persistence in parent process | Passed |
| Bare `cd` follows `$HOME` | Passed |
| `cd` error and argument-count handling | Passed |
| `PWD` updated after `chdir()` | Passed |
| `env` output and NULL-safety | Passed |
| `help` listing | Passed |
| `clear` ANSI sequence | Passed |
| `exit` via parser, incl. whitespace | Passed |
| Fall-through to `execvp()` | Passed |
| EOF termination | Passed |
| ASan / LeakSanitizer / UBSan | 0 leaks, 0 errors |
| **`tests/test_week5.sh`** | **32 passed, 0 failed** |

## Week 7 — Pipes and IPC

1. **Direct IPC parent to child**: parent writes to `pipefd[1]`, child reads from `pipefd[0]`.
2. **Direct IPC child to parent**: child writes, parent reads.
3. **EOF detection**: when the writer closes `pipefd[1]`, the reader's `read()` returns `0`.
4. **Interactive `demo-ipc`**: running `demo-ipc` inside the shell triggers the demonstration.
5. **Two-process pipeline**: `echo Hello World | grep Hello` filters through the pipe.
6. **Word count pipeline**: `seq 1 5 | wc -l` returns `5` without deadlocking.
7. **Unspaced metacharacter**: `echo unspaced_pipe|cat` parses `|` as its own token.
8. **Multi-argument pipeline**: `ls -la bin | grep shellforge` passes flags on both sides.
9. **Leading pipe**: `| ls` is rejected as a syntax error.
10. **Trailing pipe**: `ls |` is rejected as a syntax error.
11. **Multi-stage boundary**: three-stage pipelines are rejected; only two stages are supported.
12. **Left-side failure**: `nonexistent_left_cmd | wc -l` exits the child cleanly and the right side sees EOF.
13. **Right-side failure**: `echo hello | nonexistent_right_cmd` reports on stderr and returns to the prompt.
14. **Buffer and vector growth in a pipeline**: 1200-character argument and 100-argument vector both survive.
15. **Sanitizers**: pipeline and IPC paths run under ASan + UBSan.

### Week 7 results

| Test category | Result |
|---|---|
| Build under `-Wall -Wextra` | Clean, 0 warnings |
| Parent to child IPC | Passed |
| Child to parent IPC | Passed |
| EOF detection (`read() == 0`) | Passed |
| Two-process pipelines | Passed |
| Unspaced pipe tokenization | Passed |
| Pipe syntax errors (leading, trailing) | Passed |
| Multi-stage boundary rejection | Passed |
| Pipeline failure handling, both sides | Passed |
| Buffer and token vector growth | Passed |
| ASan / UBSan | 0 leaks, 0 errors |
| **`tests/test_week7.sh`** | **23 passed, 0 failed** |

## Week 6 — Signals and Process Control

Ctrl+C is simulated the way a terminal delivers it: the shell runs under
`setsid` in its own process group, and `kill -INT -<pgid>` signals the whole
group, so the shell and its running child each receive it.

| Test category | Result |
|---|---|
| Ctrl+C at an idle prompt: message, shell continues, exit code 0 | Passed |
| Ctrl+C during `sleep 5`: child ends in about 2 s, shell continues | Passed |
| Ctrl+C during `sleep 5 \| cat`: both children end | Passed |
| Ctrl+Z ignored: no stopped process, session completes | Passed |
| Child starts with SIGINT at default and SIGCHLD unblocked (`/proc/self/status`) | Passed |
| Zombie without a handler, reaped with it (`tests/sigchld_check.c`) | Passed |
| Foreground exit status never stolen by the reaper | Passed |
| ASan / UBSan with SIGINT | 0 reports |
| **`tests/test_week6.sh`** | **21 passed, 0 failed** |

## Week 8 — Valgrind, GDB and AddressSanitizer

| Test category | Result |
|---|---|
| Valgrind, full session: all heap blocks freed, 0 errors | 58 allocs, 58 frees |
| Valgrind, exit by EOF instead of `exit` | 0 leaks, 0 errors |
| Valgrind control: deliberate `malloc(200)` leak | reported, 200 bytes definitely lost |
| GDB: breakpoint in `parse_line`, backtrace to `main`, tokens and NULL terminator | Passed |
| `make asan`: full session, no ASan/LSan/UBSan report | Passed |
| ASan control: heap-buffer-overflow reported with its source line | Passed |
| No zombie children while the shell idles | Passed |
| Every `malloc`/`realloc` result checked | 4 of 4 |
| **`tests/test_week8.sh`** | **22 passed, 0 failed** (11 skipped without Valgrind/GDB) |

## Week 9 — File Descriptors and I/O Redirection

| Test category | Result |
|---|---|
| `>` writes, truncates, creates with mode 0644 | Passed |
| `>>` appends, creates a missing file | Passed |
| `<` input; missing input file reported | Passed |
| `2>` stderr, including the shell's own "not found" message | Passed |
| `sort < in > out`, operators without spaces, `a2>f` | Passed |
| Built-ins redirected, terminal restored | Passed |
| Redirection inside a pipeline | Passed |
| Syntax and `open()` errors | Passed |
| No descriptor leaks (child sees fds 0-3 only) | Passed |
| ASan / UBSan | 0 reports |
| **`tests/test_week9.sh`** | **29 passed, 0 failed** |

## Regression Summary

All three suites pass together via `make test`:

```
tests/test_week4.sh    21 passed, 0 failed
tests/test_week5.sh    32 passed, 0 failed
tests/test_week6.sh    21 passed, 0 failed
tests/test_week7.sh    23 passed, 0 failed
tests/test_week8.sh    22 passed, 0 failed, 0 skipped
tests/test_week9.sh    29 passed, 0 failed
```
