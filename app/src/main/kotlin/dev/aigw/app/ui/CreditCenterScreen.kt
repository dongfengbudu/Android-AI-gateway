package dev.aigw.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 额度中心：列出有账号的供应商，点进去看该供应商各账号的额度包明细。
 */
@Composable
fun CreditCenterScreen(state: AppUiState, onOpen: (String) -> Unit, onBack: () -> Unit) {
    PageScaffold(
        title = "额度中心",
        subtitle = "按供应商查看额度包",
        leading = { BackButton(onBack) },
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val providers = state.providers.filter { state.accountsOf(it.id).isNotEmpty() }
        if (providers.isEmpty()) {
            SectionCard(enterIndex = 0) {
                EmptyHint("还没有账号", "先去「供应商」页添加账号。")
            }
        } else {
        for (info in providers) {
            val accounts = state.accountsOf(info.id)
            val known = accounts.filter { it.creditsKnown }
            SectionCard(modifier = Modifier.clickable { onOpen(info.id) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = info.displayName,
                            style = MiuixTheme.textStyles.headline2,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${accounts.size} 个账号 · 额度合计 " +
                                if (known.isEmpty()) "—" else known.sumOf { it.credits }.toString(),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Icon(
                        MiuixIcons.ChevronForward,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.outline,
                    )
                }
            }
        }
        }
            }
        }
    }
}

/** 某个供应商各账号的额度包明细。 */
@Composable
fun ProviderCreditsScreen(
    providerId: String,
    state: AppUiState,
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val accounts = state.accountsOf(providerId)
    val displayName = state.providerOf(providerId)?.displayName ?: providerId

    // 进页面拉一次；账号变化时重拉
    LaunchedEffect(providerId, accounts.map { it.uid }) {
        accounts.forEach { viewModel.loadCreditPacks(providerId, it.uid) }
    }

    PageScaffold(
        title = displayName,
        subtitle = "额度包明细",
        leading = { BackButton(onBack) },
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (accounts.isEmpty()) {
            SectionCard(enterIndex = 1) {
                EmptyHint("还没有账号", "先去该供应商页添加账号。")
            }
        } else {
        for (account in accounts) {
            val packs = state.creditPacks["$providerId/${account.uid}"].orEmpty()
            SectionCard(enterIndex = 2) {
                SectionLabel(account.nickname.ifEmpty { account.uid })
                Text(
                    text = "余额 ${if (account.creditsKnown) account.credits.toString() else "—"} · ${accountStatusText(account)}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                if (packs.isEmpty()) {
                    Text(
                        text = if (!account.creditsKnown && account.detail.isNotEmpty()) {
                            // 额度包为空多半伴随刷新失败，把池里记下的失败原因亮出来
                            account.detail
                        } else {
                            "没有额度包明细"
                        },
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    for (pack in packs) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (pack.group.isEmpty()) pack.name else "${pack.name}（${pack.group}）",
                                style = MiuixTheme.textStyles.body2,
                            )
                            Text(
                                text = buildString {
                                    if (pack.remain >= 0) {
                                        append("剩余 ")
                                        append(pack.remain)
                                    } else {
                                        append("剩余 —")
                                    }
                                    if (pack.limit > 0) append(" / ").append(pack.limit)
                                    if (pack.used > 0) append(" · 已用 ").append(pack.used)
                                },
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
                OutlineActionButton("刷新") {
                    viewModel.refreshCredits(providerId, account.uid)
                    viewModel.loadCreditPacks(providerId, account.uid)
                }
            }
        }
        }
            }
        }
    }
}
