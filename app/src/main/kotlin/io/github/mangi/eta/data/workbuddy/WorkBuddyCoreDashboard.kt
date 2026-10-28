package io.github.mangi.eta.data.workbuddy

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray

/** Direct views of the packaged Go backend's authenticated /status endpoint. */
internal object WorkBuddyCoreDashboard {
    data class AccountRow(
        val nickname: String,
        val maskedId: String,
        val realm: String,
        val creditsRecorded: Long,
        val cooling: Boolean,
        val coolingSeconds: Long,
        val disabled: Boolean,
        val manualDisabled: Boolean,
        val needsRelogin: Boolean,
        val successCount: Long,
    ) {
        val state: String get() = when {
            needsRelogin -> "授权失效，需要电脑重新登录"
            manualDisabled -> "手动暂停"
            disabled -> "不可用（已禁用）"
            cooling -> "冷却中（剩余 ${coolingSeconds} 秒）"
            else -> "可参与调度"
        }
    }

    data class Summary(
        val total: Int,
        val healthy: Int,
        val cooling: Int,
        val disabled: Int,
        val inFlightFull: Int,
        val stickySessions: Int,
        val budgetUsed: Double,
        val budgetLimit: Double,
        val cnTotal: Int,
        val globalTotal: Int,
        val accounts: List<AccountRow> = emptyList(),
    )

    suspend fun load(): Summary = withContext(Dispatchers.IO) {
        val endpoint = WorkBuddyEmbeddedCore.ensureStarted()
        val url = endpoint.baseUrl.removeSuffix("/v1") + "/status"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 3000
        connection.readTimeout = 6000
        connection.setRequestProperty("Authorization", "Bearer ${endpoint.apiKey}")
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (connection.responseCode != 200) throw IllegalStateException(
                "WB 原生核心状态查询失败（HTTP ${connection.responseCode}）"
            )
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = try { JSONObject(body) } catch (_: Exception) {
                throw IllegalStateException("WB 原生核心返回无效状态数据")
            }
            parseSummary(root)
        } finally { connection.disconnect() }
    }

    /**
     * Whitelist status fields. Never pass a raw Go response (or auth content)
     * directly to Compose, logs, clipboard or a diagnostic report.
     */
    internal fun parseSummary(root: JSONObject): Summary {
        val budget = root.optJSONObject("daily_budget")
        val realms = root.optJSONObject("realm_totals")
        val accountItems = root.optJSONArray("accounts") ?: JSONArray()
        val accounts = (0 until minOf(accountItems.length(), 200)).mapNotNull { i ->
            val item = accountItems.optJSONObject(i) ?: return@mapNotNull null
            val uid = item.optString("uid")
            AccountRow(
                nickname = item.optString("nickname").take(60).ifBlank { "未命名账号" },
                maskedId = if (uid.length > 6) uid.take(3) + "•••" + uid.takeLast(3) else "••••",
                realm = item.optString("realm").take(20),
                creditsRecorded = item.optLong("credits"),
                cooling = item.optBoolean("cooling"),
                coolingSeconds = item.optLong("cool_remaining_sec").coerceAtLeast(0),
                disabled = item.optBoolean("disabled"),
                manualDisabled = item.optBoolean("manual_disabled"),
                needsRelogin = item.optBoolean("needs_relogin"),
                successCount = item.optLong("success_count"),
            )
        }
        return Summary(
                total = root.optInt("total"),
                healthy = root.optInt("healthy"),
                cooling = root.optInt("cooling"),
                disabled = root.optInt("disabled"),
                inFlightFull = root.optInt("in_flight_full"),
                stickySessions = root.optInt("sticky_sessions"),
                budgetUsed = budget?.optDouble("used") ?: 0.0,
                budgetLimit = budget?.optDouble("limit") ?: 0.0,
                cnTotal = realms?.optJSONObject("cn")?.optInt("total") ?: 0,
                globalTotal = realms?.optJSONObject("global")?.optInt("total") ?: 0,
                accounts = accounts,
            )
    }
}
