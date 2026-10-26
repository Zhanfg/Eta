package io.github.mangi.eta.data.workbuddy

/**
 * WorkBuddy onboarding gates derived from *observed facts*, not from a saved
 * "success" flag. Inspired by Kelivo's explicit runtime/task state separation.
 *
 * "Running" only proves that the Go process is alive, never that the user has
 * a valid CodeBuddy subscription or that tool calls work.
 */
internal object WorkBuddySetupGate {
    enum class Stage {
        NO_BINARY, NO_ACCOUNTS, CORE_STOPPED, MODEL_DISCOVERY, BYTE_PROBE, VERIFIED
    }

    fun derive(
        binaryInstalled: Boolean,
        accountCount: Int,
        coreRunning: Boolean,
        modelCount: Int,
        streamingVerified: Boolean,
    ): Stage = when {
        !binaryInstalled -> Stage.NO_BINARY
        accountCount == 0 -> Stage.NO_ACCOUNTS
        !coreRunning -> Stage.CORE_STOPPED
        modelCount <= 0 -> Stage.MODEL_DISCOVERY
        !streamingVerified -> Stage.BYTE_PROBE
        else -> Stage.VERIFIED
    }

    fun userHint(stage: Stage): String = when (stage) {
        Stage.NO_BINARY -> "当前 APK 未包含 WorkBuddy ARM64 核心，请安装含原生核心的验证包"
        Stage.NO_ACCOUNTS -> "先导入电脑导出的 JSON 或 accounts.zip，凭据只保存在本机"
        Stage.CORE_STOPPED -> "账号已保存；下一步启动本地 Go 核心，验证实际运行能力"
        Stage.MODEL_DISCOVERY -> "本地核心已启动；下一步同步账号真正可用的模型"
        Stage.BYTE_PROBE -> "模型已发现；仍需明确同意后执行一次可能消耗积分的 SSE 真实请求"
        Stage.VERIFIED -> "本次会话的流式文本通路已验证；多工具调用及完整管理能力仍需单独验收"
    }
}
