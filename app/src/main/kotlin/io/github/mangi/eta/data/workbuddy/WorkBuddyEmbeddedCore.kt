package io.github.mangi.eta.data.workbuddy

import android.content.Context
import android.system.Os
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Launches the pinned upstream Go workbuddy2api *inside the Android app sandbox*.
 *
 * This is not WebView and does not contact an external Manager service. The Go
 * process is a packaged native executable (libwbcore.so for APK extraction),
 * bound to 127.0.0.1 with a per-start random API key.
 *
 * SECURITY NOTICE: Upstream Go requires readable auth JSON files and writes
 * refreshed tokens back. The temporary auth directory is sandbox-private and
 * chmod 0700/0600, but contains unencrypted credentials while the core runs.
 * Never back it up or expose it to Storage Access Framework.
 *
 * This class is an experimental runtime adapter pending on-device validation.
 */
internal object WorkBuddyEmbeddedCore {
    data class Endpoint(val baseUrl: String, val apiKey: String)
    private val lock = Any()
    private val random = SecureRandom()
    @Volatile private var context: Context? = null
    private var running: java.lang.Process? = null
    private var endpoint: Endpoint? = null
    private var activeAgentRequests: Int = 0

    /**
     * A model request holds a lease for its entire SSE/tool generation. Do not
     * kill the Go pool during an in-flight ETA Agent operation.
     */
    fun <T> withActiveRequest(block: (Endpoint) -> T): T {
        val current = synchronized(lock) {
            val e = ensureStarted()
            activeAgentRequests++
            e
        }
        try {
            return block(current)
        } finally {
            synchronized(lock) { activeAgentRequests-- }
        }
    }


    fun init(ctx: Context) { context = ctx.applicationContext }

    /**
     * Cold-start hygiene, invoked on a background dispatcher after EtaApp
     * initializes Keystore. If Android killed the previous run, recover
     * refreshed tokens and securely leave no stale runtime auth JSON behind.
     * A normal running core is never interrupted by this path.
     */
    fun recoverAfterUnexpectedExit(): Boolean = synchronized(lock) {
        if (running?.isAlive == true) return false
        context?.let { recoverPrivateSnapshots(it) }
        true
    }

    fun isBundled(): Boolean {
        val ctx = context ?: return false
        return File(ctx.applicationInfo.nativeLibraryDir, "libwbcore.so").isFile
    }

    fun isRunning(): Boolean = synchronized(lock) {
        val proc = running
        proc != null && proc.isAlive && endpoint != null
    }

    /**
     * User-shareable, strictly redacted on-device diagnostic. No tokens, UIDs,
     * filesystem paths, app IDs, account names or network requests.
     */
    fun diagnosticSummary(): String = synchronized(lock) {
        val ctx = context
        if (ctx == null) return@synchronized "ETA WorkBuddy: runtime not initialized"
        val binary = File(ctx.applicationInfo.nativeLibraryDir, "libwbcore.so")
        val accountState = runCatching {
            "readable (${WorkBuddyLocalVault.summaries().size} accounts)"
        }.getOrElse { error ->
            "unreadable (${error.javaClass.simpleName})"
        }
        buildString {
            appendLine("ETA WorkBuddy local diagnostics v1")
            appendLine("Native core file present: ${binary.isFile}")
            appendLine("Native core executable: ${binary.isFile && binary.canExecute()}")
            appendLine("Native core size: ${if (binary.isFile) binary.length() else -1} bytes")
            appendLine("Device ABI supported: ${android.os.Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }}")
            appendLine("Local encrypted vault: $accountState")
            appendLine("Go process running: ${running?.isAlive == true}")
            appendLine("Health-confirmed endpoint: ${endpoint != null}")
            appendLine("Active Agent requests: $activeAgentRequests")
            appendLine("Network or model entitlement: not tested")
        }
    }

    private fun baseDir(context: Context): File = File(context.noBackupFilesDir, "workbuddy-native")

    private fun secret(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun writeLocked(file: File, text: String) {
        file.parentFile?.mkdirs()
        // Create with mode 0600 *before* any auth/config bytes are written.
        // File.writeText then chmod leaves a transient permissive window.
        val fd = Os.open(
            file.absolutePath,
            android.system.OsConstants.O_WRONLY or
                android.system.OsConstants.O_CREAT or
                android.system.OsConstants.O_TRUNC or
                android.system.OsConstants.O_NOFOLLOW,
            384,
        )
        java.io.FileOutputStream(fd).use { output ->
            Os.chmod(file.absolutePath, 384)
            output.write(text.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
    }

    /**
     * Recovery-first restart. A killed Android process may leave the Go core's
     * refreshed credentials on disk. Merge them into the Keystore *before*
     * rewriting any auth file. Never delete snapshots when decryption fails.
     */
    private fun recoverPrivateSnapshots(ctx: Context) {
        val authDir = File(baseDir(ctx), "auths")
        val snapshots = authDir.listFiles()?.filter {
            it.isFile && it.name.startsWith("workbuddy-") && it.extension == "json"
        }.orEmpty()
        if (snapshots.isEmpty()) return
        WorkBuddyLocalVault.ingestCoreRefreshes(authDir)
        snapshots.forEach { file ->
            check(file.delete()) { "无法清理旧账号快照；已暂停启动，避免泄漏凭据" }
        }
    }

    /**
     * Lossless adapter from encrypted desktop-export JSON to the native Go
     * auth-file contract. Existing Keystore snapshots without sourceJson keep
     * the legacy fallback to avoid forcing a destructive migration.
     */
    internal fun createCoreAuthDocument(account: WorkBuddyLocalVault.Account): JSONObject {
        val raw = runCatching {
            account.sourceJson.takeIf(String::isNotBlank)?.let(::JSONObject)
        }.getOrNull() ?: JSONObject()
        val metadata = raw.optJSONObject("account") ?: JSONObject()
        val auth = raw.optJSONObject("auth") ?: JSONObject()
        metadata.put("uid", account.uid)
        if (account.enterpriseId.isNotBlank()) metadata.put("enterpriseId", account.enterpriseId)
        if (account.nickname.isNotBlank()) metadata.put("nickname", account.nickname)
        auth.put("accessToken", account.accessToken)
        if (account.refreshToken.isNotBlank()) auth.put("refreshToken", account.refreshToken)
        if (account.expiresAt > 0) auth.put("expiresAt", account.expiresAt)
        if (account.domain.isNotBlank()) auth.put("domain", account.domain)
        if (account.realmHint.isNotBlank() || !auth.has("realm")) auth.put("realm", account.realm)
        raw.put("account", metadata)
        raw.put("auth", auth)
        return raw
    }

    private fun writeAccounts(ctx: Context): Int {
        val root = File(baseDir(ctx), "auths")
        root.mkdirs()
        Os.chmod(root.absolutePath, 448) // 0700
        // Only names generated by our own code live here, never untrusted filenames.
        // Decrypt BEFORE touching old snapshots: an invalidated Android
        // Keystore must never cause destructive deletion of recoverable auth.
        val snapshot = WorkBuddyLocalVault.snapshotForCore()
        require(snapshot.isNotEmpty()) { "本机账号库没有可用的 WorkBuddy 账号" }
        root.listFiles()?.filter { it.isFile && it.name.startsWith("workbuddy-") && it.extension == "json" }
            ?.forEach { file ->
                check(file.delete()) { "旧 Go 账号快照无法安全清理，已取消启动" }
            }
        snapshot.forEachIndexed { index, account ->
            // A WorkBuddy subscription is a *credential document*, not just
            // five primitive fields. Retain unknown nested auth/account
            // metadata such as future signing, tenant, or device context.
            // Only patch fields updated by the on-device token refresh.
            val json = createCoreAuthDocument(account)
            writeLocked(File(root, "workbuddy-${index.toString().padStart(3, '0')}.json"), json.toString())
        }
        return snapshot.size
    }

    private fun allocatePort(): Int = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        .use { it.localPort }

    private fun health(endpoint: Endpoint): Boolean {
        val connection = (URL(endpoint.baseUrl.removeSuffix("/v1") + "/healthz")
            .openConnection() as HttpURLConnection)
        // A mobile Go subprocess may need extra time while the device is
        // busy/thermal-throttled. This probe must not incorrectly mark a
        // healthy process as dead due to a 300 ms startup connect timeout.
        connection.connectTimeout = 1_000
        connection.readTimeout = 1_000
        connection.instanceFollowRedirects = false
        return try {
            connection.requestMethod = "GET"
            val status = connection.responseCode
            // Go reports HTTP 503 if all accounts are unavailable, EVEN WHEN THE
            // SERVICE IS RUNNING. Health must not mistake quota exhaustion for
            // a failed Android process boot.
            if (status != 200 && status != 503) return false
            if (connection.getHeaderField("X-Service") != "workbuddy2api") return false
            val stream = if (status == 200) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { reader ->
                reader.readText().take(4096)
            } ?: return false
            JSONObject(body).optString("service") == "workbuddy2api"
        } catch (_: Exception) { false } finally { connection.disconnect() }
    }

    fun ensureStarted(): Endpoint = synchronized(lock) {
        val current = endpoint
        val live = running
        if (current != null && live?.isAlive == true && health(current)) return current

        val ctx = checkNotNull(context) { "ETA 尚未初始化 WorkBuddy 运行时" }
        val binary = File(ctx.applicationInfo.nativeLibraryDir, "libwbcore.so")
        check(binary.isFile) { "APK 未打包 Android ARM64 Go 核心。请使用带原生核心的验证包" }
        // KELIVO-style reversible recovery: persist existing state before making
        // changes, including when a previous process was killed by Android.
        stop()
        val accounts = writeAccounts(ctx)
        require(accounts > 0) { "请先导入 WorkBuddy 账号 JSON" }

        // Bind to loopback only. A random bearer token is required even from
        // other processes on the same device. No public wildcard listener.
        val port = allocatePort()
        val key = secret()
        val home = baseDir(ctx)
        home.mkdirs()
        Os.chmod(home.absolutePath, 448)
        val data = File(home, "data").apply {
            mkdirs()
            Os.chmod(absolutePath, 448)
        }
        val config = JSONObject()
            .put("listen", "127.0.0.1:$port")
            .put("api_key", key)
            .put("auth_dir", File(home, "auths").absolutePath)
            .put("state_file", File(data, "state.json").absolutePath)
            // No surprise background account traffic on mobile by default.
            .put("schedule", JSONObject()
                .put("checkin_enabled", false)
                .put("travel_enabled", false)
                .put("activity_enabled", false)
                .put("keepalive_enabled", false)
                .put("school_enabled", false)
                .put("cat_enabled", false))
            .put("admin", JSONObject().put("enabled", false))
            .put("metrics", JSONObject().put("enabled", false))
            .put("upstash", JSONObject())
        val file = File(home, "config.json")
        writeLocked(file, config.toString())
        running?.destroy()
        endpoint = null

        val process = try {
            ProcessBuilder(binary.absolutePath, "-config", file.absolutePath)
                .directory(home)
                .redirectErrorStream(true)
                .start()
        } catch (error: Exception) {
            // Failed exec must not leave cleartext credentials indefinitely.
            // Recovery failure is surfaced rather than deleting recoverable data.
            recoverPrivateSnapshots(ctx)
            throw IllegalStateException("Android 无法启动 WB Go 核心：${error.javaClass.simpleName}")
        }
        running = process
        // Drain stdout without logging any potentially sensitive response.
        Thread({
            runCatching { process.inputStream.use { input ->
                val buf = ByteArray(4096)
                while (input.read(buf) != -1) { /* discard */ }
            } }
        }, "wb-core-output").apply { isDaemon = true; start() }

        val e = Endpoint("http://127.0.0.1:$port/v1", key)
        // Monotonic time budget: per-probe network timeouts are part of
        // startup elapsed time. The old repeat(32)*150ms estimate did not
        // account for connect/read and could report a false 4.8s deadline.
        val deadline = android.os.SystemClock.elapsedRealtime() + 20_000L
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (!process.isAlive) {
                val exit = runCatching { process.exitValue() }.getOrNull()
                running = null
                endpoint = null
                throw IllegalStateException("WB Go 核心启动失败（exit=$exit）；请检查 APK 原生核心及系统权限")
            }
            if (health(e)) {
                endpoint = e
                return e
            }
            Thread.sleep(250)
        }
        process.destroy()
        runCatching { process.waitFor(2, TimeUnit.SECONDS) }
        if (process.isAlive) process.destroyForcibly()
        running = null
        endpoint = null
        throw IllegalStateException("WB Go 核心在启动期限内未响应本机健康检查")
    }

    /**
     * Before stopping, preserve upstream-refreshed credentials back to the
     * AES-GCM Keystore vault. Do not print or return any raw tokens.
     */
    fun stop() = synchronized(lock) {
        check(activeAgentRequests == 0) {
            "仍有 ${activeAgentRequests} 个 Agent 调用运行中，结束后才能停止或重新导入订阅"
        }
        val process = running
        process?.destroy()
        if (process != null) {
            runCatching { process.waitFor(3, TimeUnit.SECONDS) }
            if (process.isAlive) process.destroyForcibly()
        }
        running = null
        endpoint = null
        // Fail closed: a vault/Keystore error must never silently purge the
        // last refreshed credentials. Snapshots remain private for recovery.
        context?.let { recoverPrivateSnapshots(it) }
    }

    /** Re-read JSON after confirmed import. Never reload while requests run. */
    fun restart(): Endpoint {
        stop()
        return ensureStarted()
    }
}
