package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.runtime.AgentRunController
import io.github.mangi.eta.data.model.CustomHeader
import io.github.mangi.eta.data.model.OpenAiEndpointMode
import io.github.mangi.eta.data.model.ProviderSourceTypes
import io.github.mangi.eta.data.workbuddy.WorkBuddyLocalVault
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Direct on-device CodeBuddy inference. No Python manager, Docker, VPS,
 * background localhost HTTP daemon, or `wbk_` gateway key.
 *
 * Selected JSON credentials come from Android Keystore storage, and only the
 * actual upstream CodeBuddy endpoint receives their access token.
 */
internal object WorkBuddyDirectProvider : AgentProviderClient {
    override val id = "workbuddy_direct"
    override val capabilities: ProviderCapabilities = OpenAiChatCompletionsProvider.capabilities

    private val refreshClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .callTimeout(20, TimeUnit.SECONDS).build()

    private fun host(account: WorkBuddyLocalVault.Account): String =
        if (account.realm == "global") "https://www.workbuddy.ai"
        else "https://copilot.tencent.com"

    private fun origin(account: WorkBuddyLocalVault.Account): String =
        if (account.realm == "global") "https://www.workbuddy.ai" else "https://www.codebuddy.cn"

    private fun refreshIfNecessary(input: WorkBuddyLocalVault.Account): WorkBuddyLocalVault.Account {
        val now = System.currentTimeMillis() / 1000
        if (input.expiresAt <= 0 || now + 600 < input.expiresAt) return input
        require(input.refreshToken.isNotBlank()) { "CodeBuddy 账号已过期且缺少刷新凭据，请重新导入 JSON" }
        val endpoint = host(input) + "/v2/plugin/auth/token/refresh"
        val headers = Request.Builder().url(endpoint)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/plain, */*")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Origin", origin(input))
            .header("Referer", origin(input) + "/")
            .header("User-Agent", "CLI/2.63.2 CodeBuddy/2.63.2")
            .header("X-Refresh-Token", input.refreshToken)
            .header("X-Auth-Refresh-Source", "workbuddy")
            .apply {
                if (input.enterpriseId.isNotBlank()) header("X-Enterprise-Id", input.enterpriseId)
            }.post(okhttp3.RequestBody.create(null, ByteArray(0))).build()
        val payload = refreshClient.newCall(headers).execute().use { res ->
            if (!res.isSuccessful) throw IllegalStateException("CodeBuddy 账号刷新失败：HTTP ${res.code}，请在电脑重新登录")
            val reply = res.peekBody(65_536).string()
            val parsed = runCatching { JSONObject(reply) }.getOrNull()
                ?: throw IllegalStateException("CodeBuddy 账号刷新响应无效")
            require(parsed.optInt("code", -1) == 0) { "CodeBuddy 账号刷新被拒绝，请在电脑重新登录" }
            parsed.optJSONObject("data") ?: throw IllegalStateException("CodeBuddy 账号刷新未返回凭据")
        }
        val newToken = payload.optString("accessToken")
        require(newToken.isNotBlank()) { "CodeBuddy 刷新未返回 Access Token" }
        val next = input.copy(
            accessToken = newToken,
            refreshToken = payload.optString("refreshToken").ifBlank { input.refreshToken },
            expiresAt = payload.optLong("expiresIn").takeIf { it > 0 }?.let { now + it } ?: input.expiresAt,
            domain = payload.optString("domain").ifBlank { input.domain },
        )
        WorkBuddyLocalVault.update(next)
        return next
    }

    override fun complete(
        request: ProviderRequest,
        runController: AgentRunController,
        onEvent: (ProviderEvent) -> Unit,
    ): ProviderResponse {
        runController.throwIfCancelled()
        val acct = refreshIfNecessary(WorkBuddyLocalVault.select())
        val headers = mutableListOf(
            CustomHeader("X-Product", "SaaS"),
            CustomHeader("X-Requested-With", "XMLHttpRequest"),
        )
        if (acct.uid.isNotBlank()) headers += CustomHeader("X-User-Id", acct.uid)
        else headers += CustomHeader("X-No-User-Id", "1")
        if (acct.enterpriseId.isNotBlank()) headers += CustomHeader("X-Enterprise-Id", acct.enterpriseId)
        else headers += CustomHeader("X-No-Enterprise-Id", "1")
        if (acct.domain.isNotBlank()) headers += CustomHeader("X-Domain", acct.domain)
        else headers += CustomHeader("X-No-Department-Info", "1")
        val replacement = request.config.copy(
            apiKey = acct.accessToken,
            baseUrl = host(acct) + "/v2",
            providerSourceType = ProviderSourceTypes.WORKBUDDY_LOCAL,
            openAiEndpointMode = OpenAiEndpointMode.CHAT_COMPLETIONS,
            hostedWebSearchEnabled = false,
            customHeaders = headers,
        )
        return OpenAiChatCompletionsProvider.complete(
            request.copy(config = replacement), runController, onEvent,
        )
    }
}
