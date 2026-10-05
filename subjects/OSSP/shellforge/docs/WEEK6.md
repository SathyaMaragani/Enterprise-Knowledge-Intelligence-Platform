# OSSP Week 6 — Signals and Process Control (CO-2, CO-3)

**Milestone:** handle SIGINT, SIGTSTP and SIGCHLD safely, so Ctrl+C ends the
running command but never the shell, and no finished child is left a zombie.

Module: `include/signals.h`, `src/signals.c`, installed by `initialize_signals()`
at the top of `main()`, before the first prompt.

## Concepts

- **Signal:** a software interrupt the kernel delivers to a process. It is
  *asynchronous*: it can arrive between any two instructions, unlike a function
  call, which happens where the program asks for it.
- **Synchronous vs asynchronous:** SIGSEGV and SIGFPE are caused by the
  instruction just executed (synchronous). SIGINT from the keyboard, or SIGCHLD
  when a child ends, arrive independently of what the process is doing
  (asynchronous).
- **Disposition:** for each signal a process chooses the default action, to
  ignore it, or to catch it with a handler. SIGKILL and SIGSTOP cannot be caught
  or ignored.

| Signal | No. | Source | Default | ShellForge |
|---|---|---|---|---|
| SIGINT | 2 | Ctrl+C | terminate | shell catches it; children get the default |
| SIGQUIT | 3 | Ctrl+\\ | quit + core | unchanged |
| SIGKILL | 9 | `kill -9` | terminate | cannot be caught |
| SIGTERM | 15 | `kill` | terminate | unchanged |
| SIGCHLD | 17 | a child stops or exits | ignore | caught: the handler reaps zombies |
| SIGTSTP | 20 | Ctrl+Z | stop | ignored (see below) |
| SIGSEGV | 11 | invalid memory access | terminate + core | unchanged |

## Ctrl+C: who receives it

The terminal sends SIGINT to every process in its *foreground process group*.
While `sleep 100` runs, that group holds both the shell and the child:

```
 Ctrl+C -> terminal driver -> SIGINT to the foreground process group
                                   |                         |
                             ShellForge                 sleep 100
                         sigint_handler()          default action: dies
                         prints a newline                    |
                                   |                         |
                       waitpid() returns 130 (128 + 2) <-----+
                                   |
                              next prompt
```

At an idle prompt only the shell receives it. The terminal has already discarded
the half-typed line, so the handler prints the handbook's reminder and a fresh
prompt:

```
myshell> ^C
ShellForge: type 'exit' to quit.
myshell>
```

The handler knows which case it is in from a `volatile sig_atomic_t` flag set
around every foreground command, the only kind of variable a handler may
safely share with the main program.

## SIGCHLD and zombies

A child that has exited keeps its process-table entry (PID and exit status)
until the parent collects it with `wait()`/`waitpid()`. Until then it is a
**zombie**, shown as state `Z` by `ps -el`.

```
fork() -> child runs -> child exits -> SIGCHLD to the parent
                                          |
                          sigchld_handler(): while (waitpid(-1, NULL, WNOHANG) > 0);
                                          |
                                   zombie removed
```

- `WNOHANG` makes the handler return at once instead of blocking on children
  still running.
- The loop matters because signals do not queue: two children exiting together
  can raise a single SIGCHLD.
- The handler saves and restores `errno`, because its `waitpid()` could
  otherwise overwrite an `errno` the main program is about to read.

## The race the handbook's version has

The chapter's `sigchld_handler` reaps *any* child with `waitpid(-1, ...)`.
That includes the foreground command that `execute_command()` is about to wait
for. If the handler wins, the shell's own `waitpid(pid)` fails with `ECHILD`
and the command's exit status is lost.

ShellForge closes the gap by blocking SIGCHLD from just before `fork()` until
the shell's `waitpid()` returns (`foreground_begin()` / `foreground_end()`,
using `sigprocmask()`). A SIGCHLD raised meanwhile stays pending and is
delivered when the block lifts, by which time the shell has already collected
its child. The handler then mops up anything else.

Two consequences, both handled in `reset_child_signals()`, which runs in every
child before `execvp()`:

1. **A blocked signal mask survives `exec`.** Without unblocking, every program
   ShellForge runs would start with SIGCHLD blocked.
2. **An ignored signal stays ignored across `exec`.** A caught one reverts to
   the default on its own. But a shell started in the background by `sh`
   inherits SIGINT *ignored*, so the child resets it explicitly to make Ctrl+C
   reach it.

## Ctrl+Z

ShellForge has no job control yet: no `fg`/`bg` and no process groups per job.
If Ctrl+Z stopped a foreground child, nothing could resume it, and the shell's
`waitpid()` would wait for ever. So SIGTSTP is ignored by the shell, and the
ignore is inherited by the programs it starts. Ctrl+Z does nothing until job
control is added.

## signal() vs sigaction()

The handbook uses `signal()` and notes that later chapters may move to
`sigaction()`. ShellForge uses `sigaction()` now, for one specific flag:

| | `signal()` | `sigaction()` |
|---|---|---|
| Standard | behaviour varies between systems | POSIX, precisely specified |
| Handler stays installed after firing | not guaranteed (System V resets it) | yes |
| Block other signals during the handler | no control | `sa_mask` |
| Restart interrupted system calls | implementation-defined | `SA_RESTART` |

`SA_RESTART` matters here. Without it, a Ctrl+C at the prompt interrupts the
`read()` underneath `getchar()`. `read_line()` would then see an error that
looks like end-of-input.

## Async-signal safety

The handbook's handler calls `printf()`. `printf()` is not async-signal-safe: if
the signal lands while the main program is inside `printf()`, holding stdio's
internal lock or half-way through updating its buffer, a second `printf()` from
the handler can deadlock or corrupt output. ShellForge's handlers use only
`write()` and `waitpid()`, which POSIX lists as safe to call from a handler.

## Verification — `tests/test_week6.sh` (21 assertions)

| Check | How |
|---|---|
| Ctrl+C at the prompt | `kill -INT` the idle shell: message printed, next command still runs, exit code 0 |
| Ctrl+C during `sleep 5` | shell run under `setsid` (own process group, as a terminal gives it); `kill -INT -<pgid>` ends `sleep` in about 2 s; the shell continues |
| Ctrl+C during `sleep 5 \| cat` | both pipeline children end; the shell continues |
| Ctrl+Z | `kill -TSTP -<pgid>`: no process in state `T`; the session completes |
| Child signal state | `grep Sig /proc/self/status` run by the shell: SIGINT not ignored, SigBlk 0 |
| Zombie reaping | `tests/sigchld_check.c`: an unwaited child is `Z` without the handler and gone with it |
| Exit status not stolen | `true`, `false`, a missing command: no `waitpid error` |
| Sanitizers | ASan + UBSan build survives SIGINT with no report |

```
without a SIGCHLD handler: child 122 state Z (zombie)
with the SIGCHLD handler:  child 123 reaped (no /proc entry)
earlier zombie 122: reaped too
```
