# OSSP Week 6 — Pipes & IPC Foundation (CO-3)

## 1. Concepts & Architecture

### What is a Pipe?
A pipe is a fundamental unidirectional inter-process communication (IPC) channel provided by the Unix kernel:
- **Unidirectional byte stream**: Data written to the write-end flows to the read-end on a First-In, First-Out (FIFO) basis.
- **Kernel-managed buffer**: Pipes do not write to the disk filesystem; bytes reside in a bounded kernel memory buffer (typically 64 KB on Linux).
- **Transient channel**: Anonymous pipes exist only as long as processes hold open file descriptors referencing them.

### The `pipe()` System Call
Allocates an anonymous pipe in kernel memory and returns two new file descriptors:

```c
int pipefd[2];
if (pipe(pipefd) == -1) {
    perror("pipe");
    return -1;
}
```

- **`pipefd[0]`**: Read end of the pipe (data is retrieved using `read()`).
- **`pipefd[1]`**: Write end of the pipe (data is pushed using `write()`).

---

## 2. File Descriptor Lifecycle & EOF Behavior

### Crucial File Descriptor Cleanup Rules
Proper file descriptor lifecycle management is essential in pipeline programming:

1. **Closing unused ends in child processes**:
   - The writing child closes `pipefd[0]` before executing.
   - The reading child closes `pipefd[1]` before executing.
2. **Closing both ends in the parent process**:
   - Immediately after forking both children, the parent **must close both `pipefd[0]` and `pipefd[1]`**.

```text
       +-------------------------------------------------------------+
       |                        Parent Shell                         |
       |  pipe(pipefd) -> forks Child 1 & Child 2                    |
       |  CRITICAL: Parent closes pipefd[0] and pipefd[1]            |
       +-------------------------------------------------------------+
               |                                             |
               v                                             v
    +-----------------------+                     +-----------------------+
    | Child 1 (Producer)    |                     | Child 2 (Consumer)    |
    | dup2(pipefd[1], 1)    |                     | dup2(pipefd[0], 0)    |
    | closes pipefd[0], [1] |                     | closes pipefd[0], [1] |
    | execvp(args1)         |                     | execvp(args2)         |
    | writes to STDOUT      |                     | reads from STDIN      |
    +-----------------------+                     +-----------------------+
               |                                             ^
               |            [ Kernel Pipe Buffer ]           |
               +=======> [ pipefd[1] ===> pipefd[0] ] =======+
```

### Why Orphaned Write Descriptors Cause Deadlock
The `read()` system call on a pipe blocks until:
1. Data becomes available, or
2. **All write descriptors to the pipe are closed**, at which point `read()` returns **0** (End-Of-File, EOF).

If the parent process forgets to close `pipefd[1]`, the kernel considers the write end still open. Even when Child 1 terminates, Child 2's `read()` will block forever waiting for input from the parent, causing the pipeline to hang indefinitely!

---

## 3. Redirection via `dup2()`

The `dup2(int oldfd, int newfd)` system call duplicates an open file descriptor:
- If `newfd` is already open, it is silently closed first.
- `newfd` now refers to the same open file description as `oldfd`.

In ShellForge pipelines:
- **Child 1 (Producer)**:
  `dup2(pipefd[1], STDOUT_FILENO);`
  Standard output (FD 1) is redirected into the pipe write end.
- **Child 2 (Consumer)**:
  `dup2(pipefd[0], STDIN_FILENO);`
  Standard input (FD 0) is redirected from the pipe read end.

---

## 4. Demonstrating IPC in ShellForge

ShellForge provides an interactive and CLI-accessible IPC demonstration:

```bash
./bin/shellforge --demo-ipc
# or inside the shell:
myshell> demo-ipc
```

The demonstration exercises:
1. **Parent → Child IPC**: Parent writes `"Hello Child from Parent!"` into a pipe; Child reads and echoes to stdout.
2. **Child → Parent IPC**: Child writes `"Greetings Parent from Child!"` into a pipe; Parent reads and echoes to stdout.
3. **EOF Verification**: Writer closes `pipefd[1]`; Reader verifies `read()` returns `0`, confirming clean EOF signaling.

---

## 5. Pipeline Execution in the Shell

Typing `cmd1 | cmd2` (e.g. `echo Hello World | grep Hello` or `seq 1 5 | wc -l`):
1. **Parser (`parser.c`)**: Recognizes `|` as a distinct token even without spaces (e.g. `cmd1|cmd2`).
2. **Validator (`main.c`)**:
   - Ensures `|` is neither the first nor last argument.
   - Enforces the Week 5 scope boundary: multi-stage pipelines (3+ commands) are rejected with a clear educational message.
3. **Executor (`executor.c`)**:
   - Creates anonymous pipe.
   - Forks Child 1 and Child 2.
   - Closes pipe descriptors in parent.
   - Waits for both children via `waitpid()`.

---

## 6. Verification Summary

Executed inside the `gcc:13` Docker container:
- **Build**: Compiles cleanly with `-Wall -Wextra -g`.
- **Automated Tests**: 23/23 assertions passed in `tests/test_week5.sh`.
- **Regression Tests**: 21/21 assertions passed in `tests/test_week4.sh`.
- **Memory Safety**: Clean under `-fsanitize=address,undefined` (ASan/UBSan) with zero leaks and zero errors.
