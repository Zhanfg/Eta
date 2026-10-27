# ETA Workspace Companion: KSU / Magisk capability-preview module

**Status: diagnostics only, not a runnable cross-app virtual machine. Do not flash this module expecting background app control.** The Android app-side scheduler and isolation policy are under active development in the same branch.

The module is intentionally tiny: a KSU/Magisk-compatible `module.prop`, a late-start service that records only system capability categories and a manager Action button that prints diagnostics. There is no `system/` overlay, sepolicy broadening, Zygisk, per-app hook, login/database copy, package spoofing or automatic input injection.

Required future components for an operational release:
1. Reproducibly built, signed local `app_process`/native display-owner bridge with Android 13–17 ROM compatibility checks.
2. Authenticated `AF_UNIX` connection to ETA with peer-UID validation and versioned request/response envelopes.
3. Root/shell service that creates and holds 1–2 trusted background displays and returns leases whose display IDs are greater than zero. This is distinct from an ordinary application-owned VirtualDisplay.
4. Display-specific Activity launch, accessibility-node reading, screenshot and input injection. Any focus/IME leak into physical Display 0 aborts the session.
5. Foreground-app conflict checking and per-package app leases. Already installed apps run in the **same Android user** so they normally reuse logged-in data; that is not equivalent to a second sandbox or guaranteed multi-instance support.
6. Task checkpoints and explicit final confirmation for consequential actions such as messages, orders and payments.
7. Device validation, CI and tested APK-module version matching.

Developer packaging (KSU/Magisk manager): zip the **contents** of this folder with `module.prop` at ZIP root. The resulting diagnostic package does not change runtime behavior.

Do not upload user login databases, tokens, passwords or screenshots into GitHub artifacts. The module is a privilege boundary and must never accept arbitrary shell commands from LLM output.
