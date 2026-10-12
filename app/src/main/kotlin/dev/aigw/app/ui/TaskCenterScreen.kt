package dev.aigw.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.aigw.app.data.AutoTaskKind
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Calendar

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
            SectionCard(enterIndex = 1) {
                Text(
                    text = "下次执行：" + nextRunText(state.nextTaskAt),
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "定时任务跟着网关前台服务跑：服务停掉或被系统回收后不再触发，重新打开网关即恢复。" +
                        "执行记录见「运行日志」。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        if (state.providers.none { state.accountsOf(it.id).isNotEmpty() }) {
            item {
                SectionCard(enterIndex = 2) {
                    EmptyHint("还没有账号", "先去「供应商」页添加账号，再回来签到。")
                }
            }
        }

        for (info in state.providers) {
            val accounts = state.accountsOf(info.id)
            if (accounts.isEmpty()) continue
            item(key = info.id) { SmallTitle(info.displayName) }
            item(key = "${info.id}/card") {
                SectionCard(enterIndex = 3) {
                    for (account in accounts) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = account.nickname.ifEmpty { account.uid },
                                style = MiuixTheme.textStyles.body2,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = buildString {
                                    append("额度 ")
                                    append(if (account.creditsKnown) account.credits.toString() else "—")
                                    append(" · ")
                                    append(accountStatusText(account))
                                },
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
