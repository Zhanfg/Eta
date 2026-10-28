package io.github.mangi.eta.agent.runtime

import java.util.ArrayDeque
import java.util.LinkedHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Multi-app workspace admission and fair display scheduling.
 *
 * This component does not launch activities or inject input. Its only valid
 * display IDs come from a separately authenticated, capability-checked bridge.
 * In particular it must never fall back to Android's physical Display 0.
 */
internal class AgentWorkspaceScheduler(
    private val maxDisplays: Int = 2,
    private val maxApplications: Int = 4,
) {
    init {
        require(maxDisplays in 1..4)
        require(maxApplications in 1..12)
    }

    enum class Reason {
        WAITING_FOR_DISPLAY,
        CONFLICT_WITH_USER,
        PACKAGE_IN_USE,
        CAPACITY_REACHED,
        NO_VALIDATED_DISPLAY,
        INVALID_REQUEST,
    }

    sealed interface Admission {
        data class Granted(val taskId: String, val packageName: String, val displayId: Int) : Admission
        data class Deferred(val taskId: String, val reason: Reason) : Admission
    }

    data class ActiveTask(val taskId: String, val packageName: String, val displayId: Int)
    data class QueueItem(val taskId: String, val packageName: String)

    private val gate = ReentrantLock()
    private val displays = sortedSetOf<Int>()
    private val active = LinkedHashMap<String, ActiveTask>()
    private val queue = ArrayDeque<QueueItem>()
    private var foregroundPackage: String? = null

    /** Trust boundary: only a verified display lease provider should call this. */
    fun registerValidatedDisplay(displayId: Int): Boolean = gate.withLock {
        if (displayId <= 0 || displayId in displays || displays.size >= maxDisplays) return false
        displays.add(displayId)
    }

    /** Unregistered displays invalidate their leases; no implicit reroute to 0. */
    fun removeDisplay(displayId: Int): List<ActiveTask> = gate.withLock {
        displays.remove(displayId)
        val lost = active.values.filter { it.displayId == displayId }
        lost.forEach { active.remove(it.taskId) }
        // Keep the tasks as pending; the dispatcher must re-observe/relaunch
        // after a new authenticated display is allocated.
        lost.forEach { enqueue(it.taskId, it.packageName) }
        lost
    }

    fun updateUserForeground(packageName: String?): List<ActiveTask> = gate.withLock {
        foregroundPackage = packageName?.trim()?.takeIf(String::isNotEmpty)
        val colliding = active.values.filter { it.packageName == foregroundPackage }
        colliding.forEach {
            active.remove(it.taskId)
            queue.addFirst(QueueItem(it.taskId, it.packageName))
        }
        // Caller must suspend input immediately. No "move app back" side effect.
        colliding
    }

    fun request(taskId: String, packageName: String): Admission = gate.withLock {
        if (taskId.isBlank() || packageName.isBlank()) {
            return Admission.Deferred(taskId, Reason.INVALID_REQUEST)
        }
        active[taskId]?.let { current ->
            return if (current.packageName == packageName) {
                Admission.Granted(taskId, packageName, current.displayId)
            } else {
                Admission.Deferred(taskId, Reason.INVALID_REQUEST)
            }
        }
        if (queue.any { it.taskId == taskId && it.packageName != packageName }) {
            return Admission.Deferred(taskId, Reason.INVALID_REQUEST)
        }
        val queued = queue.any { it.taskId == taskId }
        if (!queued && active.size + queue.size >= maxApplications) {
            return Admission.Deferred(taskId, Reason.CAPACITY_REACHED)
        }
        if (packageName == foregroundPackage) {
            enqueue(taskId, packageName)
            return Admission.Deferred(taskId, Reason.CONFLICT_WITH_USER)
        }
        if (active.values.any { it.packageName == packageName }) {
            enqueue(taskId, packageName)
            return Admission.Deferred(taskId, Reason.PACKAGE_IN_USE)
        }
        if (displays.isEmpty()) {
            enqueue(taskId, packageName)
            return Admission.Deferred(taskId, Reason.NO_VALIDATED_DISPLAY)
        }
        val freeDisplay = displays.firstOrNull { display -> active.values.none { it.displayId == display } }
        val earliestRunnable = queue.firstOrNull { item ->
            item.packageName != foregroundPackage &&
                active.values.none { it.packageName == item.packageName }
        }
        if (freeDisplay == null || (earliestRunnable != null && earliestRunnable.taskId != taskId)) {
            enqueue(taskId, packageName)
            return Admission.Deferred(taskId, Reason.WAITING_FOR_DISPLAY)
        }
        queue.removeAll { it.taskId == taskId }
        val granted = ActiveTask(taskId, packageName, freeDisplay)
        active[taskId] = granted
        Admission.Granted(taskId, packageName, freeDisplay)
    }

    /** Called at a checkpoint, not mid-gesture or during an uncertain send/payment. */
    fun yieldAtCheckpoint(taskId: String): Boolean = gate.withLock {
        val old = active.remove(taskId) ?: return false
        enqueue(old.taskId, old.packageName)
        true
    }

    fun complete(taskId: String) = gate.withLock {
        active.remove(taskId)
        queue.removeAll { it.taskId == taskId }
    }

    fun activeTasks(): List<ActiveTask> = gate.withLock { active.values.toList() }
    fun queuedTasks(): List<QueueItem> = gate.withLock { queue.toList() }
    fun nextReadyTaskId(): String? = gate.withLock {
        queue.firstOrNull { candidate ->
            candidate.packageName != foregroundPackage &&
                active.values.none { it.packageName == candidate.packageName } &&
                displays.any { display -> active.values.none { it.displayId == display } }
        }?.taskId
    }

    private fun enqueue(taskId: String, packageName: String) {
        if (queue.none { it.taskId == taskId } && active.size + queue.size < maxApplications) {
            queue.addLast(QueueItem(taskId, packageName))
        }
    }
}
