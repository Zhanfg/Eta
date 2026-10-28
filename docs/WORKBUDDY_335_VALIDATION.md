# Eta 3.3.5 WorkBuddy: build evidence and remaining device acceptance

## Existing build, verified through GitHub on 2026-10-28

Source: `release/eta-workbuddy-mvp-v1@1f33c90801c32ef438bedb7f6acd9528983a6719`.

- [PR Check #116](https://github.com/Zhanfg/Eta/actions/runs/40745169987): successful lint, unit tests and ordinary Debug build.
- [WorkBuddy core build #23](https://github.com/Zhanfg/Eta/actions/runs/40745160576): successful pinned official source verification, host Go tests/HTTP smoke, Android ARM64 ELF build, Java 17 API smoke, Android lint/tests/Debug build and native-core packaging.
- [Integrated APK artifact](https://github.com/Zhanfg/Eta/actions/runs/40745160576/artifacts/12841283759): `eta-workbuddy-embedded-core-debug`.
- [Java 17 API artifact](https://github.com/Zhanfg/Eta/actions/runs/40745160576/artifacts/12842625633): `eta-ai-runtime-api-java17`.

The integrated artifact ZIP digest reported by GitHub is
`f4f7dbf78a93f4424e6dc0c0b394dc59497c523d7230374bf0a6cb8957703269`.
This is the artifact archive digest, **not** the APK file digest or its signing certificate.
The Go binary digest printed before packaging was
`69d8b7488d8a7b729168d3afc53a0586f55764447a82116bad371ca95b87705e`.

These results apply to the exact source above. They do not prove any subsequent follow-up commit.
GitHub artifacts expire; retrieve them while available.

## Follow-up validation added here

- Generate five nested account/auth JSON records in an in-memory ZIP using explicitly invalid synthetic values. Check local parsing, count and redacted previews.
- Validate the real ELF64 AArch64 PIE header, program-header bounds, executable load and PT_INTERP, rather than accepting an interpreter string found anywhere in a file.
- Compare the packaged core byte-for-byte with the binary built in the same run; reject missing or duplicate native ZIP entries.
- Select the exact 3.3.5 Debug APK; inspect its actual application ID/version using apkanalyzer, and verify its APK signature using apksigner.
- Produce APK/JAR file SHA-256 sums and public build provenance.
- Limit build concurrency and run Kotlin in the Gradle process to avoid separate large heaps.
- Upload an explicit allowlist of APK, API JAR, checksums and provenance. Account exports, runtime auth/config, source fixtures and logs are excluded.

The new Python/Kotlin tests and stricter script require their own successful workflow run before they can be described as passed. The local cloud executor was offline while this follow-up was authored.

## Local/CI reproduction

Use JDK 25, Go 1.25.x, Python 3, Android command-line tools, SDK 37 and build-tools, plus the repository's Gradle Wrapper.

```bash
python3 -m unittest discover -s scripts/tests -v
bash scripts/build_workbuddy_mvp.sh
```

The dedicated `WorkBuddy delivery validation` workflow runs these checks against the exact checked-out commit. It uses no real account export, account secret or release-signing key.

## Device acceptance still required

Keep PR #9 Draft until the required device checks pass.

1. Install the integrated ARM64 APK on Android 13 or newer. Check signing compatibility before updating an existing installation; do not automatically uninstall or discard existing app data.
2. With networking disabled, select a locally generated five-account ZIP, confirm import and read back the encrypted local account count. Invalid synthetic accounts test the offline flow only.
3. Check native process startup and local health/status separately. This requires the actual ARM64 Go executable on Android; host Go smoke and ZIP parsing are insufficient.
4. With an explicitly authorized valid account, separately check model discovery, SSE reply and tool round-trip. Do not upload the account ZIP or responses to GitHub.
5. Verify original LSPosed scopes/hooks and existing Eta behavior.

Report only counts, boolean results, source/build IDs, ABI/version and redacted error categories. Never attach tokens, UIDs, nicknames, exported JSON/ZIP, private filesystem paths, production keystores or raw runtime logs.
