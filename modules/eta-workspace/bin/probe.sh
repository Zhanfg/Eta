#!/system/bin/sh
# Diagnostic only; never start or probe another app's private processes.
sdk="$(getprop ro.build.version.sdk 2>/dev/null)"
release="$(getprop ro.build.version.release 2>/dev/null)"
printf 'ETA workspace capability preview\n'
printf 'android_sdk=%s\n' "$sdk"
printf 'android_release=%s\n' "$release"
if command -v input >/dev/null 2>&1; then
  if input --help 2>&1 | grep -q -- '-d DISPLAY_ID'; then
    printf 'display_targeted_input=cli_declared\n'
  else
    printf 'display_targeted_input=unconfirmed\n'
  fi
else
  printf 'display_targeted_input=unavailable\n'
fi
if command -v dumpsys >/dev/null 2>&1; then
  if dumpsys display >/dev/null 2>&1; then
    printf 'display_service=responding\n'
  else
    printf 'display_service=unavailable\n'
  fi
fi
printf 'virtual_display_runtime=not_installed\n'
printf 'third_party_background_gui=not_validated\n'
printf 'lsp_target_app_injection=not_used\n'
printf 'display_zero_fallback=forbidden\n'
