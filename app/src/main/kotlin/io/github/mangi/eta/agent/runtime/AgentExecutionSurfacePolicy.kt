package io.github.mangi.eta.agent.runtime

import io.github.mangi.eta.agent.tool.AgentToolRequirements

/**
 * A fail-closed routing contract. Runtime currently enforces the BACKGROUND_ONLY
 * branch before dispatch. Secondary display routing remains capability-gated
 * until scoped accessibility and screenshot support is implemented.
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
        val primaryDisplayTool = requiresForegroundSurface(tool)
        if (!primaryDisplayTool) return Decision(Route.DATA)
        if (agentDisplayId != null && supportsSecondaryDisplay(tool)) {
            return Decision(Route.SECONDARY_DISPLAY, displayId = agentDisplayId)
        }
        if (mode == Mode.FOREGROUND_WITH_PERMISSION && handoverAuthorized) {
            return Decision(Route.PRIMARY_DISPLAY_AUTHORIZED)
        }
        return Decision(Route.REQUIRE_USER_HANDOVER, needsUserApproval = mode != Mode.BACKGROUND_ONLY)
    }

    /**
     * Even though the terminal can do ordinary file work, Root shell may call
     * `am start` or `input` and bypass our display routing. Disable arbitrary
     * command execution in strict mode until command-scoped sandboxing exists.
     */
    fun requiresForegroundSurface(tool: String): Boolean =
        AgentToolRequirements.requiresGuiControl(tool) ||
            tool in setOf(
                "set_alarm", "set_timer", "set_clipboard", "get_clipboard",
                "terminal", "terminal_job", "terminal_daemon",
                "media_control", "set_volume", "set_setting",
                "set_device_state", "app_state_control", "set_flashlight",
                "set_brightness", "set_screen_timeout", "set_hotspot",
                "set_sound_mode", "set_do_not_disturb",
            )

    private fun supportsSecondaryDisplay(tool: String): Boolean =
        tool in setOf(
            "observe_screen", "tap", "tap_area", "tap_element",
            "long_press", "long_press_element", "swipe", "scroll",
            "scroll_element", "type_text", "input_text", "replace_text",
            "clear_text", "paste_text", "press_key", "wait_for_text",
            "wait_for_package", "launch_app", "open_uri",
        )
}
