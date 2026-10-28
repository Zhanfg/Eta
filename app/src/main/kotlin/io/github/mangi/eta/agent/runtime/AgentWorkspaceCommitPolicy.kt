package io.github.mangi.eta.agent.runtime

/**
 * Consequential actions require explicit task-scoped delegation, a verified
 * destination and a one-time execution checkpoint. Model prose alone is never
 * authorization to purchase, pay, or message an arbitrary contact.
 */
internal object AgentWorkspaceCommitPolicy {
    enum class Kind { OBSERVE, NAVIGATE, DRAFT, SEND_MESSAGE, PLACE_ORDER, MAKE_PAYMENT }
    enum class Verdict { ALLOW, REQUIRE_APPROVAL, REJECT }

    data class Grant(
        val taskId: String,
        val packages: Set<String>,
        val recipients: Set<String> = emptySet(),
        val merchants: Set<String> = emptySet(),
        val maximumOrderMinorUnits: Long? = null,
        val allowSending: Boolean = false,
        val allowOrdering: Boolean = false,
        val validUntilEpochMillis: Long,
    )

    data class Step(
        val taskId: String,
        val packageName: String,
        val kind: Kind,
        val exactTarget: String? = null,
        val amountMinorUnits: Long? = null,
        val targetVerified: Boolean = false,
        val sameUserProfile: Boolean = true,
        val displayId: Int,
    )

    fun check(
        step: Step,
        grant: Grant?,
        nowEpochMillis: Long,
        approvalForThisStep: Boolean = false,
    ): Verdict {
        if (step.displayId <= 0 || !step.sameUserProfile) return Verdict.REJECT
        if (grant == null || step.taskId != grant.taskId ||
            nowEpochMillis >= grant.validUntilEpochMillis ||
            step.packageName !in grant.packages) return Verdict.REJECT

        when (step.kind) {
            Kind.OBSERVE, Kind.NAVIGATE, Kind.DRAFT -> return Verdict.ALLOW
            Kind.SEND_MESSAGE -> {
                if (!step.targetVerified ||
                    step.exactTarget.isNullOrBlank() ||
                    step.exactTarget !in grant.recipients) return Verdict.REJECT
                return if (grant.allowSending || approvalForThisStep) Verdict.ALLOW
                    else Verdict.REQUIRE_APPROVAL
            }
            Kind.PLACE_ORDER -> {
                if (!step.targetVerified ||
                    step.exactTarget.isNullOrBlank() ||
                    step.exactTarget !in grant.merchants ||
                    step.amountMinorUnits == null || step.amountMinorUnits < 0L ||
                    grant.maximumOrderMinorUnits == null ||
                    step.amountMinorUnits > grant.maximumOrderMinorUnits) return Verdict.REJECT
                return if (grant.allowOrdering || approvalForThisStep) Verdict.ALLOW
                    else Verdict.REQUIRE_APPROVAL
            }
            Kind.MAKE_PAYMENT -> {
                // The grant and a one-time approval can permit *attempting*
                // the checkout flow, not skipping the payment provider's own
                // user verification, protected UI or authorization.
                if (!step.targetVerified ||
                    step.exactTarget.isNullOrBlank() ||
                    step.exactTarget !in grant.merchants ||
                    step.amountMinorUnits == null || step.amountMinorUnits < 0L ||
                    grant.maximumOrderMinorUnits == null ||
                    step.amountMinorUnits > grant.maximumOrderMinorUnits) return Verdict.REJECT
                return if (approvalForThisStep) Verdict.ALLOW else Verdict.REQUIRE_APPROVAL
            }
        }
    }
}
