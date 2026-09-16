#!/bin/sh
set -e

echo "=========================================="
echo " Running ShellForge Week 5 Test Suite     "
echo " (Built-in Commands & Environment Vars)   "
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

assert_not_contains() {
    TEST_NAME="$1"
    UNEXPECTED="$2"
    OUTPUT="$3"

    if echo "$OUTPUT" | grep -F "$UNEXPECTED" > /dev/null; then
        echo "[FAIL] $TEST_NAME"
        echo "  Expected NOT to contain: $UNEXPECTED"
        echo "  Actual output: $OUTPUT"
        FAILED=$((FAILED + 1))
    else
        echo "[PASS] $TEST_NAME"
        PASSED=$((PASSED + 1))
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

# 1. Clean build
echo "--- 1. Compilation Test ---"
make clean > /dev/null
if make; then
    echo "[PASS] Build under -Wall -Wextra"
    PASSED=$((PASSED + 1))
else
    echo "[FAIL] Build failed"
    FAILED=$((FAILED + 1))
fi

# 2. pwd built-in
echo "--- 2. pwd Built-in ---"
OUT=$(printf "pwd\nexit\n" | ./bin/shellforge)
assert_contains "pwd prints working directory" "/src" "$OUT"

# 3. cd changes the SHELL's own directory
#    This is the decisive built-in test. If cd were forked and exec'd, the
#    child would chdir and exit, and the following pwd would print the
#    original directory. Persistence across commands proves it ran in-process.
echo "--- 3. cd Persists in the Parent Process ---"
OUT=$(printf "cd /tmp\npwd\nexit\n" | ./bin/shellforge)
assert_contains "cd /tmp then pwd shows /tmp" "/tmp" "$OUT"
assert_not_contains "cd did not silently stay in /src" "/src" "$(printf 'cd /tmp\npwd\nexit\n' | ./bin/shellforge | grep -A1 myshell | tail -2)"

OUT=$(printf "cd /tmp\ncd /etc\npwd\nexit\n" | ./bin/shellforge)
assert_contains "successive cd calls accumulate" "/etc" "$OUT"

# 4. cd with no argument follows $HOME
echo "--- 4. cd Defaults to \$HOME ---"
OUT=$(printf "cd /tmp\ncd\npwd\nexit\n" | ./bin/shellforge)
assert_contains "bare cd returns to \$HOME ($HOME)" "$HOME" "$OUT"

# 5. cd error handling
echo "--- 5. cd Error Handling ---"
OUT=$(printf "cd /no/such/directory/xyz\necho survived\nexit\n" | ./bin/shellforge 2>&1)
assert_contains "cd to missing directory reports error" "ShellForge: cd: /no/such/directory/xyz: No such file or directory" "$OUT"
assert_contains "shell survives a failed cd" "survived" "$OUT"

OUT=$(printf "cd /tmp /etc\nexit\n" | ./bin/shellforge 2>&1)
assert_contains "cd rejects too many arguments" "ShellForge: cd: too many arguments" "$OUT"

# 6. PWD environment variable tracks cd
echo "--- 6. PWD Environment Variable Updated by cd ---"
OUT=$(printf "cd /tmp\nenv\nexit\n" | ./bin/shellforge)
assert_contains "env reports PWD=/tmp after cd" "PWD=/tmp" "$OUT"

# 7. env built-in
echo "--- 7. env Built-in ---"
OUT=$(printf "env\nexit\n" | ./bin/shellforge)
assert_contains "env prints HOME" "HOME=" "$OUT"
assert_contains "env prints PATH" "PATH=" "$OUT"
assert_contains "env prints SHELL" "SHELL=" "$OUT"

# 8. env is NULL-safe for unset variables
#    getenv() returns NULL for an unset name and printf("%s", NULL) is
#    undefined behaviour, so an unset variable must render as a literal.
echo "--- 8. env NULL-safety for Unset Variables ---"
OUT=$(printf "env\nexit\n" | env -u USER ./bin/shellforge)
assert_contains "unset USER renders as (not set)" "USER=(not set)" "$OUT"

# 9. help built-in
echo "--- 9. help Built-in ---"
OUT=$(printf "help\nexit\n" | ./bin/shellforge)
assert_contains "help lists cd" "cd" "$OUT"
assert_contains "help lists pwd" "pwd" "$OUT"
assert_contains "help lists env" "env" "$OUT"
assert_contains "help lists clear" "clear" "$OUT"
assert_contains "help lists exit" "exit" "$OUT"

# 10. clear built-in emits the ANSI erase sequence
echo "--- 10. clear Built-in ---"
OUT=$(printf "clear\nexit\n" | ./bin/shellforge | cat -v)
assert_contains "clear emits ANSI erase-display" "^[[2J" "$OUT"

# 11. exit built-in routes through the parser
#     The pre-Week-5 shell matched the raw input line, so "exit" with trailing
#     whitespace did not terminate. Going through the token vector fixes that.
echo "--- 11. exit Built-in via Parser ---"
OUT=$(printf "exit\n" | ./bin/shellforge)
assert_contains "exit terminates cleanly" "Exiting ShellForge..." "$OUT"

OUT=$(printf "   exit   \n" | ./bin/shellforge)
assert_contains "exit with surrounding whitespace terminates" "Exiting ShellForge..." "$OUT"

OUT=$(printf "exit\necho should_not_run\n" | ./bin/shellforge)
assert_not_contains "nothing runs after exit" "should_not_run" "$OUT"

# 12. Built-ins do not shadow external programs
echo "--- 12. External Commands Still Execute ---"
OUT=$(printf "echo external_ok\nexit\n" | ./bin/shellforge)
assert_contains "external echo still works" "external_ok" "$OUT"

OUT=$(printf "nonexistent_builtin_xyz\necho after\nexit\n" | ./bin/shellforge 2>&1)
assert_contains "unknown command falls through to execvp" "ShellForge: nonexistent_builtin_xyz: No such file or directory" "$OUT"
assert_contains "shell survives unknown command" "after" "$OUT"

# 13. EOF termination
echo "--- 13. EOF Termination ---"
OUT=$(printf "" | ./bin/shellforge)
EXIT_CODE=$?
assert_exit_code "Empty input EOF exit code" 0 $EXIT_CODE

# 14. Sanitizers over the built-in paths
echo "--- 14. Memory & Sanitizer Verification ---"
make clean > /dev/null
make CFLAGS="-Wall -Wextra -g -Iinclude -fsanitize=address,undefined" > /dev/null
SAN_OUT=$(printf "pwd\ncd /tmp\npwd\ncd\nenv\nhelp\nclear\ncd /nope\necho san_ok\nexit\n" | ./bin/shellforge 2>&1)
assert_contains "ASan built-in run completed" "san_ok" "$SAN_OUT"
assert_contains "ASan clean exit" "Exiting ShellForge..." "$SAN_OUT"
assert_not_contains "No AddressSanitizer errors" "ERROR: AddressSanitizer" "$SAN_OUT"
assert_not_contains "No LeakSanitizer leaks" "ERROR: LeakSanitizer" "$SAN_OUT"
assert_not_contains "No UBSan runtime errors" "runtime error:" "$SAN_OUT"

echo "=========================================="
echo " Test Summary: $PASSED passed, $FAILED failed"
echo "=========================================="

if [ "$FAILED" -ne 0 ]; then
    exit 1
fi
