package dev.aigw.app.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aigw.core.gateway.CustomProviderConfig
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard {
            SectionLabel("基本信息")
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("启用", style = MiuixTheme.textStyles.body2)
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
        }

        SectionCard(modifier = Modifier.padding(top = 12.dp)) {
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
}
