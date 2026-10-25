package io.github.mangi.eta.data.workbuddy

import io.github.mangi.eta.data.model.Model
import io.github.mangi.eta.data.model.ModelSource
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Lists models against Tencent using locally imported account credentials.
 * Never substitutes a fabricated static model list if upstream discovery fails.
 */
internal object WorkBuddyLocalModels {
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .callTimeout(20, TimeUnit.SECONDS).build()

    suspend fun fetch(): List<Model> = withContext(Dispatchers.IO) {
        val rawAccount = WorkBuddyLocalVault.select()
        val account = io.github.mangi.eta.agent.model.WorkBuddyDirectProvider.refreshIfNecessary(rawAccount)
        val global = account.realm == "global"
        val host = if (global) "https://www.workbuddy.ai" else "https://copilot.tencent.com"
        val origin = if (global) "https://www.workbuddy.ai" else "https://www.codebuddy.cn"
        val request = Request.Builder()
            .url("$host/console/enterprises/personal/models")
            .header("Authorization", "Bearer ${account.accessToken}")
            .header("Accept", "application/json")
            .header("Origin", origin)
            .header("Referer", "$origin/")
            .header("User-Agent", "CLI/2.63.2 CodeBuddy/2.63.2")
            .get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("读取 CodeBuddy 模型列表失败（HTTP ${response.code}）。请检查登录授权")
            val document = runCatching {
                JSONObject(response.peekBody(262_144).string())
            }.getOrNull() ?: error("模型列表响应不是 JSON")
            require(document.optInt("code", -1) == 0) { "CodeBuddy 返回业务错误，建议重新登录" }
            val array = document.optJSONObject("data")?.optJSONArray("models")
                ?: error("CodeBuddy 未返回可用模型")
            val models = mutableListOf<Model>()
            val visited = HashSet<String>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                if (id.isEmpty() || !visited.add(id)) continue
                models += Model(
                    id = UUID.randomUUID().toString(),
                    modelId = id,
                    displayName = item.optString("name").ifBlank { id },
                    source = ModelSource.REMOTE,
                    toolCall = true,
                    sortOrder = models.size,
                )
            }
            require(models.isNotEmpty()) { "CodeBuddy 返回空模型目录：账号可能尚未获得访问权限" }
            models
        }
    }
}
