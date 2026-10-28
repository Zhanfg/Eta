#!/usr/bin/env bash
set -euo pipefail

# An intentionally frozen snapshot of ETA's existing scope.list before
# the workspace-module feature. Do not silently add OR remove entries here:
# adding target-app processes must be an explicit separate product decision.
scope_file="app/src/main/resources/META-INF/xposed/scope.list"
module_main="app/src/main/kotlin/io/github/mangi/eta/ModuleMain.kt"
mapfile -t actual < <(sed '/^[[:space:]]*$/d' "$scope_file")
expected=(
  system
  com.android.systemui
  com.google.android.googlequicksearchbox
  com.coloros.colordirectservice
  com.heytap.speechassist
  com.oplus.aimemory
  com.miui.voiceassist
  com.miui.home
  com.mi.android.globallauncher
)
if [ "${#actual[@]}" -ne "${#expected[@]}" ]; then
  echo "ERROR: the original ETA LSPosed scope was changed (expected ${#expected[@]} entries)" >&2
  exit 1
fi
for i in "${!expected[@]}"; do
  if [ "${actual[$i]}" != "${expected[$i]}" ]; then
    echo "ERROR: ETA scope changed at index $i: ${actual[$i]} != ${expected[$i]}" >&2
    exit 1
  fi
done

# Scope list alone is not enough: ensure the previous OEM integration handlers
# remain registered in ModuleMain, in addition to system and launcher handlers.
required_installers=(
  SystemServerHooks
  SystemUiHooks
  HyperOsLauncherHooks
  GoogleEligibilityHooks
  GoogleAppHooks
  ColorDirectHooks
  BreenoHooks
  ColorOsMemoryHooks
  HyperOsScreenSearchHooks
  XiaoAiHooks
)
for name in "${required_installers[@]}"; do
  if ! grep -Fq "${name}.install" "$module_main"; then
    echo "ERROR: original ETA hook registration removed: $name" >&2
    exit 1
  fi
done
echo "PASS: original nine ETA LSPosed scopes and existing OEM hook registrations preserved"
