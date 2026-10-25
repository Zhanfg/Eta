package io.github.mangi.eta.ui.pages.providers

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.data.workbuddy.WorkBuddyAccountImport
import io.github.mangi.eta.data.workbuddy.WorkBuddyLocalVault
import io.github.mangi.eta.ui.components.EtaPreference
import io.github.mangi.eta.ui.components.EtaTextButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Pure on-device CodeBuddy account management.
 * No WebView, external WorkBuddy server, local HTTP daemon or administrator password.
 */
@Composable
internal fun WorkBuddyOnDeviceTab(
    scrollBehavior: ScrollBehavior,
    contentSidePadding: Dp,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf<WorkBuddyAccountImport.Preview?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var accounts by remember { mutableStateOf<List<WorkBuddyLocalVault.AccountSummary>>(emptyList()) }

    fun refresh() {
        scope.launch {
            accounts = try {
                withContext(Dispatchers.IO) { WorkBuddyLocalVault.summaries() }
            } catch (_: Exception) {
                status = "本地加密账号库不可用；检查设备密钥库后重试"
                emptyList()
            }
        }
    }

    LaunchedEffect(Unit) {
        accounts = try {
            withContext(Dispatchers.IO) { WorkBuddyLocalVault.summaries() }
        } catch (_: Exception) { emptyList() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        picked = null
        preview = null
        confirm = false
        if (uri != null) {
            scope.launch {
                busy = true
                status = null
                try {
                    // SAF access is ephemeral, raw JSON stays outside Compose state.
                    preview = WorkBuddyAccountImport.preview(context, uri)
                    picked = uri
                    status = "文件已校验；账号密钥不会出现在预览中"
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    status = "JSON 无效：请使用从本人 CodeBuddy/WorkBuddy 账户导出的账号凭据 JSON（含 uid 和 accessToken），不能导入普通 API 订阅配置"
                } finally { busy = false }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(start = contentSidePadding, end = contentSidePadding),
    ) {
        item {
            ProviderSection(title = "手机本地账号") {
                EtaPreference(
                    title = "无需服务器 · 本地加密",
                    summary = "电脑端完成账号登录并导出 JSON，复制到手机后在这里导入。凭据通过 Android Keystore AES-GCM 加密保存在本机，不上传到第三方中转网关。",
                )
                EtaPreference(
                    title = "已保存 ${accounts.size} 个账号",
                    summary = "仅显示脱敏账号名称与区域；实际可用性以联网调用结果为准",
                )
                accounts.take(50).forEach { a ->
                    EtaPreference(title = a.name, summary = "ID：${a.maskedId} · ${a.realm}")
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EtaTextButton(
                        text = if (busy) "正在检查…" else "选择本地 JSON 文件",
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    )
                    preview?.let { p ->
                        EtaPreference(
                            title = "准备导入 ${p.count} 个账号",
                            summary = "只导入当前加密账号库；重复账号跳过，不覆盖已有凭据",
                        )
                        p.accounts.take(12).forEach { a ->
                            EtaPreference(title = a.nickname, summary = "${a.account} · ${a.realm}")
                        }
                        EtaTextButton(
                            text = if (confirm) "再次点击，确认写入本机" else "导入 JSON 到本地账号库",
                            enabled = !busy && picked != null,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (!confirm) {
                                    confirm = true
                                } else {
                                    confirm = false
                                    val uri = picked
                                    if (uri != null) {
                                        scope.launch {
                                            busy = true
                                            status = null
                                            try {
                                                val result = withContext(Dispatchers.IO) {
                                                    WorkBuddyLocalVault.importJson(context, uri)
                                                }
                                                picked = null
                                                preview = null
                                                accounts = withContext(Dispatchers.IO) {
                                                    WorkBuddyLocalVault.summaries()
                                                }
                                                status = "新增 ${result.added} 个，跳过重复 ${result.duplicates} 个；本地共 ${result.total} 个。现在可在「模型」页选择可用模型。"
                                            } catch (cancel: CancellationException) {
                                                throw cancel
                                            } catch (_: Exception) {
                                                status = "本地导入失败；请核对 JSON 文件和系统密钥库"
                                            } finally { busy = false }
                                        }
                                    }
                                }
                            },
                        )
                    }
                    EtaTextButton(
                        text = "刷新本地账号列表",
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { refresh() },
                    )
                }
            }
        }
        item {
            ProviderSection(title = "使用说明") {
                EtaPreference(
                    title = "直接连接 CodeBuddy",
                    summary = "请选择此「WorkBuddy · 本地账号」服务商。旧的「WorkBuddy Manager」需独立 HTTPS 服务，不适用于纯手机本地模式。",
                )
                EtaPreference(
                    title = "协议与网络",
                    summary = "本地直连使用上游原生流式 Chat 格式。Responses / Messages 属于网关转换协议，不能直接发给 CodeBuddy 上游。",
                )
            }
        }
        status?.let { value ->
            item {
                Text(
                    text = value,
                    style = MiuixTheme.textStyles.footnote2,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                )
            }
        }
    }
}
