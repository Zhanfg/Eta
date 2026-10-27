package io.github.mangi.eta.agent.runtime

import io.github.mangi.eta.agent.tool.AgentToolRequirements

/**
 * Planning-only contract for future per-run workspace routing.
 *
 * Not wired to AgentLocalTools yet: selecting a virtual display must never
 * silently change the existing runtime until capability tests and UI controls
 * are in place. This policy deliberately fails closed for every Display 0 tool.
 */
internal object AgentExecutionSurfacePolicy {
    enum class Mode {
        BACKGROUND_ONLY,
        BACKGROUND_PREFERRED,
        FOREGROUND_WITH_PERMISSION,
    }

    enum class Route {
        DATA,
        OFFSCREEN_BROWSER,
        SECONDARY_DISPLAY,
        REQUIRE_USER_HANDOVER,
        PRIMARY_DISPLAY_AUTHORIZED,
    }

    data class Decision(
        val route: Route,
        val displayId: Int? = null,
        val needsUserApproval: Boolean = false,
    )

    /**
     * [agentDisplayId] must come from a successfully validated secondary
     * display lease, not an arbitrary caller-supplied display identifier.
     * [handoverAuthorized] must be an explicit per-operation approval.
     */
    fun route(
        tool: String,
        mode: Mode,
        agentDisplayId: Int? = null,
        handoverAuthorized: Boolean = false,
    ): Decision {
        require(agentDisplayId == null || agentDisplayId > 0) {
            "Secondary display must not be Display.DEFAULT_DISPLAY"
        }
        if (tool == "browser_use") return Decision(Route.OFFSCREEN_BROWSER)
        val primaryDisplayTool = AgentToolRequirements.requiresGuiControl(tool) ||
            tool == "set_alarm" || tool == "set_timer"
        if (!primaryDisplayTool) return Decision(Route.DATA)
        if (agentDisplayId != null && supportsSecondaryDisplay(tool)) {
            return Decision(Route.SECONDARY_DISPLAY, displayId = agentDisplayId)
        }
        if (mode == Mode.FOREGROUND_WITH_PERMISSION && handoverAuthorized) {
            return Decision(Route.PRIMARY_DISPLAY_AUTHORIZED)
        }
        return Decision(Route.REQUIRE_USER_HANDOVER, needsUserApproval = mode != Mode.BACKGROUND_ONLY)
    }

    private fun supportsSecondaryDisplay(tool: String): Boolean =
        tool in setOf(
            "observe_screen", "tap", "tap_area", "tap_element",
            "long_press", "long_press_element", "swipe", "scroll",
            "scroll_element", "type_text", "input_text", "replace_text",
            "clear_text", "paste_text", "press_key", "wait_for_text",
            "wait_for_package", "launch_app", "open_uri",
        )
}
