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


## 手机原生 JSON 导入（电脑登录 → 手机上直接导入）

此功能在 ETA 内完成，不调用浏览器上传页或 WebView。

1. 在电脑上通过有权访问的 WorkBuddy/CodeBuddy 账号登录，并导出**账号凭据 JSON**。原版 WorkBuddy Manager 在管理员账号页面 `/api/accounts/export` 导出的是 ZIP：请先解压后传输其中的 `.json`。也支持符合上述 `accounts` 多账号结构的 JSON，**不是任意 JavaScript 文件，也不是包含 API URL 的普通订阅配置**。
2. 将 JSON 通过可信的端到端途径传到手机。该文件包含真实 access/refresh tokens，避免用公开网盘或不受保护的聊天转发。
3. ETA → Providers → WorkBuddy Manager → WorkBuddy →「电脑登录 → 手机 JSON 导入」→「选择账号 JSON 文件」。
4. ETA 使用 Android SAF 文件选择器，最大 2 MiB，验证是否含 `uid/user_id` + `accessToken/access_token`，显示脱敏账号列表，不渲染原始 Token。
5. 输入**WorkBuddy Manager 服务端管理员用户名与密码**，确认目标 HTTPS Base URL，二次点击导入。ETA 临时调用 `POST /api/login`，使用服务端 Set-Cookie 的 `wb_session`，然后经 `multipart/form-data` 的 `files` 字段调用 `POST /api/accounts/upload`。普通管理 API Token `wbt_` **不具备此权限**。
6. 默认 `overwrite=false`；重复账号由服务端拒绝，绝不悄悄替换。显示新增、拒绝与失败的真实数量；随后使用同一次会话 `GET /api/accounts` 展示实时池中账号列表。
7. 密码、登录 Cookie 和文件原始字节不会存入 ETA 数据库、日志或 Preferences；会话 Cookie 不进入全局 CookieJar，完成请求后不保存在客户端。**服务端所产生的会话可能在其自身过期前仍有效**；此接口遵循服务端会话管理。

**限制**：当前需有可访问的 WorkBuddy Manager 后端；导入不是把 Python/Go 账号网关部署在 Android 本机。账号 JSON 能通过格式校验不代表账户登录有效、有积分或具备特定模型权益；以服务端实际状态及模型发现为准。

## 传输协议

WorkBuddy 新建 Provider **默认 OpenAI Responses**。在 Provider 的「配置 → API 协议」中可以手动选：
- **Responses**（默认）：`/v1/responses`；由 WorkBuddy 翻译到 Chat 网关
- **Anthropic Messages**：`/v1/messages`；ETA 使用现有 Anthropic Messages 客户端，仍由 WorkBuddy 网关翻译
- **Chat Completions**：`/v1/chat/completions`；作为旧兼容兜底

三者共享 ETA Agent 工具执行和会话存储。切换只是序列化/反序列化模式的变化，**不保证**上游原生推理状态、托管工具、提示缓存和 token 统计语义完整保留。已有配置的端点选择不被默认值覆盖。

### 验证项
- SAF 中断、无效 JSON、重复 UID、超 2 MiB、嵌套 auth/account、多个账号导入
- HTTPS 网关、登录非 admin、Cookie 缺失、401/403/429、服务端不支持上传的版本、网络断开
- 不发生明文账号密钥在 UI/日志/粘贴板外泄；没有 CookieJar 长期储存
- 上传成功与账号池刷新分开报告，不把 API 上传成功误判为模型可用
- 三协议消息和工具调用 SSE 回归，在有合法账号的测试后端上分别检查


## On-device WorkBuddy JSON mode (new; preferred for PC login → phone import)

> This section supersedes the earlier statement that a WorkBuddy Manager
> server is always required. **The separate managed gateway still does
> require a backend**, but the new built-in `WorkBuddy · 本地账号`
> operates directly on the Android device.

1. In ETA → Providers, open **WorkBuddy · 本地账号**, not **WorkBuddy Manager**.
2. Open **本地账号 / WorkBuddy**, choose the `.json` exported after logging in to your own CodeBuddy / WorkBuddy account on the computer, inspect the masked preview and confirm import.
3. The app normalizes nested `auth` + `account` and flat JSON, supports `{"accounts":[...]}` exports, and saves normalized credentials in an AES-256-GCM encrypted no-backup Android Keystore-backed private file. It does **not** require a Manager URL, administrator password, Docker, a self-hosted proxy, or a `wbk_` key.
4. Duplicate UIDs are updated **only after the explicit import confirmation**, so users can repeat the desktop export after token rotation.
5. ETA uses the local account to query the available model list from the provider's HTTPS endpoint and populates ETA's normal Model tab. Enable/select a model there before using the Agent.
6. The local runtime uses Chat Completions SSE because the underlying Tencent endpoint is Chat-shaped. The original ETA Agent tools, reasoning panel and conversation UI remain shared. The external Manager provider supports Responses/Messages/Chat bridges separately.

**Security boundaries:** Only known official Tencent HTTPS hosts are used for model calls and refresh; saved credentials never appear in ETA Provider API Key fields or model-configuration JSON. A Keystore key is tied to the Android installation; uninstalling the app typically destroys access to the encrypted vault. Keep an independently protected original export if restoration is needed. API keys or cookies from unrelated services cannot be imported as CodeBuddy account JSON.

**Validation still required:** This implementation was developed from inspectable third-party open-source API behavior; native on-device calling, token refresh and model discovery must be tested against an account you control. The upstream vendor can change its private interface and may impose usage restrictions. The local mode is not the entire Python manager/Go pool port: full advanced account-pool orchestration, scheduler, entitlement reconciliation and broad 3-protocol native support remain unavailable here.

**Potential installation blocker:** The PR CI produces a **debug-signed** package using ETA's production applicationId. The Debug APK can fail to install over a production APK signed with a different certificate (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). The project must not claim that this debug artifact is an in-place production update. Back up application data before any uninstall, or use a compatible signing/update channel.
