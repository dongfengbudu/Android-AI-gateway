package dev.aigw.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 任务中心：批量签到与额度维护。
 *
 * 只对声明了签到能力的供应商（Trae / WorkBuddy）执行签到；
 * 额度刷新对所有供应商生效（自定义供应商没有额度接口，会返回「不支持」）。
 */
@Composable
fun TaskCenterScreen(state: AppUiState, viewModel: AppViewModel, onBack: () -> Unit) {
    PageScaffold(
        title = "任务中心",
        subtitle = "签到与额度",
        leading = { BackButton(onBack) },
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard(enterIndex = 0) {
            SectionLabel("批量操作")
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
                text = "刷新全部额度",
                onClick = { viewModel.refreshAllCredits() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "签到作用于 Trae 与 WorkBuddy 国内版（国际版没有签到制度，自动跳过）；" +
                    "任务作用于 Loomy 的新手任务；额度刷新作用于全部账号。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }

        if (state.providers.none { state.accountsOf(it.id).isNotEmpty() }) {
            SectionCard(enterIndex = 1) {
                EmptyHint("还没有账号", "先去「供应商」页添加账号，再回来签到。")
            }
        }

        for (info in state.providers) {
            val accounts = state.accountsOf(info.id)
            if (accounts.isEmpty()) continue
            SectionCard(enterIndex = 2) {
                SectionLabel(info.displayName)
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
}
