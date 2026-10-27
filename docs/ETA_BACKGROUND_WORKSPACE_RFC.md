# ETA Background Workspace / 物理屏与 Agent 工作空间隔离

Status: **B1 partial implementation**. Strict background mode is now exposed in Settings and checked before ETA's built-in tool dispatch. A non-invasive Android virtual-display capability check is available in Settings. This is not yet working third-party app isolation; physical-device testing remains necessary.

## Objective

One physical phone runs two independent interaction surfaces:
- Display 0: owned by the human. Browsing, typing, gaming and notifications remain usable.
- Agent background execution: networking, shell, MCP, model inference and offscreen WebView must not change foreground app, focus or clipboard.
- If a workflow truly needs Android GUI interaction, attempt a separate display **only after permission and capability checks**. If unavailable, queue foreground-required steps and explicitly request a user-approved handover. Never silently steal Display 0.

This is stronger than "keep a foreground service alive" or a transparent overlay. A running service does not isolate window focus, IME, input or display ownership.

## Existing ETA architecture / confirmed gaps

- `AgentRuntimeRunExecutor` creates `GuiControlCoordinator.RunControl` per run.
- `GuiControlCoordinator` coordinates **Agent ↔ Agent**, not **Agent ↔ user**. It currently models one shared display, and `pauseForUser` pauses the whole `AgentRunController`, which is unsuitable for independent background work.
- `AgentToolRequirements` flags `observe_screen`, `tap`, `swipe`, `launch_app`, `open_uri`, `wait_for_text` and related calls as GUI-consuming. `browser_use`, `fetch_url`, `web_search`, terminal, file and MCP tools are not GUI-consuming.
- `AgentBrowserSession` already has offscreen WebView layout, DOM operations and its own session lock, with temporary user attachment. This path can be hardened separately; it does not require virtualizing all of Android.
- Current accessibility actions use `rootInActiveWindow`, default-display-sized coordinates and `GestureDescription.Builder` without setting a display ID. This cannot safely target an independent virtual display.
- Current screen observations are tied to foreground screen and must never be silently remapped across displays.

## Execution backends

| Backend | Scope | Screen ownership | Implementation decision |
| --- | --- | --- | --- |
| DATA | Remote HTTP, models, MCP, terminal, file operations | No display | Continue existing execution; checkpoint progress, cancel, resumable failures |
| OFFSCREEN_WEB | Existing independent WebView session; DOM/AX snapshot, browser screenshot | Agent WebView only | Default browser mode; avoid attaching to Display 0 unless user explicitly opens session |
| SECONDARY_DISPLAY | App launch, UI tree, screenshots and gestures on dedicated display | Agent display ID != 0 | Experimental / capability-gated: requires supported display, launch permissions, IME and input tests |
| FOREGROUND_HANDOVER | Operations that cannot be isolated | Human-controlled Display 0 | Explicit confirmation + lease + bounded steps + automatic release |

Do not send `input tap`, `input swipe`, `rootInActiveWindow`, clipboard paste or broad screenshot requests to Display 0 on a BACKGROUND_ONLY task.

## Dispatcher contracts (proposed)

```text
AgentToolCall
  -> ExecutionSurfacePolicy.classify(tool, arguments)
  -> BackgroundScheduler
     -> DATA                  (no UI)
     -> OFFSCREEN_WEB         (session-scoped WebView)
     -> SECONDARY_DISPLAY     (display-scoped Android GUI)
     -> NEEDS_HANDOVER        (suspend this step; preserve run/checkpoint)
```

- Persist per-run `executionMode`: `BACKGROUND_PREFERRED` (default proposal), `BACKGROUND_ONLY` (hard block foreground), `FOREGROUND_WITH_PERMISSION`.
- Persist `surfaceId` and optional `displayId` per GUI observation/action. `observation_id`, node identities, coordinates and screenshots must include the display ownership. Reject old IDs after display creation, removal, orientation or task switch.
- Maintain one **lease per surface**, not one global GUI owner. Human always owns Display 0 by default.
- A user touch/handover interruption invalidates queued Display 0 actions, never cancels unrelated model inference, WebView, shell or file work.
- Mark global resources separately: clipboard, input focus/IME, audio focus, media playback, account writes, notification/permission dialogs. The two displays are **not** fully isolated user profiles or app data stores.
- Every action reports `dispatched`, `verified`, `unknown` or `blocked` (with display and task identity). Never auto-retry unknown side-effectful actions.

## Secondary-display feasibility requirements

1. Create a bounded-size `VirtualDisplay` and verify `FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS`.
2. Check `ActivityManager.isActivityStartAllowedOnDisplay` before launch; app-owned/private displays cannot universally host third-party activities. If using a public or privileged display path, explicitly verify grants / ROM restrictions; do not claim it works on arbitrary phones.
3. Launch with `ActivityOptions.setLaunchDisplayId(displayId)` only when allowed, track actual window display ID after launch; fail closed if activity appears on Display 0.
4. For accessible windows use `AccessibilityService.getWindowsOnAllDisplays()`, then `AccessibilityWindowInfo.displayId` and target-display node roots; never `rootInActiveWindow` for background GUI.
5. Use `GestureDescription.Builder.setDisplayId(displayId)` (API 30+) and target-display screenshots (`AccessibilityService.takeScreenshot(displayId,...)`), with policy fallback when unavailable.
6. Configure local-display IME policy where supported: some ROMs show the IME for secondary display on the primary physical display. If that happens, downgrade to HANDOVER rather than disrupting the user.
7. Test SystemUI, dialogs, keyboard, background starts, per-app multi-instance handling, virtual display teardown, rotation and process death. A secondary display is *not* a separate Android user/profile: same-package shared state and non-reentrant activities can still conflict.
8. Protected apps / FLAG_SECURE, restricted permissions, Play Integrity / sensitive login flows must be handled by standard OS enforcement. Do not bypass these protections.

The feasibility of this path must be tested **first** on OnePlus/ColorOS and a representative AOSP-based ROM, with explicit criteria for declining unsupported devices.

## UX

- Default: **后台优先**. ETA runs without demanding screen focus. A persistent notification contains state and Stop; optional peek opens a small read-only preview.
- Options: **纯后台（不碰物理屏）**, **后台优先（必要时询问）**, **允许前台协作**.
- When a step needs Display 0, show a nonintrusive "待授权前台操作" prompt; leave all background-capable steps running. Do not force startActivity or global BACK behind the user's actions.
- User has immediate preemption. Handover on Display 0 is explicit, step-scoped and ends when operation is verified; no fullscreen takeover by default.

## Practical acceptance gates

A0. Offscreen browser and local tools continue while the human types in another app. No foreground task switches, touches, focus theft, system bar overlays or clipboard edits.
A1. On `BACKGROUND_ONLY`, **zero Display 0 gestures** and no forced foreground launches; impossible steps remain queued.
A2. Browser session read/click/type/screenshot works without a visible Activity; page images and image text are accurately reported as vision attachments (not fictitious OCR).
A3. On a supported virtual display, verify third-party launch, window tree, gesture targeting, screenshot, IME and user foreground concurrency. If unsupported, no fallback injection into Display 0.
A4. With user consent, handover / resume does not replay uncertain purchases, messages or form submissions.
A5. ColorOS, AOSP, Android 13 and 17: denied permissions, renderer failure, user foreground app switching, rotation, memory pressure, device lock, FGS restrictions.
A6. Latest WorkBuddy integrated APK build and authorized account import/model/SSE/tool-loop separately verified; no shared foreground requirement for Go core.

## Recommended rollout

1. **B1 Safe background**: classify tool surfaces, background-only policy, default offscreen WebView, GUI-step deferral, checkpoint and read-only status. This is useful even without Root.
2. **B2 Experimental secondary display**: capability probe + per-display node, screenshot, gesture, app launch + IME policy; opt-in supported-ROM only.
3. **B3 Multi-task resource governance**: per-display leases, global resource conflict matrix, throttling and reentrancy protection; visual QA, battery/network failover.
4. **B4 User-facing release**: test matrix, permission UX, release gate and integration into main when verified.

References:
- Android DisplayManager / VirtualDisplay: https://developer.android.com/reference/android/hardware/display/DisplayManager
- Android AOSP secondary display launch policy: https://source.android.google.cn/docs/core/display/multi_display/activity-launch?hl=en
- Android ActivityOptions#setLaunchDisplayId: https://developer.android.com/reference/android/app/ActivityOptions
- Accessibility multi-display windows / gestures: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- GestureDescription.Builder#setDisplayId: https://developer.android.com/reference/android/accessibilityservice/GestureDescription.Builder
- Playwright MCP (browser AX snapshot architecture, not direct Android app control): https://github.com/microsoft/playwright-mcp
