package io.github.mangi.eta.ui.pages.providers

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import android.net.Uri
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.data.model.withApiKey
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.RuntimeConfigRepository
import io.github.mangi.eta.data.workbuddy.WorkBuddyGateway
import io.github.mangi.eta.data.workbuddy.WorkBuddyAccountImport
import io.github.mangi.eta.ui.components.EtaPreference
import io.github.mangi.eta.ui.components.EtaTextButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * All controls below are native Compose widgets. No WebView and no WebUI dependency.
 * The backend remains a separate WorkBuddy Manager installation.
 */
@Composable
internal fun WorkBuddyNativeTab(
    provider: ProviderSetting,
    scrollBehavior: ScrollBehavior,
    contentSidePadding: Dp,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var managementToken by remember(provider.id) { mutableStateOf("") }
    var realm by remember(provider.id) { mutableStateOf("cn") }
    var region by remember(provider.id) { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var usage by remember(provider.id) { mutableStateOf<WorkBuddyGateway.Usage?>(null) }
    var health by remember(provider.id) { mutableStateOf<Boolean?>(null) }
    var pool by remember(provider.id) { mutableStateOf<WorkBuddyGateway.Pool?>(null) }
    var authorization by remember(provider.id) { mutableStateOf<WorkBuddyGateway.Authorization?>(null) }
    var poll by remember(provider.id) { mutableStateOf<String?>(null) }
    var confirmReplace by remember(provider.id) { mutableStateOf(false) }
    // SAF grants are transient; never keep credential JSON bytes, administrator
    // cookies, or a readback of tokens in Compose state.
    var selectedAccountJson by remember(provider.id) { mutableStateOf<Uri?>(null) }
    var accountPreview by remember(provider.id) { mutableStateOf<WorkBuddyAccountImport.Preview?>(null) }
    var importResult by remember(provider.id) { mutableStateOf<WorkBuddyAccountImport.Result?>(null) }
    var adminUsername by remember(provider.id) { mutableStateOf("") }
    var adminPassword by remember(provider.id) { mutableStateOf("") }
    var confirmImport by remember(provider.id) { mutableStateOf(false) }

    fun runAction(action: suspend () -> Unit) {
        if (working) return
        scope.launch {
            working = true
            message = null
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Never reveal credentials or untrusted upstream response bodies.
                message = failure.message?.take(160) ?: "操作失败，请检查服务配置"
            } finally {
                working = false
            }
        }
    }

    val jsonPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected ->
        selectedAccountJson = null
        accountPreview = null
        importResult = null
        confirmImport = false
        if (selected != null) {
            runAction {
                val parsed = WorkBuddyAccountImport.preview(context, selected)
                selectedAccountJson = selected
                accountPreview = parsed
                message = "已校验 ${parsed.count} 个账号。请确认目标网关及管理员身份后再导入。"
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(start = contentSidePadding, end = contentSidePadding),
    ) {
        item {
            ProviderSection(title = "WorkBuddy 原生网关") {
                EtaPreference(
                    title = "独立账号池 / API 网关",
                    summary = "ETA 直接使用 WorkBuddy Manager 的 API；无需 WebUI。请先在配置页填写你的 HTTPS 网关 /v1 地址。",
                )
                EtaTextButton(
                    text = if (working) "处理中…" else "检查网关与本密钥额度",
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    onClick = {
                        runAction {
                            health = WorkBuddyGateway.health(provider.baseUrl)
                            usage = WorkBuddyGateway.usage(provider)
                            message = "查询完成（积分和用量为管理端记录的快照）"
                        }
                    },
                )
                health?.let { value ->
                    EtaPreference(
                        title = "上游健康状态",
                        summary = if (value) "网关已连接上游" else "上游当前不可用",
                    )
                }
                usage?.let { record ->
                    EtaPreference(
                        title = record.keyName.ifBlank { "当前调用密钥" },
                        summary = "状态：${if (record.enabled && !record.expired) "有效" else "禁用或过期"} · 区域：${record.realm.ifBlank { "未限定" }}",
                    )
                    EtaPreference(title = "Token 额度", summary = record.tokens.summary())
                    EtaPreference(title = "积分额度", summary = record.credits.summary())
                }
            }
        }
        item {
            ProviderSection(title = "账号池管理（原生）") {
                EtaPreference(
                    title = "管理令牌",
                    summary = "输入 wbt_ 管理令牌，仅保存在当前页面内存；与 wbk_ 模型调用密钥严格分离。账号扫码需 admin 权限。",
                )
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    TextField(
                        value = managementToken,
                        onValueChange = { managementToken = it },
                        label = "wbt_ 管理令牌",
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EtaTextButton(
                            text = "国内版 CN",
                            enabled = !working,
                            modifier = Modifier.weight(1f),
                            onClick = { realm = "cn"; authorization = null; poll = null },
                        )
                        EtaTextButton(
                            text = "国际版 Global",
                            enabled = !working,
                            modifier = Modifier.weight(1f),
                            onClick = { realm = "global"; authorization = null; poll = null },
                        )
                    }
                    Text(
                        text = "当前区域：${if (realm == "cn") "CN" else "Global"}",
                        style = MiuixTheme.textStyles.footnote2,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    if (realm == "global") {
                        TextField(
                            value = region,
                            onValueChange = { region = it },
                            label = "国际版地区代码，例如 HK",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    EtaTextButton(
                        text = "读取账号池",
                        enabled = !working && managementToken.startsWith("wbt_"),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            runAction {
                                pool = WorkBuddyGateway.accounts(provider.baseUrl, managementToken)
                                message = "账号池读取成功"
                            }
                        },
                    )
                }
                pool?.let { p ->
                    EtaPreference(
                        title = "账号数 ${p.total} · 已同步积分 ${p.poolSynced}",
                        summary = if (p.poolAvailable) "上游连接正常" else "上游连接异常",
                    )
                    p.accounts.take(30).forEachIndexed { index, account ->
                        EtaPreference(
                            title = "${index + 1}. ${account.nickname}",
                            summary = "签到记录：${if (account.checkinToday) "本端今日已记录" else "尚未记录"}",
                        )
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    EtaTextButton(
                        text = "添加本人授权的 CodeBuddy 账号",
                        enabled = !working && managementToken.startsWith("wbt_") && (realm != "global" || region.isNotBlank()),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            runAction {
                                authorization = WorkBuddyGateway.startAuthorization(
                                    provider.baseUrl, managementToken, realm, region,
                                )
                                poll = null
                                message = "已生成授权链接，请自行在官方页面完成授权"
                            }
                        },
                    )
                    authorization?.let { current ->
                        EtaTextButton(
                            text = "打开授权页面（非 WebUI）",
                            enabled = !working,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                message = openProviderAccountLink(context, current.url)
                                    ?: "已打开授权页面；完成后返回 ETA 检查状态"
                            },
                        )
                        EtaTextButton(
                            text = "检查扫码授权结果",
                            enabled = !working && managementToken.startsWith("wbt_"),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                runAction {
                                    val latest = WorkBuddyGateway.pollAuthorization(
                                        provider.baseUrl, managementToken, current, region,
                                    )
                                    poll = latest.status
                                    if (latest.status != "waiting") {
                                        pool = WorkBuddyGateway.accounts(provider.baseUrl, managementToken)
                                    }
                                    message = latest.message ?: "授权状态：${latest.status}"
                                }
                            },
                        )
                    }
                    poll?.let { Text("授权状态：$it", style = MiuixTheme.textStyles.footnote2) }
                }
            }
        }
        item {
            ProviderSection(title = "电脑登录 → 手机 JSON 导入") {
                EtaPreference(
                    title = "原生导入账号订阅",
                    summary = "在电脑导出 WorkBuddy / CodeBuddy 账号 JSON，传到手机后直接选择文件。无需网页控件；仅上传到你配置的 WorkBuddy Manager。敏感 Token 不会展示或保存于 ETA。",
                )
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EtaTextButton(
                        text = "选择账号 JSON 文件",
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            jsonPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                        },
                    )
                    accountPreview?.let { preview ->
                        EtaPreference(
                            title = "待导入 ${preview.count} 个账号",
                            summary = "文件已在本机通过格式校验。请确保这些账号属于你或已经获得账号持有人授权。",
                        )
                        preview.accounts.take(12).forEach { a ->
                            EtaPreference(
                                title = a.nickname,
                                summary = "账号：${a.account} · 区域：${a.realm}",
                            )
                        }
                        if (preview.count > 12) {
                            EtaPreference(title = "另外 ${preview.count - 12} 个账号未展开")
                        }
                    }
                    TextField(
                        value = adminUsername,
                        onValueChange = { adminUsername = it.take(64) },
                        label = "WorkBuddy 管理员用户名",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = adminPassword,
                        onValueChange = { adminPassword = it },
                        label = "管理员密码（仅本次上传）",
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    EtaPreference(
                        title = "安全与重复账号策略",
                        summary = "仅支持 JSON，单文件最多 2 MB；现有账号不覆盖。服务器要求独立管理员会话，不能用 wbt_ 或 wbk_ 替代。导入成功后账号凭据归你的服务端账号池管理。",
                    )
                    EtaTextButton(
                        text = when {
                            working -> "处理中…"
                            confirmImport -> "再次点击确认上传账号"
                            else -> "确认导入到当前 WorkBuddy 账号池"
                        },
                        enabled = !working && selectedAccountJson != null &&
                            accountPreview != null && adminUsername.isNotBlank() && adminPassword.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (!confirmImport) {
                                confirmImport = true
                            } else {
                                confirmImport = false
                                val file = selectedAccountJson
                                if (file != null) {
                                    runAction {
                                        val result = try {
                                            WorkBuddyAccountImport.importAccounts(
                                                context, file, provider.baseUrl, adminUsername, adminPassword,
                                            )
                                        } finally {
                                            adminPassword = "" // clear on success AND failure
                                        }
                                        importResult = result
                                        if (managementToken.startsWith("wbt_")) {
                                            pool = runCatching {
                                                WorkBuddyGateway.accounts(provider.baseUrl, managementToken)
                                            }.getOrNull()
                                        }
                                        message = "导入结果：新增 ${result.added}，已存在 ${result.rejected}，失败 ${result.failed}。请查看账号状态并同步模型列表。"
                                        selectedAccountJson = null
                                        accountPreview = null
                                    }
                                }
                            }
                        },
                    )
                    importResult?.let { result ->
                        EtaPreference(
                            title = "已成功导入 ${result.imported} 个账号",
                            summary = "新增 ${result.added} · 拒绝重复/无效 ${result.rejected} · 失败 ${result.failed}；不会静默覆盖已有凭据。",
                        )
                        result.livePoolTotal?.let { count ->
                            EtaPreference(
                                title = "服务端账号池现有 ${count} 个账号",
                                summary = "通过本次管理员会话实时读取。尚需服务端验证账号有效性与实际额度。",
                            )
                        }
                        result.liveAccounts.forEach { imported ->
                            EtaPreference(
                                title = imported.nickname,
                                summary = "账号：${imported.account} · 区域：${imported.realm} · " +
                                    "积分：${imported.credits?.toString() ?: "未同步"} · ${imported.poolStatus ?: "状态未知"}",
                            )
                        }
                    }
                }
            }
        }
        item {
            ProviderSection(title = "ETA 专属调用密钥") {
                EtaPreference(
                    title = "生成并直接绑定 wbk_ 密钥",
                    summary = "使用当前 wbt_ admin 令牌在服务端创建一把 ETA 专用密钥，并自动写入 ETA Provider。旧密钥如有配置会被替换；服务端默认无额度上限，请妥善管理。",
                )
                EtaTextButton(
                    text = when {
                        working -> "处理中…"
                        confirmReplace -> "再次点击确认替换并创建"
                        else -> "创建并绑定 ETA 调用密钥"
                    },
                    enabled = !working && managementToken.startsWith("wbt_"),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    onClick = {
                        if (provider.apiKey.isNotBlank() && !confirmReplace) {
                            confirmReplace = true
                        } else {
                            confirmReplace = false
                            runAction {
                                val newKey = WorkBuddyGateway.createInferenceKey(provider.baseUrl, managementToken, realm)
                                ProviderRepository.updateProvider(provider.withApiKey(newKey))
                                RuntimeConfigRepository.syncToRemotePreferences(EtaApp.serviceInstance)
                                usage = WorkBuddyGateway.usage(provider.withApiKey(newKey))
                                message = "ETA 专属调用密钥已创建并保存。请前往「模型」页同步可用模型。"
                            }
                        }
                    },
                )
            }
        }
        message?.let { text ->
            item {
                Text(
                    text = text,
                    style = MiuixTheme.textStyles.footnote2,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp),
                )
            }
        }
    }
}
