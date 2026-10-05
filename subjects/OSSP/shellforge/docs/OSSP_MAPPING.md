# OSSP Syllabus Mapping

## Week 1

**CO-1:**
- Shell as user-space interface
- Linux development environment
- Systems programming basics
- REPL
- Compilation/build process

## Week 2

**CO-1:**
- Systems programming fundamentals
- C compilation pipeline

**CO-4 foundation:**
- Stack vs heap
- Dynamic memory allocation
- malloc
- realloc
- free
- Memory errors/leaks

## Week 3

**CO-1:**
- Command-line parsing and lexical analysis
- Tokenization using `strtok()`
- Dynamic argument vector (`argv[]`) construction
- Memory layout for `execvp()` execution shape

## Week 4

**CO-2 — Processes:**
- Process concept vs passive program on disk
- Process address space and lifecycle (New, Ready, Running, Waiting, Terminated)
- `fork()` system call: process creation, PID return values, parent/child branching
- `execvp()` system call: address space replacement, PATH resolution, argument passing
- `waitpid()` system call: process synchronization, zombie process prevention
- Process termination status inspection (`WIFEXITED`, `WEXITSTATUS`, `WIFSIGNALED`, `WTERMSIG`)
- Robust error handling: fork exhaustion, command not found, child `_exit()` safety

## Week 5

**CO-2 — Built-in Commands and Process State:**
- Built-in versus external command dispatch
- Why `cd` cannot be forked: working directory is per-process state in the PCB
- `chdir()` system call: changing the shell's own working directory
- `getcwd()` system call: reading the working directory, with error handling
- `getenv()` / `setenv()`: reading and updating the process environment
- Environment variable model: `HOME`, `USER`, `PATH`, `SHELL`, `PWD`
- `PWD` maintained explicitly, since it does not follow `chdir()` on its own
- Clean shutdown ordering: `exit` unwinds to `main()` so allocations are freed

## Week 6

**CO-2 / CO-3 — Signals and Process Control:**
- Signals as asynchronous software interrupts; synchronous vs asynchronous events
- Signal dispositions: default, ignore, catch; SIGKILL/SIGSTOP cannot be caught
- `sigaction()` with `SA_RESTART` (and how it differs from `signal()`)
- SIGINT: the shell survives Ctrl+C; the foreground child receives the default action
- SIGCHLD: reaping with `waitpid(-1, NULL, WNOHANG)`; zombie processes (state `Z`)
- Race between the SIGCHLD reaper and the foreground `waitpid()`, closed with `sigprocmask()`
- Signal masks and ignored dispositions are inherited across `fork()` and `exec()`
- SIGTSTP ignored until job control exists
- Async-signal safety: `write()` in handlers, not `printf()`

## Week 7

**CO-3 — Inter-Process Communication (IPC):**
- Anonymous pipes via `pipe()` system call
- Unidirectional byte-stream communication model
- Parent → Child IPC demonstration
- Child → Parent IPC demonstration
- File descriptor redirection via `dup2()`
- Two-process command pipeline (`cmd1 | cmd2`)
- File descriptor lifecycle management and EOF semantics
- Deadlock prevention through parent write-end descriptor closure

## Week 8

**CO-4 — Memory Management and Debugging:**
- Process memory layout: text, data, BSS, heap, stack
- Memory errors: leaks, dangling pointers, overflows, double free, invalid reads and writes
- Valgrind `--leak-check=full`: a clean report, plus a deliberate 200-byte leak as a control
- GDB: breakpoints, backtrace, stepping, inspecting variables
- AddressSanitizer / UndefinedBehaviorSanitizer (`make asan`), with a heap-buffer-overflow control
- Defensive programming: checking every allocation and system call; fixes from the review

## Week 9

**CO-5 — File Descriptors and I/O Redirection:**
- File descriptor table; stdin/stdout/stderr as fds 0, 1, 2
- `open()` flags: `O_RDONLY`, `O_WRONLY`, `O_CREAT`, `O_TRUNC`, `O_APPEND`; mode `0644` and umask
- `dup2()` to redirect a standard stream; `close()` to release the original
- Operators `>`, `>>`, `<`, `2>`, with or without spaces, combined, and inside pipelines
- Redirecting built-ins in the shell process: saving and restoring fds 0-2
- Close-on-exec (`F_DUPFD_CLOEXEC`) to stop descriptor leaks into children
