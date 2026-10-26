package io.github.mangi.eta.data.workbuddy

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkBuddySetupGateTest {
    @Test fun neverClaimsReadyBasedOnPackagingOrImportAlone() {
        val stage = WorkBuddySetupGate
        assertEquals(stage.Stage.NO_BINARY, stage.derive(false, 1, false, 0, false))
        assertEquals(stage.Stage.NO_ACCOUNTS, stage.derive(true, 0, false, 0, false))
        assertEquals(stage.Stage.CORE_STOPPED, stage.derive(true, 1, false, 3, true))
    }

    @Test fun eachActualVerificationIsRequired() {
        val gate = WorkBuddySetupGate
        assertEquals(gate.Stage.MODEL_DISCOVERY, gate.derive(true, 1, true, 0, false))
        assertEquals(gate.Stage.BYTE_PROBE, gate.derive(true, 1, true, 2, false))
        assertEquals(gate.Stage.VERIFIED, gate.derive(true, 1, true, 2, true))
    }

    @Test fun successfulTextProbeIsNotMistakenForCompleteToolParity() {
        val gate = WorkBuddySetupGate
        val hint = gate.userHint(gate.Stage.VERIFIED)
        assert(hint.contains("工具调用"))
        assert(hint.contains("单独验收"))
    }
}
