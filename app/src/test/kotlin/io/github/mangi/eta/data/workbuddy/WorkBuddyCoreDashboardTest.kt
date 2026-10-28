package io.github.mangi.eta.data.workbuddy

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkBuddyCoreDashboardTest {
    @Test fun projectsOnlySafeRealAccountFields() {
        val result = WorkBuddyCoreDashboard.parseSummary(JSONObject(
            """
            {"total":2,"healthy":1,"cooling":1,"disabled":0,"in_flight_full":0,
             "sticky_sessions":3,
             "realm_totals":{"cn":{"total":1},"global":{"total":1}},
             "daily_budget":{"used":1.5,"limit":10.0},
             "accounts":[
               {"uid":"1234567890","nickname":"Personal","realm":"cn","credits":42,
                "cooling":true,"cool_remaining_sec":128,"success_count":9,
                "accessToken":"MUST_NEVER_EXPOSE"},
               {"uid":"abcdefghi","realm":"global","credits":0,
                "disabled":true,"needs_relogin":true,"success_count":0}
             ]}
            """.trimIndent()
        ))
        assertEquals(2, result.total)
        assertEquals(1, result.healthy)
        assertEquals(1, result.cnTotal)
        assertEquals(1, result.globalTotal)
        assertEquals(1.5, result.budgetUsed, 0.0001)
        assertEquals(2, result.accounts.size)
        assertEquals("123•••890", result.accounts.first().maskedId)
        assertTrue(result.accounts.first().cooling)
        assertEquals(128L, result.accounts.first().coolingSeconds)
        assertTrue(result.accounts.last().needsRelogin)
        assertTrue(result.accounts.last().state.contains("电脑重新登录"))
        assertFalse(result.toString().contains("MUST_NEVER_EXPOSE"))
        assertFalse(result.toString().contains("1234567890"))
    }

    @Test fun emptyPoolMeansNoAvailableSubscription() {
        val result = WorkBuddyCoreDashboard.parseSummary(JSONObject(
            """{"total":0,"healthy":0,"accounts":[],"daily_budget":{}}"""
        ))
        assertTrue(result.accounts.isEmpty())
        assertEquals(0, result.healthy)
    }
}
