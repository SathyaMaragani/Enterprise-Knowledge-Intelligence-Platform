# OSSP Week 10 — Threads and Concurrency with POSIX Threads (CO-6)

**Milestone:** add multithreading and synchronisation to ShellForge: a
background monitor thread, `pthread_join()`, and a mutex protecting shared data.

Module: `include/thread.h`, `src/thread.c`. The Makefile links with `-pthread`.

## Process vs thread

| Process | Thread |
|---|---|
| Own address space | Shares the process's address space |
| Created with `fork()` | Created with `pthread_create()` |
| Talks to others through IPC (Week 7 pipes) | Talks to others through shared memory |
| Higher creation cost | Lower creation cost |

```
                 ShellForge process
      +--------------------------------------+
      |  code, globals, heap: shared          |
      +--------------------------------------+
        main thread           monitor thread
        stack + registers     stack + registers
        (prompt, commands)    (heartbeat)
```

`ls /proc/<pid>/task` lists one entry per thread: 2 while the monitor runs,
1 with it turned off. The Week 10 tests check exactly that.

## The monitor thread

```c
start_monitor_thread();   /* in main(), after initialize_signals() */
...
stop_monitor_thread();    /* before main() returns */
```

Every `SHELLFORGE_MONITOR` seconds (default 10; `0` turns it off) it prints:

```
myshell> sleep 2.5

[Monitor] ShellForge Running...

[Monitor] ShellForge Running...
myshell>
```

The main thread waits for `sleep` in `waitpid()` while the monitor keeps
running: two threads, one process.

## Race condition and mutex: `demo-threads`

```
myshell> demo-threads 4 100000
[Threads] 4 threads x 100000 increments, expected total 400000
  Without a mutex: counter = 330197 (69803 updates lost to the race), 1.1 ms
  With a mutex:    counter = 400000 (0 lost), 5.7 ms
[Threads] all 4 workers joined with pthread_join()
```

`counter++` is three steps: load, add, store. Two threads can load the same
value, both add 1, and both store it, so one increment is lost. The second run
puts the increment in a critical section:

```
Thread A                        Thread B
pthread_mutex_lock(&lock)
counter++                       pthread_mutex_lock(&lock)   <- blocks
pthread_mutex_unlock(&lock)       ...
                                counter++
                                pthread_mutex_unlock(&lock)
```

- **Joining.** `pthread_join()` waits for every worker before the counter is read.
- **The cost of the lock.** The mutex makes the result exact, and about five
  times slower here, because the threads now take turns.
- **Arguments.** `demo-threads [threads 1-16] [increments 1-1000000]`, with
  defaults 4 and 100,000 as in the handbook. Anything else prints a usage line
  and the shell carries on.
- **A built-in.** It runs in the shell process, because its threads must share
  that process's memory. Its output can be redirected: `demo-threads > out.txt`.
- **Lost updates vary.** On one CPU the threads rarely overlap and few or no
  updates are lost. That is the nature of a race, not a sign it is safe.

## Threads and signals

Week 6 blocks SIGCHLD while the shell waits for a command, so the reaper cannot
collect that command first. But `sigprocmask()` changes the mask of the calling
thread only. A process-directed signal goes to any thread that does not block
it, so with a second thread SIGCHLD is delivered to the monitor instead. The
reaper then runs there, collects the foreground child, and the shell's own
`waitpid()` fails:

```
ShellForge: waitpid error: No child processes
```

So `thread.c` creates every thread with all signals blocked: it fills the mask
with `pthread_sigmask()` around `pthread_create()` (a new thread inherits its
creator's mask), then restores it. SIGINT and SIGCHLD keep going to the main
thread, as in Week 6.

This was confirmed against a mutation. Without the mask, 264 of 300 commands
lost their exit status; with it, none did. The Week 10 suite runs 100 commands
and requires no `waitpid` error.

## Differences from the handbook listing

| Handbook | ShellForge | Why |
|---|---|---|
| `sleep(10)` in an endless loop; `pthread_detach()` | `pthread_cond_timedwait()` on a condition variable; `pthread_join()` at exit | A detached, sleeping thread cannot be stopped. Exit has to wait up to 10 s or end with the thread mid-sleep, and Valgrind then reports its stack. `stop_monitor_thread()` wakes it at once and joins it. |
| Thread created with the inherited signal mask | All signals blocked in new threads | The SIGCHLD problem above |
| Fixed 10 s interval | `SHELLFORGE_MONITOR` seconds, `0` for off | Tests and the web page need a 1 s heartbeat or none |
| Race and mutex shown as snippets | `demo-threads` built-in | Shows both totals side by side, and runs from the web page |
| `pthread_create()` result ignored | Checked; errors reported with `strerror(rc)` | pthread functions return the error, they do not set `errno` |
| `process.c` | `executor.c` | Kept from Week 4 |

`counter` is `volatile` so each `++` is a real load, add and store, as at
`-O0`. It does not make the increment atomic: that is the race being shown.

## Transcript mode: `--echo`

`shellforge --echo` prints each line it reads after the prompt, so a scripted
session reads like a typed one. The web app's ShellForge page uses it (see below).

## In the web app

The backend image compiles ShellForge and `ShellForgeService` runs it for the
**ShellForge** page (`/shellforge`):

- **Race lab.** Choose threads (1-8) and increments (1-200,000). The page shows
  the expected total, the unprotected total with the updates lost, the mutex total,
  and the transcript.
- **Sessions.** Fixed command lists for Weeks 4, 5, 7, 9 and 10 (the monitor),
  each run in a fresh empty directory. The shell gets only `PATH`, `HOME`,
  `PWD`, `USER`, `SHELL` and `LANG`, never the server's own variables. Runs have
  a 20-second limit.

Typing arbitrary commands is deliberately not offered: it would let any
signed-in user run programs on the server.

## Verification — `tests/test_week10.sh` (32 checks)

| Area | Cases |
|---|---|
| Build | compiles under `-Wall -Wextra` with `thread.c`; `-pthread` in the Makefile |
| Monitor | heartbeat every second during a 2.5 s command; off with `SHELLFORGE_MONITOR=0`; 2 entries in `/proc/<pid>/task` with it, 1 without; `exit` joins it in milliseconds despite a 30 s interval |
| Signals | 100 commands with the monitor running, no stolen exit status; pipeline waits unaffected |
| `demo-threads` | expected total; mutex total exact; workers joined; unprotected total never above expected; one thread cannot race; defaults; 5 out-of-range arguments rejected; redirection; listed in `help` |
| `--echo` | each line follows the prompt, ahead of the command's own error |
| ThreadSanitizer | reports the unprotected `counter++`, and every race it reports is in `unsafe_worker`: the mutex worker and the monitor are clean |
| ASan, UBSan, Valgrind | a threaded session completes with no sanitizer report; Valgrind: all heap blocks freed, 0 errors |

ThreadSanitizer needs address-space randomisation narrowed (`setarch -R`),
which Docker's default seccomp profile blocks. There the suite reports those
two checks as skipped, not failed; run the container with
`--security-opt seccomp=unconfined` to include them (see `tests/TESTS.md`).
Weeks 4-9 also pass unchanged with the monitor thread running.
