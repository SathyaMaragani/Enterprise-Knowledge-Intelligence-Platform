# OSSP Week 2 - Dynamic Command Input

## The C Compilation Pipeline

Source code goes through several stages before becoming an executable:

1. **Source**: Human-readable `.c` code.
2. **Preprocessor**: Handles `#include` and `#define` directives, expanding macros and including headers.
3. **Compiler**: Translates C code into assembly language.
4. **Assembler**: Converts assembly into machine-readable object files (`.o`).
5. **Linker**: Combines multiple object files and libraries into a final executable.
6. **Executable**: The final program that the OS can run.

## Memory Management

- **Stack memory**: Automatically managed memory used for local variables. Fast but limited in size.
- **Heap memory**: Manually managed memory used for dynamic allocation. Larger but requires careful management.
- **`malloc()`**: Allocates a specified number of bytes on the heap and returns a pointer.
- **`realloc()`**: Resizes an existing heap allocation. If it cannot expand in place, it allocates new space, copies the data, and frees the old space.
- **`free()`**: Releases previously allocated heap memory back to the system.
- **Why dynamic allocation is needed**: Fixed-size arrays limit the size of user input. Dynamic allocation allows the buffer to grow to accommodate arbitrarily long commands.
- **Why memory leaks occur**: When dynamically allocated memory is no longer needed but `free()` is never called, the memory remains occupied.
- **Why `free()` is necessary**: To prevent memory leaks and ensure the system reclaims unused memory for future allocations.
