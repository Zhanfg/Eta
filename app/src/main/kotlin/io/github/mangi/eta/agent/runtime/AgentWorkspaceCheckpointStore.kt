package io.github.mangi.eta.agent.runtime

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Crash-recoverable step receipt journal for multi-app workflows.
 *
 * Persists only non-sensitive step identifiers, package and state. NO screen
 * captures, chat text, cookies, authorization tokens or login data.
 *
 * A write's PREPARED or IN_FLIGHT record is ambiguous after process death:
 * recovery MUST require verification before any repeated side effect.
 */
internal class AgentWorkspaceCheckpointStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    enum class Kind { READ, WRITE }
    enum class Phase { PREPARED, IN_FLIGHT, VERIFIED, OUTCOME_UNKNOWN }
    enum class Disposition { READY, ALREADY_VERIFIED, RECONCILE_REQUIRED, STORAGE_FAILED, INVALID_STEP }

    data class Receipt(
        val taskId: String,
        val stepId: String,
        val packageName: String,
        val kind: Kind,
        val phase: Phase,
        val updatedAtMillis: Long,
    )

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        "eta_background_workspace_steps_v1", Context.MODE_PRIVATE
    )
    private val mutex = Any()

    fun prepare(
        taskId: String,
        stepId: String,
        packageName: String,
        kind: Kind,
    ): Disposition = synchronized(mutex) {
        if (!validIdentifier(taskId) || !validIdentifier(stepId) || !validIdentifier(packageName)) {
            return Disposition.INVALID_STEP
        }
        val existing = read(taskId, stepId)
        if (existing != null) {
            if (existing.packageName != packageName || existing.kind != kind) {
                return Disposition.INVALID_STEP
            }
            if (existing.phase == Phase.VERIFIED) return Disposition.ALREADY_VERIFIED
            // Writes are not replayable even if the process died before
            // marking IN_FLIGHT: delivery may already have begun.
            if (kind == Kind.WRITE) return Disposition.RECONCILE_REQUIRED
        }
        val next = Receipt(taskId, stepId, packageName, kind, Phase.PREPARED, clock())
        if (persist(next)) Disposition.READY else Disposition.STORAGE_FAILED
    }

    fun markInFlight(taskId: String, stepId: String): Boolean =
        transition(taskId, stepId, Phase.IN_FLIGHT, setOf(Phase.PREPARED))

    /**
     * Call only after reading back the actual destination state. An unknown
     * outcome is never evidence of success without external reconciliation.
     */
    fun markVerified(taskId: String, stepId: String): Boolean =
        transition(taskId, stepId, Phase.VERIFIED,
            setOf(Phase.IN_FLIGHT, Phase.OUTCOME_UNKNOWN))

    fun markUnknown(taskId: String, stepId: String): Boolean =
        transition(taskId, stepId, Phase.OUTCOME_UNKNOWN,
            setOf(Phase.PREPARED, Phase.IN_FLIGHT, Phase.OUTCOME_UNKNOWN))

    /** Approval of a retry is not a delivery verification; do not reset writes here. */
    fun get(taskId: String, stepId: String): Receipt? = synchronized(mutex) {
        read(taskId, stepId)
    }

    fun recover(taskId: String, stepId: String): Disposition = synchronized(mutex) {
        val existing = read(taskId, stepId) ?: return Disposition.READY
        when {
            existing.phase == Phase.VERIFIED -> Disposition.ALREADY_VERIFIED
            existing.kind == Kind.WRITE -> Disposition.RECONCILE_REQUIRED
            else -> Disposition.READY
        }
    }

    private fun transition(
        taskId: String,
        stepId: String,
        target: Phase,
        allowed: Set<Phase>,
    ): Boolean = synchronized(mutex) {
        val old = read(taskId, stepId) ?: return false
        if (old.phase !in allowed) return false
        persist(old.copy(phase = target, updatedAtMillis = clock()))
    }

    private fun read(taskId: String, stepId: String): Receipt? {
        if (!validIdentifier(taskId) || !validIdentifier(stepId)) return null
        val value = prefs.getString(key(taskId, stepId), null) ?: return null
        return runCatching {
            val obj = JSONObject(value)
            val savedTask = obj.getString("task")
            val savedStep = obj.getString("step")
            if (savedTask != taskId || savedStep != stepId) return null
            Receipt(
                savedTask, savedStep, obj.getString("package"),
                Kind.valueOf(obj.getString("kind")),
                Phase.valueOf(obj.getString("phase")),
                obj.getLong("timestamp"),
            )
        }.getOrNull()
    }

    private fun persist(receipt: Receipt): Boolean {
        val entry = JSONObject()
            .put("task", receipt.taskId)
            .put("step", receipt.stepId)
            .put("package", receipt.packageName)
            .put("kind", receipt.kind.name)
            .put("phase", receipt.phase.name)
            .put("timestamp", receipt.updatedAtMillis)
            .toString()
        return prefs.edit().putString(key(receipt.taskId, receipt.stepId), entry).commit()
    }

    private fun key(taskId: String, stepId: String): String =
        "step.${taskId.length}:${taskId}.${stepId.length}:${stepId}"

    private fun validIdentifier(input: String): Boolean =
        input.isNotBlank() && input.length <= 160 &&
            input.none { it.isISOControl() || it == '\u0000' }
}
