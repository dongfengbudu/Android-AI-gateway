package dev.aigw.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.aigw.app.data.AutoTaskKind
import dev.aigw.core.usage.OperationEntry
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

        item { SmallTitle("运行日志") }
        item {
            SectionCard(enterIndex = 1) {
                TaskLogPanel(state.operations, viewModel)
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
                        "执行记录见上方「运行日志」或「日志」页。",
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

/** 面板最多展示的条数：日志池留 1000 条，面板只给最近一段，免得越滚越长。 */
private const val LOG_PANEL_LIMIT = 200

/** 面板固定高度：高于一屏的一半就会把操作按钮顶出视野。 */
private const val LOG_PANEL_HEIGHT_DP = 220

/** 批量任务要跑几分钟，日志是边跑边写的，停在本页时按这个间隔轮询。 */
private const val LOG_PANEL_REFRESH_MILLIS = 2_000L

private val LOG_TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

/**
 * 运行日志面板：固定高度、内部独立滚动、最新在上。
 *
 * 用 LazyColumn 而不是 Column：日志最多 200 条，普通 Column 会把它们全部组合出来，
 * 每 2 秒一次刷新就是一次全量重组。
 */
@Composable
private fun TaskLogPanel(operations: List<OperationEntry>, viewModel: AppViewModel) {
    LaunchedEffect(Unit) {
        viewModel.refreshOperations()
        while (true) {
            delay(LOG_PANEL_REFRESH_MILLIS)
            viewModel.refreshOperations()
        }
    }
    val entries = operations.take(LOG_PANEL_LIMIT)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "最近 ${entries.size} 条",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "完整记录见「日志」页",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        if (entries.isEmpty()) {
            EmptyHint("暂无日志", "按上面的按钮或发起对话后，记录会出现在这里。")
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
                    LogEntryLine(entry)
                }
            }
        }
    }
}

/** 一行日志：级别彩点 + 类别 + 时间在上，正文在下（正文放不下就截断，详情去「日志」页看）。 */
@Composable
private fun LogEntryLine(entry: OperationEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
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
                text = LOG_TIME_FORMAT.format(Date(entry.atMillis)),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Text(
            text = entry.message,
            style = MiuixTheme.textStyles.footnote1,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
