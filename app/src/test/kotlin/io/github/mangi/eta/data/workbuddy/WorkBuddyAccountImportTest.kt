package io.github.mangi.eta.data.workbuddy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkBuddyAccountImportTest {
    @Test fun parsesComputerExportWithoutRevealingTokens() {
        val fixture = """
            {"uid":"1234567890","accessToken":"secret-access-token",
              "refreshToken":"secret-refresh-token","nickname":"Mine","realm":"cn"}
        """.trimIndent()
        val p = WorkBuddyAccountImport.parsePreview(fixture.toByteArray())
        assertEquals(1, p.count)
        assertEquals("123•••890", p.accounts.single().account)
        assertEquals("Mine", p.accounts.single().nickname)
        assertFalse(p.toString().contains("secret"))
        assertFalse(p.toString().contains("1234567890"))
    }

    @Test fun supportsNestedAccountAndAuthPlusBatchExports() {
        val fixture = """
            {"accounts":[
              {"account":{"uid":"123456789","nickname":"Primary"},"auth":{"access_token":"a"}},
              {"uid":"987654321","auth":{"accessToken":"b"},"realm":"global"}
            ]}
        """.trimIndent()
        val p = WorkBuddyAccountImport.parsePreview(fixture.toByteArray())
        assertEquals(2, p.count)
        assertEquals("Primary", p.accounts.first().nickname)
        assertEquals("global", p.accounts.last().realm)
    }

    @Test fun rejectsRandomSubscriptionJsonWithoutAccountCredentials() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkBuddyAccountImport.parsePreview("""{"models":["gpt"],"endpoint":"https://somewhere"}""".toByteArray())
        }
    }

    @Test fun rejectsOverSizedFilesAndDuplicateAccounts() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkBuddyAccountImport.parsePreview(ByteArray(2 * 1024 * 1024 + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkBuddyAccountImport.parsePreview(
                """[{"uid":"same","accessToken":"a"},{"uid":"same","accessToken":"b"}]""".toByteArray()
            )
        }
    }

    @Test fun acceptsBomAndRejectsInvalidContent() {
        val p = WorkBuddyAccountImport.parsePreview(
            "\uFEFF{\"uid\":\"abcdefghi\",\"accessToken\":\"fixture\"}".toByteArray()
        )
        assertEquals(1, p.count)
        assertTrue(p.accounts.single().account.contains("•••"))
        assertThrows(IllegalArgumentException::class.java) {
            WorkBuddyAccountImport.parsePreview("not JSON".toByteArray())
        }
    }
}
