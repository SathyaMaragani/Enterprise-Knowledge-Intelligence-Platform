# ShellForge

ShellForge is a Unix-like shell developed as part of the OSSP project.

## Week 1 Features

- Interactive REPL loop
- ShellForge startup banner
- Exit command
- Makefile-based build
- Modular source/header structure
- Git-based development

## Week 2 Features

- Dynamic command input
- malloc()
- realloc()
- free()
- Automatic buffer expansion
- Modular input module
- Long command support
- Memory cleanup

## Week 3 Features

- Command parsing using strtok()
- Dynamic argv[] construction
- Modular parser implementation
- Ready for process execution with execvp()

## Week 4 Features

- Process creation with `fork()`
- Program execution with `execvp()`
- Process synchronization with `waitpid()`
- Exit status and signal tracking (`WIFEXITED`, `WEXITSTATUS`, `WIFSIGNALED`)
- Robust error handling for invalid commands and fork exhaustion
- Modular executor (`include/executor.h`, `src/executor.c`)
- Stream flushing to prevent parent/child stdio buffer duplication
- Automated test suite (`tests/test_week4.sh`, 21 assertions passing)

## Week 5 Features

- Built-in command dispatch, executed in the shell process rather than a child
- `cd` using `chdir()`, defaulting to `$HOME`, keeping `PWD` in step
- `pwd` using `getcwd()`, with the return value checked
- `env` using `getenv()`, NULL-safe for unset variables
- `clear` via the ANSI erase sequence (no subprocess)
- `help` listing the built-ins
- `exit` unwinding through `main()` so allocations are freed first
- Fall-through to `fork()`/`execvp()` for anything that is not a built-in
- Modular built-ins (`include/builtin.h`, `src/builtin.c`)
- Automated test suite (`tests/test_week5.sh`)

## Week 6 Features

- Inter-Process Communication (IPC) via `pipe()`
- Parent → Child unidirectional communication
- Child → Parent unidirectional communication
- File descriptor redirection using `dup2()`
- Two-process pipelines (`cmd1 | cmd2`)
- Pipe metacharacter tokenization with or without spaces (`cmd1|cmd2`)
- Rigorous file-descriptor cleanup in parent and child to prevent deadlocks
- Verified EOF detection behavior (`read() == 0`)
- `--demo-ipc` CLI flag and interactive `demo-ipc` shell command
- Automated test suite (`tests/test_week6.sh`)

## Current Version

ShellForge Version 6.0

## Build

```bash
make
```

## Run

```bash
make run
```

## Test

```bash
make test
```

## Build Environment

This Windows host has no C toolchain, so the project is built and tested in a
container:

```bash
docker run --rm -v "$(pwd):/src" -w /src gcc:13 sh -c "make clean && make test"
```

`make`, `make run`, and `make test` work unchanged on any Linux machine with gcc installed.
