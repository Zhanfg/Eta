package io.github.mangi.eta.data.workbuddy

import java.net.HttpURLConnection
import java.net.URL
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Explicit, credit-consuming end-to-end smoke test:
 * Android app -> native Go pool -> CodeBuddy -> Go SSE -> Android.
 * Does not log, expose or persist the user prompt, token or returned text.
 */
internal object WorkBuddyBytePathProbe {
    data class Outcome(
        val responseCode: Int,
        val sseEvents: Int,
        val visibleCharacters: Int,
        val firstTextMillis: Long?,
        val completed: Boolean,
    )

    internal fun parseSse(reader: java.io.BufferedReader, onFirstText: () -> Unit): Outcome {
        var events = 0
        var characters = 0
        var done = false
        var first: Long? = null
        val start = System.nanoTime()
        while (events < 2048) {
            val line = reader.readLine() ?: break
            if (!line.startsWith("data:")) continue
            val body = line.removePrefix("data:").trim()
            if (body == "[DONE]") { done = true; break }
            if (body.isBlank()) continue
            val event = try { JSONObject(body) } catch (_: Exception) { continue }
            if (event.has("error")) throw IllegalStateException("上游 SSE 返回模型错误")
            val delta = event.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
            val text = delta?.optString("content").orEmpty()
            if (text.isNotBlank()) {
                if (first == null) {
                    first = (System.nanoTime() - start) / 1_000_000
                    onFirstText()
                }
                characters += text.length
            }
            events++
        }
        return Outcome(200, events, characters, first, done)
    }

    suspend fun execute(): Outcome = withContext(Dispatchers.IO) {
        val endpoint = WorkBuddyEmbeddedCore.ensureStarted()
        val model = WorkBuddyLocalModels.fetch().firstOrNull()
            ?: throw IllegalStateException("账号池还未获得任何可用模型")
        val url = URL(endpoint.baseUrl + "/chat/completions")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 12000
        connection.readTimeout = 60000
        connection.instanceFollowRedirects = false
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer ${endpoint.apiKey}")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "text/event-stream")
        val body = JSONObject()
            .put("model", model.modelId)
            .put("stream", true)
            .put("max_tokens", 16)
            .put("messages", JSONArray().put(
                JSONObject().put("role", "user").put("content", "Please answer OK.")
            )).toString()
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("真实流式调用失败，HTTP $status；请检查账号授权、额度、模型权限和网络")
            }
            val type = connection.contentType.orEmpty()
            require(type.contains("text/event-stream", ignoreCase = true)) {
                "Go 核心未返回 SSE，实际类型为 ${type.take(40)}"
            }
            val result = connection.inputStream.bufferedReader(Charsets.UTF_8)
                .use { parseSse(it) {} }
            require(result.visibleCharacters > 0) { "已连接但未收到任何可见文本字节" }
            require(result.completed) { "返回了文本字节，但 SSE 流未正常完成" }
            result
        } finally {
            connection.disconnect()
        }
    }
}
