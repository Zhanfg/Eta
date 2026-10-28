package io.github.mangi.eta.agent.runtime

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AgentWorkspaceCheckpointStoreTest {
    private lateinit var context: Context

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(
            "eta_background_workspace_steps_v1", Context.MODE_PRIVATE
        ).edit().clear().commit()
    }

    @After fun tearDown() {
        context.getSharedPreferences(
            "eta_background_workspace_steps_v1", Context.MODE_PRIVATE
        ).edit().clear().commit()
    }

    @Test fun messageCrashBeforeDispatchRequiresReconciliation() {
        val first = AgentWorkspaceCheckpointStore(context) { 1000L }
        assertEquals(AgentWorkspaceCheckpointStore.Disposition.READY,
            first.prepare("task", "send-message", "com.example.chat",
                AgentWorkspaceCheckpointStore.Kind.WRITE))
        val recovered = AgentWorkspaceCheckpointStore(context) { 2000L }
        assertEquals(AgentWorkspaceCheckpointStore.Disposition.RECONCILE_REQUIRED,
            recovered.recover("task", "send-message"))
        assertEquals(AgentWorkspaceCheckpointStore.Disposition.RECONCILE_REQUIRED,
            recovered.prepare("task", "send-message", "com.example.chat",
                AgentWorkspaceCheckpointStore.Kind.WRITE))
    }

    @Test fun inFlightPurchaseCannotBeBlindlyReplayedAndReadbackMayVerify() {
        val first = AgentWorkspaceCheckpointStore(context)
        assertEquals(AgentWorkspaceCheckpointStore.Disposition.READY,
            first.prepare("purchase", "place-order", "com.example.store",
                AgentWorkspaceCheckpointStore.Kind.WRITE))
        assertTrue(first.markInFlight("purchase", "place-order"))
        assertEquals(AgentWorkspaceCheckpointStore.Disposition.RECONCILE_REQUIRED,
            AgentWorkspaceCheckpointStore(context).recover("purchase", "place-order"))
        assertTrue(first.markUnknown("purchase", "place-order"))
        assertTrue(first.markVerified("purchase", "place-order"))
        assertEquals(AgentWorkspaceCheckpointStore.Disposition.ALREADY_VERIFIED,
            AgentWorkspaceCheckpointStore(context).recover("purchase", "place-order"))
    }

    @Test fun rejectedAndRepeatedTransitionsCannotEraseKnownDelivery() {
        val store = AgentWorkspaceCheckpointStore(context)
        assertFalse(store.markInFlight("task", "missing"))
        assertEquals(AgentWorkspaceCheckpointStore.Disposition.READY,
            store.prepare("task", "send", "com.example.chat",
                AgentWorkspaceCheckpointStore.Kind.WRITE))
        assertTrue(store.markInFlight("task", "send"))
        assertTrue(store.markVerified("task", "send"))
        assertFalse(store.markUnknown("task", "send"))
        assertFalse(store.markInFlight("task", "send"))
        assertEquals(AgentWorkspaceCheckpointStore.Disposition.ALREADY_VERIFIED,
            store.prepare("task", "send", "com.example.chat",
                AgentWorkspaceCheckpointStore.Kind.WRITE))
    }

    @Test fun readOnlyStepMayRestartAndStepIdentityCannotBeChanged() {
        val store = AgentWorkspaceCheckpointStore(context)
        assertEquals(AgentWorkspaceCheckpointStore.Disposition.INVALID_STEP,
            store.prepare("", "step", "com.example", AgentWorkspaceCheckpointStore.Kind.READ))
        assertEquals(AgentWorkspaceCheckpointStore.Disposition.READY,
            store.prepare("task", "inspect", "com.example.a",
                AgentWorkspaceCheckpointStore.Kind.READ))
        assertTrue(store.markInFlight("task", "inspect"))
        assertEquals(AgentWorkspaceCheckpointStore.Disposition.READY,
            AgentWorkspaceCheckpointStore(context).prepare("task", "inspect", "com.example.a",
                AgentWorkspaceCheckpointStore.Kind.READ))
        assertEquals(AgentWorkspaceCheckpointStore.Disposition.INVALID_STEP,
            store.prepare("task", "inspect", "com.example.b",
                AgentWorkspaceCheckpointStore.Kind.READ))
    }
}
