package io.github.mangi.eta.data.workbuddy

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Import WorkBuddy-authenticated JSON exported on the user's computer.
 * Files are selected using Android SAF, read into temporary memory and posted
 * only to the user-configured HTTPS WorkBuddy Manager after session-admin login.
 * Neither Tencent credentials nor admin cookie are persisted by this class.
 */
internal object WorkBuddyAccountImport {
    private const val MAX_FILE_SIZE = 2 * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .callTimeout(40, TimeUnit.SECONDS).build()

    data class AccountPreview(val account: String, val nickname: String, val realm: String)
    data class Preview(val count: Int, val accounts: List<AccountPreview>)
    data class Result(
        val added: Int, val updated: Int, val rejected: Int, val failed: Int,
        val livePoolTotal: Int? = null,
        val liveAccounts: List<AccountPreview> = emptyList(),
    ) {
        val imported get() = added + updated
    }

    private fun JsonObject.str(name: String): String =
        (this[name] as? JsonPrimitive)?.content.orEmpty()

    private fun attribute(raw: JsonObject, vararg names: String): String {
        val account = raw["account"] as? JsonObject
        val auth = raw["auth"] as? JsonObject
        for (obj in listOfNotNull(auth, account, raw)) {
            for (name in names) {
                val value = obj.str(name)
                if (value.isNotBlank()) return value
            }
        }
        return ""
    }

    internal fun parsePreview(bytes: ByteArray): Preview {
        require(bytes.isNotEmpty() && bytes.size <= MAX_FILE_SIZE) { "请选择不超过 2 MB 的 JSON 文件" }
        val root = try {
            json.parseToJsonElement(bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF"))
        } catch (_: Exception) {
            throw IllegalArgumentException("文件不是有效的 JSON")
        }
        val entries = when (root) {
            is JsonObject -> (root["accounts"] as? JsonArray)?.toList() ?: listOf(root)
            is JsonArray -> root.toList()
            else -> throw IllegalArgumentException("不支持此订阅结构；需要 WorkBuddy 账号 JSON")
        }
        require(entries.isNotEmpty() && entries.size <= 200) { "账号数量必须在 1–200 之间" }
        val seen = HashSet<String>()
        return Preview(
            count = entries.size,
            accounts = entries.map { entry ->
                val obj = entry as? JsonObject ?: throw IllegalArgumentException("账号列表存在无效对象")
                val uid = attribute(obj, "uid", "user_id")
                val token = attribute(obj, "accessToken", "access_token")
                require(uid.isNotBlank() && token.isNotBlank()) {
                    "账号文件缺少 uid 或 accessToken；无法导入为可用订阅"
                }
                require(seen.add(uid)) { "文件中存在重复账号，请先整理" }
                val name = attribute(obj, "nickname", "name")
                    .replace(Regex("[\\p{Cntrl}]"), "").take(48)
                AccountPreview(
                    account = if (uid.length <= 6) "••••" else uid.take(3) + "•••" + uid.takeLast(3),
                    nickname = name.ifBlank { "未命名账号" },
                    realm = attribute(obj, "realm").ifBlank { "自动识别" }.take(24),
                )
            },
        )
    }

    private fun readBounded(context: Context, uri: Uri): ByteArray {
        val source = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("无法读取选中的文件")
        return source.use { input ->
            val output = ByteArrayOutputStream()
            val block = ByteArray(8192)
            while (true) {
                val count = input.read(block)
                if (count < 0) break
                require(output.size() + count <= MAX_FILE_SIZE) { "JSON 文件大小超过 2 MB" }
                output.write(block, 0, count)
            }
            output.toByteArray()
        }
    }

    suspend fun preview(context: Context, uri: Uri): Preview = withContext(Dispatchers.IO) {
        parsePreview(readBounded(context, uri))
    }

    suspend fun importAccounts(
        context: Context,
        uri: Uri,
        baseUrl: String,
        username: String,
        password: String,
    ): Result = withContext(Dispatchers.IO) {
        require(username.isNotBlank() && password.isNotBlank()) { "请输入 WorkBuddy Manager 管理员账号和密码" }
        val bytes = readBounded(context, uri)
        parsePreview(bytes) // Fail closed before sending any credentials.
        val origin = WorkBuddyGateway.validatedBase(baseUrl)
            .newBuilder().encodedPath(
                WorkBuddyGateway.validatedBase(baseUrl).encodedPath.trimEnd('/').removeSuffix("/v1") + "/"
            ).build()
        val loginUrl = origin.newBuilder().addPathSegments("api/login").build()
        val uploadUrl = origin.newBuilder().addPathSegments("api/accounts/upload").build()
        val loginBody = buildJsonObject {
            put("username", username.trim())
            put("password", password)
        }.toString().toRequestBody("application/json".toMediaType())
        val session = client.newCall(
            Request.Builder().url(loginUrl).post(loginBody).build()
        ).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException(
                "管理员登录失败（HTTP ${response.code}），请检查账号或服务端限制"
            )
            val cookie = Cookie.parseAll(loginUrl, response.headers).firstOrNull {
                it.name == "wb_session" && it.matches(uploadUrl) && it.httpOnly
            } ?: throw IllegalStateException("登录成功，但 WorkBuddy 未返回有效的管理员会话")
            // Response bodies contain no secrets in normal operation; never expose raw JSON.
            val loginJson = response.body?.string()?.let {
                json.parseToJsonElement(it) as? JsonObject
            } ?: throw IllegalStateException("服务器登录响应无效")
            require(loginJson.str("role") == "admin") { "此账号不是管理员，无法导入账号 JSON" }
            cookie.name + "=" + cookie.value
        }
        val fileBody = bytes.toRequestBody("application/json".toMediaType())
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("files", "eta-workbuddy-import.json", fileBody).build()
            val imported = client.newCall(
                Request.Builder().url(uploadUrl)
                    .header("Cookie", session)
                    .header("Accept", "application/json")
                    .post(form).build()
            ).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException(
                    "账号导入失败（HTTP ${response.code}）。可能需要管理员会话或上游授权"
                )
                val body = response.body?.string()?.let {
                    json.parseToJsonElement(it) as? JsonObject
                } ?: throw IllegalStateException("服务器未返回有效导入结果")
                fun tally(field: String): Int = (body[field] as? JsonArray)?.size ?: 0
                Result(
                    added = tally("added"),
                    updated = tally("overwritten"),
                    rejected = tally("rejected"),
                    failed = tally("failed"),
                )
            }
            // Refresh via the *same* ephemeral session. This works even when the
            // phone has no separately configured wbt_ management API token.
            // Upload outcome is still returned if the optional list read fails.
            runCatching {
                val listUrl = origin.newBuilder().addPathSegments("api/accounts").build()
                client.newCall(Request.Builder().url(listUrl).header("Cookie", session).get().build())
                    .execute().use { response ->
                        if (!response.isSuccessful) return@runCatching imported
                        val body = response.body?.string()?.let {
                            json.parseToJsonElement(it) as? JsonObject
                        } ?: return@runCatching imported
                        val entries = (body["accounts"] as? JsonArray).orEmpty()
                        imported.copy(
                            livePoolTotal = body.str("total").toIntOrNull(),
                            liveAccounts = entries.take(30).mapNotNull { entry ->
                                val account = entry as? JsonObject ?: return@mapNotNull null
                                val uid = account.str("uid")
                                AccountPreview(
                                    account = if (uid.length <= 6) "••••" else uid.take(3) + "•••" + uid.takeLast(3),
                                    nickname = account.str("nickname").ifBlank { "未命名账号" }.take(48),
                                    realm = account.str("realm").ifBlank { "自动识别" }.take(24),
                                )
                            },
                        )
                    }
            }.getOrDefault(imported)
        // Session exists only as a local string for this call. No cookie jar.
    }
}
