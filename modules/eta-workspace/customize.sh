#!/system/bin/sh
# Sourced by KernelSU/Magisk's module installer after extraction.
# No system modifications, sepolicy changes or display/input daemon.
# Native service implements authorization and capability negotiation only.
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/bin/probe.sh" 0 0 0755
if [ -f "$MODPATH/bin/eta-workspace-bridge" ]; then
  set_perm "$MODPATH/bin/eta-workspace-bridge" 0 0 0755
fi
