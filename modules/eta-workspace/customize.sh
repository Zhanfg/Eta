#!/system/bin/sh
# Sourced by KernelSU/Magisk's module installer after extraction.
# No system modifications, sepolicy changes or privileged daemon yet.
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/bin/probe.sh" 0 0 0755
