package io.github.mangi.eta.data.workbuddy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkBuddyGatewayTest {
    @Test fun acceptsHttpsWithVpcSubpath() {
        val url = WorkBuddyGateway.validatedBase("https://gateway.example.com/proxy/v1/")
        assertEquals("https", url.scheme)
        assertEquals("/proxy/v1", url.encodedPath)
    }

    @Test fun acceptsLoopbackForLocalGatewayOnly() {
        assertEquals("http", WorkBuddyGateway.validatedBase("http://127.0.0.1:7864/v1").scheme)
        assertEquals("http", WorkBuddyGateway.validatedBase("http://localhost:7864/v1").scheme)
    }

    @Test fun rejectsCredentialLeakOverPlainHttp() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkBuddyGateway.validatedBase("http://my-server.example.com/v1")
        }
    }

    @Test fun rejectsMisconfiguredEndpoint() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkBuddyGateway.validatedBase("https://gateway.example.com/api")
        }
    }

    @Test fun rejectsEmbeddedCredentialsAndQuery() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkBuddyGateway.validatedBase("https://admin:secret@gateway.example.com/v1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkBuddyGateway.validatedBase("https://gateway.example.com/v1?token=x")
        }
    }
}
