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
            if (listOf("nes-", "completion-", "codewise-").any { prefix -> low.startsWith(prefix) }) return null
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

    /** The authoritative catalog comes from the locally running Go pool. */
    suspend fun fetch(): List<Model> = withContext(Dispatchers.IO) {
        val endpoint = WorkBuddyEmbeddedCore.ensureStarted()
        val request = Request.Builder()
            .url(endpoint.baseUrl + "/models")
            .header("Authorization", "Bearer ${endpoint.apiKey}")
            .header("Accept", "application/json")
            .get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException(
                "本地 WB 核心模型查询失败（HTTP ${response.code}）"
            )
            val document = runCatching { JSONObject(response.peekBody(262_144).string()) }.getOrNull()
                ?: throw IllegalStateException("本地 WB 核心返回无效模型 JSON")
            val data = document.optJSONArray("data") ?: throw IllegalStateException(
                "本地 WB 核心模型列表为空，请检查订阅权限"
            )
            val models = LinkedHashMap<String, Model>()
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                if (id.isBlank() || id in models) continue
                val declaredTool = if (item.has("supports_tool_call")) {
                    item.optBoolean("supports_tool_call")
                } else null
                val declaredImage = if (item.has("supports_images")) {
                    item.optBoolean("supports_images")
                } else null
                val declaredReasoning = if (item.has("supports_reasoning")) {
                    item.optBoolean("supports_reasoning")
                } else null
                models[id] = Model(
                    id = UUID.nameUUIDFromBytes(("workbuddy:$id").toByteArray(Charsets.UTF_8)).toString(),
                    modelId = id,
                    displayName = item.optString("name").ifBlank { id },
                    source = ModelSource.REMOTE,
                    contextWindow = item.optInt("context_length", 0).takeIf { it > 0 },
                    toolCall = declaredTool,
                    attachment = declaredImage,
                    reasoning = declaredReasoning,
                )
            }
            require(models.isNotEmpty()) { "本地 WB 核心未返回任何可用模型" }
            models.values.toList()
        }
    }

}
