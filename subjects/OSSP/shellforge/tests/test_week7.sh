#!/bin/sh
set -e

echo "=========================================="
echo " Running ShellForge Week 6 Test Suite     "
echo "=========================================="

PASSED=0
FAILED=0

assert_contains() {
    TEST_NAME="$1"
    EXPECTED="$2"
    OUTPUT="$3"

    if echo "$OUTPUT" | grep -F "$EXPECTED" > /dev/null; then
        echo "[PASS] $TEST_NAME"
        PASSED=$((PASSED + 1))
    else
        echo "[FAIL] $TEST_NAME"
        echo "  Expected to contain: $EXPECTED"
        echo "  Actual output: $OUTPUT"
        FAILED=$((FAILED + 1))
    fi
}

assert_exit_code() {
    TEST_NAME="$1"
    EXPECTED_CODE="$2"
    ACTUAL_CODE="$3"

    if [ "$EXPECTED_CODE" -eq "$ACTUAL_CODE" ]; then
        echo "[PASS] $TEST_NAME (exit code: $ACTUAL_CODE)"
        PASSED=$((PASSED + 1))
    else
        echo "[FAIL] $TEST_NAME"
        echo "  Expected exit code: $EXPECTED_CODE, got: $ACTUAL_CODE"
        FAILED=$((FAILED + 1))
    fi
}

# 1. Clean build test
echo "--- 1. Compilation Test ---"
make clean > /dev/null
if make; then
    echo "[PASS] Build under -Wall -Wextra"
    PASSED=$((PASSED + 1))
else
    echo "[FAIL] Build failed"
    FAILED=$((FAILED + 1))
fi

# 2. Banner test
echo "--- 2. Banner & Version Test ---"
OUT=$(printf "exit\n" | ./bin/shellforge)
assert_contains "Startup banner Version 6.0" "Welcome to ShellForge Version 6.0" "$OUT"
assert_contains "Clean exit message" "Exiting ShellForge..." "$OUT"

# 3. Direct IPC Demonstrations (--demo-ipc)
echo "--- 3. IPC Demonstrations ---"
IPC_OUT=$(./bin/shellforge --demo-ipc)
assert_contains "Parent -> Child IPC" "Received from parent: \"Hello Child from Parent!\"" "$IPC_OUT"
assert_contains "Child -> Parent IPC" "Received from child: \"Greetings Parent from Child!\"" "$IPC_OUT"
assert_contains "EOF Detection (0 bytes)" "Reader read() returned: 0 byte(s) (0 indicates EOF received)" "$IPC_OUT"

# 4. Interactive demo-ipc command inside shell
echo "--- 4. Interactive demo-ipc Command ---"
SHELL_IPC=$(printf "demo-ipc\nexit\n" | ./bin/shellforge)
assert_contains "Interactive demo-ipc command" "IPC Demonstration Completed Successfully" "$SHELL_IPC"

# 5. Basic two-process pipeline
echo "--- 5. Basic Two-Process Pipelines ---"
OUT=$(printf "echo Hello World | grep Hello\nexit\n" | ./bin/shellforge)
assert_contains "echo | grep pipeline" "Hello World" "$OUT"

OUT=$(printf "seq 1 5 | wc -l\nexit\n" | ./bin/shellforge)
assert_contains "seq | wc -l pipeline" "5" "$OUT"

# 6. Pipeline tokenization without spaces
echo "--- 6. Pipeline Tokenization without Spaces ---"
OUT=$(printf "echo unspaced_pipe|cat\nexit\n" | ./bin/shellforge)
assert_contains "Unspaced pipe cmd1|cmd2" "unspaced_pipe" "$OUT"

# 7. Multi-argument pipeline
echo "--- 7. Multi-argument Pipeline ---"
OUT=$(printf "ls -la bin | grep shellforge\nexit\n" | ./bin/shellforge)
assert_contains "ls -la bin | grep shellforge" "shellforge" "$OUT"

# 8. Syntax error handling
echo "--- 8. Pipeline Syntax Error Handling ---"
OUT=$(printf "| ls\nexit\n" | ./bin/shellforge 2>&1)
assert_contains "Leading pipe syntax error" "ShellForge: syntax error near unexpected token '|'" "$OUT"

OUT=$(printf "ls |\nexit\n" | ./bin/shellforge 2>&1)
assert_contains "Trailing pipe syntax error" "ShellForge: syntax error near unexpected token '|'" "$OUT"

# 9. Boundary enforcement: Reject multi-stage pipelines
echo "--- 9. Multi-stage Pipeline Boundary Check ---"
OUT=$(printf "echo 1 | echo 2 | echo 3\nexit\n" | ./bin/shellforge 2>&1)
assert_contains "Multi-stage pipeline boundary rejection" "multi-stage pipelines are not supported in Week 6" "$OUT"

# 10. Pipeline error handling: Left command failure
echo "--- 10. Pipeline Error Handling ---"
OUT=$(printf "nonexistent_left_cmd | wc -l\nexit\n" | ./bin/shellforge 2>&1)
assert_contains "Invalid left command error" "ShellForge: nonexistent_left_cmd: No such file or directory" "$OUT"

OUT=$(printf "echo hello | nonexistent_right_cmd\nexit\n" | ./bin/shellforge 2>&1)
assert_contains "Invalid right command error" "ShellForge: nonexistent_right_cmd: No such file or directory" "$OUT"

# 11. EOF handling
echo "--- 11. EOF Termination ---"
OUT=$(printf "" | ./bin/shellforge)
EXIT_CODE=$?
assert_exit_code "Empty input EOF exit code" 0 $EXIT_CODE

# 12. Dynamic buffer & token growth in pipeline
echo "--- 12. Dynamic Buffer & Token Growth in Pipeline ---"
LONG_STR=$(awk 'BEGIN { for (i=1; i<=1200; i++) printf "k"; print "" }')
OUT=$(printf "echo %s | cat\nexit\n" "$LONG_STR" | ./bin/shellforge)
assert_contains "Long argument in pipeline (>1024 chars)" "$LONG_STR" "$OUT"

MANY_ARGS=$(awk 'BEGIN { for (i=1; i<=100; i++) printf "w%d ", i; print "" }')
OUT=$(printf "echo %s | grep w50\nexit\n" "$MANY_ARGS" | ./bin/shellforge)
assert_contains "Large argument vector in pipeline" "w50" "$OUT"

# 13. Sanitizers (ASan + UBSan)
echo "--- 13. Memory & Sanitizer Verification ---"
make clean > /dev/null
make CFLAGS="-Wall -Wextra -g -Iinclude -fsanitize=address,undefined" > /dev/null
SAN_OUT=$(printf "demo-ipc\necho sanitizer_pipe | cat\nseq 1 4 | wc -l\nexit\n" | ./bin/shellforge 2>&1)
assert_contains "ASan IPC demo" "IPC Demonstration Completed Successfully" "$SAN_OUT"
assert_contains "ASan pipeline execution" "sanitizer_pipe" "$SAN_OUT"
assert_contains "ASan pipeline output count" "4" "$SAN_OUT"
assert_contains "ASan clean exit" "Exiting ShellForge..." "$SAN_OUT"

echo "=========================================="
echo " Test Summary: $PASSED passed, $FAILED failed"
echo "=========================================="

if [ "$FAILED" -ne 0 ]; then
    exit 1
fi
