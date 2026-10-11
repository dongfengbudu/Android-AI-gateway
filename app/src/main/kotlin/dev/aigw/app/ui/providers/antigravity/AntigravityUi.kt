package dev.aigw.app.ui.providers.antigravity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aigw.app.ui.QuotaPackBar
import dev.aigw.app.ui.SectionCard
import dev.aigw.app.ui.SectionHeader
import dev.aigw.app.ui.providers.ProviderUi
import dev.aigw.app.ui.providers.ProviderUiActions
import dev.aigw.core.pool.AccountStatus
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.theme.MiuixTheme

object AntigravityUi : ProviderUi {

    override val id = "antigravity"

    override fun description() = "Google 账号 OAuth 登录"

    @Composable
    override fun Icon(modifier: Modifier) {
        Icon(
            MiuixIcons.CloudFill,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.primary,
            modifier = modifier,
        )
    }

    @Composable
    override fun LoginEntry(accounts: List<AccountStatus>, actions: ProviderUiActions) {
        SectionCard(enterIndex = 0) {
            SectionHeader("登录")
            Button(
                onClick = { actions.onWebLogin(id) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text("在浏览器中登录 Google 账号") }
        }
    }

    @Composable
    override fun AccountExtra(status: AccountStatus, actions: ProviderUiActions) {
        val packs = actions.creditPacksOf(id, status.uid)
        // 首次进详情页没有数据时拉一次（打上游；拉取结果回到 state.creditPacks）
        LaunchedEffect(id, status.uid) {
            if (packs.isEmpty()) {
                actions.onLoadCreditPacks(id, status.uid)
            }
        }
        if (packs.isEmpty()) return

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (pack in packs) {
                QuotaPackBar(pack)
            }
        }
    }
}
