package dev.aigw.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.aigw.core.gateway.GatewaySettings
import dev.aigw.core.usage.StorageAudit
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 数据管理：存储与清理。
 *
 * 调用记录会保存每次请求的输入/输出/思考链，是应用体积的主要来源，所以这里做两件事：
 * 让用户能改保留天数（服务运行时按此每日自动清理），以及提供几个手动清理动作。
 */
@Composable
fun DataManagementScreen(state: AppUiState, viewModel: AppViewModel, onBack: () -> Unit) {
    var showDetail by remember { mutableStateOf(false) }
    val retention = state.settings.logRetentionDays
    val records = state.storage?.recordsChars ?: 0L

    // 存储占用不挂在全局 refresh 里（要扫全量存储 + 遍历缓存目录，代价高），
    // 所以进入本页时才单独算一次；清理动作后也会由 refreshStorage 更新。
    LaunchedEffect(Unit) { viewModel.refreshStorage() }

    PageScaffold(
        title = "数据管理",
        subtitle = "存储与清理",
        leading = { BackButton(onBack) },
    ) {
        item {
            SectionCard(enterIndex = 0) {
                Text("使用记录", style = MiuixTheme.textStyles.headline2, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "记录会保存每次请求的输入/输出/思考链，是应用数据体积的主要来源；服务运行时按保留天数每日自动清理。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                RetentionField(retention) { viewModel.setLogRetentionDays(it) }
                HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                Text(
                    text = "当前占用 ${StorageAudit.formatSize(records)} · ${state.storedCalls} 条记录",
                    style = MiuixTheme.textStyles.body2,
                )
                Text(
                    text = "自动保留 $retention 天，服务运行时会每日自动清理",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        item { SmallTitle("清理") }
        item {
            SectionCard(enterIndex = 1) {
                OutlineActionButton("查看存储占用明细") { showDetail = !showDetail }
                if (showDetail) {
                    StorageDetail(state)
                }
                OutlineActionButton("截断超长记录内容") { viewModel.truncateLongRecords() }
                OutlineActionButton("清理过期记录") { viewModel.purgeExpiredRecords() }
            }
        }
    }
}

/**
 * 保留天数输入。
 *
 * 失焦或回车才提交，避免边输入边保存把「30」中间的「3」当成 30 天。越界值由 core 夹回，
 * 这里只做即时提示。
 */
@Composable
private fun RetentionField(current: Int, onCommit: (Int) -> Unit) {
    var text by remember(current) { mutableStateOf(current.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in GatewaySettings.MIN_RETENTION_DAYS..GatewaySettings.MAX_RETENTION_DAYS
    val changed = parsed != null && parsed != current

    Text(
        text = "保留天数（${GatewaySettings.MIN_RETENTION_DAYS}-${GatewaySettings.MAX_RETENTION_DAYS}）",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
    TextField(
        value = text,
        onValueChange = { input -> text = input.filter(Char::isDigit).take(4) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    when {
        text.isEmpty() -> Text(
            text = "不能为空",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.error,
        )
        !valid -> Text(
            text = "需在 ${GatewaySettings.MIN_RETENTION_DAYS}-${GatewaySettings.MAX_RETENTION_DAYS} 之间",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.error,
        )
    }
    if (changed) {
        OutlineActionButton("保存保留天数") { onCommit(parsed) }
    }
}

/** 存储占用明细：按前缀归类，占用从大到小。 */
@Composable
private fun StorageDetail(state: AppUiState) {
    val report = state.storage
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (report == null) {
            EmptyHint("暂无法统计", "稍后再试。")
        } else {
            report.entries.forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, style = MiuixTheme.textStyles.body2)
                        Text(
                            text = "${entry.detail} · ${entry.items} 项",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Text(
                        text = StorageAudit.formatSize(entry.chars),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
        HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
        Text(
            text = "统计在应用内完成，不联网；「账号与凭证」含 token，请勿外传。",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
