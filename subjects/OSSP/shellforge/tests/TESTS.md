# ShellForge Tests

## Build Environment

This Windows host has no `gcc`/`make`, so all tests below are executed inside a
container:

```bash
docker run --rm -i -v "$(pwd):/src" -w /src gcc:13 sh -c "make clean && make && ./bin/shellforge"
```

## Test Procedure

1. **Program starts successfully.**
   - Action: Run `./bin/shellforge`.
   - Expected: Program launches without errors.
2. **Startup banner appears.**
   - Action: Observe output.
   - Expected: `Welcome to ShellForge Version 3.0` banner is displayed.
3. **A command is tokenized into argv[].**
   - Action: Enter `ls -l /home`.
   - Expected: `argv[0] = ls`, `argv[1] = -l`, `argv[2] = /home`, `argv[3] = NULL`.
4. **Single-word command.**
   - Action: Enter `pwd`.
   - Expected: `argv[0] = pwd`, `argv[1] = NULL`.
5. **Irregular spacing collapses.**
   - Action: Enter `  ls   -l    /home  ` with leading, trailing and repeated spaces, and a tab.
   - Expected: Identical result to test 3 — consecutive delimiters produce no empty tokens.
6. **Multiple commands can be entered.**
   - Action: Enter several commands in succession.
   - Expected: Each is tokenized independently; no state leaks between lines.
7. **Empty and whitespace-only input.**
   - Action: Press Enter on an empty line, then enter a line of only spaces and tabs.
   - Expected: No token output, no crash, prompt returns.
8. **"exit" terminates the program.**
   - Action: Enter `exit`.
   - Expected: Output `Exiting ShellForge...` and program cleanly exits.
9. **EOF terminates gracefully.**
   - Action: Press Ctrl+D (Unix) or Ctrl+Z (Windows).
   - Expected: Program handles EOF without infinite loops and cleanly exits.
10. **Project builds using make.**
    - Action: Run `make clean` then `make`.
    - Expected: Compiles with no warnings or errors under `-Wall -Wextra`.
11. **Long input test.**
    - Action: Paste a string of >1024 characters into the prompt.
    - Expected: The input buffer grows without crashing or truncating, and memory is freed.
12. **Token vector growth.**
    - Action: Enter a command with more than 64 arguments (200 exercises two growth steps).
    - Expected: All tokens are returned in order and the vector is `NULL`-terminated at the correct index.
13. **Memory validation.**
    - Action: Build with `-fsanitize=address,undefined` and run the cases above,
      or run under Valgrind (`valgrind --leak-check=full ./bin/shellforge`).
    - Expected: Zero memory leaks, zero invalid reads/writes, zero undefined behaviour.
    - Note: confirm the leak detector is actually active before trusting a clean
      result — compile a deliberate one-line leak and check it is reported.

## Results (Week 3)

Executed in `gcc:13`. All of the above pass:

| Test | Result |
|---|---|
| Build under `-Wall -Wextra` | Clean, zero warnings |
| Tokenization (tests 3-6) | Matches expected `argv[]` exactly |
| Empty / whitespace-only (test 7) | No output, no crash |
| `exit` and EOF (tests 8-9) | Both exit cleanly; empty stdin exits 0 |
| Long input, 3000 chars (test 11) | Buffer grew, token returned intact at full length |
| 200-token growth (test 12) | `argv[200] = NULL`, all tokens in order |
| ASan + UBSan (test 13) | No reports; detector verified against a control leak |
