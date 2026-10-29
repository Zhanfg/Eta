package io.github.mangi.eta.data.workbuddy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Raw WorkBuddy desktop-export auth JSON must survive the Android vault / Go handoff. */
class WorkBuddyGoAuthContractTest {
    @Test fun preservesUnknownNestedFieldsAndUpdatesTokens() {
        val source = """{
          "account":{"uid":"fixture-usr","nickname":"Fixture","enterpriseId":"e1",
            "futureTenantField":{"policy":"new"}},
          "auth":{"accessToken":"older-fake","refreshToken":"old-refresh",
            "expiresAt":1900000000,"realm":"global","domain":"workbuddy.ai",
            "futureSessionData":{"nonce":"opaque-preserved"}},
          "extension":{"version":2,"features":["future-capability"]}
        }"""
        val account = WorkBuddyLocalVault.Account(
            uid = "fixture-usr", accessToken = "new-fake", refreshToken = "new-refresh",
            expiresAt = 1950000000, realmHint = "global", domain = "workbuddy.ai",
            sourceJson = source,
        )
        val outgoing = WorkBuddyEmbeddedCore.createCoreAuthDocument(account)
        assertEquals("new-fake", outgoing.getJSONObject("auth").getString("accessToken"))
        assertEquals("new-refresh", outgoing.getJSONObject("auth").getString("refreshToken"))
        assertEquals(1950000000L, outgoing.getJSONObject("auth").getLong("expiresAt"))
        assertEquals("opaque-preserved", outgoing.getJSONObject("auth")
            .getJSONObject("futureSessionData").getString("nonce"))
        assertEquals("new", outgoing.getJSONObject("account")
            .getJSONObject("futureTenantField").getString("policy"))
        assertTrue(outgoing.getJSONObject("extension").getJSONArray("features").length() == 1)
        assertFalse(outgoing.toString().contains("older-fake"))
    }

    @Test fun oldVaultWithoutRawDocumentStillCreatesCompatibleAuthEnvelope() {
        val old = WorkBuddyLocalVault.Account(
            uid = "old-uid", accessToken = "synthetic", refreshToken = "synthetic-refresh",
            nickname = "Old", domain = "example.invalid",
            realmHint = "cn",
        )
        val json = WorkBuddyEmbeddedCore.createCoreAuthDocument(old)
        assertEquals("old-uid", json.getJSONObject("account").getString("uid"))
        assertEquals("synthetic", json.getJSONObject("auth").getString("accessToken"))
        assertEquals("cn", json.getJSONObject("auth").getString("realm"))
    }
}
