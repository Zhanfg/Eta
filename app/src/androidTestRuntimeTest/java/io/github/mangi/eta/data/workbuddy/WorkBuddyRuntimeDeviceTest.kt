package io.github.mangi.eta.data.workbuddy

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android/Bionic/Keystore checks. Never run with a user's exported credentials. */
@RunWith(AndroidJUnit4::class)
class WorkBuddyRuntimeDeviceTest {
    private lateinit var context: Context
    private lateinit var fixture: File

    @Before fun prepare() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        check(target.packageName.endsWith(".runtimetest")) { "Requires isolated runtimeTest app" }
        fixture = File(target.noBackupFilesDir, "synthetic-runtime-fixture")
        fixture.deleteRecursively()
        check(fixture.mkdirs())
        context = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = fixture
        }
        WorkBuddyLocalVault.init(context)
        WorkBuddyEmbeddedCore.init(context)
    }

    @After fun clean() {
        try { WorkBuddyEmbeddedCore.stop() } finally { fixture.deleteRecursively() }
    }

    private fun importFive() {
        val source = File(fixture, "synthetic-accounts.zip")
        ZipOutputStream(source.outputStream()).use { zip ->
            for (index in 1..5) {
                val account = JSONObject().put("uid", "synthetic-account-000" + index)
                val auth = JSONObject().put("accessToken", "fixture-invalid-access-" + index)
                    .put("refreshToken", "fixture-invalid-refresh-" + index).put("realm", "cn")
                zip.putNextEntry(ZipEntry("workbuddy-fixture-" + index + ".json"))
                zip.write(JSONObject().put("account", account).put("auth", auth).toString().toByteArray())
                zip.closeEntry()
            }
        }
        val imported = WorkBuddyEmbeddedCore.importAccounts { WorkBuddyLocalVault.importJson(context, Uri.fromFile(source)) }
        assertEquals(5, imported.total)
        assertEquals(5, WorkBuddyLocalVault.summaries().size)
        val encrypted = File(fixture, "workbuddy-accounts-v1.bin").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(encrypted.contains("fixture-invalid-access"))
        assertFalse(encrypted.contains("synthetic-account"))
        source.delete()
    }

    @Test fun fiveAccountsPersistOfflineAndCoreRestartsWithPrivateState() {
        importFive() // All import code must remain network-independent.
        assertFalse(WorkBuddyEmbeddedCore.isRunning())
        repeat(2) {
            val endpoint = WorkBuddyEmbeddedCore.ensureStarted()
            assertTrue(WorkBuddyEmbeddedCore.isRunning())
            val connection = URL(endpoint.baseUrl.removeSuffix("/v1") + "/status").openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("Authorization", "Bearer " + endpoint.apiKey)
            try {
                assertEquals(200, connection.responseCode)
                val status = JSONObject(connection.inputStream.bufferedReader().use { reader -> reader.readText() })
                assertEquals(5, status.getInt("total"))
            } finally { connection.disconnect() }
            WorkBuddyEmbeddedCore.stop()
            assertFalse(WorkBuddyEmbeddedCore.isRunning())
            assertEquals(5, WorkBuddyLocalVault.summaries().size)
            assertEquals(0, File(fixture, "workbuddy-native/auths").listFiles().orEmpty().size)
        }
    }

    @Test fun importCannotStopAnActiveAgentRequest() {
        importFive()
        WorkBuddyEmbeddedCore.withActiveRequest {
            assertThrows(IllegalStateException::class.java) {
                WorkBuddyEmbeddedCore.importAccounts { fail("Import must not run during an active request") }
            }
            assertTrue(WorkBuddyEmbeddedCore.isRunning())
        }
    }

    private fun runProbe(name: String): Pair<Int, JSONObject> {
        val binary = File(context.applicationInfo.nativeLibraryDir, name)
        assertTrue("Runtime probe must be packaged in runtimeTest only", binary.isFile)
        val process = WorkBuddyAndroidEnvironment.configure(
            ProcessBuilder(binary.absolutePath).redirectErrorStream(true), fixture, context.cacheDir,
        ).start()
        try {
            assertTrue("Android network probe timed out", process.waitFor(45, TimeUnit.SECONDS))
            // Do not echo arbitrary native output into reports.
            return process.exitValue() to JSONObject(process.inputStream.bufferedReader().readText())
        } finally { process.destroyForcibly() }
    }

    @Test fun legacyPureGoBuildReproducesAndroidDnsFailure() {
        val (exit, report) = runProbe("libwbprobe_legacy.so")
        assertEquals(1, exit)
        assertFalse(report.getBoolean("cgo"))
        assertFalse("Legacy build unexpectedly resolved through Android DNS", report.getBoolean("dns"))
    }

    @Test fun bionicDnsAndSystemTlsWorkInsideTheAndroidApplicationSandbox() {
        val (exit, report) = runProbe("libwbprobe.so")
        assertEquals(0, exit)
        for (field in listOf("cgo", "dns", "system_roots", "https")) {
            assertTrue("Android runtime check failed: " + field, report.getBoolean(field))
        }
    }
}
