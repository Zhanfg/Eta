package io.github.mangi.eta.data.workbuddy

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
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

    @Test fun fiveAccountDesktopExportWithNestedAuthAndFutureExpiryImportsOffline() {
        // Synthetic metadata-only contract matching a five-file computer export.
        // Never store a user-supplied archive or any real access/refresh token in CI.
        val timestamp = 1_825_000_000L
        val fixture = (1..5).map { index ->
            "workbuddy-synthetic-$index.json" to """
                {
                  "account": {
                    "uid": "synthetic-account-uuid-$index",
                    "nickname": "OfflineFixture$index",
                    "enterpriseId": ""
                  },
                  "auth": {
                    "accessToken": "NOT_A_REAL_ACCESS_TOKEN_$index",
                    "refreshToken": "NOT_A_REAL_REFRESH_TOKEN_$index",
                    "expiresAt": $timestamp,
                    "domain": "example.invalid",
                    "realm": "${if (index == 5) "global" else "cn"}"
                  }
                }
            """.trimIndent()
        }.toTypedArray()
        val documents = WorkBuddyAccountImport.readImportDocumentsBytes(archive(*fixture))
        assertEquals(5, documents.size)
        val previews = documents.flatMap { WorkBuddyAccountImport.parsePreview(it).accounts }
        assertEquals(5, previews.size)
        assertEquals(5, previews.map { it.account }.toSet().size)
        assertEquals("global", previews.last().realm)
        assertEquals("cn", previews.first().realm)
        // Preview never returns bearer or refresh token data.
        assertEquals(false, previews.toString().contains("NOT_A_REAL_ACCESS_TOKEN"))
        assertEquals(false, previews.toString().contains("NOT_A_REAL_REFRESH_TOKEN"))
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
