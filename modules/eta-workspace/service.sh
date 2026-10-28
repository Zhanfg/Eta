#!/system/bin/sh
# KernelSU/Magisk late_start. Starts ONLY the capability IPC bridge.
# Never launches apps, injects input, changes SELinux or touches Display 0.
MODDIR="${0%/*}"
i=0
while [ "$(getprop sys.boot_completed)" != "1" ] && [ "$i" -lt 90 ]; do
  sleep 1
  i=$((i + 1))
done
if [ -f "$MODDIR/disable" ]; then
  exit 0
fi
umask 077
mkdir -p "$MODDIR/state"
sh "$MODDIR/bin/probe.sh" > "$MODDIR/state/last_probe.txt" 2>&1

BRIDGE="$MODDIR/bin/eta-workspace-bridge"
if [ ! -x "$BRIDGE" ]; then
  printf 'BRIDGE_BINARY_MISSING\n' > "$MODDIR/state/bridge_status"
  exit 0
fi
# Same logged-in user 0 by default. Only package directory owner UID is read,
# not its contents. Multi-user instances require separate authorization.
APP_DATA="/data/user/0/io.github.mangi.eta"
if [ ! -d "$APP_DATA" ]; then
  printf 'ETA_APP_NOT_INSTALLED_USER_0\n' > "$MODDIR/state/bridge_status"
  exit 0
fi
APP_UID="$(stat -c '%u' "$APP_DATA" 2>/dev/null)"
case "$APP_UID" in
  ''|*[!0-9]*)
    printf 'ETA_APP_UID_UNAVAILABLE\n' > "$MODDIR/state/bridge_status"
    exit 0
    ;;
esac
if [ "$APP_UID" -lt 10000 ] || [ "$APP_UID" -gt 1999999 ]; then
  printf 'ETA_APP_UID_INVALID\n' > "$MODDIR/state/bridge_status"
  exit 0
fi
"$BRIDGE" --app-uid "$APP_UID" >> "$MODDIR/state/bridge.log" 2>&1 &
BRIDGE_PID="$!"
printf '%s\n' "$BRIDGE_PID" > "$MODDIR/state/bridge.pid"
printf 'BRIDGE_LAUNCH_ATTEMPTED\n' > "$MODDIR/state/bridge_status"
exit 0
