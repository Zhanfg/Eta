package io.github.mangi.eta.ui.app

import io.github.mangi.eta.agent.runtime.AgentRunCheckpointStore
import io.github.mangi.eta.agent.runtime.AgentRuntimeWire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRunRecoveryCoordinatorTest {
    @Test
    fun sameProcessActiveCheckpointIsReattached() {
        val plan = AgentRunRecoveryCoordinator.plan(
            checkpoints = listOf(checkpoint("run-active", owner = "same-process")),
            completedRuns = emptyList(),
            activeStateKnown = true,
            terminalStateKnown = true,
            activeRunIds = setOf("run-active"),
            locallyObservedRunIds = emptySet(),
        )

        assertEquals("run-active", plan.reattach.single().runId)
        assertTrue(plan.interrupted.isEmpty())
    }

    @Test
    fun terminalResultTakesPrecedenceAndKeepsCheckpointForTraceReplay() {
        val checkpoint = checkpoint("run-complete")
        val completed = completed("run-complete")

        val plan = AgentRunRecoveryCoordinator.plan(
            checkpoints = listOf(checkpoint),
            completedRuns = listOf(completed),
            activeStateKnown = true,
            terminalStateKnown = true,
            activeRunIds = setOf("run-complete"),
            locallyObservedRunIds = emptySet(),
        )

        assertEquals(completed, plan.completed.single().result)
        assertEquals(checkpoint, plan.completed.single().checkpoint)
        assertTrue(plan.reattach.isEmpty())
        assertTrue(plan.interrupted.isEmpty())
    }

    @Test
    fun inactiveCheckpointIsInterruptedButLocallyObservedRunIsIgnored() {
        val plan = AgentRunRecoveryCoordinator.plan(
            checkpoints = listOf(checkpoint("run-stale"), checkpoint("run-local")),
            completedRuns = emptyList(),
            activeStateKnown = true,
            terminalStateKnown = true,
            activeRunIds = emptySet(),
            locallyObservedRunIds = setOf("run-local"),
        )

        assertEquals(listOf("run-stale"), plan.interrupted.map { it.runId })
        assertTrue(plan.reattach.isEmpty())
    }

    @Test
    fun unavailableRuntimeLeavesUnresolvedCheckpointUntouched() {
        val plan = AgentRunRecoveryCoordinator.plan(
            checkpoints = listOf(checkpoint("run-unknown")),
            completedRuns = emptyList(),
            activeStateKnown = false,
            terminalStateKnown = false,
            activeRunIds = emptySet(),
            locallyObservedRunIds = emptySet(),
        )

        assertTrue(plan.interrupted.isEmpty())
        assertTrue(plan.reattach.isEmpty())
    }

    @Test
    fun knownActiveRunCanReattachEvenWhenTerminalQueryFailed() {
        val plan = AgentRunRecoveryCoordinator.plan(
            checkpoints = listOf(checkpoint("run-active"), checkpoint("run-unknown")),
            completedRuns = emptyList(),
            activeStateKnown = true,
            terminalStateKnown = false,
            activeRunIds = setOf("run-active"),
            locallyObservedRunIds = emptySet(),
        )

        assertEquals("run-active", plan.reattach.single().runId)
        assertTrue(plan.interrupted.isEmpty())
    }

    @Test
    fun mixedConcurrentRunsRecoverWithoutInterruptingOtherActiveRuns() {
        val plan = AgentRunRecoveryCoordinator.plan(
            checkpoints = listOf("a", "b", "done", "orphan", "local").map { checkpoint(it) },
            completedRuns = listOf(completed("done"), completed("local")),
            activeStateKnown = true,
            terminalStateKnown = true,
            activeRunIds = setOf("a", "b", "done", "local"),
            locallyObservedRunIds = setOf("local"),
        )

        assertEquals(listOf("done"), plan.completed.map { it.result.result.runId })
        assertEquals(listOf("a", "b"), plan.reattach.map { it.runId })
        assertEquals(listOf("orphan"), plan.interrupted.map { it.runId })
    }

    @Test
    fun duplicateDurableTerminalEnvelopesReplayOnlyLatestOnceAfterRestart() {
        val early = completed("run-duplicated")
        val late = AgentRuntimeWire.CompletedRun(
            handoff = early.handoff,
            result = AgentRuntimeWire.RunResult(
                runId = "run-duplicated",
                ok = true,
                content = "更完整的最终结果",
            ),
            createdAt = early.createdAt + 10,
        )
        val plan = AgentRunRecoveryCoordinator.plan(
            checkpoints = listOf(checkpoint("run-duplicated")),
            completedRuns = listOf(late, early, late),
            activeStateKnown = true,
            terminalStateKnown = true,
            activeRunIds = emptySet(),
            locallyObservedRunIds = emptySet(),
        )
        assertEquals(1, plan.completed.size)
        assertEquals("更完整的最终结果", plan.completed.single().result.result.content)
        assertTrue(plan.interrupted.isEmpty())
        assertTrue(plan.reattach.isEmpty())
    }

    private fun checkpoint(
        runId: String,
        owner: String = "old-process",
    ) = AgentRunCheckpointStore.Checkpoint(
        runId = runId,
        ownerInstanceId = owner,
        handoff = AgentRuntimeWire.EntryHandoff(
            id = runId,
            source = AgentRuntimeWire.AGENT_UI_HANDOFF_SOURCE,
            payload = "conversation-1",
        ),
        events = emptyList(),
        createdAt = 1L,
        updatedAt = 2L,
    )

    private fun completed(runId: String) = AgentRuntimeWire.CompletedRun(
        handoff = AgentRuntimeWire.EntryHandoff(
            id = runId,
            source = AgentRuntimeWire.AGENT_UI_HANDOFF_SOURCE,
            payload = "conversation-1",
        ),
        result = AgentRuntimeWire.RunResult(
            runId = runId,
            ok = true,
            content = "完成",
        ),
        createdAt = 3L,
    )
}
