#!/usr/bin/env bash
set -euo pipefail

scope_file="app/src/main/resources/META-INF/xposed/scope.list"
module_main="app/src/main/kotlin/io/github/mangi/eta/ModuleMain.kt"
mapfile -t actual < <(sed '/^[[:space:]]*$/d' "$scope_file")
expected=(
  system
  com.android.systemui
  com.miui.home
  com.mi.android.globallauncher
)
if [ "${#actual[@]}" -ne "${#expected[@]}" ]; then
  echo "ERROR: LSPosed scope count changed; expected system/SystemUI/launchers only" >&2
  exit 1
fi
for i in "${!expected[@]}"; do
  if [ "${actual[$i]}" != "${expected[$i]}" ]; then
    echo "ERROR: unexpected target scope '${actual[$i]}'" >&2
    exit 1
  fi
done
if grep -E '^[[:space:]]*import io.github.mangi.eta.hook.(google|breeno|xiaoai|aimemory|colordirect)' "$module_main"; then
  echo "ERROR: target-app LSPosed hook was re-enabled" >&2
  exit 1
fi
if ! grep -q 'SystemUiHooks.install' "$module_main" || ! grep -q 'SystemServerHooks.install' "$module_main"; then
  echo "ERROR: expected system hook baseline was lost" >&2
  exit 1
fi
echo "OK: ETA injects only system, SystemUI and supported launcher processes"
