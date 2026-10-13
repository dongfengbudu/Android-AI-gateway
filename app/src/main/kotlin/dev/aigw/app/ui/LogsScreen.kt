package dev.aigw.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import dev.aigw.app.ui.theme.SuccessGreen
import dev.aigw.app.ui.theme.WarnAmber
import dev.aigw.core.usage.CallRecord
import dev.aigw.core.usage.CallStatus
import dev.aigw.core.usage.LogLevel
import dev.aigw.core.usage.OperationEntry
import dev.aigw.core.usage.OperationLog
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.extra.WindowBottomSheet
import top.yukonga.miuix.kmp.extra.WindowDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 调用记录（结构化）与操作日志（文本）合并后的展示条目。 */
private sealed interface LogItem {
    val atMillis: Long

    data class Call(val record: CallRecord) : LogItem {
        override val atMillis: Long get() = record.startedAtMillis
    }

    data class Op(val entry: OperationEntry) : LogItem {
        override val atMillis: Long get() = entry.atMillis
    }
}

private enum class LevelFilter(val label: String, val level: LogLevel?) {
    ALL("全部", null), INFO("信息", LogLevel.INFO), WARN("警告", LogLevel.WARN), ERROR("错误", LogLevel.ERROR),
}

/** 统一条目映射到级别：调用记录失败=错误、中断=警告，让「只看问题」对调用也生效。 */
private fun levelOf(item: LogItem): LogLevel = when (item) {
    is LogItem.Call -> levelOf(item.record)
    is LogItem.Op -> item.entry.level
}

private fun levelOf(record: CallRecord): LogLevel = when (record.status) {
    CallStatus.FAILED -> LogLevel.ERROR
    CallStatus.ABORTED -> LogLevel.WARN
    CallStatus.SUCCESS -> LogLevel.INFO
}

@Composable
fun LogsScreen(state: AppUiState, viewModel: AppViewModel) {
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var level by rememberSaveable { mutableStateOf(LevelFilter.ALL) }
    var detail by remember { mutableStateOf<LogItem?>(null) }
    // 长按删除/清空不可恢复，均需二次确认
    var pendingDelete by remember { mutableStateOf<CallRecord?>(null) }
    var showClear by remember { mutableStateOf(false) }

    // 停留在本页时自动刷新（只刷记录，不动低频数据）；进入页面先刷一次，避免首帧展示旧数据
    LaunchedEffect(Unit) {
        viewModel.refreshRecords()
        while (true) {
            delay(5_000)
            viewModel.refreshRecords()
        }
    }

    val items = remember(state.calls, state.operations, category, level) {
        buildList {
            if (category == null || category == KEY_CALL) {
                state.calls.forEach { add(LogItem.Call(it)) }
            }
            state.operations
                .filter { category == null || it.category == category }
                .forEach { add(LogItem.Op(it)) }
        }
            .filter { level.level == null || levelOf(it) == level.level }
            .sortedByDescending { it.atMillis }
    }

    val errorCount = items.count { levelOf(it) == LogLevel.ERROR }
    val warnCount = items.count { levelOf(it) == LogLevel.WARN }

    PageScaffold(
        title = "日志",
        subtitle = "全部操作的统一记录",
        actions = {
            TextButton(text = "清空", onClick = { showClear = true })
            IconButton(onClick = { viewModel.refreshRecords() }) {
                Icon(MiuixIcons.Refresh, contentDescription = "刷新")
            }
        },
    ) {
        item(key = "filters") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 等宽铺满整行：横滑胶囊全挤在左边，既不齐也看不出是可选项
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChoiceChip("全部", category == null, modifier = Modifier.weight(1f)) { category = null }
                    ChoiceChip(
                        categoryLabel(KEY_CALL),
                        category == KEY_CALL,
                        modifier = Modifier.weight(1f),
                    ) { category = KEY_CALL }
                    ChoiceChip(
                        categoryLabel(OperationLog.CATEGORY_REQUEST),
                        category == OperationLog.CATEGORY_REQUEST,
                        modifier = Modifier.weight(1f),
                    ) { category = OperationLog.CATEGORY_REQUEST }
                    ChoiceChip(
                        categoryLabel(OperationLog.CATEGORY_LOGIN),
                        category == OperationLog.CATEGORY_LOGIN,
                        modifier = Modifier.weight(1f),
                    ) { category = OperationLog.CATEGORY_LOGIN }
                    unknownCategories(state.operations).forEach { extra ->
                        ChoiceChip(
                            categoryLabel(extra),
                            category == extra,
                            modifier = Modifier.weight(1f),
                        ) { category = extra }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LevelFilter.entries.forEach { option ->
                        ChoiceChip(option.label, level == option, modifier = Modifier.weight(1f)) { level = option }
                    }
                }
                Text(
                    text = "共 ${items.size} 条" +
                        (if (errorCount > 0) " · 错误 $errorCount" else "") +
                        (if (warnCount > 0) " · 警告 $warnCount" else ""),
                    style = MiuixTheme.textStyles.headline2,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "点条目看原文 · 调用记录长按删除 · 停留本页自动刷新",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        if (items.isEmpty()) {
            item(key = "empty") {
                SectionCard {
                    EmptyHint(
                        title = "暂无日志",
                        message = "发起调用、登录或任一操作后，记录会按时间统一出现在这里。",
                    )
                }
            }
        } else {
            itemsIndexed(items, key = { _, item -> itemKey(item) }) { index, item ->
                // 换筛选条件时整批条目淡入淡出 + 位移（animateItem 按 key 识别增删），
                // 让「列表换了」看得见。
                Column(modifier = Modifier.animateItem()) {
                    when (item) {
                        is LogItem.Call -> CallRow(
                            record = item.record,
                            onClick = { detail = item },
                            onLongClick = { pendingDelete = item.record },
                        )
                        is LogItem.Op -> OperationRow(entry = item.entry, onClick = { detail = item })
                    }
                    if (index != items.lastIndex) {
                        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }

    WindowBottomSheet(
        show = detail != null,
        title = if (detail is LogItem.Call) "调用详情" else "日志详情",
        onDismissRequest = { detail = null },
    ) {
        when (val item = detail) {
            is LogItem.Call -> CallDetailSheet(item.record)
            is LogItem.Op -> OperationDetailSheet(item.entry)
            null -> {}
        }
    }

    WindowDialog(
        show = pendingDelete != null,
        title = "删除调用记录",
        onDismissRequest = { pendingDelete = null },
    ) {
        Text(
            text = "将删除该条调用记录，删除后不可恢复。",
            style = MiuixTheme.textStyles.main,
        )
        DialogButtonRow(
            confirmText = "删除",
            onCancel = { pendingDelete = null },
            onConfirm = {
                pendingDelete?.let { viewModel.deleteCall(it.id) }
                pendingDelete = null
            },
        )
    }

    WindowDialog(
        show = showClear,
        title = "清空日志",
        onDismissRequest = { showClear = false },
    ) {
        Text(
            text = "将清空调用记录与全部操作日志（含用量统计聚合），删除后不可恢复。",
            style = MiuixTheme.textStyles.main,
        )
        DialogButtonRow(
            confirmText = "清空",
            onCancel = { showClear = false },
            onConfirm = {
                showClear = false
                viewModel.clearCalls()
                viewModel.clearLogs()
                viewModel.refreshRecords()
            },
        )
    }
}

/** 数据里出现、但界面没有固定胶囊的类别（新功能自带的新类别自动出现）。 */
private fun unknownCategories(operations: List<OperationEntry>): List<String> =
    operations.map { it.category }
        .distinct()
        .filter { it != KEY_CALL && it != OperationLog.CATEGORY_REQUEST && it != OperationLog.CATEGORY_LOGIN }

private fun itemKey(item: LogItem): String = when (item) {
    is LogItem.Call -> "call-${item.record.id}"
    is LogItem.Op -> "op-${item.entry.id}"
}

/** 操作日志一行：级别彩点 + 类别/时间在首行，正文在下，行间分割线由列表补。 */
@Composable
private fun OperationRow(entry: OperationEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(levelColor(entry.level)),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = categoryLabel(entry.category),
                style = MiuixTheme.textStyles.footnote2,
                fontWeight = FontWeight.SemiBold,
                color = levelColor(entry.level),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = TIME_FORMAT.format(Date(entry.atMillis)),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Text(
            text = entry.message,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallRow(record: CallRecord, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
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

private fun statusLabel(status: CallStatus): String = when (status) {
    CallStatus.SUCCESS -> "成功"
    CallStatus.FAILED -> "失败"
    CallStatus.ABORTED -> "中断"
}

private fun levelLabel(level: LogLevel): String = when (level) {
    LogLevel.INFO -> "信息"
    LogLevel.WARN -> "警告"
    LogLevel.ERROR -> "错误"
}

/** 状态语义色：成功绿/警示橙为固定语义色，错误红随主题。 */
@Composable
private fun statusColor(status: CallStatus): Color = when (status) {
    CallStatus.SUCCESS -> SuccessGreen
    CallStatus.FAILED -> MiuixTheme.colorScheme.error
    CallStatus.ABORTED -> WarnAmber
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
private fun OperationDetailSheet(entry: OperationEntry) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(categoryLabel(entry.category), style = MiuixTheme.textStyles.headline2, fontWeight = FontWeight.SemiBold)
        Text(
            text = "${TIME_FORMAT.format(Date(entry.atMillis))} · ${levelLabel(entry.level)}",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        DetailBlock("内容", entry.message) { clipboard.setText(AnnotatedString(entry.message)) }
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

private val TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
