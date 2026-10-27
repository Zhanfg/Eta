#!/system/bin/sh
# KSU/Magisk late_start. Do not hook, mount over system, or launch apps.
MODDIR="${0%/*}"
i=0
while [ "$(getprop sys.boot_completed)" != "1" ] && [ "$i" -lt 60 ]; do
  sleep 1
  i=$((i + 1))
done
if [ -f "$MODDIR/disable" ]; then
  exit 0
fi
# Root module is intentionally inert until a validated display bridge exists.
# Persist only capability categories; do not log private app data.
umask 077
mkdir -p "$MODDIR/state"
sh "$MODDIR/bin/probe.sh" > "$MODDIR/state/last_probe.txt" 2>&1
exit 0
