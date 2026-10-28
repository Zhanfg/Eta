package io.github.mangi.eta.agent.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentWorkspaceCommitPolicyTest {
    private val grant = AgentWorkspaceCommitPolicy.Grant(
        taskId = "compare-buy",
        packages = setOf("com.store.one", "com.chat.two"),
        recipients = setOf("family-chat"),
        merchants = setOf("known-shop"),
        maximumOrderMinorUnits = 25000L,
        allowSending = false,
        allowOrdering = false,
        validUntilEpochMillis = 10000L,
    )

    @Test fun readOnlyOperationsMayRunWithValidDisplayAndGrant() {
        val step = AgentWorkspaceCommitPolicy.Step(
            "compare-buy", "com.store.one",
            AgentWorkspaceCommitPolicy.Kind.OBSERVE,
            displayId = 16
        )
        assertEquals(AgentWorkspaceCommitPolicy.Verdict.ALLOW,
            AgentWorkspaceCommitPolicy.check(step, grant, 5000L))
        assertEquals(AgentWorkspaceCommitPolicy.Verdict.REJECT,
            AgentWorkspaceCommitPolicy.check(step.copy(displayId = 0), grant, 5000L))
    }

    @Test fun messageRequiresMatchingVerifiedRecipientAndPermission() {
        val step = AgentWorkspaceCommitPolicy.Step(
            "compare-buy", "com.chat.two",
            AgentWorkspaceCommitPolicy.Kind.SEND_MESSAGE,
            exactTarget = "family-chat",
            targetVerified = true,
            displayId = 18
        )
        assertEquals(AgentWorkspaceCommitPolicy.Verdict.REQUIRE_APPROVAL,
            AgentWorkspaceCommitPolicy.check(step, grant, 5000L))
        assertEquals(AgentWorkspaceCommitPolicy.Verdict.ALLOW,
            AgentWorkspaceCommitPolicy.check(step, grant, 5000L, approvalForThisStep = true))
        assertEquals(AgentWorkspaceCommitPolicy.Verdict.REJECT,
            AgentWorkspaceCommitPolicy.check(step.copy(exactTarget = "stranger"), grant, 5000L))
        assertEquals(AgentWorkspaceCommitPolicy.Verdict.REJECT,
            AgentWorkspaceCommitPolicy.check(step.copy(targetVerified = false), grant, 5000L))
    }

    @Test fun shoppingRequiresVerifiedMerchantBudgetAndPermission() {
        val order = AgentWorkspaceCommitPolicy.Step(
            "compare-buy", "com.store.one",
            AgentWorkspaceCommitPolicy.Kind.PLACE_ORDER,
            exactTarget = "known-shop", amountMinorUnits = 18800,
            targetVerified = true, displayId = 19
        )
        assertEquals(AgentWorkspaceCommitPolicy.Verdict.REQUIRE_APPROVAL,
            AgentWorkspaceCommitPolicy.check(order, grant, 5000L))
        assertEquals(AgentWorkspaceCommitPolicy.Verdict.ALLOW,
            AgentWorkspaceCommitPolicy.check(order, grant, 5000L, approvalForThisStep = true))
        assertEquals(AgentWorkspaceCommitPolicy.Verdict.REJECT,
            AgentWorkspaceCommitPolicy.check(order.copy(amountMinorUnits = 28000L), grant, 5000L))
    }

    @Test fun expiredGrantAndUnverifiedPaymentCannotProceed() {
        val step = AgentWorkspaceCommitPolicy.Step(
            "compare-buy", "com.store.one",
            AgentWorkspaceCommitPolicy.Kind.MAKE_PAYMENT,
            exactTarget = "known-shop", amountMinorUnits = 1200,
            targetVerified = false, displayId = 17
        )
        assertEquals(AgentWorkspaceCommitPolicy.Verdict.REJECT,
            AgentWorkspaceCommitPolicy.check(step, grant, 5000L, approvalForThisStep = true))
        assertEquals(AgentWorkspaceCommitPolicy.Verdict.REJECT,
            AgentWorkspaceCommitPolicy.check(step, grant, 10000L, approvalForThisStep = true))
    }
}
