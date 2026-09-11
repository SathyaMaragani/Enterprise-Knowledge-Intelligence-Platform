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

## Current Version

ShellForge Version 3.0

## Build

make

## Run

make run

## Build Environment

This Windows host has no C toolchain, so the project is built and tested in a
container:

```bash
docker run --rm -v "$(pwd):/src" -w /src gcc:13 sh -c "make clean && make"
```

`make` and `make run` work unchanged on any Linux machine with gcc installed.

*Note: Actual Linux command execution is intentionally NOT implemented yet and will be introduced in later OSSP milestones.*
