package io.github.mangi.eta.data.workbuddy

import io.github.mangi.eta.agent.model.WorkBuddyDirectProvider
import io.github.mangi.eta.data.model.Model
import io.github.mangi.eta.data.model.ModelSource
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** Native model discovery: merge official enterprise catalog with /v3/config.
 * The first successful domain does not hide models available from the other.
 */
internal object WorkBuddyLocalModels {
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .callTimeout(20, TimeUnit.SECONDS).build()

    private fun fetchResponse(
        host: String,
        path: String,
        account: WorkBuddyLocalVault.Account,
    ): Any? {
        val global = account.realm == "global"
        val origin = if (global) "https://www.workbuddy.ai" else "https://www.codebuddy.cn"
        val request = Request.Builder().url(host + path)
            .header("Authorization", "Bearer ${account.accessToken}")
            .header("Accept", "application/json")
            .header("Accept-Language", if (global) "en-US" else "zh-CN")
            .header("X-CodeBuddy-Request", "1")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Origin", origin).header("Referer", "$origin/")
            .header("User-Agent", "CLI/2.63.2 CodeBuddy/2.63.2")
            .apply { if (account.uid.isNotBlank()) header("X-User-Id", account.uid) }
            .get().build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val document = runCatching { JSONObject(response.peekBody(262_144).string()) }.getOrNull()
                ?: return@use null
            if (document.has("code") && document.optInt("code", -1) != 0) return@use null
            document.opt("data")?.takeUnless { it == JSONObject.NULL } ?: document
        }
    }

    /** Parses real, declared capabilities; never mark every model tool capable. */
    internal fun parseCatalog(data: Any?, global: Boolean, v3: Boolean): List<Model> {
        if (data is JSONArray) {
            val out = mutableListOf<Model>()
            for (i in 0 until data.length()) {
                when (val item = data.opt(i)) {
                    is String -> if (item.isNotBlank()) out += Model(
                        id = UUID.randomUUID().toString(), modelId = item.trim(),
                        displayName = item.trim(), source = ModelSource.REMOTE
                    )
                    is JSONObject -> out += parseModel(item, global) ?: continue
                }
            }
            return out
        }
        if (data !is JSONObject) return emptyList()
        val list = data.optJSONArray("models") ?: return emptyList()
        val allowed = if (!global && !v3) {
            val agents = data.optJSONArray("agents")
            var cliModels: Set<String>? = null
            if (agents != null) {
                for (i in 0 until agents.length()) {
                    val agent = agents.optJSONObject(i) ?: continue
                    if (agent.optString("name") != "cli") continue
                    val values = agent.optJSONArray("models") ?: break
                    cliModels = (0 until values.length()).mapNotNull {
                        values.optString(it).takeIf(String::isNotBlank)
                    }.toSet()
                    break
                }
            }
            cliModels
        } else null
        val out = mutableListOf<Model>()
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            val mid = item.optString("id").trim()
            if (allowed != null && mid !in allowed) continue
            parseModel(item, global)?.let(out::add)
        }
        return out
    }

    private fun parseModel(item: JSONObject, global: Boolean): Model? {
        val id = item.optString("id").trim()
        if (id.isBlank() || item.optBoolean("disabled")) return null
        val low = id.lowercase()
        if (!global) {
            if (listOf("nes-", "completion-", "codewise-").any(low::startsWith)) return null
            val maxOutput = item.optInt("maxOutputTokens", 0)
            if (maxOutput in 1..256) return null
            val tags = item.optJSONArray("tags")
            if (tags != null && (0 until tags.length()).any { tags.optString(it) == "text-to-image" }) return null
        }
        return Model(
            id = UUID.randomUUID().toString(),
            modelId = id,
            displayName = item.optString("name").ifBlank { id },
            source = ModelSource.REMOTE,
            contextWindow = item.optInt("maxInputTokens", 0).takeIf { it > 0 },
            toolCall = if (item.has("supportsToolCall")) item.optBoolean("supportsToolCall") else null,
            reasoning = if (item.has("supportsReasoning")) item.optBoolean("supportsReasoning") else null,
            attachment = if (item.has("supportsImages")) item.optBoolean("supportsImages") else null,
        )
    }

    suspend fun fetch(): List<Model> = withContext(Dispatchers.IO) {
        val account = WorkBuddyDirectProvider.refreshIfNecessary(WorkBuddyLocalVault.select())
        val global = account.realm == "global"
        val host = if (global) "https://www.workbuddy.ai" else "https://copilot.tencent.com"
        val enterprisePaths = if (global) listOf(
            "/v2/enterprises/personal/models", "/console/enterprises/personal/models"
        ) else listOf("/console/enterprises/personal/models")
        // Try enterprise endpoints in priority order. An empty catalog is not success.
        var enterprise: List<Model> = emptyList()
        for (path in enterprisePaths) {
            val response = runCatching { fetchResponse(host, path, account) }.getOrNull()
            enterprise = parseCatalog(response, global, v3 = false)
            if (enterprise.isNotEmpty()) break
        }
        val v3 = parseCatalog(
            runCatching { fetchResponse(host, "/v3/config", account) }.getOrNull(), global, v3 = true
        )
        val unique = LinkedHashMap<String, Model>()
        (v3 + enterprise).forEach { unique.putIfAbsent(it.modelId, it) }
        require(unique.isNotEmpty()) {
            "无法获取 CodeBuddy 模型目录：请检查账号的登录状态、CN/Global 区域及实际模型权益"
        }
        unique.values.toList()
    }
}
