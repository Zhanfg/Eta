package io.github.mangi.eta.agent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** A single Agent tool loop must not hop between upstream WorkBuddy accounts. */
class WorkBuddyPoolSessionKeyTest {
    @Test fun sameAgentSessionHasStablePoolBindingAcrossTurns() {
        val session = "test-conversation-a"
        val initial = WorkBuddyEmbeddedProvider.poolSessionKey(session)
        val subsequentToolTurn = WorkBuddyEmbeddedProvider.poolSessionKey(session)
        assertEquals(initial, subsequentToolTurn)
        assertFalse(initial.contains(session))
    }

    @Test fun unrelatedAgentSessionsHaveSeparateBindingKeys() {
        val a = WorkBuddyEmbeddedProvider.poolSessionKey("fixture-session-a")
        val b = WorkBuddyEmbeddedProvider.poolSessionKey("fixture-session-b")
        assertNotEquals(a, b)
    }
}
