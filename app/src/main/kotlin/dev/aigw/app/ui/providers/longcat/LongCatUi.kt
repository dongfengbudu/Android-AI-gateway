package dev.aigw.app.ui.providers.longcat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aigw.app.ui.SectionCard
import dev.aigw.app.ui.SectionLabel
import dev.aigw.app.ui.accountStatusText
import dev.aigw.app.ui.providers.ProviderUi
import dev.aigw.app.ui.providers.ProviderUiActions
import dev.aigw.core.pool.AccountStatus
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * LongCat（美团龙猫）界面组件。
 *
 * 两种账号形态：
 *  - **免登录**：一键添加，走 `chat-completion-oversea-V2`，无需任何凭证（但上游有 IP 级限流）；
 *  - **Cookie 登录**：从浏览器复制的 Cookie 串（至少含 `passport_token_key`），
 *    走 `session-create` + `chat-completion-V2`，更稳但需要自己提供 Cookie。
 */
object LongCatUi : ProviderUi {

    override val id: String = "longcat"

    override fun description() = "美团 LongCat 龙猫，支持免登录通道"

    @Composable
    override fun Icon(modifier: Modifier) {
        Icon(
            MiuixIcons.Favorites,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.primary,
            modifier = modifier,
        )
    }

    @Composable
    override fun LoginEntry(accounts: List<AccountStatus>, actions: ProviderUiActions) {
        AddAccountSection(accounts, actions)
        for (account in accounts) {
            AccountCard(account, actions)
        }
    }

    @Composable
    private fun AddAccountSection(accounts: List<AccountStatus>, actions: ProviderUiActions) {
        var expanded by rememberSaveable { mutableStateOf(false) }
        var cookie by remember { mutableStateOf("") }

        SectionCard {
            Button(
                onClick = {
                    // 免登录通道：直接塞一个无 Cookie 的账号
                    actions.onImport(id, """{"cookie":"","nickname":"免登录通道","uid":"anonymous"}""")
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text("添加免登录账号") }

            if (!expanded) {
                TextButton(
                    text = "网页登录（推荐）",
                    onClick = {
                        // 内嵌网页登录：打开 longcat.chat，用户登录完由宿主读 Cookie
                        actions.onCookieLogin(
                            id,
                            "https://longcat.chat/",
                            listOf("https://longcat.chat/"),
                            "登录 LongCat",
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
                TextButton(
                    text = "手动粘贴 Cookie",
                    onClick = { expanded = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
                return@SectionCard
            }

            SectionLabel("Cookie 登录")
            Text(
                "浏览器打开 longcat.chat 登录后，从开发者工具里复制整段 Cookie " +
                    "（至少要有 passport_token_key）。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 12.dp),
            )
            TextField(
                value = cookie,
                onValueChange = { cookie = it },
                label = "Cookie",
                minLines = 3,
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        actions.onImport(id, """{"cookie":${quote(cookie.trim())}}""")
                        cookie = ""
                        expanded = false
                    },
                    enabled = cookie.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text("导入") }
                TextButton(
                    text = "取消",
                    onClick = { expanded = false },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    @Composable
    private fun AccountCard(status: AccountStatus, actions: ProviderUiActions) {
        SectionCard {
            Text(
                status.nickname.ifEmpty { status.uid },
                style = MiuixTheme.textStyles.body2,
            )
            Text(
                accountStatusText(status),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            val region = actions.regionOf(id, status.uid)
            if (region.isNotEmpty()) {
                Text(
                    if (region == "cn") "通道：Cookie 登录" else "通道：免登录",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    text = "刷新",
                    onClick = { actions.onRefreshCredits(id, status.uid) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "删除",
                    onClick = { actions.onRemoveAccount(id, status.uid) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    /** 极简 JSON 字符串转义（Cookie 里可能含引号/反斜杠）。 */
    private fun quote(value: String): String {
        val builder = StringBuilder("\"")
        for (ch in value) {
            when (ch) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> if (ch < ' ') builder.append(String.format("\\u%04x", ch.code)) else builder.append(ch)
            }
        }
        return builder.append("\"").toString()
    }
}
