package io.github.mangi.eta.agent.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentWorkspaceSchedulerTest {
    @Test fun physicalDisplayCanNeverBeRegistered() {
        val scheduler = AgentWorkspaceScheduler()
        assertFalse(scheduler.registerValidatedDisplay(0))
        assertFalse(scheduler.registerValidatedDisplay(-1))
        assertTrue(scheduler.registerValidatedDisplay(7))
        assertFalse(scheduler.registerValidatedDisplay(7))
    }

    @Test fun twoDisplaysRotateFourDifferentApplications() {
        val scheduler = AgentWorkspaceScheduler(maxDisplays = 2, maxApplications = 4)
        scheduler.registerValidatedDisplay(11)
        scheduler.registerValidatedDisplay(12)
        assertEquals(AgentWorkspaceScheduler.Admission.Granted("search", "com.store.alpha", 11),
            scheduler.request("search", "com.store.alpha"))
        assertEquals(AgentWorkspaceScheduler.Admission.Granted("chat", "com.chat.beta", 12),
            scheduler.request("chat", "com.chat.beta"))
        assertEquals(AgentWorkspaceScheduler.Reason.WAITING_FOR_DISPLAY,
            (scheduler.request("price", "com.store.gamma") as AgentWorkspaceScheduler.Admission.Deferred).reason)
        assertEquals(AgentWorkspaceScheduler.Reason.WAITING_FOR_DISPLAY,
            (scheduler.request("notes", "com.notes.delta") as AgentWorkspaceScheduler.Admission.Deferred).reason)
        assertNull(scheduler.nextReadyTaskId())
        assertEquals(AgentWorkspaceScheduler.Reason.CAPACITY_REACHED,
            (scheduler.request("fifth", "com.extra.epsilon") as AgentWorkspaceScheduler.Admission.Deferred).reason)

        scheduler.complete("search")
        assertEquals("price", scheduler.nextReadyTaskId())
        assertEquals(AgentWorkspaceScheduler.Admission.Granted("price", "com.store.gamma", 11),
            scheduler.request("price", "com.store.gamma"))
        assertNull(scheduler.nextReadyTaskId())
        scheduler.complete("chat")
        assertEquals("notes", scheduler.nextReadyTaskId())
        assertEquals(AgentWorkspaceScheduler.Admission.Granted("notes", "com.notes.delta", 12),
            scheduler.request("notes", "com.notes.delta"))
    }

    @Test fun foregroundAppOwnershipWinsOverVirtualWorkspace() {
        val scheduler = AgentWorkspaceScheduler()
        scheduler.registerValidatedDisplay(20)
        scheduler.updateUserForeground("com.chat.beta")
        assertEquals(AgentWorkspaceScheduler.Reason.CONFLICT_WITH_USER,
            (scheduler.request("chat", "com.chat.beta") as AgentWorkspaceScheduler.Admission.Deferred).reason)
        assertTrue(scheduler.activeTasks().isEmpty())
        // The foreground conflict cannot starve unrelated queued packages.
        assertEquals(AgentWorkspaceScheduler.Admission.Granted("search", "com.store.alpha", 20),
            scheduler.request("search", "com.store.alpha"))
    }

    @Test fun displayRemovalNeverFallsBackToDisplayZero() {
        val scheduler = AgentWorkspaceScheduler()
        scheduler.registerValidatedDisplay(34)
        scheduler.request("task", "com.store.alpha")
        val lost = scheduler.removeDisplay(34)
        assertEquals(1, lost.size)
        assertTrue(scheduler.activeTasks().isEmpty())
        assertEquals(AgentWorkspaceScheduler.Reason.NO_VALIDATED_DISPLAY,
            (scheduler.request("task", "com.store.alpha") as AgentWorkspaceScheduler.Admission.Deferred).reason)
        assertEquals("task", scheduler.queuedTasks().single().taskId)
    }

    @Test fun samePackageCannotBeControlledByTwoTasks() {
        val scheduler = AgentWorkspaceScheduler()
        scheduler.registerValidatedDisplay(3)
        scheduler.registerValidatedDisplay(4)
        scheduler.request("one", "com.chat.beta")
        assertEquals(AgentWorkspaceScheduler.Reason.PACKAGE_IN_USE,
            (scheduler.request("two", "com.chat.beta") as AgentWorkspaceScheduler.Admission.Deferred).reason)
        assertEquals(1, scheduler.activeTasks().size)
        scheduler.complete("one")
        assertEquals(AgentWorkspaceScheduler.Admission.Granted("two", "com.chat.beta", 3),
            scheduler.request("two", "com.chat.beta"))
    }
}
