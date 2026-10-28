#!/usr/bin/env bash
# ETA WorkBuddy MVP reproducible *unsigned debug* APK builder.
# No accounts.zip or tokens are needed and must never be supplied to CI.
set -euo pipefail

RELEASE_URL="https://github.com/ithtelab/workbuddy-manager/releases/download/v2.9.0/workbuddy-manager-v2.9.0.tar.gz"
RELEASE_SHA256="7f1a37b9bd9987c6210a41e58360e37658e71c4c305fbe85c64b25305bb7133e"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
for command in curl sha256sum tar go java javac python3 file readelf; do
  command -v "$command" >/dev/null || { echo "MISSING TOOL: $command" >&2; exit 2; }
done
if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
  echo "ANDROID_HOME / ANDROID_SDK_ROOT is missing" >&2
  exit 2
fi
mkdir -p "$ROOT/build-mvp"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

curl --fail --location --retry 3 --silent --show-error "$RELEASE_URL" \
  -o "$WORK/upstream.tgz"
printf '%s  %s\n' "$RELEASE_SHA256" "$WORK/upstream.tgz" | sha256sum --check --status || {
  echo "PINNED_UPSTREAM_SHA_MISMATCH — build aborted" >&2
  exit 3
}
mkdir -p "$WORK/src"
tar --no-same-owner -xzf "$WORK/upstream.tgz" -C "$WORK/src"
mapfile -t modfiles < <(find "$WORK/src" -type f -path '*/upstream/go.mod')
if (( ${#modfiles[@]} != 1 )); then
  echo "Expected one official upstream/go.mod file" >&2
  exit 3
fi
UPSTREAM="$(dirname "${modfiles[0]}")"
(cd "$UPSTREAM" && go mod verify && go test ./internal/... ./cmd/server/... &&
  GOOS=android GOARCH=arm64 CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' \
    -o "$WORK/libwbcore.so" ./cmd/server)
readelf -h "$WORK/libwbcore.so" | grep -Eq 'Machine:.*AArch64'
readelf -h "$WORK/libwbcore.so" | grep -Eq 'Type:.*DYN'
readelf -l "$WORK/libwbcore.so" | grep -q '/system/bin/linker64'

mkdir -p app/src/main/jniLibs/arm64-v8a
cp "$WORK/libwbcore.so" app/src/main/jniLibs/arm64-v8a/libwbcore.so
./gradlew --no-daemon --no-configuration-cache \
  :ai-runtime-api:jar :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
APK="$(find app/build/outputs/apk/debug -maxdepth 1 -name '*.apk' -print -quit)"
test -n "$APK"
python3 - "$APK" <<'PY'
import sys, zipfile, struct
with zipfile.ZipFile(sys.argv[1]) as apk:
    data=apk.read("lib/arm64-v8a/libwbcore.so")
    assert data[:4] == bytes([0x7f, 0x45, 0x4c, 0x46]) and data[4] == 2
    assert struct.unpack_from("<H", data, 18)[0] == 183
    assert b"/system/bin/linker64" in data
    print("PASS: APK includes executable Android ARM64 Go core")
PY
cp "$APK" "$ROOT/build-mvp/"
cp ai-runtime-api/build/libs/*.jar "$ROOT/build-mvp/"
(cd "$ROOT/build-mvp" && sha256sum ./*.apk ./*.jar > SHA256SUMS)
echo "BUILD COMPLETE: $ROOT/build-mvp (Android device validation remains required)"
