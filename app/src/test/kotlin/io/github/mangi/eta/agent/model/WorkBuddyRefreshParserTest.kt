package io.github.mangi.eta.agent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkBuddyRefreshParserTest {
    @Test fun supportsBareResponse() {
        val body = WorkBuddyDirectProvider.parseRefreshResponse(
            """{"accessToken":"newtoken","refreshToken":"newrefresh","expiresIn":7200}"""
        )
        assertEquals("newtoken", body.getString("accessToken"))
    }

    @Test fun supportsEnvelopedResponse() {
        val body = WorkBuddyDirectProvider.parseRefreshResponse(
            """{"code":0,"data":{"accessToken":"newtoken","refreshToken":"newrefresh"}}"""
        )
        assertEquals("newtoken", body.getString("accessToken"))
    }

    @Test fun rejectsErrorsWithoutPrintingCredentials() {
        val error = assertThrows(IllegalStateException::class.java) {
            WorkBuddyDirectProvider.parseRefreshResponse(
                """{"code":403,"message":"secret-refresh-token"}"""
            )
        }
        assertEquals(false, error.message.orEmpty().contains("secret-refresh-token"))
    }

    @Test fun rejectsMissingAccessToken() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkBuddyDirectProvider.parseRefreshResponse("""{"code":0,"data":{}}""")
        }
    }
}
