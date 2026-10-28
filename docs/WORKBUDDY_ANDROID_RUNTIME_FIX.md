# WorkBuddy Android networking and runtime repair

The previous CGO-disabled executable could pass host HTTP tests and APK checks while failing Android DNS. Go 1.25's net/conf.go explicitly prefers cgo on Android because ordinary Go DNS does not use Android's resolver. Android does not normally provide a usable /etc/resolv.conf/localhost DNS server. This is a runtime requirement, not an APK packaging requirement.

Changes:

- Build the official, SHA-pinned upstream gateway with NDK/Bionic, CGO enabled, PIE and 16 KiB load alignment. Require libc.so in its dynamic dependencies.
- Use both Android 14+ Conscrypt APEX and legacy Android system certificate directories with normal TLS verification. Give the child a private HOME and cache directory.
- Serialize explicit account import with core startup and active request leases. Do not recover a never-started core before a fresh import.
- Initialize/recover WorkBuddy only in the main Eta process. A voice or other secondary process must not delete the main process's active auth snapshots.
- Preserve an exported account's device_token in the encrypted vault and the local Go account file; never display or log it.

Actual Android acceptance is implemented in a separate runtimeTest build with application ID io.github.mangi.eta.runtimetest. It cannot replace an existing Eta installation and uses only in-memory synthetic invalid credentials. Its Application excludes unrelated production service startup. The test-only native probes are absent from the normal ARM64 APK.

The Android 35 emulator workflow checks:

1. Five-account ZIP import, actual Android Keystore encryption/readback, native Go /status count, stop/restart and private snapshot cleanup without model requests.
2. Import rejected while an Agent request lease is held.
3. A netgo-tagged probe forces the former core's pure-Go DNS resolver and reproduces failed Android DNS. Android/x86_64 requires external linking, so this comparison retains cgo linkage while explicitly selecting the legacy resolver.
4. The NDK/Bionic executable resolves example.com and verifies its public HTTPS certificate using Android system roots. No account or subscription is used in this check.

Build commands:

```bash
bash scripts/build_runtime_test.sh
./gradlew -Peta.nativeAbi=x86_64 -Peta.runtimeTests=true :app:connectedRuntimeTestAndroidTest
bash scripts/build_workbuddy_mvp.sh
```

A physical ARM64 device and an authorized valid subscription remain necessary for vendor-specific device behavior and real model/SSE/tool calls. Never upload account ZIP/JSON, Tokens, private keystores, decrypted vaults, native runtime auth files or raw device logs. CI uploads only the normal APK, public API JAR, checksums and public provenance. Passing build/unit tests alone is not Android runtime validation; check both jobs of the WorkBuddy Android runtime workflow for the exact commit.
