package io.github.mangi.eta.data.workbuddy

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Direct views of the packaged Go backend's authenticated /status endpoint. */
internal object WorkBuddyCoreDashboard {
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
            val budget = root.optJSONObject("daily_budget")
            val realms = root.optJSONObject("realm_totals")
            Summary(
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
            )
        } finally { connection.disconnect() }
    }
}
