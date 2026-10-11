package dev.aigw.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.aigw.core.usage.CallRecord
import dev.aigw.core.usage.CallStatus
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LogTab(val label: String) { Calls("调用记录"), Requests("请求日志") }

private enum class StatusFilter(val label: String) {
    ALL("全部"), SUCCESS("成功"), FAILED("失败"), ABORTED("中断"),
}

@Composable
fun LogsScreen(state: AppUiState, viewModel: AppViewModel) {
    var tab by remember { mutableStateOf(LogTab.Calls) }
    var filter by remember { mutableStateOf(StatusFilter.ALL) }
    var detail by remember { mutableStateOf<CallRecord?>(null) }

    // 停留在本页时自动刷新（只刷记录，不动低频数据）
    LaunchedEffect(tab) {
        while (true) {
            delay(5_000)
            viewModel.refreshRecords()
        }
    }

    val calls = remember(state.calls, filter) {
        state.calls.filter {
            when (filter) {
                StatusFilter.ALL -> true
                StatusFilter.SUCCESS -> it.status == CallStatus.SUCCESS
                StatusFilter.FAILED -> it.status == CallStatus.FAILED
                StatusFilter.ABORTED -> it.status == CallStatus.ABORTED
            }
        }
    }

    // 本页自带骨架：筛选区固定在顶部，列表用 LazyColumn 懒加载。
    // 记录最多数百条、每条含大段原文，一次性组合全部卡片会明显卡顿；
    // 而 LazyColumn 不能嵌在 PageScaffold 的 verticalScroll 里（无限高度约束会崩），所以这里不走 PageScaffold。
    Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
        PageHeader(
            title = "记录",
            subtitle = "调用与请求日志",
            actions = {
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(MiuixIcons.Refresh, contentDescription = "刷新")
                }
                StatusChip(state.running)
            },
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(LogTab.Calls.label, tab == LogTab.Calls) { tab = LogTab.Calls }
                ChoiceChip(LogTab.Requests.label, tab == LogTab.Requests) { tab = LogTab.Requests }
            }
            if (tab == LogTab.Calls) {
                Text(
                    text = "共 ${state.calls.size} 条记录",
                    style = MiuixTheme.textStyles.headline2,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusFilter.entries.forEach { option ->
                        ChoiceChip(option.label, filter == option, { filter = option })
                    }
                }
                Text(
                    text = "点卡片看原文 · 长按删除 · 停留本页自动刷新",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else {
                Text(
                    text = "共 ${state.logs.size} 条日志",
                    style = MiuixTheme.textStyles.headline2,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "网关与上游交互的流水，仅保留最近若干条。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (tab == LogTab.Calls) {
                if (calls.isEmpty()) {
                    item(key = "empty-calls") {
                        SectionCard {
                            EmptyHint(
                                title = "暂无调用记录",
                                message = "用任一 API Key 调用本地网关后，记录会出现在这里。",
                            )
                        }
                    }
                } else {
                    items(calls, key = { it.id }) { record ->
                        SectionCard {
                            CallRow(record, onClick = { detail = record }, onLongClick = { viewModel.deleteCall(record.id) })
                        }
                    }
                }
            } else {
                if (state.logs.isEmpty()) {
                    item(key = "empty-logs") {
                        SectionCard {
                            EmptyHint("暂无请求日志", "启动服务并发起调用后，这里会记录每次转发与换号。")
                        }
                    }
                } else {
                    itemsIndexed(state.logs) { _, line ->
                        SectionCard {
                            Text(
                                text = line.render(),
                                style = MiuixTheme.textStyles.footnote1.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    WindowBottomSheet(
        show = detail != null,
        title = "调用详情",
        onDismissRequest = { detail = null },
    ) {
        detail?.let { record ->
            CallDetailSheet(record)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallRow(record: CallRecord, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = record.model.ifEmpty { "未指定模型" },
                style = MiuixTheme.textStyles.headline2,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = statusLabel(record.status),
                style = MiuixTheme.textStyles.footnote2,
                color = statusColor(record.status),
            )
        }
        Text(
            text = buildString {
                append(TIME_FORMAT.format(Date(record.startedAtMillis)))
                append(" · ")
                append(if (record.streaming) "流式" else "非流式")
                append(" · ${record.durationMillis}ms")
                if (record.totalTokens > 0) append(" · ${record.totalTokens} tokens")
                if (record.pointsConsumed > 0) append(" · 消耗 ${record.pointsConsumed} 积分")
                if (record.accountNickname.isNotEmpty()) append(" · ${record.accountNickname}")
            },
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        if (record.error.isNotEmpty()) {
            Text(
                text = record.error,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.error,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun CallDetailSheet(record: CallRecord) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(record.id, style = MiuixTheme.textStyles.headline2, fontWeight = FontWeight.SemiBold)
        Text(
            text = "${TIME_FORMAT.format(Date(record.startedAtMillis))} · ${statusLabel(record.status)} · HTTP ${record.httpStatus}",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        DetailBlock("请求原文", record.requestBody) { clipboard.setText(AnnotatedString(record.requestBody)) }
        DetailBlock("响应原文", record.responseBody) { clipboard.setText(AnnotatedString(record.responseBody)) }
        if (record.rawResponse.isNotEmpty()) {
            DetailBlock("返回原文（上游报文）", record.rawResponse) { clipboard.setText(AnnotatedString(record.rawResponse)) }
        }
        if (record.error.isNotEmpty()) DetailBlock("错误", record.error) { clipboard.setText(AnnotatedString(record.error)) }
    }
}

@Composable
private fun DetailBlock(title: String, body: String, onCopy: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MiuixTheme.textStyles.body2, modifier = Modifier.weight(1f))
            Text(
                text = "复制",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onCopy)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Text(
            text = body.ifEmpty { "（空）" },
            style = MiuixTheme.textStyles.footnote1.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
        )
    }
}

private fun statusLabel(status: CallStatus): String = when (status) {
    CallStatus.SUCCESS -> "成功"
    CallStatus.FAILED -> "失败"
    CallStatus.ABORTED -> "中断"
}

@Composable
private fun statusColor(status: CallStatus): Color = when (status) {
    CallStatus.SUCCESS -> MiuixTheme.colorScheme.onTertiaryContainer
    CallStatus.FAILED -> MiuixTheme.colorScheme.error
    CallStatus.ABORTED -> Color(0xFFC97F0A)
}

private val TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
