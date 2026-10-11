package dev.aigw.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.aigw.core.gateway.ProxyType
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.extra.WindowDialog

/**
 * 代理设置：境外供应商（如 Antigravity）需要走代理。
 *
 * 打开总开关后默认所有上游都走代理，个别不需要的可以在下面的列表里单独关掉。
 */
@Composable
fun ProxyScreen(state: AppUiState, viewModel: AppViewModel, onBack: () -> Unit) {
    val settings = state.proxy
    var host by remember(settings.host) { mutableStateOf(settings.host) }
    var port by remember(settings.port) { mutableStateOf(settings.port.toString()) }
    var username by remember(settings.username) { mutableStateOf(settings.username) }
    var password by remember(settings.password) { mutableStateOf(settings.password) }
    var excludedText by remember(settings.excludedHosts) {
        mutableStateOf(settings.excludedHosts.joinToString(","))
    }
    var showTypeDialog by remember { mutableStateOf(false) }

    PageScaffold(
        title = "代理设置",
        subtitle = if (settings.usable) "已启用 ${settings.host}:${settings.port}" else "未启用",
        leading = { BackButton(onBack) },
    ) {
        item { SmallTitle("代理") }
        item {
        SectionCard(enterIndex = 0) {
            SuperSwitch(
                title = "启用代理",
                summary = "开启后所有上游请求默认走代理",
                checked = settings.enabled,
                onCheckedChange = { viewModel.updateProxySettings(settings.copy(enabled = it)) },
                insideMargin = PaddingValues(0.dp),
            )
            SuperArrow(
                title = "代理类型",
                endActions = {
                    Text(
                        text = settings.type.label,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                },
                onClick = { showTypeDialog = true },
                insideMargin = PaddingValues(0.dp),
            )
            TextField(
                value = host,
                onValueChange = { host = it.trim() },
                label = "代理地址",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit).take(5) },
                label = "端口",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = username,
                onValueChange = { username = it },
                label = "用户名（可选）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = password,
                onValueChange = { password = it },
                label = "密码（可选）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = excludedText,
                onValueChange = { excludedText = it },
                label = "不走代理的地址",
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "逗号分隔；支持通配符 *、host:port、CIDR 网段",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            OutlineActionButton("保存") {
                val parsed = port.toIntOrNull()
                if (parsed == null || parsed !in 1..65535) {
                    viewModel.notice("端口需要在 1~65535 之间")
                } else {
                    val excludedHosts = excludedText
                        .split(Regex("[,，\\n\\s]+"))
                        .filter { it.isNotEmpty() }
                        .distinct()
                    viewModel.updateProxySettings(
                        settings.copy(
                            host = host.trim(),
                            port = parsed,
                            username = username.trim(),
                            password = password,
                            type = settings.type,
                            excludedHosts = excludedHosts,
                        ),
                    )
                    viewModel.notice("代理设置已保存")
                }
            }
        }
        }

        item { SmallTitle("走代理的供应商") }
        item {
        SectionCard(enterIndex = 1) {
            if (state.providers.isEmpty()) {
                EmptyHint("还没有供应商", "内置供应商会在启动时自动登记。")
            } else {
                state.providers.forEachIndexed { index, info ->
                    if (index > 0) HorizontalDivider(modifier = Modifier.fillMaxWidth())
                    SuperSwitch(
                        title = info.displayName,
                        summary = info.id,
                        checked = info.id !in settings.excludedProviderIds,
                        onCheckedChange = { checked ->
                            val excluded = if (checked) {
                                settings.excludedProviderIds - info.id
                            } else {
                                settings.excludedProviderIds + info.id
                            }
                            viewModel.updateProxySettings(settings.copy(excludedProviderIds = excluded))
                        },
                        insideMargin = PaddingValues(vertical = 12.dp),
                    )
                }
            }
        }
        }
    }

    if (showTypeDialog) {
        ProxyTypeDialog(
            show = showTypeDialog,
            current = settings.type,
            onSelect = { selected ->
                showTypeDialog = false
                viewModel.updateProxySettings(settings.copy(type = selected))
                viewModel.notice("代理设置已保存")
            },
            onDismiss = { showTypeDialog = false },
        )
    }
}

@Composable
private fun ProxyTypeDialog(
    show: Boolean,
    current: ProxyType,
    onSelect: (ProxyType) -> Unit,
    onDismiss: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = "代理类型",
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            for (type in ProxyType.entries) {
                Text(
                    text = type.label,
                    style = MiuixTheme.textStyles.main,
                    fontWeight = if (type == current) FontWeight.SemiBold else null,
                    color = if (type == current) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(type) }
                        .padding(vertical = 12.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}
