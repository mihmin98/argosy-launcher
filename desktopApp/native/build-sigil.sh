#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
out="${1:?usage: build-sigil.sh <output dir>}"

case "$(uname -s)" in
  Linux) os=linux; lib=libsigil-jni.so ;;
  Darwin) os=macos; lib=libsigil-jni.dylib ;;
  *) echo "build-sigil.sh: unsupported OS $(uname -s); Windows uses build-sigil.ps1" >&2; exit 1 ;;
esac
case "$(uname -m)" in
  x86_64 | amd64) arch=x64 ;;
  arm64 | aarch64) arch=arm64 ;;
  *) echo "build-sigil.sh: unsupported architecture $(uname -m)" >&2; exit 1 ;;
esac

build="$out/sigil-$os-$arch"
cmake -S "$root/sigil" -B "$build" \
  -DSIGIL_BUILD_JNI_HOST=ON -DSIGIL_BUILD_CLI=OFF -DSIGIL_BUILD_TESTS=OFF \
  -DCMAKE_BUILD_TYPE=Release
cmake --build "$build" --target sigil_jni_host --config Release
mkdir -p "$out/natives/$os-$arch"
cp "$build/bindings/$lib" "$out/natives/$os-$arch/$lib"
