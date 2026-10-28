package io.github.mangi.eta.agent.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundDisplayCapabilityProbeTest {
    @Test fun anAllowedLaunchPolicyIsOnlyPermissionToPerformMoreTests() {
        val result = BackgroundDisplayCapabilityProbe.Result(
            featureSupported = true,
            displayCreated = true,
            targetLaunchPermitted = true,
            code = "LAUNCH_POLICY_ALLOWED_NOT_TESTED",
        )
        assertTrue(result.canProceedToDeviceTest)
    }

    @Test fun noDisplayAndNoThirdPartyPermissionFailClosed() {
        val cases = listOf(
            BackgroundDisplayCapabilityProbe.Result(false, false, false, "NO_SECONDARY_DISPLAY_FEATURE"),
            BackgroundDisplayCapabilityProbe.Result(true, false, false, "DISPLAY_CREATION_FAILED"),
            BackgroundDisplayCapabilityProbe.Result(true, true, false, "THIRD_PARTY_LAUNCH_RESTRICTED"),
        )
        cases.forEach { assertFalse(it.canProceedToDeviceTest) }
    }
}
