#!/usr/bin/env bash
# Build an isolated x86_64 Android app containing only synthetic runtime tests.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
curl -fsSL --retry 3 https://github.com/ithtelab/workbuddy-manager/releases/download/v2.9.0/workbuddy-manager-v2.9.0.tar.gz -o "$WORK/upstream.tgz"
printf '%s  %s\n' 7f1a37b9bd9987c6210a41e58360e37658e71c4c305fbe85c64b25305bb7133e "$WORK/upstream.tgz" | sha256sum -c -
tar -xzf "$WORK/upstream.tgz" -C "$WORK"
mapfile -t modules < <(find "$WORK" -path '*/upstream/go.mod')
(( ${#modules[@]} == 1 ))
OUT="$ROOT/app/src/runtimeTest/jniLibs/x86_64"
mkdir -p "$OUT"
(cd "$(dirname "${modules[0]}")" && bash "$ROOT/scripts/build_android_go.sh" x86_64 "$OUT/libwbcore.so" ./cmd/server)
bash "$ROOT/scripts/build_android_go.sh" x86_64 "$OUT/libwbprobe.so" "$ROOT/scripts/runtime-probe/main.go"
# Android/amd64 requires external linking. Force the same pure-Go DNS resolver
# used by the former CGO-disabled ARM64 core while retaining x86_64 linkage.
bash "$ROOT/scripts/build_android_go.sh" x86_64 "$OUT/libwbprobe_legacy.so" \
  "$ROOT/scripts/runtime-probe/main.go" -tags netgo
cd "$ROOT"
./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx5g \
  -Pkotlin.compiler.execution.strategy=in-process -Peta.nativeAbi=x86_64 -Peta.runtimeTests=true \
  :app:assembleRuntimeTest :app:assembleRuntimeTestAndroidTest
