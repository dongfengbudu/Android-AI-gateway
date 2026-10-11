package dev.aigw.app.ui.providers.trae

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import dev.aigw.app.ui.ChoiceChip
import dev.aigw.app.ui.OutlineActionButton
import dev.aigw.app.ui.SectionCard
import dev.aigw.app.ui.SectionHeader
import dev.aigw.app.ui.providers.ProviderUi
import dev.aigw.app.ui.providers.ProviderUiActions
import dev.aigw.core.pool.AccountStatus
import dev.aigw.core.provider.ACTION_CHECKIN
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.MindMap
import top.yukonga.miuix.kmp.theme.MiuixTheme

object TraeUi : ProviderUi {

    override val id: String = "trae"

    override fun description(): String = "网页登录导入，走 SOLO 免费通道"

    @Composable
    override fun Icon(modifier: Modifier) {
        Icon(
            imageVector = MiuixIcons.MindMap,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.primary,
            modifier = modifier,
        )
    }

    @Composable
    override fun LoginEntry(accounts: List<AccountStatus>, actions: ProviderUiActions) {
        var expanded by remember { mutableStateOf(false) }
        var raw by remember { mutableStateOf("") }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionCard(enterIndex = 0) {
                SectionHeader("登录")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { actions.onWebLogin(id) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("在浏览器中打开 Trae 登录页")
                    }
                    OutlineActionButton(
                        text = if (expanded) "收起粘贴导入" else "粘贴凭证 JSON 导入",
                    ) { expanded = !expanded }
                    if (expanded) {
                        TextField(
                            value = raw,
                            onValueChange = { raw = it },
                            modifier = Modifier
                                .fillMaxWidth(),
                            minLines = 3,
                            maxLines = 8,
                            label = "回调链接 / 凭证 JSON",
                        )
                        Button(
                            onClick = {
                                actions.onImport(id, raw.trim())
                                raw = ""
                            },
                            enabled = raw.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text("导入")
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun AccountExtra(status: AccountStatus, actions: ProviderUiActions) {
        val deviceId = actions.deviceIdOf(id, status.uid)
        var manual by remember(status.uid) { mutableStateOf("") }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("额度", style = MiuixTheme.textStyles.body2)
            Text(
                text = if (status.creditsKnown) status.credits.toString() else "—",
                style = MiuixTheme.textStyles.body2,
            )
            if (status.detail.isNotEmpty()) {
                Text(
                    text = status.detail,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("签到", false) {
                    actions.onAction(id, status.uid, ACTION_CHECKIN, JsonObject())
                }
            }
            Text("设备指纹", style = MiuixTheme.textStyles.body2)
            Text(
                text = deviceId.ifEmpty { "—" },
                style = MiuixTheme.textStyles.body2,
            )
            Text(
                text = "设备指纹用于签到校验，非数字格式会被上游拒绝。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("重新生成", false) { actions.onRegenerateDeviceId(id, status.uid) }
            }
            TextField(
                value = manual,
                onValueChange = { manual = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = "手动填写设备指纹",
            )
            Button(
                onClick = {
                    actions.onSetDeviceId(id, status.uid, manual.trim())
                    manual = ""
                },
                enabled = manual.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("保存")
            }
        }
    }
}
