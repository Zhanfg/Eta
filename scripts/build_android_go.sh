#!/usr/bin/env bash
# Android networking needs Bionic getaddrinfo; a CGO-disabled ELF is insufficient.
set -euo pipefail
abi=${1:?Usage: build_android_go.sh arm64-v8a|x86_64 output package [go build flags]}
output=${2:?Output file required}
package=${3:?Go package required}
shift 3
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
ndk=${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-$sdk/ndk/30.0.16248370}}
case "$abi" in
  arm64-v8a) arch=arm64; triple=aarch64-linux-android ;;
  x86_64) arch=amd64; triple=x86_64-linux-android ;;
  *) echo 'Unsupported Android ABI' >&2; exit 2 ;;
esac
case $(uname -s) in
  Linux) host=linux-x86_64 ;;
  Darwin) host=darwin-x86_64 ;;
  *) echo 'Unsupported build host' >&2; exit 2 ;;
esac
cc="$ndk/toolchains/llvm/prebuilt/$host/bin/${triple}33-clang"
test -x "$cc" || { echo 'Android NDK compiler is missing' >&2; exit 2; }
# -buildmode=pie and 16 KiB load alignment are needed for current Android devices.
CGO_ENABLED=1 GOOS=android GOARCH="$arch" CC="$cc" \
  go build -buildmode=pie -trimpath -ldflags="-s -w -extldflags=-Wl,-z,max-page-size=16384" \
  "$@" -o "$output" "$package"
