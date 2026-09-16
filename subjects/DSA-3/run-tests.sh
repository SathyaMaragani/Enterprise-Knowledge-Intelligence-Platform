#!/bin/sh
# Build and run the TextHack test suites.
#
# Deliberately plain javac: the subject builds its algorithms from scratch, and
# a JDK is the only prerequisite -- no Maven, no JUnit, no dependency download,
# nothing to resolve offline.
#
#   sh run-tests.sh
set -e

cd "$(dirname "$0")"

OUT=out
SOURCES=$(find texthack tests -name '*.java')

echo "=========================================="
echo " Building TextHack"
echo "=========================================="
rm -rf "$OUT"
javac -encoding UTF-8 -Xlint:all -d "$OUT" $SOURCES
echo "  compiled $(echo "$SOURCES" | wc -l | tr -d ' ') source files"
echo

# Every suite runs even if an earlier one fails, so a single regression does not
# hide the state of the rest. `set -e` is suspended around each run for that
# reason, and the exit codes are collected and reported at the end.
SUITES="tests.StringAlgorithmTests tests.SuffixAndMultiPatternTests"
STATUS=0

for suite in $SUITES; do
    set +e
    java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" "$suite"
    code=$?
    set -e
    if [ "$code" -ne 0 ]; then
        STATUS=1
        echo "  !! $suite FAILED (exit $code)"
    fi
    echo
done

if [ "$STATUS" -ne 0 ]; then
    echo "=========================================="
    echo " One or more suites FAILED"
    echo "=========================================="
    exit 1
fi

echo "=========================================="
echo " All suites passed"
echo "=========================================="
