package io.github.mangi.eta.data.workbuddy

/**
 * ETA/Kelivo-style single-source status model: one user-facing result instead
 * of mixing unrelated booleans and mistaking "compiled" for "functional".
 */
internal enum class WorkBuddyReadiness(val title: String, val nextAction: String) {
    CORE_MISSING("缺少 Android 原生核心", "安装包含 ARM64 Go 核心的测试 APK"),
    IMPORT_REQUIRED("需要导入订阅", "从电脑导出 JSON / accounts.zip 并在此选择"),
    CORE_STOPPED("账号已导入，核心未启动", "点击「启动并验证原生后端」"),
    ACCOUNT_UNAVAILABLE("核心已启动，账号不可服务", "检查账号授权、额度、冷却或网络"),
    MODEL_UNVERIFIED("核心已启动，模型未验证", "点击「重新同步模型」"),
    CHAT_UNVERIFIED("模型已发现，尚未实测对话", "使用真实 SSE 测试，确认可能产生的订阅消耗"),
    VERIFIED("模型通路已实测通过", "返回 ETA 聊天使用当前模型"),
}

internal fun workBuddyReadiness(
    coreBundled: Boolean,
    accountCount: Int,
    coreRunning: Boolean,
    accountHealthy: Int?,
    modelCount: Int?,
    actualStreamPassed: Boolean,
): WorkBuddyReadiness = when {
    !coreBundled -> WorkBuddyReadiness.CORE_MISSING
    accountCount <= 0 -> WorkBuddyReadiness.IMPORT_REQUIRED
    !coreRunning -> WorkBuddyReadiness.CORE_STOPPED
    accountHealthy != null && accountHealthy <= 0 -> WorkBuddyReadiness.ACCOUNT_UNAVAILABLE
    modelCount == null || modelCount <= 0 -> WorkBuddyReadiness.MODEL_UNVERIFIED
    !actualStreamPassed -> WorkBuddyReadiness.CHAT_UNVERIFIED
    else -> WorkBuddyReadiness.VERIFIED
}
