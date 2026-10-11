package dev.aigw.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperSwitch

/** 接入设置：客户端要用的地址、端口与网关鉴权 Key。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApiSettingsScreen(state: AppUiState, viewModel: AppViewModel, onBack: () -> Unit) {
    val settings = state.settings
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var port by remember(settings.port) { mutableStateOf(settings.port.toString()) }
    var apiKey by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }

    PageScaffold(
        title = "接入设置",
        subtitle = "端口与 API Key",
        leading = { BackButton(onBack) },
    ) {
        item {
        SmallTitle("接入地址")
        SectionCard(enterIndex = 0) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AddressTile(state.localUrl.ifEmpty { "http://127.0.0.1:${settings.port}/v1" }) {
                    clipboard.setText(AnnotatedString(state.localUrl))
                    Toast.makeText(context, "已复制本机地址", Toast.LENGTH_SHORT).show()
                }
                state.lanUrls.forEach { url ->
                    AddressTile(url) {
                        clipboard.setText(AnnotatedString(url))
                        Toast.makeText(context, "已复制局域网地址", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        SmallTitle("端口")
        SectionCard(enterIndex = 1) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    label = "监听端口",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SuperSwitch(
                    title = "同时监听局域网",
                    summary = "电脑/手机同一网络下可直接调用",
                    checked = settings.exposeLan,
                    onCheckedChange = { viewModel.updateSettings(settings.copy(exposeLan = it)) },
                    insideMargin = PaddingValues(0.dp),
                )
            }
        }

        SmallTitle("API Key")
        SectionCard(enterIndex = 2) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = "网关鉴权 Key（留空表示不校验）",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SuperSwitch(
                    title = "无 Key 调用",
                    summary = "允许客户端不带鉴权 Key 直接调用本机网关",
                    checked = settings.allowNoKey,
                    onCheckedChange = { viewModel.updateSettings(settings.copy(allowNoKey = it)) },
                    insideMargin = PaddingValues(0.dp),
                )
            }
        }

        SmallTitle("默认供应商")
        SectionCard(enterIndex = 3) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.providers.forEach { provider ->
                    ChoiceChip(
                        text = provider.displayName,
                        selected = (settings.defaultProvider == provider.id),
                        onClick = { viewModel.updateSettings(settings.copy(defaultProvider = provider.id)) },
                    )
                }
            }
        }

        SectionCard(enterIndex = 4) {
            OutlineActionButton("保存") {
                val parsed = port.toIntOrNull()
                if (parsed == null || parsed !in 1024..65535) {
                    viewModel.notice("端口需要在 1024~65535 之间")
                } else {
                    viewModel.updateSettings(settings.copy(port = parsed, apiKey = apiKey.trim()))
                    viewModel.notice("已保存；端口改动需重启服务生效")
                }
            }
        }
    }
}
}
