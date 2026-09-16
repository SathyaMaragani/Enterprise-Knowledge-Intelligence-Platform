# ShellForge Tests

## Build Environment

This Windows host has no `gcc`/`make`, so all tests below are executed inside a
container:

```bash
docker run --rm -i -v "$(pwd):/src" -w /src gcc:13 sh -c "make clean && make test"
```

`make test` runs all three automated suites in order: Week 4, Week 5, Week 6.

## Automated Suites

| Suite | Chapter | Assertions |
|---|---|---|
| `tests/test_week4.sh` | Processes and command execution | 21 |
| `tests/test_week5.sh` | Built-in commands and environment variables | 32 |
| `tests/test_week6.sh` | Pipes and IPC | 23 |

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
   - Expected: `Welcome to ShellForge Version 6.0` banner is displayed.
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

## Week 6 — Pipes and IPC

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

### Week 6 results

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
| **`tests/test_week6.sh`** | **23 passed, 0 failed** |

## Regression Summary

All three suites pass together via `make test`:

```
tests/test_week4.sh    21 passed, 0 failed
tests/test_week5.sh    32 passed, 0 failed
tests/test_week6.sh    23 passed, 0 failed
```
