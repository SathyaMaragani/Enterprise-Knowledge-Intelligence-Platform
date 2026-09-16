#!/bin/sh
set -e

echo "=========================================="
echo " Running ShellForge Week 4 Test Suite     "
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
assert_contains "Startup banner" "Welcome to ShellForge Version" "$OUT"
assert_contains "Clean exit message" "Exiting ShellForge..." "$OUT"

# 3. Basic command execution
echo "--- 3. Basic Command Execution ---"
OUT=$(printf "echo Hello ShellForge\nexit\n" | ./bin/shellforge)
assert_contains "Echo command execution" "Hello ShellForge" "$OUT"

OUT=$(printf "pwd\nexit\n" | ./bin/shellforge)
assert_contains "Pwd command execution" "/src" "$OUT"

# 4. Multi-argument execution
echo "--- 4. Multi-argument Execution ---"
OUT=$(printf "echo arg1 arg2 arg3 arg4\nexit\n" | ./bin/shellforge)
assert_contains "Multiple arguments" "arg1 arg2 arg3 arg4" "$OUT"

OUT=$(printf "ls -la bin\nexit\n" | ./bin/shellforge)
assert_contains "ls -la bin execution" "shellforge" "$OUT"

# 5. Non-existent command error handling
echo "--- 5. Non-existent Command Handling ---"
OUT=$(printf "nonexistent_cmd_12345\nexit\n" | ./bin/shellforge 2>&1)
assert_contains "Command not found error" "ShellForge: nonexistent_cmd_12345: No such file or directory" "$OUT"
assert_contains "Shell survives invalid command and exits" "Exiting ShellForge..." "$OUT"

# 6. Child non-zero exit handling
echo "--- 6. Child Non-Zero Exit Handling ---"
OUT=$(printf "false\necho still alive\nexit\n" | ./bin/shellforge)
assert_contains "Shell continues after child non-zero exit" "still alive" "$OUT"

# 7. Whitespace and empty input
echo "--- 7. Whitespace Handling ---"
OUT=$(printf "   \n\t  \n\necho after blanks\nexit\n" | ./bin/shellforge)
assert_contains "Blank lines ignored" "after blanks" "$OUT"

# 8. EOF termination
echo "--- 8. EOF Termination ---"
OUT=$(printf "" | ./bin/shellforge)
EXIT_CODE=$?
assert_exit_code "Empty input EOF exit code" 0 $EXIT_CODE

# 9. Long input (>1024 chars)
echo "--- 9. Long Input Test (>1024 characters) ---"
LONG_ARG=$(awk 'BEGIN { for (i=1; i<=1500; i++) printf "x"; print "" }')
OUT=$(printf "echo %s\nexit\n" "$LONG_ARG" | ./bin/shellforge)
assert_contains "1500-char input execution" "$LONG_ARG" "$OUT"

# 10. Large argument vector (200 tokens)
echo "--- 10. Large Argument Vector (200 tokens) ---"
MANY_ARGS=$(awk 'BEGIN { for (i=1; i<=200; i++) printf "t%d ", i; print "" }')
OUT=$(printf "echo %s\nexit\n" "$MANY_ARGS" | ./bin/shellforge)
assert_contains "200-argument token 1" "t1" "$OUT"
assert_contains "200-argument token 200" "t200" "$OUT"

# 11. Debug tokens mode (Preserving Week 3 verification)
echo "--- 11. Debug Tokens Mode ---"
OUT=$(printf "ls -l /home\nexit\n" | ./bin/shellforge --debug-tokens)
assert_contains "Debug tokens argv[0]" "argv[0] = ls" "$OUT"
assert_contains "Debug tokens argv[1]" "argv[1] = -l" "$OUT"
assert_contains "Debug tokens argv[2]" "argv[2] = /home" "$OUT"
assert_contains "Debug tokens NULL termination" "argv[3] = NULL" "$OUT"

# 12. AddressSanitizer and UndefinedBehaviorSanitizer
echo "--- 12. Memory & Sanitizer Verification ---"
make clean > /dev/null
make CFLAGS="-Wall -Wextra -g -Iinclude -fsanitize=address,undefined" > /dev/null
OUT=$(printf "echo Sanitizer Check\npwd\nls -la bin\nnonexistent_cmd\nexit\n" | ./bin/shellforge 2>&1)
assert_contains "ASan clean execution" "Sanitizer Check" "$OUT"
assert_contains "ASan clean exit" "Exiting ShellForge..." "$OUT"

echo "=========================================="
echo " Test Summary: $PASSED passed, $FAILED failed"
echo "=========================================="

if [ "$FAILED" -ne 0 ]; then
    exit 1
fi
