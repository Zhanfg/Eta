# ETA 全应用改进：从 Kelivo 借鉴架构与交互原则

**范围：整个 ETA，不限 WorkBuddy。** 本分支以 ETA 原始 `main` 为基线，可独立于尚未完成真机验收的 WorkBuddy 迁移合并。

## 使用原则

- 借鉴 `Zhanfg/kelivo` 已体现的模式，不复制 Flutter/AGPL-3.0 代码、主题资源或专有文件。
- ETA 继续使用 Kotlin、Jetpack Compose、Miuix、当前 Agent Runtime 和 Android 原生权限体系。
- 不把 Kelivo 的 Workspace、Pi 运行时或 PRoot 当作 ETA 的依赖，也不覆盖 ETA 已有的可用能力。
- 用户视觉设置、设备差异、系统壁纸、无障碍对比度和中文字体首先被视为跨页面约束。

## 全应用模块映射

| Kelivo 中的设计/工程经验 | ETA 落点 | 本轮状态 |
| --- | --- | --- |
| 设计令牌集中治理 | `EtaDesignTokens`：间距、几何、动画、48dp 触控尺寸 | 已实现；用于主导航、聊天输入、卡片、设置行、工具与空态 |
| 动态主题集中计算 | `AgentAppTheme`：Monet 原生系统配色、用户强调色、纯黑、对比度修复 | 已实现；单一主题入口 |
| Surface ladder 与语义颜色 | `EtaSemanticPalette`：页面/卡片/填充/分割线、动态成功/告警/执行状态 | 已实现；交互状态和空态可用 |
| 可读性而非只改变颜色 | `EtaAccessibilityPalette`：最终调色板中正文角色 4.5:1、语义图示 3:1 | 已实现，已添加单元测试 |
| 页间一致的视觉层次 | `EtaCard`、`EtaFeatureCard`、`EtaPreferenceStyle`、`MiuixScaffoldPage` | 已接入 |
| 聊天稳定位置与响应布局 | `AgentChatInputBar`、`AgentAppShell` | 令牌应用；复杂动效和桌面布局验收待续 |
| Agent 阶段与工具状态可观察 | `AgentStatusCard`、`AgentToolActivity`、`PermissionHealthCard` | 改为语义色与可点按尺寸 |
| 可恢复的运行状态与 durable journal | `AgentRunCheckpointStore`、`AgentRunRecoveryCoordinator`、`AgentPendingResultRecovery` | ETA 现有能力；需要专项故障注入验收 |
| 模型 Provider 隔离/路由 | `AgentModelClient` / `ProviderClientFactory` | 已有能力；协议/流式兼容矩阵需后续扩展 |
| 文件与环境隔离 | ETA 内部 Terminal/Workspace/Root 运行环境 | 专项安全与可恢复性审计待续 |
| 备份/迁移/设置生命周期 | ETA Backup + DataStore / Appearance Settings | 跨版本迁移测试待续 |
| 权限与工具真实性 | Agent Permission Health + Tool Runtime | 语义状态色已改；工具回归与真机校验待续 |

**不声称**一次设计系统提交已经覆盖上述所有剩余验收项，也不因此宣称 Material 3 Expressive 官方完整合规。视觉和架构回归必须通过 Android 构建及真实设备测试。

## 发布门槛

1. Lint、unit tests、Debug APK 对最新分支提交绿色。
2. Monet 系统壁纸配色、指定强调色、浅色/深色/纯黑场景对比度测试。
3. Chat、Agent Tool、Provider、Settings、Skills、Permissions、History、Memory、Terminal 在 1.0x / 大字体 / 窄屏 / 600dp+ 宽屏验证。
4. 系统进程死亡后的 Agent Checkpoint 恢复、取消与工具授权语义不回退。
5. 若未来合并 WB PR，合并后重新运行双分支的端到端测试和 APK 构建，不能以单独通过代替合并后的通过。
