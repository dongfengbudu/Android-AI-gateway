package dev.aigw.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.aigw.app.ui.theme.BackgroundTone
import dev.aigw.app.ui.theme.SuccessGreen
import dev.aigw.app.ui.theme.defaultThemeAccent
import dev.aigw.core.usage.StorageAudit
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.extra.WindowDialog

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
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
            }
        }

        item { SmallTitle("外观") }
        item {
            GroupCard {
                SuperSwitch(
                    title = "动态取色",
                    summary = "跟随系统壁纸生成配色（Android 12 及以上）",
                    checked = state.dynamicColor,
                    onCheckedChange = { viewModel.setDynamicColor(it) },
                )
                GroupDivider()
                SuperArrow(
                    title = "外观主题",
                    summary = "明暗模式、页面底色与强调色",
                    endActions = {
                        MetaText(appearanceMeta(state))
                    },
                    onClick = { open(SubPage.AppearanceTheme) },
                )
            }
        }

        item { SmallTitle("核心功能") }
        item {
            GroupCard {
                SuperArrow(
                    title = "接入设置",
                    summary = "端口与 API Key",
                    endActions = { MetaText(":${state.port}") },
                    onClick = { open(SubPage.ApiSettings) },
                )
                GroupDivider()
                SuperArrow(
                    title = "安全防护",
                    summary = "反审核脱敏与账号限速",
                    endActions = { MetaText(if (state.security.sanitizeEnabled) "脱敏开启" else "脱敏关闭") },
                    onClick = { open(SubPage.SecurityProtection) },
                )
                GroupDivider()
                SuperArrow(
                    title = "额度中心",
                    summary = "按供应商查看额度包明细",
                    endActions = {
                        if (state.pool.creditsKnown > 0) MetaText(formatNumber(state.pool.totalCredits))
                    },
                    onClick = { open(SubPage.CreditCenter) },
                )
                GroupDivider()
                SuperArrow(
                    title = "用量统计",
                    summary = "请求数、token 与成功率",
                    endActions = { MetaText(formatHumanTokens(state.today.totalTokens)) },
                    onClick = { open(SubPage.Usage) },
                )
                GroupDivider()
                SuperArrow(
                    title = "代理设置",
                    summary = "境外供应商需要走代理",
                    endActions = { MetaText(if (state.proxy.usable) "已启用" else "未启用") },
                    onClick = { open(SubPage.Proxy) },
                )
                GroupDivider()
                SuperArrow(
                    title = "保活与权限",
                    summary = "通知、电池优化与后台限制",
                    endActions = { MetaText(if (state.keepAlive?.allGood == true) "就绪" else "待配置") },
                    onClick = { open(SubPage.KeepAlive) },
                )
                GroupDivider()
                SuperArrow(
                    title = "数据管理",
                    summary = "存储占用、清理与截断",
                    endActions = { MetaText(StorageAudit.formatSize(state.storage?.totalChars ?: 0L)) },
                    onClick = {
                        viewModel.refreshStorage()
                        open(SubPage.DataManagement)
                    },
                )
            }
        }

        item { SmallTitle("日志与更新") }
        item {
            GroupCard {
                SuperSwitch(
                    title = "详细日志",
                    summary = "记录每次调用的请求、转发、发送与返回原文，用于排查 503 等问题；关闭后立即生效",
                    checked = state.settings.verboseLogging,
                    onCheckedChange = {
                        viewModel.updateSettings(state.settings.copy(verboseLogging = it))
                    },
                )
                GroupDivider()
                SuperArrow(
                    title = "检查更新",
                    summary = "从 GitHub Releases 获取最新版本",
                    endActions = { if (state.updateChecking) MetaText("检查中…") },
                    onClick = { if (!state.updateChecking) viewModel.checkUpdate() },
                )
                GroupDivider()
                SuperArrow(
                    title = "项目地址",
                    summary = "在浏览器打开源码仓库",
                    endActions = { MetaText("GitHub") },
                    onClick = { viewModel.openProjectRepo() },
                )
                GroupDivider()
                SuperSwitch(
                    title = "自动检查更新",
                    summary = "打开应用时后台检测，每天最多一次",
                    checked = state.autoCheckUpdate,
                    onCheckedChange = { viewModel.setAutoCheckUpdate(it) },
                )
            }
        }

        item { SmallTitle("关于") }
        item {
            SectionCard {
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
}

/** 分组行右侧的元信息小字（等宽风格去掉，统一主题小号灰字）。 */
@Composable
private fun MetaText(text: String) {
    if (text.isEmpty()) return
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/** 外观主题条目的元信息：当前模式 + 强调色相度数（默认时不展示）。 */
private fun appearanceMeta(state: AppUiState): String {
    val mode = when (state.themeMode) {
        dev.aigw.app.ui.theme.ThemeMode.Light -> "浅色"
        dev.aigw.app.ui.theme.ThemeMode.Dark -> "深色"
        dev.aigw.app.ui.theme.ThemeMode.System -> "自动"
    }
    val isDefaultHue = state.accentHue == defaultThemeAccent.normalizedHue
    val accentLabel = if (isDefaultHue) "默认靛蓝" else "色相 ${state.accentHue.toInt()}°"
    val toneLabel = if (state.backgroundTone == BackgroundTone.Theme) "" else " · " + state.backgroundTone.label
    return mode + " · " + accentLabel + toneLabel
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
        item { SmallTitle("今日") }
        item {
            SectionCard(enterIndex = 3) {
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

        item { SmallTitle("累计") }
        item {
            SectionCard(enterIndex = 4) {
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

        item { SmallTitle("最近调用") }
        item {
            SectionCard(enterIndex = 5) {
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

        item { SmallTitle("存储") }
        item {
            SectionCard(enterIndex = 6) {
                Text(
                    text = "调用记录 ${state.storedCalls} 条，占用约 ${StorageAudit.formatSize(state.storage?.totalChars ?: 0L)}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
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
