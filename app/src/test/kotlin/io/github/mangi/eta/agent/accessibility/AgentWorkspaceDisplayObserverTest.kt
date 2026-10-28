package io.github.mangi.eta.agent.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgentWorkspaceDisplayObserverTest {
    @Test fun physicalDisplayIsNotObservableThroughWorkspaceInterface() {
        val result = AgentWorkspaceDisplayObserver.observe(null, 0)
        assertEquals(AgentWorkspaceDisplayObserver.Status.INVALID_DISPLAY, result.status)
        assertFalse(result.ready)
        assertTrue(result.windows.isEmpty())
    }

    @Test fun negativeAndMissingDisplayServiceFailClosed() {
        assertEquals(
            AgentWorkspaceDisplayObserver.Status.INVALID_DISPLAY,
            AgentWorkspaceDisplayObserver.observe(null, -1).status,
        )
        assertEquals(
            AgentWorkspaceDisplayObserver.Status.SERVICE_UNAVAILABLE,
            AgentWorkspaceDisplayObserver.observe(null, 7).status,
        )
    }
}
