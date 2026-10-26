package io.github.mangi.eta.data.workbuddy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkBuddyReadinessTest {
    @Test fun neverClaimReadyForOnlyBundledOrImportedBackend() {
        val f = ::workBuddyReadiness
        assertEquals(WorkBuddyReadiness.CORE_MISSING, f(false, 1, false, null, null, false))
        assertEquals(WorkBuddyReadiness.IMPORT_REQUIRED, f(true, 0, false, null, null, false))
        assertEquals(WorkBuddyReadiness.CORE_STOPPED, f(true, 2, false, null, 3, false))
        assertEquals(WorkBuddyReadiness.ACCOUNT_UNAVAILABLE, f(true, 2, true, 0, 3, false))
        assertEquals(WorkBuddyReadiness.MODEL_UNVERIFIED, f(true, 2, true, 1, null, false))
        assertEquals(WorkBuddyReadiness.CHAT_UNVERIFIED, f(true, 2, true, 1, 3, false))
        assertEquals(WorkBuddyReadiness.VERIFIED, f(true, 2, true, 1, 3, true))
    }

    @Test fun oldRuntimeSnapshotNeverOverwritesNewerDesktopExport() {
        assertFalse(WorkBuddyLocalVault.canRecoverToken(200L, 100L))
        assertFalse(WorkBuddyLocalVault.canRecoverToken(200L, 0L))
        assertTrue(WorkBuddyLocalVault.canRecoverToken(200L, 201L))
        assertTrue(WorkBuddyLocalVault.canRecoverToken(0L, 201L))
    }
}
