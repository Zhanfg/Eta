package io.github.mangi.eta.data.workbuddy

import io.github.mangi.eta.data.model.ProviderSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Native, protocol-level WorkBuddy Manager integration.
 *
 * The data-plane key (wbk_) belongs to ProviderSetting; the management-plane
 * token (wbt_) is supplied by the user for the current screen only.
 * No Tencent credentials or session cookies are stored here.
 */
internal object WorkBuddyGateway {
    private const val MAX_REPLY_BYTES = 131_072L
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    data class Allowance(val used: Long, val limit: Long?, val remaining: Long?) {
        fun summary(): String = if (limit == null) "$used / 不限" else "$used / $limit (剩余 ${remaining ?: 0})"
    }

    data class Usage(
        val keyName: String,
        val enabled: Boolean,
        val expired: Boolean,
        val realm: String,
        val tokens: Allowance,
        val credits: Allowance,
        val snapshotAt: Long,
    )

    data class Account(
        val nickname: String,
        val online: Boolean?,
        val checkinToday: Boolean,
    )

    data class Pool(
        val total: Int,
        val poolAvailable: Boolean,
        val poolSynced: Int,
        val accounts: List<Account>,
    )

    data class Authorization(val state: String, val url: String, val realm: String)
    data class Poll(val status: String, val message: String?)

    /** Only exact HTTPS origins or explicit device loopback may receive credentials. */
    internal fun validatedBase(baseUrl: String): HttpUrl {
        val url = baseUrl.trim().trimEnd('/').toHttpUrlOrNull()
            ?: throw IllegalArgumentException("请配置有效的 WorkBuddy HTTPS 网关地址")
        require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
            "网关地址不能包含用户信息、查询参数或片段"
        }
        require(url.encodedPath.trimEnd('/').endsWith("/v1")) {
            "WorkBuddy Base URL 必须以 /v1 结尾"
        }
        val loopback = url.host in setOf("localhost", "127.0.0.1", "::1", "[::1]")
        require(url.scheme == "https" || (url.scheme == "http" && loopback)) {
            "为了保护账号及密钥，非本机地址必须使用 HTTPS"
        }
        return url
    }

    private fun endpoint(base: HttpUrl, path: String): HttpUrl {
        val prefix = base.encodedPath.trimEnd('/').removeSuffix("/v1")
        return base.newBuilder().encodedPath("$prefix/$path").build()
    }

    private suspend fun fetch(
        url: HttpUrl,
        token: String? = null,
        method: String = "GET",
        payload: JsonObject? = null,
    ): JsonObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .method(method, payload?.toString()?.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            // No redirect following, no response body or credentials in error messages.
            if (!response.isSuccessful) throw IllegalStateException("WorkBuddy 请求失败：HTTP ${response.code}")
            val bytes = response.peekBody(MAX_REPLY_BYTES + 1).bytes()
            require(bytes.size <= MAX_REPLY_BYTES) { "WorkBuddy 返回数据过大" }
            (json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject)
                ?: throw IllegalStateException("WorkBuddy 返回格式无效")
        }
    }

    private fun JsonObject.str(name: String): String =
        (this[name] as? JsonPrimitive)?.content.orEmpty()
    private fun JsonObject.flag(name: String): Boolean =
        (this[name] as? JsonPrimitive)?.content == "true"
    private fun JsonObject.number(name: String): Long =
        str(name).toLongOrNull() ?: 0L
    private fun JsonObject.obj(name: String): JsonObject =
        this[name] as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonObject.allowance(name: String): Allowance {
        val data = obj(name)
        return Allowance(
            used = data.number("used"),
            limit = data.str("limit").toLongOrNull(),
            remaining = data.str("remaining").toLongOrNull(),
        )
    }

    private fun requireGatewayKey(key: String): String =
        key.trim().also { require(it.startsWith("wbk_")) { "请先在 Provider 配置中填写 wbk_ 网关调用密钥并保存" } }
    private fun requireManagementToken(token: String): String =
        token.trim().also { require(it.startsWith("wbt_")) { "需要 wbt_ 管理令牌；不要填写 wbk_ 模型调用密钥" } }

    suspend fun usage(provider: ProviderSetting): Usage {
        val data = fetch(endpoint(validatedBase(provider.baseUrl), "v1/usage"), requireGatewayKey(provider.apiKey))
        val key = data.obj("key")
        return Usage(
            keyName = key.str("name"),
            enabled = key.flag("enabled"),
            expired = key.flag("expired"),
            realm = key.str("realm"),
            tokens = data.allowance("tokens"),
            credits = data.allowance("credits"),
            snapshotAt = data.number("snapshot_at"),
        )
    }

    suspend fun health(baseUrl: String): Boolean =
        fetch(endpoint(validatedBase(baseUrl), "healthz")).flag("upstream_ok")

    suspend fun accounts(baseUrl: String, managementToken: String): Pool {
        val data = fetch(endpoint(validatedBase(baseUrl), "api/accounts"), requireManagementToken(managementToken))
        val accounts = (data["accounts"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { el ->
            val a = el as? JsonObject ?: return@mapNotNull null
            Account(
                nickname = a.str("nickname").ifBlank { a.str("note").ifBlank { "未命名账号" } },
                online = (a["healthy"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull(),
                checkinToday = a["checkin_today"] != null && a["checkin_today"].toString() != "null",
            )
        }
        return Pool(data.number("total").toInt(), data.flag("pool_available"), data.number("pool_synced").toInt(), accounts)
    }

    suspend fun startAuthorization(
        baseUrl: String,
        managementToken: String,
        realm: String,
        region: String,
    ): Authorization {
        require(realm in setOf("cn", "global")) { "未知的账号区域" }
        val data = fetch(
            endpoint(validatedBase(baseUrl), "api/auth/start"),
            requireManagementToken(managementToken),
            method = "POST",
            payload = buildJsonObject {
                put("realm", realm)
                if (region.isNotBlank()) put("region", region.trim().uppercase())
            },
        )
        val state = data.str("state")
        val url = data.str("authUrl")
        require(state.isNotBlank() && url.startsWith("https://")) { "未获得有效的授权链接" }
        return Authorization(state, url, realm)
    }

    suspend fun pollAuthorization(
        baseUrl: String,
        managementToken: String,
        authorization: Authorization,
        region: String,
    ): Poll {
        val url = endpoint(validatedBase(baseUrl), "api/auth/poll").newBuilder()
            .addQueryParameter("state", authorization.state)
            .addQueryParameter("realm", authorization.realm)
            .apply { if (region.isNotBlank()) addQueryParameter("region", region.trim().uppercase()) }
            .build()
        val data = fetch(url, requireManagementToken(managementToken))
        return Poll(data.str("status"), data.str("message").ifBlank { null })
    }

    /** Creates an independent inference key. Callers must save it once; the server only stores a hash. */
    suspend fun createInferenceKey(
        baseUrl: String,
        managementToken: String,
        realm: String,
    ): String {
        require(realm in setOf("cn", "global"))
        val data = fetch(
            endpoint(validatedBase(baseUrl), "api/keys"),
            requireManagementToken(managementToken),
            method = "POST",
            payload = buildJsonObject {
                put("name", "Eta Android")
                put("realm", realm)
            },
        )
        return data.str("key").also { require(it.startsWith("wbk_")) { "服务端未返回可用的调用密钥" } }
    }
}
