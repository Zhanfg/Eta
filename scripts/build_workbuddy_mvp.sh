#!/usr/bin/env bash
# ETA WorkBuddy MVP reproducible debug-signed APK builder.
# No accounts.zip or tokens are needed and must never be supplied to CI.
set -euo pipefail

RELEASE_URL="https://github.com/ithtelab/workbuddy-manager/releases/download/v2.9.0/workbuddy-manager-v2.9.0.tar.gz"
RELEASE_SHA256="7f1a37b9bd9987c6210a41e58360e37658e71c4c305fbe85c64b25305bb7133e"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
for command in curl sha256sum tar go java javac python3 file readelf git; do
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
  bash "$ROOT/scripts/build_android_go.sh" arm64-v8a "$WORK/libwbcore.so" ./cmd/server)
readelf -h "$WORK/libwbcore.so" | grep -Eq 'Machine:.*AArch64'
readelf -h "$WORK/libwbcore.so" | grep -Eq 'Type:.*DYN'
readelf -l "$WORK/libwbcore.so" | grep -q '/system/bin/linker64'

readelf -d "$WORK/libwbcore.so" | grep -E 'NEEDED.*\[libc.so\]'

mkdir -p app/src/main/jniLibs/arm64-v8a
cp "$WORK/libwbcore.so" app/src/main/jniLibs/arm64-v8a/libwbcore.so
./gradlew --no-daemon --no-configuration-cache --max-workers=2 \
  "-Dorg.gradle.jvmargs=-Xmx5g" -Pkotlin.compiler.execution.strategy=in-process \
  :ai-runtime-api:jar :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
APK="$ROOT/app/build/outputs/apk/debug/Eta-v3.3.5-debug.apk"
test -s "$APK"
python3 scripts/verify_workbuddy_apk.py "$APK" "$WORK/libwbcore.so"
SDK="${ANDROID_HOME:-}"
if [[ -z "$SDK" ]]; then SDK="${ANDROID_SDK_ROOT:-}"; fi
ANALYZER="$SDK/cmdline-tools/latest/bin/apkanalyzer"
test -x "$ANALYZER"
[[ "$("$ANALYZER" manifest application-id "$APK")" == "io.github.mangi.eta" ]]
[[ "$("$ANALYZER" manifest version-name "$APK")" == "3.3.5-debug" ]]
SIGNER="$(find "$SDK/build-tools" -mindepth 2 -maxdepth 2 -name apksigner -type f | sort -V | tail -n 1)"
test -x "$SIGNER"
"$SIGNER" verify --verbose "$APK"
cp "$APK" "$ROOT/build-mvp/"
cp ai-runtime-api/build/libs/*.jar "$ROOT/build-mvp/"
(cd "$ROOT/build-mvp" && sha256sum ./*.apk ./*.jar > SHA256SUMS)
SOURCE_COMMIT="$(git rev-parse HEAD)"
python3 - "$SOURCE_COMMIT" "$ROOT/build-mvp/BUILD_PROVENANCE.json" <<'PY'
import json
import sys
record = {
    "source_commit": sys.argv[1],
    "application_id": "io.github.mangi.eta",
    "version_name": "3.3.5-debug",
    "native_abi": "arm64-v8a",
    "android_dns": "bionic-cgo",
    "tls_trust": "android-system-and-conscrypt-apex",
    "upstream_release": "workbuddy-manager-v2.9.0",
    "upstream_sha256": "7f1a37b9bd9987c6210a41e58360e37658e71c4c305fbe85c64b25305bb7133e",
    "device_validation": "pending",
    "real_account_tests": "not_run",
}
with open(sys.argv[2], "w", encoding="utf-8") as output:
    json.dump(record, output, indent=2)
    output.write("\n")
PY
echo "BUILD COMPLETE: $ROOT/build-mvp (Android device validation remains required)"
