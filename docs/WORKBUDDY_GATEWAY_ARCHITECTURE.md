# ETA WorkBuddy backend audit — 2026-10-29

## Architectural correction

The old delivery proved that an Android APK contains an AArch64 Go ELF and that
a **Linux-hosted** workbuddy2api starts with synthetic account JSON. It did NOT
prove authenticated Android inference, account refresh, real model entitlements,
or any successful Tencent request. Compilation and ZIP parsing are separate gates.

**Sub2API** (Wei-Shaw/sub2api) is a *generic subscription gateway*: inbound
API keys, pool/account grouping, sticky routing, quota accounting, rate limits and
protocol adapters; server deployment uses Go, PostgreSQL and Redis. **workbuddy2api**
is the *provider-specific* Go gateway included with ithtelab/workbuddy-manager:
the Go core consumes `auths/*.json` and speaks WorkBuddy's upstream protocol.
The Manager is an optional Python/UI policy, keys, audit and operations layer.

ETA must not embed Sub2API's distributed service stack just to obtain local
WorkBuddy inference, nor confuse a Manager API token with a CodeBuddy OAuth token.

## The target on-device graph

```text
 ETA Compose settings / SAF ZIP import
    -> bounded ZIP / JSON validator
    -> Android Keystore encrypted vault (lossless original credential JSON)
    -> Go credential-file adapter (sandbox 0700/0600; preserve unknown fields)
    -> Android ARM64 WorkBuddy Go process manager (loopback + random bearer key)
    -> workbuddy2api pool: refresh, health, sticky sessions, concurrency/cooldown
    -> CodeBuddy / WorkBuddy official upstream HTTPS
    -> local Go /v1/chat/completions SSE and /v1/models
    -> reusable ai-runtime-api lease -> ETA Agent streaming/tool loop
```

KELIVO should reuse the **ai-runtime-api** abstraction and a future independent
Android WorkBuddy backend library. It must own its own account vault (or use an
explicitly authenticated IPC broker with user consent), and never read ETA's
private Keystore files directly.

## Seven independent release gates

| Gate | Check | Can use fake accounts? |
| --- | --- | --- |
| A | ZIP structure, duplicate UID, bounds, SAF | Yes |
| B | Keystore save -> readback, raw auth field preservation | Yes, on emulator/device |
| C | ELF extraction, executable bit, native subprocess launch | Yes, **Android** needed |
| D | Go /healthz + authenticated /status total | Yes, Android and host |
| E | Go /v1/models returns registered model ids | Sometimes; no entitlement proof |
| F | Real account refresh, upstream access and SSE response | **No**, authorized real account |
| G | ETA Agent tool-call turn + session affinity/recovery | **No**, authorized real account |

A passing gate cannot be substituted for a later one. Do not label a release
“real calls supported and verified” after only A, D, E.

## Concrete errors fixed

1. Earlier code flattened nested source JSON into a reduced Kotlin `Account`
   value, then regenerated an abbreviated `account/auth` JSON for Go. Future
   metadata would be silently stripped. New code stores the complete original
   source document under AES-GCM and preserves unknown fields during Go handoff,
   updating only known refreshed credentials.
2. The vault adapter previously deleted old core auth snapshots **before**
   decrypting the Keystore vault; a Keystore error could erase recoverable files.
   The decrypted snapshot is now required before deleting stale files.
3. Old CI shipped pinned upstream v2.9.0 from Oct 24, missing later provider
   protocol/channel/account updates. CI is updated to **v2.11.1** (Oct 27,
   SHA256 checked against official GitHub release) with no checksum bypass.
4. Existing native tests are **host-side only**. Keep Android process execution
   explicitly unverified until an emulator/phone actually launches libwbcore.so.
5. ETA had not passed its Agent session identity to the Go gateway. Native
   workbuddy2api uses conversation keys / `prompt_cache_key` for sticky pool
   routing across a tool loop. The embedded Provider now hashes the ETA session
   ID into a stable `prompt_cache_key` per conversation (never sending raw
   local session IDs to the Go pool).
6. An empty `tools: []` combined with `tool_choice: auto` can be invalid for
   an OpenAI-compatible gateway. The JSON builder now omits both keys when an
   Agent turn declares zero tools, with regression coverage.

## Troubleshooting without credential disclosure

- No file picker result or ZIP rejected: report SAF provider / data format stage.
- Readback fails with Keystore exception: report encrypted vault stage; **never**
  auto-delete the vault. Explicit user-approved reset is a separate operation.
- Go launch fails / exit code / port timeout: report native-process stage.
  Avoid logging config, tokens or upstream response body.
- Go /status total=0 after importing N accounts: identify Go auth format or
  filesystem path mapping, *not* cloud model permissions.
- Total=N but healthy=0: account authorization, expiry, pool eligibility, or
  upstream connectivity; no fabricated “success”.
- Models present but inference HTTP 401/403/429/5xx: distinguish credential,
  account entitlement, rate limits, channel errors and transport errors.
- Streaming text without tool calls: not equivalent to Agent tool-loop success.

Do not upload `accounts.zip`, bearer credentials, or an API key to CI.
Tests generate synthetic auth records only.

## Relevant upstream documentation

- https://github.com/Wei-Shaw/sub2api
- https://github.com/ithtelab/workbuddy-manager/blob/main/docs/development.md
- https://github.com/ithtelab/workbuddy-manager/blob/main/deploy/README.md
- https://github.com/ithtelab/workbuddy-manager/releases/tag/v2.11.1
