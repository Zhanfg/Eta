package io.github.mangi.eta.data.workbuddy

import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/**
 * Stable, credential-free support codes. Never show upstream response JSON,
 * URLs containing queries, bearer tokens, account identifiers or raw errors.
 * The user can safely report the code from the native WorkBuddy settings page.
 */
internal object WorkBuddyFailure {
    fun describe(error: Throwable): String {
        val message = error.message.orEmpty().lowercase()
        val codeAndAction = when {
            "apk 未打包" in message || "libwbcore" in message ->
                "WB-CORE-01" to "安装带有 ARM64 Go 核心的 ETA 集成 APK"
            "无法启动 wb go 核心" in message || "启动期间退出" in message ||
                "未响应健康检查" in message ->
                "WB-CORE-02" to "原生进程未启动；检查 Android 版本、ABI 和应用执行权限"
            "尚未初始化" in message || "本地账号库尚未初始化" in message ->
                "WB-CORE-03" to "应用运行时尚未初始化；重新打开 ETA 再试"
            "未导入" in message || "先导入" in message || "账号数量" in message ->
                "WB-IMPORT-01" to "先导入本人 CodeBuddy 账号 JSON 或官方 accounts.zip"
            "keystore" in message || "decrypt" in message || "本地账号库文件损坏" in message ->
                "WB-VAULT-01" to "本机加密账号库不可读；不要清理数据，请先检查密钥库"
            "401" in message || "403" in message || "授权" in message ||
                "token" in message || "无效凭据" in message ->
                "WB-AUTH-01" to "账号授权或令牌可能已失效；从电脑重新导出并导入"
            "模型列表" in message || "模型目录" in message || "模型" in message ||
                "model" in message ->
                "WB-MODEL-01" to "没有取得可用模型；检查账号区域、订阅权益和网络"
            "sse" in message || "stream" in message || "流式" in message ->
                "WB-STREAM-01" to "流式响应未完整结束；检查网络并执行真实通路测试"
            error is SocketTimeoutException -> "WB-NET-01" to "上游请求超时；检查网络或代理连接"
            error is ConnectException || error is SSLException ->
                "WB-NET-02" to "网络连接失败；检查目标网络及 HTTPS 证书"
            else -> "WB-UNKNOWN" to "操作未完成；请提供错误代码和当前操作步骤"
        }
        return "${codeAndAction.first} · ${codeAndAction.second}"
    }
}
