# ETA Virtual Workspace: generic same-user, multi-app Android execution

**Status as of 2026-10-28:** app-side scheduler, preserved LSPosed scopes, real UID-authenticated AF_UNIX handshake source, Settings IPC probe and diagnostic-only KSU/Magisk build workflow. **Not yet a working trusted-display service or installable end-to-end product.** This is intentionally separate from WeChat, any other specific app, and WorkBuddy.

## Requirements / invariants

1. Display 0 always belongs to the human. Background tasks NEVER inject display-0 input, change the foreground task, trigger a global BACK, move the IME onto the human display, or silently fall back when a display lease fails.
2. Run already installed apps **as the same unlocked Android user** to reuse their real profiles and login data. No app clones, duplicated databases, synthetic login state, secondary Android user or remote mirror are created.
3. **Preserve all existing ETA LSPosed scope entries and hook registrations unchanged.** Do not add any new app to the scope for the virtual workspace. Existing system, SystemUI, Google, OEM assistant/memory and launcher integrations remain as before. Background multi-app automation must be provided by the separate KSU/Magisk workspace service, not by adding target-app hooks.
4. Support 3–4 app **jobs** as a capacity goal with a dynamic one-/two-display pool, not an unconditional promise of four parallel foreground Android Activity instances. The number of usable displays is determined by ROM and RAM conditions.
5. Use authorized public UI behavior and Android security checks; no anti-detection spoofing, traffic interception, app integrity bypass, automation evasion, private data copying or SELinux permissive state.
6. Every consequential external side effect gets explicit scope, destination identity, idempotency and post-action readback. Never retry an operation with unknown outcome without checking actual results.

## Components

```text
ETA Android app (unprivileged)
  TaskGraph -> WorkScheduler -> AppWorkspaceScheduler -> StepCheckpointStore
        |               |                 |
        |               +-- Data/HTTP, MCP, shell-allowed-file, offscreen WebView
        |                                 |
        +--------- authenticated local AF_UNIX IPC --------------------+
                                                                     |
KernelSU / Magisk module, native companion (planned)                 |
  WorkspaceBridge   SO_PEERCRED(ETA app UID), version, scoped token <--+
        |
        +-- DisplayPool: session 1 / session 2, both displayId > 0
        +-- ActivityTaskManager: same-user launch/track task + window ID
        +-- TargetedInput: gesture displayId, verification and cancellation
        +-- TargetedCapture: per-display screenshot + AX hierarchy
        +-- Focus/IME Guard: never hijack keyboard or focus on Display 0
        +-- Watchdog: process death, display removal, lock, SELinux errors
        |
        +-- No new target-app hooks / no login export / no network listener
```

The bridge shall be a small local `app_process`/native component, inspired by the **on-device service** used by scrcpy (Apache-2.0); no desktop scrcpy client, network control endpoint, or general-purpose Root shell RPC. Root and shell UIDs have different Android display permissions: both must be tested independently. Do not assume Root equals system_server or grants signature permissions. Avoid patching SELinux policy unless evidence demonstrates the minimum scoped rule necessary. Never blanket-disable enforcement.

The diagnostic-only module is under `modules/eta-workspace/`. It currently runs harmless capability probes, not an Android display service. A separate release gate is mandatory before calling its ZIP functional.

## Reusing logins: precise semantics

Use the already installed package under the **existing foreground Android user ID**. Android framework still launches the app as its original package UID and reads the same encrypted app storage. ETA never reads or duplicates stored credentials. A user's currently signed-in app generally remains signed in when opened from another display, subject to the app's own session rules.

However, **the same-user package has shared process/global state**. If an already-open Activity is singleTask/singleInstance or not multi-display aware, a launch may relocate or reuse it. Never force-move a user's active app off Display 0. Use `updateUserForeground` + per-package lease; if a collision exists, suspend that app's background step and let unrelated tasks continue. A real second sandbox would require a second Android profile and may require logging in again.

## Display model

- Initial pool = 1 secondary display; scaling goal = 2 active displays if GPU/memory and ROM support; upper cap 4 only after extensive testing, not default.
- Multiple applications rotate on a display via Android tasks; Android may stop/freeze background Activities. Resume each app from a checkpoint and re-observe before acting.
- `AgentWorkspaceScheduler` admits at most 4 app jobs by default, enforces display IDs greater than 0 and one in-flight controller per package. Pending jobs use FIFO fair scheduling across available displays.
- Display lease identity includes `taskId, displayId, Android userId, generation, ownerUid, issuedAt, expiry`. A display identifier alone is not authorization.
- Background snapshots use `displayId + windowId + observationId + generation + viewport transform`. Changes to orientation, task focus, display recreation or user foreground activity invalidate the old observation.
- Browser/MCP/native API tasks can run in parallel and do not consume display slots.

## System calls (gated and verified)

- Display creation/lifetime: Android `DisplayManager` service via an authorized `app_process` bridge; retain a `Surface` and release all resources on crash/cancel.
- App launch: `ActivityOptions.setLaunchDisplayId(displayId)` or verified `am start --display ID`, explicitly as the existing Android user; inspect actual task/display association after every launch. Never launch an app on Display 0 when the secondary display declines it.
- Input: `GestureDescription.Builder.setDisplayId(displayId)` and/or Android's `input -d DISPLAY_ID` only against a validated lease; check all events route to target display. Keyboard and IME require separate checks, not merely an `input -d` flag.
- Observation: `getWindowsOnAllDisplays()`, window display IDs, target-specific screenshots with Android security restrictions; OCR/vision on permitted images as fallback, never claim DOM text is native OCR.
- Android secure content is intentionally inaccessible. If a sensitive App blocks screenshot, capture does not work around it.

## Versioned local IPC (capability handshake implemented; display-control protocol future)

Commands must be typed (never raw shell or raw `Intent` from model text):

```text
HELLO(version, app_signature, nonce) -> capabilities, session
CREATE_DISPLAY(width, height, dpi, user_id) -> lease | unsupported
LAUNCH(lease, verified_package) -> task_id, actual_display_id
OBSERVE(lease, task_id, limit) -> display/window scoped nodes and image
ACT(lease, observation_id, kind, bound_params) -> dispatched/verified/unknown
CHECK_FOREGROUND() -> current app ownership
CLOSE_DISPLAY(lease) -> released
```

Implemented now: the native bridge checks client UID with kernel SO_PEERCRED and implements only HELLO 1, PING and CAPS. ETA checks that the server UID is Root and that the protocol and service identifier match. There is no raw command API, no TCP/ADB listener and no display lease. Not yet implemented: service code-signing/provenance pinning, per-session authentication tokens, multi-user UID mappings, rate limiting, actual display creation and display-specific input. Future app-control commands must require a validated lease and strict authorization.

## Workflows: 4 apps, same login, foreground stays usable

Example request: compare listings in shopping apps A and B, ask a seller on messenger C, record findings in notes D, then place an approved order.

```text
Plan          A.read -> B.read -> Compare
                                        |
                           C.compose_question -> C.send_authorized_once
                                        |
                          A/B.recheck_price_and_stock
                                        |
                          D.write_draft -> Review
                                        |
                    Verify merchant + exact order amount
                                        |
                      Purchase approval checkpoint
                                        |
                          Commit once -> Verify
                                        |
                      Receipt/result + cleanup
```

Each node is a durable checkpoint: `PENDING -> LEASED -> OBSERVED -> PREPARED -> DISPATCHED -> VERIFIED`. If a send/order state becomes `OUTCOME_UNKNOWN`, the Agent must **read back** the actual conversation/order before any retry. An unknown result is never blindly replayed. The app scheduler and side-effect authorization policy are independent; `AgentWorkspaceCommitPolicy` requires verified recipients and merchants and per-task grants. Payment requires an additional explicit approval and ordinary provider verification.

## Resource contention and anti-interference policy

Display 0: highest priority human owner; never synthesize touch. Secondary display: Agent lease, but monitor actual focus and window ownership. Global resources needing separate serialization: Android clipboard, keyboard/IME, media/audio focus, system settings, permission dialogs, notifications and login/logout. If an operation cannot be safely isolated, return a blocked/handover step **without pausing unrelated inference and web/file work**.

Power: adaptive one-display default, only expand pool after memory measurement. Foreground-service user-facing notification for active runs; optional read-only miniature preview. Throttle image sampling and OCR when device is busy, use event-based UI verification before repeated screenshots. Display/input worker must be stoppable immediately; release on session or user stop.

## Delivery gates

- [x] Code: app-side scheduler and unit regression tests.
- [x] Code: per-task merchant/recipient action-authorization policy and unit regression tests.
- [x] Code: restored exact original nine LSPosed scope entries and existing Google/OEM assistant/launcher/system hook registrations; CI verifies no addition or removal.
- [x] Source: KSU/Magisk module and UID-bound native capability bridge; host protocol smoke tests, Android ARM64 build and module ZIP packaging workflow added (CI execution not yet observed).
- [ ] CI passing at current head (no claim until a fresh run is observed).
- [x] Implemented source: UID-authenticated local Root companion handshake + app-side client; no display capabilities advertised.\n- [ ] Successfully built and installed Android ARM64 Root bridge, signed build provenance, verified on a physical device.
- [ ] Actual secondary display creation + verified third-party launch and same-user session.
- [ ] Display-scoped input, screenshot, accessibility and IME without any Display 0 interaction.
- [ ] Task DAG executor and durable checkpoint store integrated with ETA runtime.
- [ ] Real 4-app rotation with two displays, app restarts, relogin checks and lock/rotation/low-memory tests.
- [ ] End-to-end message and purchase approval/reconciliation tests, using the user's authorized accounts and test transactions.
- [ ] ColorOS, AOSP-based ROM and Android 13–17 verification.
- [ ] Integration into `main`, combined WorkBuddy regression and functional module release.

## Reference reading

- AOSP display launch policy: https://source.android.google.cn/docs/core/display/multi_display/activity-launch?hl=en
- AOSP multi-display FAQ: https://source.android.com/docs/core/display/multi_display/faq
- AOSP per-display input routing: https://source.android.com/docs/core/display/multi_display/input-routing
- AOSP input methods: https://source.android.com/docs/core/display/multi_display/ime-support
- scrcpy display owner: https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/video/NewDisplayCapture.java
- KSU modules: https://kernelsu.org/guide/module.html
