# OSSP Week 5 — Built-in Commands and Environment Variables (CO-2)

## Concepts

- **Built-in command:** a command the shell implements and runs *inside its own
  process*, rather than locating a program on disk and executing it in a child.
- **External command:** a program on disk (`/bin/ls`, `/usr/bin/date`) that the
  shell runs by `fork()` + `execvp()`, as built in Week 4.
- **Environment variable:** a name/value pair inherited by every process from its
  parent. `getenv()` reads one; `setenv()` writes one into this process's own
  copy, which children then inherit.

## Why `cd` cannot be an external command

This is the whole reason built-ins exist, and it follows directly from Week 4's
process model. `fork()` gives the child its own copy of the parent's state,
including its current working directory. A child changing that copy cannot
reach back into the parent:

```
   Shell (cwd = /home/student)
        |
      fork()
        |
        +---------------------------+
        |                           |
   Parent                        Child
   waitpid()                   chdir("Documents")   <- changes the CHILD's cwd
        |                      _exit(0)
        |                           |
   child reaped                  gone
        |
   cwd is STILL /home/student      <- the change died with the child
```

The working directory is per-process state held in the kernel's process control
block. There is no system call for "change my parent's directory", and that is a
deliberate isolation property, not an oversight. So `cd` must execute in the
shell process itself — which means the shell has to recognise it *before*
reaching the fork path.

The same argument applies to `exit`: a child exiting does not end the shell.

## Built-in versus external

| | Built-in | External |
|---|---|---|
| Runs in | the shell process | a forked child |
| New process created | no | yes (`fork` + `execvp`) |
| Can change shell state | yes | no |
| Cost | a function call | process creation + program load |
| Examples | `cd`, `pwd`, `exit`, `env`, `help`, `clear` | `ls`, `date`, `grep` |

## Dispatch order

```
  line -> parse_line() -> argv[]
                            |
                     execute_builtin()
                            |
        +-------------------+--------------------+
        |                   |                    |
  BUILTIN_EXIT      BUILTIN_HANDLED      BUILTIN_NOT_FOUND
        |                   |                    |
  free + break        next prompt         execute_command()
                                           (fork + execvp)
```

`execute_builtin()` returns three outcomes rather than two. A plain
handled/not-handled pair cannot express `exit`, which must unwind back through
`main()` so the heap-allocated line buffer and token vector are freed before the
process ends. Calling `exit()` directly from inside the built-in would strand
both allocations and show up as leaks under AddressSanitizer.

## The built-ins

| Command | System call | Notes |
|---|---|---|
| `cd [dir]` | `chdir()` | No argument follows `$HOME`. Updates `PWD` on success. |
| `pwd` | `getcwd()` | Return value checked; the directory can be deleted underneath the shell. |
| `env` | `getenv()` | Prints `HOME`, `USER`, `PATH`, `SHELL`, `PWD`. |
| `clear` | none | Writes the ANSI erase sequence directly. |
| `help` | none | Lists the built-ins. |
| `exit` | none | Returns `BUILTIN_EXIT` so `main()` can clean up. |

## Three deviations from the chapter listing

**1. `getenv()` results are NULL-checked.** The listing prints
`getenv("USER")` straight into `printf("%s")`. `getenv()` returns `NULL` for an
unset variable, and passing `NULL` to `%s` is undefined behaviour — it happens to
print `(null)` on glibc and crashes elsewhere. `USER` in particular is frequently
unset in containers and in `su` sessions, so this is reachable, not theoretical.
Unset variables render as `(not set)`, and `test_week5.sh` runs the shell under
`env -u USER` to exercise that path.

**2. `clear` does not call `system("clear")`.** `system()` forks a shell to run
an external binary, so the "built-in" would spawn two processes to do something
that is one `printf`. It also fails where the `clear` binary is absent, which
includes several minimal container images. Writing `\033[2J\033[H` is what the
escape sequence exists for.

**3. `exit` does not call `exit()`.** Covered above — it returns a sentinel so
`main()` can free what it still owns.

`cd` also accepts no argument (following `$HOME`) instead of printing a usage
message, matching standard shell behaviour, and rejects more than one argument.

## Environment variables

| Variable | Meaning |
|---|---|
| `HOME` | The user's home directory |
| `USER` | Current username |
| `PATH` | Directories `execvp()` searches for programs |
| `SHELL` | The login shell |
| `PWD` | Current working directory |

`PWD` deserves a note: the kernel tracks the real working directory, but `PWD` is
an ordinary environment variable and does **not** follow `chdir()` by itself. A
shell that does not update it will report a stale directory from `env` after a
`cd`. `builtin_cd()` calls `setenv("PWD", ...)` after a successful `chdir()`, and
the test suite asserts `PWD=/tmp` after `cd /tmp`.

`PATH` is the variable Week 4 already depended on — it is what makes `execvp()`
find `ls` without being told `/bin/ls`.

## Verification

Built and run in a `gcc:13` container, since this Windows host has no C
toolchain:

```bash
docker run --rm -v "<repo>/subjects/OSSP/shellforge:/src" -w /src gcc:13 sh -c "make clean && make test"
```

`tests/test_week5.sh` covers:

- `cd` persisting across commands — the decisive proof that built-ins run in the
  shell process and not in a child
- bare `cd` following `$HOME`, and successive `cd` calls accumulating
- `cd` to a missing directory reporting an error without killing the shell
- `cd` rejecting extra arguments
- `PWD` updated after `cd`
- `env` printing the documented variables, and rendering an unset one safely
- `help` listing every built-in
- `clear` emitting the ANSI erase sequence
- `exit` terminating through the parser, including with surrounding whitespace
- external commands still reaching `execvp()`, and unknown commands failing
  without taking the shell down
- AddressSanitizer, LeakSanitizer and UndefinedBehaviorSanitizer clean across
  every built-in path
