package io.github.mangi.eta.agent.accessibility

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.ArrayDeque

/**
 * Read-only, explicitly display-scoped accessibility observation.
 *
 * This class never reads rootInActiveWindow, which targets the physical
 * foreground rather than the requested display. It does not dispatch gestures,
 * access the clipboard, take screenshots, or control activities.
 *
 * A real virtual-display bridge must verify a display lease before using
 * observations for agent actions. A positive observation is not that lease.
 */
internal object AgentWorkspaceDisplayObserver {
    enum class Status {
        OBSERVED,
        INVALID_DISPLAY,
        NO_ACCESSIBLE_WINDOWS,
        SERVICE_UNAVAILABLE,
        OBSERVATION_FAILED,
    }

    data class Window(
        val windowId: Int,
        val displayId: Int,
        val packageName: String,
        val type: Int,
        val isActive: Boolean,
        val isFocused: Boolean,
        val visibleText: List<String>,
        val truncated: Boolean,
    )

    data class Snapshot(
        val displayId: Int,
        val status: Status,
        val collectedAtElapsedMs: Long,
        val windows: List<Window> = emptyList(),
    ) {
        val ready: Boolean get() = status == Status.OBSERVED && windows.isNotEmpty()
    }

    fun observe(
        service: AgentAccessibilityService?,
        displayId: Int,
        maxWindows: Int = 8,
        maxNodesPerWindow: Int = 80,
    ): Snapshot {
        val now = SystemClock.elapsedRealtime()
        if (displayId <= 0) return Snapshot(displayId, Status.INVALID_DISPLAY, now)
        if (service == null) return Snapshot(displayId, Status.SERVICE_UNAVAILABLE, now)
        return runCatching {
            val windows = service.windowsOnAllDisplays[displayId]
                .orEmpty()
                .asSequence()
                .filter { it.displayId == displayId }
                .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .sortedByDescending { it.layer }
                .take(maxWindows.coerceIn(1, 16))
                .mapNotNull { window ->
                    val root = window.root ?: return@mapNotNull null
                    val packageName = root.packageName?.toString().orEmpty()
                    if (packageName.isBlank()) return@mapNotNull null
                    val observed = readVisibleText(root, maxNodesPerWindow.coerceIn(1, 160))
                    Window(
                        windowId = window.id,
                        displayId = window.displayId,
                        packageName = packageName,
                        type = window.type,
                        isActive = window.isActive,
                        isFocused = window.isFocused,
                        visibleText = observed.first,
                        truncated = observed.second,
                    )
                }
                .toList()
            Snapshot(
                displayId = displayId,
                status = if (windows.isEmpty()) Status.NO_ACCESSIBLE_WINDOWS else Status.OBSERVED,
                collectedAtElapsedMs = now,
                windows = windows,
            )
        }.getOrElse {
            Snapshot(displayId, Status.OBSERVATION_FAILED, now)
        }
    }

    /** Bounded, non-recursive traversal; no long-lived AccessibilityNodeInfo refs. */
    private fun readVisibleText(root: AccessibilityNodeInfo, maxNodes: Int): Pair<List<String>, Boolean> {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        val lines = ArrayList<String>()
        var examined = 0
        while (pending.isNotEmpty() && examined < maxNodes) {
            val node = pending.removeFirst()
            examined++
            if (!node.isVisibleToUser) continue
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrEmpty()) lines.add(text.take(512))
            val description = node.contentDescription?.toString()?.trim()
            if (!description.isNullOrEmpty() && description != text) {
                lines.add(description.take(512))
            }
            for (i in 0 until node.childCount.coerceAtMost(64)) {
                node.getChild(i)?.let(pending::addLast)
            }
        }
        return lines.distinct().take(160) to pending.isNotEmpty()
    }
}
