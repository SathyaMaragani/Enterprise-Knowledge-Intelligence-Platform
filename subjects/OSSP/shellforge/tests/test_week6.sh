#!/bin/sh
# Week 6 -- signals and process control. Needs Linux /proc, setsid and ps.

echo "=========================================="
echo " Running ShellForge Week 6 Test Suite     "
echo " (Signals and Process Control)            "
echo "=========================================="

PASSED=0
FAILED=0
SF=./bin/shellforge
TMP=$(mktemp -d)

pass() { echo "[PASS] $1"; PASSED=$((PASSED + 1)); }
fail() { echo "[FAIL] $1"; echo "  $2"; FAILED=$((FAILED + 1)); }

assert_contains() {
    if echo "$3" | grep -F -- "$2" > /dev/null; then pass "$1"; else fail "$1" "Expected to contain: $2 | Actual: $3"; fi
}

assert_not_contains() {
    if echo "$3" | grep -F -- "$2" > /dev/null; then fail "$1" "Expected NOT to contain: $2"; else pass "$1"; fi
}

# Runs the shell in its own session (as a terminal would give it its own
# process group), waits, then sends $1 to the whole group, as the terminal
# does for Ctrl+C or Ctrl+Z. Prints the seconds the session took.
signal_group() {
    SIGNAL=$1
    INPUT=$2
    OUT_FILE=$3
    START=$(date +%s)
    ( printf "%b" "$INPUT"; sleep 2; printf "echo after_signal\nexit\n" ) | setsid $SF > "$OUT_FILE" 2>&1 &
    sleep 0.7
    PGID=$(ps -o pgid= -C shellforge | head -1 | tr -d ' ')
    kill -"$SIGNAL" -"$PGID"
    wait
    echo $(( $(date +%s) - START ))
}

echo "--- 1. Compilation Test ---"
make clean > /dev/null
if make > /dev/null 2>&1; then pass "Build under -Wall -Wextra with signals.c"; else fail "Build" "make failed"; fi

echo "--- 2. Ctrl+C at an idle prompt ---"
( sleep 1; printf "echo alive_after_sigint\nexit\n" ) | $SF > "$TMP/idle" 2>&1 &
PID=$!
sleep 0.4
kill -INT "$PID"
wait "$PID"
RC=$?
OUT=$(cat "$TMP/idle")
assert_contains "SIGINT handler message" "ShellForge: type 'exit' to quit." "$OUT"
assert_contains "Shell keeps running after SIGINT" "alive_after_sigint" "$OUT"
assert_contains "Clean exit afterwards" "Exiting ShellForge..." "$OUT"
[ "$RC" -eq 0 ] && pass "Exit code 0, not killed by SIGINT" || fail "Exit code" "got $RC"

echo "--- 3. Ctrl+C while a command runs ---"
ELAPSED=$(signal_group INT "sleep 5\n" "$TMP/fg")
OUT=$(cat "$TMP/fg")
[ "$ELAPSED" -lt 5 ] && pass "Foreground sleep 5 terminated by SIGINT (${ELAPSED}s)" || fail "SIGINT to child" "took ${ELAPSED}s"
assert_contains "Shell survives and runs the next command" "after_signal" "$OUT"
assert_not_contains "No idle-prompt message while a child runs" "type 'exit' to quit" "$OUT"

echo "--- 4. Ctrl+C during a pipeline ---"
ELAPSED=$(signal_group INT "sleep 5 | cat\n" "$TMP/pipe")
OUT=$(cat "$TMP/pipe")
[ "$ELAPSED" -lt 5 ] && pass "Both pipeline children terminated (${ELAPSED}s)" || fail "SIGINT to pipeline" "took ${ELAPSED}s"
assert_contains "Shell survives a pipeline interrupt" "after_signal" "$OUT"

echo "--- 5. Ctrl+Z is ignored (no job control yet) ---"
OUT_FILE="$TMP/tstp"
( printf "sleep 1\n"; sleep 2; printf "echo after_signal\nexit\n" ) | setsid $SF > "$OUT_FILE" 2>&1 &
sleep 0.4
PGID=$(ps -o pgid= -C shellforge | head -1 | tr -d ' ')
kill -TSTP -"$PGID"
STATES=$(ps -o stat= -s "$PGID" | tr -d ' \n')
wait
assert_not_contains "Neither shell nor child stopped by SIGTSTP" "T" "$STATES"
assert_contains "Session completes after Ctrl+Z" "after_signal" "$(cat "$OUT_FILE")"

echo "--- 6. Children start with default signal handling ---"
# Started in the background by sh, the shell inherits SIGINT ignored; the
# child must still get the default action back, and SIGCHLD unblocked.
( printf "grep Sig /proc/self/status\nexit\n" | $SF > "$TMP/child" 2>&1 ) &
wait
OUT=$(cat "$TMP/child")
IGN=$(echo "$OUT" | awk '/SigIgn/ {print $2}')
BLK=$(echo "$OUT" | awk '/SigBlk/ {print $2}')
[ $(( 0x$IGN & 2 )) -eq 0 ] && pass "SIGINT not ignored in the child (SigIgn $IGN)" || fail "Child SIGINT" "SigIgn $IGN"
[ $(( 0x$BLK & 0x10000 )) -eq 0 ] && pass "SIGCHLD not blocked in the child (SigBlk $BLK)" || fail "Child SIGCHLD mask" "SigBlk $BLK"

echo "--- 7. SIGCHLD reaps zombies ---"
gcc -Wall -Wextra -Iinclude tests/sigchld_check.c src/signals.c -o "$TMP/sigchld_check"
OUT=$("$TMP/sigchld_check")
RC=$?
echo "$OUT" | sed 's/^/  /'
assert_contains "Unwaited child is a zombie without a handler" "(zombie)" "$OUT"
assert_contains "Handler reaps the child" "reaped (no /proc entry)" "$OUT"
[ "$RC" -eq 0 ] && pass "Zombie check exit code 0" || fail "Zombie check" "exit $RC"

echo "--- 8. Exit status is still collected by waitpid ---"
# The reaper must not steal the foreground child's status. A non-existent
# command exits 127, which the shell only knows through its own waitpid().
OUT=$(printf "true\nfalse\nnonexistent_cmd_xyz\necho status_ok\nexit\n" | $SF 2>&1)
assert_contains "No waitpid errors (ECHILD) after commands" "status_ok" "$OUT"
assert_not_contains "waitpid never fails" "waitpid error" "$OUT"

echo "--- 9. Sanitizers ---"
make clean > /dev/null
make CFLAGS="-Wall -Wextra -g -Iinclude -fsanitize=address,undefined" > /dev/null
( sleep 1; printf "echo san_alive\nsleep 0 | cat\nexit\n" ) | $SF > "$TMP/san" 2>&1 &
PID=$!
sleep 0.4
kill -INT "$PID"
wait "$PID"
OUT=$(cat "$TMP/san")
assert_contains "ASan build survives SIGINT" "san_alive" "$OUT"
assert_not_contains "No sanitizer report" "Sanitizer" "$OUT"

rm -rf "$TMP"
echo "=========================================="
echo " Test Summary: $PASSED passed, $FAILED failed"
echo "=========================================="

[ "$FAILED" -eq 0 ]
