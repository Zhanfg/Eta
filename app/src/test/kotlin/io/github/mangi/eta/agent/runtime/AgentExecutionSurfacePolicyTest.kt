package io.github.mangi.eta.agent.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentExecutionSurfacePolicyTest {
    private val policy = AgentExecutionSurfacePolicy

    @Test fun browserAndDataToolsNeverClaimThePhysicalScreen() {
        for (mode in AgentExecutionSurfacePolicy.Mode.entries) {
            assertEquals(AgentExecutionSurfacePolicy.Route.OFFSCREEN_BROWSER,
                policy.route("browser_use", mode).route)
            for (tool in listOf("fetch_url", "web_search", "terminal", "read_file", "memory_get")) {
                assertEquals(tool, AgentExecutionSurfacePolicy.Route.DATA, policy.route(tool, mode).route)
            }
        }
    }

    @Test fun backgroundOnlyNeverFallsBackToDisplayZero() {
        for (tool in listOf("tap", "observe_screen", "launch_app", "open_uri", "type_text", "set_clipboard")) {
            val decision = policy.route(
                tool,
                AgentExecutionSurfacePolicy.Mode.BACKGROUND_ONLY,
                handoverAuthorized = true,
            )
            assertEquals(tool, AgentExecutionSurfacePolicy.Route.REQUIRE_USER_HANDOVER, decision.route)
            assertFalse(tool, decision.needsUserApproval)
            assertNull(tool, decision.displayId)
        }
    }

    @Test fun supportedGuiMayUseValidatedSecondaryDisplayWithoutHandover() {
        val decision = policy.route(
            "tap_element",
            AgentExecutionSurfacePolicy.Mode.BACKGROUND_ONLY,
            agentDisplayId = 3,
        )
        assertEquals(AgentExecutionSurfacePolicy.Route.SECONDARY_DISPLAY, decision.route)
        assertEquals(3, decision.displayId)
        assertFalse(decision.needsUserApproval)
    }

    @Test fun unsupportedGlobalUiControlDoesNotLeakIntoSecondaryDisplay() {
        val decision = policy.route(
            "set_clipboard",
            AgentExecutionSurfacePolicy.Mode.BACKGROUND_PREFERRED,
            agentDisplayId = 3,
        )
        assertEquals(AgentExecutionSurfacePolicy.Route.REQUIRE_USER_HANDOVER, decision.route)
        assertTrue(decision.needsUserApproval)
    }

    @Test fun foregroundRequiresExplicitPerStepAuthorization() {
        val mode = AgentExecutionSurfacePolicy.Mode.FOREGROUND_WITH_PERMISSION
        assertEquals(AgentExecutionSurfacePolicy.Route.REQUIRE_USER_HANDOVER,
            policy.route("tap", mode).route)
        assertEquals(AgentExecutionSurfacePolicy.Route.PRIMARY_DISPLAY_AUTHORIZED,
            policy.route("tap", mode, handoverAuthorized = true).route)
        assertThrows(IllegalArgumentException::class.java) {
            policy.route("tap", mode, agentDisplayId = 0)
        }
    }
}
