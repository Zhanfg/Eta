package io.github.mangi.eta.data.workbuddy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkBuddyVaultRecoveryTest {
    @Test fun newerDesktopImportCannotBeOverwrittenByAnOrphanedCoreFile() {
        assertFalse(WorkBuddyLocalVault.canRecoverSnapshot(
            snapshotModifiedAt = 1000L,
            vaultModifiedAt = 2000L,
        ))
    }

    @Test fun newerCoreRefreshIsAcceptedAndEqualTimestampDoesNotDiscardIt() {
        assertTrue(WorkBuddyLocalVault.canRecoverSnapshot(2001L, 2000L))
        assertTrue(WorkBuddyLocalVault.canRecoverSnapshot(2000L, 2000L))
    }

    @Test fun missingSnapshotTimestampCannotAuthorizeTokenOverwrite() {
        assertFalse(WorkBuddyLocalVault.canRecoverSnapshot(0L, 2000L))
    }

    @Test fun vaultWithUnknownTimestampCanRecoverARealCheckpoint() {
        assertTrue(WorkBuddyLocalVault.canRecoverSnapshot(2000L, 0L))
    }

    @Test fun newerTokenExpirationIsRequiredWhenOriginalAccountHasFutureExpiry() {
        assertFalse(WorkBuddyLocalVault.canRecoverToken(10000L, 8000L))
        assertTrue(WorkBuddyLocalVault.canRecoverToken(10000L, 12000L))
        assertFalse(WorkBuddyLocalVault.canRecoverToken(10000L, 0L))
    }
}
