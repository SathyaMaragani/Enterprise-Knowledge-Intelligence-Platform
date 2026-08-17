# OSSP Week 1 - REPL Loop, Repository Setup, Makefile

## Concepts

- **What is a shell?**
  A shell is a user-space interface that provides access to the operating system's services. It takes input commands from the user and passes them to the OS to execute.
- **Shell vs Linux kernel:**
  The kernel is the core of the operating system that directly controls hardware and memory. The shell is a program running on top of the kernel that provides a user-friendly interface.
- **What is a REPL?**
  REPL stands for Read-Evaluate-Print Loop. It is a simple interactive programming environment.
- **Read:**
  Taking the user's input from standard input.
- **Evaluate:**
  Processing the input (currently simply acknowledging it or checking for "exit").
- **Print:**
  Outputting the result back to the user.
- **Loop:**
  Repeating the process until termination.
- **Role of GCC:**
  The GNU Compiler Collection compiles the C source code into machine code that can be executed by the processor.
- **Role of Makefile:**
  A Makefile automates the build process, managing dependencies and simplifying compilation via the `make` tool.
- **Basic project structure:**
  Separating code into logical directories like `src/`, `include/`, `bin/`, and `docs/` helps maintain an organized and scalable codebase.
