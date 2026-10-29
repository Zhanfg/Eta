# Reusable AI Runtime API — v0.1.0

**Goal:** KELIVO, ETA and future Android/desktop programs can reuse an AI backend without depending on ETA's UI, Agent loop, LSPosed, settings or phone integration.

## What is implemented

`ai-runtime-api` is a **pure Java library**, compilable to Java 17 bytecode. It has no Android, Compose, Xposed or Tencent imports and contains no credentials or login logic.

- `AiBackend`: a named backend with a long-lived streaming-safe `withSession` lease.
- `AiEndpoint`: validated HTTPS / localhost HTTP URL + bearer token. The token is never shown in `toString`.
- `AiBackendRegistry`: multiple backend registrations with unique IDs.
- `AiSessionAction`: Java/Kotlin-compatible request callback. The host backend owns process startup, failure recovery, cancellation and releasing the session.

ETA's `app/src/main/kotlin/io/github/mangi/eta/data/workbuddy/WorkBuddyAiBackend.kt` adapts the native WorkBuddy Go backend to this interface. `WorkBuddyEmbeddedProvider` **actually calls** the portable interface while preserving ETA's existing OpenAI-compatible streaming parser and tool calls.

## KELIVO / other project integration

Include the `:ai-runtime-api` Gradle project or distribute its Java 17 JAR. Then implement an `AiBackend` for each provider. The WorkBuddy adapter can be copied **only along with a host-owned WorkBuddy runtime and secure account vault**. A host must not re-use another app's private files, credentials or Android Keystore alias.

For Android ARM64 a previously CI-built WorkBuddy Go core is available as `lib/arm64-v8a/libwbcore.so` inside the historical ETA WorkBuddy debug APK; it is an Android PIE **executable**, not a JNI library. A standalone `ai-runtime-workbuddy` Android library with its own vault and native binary installer is **planned, not implemented**. A ZIP of the Java interface + ELF is only a developer kit; it is not a complete drop-in backend.

Cross-app use (KELIVO calling a running ETA process) will require an authenticated, user-approved, narrowly scoped IPC service. This module currently exposes **no exported Android Binder/Intent or TCP server** and does not transport tokens between applications.

## Verification commands

```sh
./gradlew :ai-runtime-api:jar
# Host smoke test without Android SDK:
javac --release 17 -d /tmp/eta-ai-api-classes \
  ai-runtime-api/src/main/java/io/github/mangi/ai/runtime/*.java
javac --release 17 -cp /tmp/eta-ai-api-classes \
  -d /tmp/eta-ai-api-classes \
  ai-runtime-api/src/test/java/io/github/mangi/ai/runtime/AiBackendHostSmoke.java
java -ea -cp /tmp/eta-ai-api-classes io.github.mangi.ai.runtime.AiBackendHostSmoke
```

These checks were successfully executed locally using OpenJDK 21, producing a JAR with verified Java bytecode and lease tests. **Current Android full-app Gradle CI remains unverified.**

## Release gates

1. Generate a tested ARM64 ETA APK with embedded Go executable and Java interface.
2. Demonstrate import of a synthetic five-account ZIP into the Android Keystore and offline local Go `/healthz` and `/status`; fake tokens must not be sent upstream.
3. User-authorized **real** account + model list + SSE + one Agent tool-call round trip. JSON schema validation and Go localhost health alone do not establish account entitlement.
4. Extract `ai-runtime-workbuddy` into a reusable Android library, test in a second host app before treating KELIVO portability as complete.
