# ETA Workspace Companion — KSU/Magisk 0.2.0-alpha01

**This is an IPC diagnostic preview, NOT a virtual-display/Android app automation module.**

## What actually exists in source

- Android ARM64 native daemon (`native/eta_workspace_bridge.c`), bound to a **local-only abstract AF_UNIX socket**. No TCP, no remote access, no general Root command endpoint.
- Every connection is checked with kernel `SO_PEERCRED`: only the configured ETA application UID may call the service. ETA verifies that the peer service UID is Root (0).
- Versioned operations: `HELLO 1`, `PING`, `CAPS`. Unknown instructions and version mismatches are rejected; the response explicitly reports `display_backend=false`, `app_launch=false`, `targeted_input=false`.
- Boot `service.sh` computes UID from the existing Android user 0 ETA package directory owner, starts the daemon when its native binary is present, and writes diagnostics under module-local `state/`. No app data is read.
- ETA Settings exposes a **generic** display-policy probe and a **KSU/Magisk local service handshake** test. They do not start third-party apps.
- A CI workflow compiles the native program for Linux and Android ARM64 and packages an optional diagnostic module ZIP. Host tests check UID authorization and the fail-closed protocol.

Do **not** flash this preview expecting any GUI/display capability. The current native server is intentionally read-only and cannot control any App.

## Isolation and compatibility constraints

- LSPosed scopes and original OEM assistant/Google hooks are **preserved unchanged**. The virtual workspace will **not** add app-specific LSPosed scopes.
- Use the already installed apps in the same Android user; do not duplicate credentials or move active user-owned windows away from Display 0.
- Root is not synonymous with Android's system UID. Display creation, third-party Activity launching, display-specific screenshots and IME/input isolation require separate framework permissions and on-device validation.
- The daemon is currently tailored to Android user 0. Multi-user and work profile UIDs must not be guessed or mapped silently.
- No SELinux permissive policy, special app signature spoofing, secure-window bypass, target-process injection, or remote listeners.
- Physical Display 0 remains user-owned. Zero automatic failover to Display 0 in strict background mode.

## Module ZIP

CI should package the **contents** of this directory with `module.prop` at archive root and an executable `bin/eta-workspace-bridge` compiled via Android NDK. `customize.sh` assigns script and binary permissions. On boot, `service.sh` attempts a capability-only daemon. No system files are modified.

The module manager Action runs only capability diagnostics. A successful `HELLO 1` does **not** mean secondary display support exists. The current version returns `BRIDGE_PROBE_ONLY` in ETA's Settings.

## Remaining implementation requirements

1. Real Android-privileged display owner with bounded display leases and validated cross-app launch.
2. Per-display Accessibility window, screenshot and gesture routing, with verified local-display IME behavior.
3. Durable multi-app task checkpoints, user-foreground conflict handling, command cancellation and display teardown.
4. Authorized external writes (messaging/purchases) with explicit destination check and no replay of uncertain outcomes.
5. ColorOS/AOSP device tests across supported Android versions, independent WorkBuddy integration, reproducible APK + module pairing.

If an Android ROM denies the privileged display bridge, the task must fail clearly rather than change SELinux to permissive or hijack Display 0.
