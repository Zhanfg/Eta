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
