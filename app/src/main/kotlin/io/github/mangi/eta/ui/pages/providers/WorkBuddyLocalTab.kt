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
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.RuntimeConfigRepository
import io.github.mangi.eta.data.workbuddy.WorkBuddyAccountImport
import io.github.mangi.eta.data.workbuddy.WorkBuddyLocalModels
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

/** One-phone import and usage path. No WorkBuddy Manager server required. */
@Composable
internal fun WorkBuddyLocalTab(
    provider: ProviderSetting,
    scrollBehavior: ScrollBehavior,
    contentSidePadding: Dp,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedFile by remember(provider.id) { mutableStateOf<Uri?>(null) }
    var preview by remember(provider.id) { mutableStateOf<WorkBuddyAccountImport.Preview?>(null) }
    var confirmImport by remember(provider.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("直接导入电脑端的账号 JSON，不需要 API 地址、服务器或管理员密码。") }
    var accounts by remember { mutableStateOf<List<WorkBuddyLocalVault.AccountSummary>>(emptyList()) }
    var availableModels by remember { mutableStateOf<Int?>(null) }

    fun work(operation: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Do not render raw credential-bearing upstream responses.
                message = when (error) {
                    is IllegalArgumentException, is IllegalStateException -> error.message?.take(180) ?: "操作失败"
                    else -> "操作失败（${error.javaClass.simpleName}），请检查文件、网络和账号状态"
                }
            } finally { busy = false }
        }
    }

    val chooseJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        preview = null; selectedFile = null; confirmImport = false
        if (uri != null) work {
            val parsed = WorkBuddyAccountImport.preview(context, uri)
            selectedFile = uri
            preview = parsed
            message = "已在手机上校验 ${parsed.count} 个账号。确认后只会保存至本机加密账号库。"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(start = contentSidePadding, end = contentSidePadding),
    ) {
        item {
            ProviderSection(title = "本地订阅 · 无需服务器") {
                EtaPreference(
                    title = "从电脑导入 CodeBuddy JSON",
                    summary = "在电脑登录你的账号并导出 JSON，然后发送到这台手机。ETA 不运行 WebUI、不要求 Manager 登录、不需要复制任何 API Key。",
                )
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EtaTextButton(
                        text = "选择本机 JSON 文件",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        onClick = { chooseJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    )
                    preview?.let { value ->
                        EtaPreference(
                            title = "准备导入 ${value.count} 个账号",
                            summary = "凭据仅在上传确认后写入 Android Keystore 加密的本地私有存储；不会存储于 ETA Provider/API 配置。",
                        )
                        value.accounts.take(10).forEach { a ->
                            EtaPreference(title = a.nickname, summary = "${a.account} · ${a.realm}")
                        }
                        EtaTextButton(
                            text = if (confirmImport) "再次点击确认导入到本机" else "确认本地导入",
                            enabled = !busy && selectedFile != null,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (!confirmImport) { confirmImport = true }
                                else {
                                    confirmImport = false
                                    val uri = selectedFile
                                    if (uri != null) work {
                                        val imported = withContext(Dispatchers.IO) {
                                            WorkBuddyLocalVault.importJson(context, uri)
                                        }
                                        accounts = withContext(Dispatchers.IO) { WorkBuddyLocalVault.summaries() }
                                        selectedFile = null
                                        preview = null
                                        message = "本地导入：新增 ${imported.added}、跳过重复 ${imported.duplicates}、共 ${imported.total} 个账号。正在同步可用模型。"
                                        runCatching {
                                            val models = WorkBuddyLocalModels.fetch()
                                            ProviderRepository.replaceModels(provider.id, models)
                                            RuntimeConfigRepository.syncToRemotePreferences(EtaApp.serviceInstance)
                                            availableModels = models.size
                                        }.onFailure {
                                            message = "账号已安全保存，但模型列表未同步：${it.javaClass.simpleName}。请检查网络后点击「重新同步模型」。"
                                        }.onSuccess {
                                            message = "导入完成：本机已有 ${imported.total} 个账号，发现 ${availableModels} 个模型。请到「模型」页启用并选择模型。"
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        item {
            ProviderSection(title = "本机账号与模型") {
                EtaTextButton(
                    text = if (busy) "处理中…" else "刷新本地账号",
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    onClick = { work { accounts = withContext(Dispatchers.IO) { WorkBuddyLocalVault.summaries() } } },
                )
                accounts.forEach { a ->
                    EtaPreference(title = a.name, summary = "${a.maskedId} · ${a.realm}")
                }
                EtaTextButton(
                    text = "重新同步模型",
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    onClick = { work {
                        val models = WorkBuddyLocalModels.fetch()
                        ProviderRepository.replaceModels(provider.id, models)
                        RuntimeConfigRepository.syncToRemotePreferences(EtaApp.serviceInstance)
                        availableModels = models.size
                        message = "已同步 ${models.size} 个模型，请到「模型」页启用。"
                    } },
                )
                availableModels?.let {
                    EtaPreference(title = "已发现 ${it} 个模型", summary = "模型名单来自账号实时查询，未使用虚构的离线静态表。")
                }
            }
        }
        item {
            ProviderSection(title = "安全与限制") {
                EtaPreference(
                    title = "凭据始终保留在手机本地",
                    summary = "使用 Android Keystore AES-GCM 加密存储；模型调用只访问腾讯官方 HTTPS 域名。该模式不包含独立的 WorkBuddy 管理后台与管理员 Cookie。",
                )
                EtaPreference(
                    title = "与其他模式的关系",
                    summary = "本地账号模式使用 Tencent 原生 Chat SSE；独立 Manager 模式仍支持 Responses / Messages 翻译网关。协议不同不代表模型能力改变。",
                )
            }
        }
        item {
            Text(text = message, style = MiuixTheme.textStyles.footnote2,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
        }
    }
}
