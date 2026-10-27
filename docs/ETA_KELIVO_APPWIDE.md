# ETA 全应用 × Kelivo 工程经验迁移

> 范围：**整个 ETA**，而不是仅 WorkBuddy。这里的 Kelivo 是 `Zhanfg/kelivo` 的应用工程经验参考；ETA 保留 Kotlin/Compose/Miuix 和既有 Agent 执行架构。Kelivo 的 AGPL-3.0 源码、图标和素材**不直接复制**。

## 第一阶段：共同基础设施（本分支已修改）

| 横向能力 | Kelivo 参考实践 | ETA 独立实现与影响面 | 验证 |
| --- | --- | --- | --- |
| 视觉系统 | `lib/theme/design_tokens.dart`、`app_semantic_colors.dart` 的统一间距与语义色 | `EtaDesignTokens.kt`；由共享卡片、偏好行、功能入口、列表空状态复用，主题随 Miuix/MONET 更换 | Kotlin 单测及视觉检查待验 |
| 响应式布局 | `lib/shared/responsive` 的统一断点及内容宽度 | `AdaptivePageLayout.kt` 全页面共用连续伸缩的内容宽度；避免平板突然跳变 | `AdaptivePageLayoutTest.kt` |
| Agent 状态 | 状态优先呈现，错误/进行/完成语义清楚 | `StatusColors.kt` 从当前表面派生 WCAG 对比度；`AgentStatusCard.kt` 增加运行状态文字与语义描述 | 屏幕阅读器、深浅色待实机验收 |
| 空状态 | 指引优先的页面动作 | `ListEmptyState.kt` 使用共同间距/尺寸；保留可触发的具体操作入口 | 页面回归待验 |
| 功能入口一致性 | 可复用卡片层级与控件尺寸 | `EtaCard.kt`、`EtaFeatureCard.kt`、`EtaPreferenceStyle.kt` 共用 Token，不逐页散落常量 | UI 组件测试待验 |

## 第二阶段：全 ETA 功能链路审计（不能用第一阶段冒充完成）

| 子系统 | ETA 实有基础 | 下一项验收 |
| --- | --- | --- |
| 首页/导航/多窗口 | `AgentAppShell`、`AgentNavigator`、`AdaptivePageLayout` | 手机/平板/横屏连续尺寸、返回栈与状态恢复、焦点和可访问性 |
| 聊天/Markdown/流式 | `AgentChatBody`、`AgentChatInputBar`、Markdown pipeline | 大对话帧耗时、增量更新限流、渲染一致性、长内容滚动保持 |
| Agent 任务/工具 | `AgentRunEventCoalescer`、`AgentRunRecoveryCoordinator`、工具执行层 | 中断/重试/后台恢复、取消语义与重复执行保护 |
| 模型/服务商 | Provider runtime、已有三协议客户端 | 模型能力元数据、Responses / Messages 的工具调用一致性、错误分类 |
| MCP/技能 | 内置 MCP Server 和技能管理 | 安装/更新/回滚、离线复原、权限提示和风险边界 |
| 终端/文件/浏览器 | Android terminal、workspace、browser | 应用沙箱/Root/SAF 权限差异、数据丢失防护、异常终止恢复 |
| 设置/备份/迁移 | Appearance/Backup/Settings 各模块 | 设置搜索、导出验证、恢复预览、不可逆操作确认 |
| WorkBuddy | 独立分支集成 Go ARM64 核心 + 本地账号 | 仅作为 Provider 之一验收；不得绑架全应用设计决策 |

## 发布门槛

1. **编译可用**：Lint + JUnit + assembleDebug 的完整结论；主分支不受影响。
2. **视觉可用**：浅色、深色、Monet、纯黑、1.0/放大字体、窄屏/宽屏，以及无障碍交互截图。
3. **业务可用**：会话、模型、工具、文件、终端、MCP、备份的数据通路分别实测，不能用单一模拟接口代替。
4. **安全及许可**：不引入来源不明素材；不直接移植 AGPL 源文件；密钥不进入日志或公有构建产物。
5. **性能可用**：长对话渲染、冷启动、后台恢复、进程重建和网络失败场景回归。

当前这只是**第一批全局基础设施变更**，不是宣布完成上述各功能模块的深度重构。合并前保留原 ETA main 基线，CI 和设备验证分别报告。
