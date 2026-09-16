#!/bin/sh
# Build and run the TextHack test suite.
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

java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" tests.StringAlgorithmTests
