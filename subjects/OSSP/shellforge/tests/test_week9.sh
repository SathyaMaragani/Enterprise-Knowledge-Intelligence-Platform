#!/bin/sh
# Week 9 -- file descriptors and I/O redirection: > >> < 2>

echo "=========================================="
echo " Running ShellForge Week 9 Test Suite     "
echo " (File Descriptors and I/O Redirection)   "
echo "=========================================="

PASSED=0
FAILED=0
SF=$(pwd)/bin/shellforge
TMP=$(mktemp -d)

pass() { echo "[PASS] $1"; PASSED=$((PASSED + 1)); }
fail() { echo "[FAIL] $1"; echo "  $2"; FAILED=$((FAILED + 1)); }

assert_contains() {
    if echo "$3" | grep -F -- "$2" > /dev/null; then pass "$1"; else fail "$1" "Expected to contain: $2 | Actual: $3"; fi
}

assert_not_contains() {
    if echo "$3" | grep -F -- "$2" > /dev/null; then fail "$1" "Expected NOT to contain: $2"; else pass "$1"; fi
}

assert_equals() {
    if [ "$2" = "$3" ]; then pass "$1"; else fail "$1" "Expected: [$2] | Actual: [$3]"; fi
}

# Runs ShellForge in $TMP with the given input lines; prints its output.
run() {
    ( cd "$TMP" && printf "%b" "$1" | "$SF" 2>&1 )
}

echo "--- 1. Compilation Test ---"
make clean > /dev/null
if make > /dev/null 2>&1; then pass "Build under -Wall -Wextra with redirect.c"; else fail "Build" "make failed"; fi

echo "--- 2. Output redirection > ---"
OUT=$(run "echo first > out.txt\nexit\n")
assert_equals "> writes the file" "first" "$(cat "$TMP/out.txt")"
assert_not_contains "> keeps the output off the terminal" "first" "$OUT"
run "echo second > out.txt\nexit\n" > /dev/null
assert_equals "> truncates (O_TRUNC)" "second" "$(cat "$TMP/out.txt")"
assert_equals "Created with mode 0644 (umask 022)" "-rw-r--r--" "$(ls -l "$TMP/out.txt" | cut -c1-10)"

echo "--- 3. Append redirection >> ---"
run "echo one > log.txt\necho two >> log.txt\necho three >> log.txt\nexit\n" > /dev/null
assert_equals ">> appends (O_APPEND)" "one two three" "$(tr '\n' ' ' < "$TMP/log.txt" | sed 's/ $//')"
run "echo fresh >> new.txt\nexit\n" > /dev/null
assert_equals ">> creates a missing file" "fresh" "$(cat "$TMP/new.txt")"

echo "--- 4. Input redirection < ---"
printf "banana\napple\ncherry\n" > "$TMP/fruit.txt"
OUT=$(run "sort < fruit.txt\nexit\n")
assert_contains "< feeds the file to stdin" "apple
banana
cherry" "$OUT"
OUT=$(run "cat < nosuch.txt\necho still_running\nexit\n")
assert_contains "< missing file reported" "ShellForge: nosuch.txt: No such file or directory" "$OUT"
assert_contains "Shell continues after a failed open()" "still_running" "$OUT"

echo "--- 5. Error redirection 2> ---"
OUT=$(run "ls /nonexistent_dir 2> err.txt\nexit\n")
assert_contains "2> captures stderr" "nonexistent_dir" "$(cat "$TMP/err.txt")"
assert_not_contains "2> keeps stderr off the terminal" "No such file" "$OUT"
run "ls /nonexistent_dir > o2.txt 2> e2.txt\nexit\n" > /dev/null
assert_equals "stdout and stderr to separate files: stdout empty" "" "$(cat "$TMP/o2.txt")"
assert_contains "stdout and stderr to separate files: stderr" "nonexistent_dir" "$(cat "$TMP/e2.txt")"
run "nonexistent_cmd_xyz 2> notfound.txt\nexit\n" > /dev/null
assert_contains "Shell's exec error follows 2>" "nonexistent_cmd_xyz: No such file or directory" "$(cat "$TMP/notfound.txt")"

echo "--- 6. Combined < and > ---"
run "sort < fruit.txt > sorted.txt\nexit\n" > /dev/null
assert_equals "sort < in > out" "apple banana cherry" "$(tr '\n' ' ' < "$TMP/sorted.txt" | sed 's/ $//')"

echo "--- 7. Without spaces ---"
run "echo tight>t1.txt\necho more>>t1.txt\nsort<fruit.txt>t2.txt\nexit\n" > /dev/null
assert_equals "cmd>file and cmd>>file" "tight more" "$(tr '\n' ' ' < "$TMP/t1.txt" | sed 's/ $//')"
assert_equals "cmd<in>out" "apple" "$(head -1 "$TMP/t2.txt")"
run "echo a2>t3.txt\nexit\n" > /dev/null
assert_equals "a2>f is the word a2 to stdout, not 2>" "a2" "$(cat "$TMP/t3.txt")"

echo "--- 8. Redirection of built-ins ---"
run "pwd > pwd.txt\nhelp > help.txt\nexit\n" > /dev/null
assert_equals "pwd > file" "$TMP" "$(cat "$TMP/pwd.txt")"
assert_contains "help > file" "built-in commands" "$(cat "$TMP/help.txt")"
OUT=$(run "pwd > pwd2.txt\necho back_on_terminal\nexit\n")
assert_contains "Terminal restored after a redirected built-in" "back_on_terminal" "$OUT"

echo "--- 9. Redirection inside a pipeline ---"
run "sort < fruit.txt | head -1 > first.txt\nexit\n" > /dev/null
assert_equals "sort < in | head -1 > out" "apple" "$(cat "$TMP/first.txt")"

echo "--- 10. Syntax and permission errors ---"
OUT=$(run "echo hi >\nexit\n")
assert_contains "Missing file name" "syntax error: '>' needs a file name" "$OUT"
OUT=$(run "echo hi > > x\nexit\n")
assert_contains "Operator as a file name" "syntax error: '>' needs a file name" "$OUT"
OUT=$(run "echo hi > /nonexistent_dir/f.txt\nexit\n")
assert_contains "open() failure reported" "/nonexistent_dir/f.txt: No such file or directory" "$OUT"

echo "--- 11. No descriptor leaks ---"
# A command run by the shell should see exactly what the same command sees run
# directly: 0, 1, 2, the one ls opens to read the directory, and anything this
# script itself inherited (a CI runner passes some down). The shell's saved
# copies are close-on-exec, so they add nothing.
BASE=$(ls /proc/self/fd | tr '\n' ' ' | sed 's/ $//')
run "pwd > leak1.txt\necho x | cat > leak2.txt\nls /proc/self/fd > fds.txt\nexit\n" > /dev/null
assert_equals "Child sees only the fds it inherited ($BASE)" "$BASE" "$(tr '\n' ' ' < "$TMP/fds.txt" | sed 's/ $//')"

echo "--- 12. Sanitizers ---"
make clean > /dev/null
make CFLAGS="-Wall -Wextra -g -Iinclude -fsanitize=address,undefined" > /dev/null
OUT=$(run "echo s > s.txt\necho t >> s.txt\nsort < s.txt\nls /nope 2> e.txt\npwd > p.txt\nsort < s.txt | cat > c.txt\necho x >\ncat < missing\nexit\n")
assert_contains "ASan run completes" "Exiting ShellForge..." "$OUT"
assert_not_contains "No sanitizer report" "Sanitizer" "$OUT"

rm -rf "$TMP"
echo "=========================================="
echo " Test Summary: $PASSED passed, $FAILED failed"
echo "=========================================="

[ "$FAILED" -eq 0 ]
