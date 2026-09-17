#!/bin/sh
# Downloads the MiniLM embedding model the backend runs in-process, pinned to one
# revision and checked by SHA-256:
#
#   sh fetch-model.sh <dir>     ->  <dir>/onnx/model.onnx and <dir>/tokenizer.json
#
# The files are Xenova/all-MiniLM-L6-v2's ONNX export of
# sentence-transformers/all-MiniLM-L6-v2, byte-identical to the copy the tests and
# the demo corpus were built with. The Cloud Run image and CI both use this script,
# so a different model can never slip in unnoticed.
set -eu

DIR=${1:?usage: fetch-model.sh <target-dir>}
REPO=Xenova/all-MiniLM-L6-v2
REVISION=751bff37182d3f1213fa05d7196b954e230abad9
BASE="https://huggingface.co/$REPO/resolve/$REVISION"

fetch() { # <path in repo> <target file> <sha256>
  if [ -f "$2" ] && echo "$3  $2" | sha256sum -c - >/dev/null 2>&1; then
    echo "present: $2"
    return
  fi
  curl -fsSL --retry 3 -o "$2.part" "$BASE/$1"
  echo "$3  $2.part" | sha256sum -c - >/dev/null
  mv "$2.part" "$2"
  echo "fetched: $2"
}

mkdir -p "$DIR/onnx"
fetch onnx/model.onnx "$DIR/onnx/model.onnx" 759c3cd2b7fe7e93933ad23c4c9181b7396442a2ed746ec7c1d46192c469c46e
fetch tokenizer.json "$DIR/tokenizer.json" da0e79933b9ed51798a3ae27893d3c5fa4a201126cef75586296df9b4d2c62a0
