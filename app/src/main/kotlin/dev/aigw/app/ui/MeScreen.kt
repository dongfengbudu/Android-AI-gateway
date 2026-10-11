package dev.aigw.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.aigw.app.ui.theme.LocalAppColors
import dev.aigw.app.ui.theme.SuccessGreen
import dev.aigw.app.ui.theme.formatHex
import dev.aigw.core.usage.StorageAudit
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun MeScreen(state: AppUiState, viewModel: AppViewModel, open: (SubPage) -> Unit) {
    val context = LocalContext.current
    // 点标题「我的」弹出作者信息
    var showAbout by remember { mutableStateOf(false) }
    AboutDialog(show = showAbout, onDismiss = { showAbout = false })

    PageScaffold(
        title = "我的",
        subtitle = "网关与偏好设置",
        actions = {
            IconButton(onClick = { showAbout = true }) {
                Icon(MiuixIcons.Info, contentDescription = "关于")
            }
        },
    ) {
        // 顶部事实陈述：全部取自可核实的运行状态
        item {
            Box(Modifier.padding(bottom = 12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (state.running) "本地网关 · 运行中" else "本地网关 · 已停止",
                                style = MiuixTheme.textStyles.headline2,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "监听 :${state.port} · 供应商 ${state.providers.size}",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Text(
                            text = "${state.providers.size} 项",
                            style = MiuixTheme.textStyles.headline1,
                            fontWeight = FontWeight.Light,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                }
            }
        }

        item {
            Box(Modifier.padding(bottom = 12.dp)) {
                EditorialGroupHeader("01", "外观风格 · APPEARANCE")
            }
        }
        item {
            Box(Modifier.padding(bottom = 12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    EditorialSwitchRow(
                        index = "01.1",
                        title = "动态取色 · DYNAMIC COLOR",
                        subtitle = "跟随系统壁纸生成配色（Android 12 及以上）",
                        checked = state.dynamicColor,
                        onCheckedChange = { viewModel.setDynamicColor(it) },
                    )
                    EditorialRow(
                        "01.2",
                        "外观主题 · THEME",
                        "明暗模式、配色方案与金色文字",
                        appearanceMeta(state),
                    ) { open(SubPage.AppearanceTheme) }
                }
            }
        }

        item {
            Box(Modifier.padding(bottom = 12.dp)) {
                EditorialGroupHeader("02", "核心功能偏好 · CORE FUNCTIONS")
            }
        }
        item {
            Box(Modifier.padding(bottom = 12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    EditorialRow("02.1", "接入设置 · ENDPOINTS", "端口与 API Key", ":${state.port}") { open(SubPage.ApiSettings) }
                    EditorialRow(
                        "02.2",
                        "安全防护 · SECURITY",
                        "反审核脱敏与账号限速",
                        if (state.security.sanitizeEnabled) "脱敏开启" else "脱敏关闭",
                    ) { open(SubPage.SecurityProtection) }
                    EditorialRow(
                        "02.3",
                        "额度中心 · CREDITS",
                        "按供应商查看额度包明细",
                        if (state.pool.creditsKnown > 0) formatNumber(state.pool.totalCredits) else "",
                    ) { open(SubPage.CreditCenter) }
                    EditorialRow("02.4", "用量统计 · TELEMETRY", "请求数、token 与成功率", formatHumanTokens(state.today.totalTokens)) {
                        open(SubPage.Usage)
                    }
                    EditorialRow(
                        "02.5",
                        "代理设置 · NETWORK PROXY",
                        "境外供应商需要走代理",
                        if (state.proxy.usable) "已启用" else "未启用",
                    ) { open(SubPage.Proxy) }
                    EditorialRow(
                        "02.6",
                        "保活与权限 · BACKGROUND",
                        "通知、电池优化与后台限制",
                        if (state.keepAlive?.allGood == true) "就绪" else "待配置",
                    ) { open(SubPage.KeepAlive) }
                    EditorialRow(
                        "02.7",
                        "数据管理 · STORAGE AUDIT",
                        "存储占用、清理与截断",
                        StorageAudit.formatSize(state.storage?.totalChars ?: 0L),
                    ) {
                        viewModel.refreshStorage()
                        open(SubPage.DataManagement)
                    }
                }
            }
        }

        item {
            Box(Modifier.padding(bottom = 12.dp)) {
                EditorialGroupHeader("03", "排查诊断 · DIAGNOSTICS")
            }
        }
        item {
            Box(Modifier.padding(bottom = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("详细日志", style = MiuixTheme.textStyles.body2)
                        Text(
                            "记录每次调用的请求、转发、发送与返回原文，用于排查 503 等问题；关闭后立即生效",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Switch(
                        checked = state.settings.verboseLogging,
                        onCheckedChange = {
                            viewModel.updateSettings(state.settings.copy(verboseLogging = it))
                        },
                    )
                }
            }
        }

        item {
            Box(Modifier.padding(bottom = 12.dp)) {
                EditorialGroupHeader("04", "架构关于 · ABOUT THE ENGINE")
            }
        }
        item {
            Box(Modifier.padding(bottom = 12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ai-gateway · 本地 OpenAI 兼容网关",
                        style = MiuixTheme.textStyles.body2,
                    )
                    Text(
                        text = "把多个上游供应商（Trae / Loomy / WorkBuddy / Antigravity / 自定义）" +
                            "统一成一个 OpenAI 兼容端点，供任意客户端调用。",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Text(
                        text = "应用版本 ${appVersion(context)}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        item {
            Box(Modifier.padding(bottom = 12.dp)) {
                EditorialRow(
                    "04.1",
                    "检查更新 · CHECK UPDATE",
                    "从 GitHub Releases 获取最新版本",
                    if (state.updateChecking) "检查中…" else "",
                ) { if (!state.updateChecking) viewModel.checkUpdate() }
            }
        }

        item {
            Box(Modifier.padding(bottom = 12.dp)) {
                EditorialRow(
                    "04.2",
                    "项目地址 · GITHUB",
                    "在浏览器打开源码仓库",
                    "GitHub",
                ) { viewModel.openProjectRepo() }
            }
        }

        item {
            Box(Modifier.padding(bottom = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("自动检查更新", style = MiuixTheme.textStyles.body2)
                        Text(
                            "打开应用时后台检测，每天最多一次",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Switch(
                        checked = state.autoCheckUpdate,
                        onCheckedChange = { viewModel.setAutoCheckUpdate(it) },
                    )
                }
            }
        }
    }
}

/**
 * 编辑式条目：左侧等宽编号 + 标题 + 灰色副标题，右侧真实元数据（为空则不显示）+ 金色箭头。
 */
@Composable
private fun EditorialRow(
    index: String,
    title: String,
    subtitle: String,
    meta: String,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().pressScale().clickable(onClick = onClick).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = index,
                style = MiuixTheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurface)
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MiuixTheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Text(text = "→", style = MiuixTheme.textStyles.body2, color = LocalAppColors.current.goldStart)
        }
        HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
    }
}

/** 编辑式开关条目：编号 + 标题 + 副标题 + 右侧 Switch（与 [EditorialRow] 同一栅格）。 */
@Composable
private fun EditorialSwitchRow(
    index: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = index,
                style = MiuixTheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurface)
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
    }
}

/** 外观主题条目的元信息：当前模式 + 是否自定义金色。 */
private fun appearanceMeta(state: AppUiState): String {
    val mode = when (state.themeMode) {
        dev.aigw.app.ui.theme.ThemeMode.Light -> "浅色"
        dev.aigw.app.ui.theme.ThemeMode.Dark -> "深色"
        dev.aigw.app.ui.theme.ThemeMode.System -> "自动"
    }
    val customGold = formatHex(state.goldStart) != "#D4A843" || formatHex(state.goldEnd) != "#B88A20"
    return mode + " · " + (if (customGold) formatHex(state.goldStart) else "默认金")
}

private fun appVersion(context: android.content.Context): String = runCatching {
    val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
    val name = pkg.versionName.orEmpty()
    @Suppress("DEPRECATION")
    val code = pkg.versionCode
    "${name.ifEmpty { "—" }}（build $code）"
}.getOrDefault("—")

/** 点「我的」标题弹出的作者信息：两行居中，点任意处关闭。 */
@Composable
private fun AboutDialog(show: Boolean, onDismiss: () -> Unit) {
    WindowDialog(
        show = show,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "夕白",
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "3884021834",
                style = MiuixTheme.textStyles.main,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton("关闭", onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun UsageScreen(state: AppUiState, viewModel: AppViewModel, onBack: () -> Unit) {
    PageScaffold(
        title = "用量统计",
        subtitle = "累计与今日",
        leading = { BackButton(onBack) },
    ) {
        item {
            Box(Modifier.padding(bottom = 12.dp)) {
                SectionCard(enterIndex = 3) {
                    SectionLabel("今日")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatCell(formatNumber(state.today.requests), "请求")
                        StatCell(formatNumber(state.today.success), "成功")
                        StatCell(formatNumber(state.today.failed), "失败")
                    }
                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.5f))
                    TokenUsageRow("输入 Tokens", state.today.promptTokens, MiuixTheme.colorScheme.primary)
                    TokenUsageRow("输出 Tokens", state.today.completionTokens, SuccessGreen)
                    TokenUsageRow("总计 Tokens", state.today.totalTokens, MiuixTheme.colorScheme.secondary)
                }
            }
        }

        item {
            Box(Modifier.padding(bottom = 12.dp)) {
                SectionCard(enterIndex = 4) {
                    SectionLabel("累计")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatCell(formatNumber(state.total.requests), "请求")
                        StatCell(formatNumber(state.total.success), "成功")
                        StatCell(formatNumber(state.total.failed), "失败")
                    }
                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.5f))
                    TokenUsageRow("输入 Tokens", state.total.promptTokens, MiuixTheme.colorScheme.primary)
                    TokenUsageRow("输出 Tokens", state.total.completionTokens, SuccessGreen)
                    TokenUsageRow("总计 Tokens", state.total.totalTokens, MiuixTheme.colorScheme.secondary)
                }
            }
        }

        item {
            Box(Modifier.padding(bottom = 12.dp)) {
                SectionCard(enterIndex = 5) {
                    SectionLabel("最近调用")
                    if (state.calls.isEmpty()) {
                        EmptyHint("还没有调用记录", "客户端发起请求后，这里会显示模型、账号与耗时。")
                    } else {
                        for (record in state.calls.take(20)) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "${record.providerId}/${record.model}",
                                    style = MiuixTheme.textStyles.body2,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${record.status} · ${record.durationMillis}ms · " +
                                        "tokens ${record.totalTokens} · ${record.accountNickname.ifEmpty { record.accountUid }}",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Box(Modifier.padding(bottom = 12.dp)) {
                SectionCard(enterIndex = 6) {
                    SectionLabel("存储")
                    Text(
                        text = "调用记录 ${state.storedCalls} 条，占用约 ${StorageAudit.formatSize(state.storage?.totalChars ?: 0L)}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}


/** 格式化数字：带千分位逗号 */
private fun formatNumber(n: Long): String = String.format(java.util.Locale.US, "%,d", n)

/** 人类友好的数字紧凑缩写（如 73.25M / 276.6K） */
private fun formatHumanTokens(tokens: Long): String = when {
    tokens >= 1_000_000_000L -> String.format(java.util.Locale.US, "%.2fB", tokens / 1_000_000_000.0)
    tokens >= 1_000_000L -> String.format(java.util.Locale.US, "%.2fM", tokens / 1_000_000.0)
    tokens >= 1_000L -> String.format(java.util.Locale.US, "%.1fK", tokens / 1_000.0)
    else -> tokens.toString()
}

/** 方案 C：用量统计单项明细行 */
@Composable
private fun TokenUsageRow(
    label: String,
    tokens: Long,
    indicatorColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(indicatorColor),
            )
            Text(
                text = label,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = formatNumber(tokens),
                style = MiuixTheme.textStyles.headline2,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            if (tokens >= 1_000L) {
                Text(
                    text = formatHumanTokens(tokens),
                    style = MiuixTheme.textStyles.footnote2,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
    }
}
