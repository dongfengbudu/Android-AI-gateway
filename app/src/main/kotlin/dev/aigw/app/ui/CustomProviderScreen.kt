package dev.aigw.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aigw.core.gateway.CustomProviderConfig
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperSwitch

/**
 * 自定义供应商的新建/编辑。
 *
 * API Key（账号）与模型列表在供应商详情页的「添加账号」里维护，这里只管基本信息。
 */
@Composable
fun CustomProviderScreen(
    existing: CustomProviderConfig?,
    onSave: (CustomProviderConfig) -> Unit,
    onDelete: ((String) -> Unit)?,
    onBack: () -> Unit,
) {
    var key by remember { mutableStateOf(existing?.key.orEmpty()) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var baseUrl by remember { mutableStateOf(existing?.baseUrl.orEmpty()) }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }

    PageScaffold(
        title = if (existing == null) "新建供应商" else "编辑供应商",
        subtitle = "OpenAI 兼容接口",
        leading = {
            BackButton(onBack)
        },
    ) {
        item { SmallTitle("基本信息") }
        item {
            SectionCard {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "名称",
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                TextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    label = "短名（用于模型前缀 custom:<短名>）",
                    singleLine = true,
                    enabled = existing == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
                TextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it.trim() },
                    label = "接口地址（到 /v1，或直接写 /v1/chat/completions）",
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
                SuperSwitch(
                    title = "启用",
                    summary = "关闭后不参与调度，配置保留",
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    insideMargin = PaddingValues(top = 12.dp),
                )
            }
        }

        item {
            SectionCard {
                OutlineActionButton("保存") {
                    onSave(
                        CustomProviderConfig(
                            key = key.trim(),
                            name = name.trim().ifEmpty { key.trim() },
                            baseUrl = baseUrl.trim(),
                            apiKeys = existing?.apiKeys.orEmpty(),
                            models = existing?.models.orEmpty(),
                            enabled = enabled,
                        ),
                    )
                }
                if (existing != null && onDelete != null) {
                    OutlineActionButton("删除该供应商", modifier = Modifier.padding(top = 10.dp)) { onDelete(existing.key) }
                }
            }
        }
    }
}
