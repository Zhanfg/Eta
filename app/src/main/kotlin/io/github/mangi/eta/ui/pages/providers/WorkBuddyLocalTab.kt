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
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.data.model.Model
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.ModelRepository
import io.github.mangi.eta.data.repository.RuntimeConfigRepository
import io.github.mangi.eta.data.workbuddy.WorkBuddyAccountImport
import io.github.mangi.eta.data.workbuddy.WorkBuddyLocalModels
import io.github.mangi.eta.data.workbuddy.WorkBuddyLocalVault
import io.github.mangi.eta.data.workbuddy.WorkBuddyEmbeddedCore
import io.github.mangi.eta.data.workbuddy.WorkBuddyCoreDashboard
import io.github.mangi.eta.data.workbuddy.WorkBuddyBytePathProbe
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
    var backendStatus by remember { mutableStateOf("未检测") }
    var coreSummary by remember { mutableStateOf<WorkBuddyCoreDashboard.Summary?>(null) }
    var confirmPaidProbe by remember { mutableStateOf(false) }
    var lastProbe by remember { mutableStateOf<WorkBuddyBytePathProbe.Outcome?>(null) }
    var modelChoices by remember { mutableStateOf<List<Model>>(emptyList()) }

    LaunchedEffect(provider.id) {
        accounts = withContext(Dispatchers.IO) {
            runCatching { WorkBuddyLocalVault.summaries() }.getOrDefault(emptyList())
        }
    }

    LaunchedEffect(provider.id) {
        accounts = try {
            withContext(Dispatchers.IO) { WorkBuddyLocalVault.summaries() }
        } catch (_: Exception) {
            message = "无法读取本地加密账号库。请确认手机密钥库正常。"
            emptyList()
        }
    }

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
                            summary = "确认后写入本机加密账号库；同 UID 的旧凭据会被这次电脑导出的新凭据替换，用于续期，不影响其他账号。",
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
                                        // Force the embedded Go pool to reload the new encrypted imports.
                                        if (WorkBuddyEmbeddedCore.isBundled()) {
                                            withContext(Dispatchers.IO) { WorkBuddyEmbeddedCore.stop() }
                                            backendStatus = "待启动"
                                        }
                                        selectedFile = null
                                        preview = null
                                        message = "本地导入：新增 ${imported.added}、更新凭据 ${imported.refreshed}、共 ${imported.total} 个账号。正在同步可用模型。"
                                        runCatching {
                                            val models = WorkBuddyLocalModels.fetch()
                                            ModelRepository.syncRemoteModels(provider.id, models, models.mapTo(HashSet()) { it.modelId })
                                            modelChoices = ProviderRepository.providerById(provider.id)?.models.orEmpty()
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
            ProviderSection(title = "WorkBuddy Android 原生核心") {
                EtaPreference(
                    title = "运行时状态：$backendStatus",
                    summary = if (WorkBuddyEmbeddedCore.isBundled())
                        "APK 已包含 Android ARM64 Go 网关；需要启动验证才代表可用"
                    else "本 APK 没有打包 Go 原生核心，无法运行完整的本地账号池",
                )
                EtaTextButton(
                    text = if (busy) "检测中…" else "启动并验证原生后端",
                    enabled = !busy && WorkBuddyEmbeddedCore.isBundled(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    onClick = {
                        work {
                            val core = withContext(Dispatchers.IO) { WorkBuddyEmbeddedCore.ensureStarted() }
                            backendStatus = "本地网关运行中"
                            coreSummary = WorkBuddyCoreDashboard.load()
                            message = "Go 账号池已启动；状态与积分统计直接来自后端。" 
                        }
                    },
                )
                EtaTextButton(
                    text = "刷新 Go 账号池状态",
                    enabled = !busy && WorkBuddyEmbeddedCore.isBundled(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    onClick = {
                        work {
                            coreSummary = WorkBuddyCoreDashboard.load()
                            backendStatus = "已连接"
                        }
                    },
                )
                coreSummary?.let { value ->
                    EtaPreference(
                        title = "账号池 ${value.healthy} / ${value.total} 可用",
                        summary = "国内 ${value.cnTotal} · 国际 ${value.globalTotal} · " +
                            "冷却 ${value.cooling} · 已禁用 ${value.disabled} · " +
                            "并发已满 ${value.inFlightFull}",
                    )
                    EtaPreference(
                        title = "活跃粘性会话：${value.stickySessions}",
                        summary = "来自 workbuddy2api 内存中的真实会话路由状态",
                    )
                    EtaPreference(
                        title = "当日积分使用：${value.budgetUsed}",
                        summary = if (value.budgetLimit > 0)
                            "预算上限 ${value.budgetLimit}（仅统计上游确实返回的用量）"
                        else "未设置积分预算上限；已用量仅统计上游可观测记录",
                    )
                }
                EtaTextButton(
                    text = if (confirmPaidProbe) "再次点击确认消耗积分测试" else "真实订阅 SSE 字节通路测试",
                    enabled = !busy && WorkBuddyEmbeddedCore.isBundled(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    onClick = {
                        if (!confirmPaidProbe) {
                            confirmPaidProbe = true
                            message = "该测试会从你的真实订阅中调用一次模型、可能扣除积分。再次点击才会发送请求。"
                        } else {
                            confirmPaidProbe = false
                            work {
                                val outcome = WorkBuddyBytePathProbe.execute()
                                lastProbe = outcome
                                message = "真实通路通过：HTTP ${outcome.responseCode}，${outcome.sseEvents} 个 SSE 事件，" +
                                    "收到 ${outcome.visibleCharacters} 个可见字符，流正常结束。"
                            }
                        }
                    },
                )
                lastProbe?.let { value ->
                    EtaPreference(
                        title = if (value.completed) "最近一次真实通路测试通过" else "流式调用未完成",
                        summary = "HTTP ${value.responseCode} · SSE ${value.sseEvents} 个事件 · " +
                            "可见字符 ${value.visibleCharacters} 个（不记录具体聊天内容）",
                    )
                }
                EtaTextButton(
                    text = "停止本地核心并封存令牌",
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    onClick = {
                        work {
                            withContext(Dispatchers.IO) { WorkBuddyEmbeddedCore.stop() }
                            backendStatus = "已停止"
                            coreSummary = null
                            message = "核心进程已退出，本次运行时令牌已写回加密账号库。"
                        }
                    },
                )
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
                        ModelRepository.syncRemoteModels(provider.id, models, models.mapTo(HashSet()) { it.modelId })
                        modelChoices = ProviderRepository.providerById(provider.id)?.models.orEmpty()
                        RuntimeConfigRepository.syncToRemotePreferences(EtaApp.serviceInstance)
                        availableModels = models.size
                        message = "已同步 ${models.size} 个模型，请到「模型」页启用。"
                    } },
                )
                availableModels?.let {
                    EtaPreference(title = "已发现 ${it} 个模型", summary = "模型名单来自账号实时查询，未使用虚构的离线静态表。")
                }
                modelChoices.filter { it.isEnabled }.take(8).forEach { model ->
                    EtaTextButton(
                        text = "设为当前模型：${model.displayName}",
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        onClick = {
                            work {
                                RuntimeConfigRepository.setSelectedModelId(model.id)
                                RuntimeConfigRepository.syncToRemotePreferences(EtaApp.serviceInstance)
                                message = "已切换为 ${model.displayName}。返回 ETA 聊天即可使用本机账号。"
                            }
                        },
                    )
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
                    summary = "本地账号模式已改用官方 Go 网关协议和账号池；OpenAI Responses / Anthropic Messages 仍是管理网关可选的兼容转换层，不代表上游原生能力。",
                )
            }
        }
        item {
            Text(text = message, style = MiuixTheme.textStyles.footnote2,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
        }
    }
}
