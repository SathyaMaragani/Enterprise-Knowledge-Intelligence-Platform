#!/bin/sh
# Week 10 -- threads and concurrency with POSIX threads.

echo "=========================================="
echo " Running ShellForge Week 10 Test Suite    "
echo " (Threads and Concurrency)                "
echo "=========================================="

PASSED=0
FAILED=0
SKIPPED=0
SF=$(pwd)/bin/shellforge
TMP=$(mktemp -d)

pass() { echo "[PASS] $1"; PASSED=$((PASSED + 1)); }
fail() { echo "[FAIL] $1"; echo "  $2"; FAILED=$((FAILED + 1)); }
skip() { echo "[SKIP] $1"; SKIPPED=$((SKIPPED + 1)); }

assert_contains() {
    if echo "$3" | grep -F -- "$2" > /dev/null; then pass "$1"; else fail "$1" "Expected to contain: $2 | Actual: $3"; fi
}

assert_not_contains() {
    if echo "$3" | grep -F -- "$2" > /dev/null; then fail "$1" "Expected NOT to contain: $2"; else pass "$1"; fi
}

assert_equals() {
    if [ "$2" = "$3" ]; then pass "$1"; else fail "$1" "Expected: [$2] | Actual: [$3]"; fi
}

now_ms() { echo $(($(date +%s%N) / 1000000)); }

# Threads in the newest shellforge process, read from /proc while it idles.
thread_count() {
    pid=$(ps -o pid= -C shellforge | tail -1 | tr -d ' ')
    ls "/proc/$pid/task" | wc -l
}

echo "--- 1. Build ---"
make clean > /dev/null
if make > /dev/null 2>&1; then pass "Build under -Wall -Wextra with thread.c"; else fail "Build" "make failed"; fi
if grep -q -- "-pthread" Makefile; then pass "Makefile links with -pthread"; else fail "-pthread" "missing from Makefile"; fi

echo "--- 2. Monitor thread ---"
OUT=$(printf "sleep 2.5\nexit\n" | SHELLFORGE_MONITOR=1 "$SF" 2>&1)
BEATS=$(echo "$OUT" | grep -c "\[Monitor\] ShellForge Running...")
[ "$BEATS" -ge 2 ] && pass "Heartbeat every second during a 2.5 s command ($BEATS)" || fail "Heartbeat" "$BEATS heartbeats: $OUT"
assert_contains "Shell still answers after the heartbeats" "Exiting ShellForge..." "$OUT"
OUT=$(printf "sleep 1.5\nexit\n" | SHELLFORGE_MONITOR=0 "$SF" 2>&1)
assert_not_contains "SHELLFORGE_MONITOR=0 turns the monitor off" "[Monitor]" "$OUT"

( printf "sleep 1\n"; sleep 1.5; printf "exit\n" ) | SHELLFORGE_MONITOR=30 "$SF" > /dev/null 2>&1 &
sleep 0.5
assert_equals "Two threads in /proc/PID/task: main and monitor" "2" "$(thread_count)"
wait
( printf "sleep 1\n"; sleep 1.5; printf "exit\n" ) | SHELLFORGE_MONITOR=0 "$SF" > /dev/null 2>&1 &
sleep 0.5
assert_equals "One thread with the monitor off" "1" "$(thread_count)"
wait

# The monitor waits on a condition variable, so exit wakes and joins it at once
# instead of waiting out the 30 s interval.
START=$(now_ms)
printf "exit\n" | SHELLFORGE_MONITOR=30 "$SF" > /dev/null 2>&1
ELAPSED=$(($(now_ms) - START))
[ "$ELAPSED" -lt 2000 ] && pass "exit joins the monitor at once (${ELAPSED} ms)" || fail "Prompt exit" "took ${ELAPSED} ms"

echo "--- 3. Signals stay on the main thread ---"
# The monitor is created with every signal blocked. Otherwise SIGCHLD, blocked
# only in the main thread while it waits, is delivered to the monitor; the
# reaper runs there and steals the exit status (about 9 in 10 commands).
i=0
while [ $i -lt 100 ]; do echo true; i=$((i + 1)); done > "$TMP/many"
echo exit >> "$TMP/many"
OUT=$(SHELLFORGE_MONITOR=5 "$SF" < "$TMP/many" 2>&1)
assert_not_contains "100 commands, no exit status stolen by the reaper" "waitpid error" "$OUT"
OUT=$(printf "sleep 3 | cat\necho after_pipe\nexit\n" | SHELLFORGE_MONITOR=1 "$SF" 2>&1)
assert_not_contains "Pipeline waits survive the monitor" "waitpid error" "$OUT"
assert_contains "Pipeline completes" "after_pipe" "$OUT"

echo "--- 4. Race condition and mutex (demo-threads) ---"
OUT=$(printf "demo-threads 4 100000\nexit\n" | "$SF" 2>&1)
assert_contains "Expected total reported" "4 threads x 100000 increments, expected total 400000" "$OUT"
assert_contains "Mutex: no update lost" "With a mutex:    counter = 400000 (0 lost)" "$OUT"
assert_contains "Workers joined" "all 4 workers joined with pthread_join()" "$OUT"
RACY=$(echo "$OUT" | sed -n 's/.*Without a mutex: counter = \([0-9]*\).*/\1/p')
if [ -n "$RACY" ] && [ "$RACY" -le 400000 ]; then
    pass "Without a mutex: counter = $RACY of 400000"
else
    fail "Unprotected total" "got [$RACY]"
fi
OUT=$(printf "demo-threads 1 50000\nexit\n" | "$SF" 2>&1)
assert_contains "One thread cannot race" "Without a mutex: counter = 50000 (0 updates lost" "$OUT"
OUT=$(printf "demo-threads\nexit\n" | "$SF" 2>&1)
assert_contains "Defaults: 4 threads x 100000" "4 threads x 100000 increments" "$OUT"
OUT=$(printf "demo-threads 0\ndemo-threads 17\ndemo-threads 4 abc\ndemo-threads 4 2000000\ndemo-threads 4 10 9\necho still_running\nexit\n" | "$SF" 2>&1)
USAGE=$(echo "$OUT" | grep -c "usage: demo-threads")
assert_equals "Out-of-range arguments rejected (5 cases)" "5" "$USAGE"
assert_contains "Shell continues after bad arguments" "still_running" "$OUT"
( cd "$TMP" && printf "demo-threads 2 1000 > threads.txt\nexit\n" | "$SF" > /dev/null 2>&1 )
assert_contains "A built-in, so its output redirects" "expected total 2000" "$(cat "$TMP/threads.txt")"
OUT=$(printf "help\nexit\n" | "$SF" 2>&1)
assert_contains "help lists demo-threads" "demo-threads [threads] [increments]" "$OUT"

echo "--- 5. Transcript mode (--echo) ---"
OUT=$(printf "pwd\nnonexistent_cmd_xyz\nexit\n" | "$SF" --echo 2>&1)
assert_contains "Each line echoed after the prompt" "myshell> pwd" "$OUT"
assert_contains "Echo precedes the command's error" "myshell> nonexistent_cmd_xyz
ShellForge: nonexistent_cmd_xyz: No such file or directory" "$OUT"

echo "--- 6. ThreadSanitizer: the race is real, the mutex removes it ---"
gcc -g -fsanitize=thread -Iinclude src/*.c -o "$TMP/sf_tsan" -pthread 2> /dev/null
NORAND=""
command -v setarch > /dev/null && NORAND="setarch $(uname -m) -R"
OUT=$(printf "demo-threads 2 20000\nsleep 1.2\nexit\n" | SHELLFORGE_MONITOR=1 $NORAND "$TMP/sf_tsan" 2>&1)
if echo "$OUT" | grep -q "Welcome to ShellForge"; then
    assert_contains "TSan reports the unprotected counter++" "ThreadSanitizer: data race" "$OUT"
    if echo "$OUT" | grep "SUMMARY: ThreadSanitizer" | grep -v "unsafe_worker" > /dev/null; then
        fail "Only unsafe_worker races" "$(echo "$OUT" | grep "SUMMARY: ThreadSanitizer")"
    else
        pass "Every race is in unsafe_worker: the mutex worker and the monitor are clean"
    fi
else
    skip "ThreadSanitizer cannot run on this kernel (2 checks)"
fi

echo "--- 7. Sanitizers and Valgrind ---"
SESSION="demo-threads 4 20000\nsleep 1.2\necho done | cat\nexit\n"
if make asan > /dev/null 2>&1; then pass "make asan builds"; else fail "make asan" "build failed"; fi
OUT=$(printf "$SESSION" | SHELLFORGE_MONITOR=1 "$SF" 2>&1)
assert_contains "ASan: session with threads completes" "Exiting ShellForge..." "$OUT"
assert_not_contains "ASan: no AddressSanitizer report" "AddressSanitizer" "$OUT"
assert_not_contains "ASan: no LeakSanitizer report" "LeakSanitizer" "$OUT"
assert_not_contains "ASan: no UndefinedBehaviorSanitizer report" "runtime error" "$OUT"
make clean > /dev/null
make > /dev/null 2>&1
if command -v valgrind > /dev/null; then
    OUT=$(printf "$SESSION" | SHELLFORGE_MONITOR=1 valgrind --leak-check=full --show-leak-kinds=all \
          --errors-for-leak-kinds=all --child-silent-after-fork=yes "$SF" 2>&1 > /dev/null)
    assert_contains "Valgrind: joined threads leave no heap blocks" "All heap blocks were freed -- no leaks are possible" "$OUT"
    assert_contains "Valgrind: 0 errors" "ERROR SUMMARY: 0 errors" "$OUT"
else
    skip "Valgrind not installed (2 checks)"
fi

rm -rf "$TMP"
echo "=========================================="
echo " Test Summary: $PASSED passed, $FAILED failed, $SKIPPED skipped"
echo "=========================================="

[ "$FAILED" -eq 0 ]
