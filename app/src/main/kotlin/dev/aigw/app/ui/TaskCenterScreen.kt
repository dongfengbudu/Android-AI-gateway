package dev.aigw.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.aigw.app.data.AutoTaskKind
import dev.aigw.core.usage.TaskRunEntry
import dev.aigw.core.usage.TaskRunKind
import dev.aigw.core.usage.TaskRunSnapshot
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 任务中心：批量操作、定时任务开关与账号状态。
 *
 * 批量签到只对声明了签到能力的供应商（Trae / WorkBuddy）执行，国际版自动跳过；
 * 额度刷新对所有供应商生效（自定义供应商没有额度接口，会返回「不支持」）。
 */
@Composable
fun TaskCenterScreen(state: AppUiState, viewModel: AppViewModel, onBack: () -> Unit) {
    PageScaffold(
        title = "任务中心",
        subtitle = "签到、成长任务与定时执行",
        leading = { BackButton(onBack) },
    ) {
        item { SmallTitle("批量操作") }
        item {
            SectionCard(enterIndex = 0) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = { viewModel.checkinAll() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text("批量签到")
                        }
                        TextButton(
                            text = "批量任务",
                            onClick = { viewModel.runAllTasks() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    TextButton(
                        text = "一键完成成长任务",
                        onClick = { viewModel.runAutoTasks() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        text = "刷新全部额度",
                        onClick = { viewModel.refreshAllCredits() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = "签到作用于 Trae 与 WorkBuddy 国内版（国际版没有签到制度，自动跳过）。" +
                        "「批量任务」只领已经完成的；「一键完成成长任务」会先逐项补齐行为、回读进度再领奖，" +
                        "耗时可达数分钟，请耐心等待。额度刷新作用于全部账号。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        item { SmallTitle("执行日志") }
        item {
            SectionCard(enterIndex = 1) {
                TaskRunPanel(state.taskRun, viewModel)
            }
        }

        item { SmallTitle("定时任务") }
        item {
            GroupCard {
                for ((index, kind) in AutoTaskKind.entries.withIndex()) {
                    if (index > 0) GroupDivider()
                    SuperSwitch(
                        title = kind.label,
                        summary = "${kind.hourText} · ${kind.summary}",
                        checked = state.taskSwitches[kind] ?: true,
                        onCheckedChange = { viewModel.setTaskEnabled(kind, it) },
                    )
                }
            }
        }
        item {
            SectionCard(enterIndex = 2) {
                Text(
                    text = "下次执行：" + nextRunText(state.nextTaskAt),
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "定时任务跟着网关前台服务跑：服务停掉或被系统回收后不再触发，重新打开网关即恢复。" +
                        "执行记录见上方「执行日志」或「日志」页。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        if (state.providers.none { state.accountsOf(it.id).isNotEmpty() }) {
            item {
                SectionCard(enterIndex = 3) {
                    EmptyHint("还没有账号", "先去「供应商」页添加账号，再回来签到。")
                }
            }
        }

        for (info in state.providers) {
            val accounts = state.accountsOf(info.id)
            if (accounts.isEmpty()) continue
            item(key = info.id) { SmallTitle(info.displayName) }
            // 每个账号一张独立卡片（与供应商页一致）：多账号同卡时账号之间没有边界，
            // 且额度/状态挤在一行左对齐，看不出哪项属于哪个账号。
            accounts.forEachIndexed { index, account ->
                item(key = "${info.id}/${account.uid}") {
                    SectionCard(enterIndex = 4 + index) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = account.nickname.ifEmpty { account.uid },
                                style = MiuixTheme.textStyles.body2,
                                fontWeight = FontWeight.SemiBold,
                            )
                            HorizontalDivider(modifier = Modifier.fillMaxWidth())
                            AccountInfoRow(
                                label = "额度",
                                value = if (account.creditsKnown) account.credits.toString() else "—",
                            )
                            AccountInfoRow(
                                label = "状态",
                                value = accountStatusText(account),
                                valueColor = MiuixTheme.colorScheme.onSurface,
                            )
                            if (account.detail.isNotEmpty()) {
                                Text(
                                    text = account.detail,
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 下次执行时间：当天只报时分，隔天补「明天」或月日，避免用户要自己算。 */
private fun nextRunText(at: Long?): String {
    if (at == null) return "全部关闭，不会自动执行"
    val target = Calendar.getInstance().apply { timeInMillis = at }
    val clock = "%02d:%02d".format(target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE))
    val today = Calendar.getInstance()
    if (sameDay(today, target)) return "今天 $clock"
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    if (sameDay(tomorrow, target)) return "明天 $clock"
    return "%d月%d日 %s".format(target.get(Calendar.MONTH) + 1, target.get(Calendar.DAY_OF_MONTH), clock)
}

private fun sameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

/** 面板最多展示的条数：一轮「一键完成成长任务」就有上百条请求，只给最近一段。 */
private const val LOG_PANEL_LIMIT = 200

/** 面板固定高度：高于一屏的一半就会把操作按钮顶出视野。 */
private const val LOG_PANEL_HEIGHT_DP = 220

/** 批量任务要跑几分钟，流水是边跑边写的，停在本页时按这个间隔轮询。 */
private const val LOG_PANEL_REFRESH_MILLIS = 2_000L

private val LOG_TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

/** 行首标记：区分「走到哪一步」「发出去的」「收回来的」「最后结果」。 */
private fun kindMark(kind: TaskRunKind): String = when (kind) {
    TaskRunKind.PROGRESS -> "▸"
    TaskRunKind.REQUEST -> "→"
    TaskRunKind.RESPONSE -> "←"
    TaskRunKind.RESULT -> "="
}

private fun kindName(kind: TaskRunKind): String = when (kind) {
    TaskRunKind.PROGRESS -> "进度"
    TaskRunKind.REQUEST -> "请求"
    TaskRunKind.RESPONSE -> "响应"
    TaskRunKind.RESULT -> "结果"
}

@Composable
private fun kindColor(kind: TaskRunKind): Color = when (kind) {
    TaskRunKind.PROGRESS, TaskRunKind.RESULT -> MiuixTheme.colorScheme.primary
    TaskRunKind.REQUEST -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    TaskRunKind.RESPONSE -> MiuixTheme.colorScheme.onSurface
}

/**
 * 执行日志面板：本次（或最近一次）动作执行的实时流水。
 *
 * 展示的是 provider 内部真实发出的请求与上游响应，外加「正在跑哪个账号、哪一步」，
 * 用固定高度的 LazyColumn 独立滚动：一轮跑几百条，跟整页一起滚就找不着北了。
 */
@Composable
private fun TaskRunPanel(run: TaskRunSnapshot, viewModel: AppViewModel) {
    LaunchedEffect(Unit) {
        viewModel.refreshLogPanel()
        while (true) {
            delay(LOG_PANEL_REFRESH_MILLIS)
            viewModel.refreshLogPanel()
        }
    }
    val entries = run.entries.take(LOG_PANEL_LIMIT)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    run.label.isEmpty() -> "还没有执行记录"
                    run.running -> "执行中：${run.label}"
                    else -> "最近一次：${run.label}"
                },
                style = MiuixTheme.textStyles.footnote1,
                color = if (run.running) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${entries.size} 条",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        if (entries.isEmpty()) {
            EmptyHint("暂无执行记录", "点上面的按钮后，这里会实时显示发往上游的请求与响应。")
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LOG_PANEL_HEIGHT_DP.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MiuixTheme.colorScheme.surfaceVariant),
            ) {
                itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp))
                    }
                    TaskRunLine(entry)
                }
            }
        }
    }
}

/** 一行流水：标记 + 时间在上，正文在下；报文用等宽字体，长内容截断。 */
@Composable
private fun TaskRunLine(entry: TaskRunEntry) {
    val payload = entry.kind == TaskRunKind.REQUEST || entry.kind == TaskRunKind.RESPONSE
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = kindMark(entry.kind),
                style = MiuixTheme.textStyles.footnote2,
                fontWeight = FontWeight.SemiBold,
                color = kindColor(entry.kind),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = kindName(entry.kind),
                style = MiuixTheme.textStyles.footnote2,
                color = kindColor(entry.kind),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = LOG_TIME_FORMAT.format(Date(entry.atMillis)),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Text(
            text = entry.text,
            style = if (payload) {
                MiuixTheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace)
            } else {
                MiuixTheme.textStyles.footnote1
            },
            maxLines = if (entry.kind == TaskRunKind.RESULT) 6 else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
