package io.github.mangi.eta.data.workbuddy

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Local, on-device credentials: no external WorkBuddy Manager installation is
 * required. Android Keystore AES-GCM protects the file in noBackupFilesDir.
 * This vault is deliberately not connected to Xposed preferences or logs.
 */
internal object WorkBuddyLocalVault {
    private const val ALIAS = "eta.workbuddy.local.v1"
    private const val FILE = "workbuddy-accounts-v1.bin"
    private const val LIMIT = 2 * 1024 * 1024
    private val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val nextIndex = AtomicInteger()
    private val lock = Any()
    @Volatile private var context: Context? = null

    @Serializable
    data class Account(
        val uid: String,
        val accessToken: String,
        val refreshToken: String = "",
        val expiresAt: Long = 0,
        val domain: String = "",
        val enterpriseId: String = "",
        val nickname: String = "",
        val realmHint: String = "",
    ) {
        val realm: String get() = when {
            realmHint.equals("global", ignoreCase = true) -> "global"
            realmHint.equals("cn", ignoreCase = true) -> "cn"
            domain.equals("workbuddy.ai", ignoreCase = true) ||
                domain.endsWith(".workbuddy.ai", ignoreCase = true) -> "global"
            else -> "cn"
        }
        fun maskedId(): String = if (uid.length <= 6) "••••" else uid.take(3) + "•••" + uid.takeLast(3)
    }
    data class AccountSummary(val name: String, val maskedId: String, val realm: String)
    data class ImportResult(val added: Int, val refreshed: Int, val total: Int)

    fun init(applicationContext: Context) { context = applicationContext.applicationContext }
    private fun root(): Context = checkNotNull(context) { "本地账号库尚未初始化，请重启 ETA" }
    private fun path() = File(root().noBackupFilesDir, FILE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }
    private fun JsonObject.str(name: String): String = (this[name] as? JsonPrimitive)?.content.orEmpty()
    private fun JsonObject.num(name: String): Long = str(name).toLongOrNull() ?: 0

    private fun parse(bytes: ByteArray): List<Account> {
        WorkBuddyAccountImport.parsePreview(bytes)
        val value = codec.parseToJsonElement(bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF"))
        val sources = when (value) {
            is JsonArray -> value.toList()
            is JsonObject -> (value["accounts"] as? JsonArray)?.toList() ?: listOf(value)
            else -> emptyList()
        }
        fun field(v: JsonObject, vararg keys: String): String {
            val auth = v["auth"] as? JsonObject
            val account = v["account"] as? JsonObject
            for (part in listOfNotNull(auth, account, v)) {
                for (name in keys) part.str(name).takeIf { it.isNotBlank() }?.let { return it }
            }
            return ""
        }
        return sources.map { item ->
            val v = item as JsonObject
            Account(
                uid = field(v, "uid", "user_id"),
                accessToken = field(v, "accessToken", "access_token"),
                refreshToken = field(v, "refreshToken", "refresh_token"),
                expiresAt = field(v, "expiresAt", "expires_at").toLongOrNull()
                    ?: field(v, "expiresIn", "expires_in").toLongOrNull()?.let {
                        System.currentTimeMillis() / 1000 + it
                    } ?: 0,
                domain = field(v, "domain"),
                enterpriseId = field(v, "enterpriseId", "enterprise_id"),
                nickname = field(v, "nickname", "name").take(80),
                realmHint = field(v, "realm").take(16),
            )
        }
    }

    private fun read(): MutableList<Account> {
        val file = path()
        if (!file.exists()) return mutableListOf()
        val data = file.readBytes()
        require(data.size > 16 && data.size < LIMIT * 2) { "本地账号库文件损坏" }
        val nonce = data.copyOfRange(0, 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, nonce))
        }
        return codec.decodeFromString<List<Account>>(cipher.doFinal(data, 12, data.size - 12).toString(Charsets.UTF_8)).toMutableList()
    }

    private fun write(accounts: List<Account>) {
        require(accounts.size <= 200) { "本地账号数量超过上限" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key())
        }
        val plaintext = codec.encodeToString(accounts).toByteArray()
        val encoded = cipher.iv + cipher.doFinal(plaintext)
        val target = path()
        val tmp = File(target.parentFile, "$FILE.tmp")
        tmp.outputStream().use { it.write(encoded); it.flush() }
        check(tmp.renameTo(target)) { "保存本地账号失败" }
    }

    // No getters expose bearer tokens outside the provider runtime.
    /** Only the on-device embedded runtime may serialize these into the private Go auths directory. */
    fun snapshotForCore(): List<Account> = synchronized(lock) { read().toList() }

    /**
     * Import refreshed access tokens from the Go core after graceful shutdown.
     * Files are never exposed via Android's exported providers.
     */
    fun ingestCoreRefreshes(directory: File): Int = synchronized(lock) {
        val known = read()
        if (known.isEmpty() || !directory.isDirectory) return@synchronized 0
        val byUid = known.withIndex().associate { it.value.uid to it.index }
        var count = 0
        directory.listFiles()?.filter { it.isFile && it.name.startsWith("workbuddy-") && it.extension == "json" }
            ?.take(200)?.forEach { file ->
                try {
                    if (file.length() > 65_536) return@forEach
                    val raw = org.json.JSONObject(file.readText(Charsets.UTF_8))
                    val auth = raw.optJSONObject("auth") ?: raw
                    val account = raw.optJSONObject("account") ?: raw
                    val uid = account.optString("uid")
                    val index = byUid[uid] ?: return@forEach
                    val access = auth.optString("accessToken")
                    if (access.isBlank()) return@forEach
                    val previous = known[index]
                    val refreshed = previous.copy(
                        accessToken = access,
                        refreshToken = auth.optString("refreshToken").ifBlank { previous.refreshToken },
                        expiresAt = auth.optLong("expiresAt").takeIf { it > 0 } ?: previous.expiresAt,
                        domain = auth.optString("domain").ifBlank { previous.domain },
                    )
                    if (refreshed != previous) {
                        known[index] = refreshed
                        count++
                    }
                } catch (_: Exception) {
                    // Corrupted individual Go account files do not destroy a valid vault.
                }
            }
        if (count > 0) write(known)
        count
    }

    fun hasAccounts(): Boolean = synchronized(lock) { read().isNotEmpty() }
    fun summaries(): List<AccountSummary> = synchronized(lock) {
        read().map { AccountSummary(it.nickname.ifBlank { "CodeBuddy 账号" }, it.maskedId(), it.realm) }
    }
    fun importJson(context: Context, uri: Uri): ImportResult {
        val input = context.contentResolver.openInputStream(uri) ?: error("无法读取 JSON 文件")
        val bytes = input.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val n = stream.read(chunk)
                if (n < 0) break
                require(output.size() + n <= LIMIT) { "文件超过 2 MB" }
                output.write(chunk, 0, n)
            }
            output.toByteArray()
        }
        val incoming = parse(bytes)
        return synchronized(lock) {
            val old = read()
            val positions = old.withIndex().associate { it.value.uid to it.index }.toMutableMap()
            var added = 0
            var refreshed = 0
            for (account in incoming) {
                val existing = positions[account.uid]
                if (existing == null) {
                    positions[account.uid] = old.size
                    old.add(account)
                    added++
                } else {
                    // Explicitly confirmed import means a fresh desktop export may
                    // update an expired access/refresh token for this same UID.
                    old[existing] = account
                    refreshed++
                }
            }
            if (added + refreshed > 0) write(old)
            ImportResult(added, refreshed, old.size)
        }
    }
    fun select(): Account = synchronized(lock) {
        val all = read()
        require(all.isNotEmpty()) { "尚未导入本地 WorkBuddy JSON，请先在「本地账号」页面导入" }
        all[Math.floorMod(nextIndex.getAndIncrement(), all.size)]
    }
    /**
     * Stable account within an Agent session, including iterative tool calls.
     * Blind round-robin selection on every model call changes OAuth identity
     * between turns of one tool loop, causing quota/caching/context failures.
     * Separate sessions distribute deterministically without retaining a map of
     * user session IDs or extra secrets in memory.
     */
    fun selectForSession(sessionId: String): Account = synchronized(lock) {
        val all = read()
        require(all.isNotEmpty()) { "请先在「本地账号」导入 WorkBuddy JSON" }
        all[Math.floorMod(sessionId.hashCode(), all.size)]
    }
    fun update(account: Account) = synchronized(lock) {
        val old = read()
        val i = old.indexOfFirst { it.uid == account.uid }
        if (i >= 0) { old[i] = account; write(old) }
    }
}
