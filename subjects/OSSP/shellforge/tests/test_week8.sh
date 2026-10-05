#!/bin/sh
# Week 8 -- memory management and debugging: Valgrind, GDB, AddressSanitizer.
# Valgrind and GDB are not in the gcc:13 image; see tests/TESTS.md for the
# image that adds them. Without them, those sections are skipped, not failed.

echo "=========================================="
echo " Running ShellForge Week 8 Test Suite     "
echo " (Valgrind, GDB and AddressSanitizer)     "
echo "=========================================="

PASSED=0
FAILED=0
SKIPPED=0
SF=./bin/shellforge
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

# One session through every code path that allocates or opens something:
# input growth, token growth, built-ins, fork/exec, failed exec, pipes,
# all four redirections, failed open(), syntax errors and the IPC demo.
LONG=$(awk 'BEGIN { for (i = 0; i < 1500; i++) printf "x"; print "" }')
MANY=$(awk 'BEGIN { for (i = 1; i <= 150; i++) printf "a%d ", i; print "" }')
cat > "$TMP/session" <<EOF
echo $LONG
echo $MANY
pwd
cd /tmp
env
help
ls /
nonexistent_cmd_xyz
echo pipe | cat
seq 1 3 | wc -l
echo out > $TMP/f.txt
echo more >> $TMP/f.txt
sort < $TMP/f.txt
ls /nope 2> $TMP/e.txt
pwd > $TMP/p.txt
sort < $TMP/f.txt | cat > $TMP/c.txt
cat < $TMP/missing
echo x >
| ls
demo-ipc
exit
EOF

echo "--- 1. Debug build ---"
make clean > /dev/null
if make > /dev/null 2>&1; then pass "Build with -g debugging symbols"; else fail "Build" "make failed"; fi
if grep -q -- "-g" Makefile; then pass "Makefile passes -g"; else fail "-g flag" "missing from CFLAGS"; fi

echo "--- 2. Valgrind ---"
if command -v valgrind > /dev/null; then
    VG="valgrind --leak-check=full --show-leak-kinds=all --errors-for-leak-kinds=all --error-exitcode=99 --child-silent-after-fork=yes"

    $VG $SF < "$TMP/session" > /dev/null 2> "$TMP/vg"
    RC=$?
    OUT=$(cat "$TMP/vg")
    echo "$OUT" | grep -E "in use at exit|total heap usage|ERROR SUMMARY" | sed 's/^==[0-9]*== /  /'
    assert_contains "Full session: all heap blocks freed" "All heap blocks were freed -- no leaks are possible" "$OUT"
    assert_contains "Full session: 0 errors" "ERROR SUMMARY: 0 errors" "$OUT"
    [ "$RC" -eq 0 ] && pass "Full session: valgrind exit code 0" || fail "Valgrind exit code" "got $RC"

    # Ending with Ctrl+D instead of exit takes a different path out of main().
    OUT=$(printf "echo eof_path | cat\nls > /dev/null\n" | $VG $SF 2>&1 > /dev/null)
    assert_contains "EOF exit: no leaks" "All heap blocks were freed -- no leaks are possible" "$OUT"
    assert_contains "EOF exit: 0 errors" "ERROR SUMMARY: 0 errors" "$OUT"

    # Control: the handbook's intentional leak must be reported, or a clean
    # result above would prove nothing.
    printf '#include <stdlib.h>\nint main(void) { char *temp = malloc(200); temp[0] = 1; return 0; }\n' > "$TMP/leak.c"
    gcc -g -O0 "$TMP/leak.c" -o "$TMP/leak"
    OUT=$($VG "$TMP/leak" 2>&1)
    assert_contains "Control: Valgrind reports the 200-byte leak" "definitely lost: 200 bytes in 1 blocks" "$OUT"
else
    skip "Valgrind not installed (6 checks)"
fi

echo "--- 3. GDB ---"
if command -v gdb > /dev/null; then
    printf "echo gdb_probe\nexit\n" > "$TMP/gdb_in"
    OUT=$(gdb -batch -nx \
        -ex "set disable-randomization off" \
        -ex "break parse_line" \
        -ex "run < $TMP/gdb_in > /dev/null" \
        -ex "backtrace" \
        -ex "print line" \
        -ex "finish" \
        -ex "next" \
        -ex "print tokens[0]" \
        -ex "print tokens[1]" \
        -ex "print tokens[2]" \
        -ex "kill" \
        $SF 2>&1)
    echo "$OUT" | grep -E "^Breakpoint 1,|^#[01] |^\\\$[0-9]+ = " | sed 's/^/  /'
    assert_contains "Breakpoint hit in parse_line" ", parse_line (line=" "$OUT"
    assert_contains "Backtrace shows main as the caller" "in main" "$OUT"
    assert_contains "print line shows the input" "\"echo gdb_probe\"" "$OUT"
    assert_contains "tokens[1] after the call" "\"gdb_probe\"" "$OUT"
    if echo "$OUT" | grep -E '^\$[0-9]+ = 0x0$' > /dev/null; then
        pass "tokens[2] is NULL: argv is NULL-terminated"
    else
        fail "NULL terminator" "no '= 0x0' for tokens[2]"
    fi
else
    skip "GDB not installed (5 checks)"
fi

echo "--- 4. AddressSanitizer (make asan) ---"
if make asan > /dev/null 2>&1; then pass "make asan builds"; else fail "make asan" "build failed"; fi
OUT=$($SF < "$TMP/session" 2>&1)
assert_contains "ASan: full session completes" "Exiting ShellForge..." "$OUT"
assert_not_contains "ASan: no AddressSanitizer report" "AddressSanitizer" "$OUT"
assert_not_contains "ASan: no LeakSanitizer report" "LeakSanitizer" "$OUT"
assert_not_contains "ASan: no UndefinedBehaviorSanitizer report" "runtime error" "$OUT"

# Control: ASan must catch the handbook's heap-buffer-overflow.
printf '#include <stdlib.h>\nint main(void) { char *b = malloc(8); b[8] = 1; free(b); return 0; }\n' > "$TMP/overflow.c"
gcc -g -fsanitize=address "$TMP/overflow.c" -o "$TMP/overflow"
OUT=$("$TMP/overflow" 2>&1)
assert_contains "Control: ASan reports heap-buffer-overflow" "heap-buffer-overflow" "$OUT"
assert_contains "Control: ASan names the source line" "overflow.c:2" "$OUT"

echo "--- 5. Memory-management checklist ---"
make clean > /dev/null
make > /dev/null 2>&1
# Every child process is reaped: while the shell idles after running
# commands, it must have no zombie children.
( printf "ls > /dev/null\necho a | cat > /dev/null\nnonexistent_cmd_xyz\n"; sleep 1.5; printf "exit\n" ) | $SF > /dev/null 2>&1 &
sleep 0.8
SHELL_PID=$(ps -o pid= -C shellforge | head -1 | tr -d ' ')
ZOMBIES=$(ps -o stat= --ppid "$SHELL_PID" | grep -c Z)
wait
[ "$ZOMBIES" -eq 0 ] && pass "No zombie children while the shell idles" || fail "Zombies" "$ZOMBIES zombie children"
# Every allocation is checked: each malloc/realloc result is tested before use.
ALLOCS=$(grep -c -E "= (malloc|realloc)\(" src/*.c)
CHECKED=$(grep -c -E "if \(!(tokens|buffer|temp)\)" src/*.c | awk -F: '{s += $2} END {print s}')
ALLOCS=$(echo "$ALLOCS" | awk -F: '{s += $2} END {print s}')
[ "$ALLOCS" -eq "$CHECKED" ] && pass "All $ALLOCS malloc/realloc results checked" || fail "Unchecked allocations" "$ALLOCS allocations, $CHECKED checks"

rm -rf "$TMP"
echo "=========================================="
echo " Test Summary: $PASSED passed, $FAILED failed, $SKIPPED skipped"
echo "=========================================="

[ "$FAILED" -eq 0 ]
