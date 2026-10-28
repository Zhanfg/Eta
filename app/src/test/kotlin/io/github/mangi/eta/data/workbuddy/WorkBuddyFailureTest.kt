package io.github.mangi.eta.data.workbuddy

import java.net.SocketTimeoutException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkBuddyFailureTest {
    @Test fun noTokenOrBodyLeaksViaNativeErrorMessage() {
        val message = WorkBuddyFailure.describe(
            IllegalStateException("HTTP 403, accessToken=secret-123 refreshToken=token-456")
        )
        assertTrue(message.startsWith("WB-AUTH-01"))
        assertFalse(message.contains("secret-123"))
        assertFalse(message.contains("token-456"))
    }

    @Test fun reportsNativeAbiFailureWithoutRawData() {
        val code = WorkBuddyFailure.describe(IllegalStateException("Android 无法启动 WB Go 核心"))
        assertTrue(code.startsWith("WB-CORE-02"))
    }

    @Test fun distinguishesModelDiscoveryAndNetworkTimeout() {
        assertTrue(WorkBuddyFailure.describe(IllegalStateException("模型列表为空"))
            .startsWith("WB-MODEL-01"))
        assertTrue(WorkBuddyFailure.describe(SocketTimeoutException("secret.example.com:1234"))
            .startsWith("WB-NET-01"))
    }

    @Test fun unknownFailuresHaveAStableSupportCode() {
        assertTrue(WorkBuddyFailure.describe(IllegalArgumentException("account=private"))
            .startsWith("WB-UNKNOWN"))
        assertFalse(WorkBuddyFailure.describe(IllegalArgumentException("account=private"))
            .contains("private"))
    }
}
