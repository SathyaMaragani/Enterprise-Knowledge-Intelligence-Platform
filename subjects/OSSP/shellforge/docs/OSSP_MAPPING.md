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

**CO-3 — Inter-Process Communication (IPC):**
- Anonymous pipes via `pipe()` system call
- Unidirectional byte-stream communication model
- Parent → Child IPC demonstration
- Child → Parent IPC demonstration
- File descriptor redirection via `dup2()`
- Two-process command pipeline (`cmd1 | cmd2`)
- File descriptor lifecycle management and EOF semantics
- Deadlock prevention through parent write-end descriptor closure
