package io.github.mangi.eta.data.workbuddy

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkBuddyImportZipTest {
    private fun archive(vararg items: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            items.forEach { (name, payload) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(payload.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    @Test fun nativeImportAcceptsOfficialAccountAndAuthJson() {
        val data = """{"account":{"uid":"user-12345","nickname":"Mine"},
            "auth":{"accessToken":"secret123","refreshToken":"r","realm":"cn"}}"""
        val files = WorkBuddyAccountImport.readImportDocumentsBytes(data.toByteArray())
        assertEquals(1, files.size)
        assertEquals(1, WorkBuddyAccountImport.parsePreview(files.single()).count)
    }

    @Test fun nativeImportReadsMultipleAccountsFromManagerZip() {
        val bytes = archive(
            "workbuddy-user1.json" to """{"account":{"uid":"user-11111"},"auth":{"accessToken":"secret1"}}""",
            "workbuddy-user2.json" to """{"account":{"uid":"user-22222"},"auth":{"accessToken":"secret2"}}""",
        )
        val files = WorkBuddyAccountImport.readImportDocumentsBytes(bytes)
        assertEquals(2, files.size)
        assertEquals(listOf("user-11111", "user-22222"), files.map {
            val src = String(it)
            org.json.JSONObject(src).getJSONObject("account").getString("uid")
        })
    }

    @Test fun importsFiveSyntheticDesktopAccountsWithoutAnOnlineAccount() {
        // Generated in memory: never use a real accounts.zip or personal tokens.
        val entries = (1..5).map { index ->
            val account = org.json.JSONObject()
                .put("uid", "synthetic-user-0000" + index)
                .put("nickname", "Fixture " + index)
            val auth = org.json.JSONObject()
                .put("accessToken", "fixture-only-invalid-access-" + index)
                .put("refreshToken", "fixture-only-invalid-refresh-" + index)
                .put("realm", "cn")
            "accounts/workbuddy-fixture-" + index + ".json" to
                org.json.JSONObject().put("account", account).put("auth", auth).toString()
        }.toTypedArray()
        val documents = WorkBuddyAccountImport.readImportDocumentsBytes(archive(*entries))
        assertEquals(5, documents.size)
        val previews = documents.map { WorkBuddyAccountImport.parsePreview(it) }
        assertEquals(5, previews.sumOf { it.count })
        assertEquals(5, previews.flatMap { it.accounts }.map { it.account }.distinct().size)
        val display = previews.toString()
        assertFalse(display.contains("fixture-only-invalid-access"))
        assertFalse(display.contains("fixture-only-invalid-refresh"))
        assertFalse(display.contains("synthetic-user-0000"))
        // This proves only local ZIP parsing/preview, not Keystore persistence,
        // process startup, account entitlement or real model availability.
    }

    @Test fun zipTraversalRejectedBeforeAnyExtraction() {
        val bytes = archive("../workbuddy-evil.json" to """{"uid":"user1","accessToken":"test"}""")
        assertThrows(IllegalArgumentException::class.java) {
            WorkBuddyAccountImport.readImportDocumentsBytes(bytes)
        }
    }

    @Test fun zippedInvalidJsonRejectedWithoutSaving() {
        val bytes = archive("workbuddy.json" to """{"bad":"subscription"}""")
        assertThrows(IllegalArgumentException::class.java) {
            WorkBuddyAccountImport.readImportDocumentsBytes(bytes)
        }
    }

    @Test fun oversizedJsonRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkBuddyAccountImport.readImportDocumentsBytes(ByteArray(2 * 1024 * 1024 + 1) { ' '.code.toByte() })
        }
    }
}
