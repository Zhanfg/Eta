# ETA × WorkBuddy: Android native migration and verification gates

**Status: EXPERIMENTAL / NOT RELEASED.** This is a migration record, not a claim that the requested full WorkBuddy experience works on a phone.

## Authoritative source and byte provenance

Manager: `ithtelab/workbuddy-manager` (MIT), v2.9.0. The manager is FastAPI/Python with a Next.js UI. Its official release embeds the Go workbuddy2api gateway source at `upstream/`; the original Sliverkiss repository was removed. Do **not** mistake the separate 3304711297 desktop Tauri project for the Go upstream.

- Release input: `workbuddy-manager-v2.9.0.tar.gz`
- SHA-256: `7f1a37b9bd9987c6210a41e58360e37658e71c4c305fbe85c64b25305bb7133e`
- CI: `.github/workflows/workbuddy-android-core.yml`
- CI stages: download and verify digest → locate bundled upstream go.mod and LICENSE → go mod verify → go test → Go Linux byte-path smoke → GOOS=android GOARCH=arm64 CGO_ENABLED=0 build → ELF header/interpreter checks → package into `lib/arm64-v8a/libwbcore.so` of ETA Debug APK.
- The Android binary is a **PIE executable with /system/bin/linker64**, despite being packed in a `.so` nativeLib directory for Android app extraction. It is started as a subprocess, never loaded with `System.loadLibrary`. The `.so` suffix is a packaging accommodation, not proof of JNI/shared-library compatibility.
- No external WorkBuddy Manager, Docker, Python, Node.js, Termux or root is *intended* at runtime.

## Backend architecture

```
ETA Compose screens (native management)
    |  Android File Picker + local vault (Keystore AES-GCM)
    |  Android sandbox runtime controller (WorkBuddyEmbeddedCore)
    |        - package native executable from app nativeLibraryDir
    |        - private 0700 auth/data directories
    |        - random 256-bit localhost bearer key
    |        - listen 127.0.0.1:random_ephemeral_port
    |        - on-demand lifecycle; no background auto tasks by default
    v
[Go workbuddy2api from official v2.9.0 release]  <--- real pool / sticky session / cooldown / refresh / SSE
    |      GET /healthz, /status, /v1/models
    |      POST /v1/chat/completions, /v1/checkin
    v
CodeBuddy authorized user subscription
    ^
    |
ETA Agent tools and runtime -> WorkBuddyEmbeddedProvider -> Go core localhost /v1
```

### Protocol note

The Go core implements Chat Completions **as its native downstream API**. Responses and Anthropic Messages are Python Manager's compatibility adapters and are **not magically available** inside this embedded Go process. ETA still has its own native Responses / Messages handlers for other Providers; for local WB, use Chat/SSE until a tested protocol translation layer exists. Never send `/v1/responses` to a service that only implements Chat.

### Credential boundary

- The account source is JSON chosen from Android SAF. The vault is encrypted with Android Keystore AES-GCM in `noBackupFilesDir`.
- The upstream Go gateway reads and refreshes per-account JSON files. **The Android adapter writes temporary plaintext credentials to a sandbox-private (0700/0600) directory during runtime**; this is a **security limitation** and must not be falsely described as encrypted-at-rest while the process runs.
- The adapter attempts to merge refreshed credentials into the encrypted vault at shutdown and purge temporary files. A crash or hard kill can leave private plaintext snapshots behind. Production release must add startup scrubbing and a clean crash-recovery design with credentials kept in memory when possible.
- No external TCP listener and no fixed or published API key. Native localhost access is guarded by a random per-start 256-bit bearer key; this is still a network socket visible to apps on device, thus not equivalent to a private Binder/Unix-domain socket. A Unix-domain transport is preferred for the final version.
- No credentials are committed to GitHub or CI; CI uses **zero-account fixtures**. Only personally owned/authorized accounts should be imported and used.

## Frontend migration: actual feature inventory (not UI-only mockups)

| Feature from Manager | Native ETA integration status | Authority |
| --- | --- | --- |
| Android JSON selection + account display | Implemented prototype | Kotlin Keystore vault |
| Account import validation and overwrite | Implemented prototype | Kotlin vault |
| Account-pool scheduling, session stickiness, lease and cooling | Wired to Go on-demand backend | Go upstream; device test pending |
| Token refresh and persistence | Wired to Go; snapshot round-trip present | Go upstream + vault bridge; crash safety pending |
| Live model list | Wired to Go `/v1/models` | Go upstream; entitlement test pending |
| SSE chat streaming and Agent tool loop | Wired via ETA existing OpenAI Chat provider | Go upstream; end-to-end test pending |
| Dashboard: health and runtime start/stop | Native Compose controls added | Android runtime |
| Real subscription balance and account health | Pending Compose status / quotas UI | Go `/status` plus Manager-specific queries |
| Manual check-in and scheduler controls | Go core offers these; native settings not complete | Go |
| Multi-account groups and enable/disable | Go core handles pool; native controls pending | Go |
| API key quotas, IP allow/deny, alias mapping, audit and usage views | **Not fully ported** (Python Manager owns these) | Requires Kotlin/SQLite backend and native Compose |
| Per-account proxy egress, PG sync, remote backup, signature update | **Not ported**; decide whether relevant on local mobile app | Python Manager |
| Platform billing promotion actions | **Not ported**; require entitlement and account safety review | Manager |

A screen is done only when data comes from an identified backend source and every user-visible action executes. Placeholder controls, WebViews, fake model/credit values, and importing arbitrary JSON as a subscription do not count.

## Verification gates

G0. Provenance: official release SHA verified; no unsigned fork substituted. **PASS** when CI verifies.
G1. Go upstream source test and Android arm64 ELF crosscompile, with actual output hash and program interpreter. **PASS** when CI verifies.
G2. No-account host-level HTTP integration: process start, real response headers/body from `/healthz`, auth denied, authenticated `/v1/models`. **CI pending**.
G3. ETA APK contains `lib/arm64-v8a/libwbcore.so`, Gradle Lint, tests, Debug build pass. **CI pending**.
G4. Android arm64 actual runtime: subprocess executes from installed APK with SELinux, UID sandbox, Android linker, 127.0.0.1 loopback and real `X-Service: workbuddy2api` bytes. **NOT YET VERIFIED**.
G5. Real user-authorized account import, token refresh, manager status/credit and actual available models on OnePlus/ColorOS. **NOT YET VERIFIED**.
G6. Streaming Chat and tool_calls from authorized subscription, response time, cancellation, retry/failure taxonomy, cross-model/multi-round session. **NOT YET VERIFIED**.
G7. Native dashboard feature-parity, accessibility/dynamic color, offline/boot/failure recovery, credential hygiene (plaintext file security). **NOT YET VERIFIED**.

CI pass alone is **not** G4/G5/G6. No official-release APK should be published until critical gates pass. Keep the existing `main` as the upstream ETA reference.
