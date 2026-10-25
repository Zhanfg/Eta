# WorkBuddy Manager × ETA (native Android integration)

## Baseline
- ETA base: Mangi-11/Eta main, 2026-10-25, `b322d98193e7799e56ca2888fcc98beb6504b59b`
- WorkBuddy Manager: ithtelab/workbuddy-manager (MIT), API contract reviewed on 2026-10-25.
- This branch adds **native Kotlin and Compose integration**. There is no WebView, injected webpage, copied WebUI frontend, or embedded Python/Go server.

## What is implemented
1. ETA built-in Provider `WorkBuddy Manager` uses the already existing OpenAI Chat Completions Agent transport, tool calling and SSE streaming.
2. ETA's model tab fetches the key-scoped live list at `GET /v1/models`. Do not remove `global:` prefixes or fabricate model capabilities.
3. Native WorkBuddy tab retrieves `GET /healthz`, key-scoped `GET /v1/usage` (data-plane `wbk_`), and pool list `GET /api/accounts` (management-plane `wbt_`).
4. Native account onboarding initiates `POST /api/auth/start`, opens Tencent's returned authorization URL *only on explicit user interaction*, then reads `GET /api/auth/poll` until complete. The server performs polling, provisioning, account storage and reload, not the Android client.
5. Admin management token can create a dedicated gateway key via `POST /api/keys`, which is saved directly to the ETA Provider. Existing key replacement asks for a second tap. **This server endpoint defaults to unlimited token/credit quotas; set an appropriate server-side quota before regular use.**

## Setup
1. Deploy and operate **your own** WorkBuddy Manager + workbuddy2api service, with accounts you own or whose owners explicitly consent. Read and follow the provider's terms and account usage limits.
2. Provide HTTPS transport and authenticate the server. For same-device loopback, HTTP is permitted by the URL validator only; Android manifest policy and an on-device gateway still need to allow it.
3. In ETA > Providers > WorkBuddy Manager > Config, replace `https://workbuddy.example.invalid/v1` with your gateway HTTPS `.../v1` and save.
4. Use the WorkBuddy native tab with a scoped management token `wbt_`. It is **not saved to the Provider database** and will be cleared when that UI is disposed. The manager token must be created by an already-authorized WorkBuddy administrator; creation and high-risk settings remain server-side.
5. If you already have a `wbk_` gateway key, paste it in the ETA Provider settings. Otherwise create a dedicated key from the native tab, and import models from the Models tab.
6. Monitor gateway status and remaining credit in the native tab. These values are snapshots from the manager's own accounting, **not guarantees of third-party available credit**.

## Trust boundaries and limitations
- **This is native management + Agent integration, not a port of the whole backend.** Both WorkBuddy Manager and workbuddy2api still run outside the APK. ETA does not silently run an account pool, background sign-in, scheduler, key rotation or proxy server on your phone.
- `wbk_` authorizes model calls and is persisted using ETA's existing Provider store; `wbt_` authorizes the manager and is transient UI state, never copied into the Agent's model prompts, custom HTTP headers, or logs.
- Requests carrying secrets require HTTPS, except explicit loopback origins; redirects are disabled so an unexpected 302 cannot forward secrets to another host. Responses are bounded and credential values are not displayed in error summaries.
- This integration does not create a Tencent subscription, create credits, make paid quotas unlimited, or guarantee savings. Available capacity depends on account entitlements, credit rules, availability and platform policy. No mass registration, access-control bypass or circumvention is implemented.
- Multi-upstream management, direct key quota editing, account deletion, secure persistent management-token storage, offline failover and end-to-end instrumented Android testing are not yet implemented. These should be validated before treating the integration as production ready.
- ETA upstream currently uses PolyForm Noncommercial License 1.0.0; WorkBuddy Manager and workbuddy2api announce MIT. No WorkBuddy source code was vendored; only its public API was integrated.

## Regression checklist
- Existing Provider default, OAuth account login, model selection, Agent tool calls, SSE conversation and other built-in providers unchanged.
- On a configured WorkBuddy server: `/v1/models` and a Chat Completions streaming tool-call round trip should succeed with a `wbk_` key; verify independently.
- Wrong or expired `wbt_` must fail management only; wrong `wbk_` must fail data plane only.
- Manager unavailable should not affect selection or use of other Providers.
- Recomposition, screen navigation, app restart must not persist `wbt_`.
- Check domestic and international realm separately; international onboarding requires a valid region.
