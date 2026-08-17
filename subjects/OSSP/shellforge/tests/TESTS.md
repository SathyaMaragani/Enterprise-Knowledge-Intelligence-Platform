# ShellForge Tests

## Test Procedure

1. **Program starts successfully.**
   - Action: Run `./bin/shellforge`.
   - Expected: Program launches without errors.
2. **Startup banner appears.**
   - Action: Observe output.
   - Expected: `Welcome to ShellForge Version 2.0` banner is displayed.
3. **Normal input is displayed.**
   - Action: Enter `hello`.
   - Expected: Output shows `You entered : hello`.
4. **Multiple commands can be entered.**
   - Action: Enter multiple words in succession.
   - Expected: Each command is acknowledged sequentially.
5. **"exit" terminates the program.**
   - Action: Enter `exit`.
   - Expected: Output `Exiting ShellForge...` and program cleanly exits.
6. **EOF terminates gracefully.**
   - Action: Press Ctrl+D (Unix) or Ctrl+Z (Windows).
   - Expected: Program handles EOF without infinite loops and cleanly exits.
7. **Project builds using make.**
   - Action: Run `make clean` then `make`.
   - Expected: Source code compiles cleanly with no warnings or errors.
8. **Long input test.**
   - Action: Paste a string of >1024 characters into the prompt.
   - Expected: Program handles the long input without crashing or truncating, outputs the exact long string, and safely frees memory.
9. **Memory validation.**
   - Action: Run the executable under Valgrind (e.g., `valgrind --leak-check=full ./bin/shellforge`).
   - Expected: Zero memory leaks, zero invalid reads/writes.
