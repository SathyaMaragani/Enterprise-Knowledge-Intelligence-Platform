# OSSP Week 4 — Process Creation & Execution (CO-2)

## 1. Concepts & Architecture

### Program vs. Process
- **Program**: A passive entity stored on disk as an executable file (e.g. machine code, static data sections, ELF format in Linux).
- **Process**: An active instance of a program in execution. A process encapsulates:
  - An independent virtual address space (text, data, heap, stack).
  - CPU state (program counter, stack pointer, general-purpose registers).
  - Operating system state (Process Control Block (PCB), PID, open file descriptors, credentials, environment).

### Process Lifecycle
A process transitions through discrete states managed by the OS scheduler:
1. **New / Created**: Process created via `fork()`.
2. **Ready**: Process in memory, waiting to be assigned to a CPU core.
3. **Running**: Process instructions currently executing on CPU.
4. **Waiting / Blocked**: Process suspended awaiting an event or I/O completion.
5. **Terminated / Zombie**: Execution finished via `exit()`; PCB and exit status remain until reaped by parent.

```text
       +---------------------------------------------+
       |                                             |
       v                                             |
   [ New ] ---> [ Ready ] <---> [ Running ] ---> [ Terminated / Zombie ]
                    ^               |                |
                    |               v                v
                    +------- [ Waiting / Blocked ] [ Reaped by Parent ]
```

---

## 2. System Calls for Process Management

### 1. `fork()`
Creates an exact duplicate of the calling process:
- **Parent Process**: `fork()` returns the **child's PID** (`pid > 0`).
- **Child Process**: `fork()` returns **0**.
- **Failure**: Returns **-1** if process table limits (`EAGAIN`) or memory limits (`ENOMEM`) are reached.

```c
pid_t pid = fork();
if (pid < 0) {
    perror("ShellForge: fork error");
} else if (pid == 0) {
    /* Child execution context */
} else {
    /* Parent execution context */
}
```

### 2. `execvp()`
Replaces the current process image with a new process image loaded from an executable:
- **`file`**: Program filename or path. If no `/` is present, searches directories listed in the `PATH` environment variable.
- **`argv`**: NULL-terminated argument vector where `argv[0]` is conventionally the command name.
- **Return Behavior**: On success, `execvp()` **never returns** because the entire process address space is overwritten with the new program. If it returns (`-1`), an error occurred (e.g. `ENOENT` command not found, `EACCES` permission denied).
- **Critical child termination**: The child must call `_exit()` immediately upon `execvp()` failure to prevent continuing into the parent's REPL loop.

### 3. `waitpid()`
Suspends the calling process until the specified child changes state:
- `waitpid(pid, &status, WUNTRACED)` waits for the child process with PID `pid`.
- Prevents **zombie processes** by reading and releasing the child's termination status from the kernel.

### 4. Status Inspection Macros
- `WIFEXITED(status)`: True if child terminated normally.
- `WEXITSTATUS(status)`: Evaluates to low-order 8 bits of child's exit status.
- `WIFSIGNALED(status)`: True if child was terminated by an unhandled signal.
- `WTERMSIG(status)`: The signal number that caused child termination.

---

## 3. Shell Execution Flow

```text
User enters command: "ls -la bin"
          |
     read_line()       Dynamic input buffer expansion
          |
    parse_line()       Tokenized into argv[]: ["ls", "-la", "bin", NULL]
          |
   execute_command()
          |
       fork()
      /      \
     /        \
 (pid == 0)   (pid > 0)
    Child       Parent
     |           |
execvp()      waitpid()   Suspends until child completes
     |           |
Replaced by      |
   /bin/ls       |
     |           |
Terminates ---> Reaps child, extracts exit code
                 |
             Prints prompt "myshell> "
```

---

## 4. Key Implementation Details in ShellForge

1. **Modular Executor (`include/executor.h` / `src/executor.c`)**:
   Execution logic is isolated from the REPL driver (`main.c`) and parsing (`parser.c`).

2. **Stdio Buffer Safety**:
   `fflush(stdout)` and `fflush(stderr)` are invoked prior to `fork()`. Without flushing, unflushed stdio buffers in the parent would be duplicated into the child's address space upon `fork()`, causing repeated banner or prompt lines when executed in non-interactive pipelines.

3. **Termination with `_exit()`**:
   If `execvp()` fails in the child, `_exit()` is called instead of `exit()`, avoiding duplicate execution of parent exit handlers or stdio flushes.

4. **Preservation of Weeks 1–3**:
   - Dynamic input buffer growth (`malloc`/`realloc`/`free`) remains intact.
   - Dynamic token vector growth (`strtok`, argv doubling) remains intact.
   - Clean EOF and built-in `exit` command handling remains intact.
   - Optional `--debug-tokens` flag or `SHELLFORGE_DEBUG=1` environment variable preserves token inspection for grading/debugging.

---

## 5. Verification Summary

Executed inside the `gcc:13` Docker container:
- **Build**: Compiled cleanly under `-Wall -Wextra -g`.
- **Command Execution**: Verified `pwd`, `echo`, `ls -la bin`, multi-argument commands.
- **Error Handling**: Non-existent commands correctly report `ShellForge: <cmd>: No such file or directory` on `stderr` and shell prompt returns without crashing.
- **Exit Status**: Commands with non-zero exit codes (e.g. `false`) do not abort the shell.
- **Edge Cases**: Empty and whitespace-only lines handled cleanly; 1500-character input lines and 200-argument token vectors execute without issue.
- **Sanitizers**: Verified under `-fsanitize=address,undefined` (ASan/UBSan) with zero leaks and zero memory errors.
