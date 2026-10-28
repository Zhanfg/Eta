#!/system/bin/sh
# On-demand diagnostic only. Do not launch any third-party app or inject input.
MODDIR="${0%/*}"
sh "$MODDIR/bin/probe.sh"
echo "---- Android privileged virtual-display probe (opt-in) ----"
PROBE="$MODDIR/bin/eta-display-probe.jar"
if [ ! -r "$PROBE" ]; then
  echo "display_probe_dex=missing"
  exit 0
fi
if [ ! -x /system/bin/app_process ]; then
  echo "app_process=missing"
  exit 0
fi
echo "display_probe=starting (no app launch; release after test)"
# Boot-time service never calls this. A human invokes it from the module manager.
# All errors are printed as capability failures rather than changing SELinux.
CLASSPATH="$PROBE" /system/bin/app_process /system/bin \
  io.github.mangi.eta.workspace.WorkspaceDisplayProbe --probe-only
