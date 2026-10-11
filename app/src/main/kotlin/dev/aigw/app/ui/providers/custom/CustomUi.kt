package dev.aigw.app.ui.providers.custom

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.aigw.app.ui.ChoiceChip
import dev.aigw.app.ui.EmptyHint
import dev.aigw.app.ui.OutlineActionButton
import dev.aigw.app.ui.SectionCard
import dev.aigw.app.ui.SectionHeader
import dev.aigw.app.ui.accountStatusText
import dev.aigw.app.ui.providers.ProviderUi
import dev.aigw.app.ui.providers.ProviderUiActions
import dev.aigw.core.pool.AccountStatus
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.extra.WindowBottomSheet
import top.yukonga.miuix.kmp.utils.overScrollVertical

class CustomUi(override val id: String) : ProviderUi {

    override val managesOwnAccounts: Boolean
        get() = true

    override fun description() = "手动导入的 OpenAI 兼容供应商"

    @Composable
    override fun Icon(modifier: Modifier) {
        Icon(
            MiuixIcons.Layers,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.primary,
            modifier = modifier,
        )
    }

    @Composable
    override fun LoginEntry(accounts: List<AccountStatus>, actions: ProviderUiActions) {
        AddAccountSection(accounts, actions)
        for (account in accounts) {
            AccountDetailCard(account, actions)
        }
    }

    /** 「添加账号」表单：名称 + API Key + 模型（可拉取/手填），点「添加账号」连同模型列表一并保存。 */
    @Composable
    private fun AddAccountSection(accounts: List<AccountStatus>, actions: ProviderUiActions) {
        var expanded by rememberSaveable { mutableStateOf(false) }
        var fetchedModels by remember { mutableStateOf<List<String>?>(null) }
        var models by rememberSaveable { mutableStateOf(actions.customModelsOf(id).joinToString("\n")) }
        SectionCard(modifier = Modifier.animateContentSize()) {
            if (!expanded) {
                Button(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text("添加账号") }
                return@SectionCard
            }
            SectionHeader("添加账号")
            var nickname by remember { mutableStateOf("") }
            var apiKey by remember { mutableStateOf("") }
            TextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = "名称（可选，默认用供应商名）",
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            TextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = "API Key",
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            )
            ModelsSection(accounts, actions, apiKey.trim(), models, onFetched = { fetchedModels = it }) { models = it }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        actions.onAddCustomAccount(
                            id,
                            nickname,
                            apiKey,
                            models.lines().map { it.trim() }.filter { it.isNotEmpty() },
                        )
                        nickname = ""
                        apiKey = ""
                        expanded = false
                    },
                    enabled = apiKey.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text("添加账号") }
                TextButton(
                    text = "取消",
                    onClick = { expanded = false },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        ModelPickerSheet(
            show = fetchedModels != null,
            fetched = fetchedModels.orEmpty(),
            currentModels = models,
            onDismiss = { fetchedModels = null },
            onConfirm = { selected ->
                models = selected.sorted().joinToString("\n")
                fetchedModels = null
                actions.onNotice("已选择 ${selected.size} 个模型")
            },
        )
    }

    /** 模型管理：云端拉取 + 手动编辑。拉模型优先用填写的 key，否则用第一个已有账号的；成功后弹层勾选。 */
    @Composable
    private fun ModelsSection(
        accounts: List<AccountStatus>,
        actions: ProviderUiActions,
        apiKey: String,
        models: String,
        onFetched: (List<String>) -> Unit,
        onModelsChange: (String) -> Unit,
    ) {
        var loading by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader("模型")
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.clickable(enabled = !loading) {
                    val baseUrl = actions.customBaseUrlOf(id)
                    if (baseUrl.isEmpty()) {
                        actions.onNotice("先保存供应商的接口地址，再拉取模型")
                        return@clickable
                    }
                    val key = apiKey.ifEmpty {
                        accounts.firstOrNull()?.let { actions.accountApiKeyOf(id, it.uid) }.orEmpty()
                    }
                    if (key.isEmpty()) {
                        actions.onNotice("先填写 API Key 再拉取模型")
                        return@clickable
                    }
                    loading = true
                    actions.onFetchCustomModels(baseUrl, key) { fetched, error ->
                        loading = false
                        if (error.isNotEmpty()) {
                            actions.onNotice(error)
                        } else {
                            onFetched(fetched)
                        }
                    }
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    MiuixIcons.Download,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = if (loading) "拉取中..." else "拉取模型",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
        TextField(
            value = models,
            onValueChange = onModelsChange,
            label = "一行一个模型 id（随账号一起保存）",
            minLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )
    }

    /** 拉取成功后的模型勾选弹层：搜索过滤、整行点选，保存时把选中项写回模型输入框。 */
    @Composable
    private fun ModelPickerSheet(
        show: Boolean,
        fetched: List<String>,
        currentModels: String,
        onDismiss: () -> Unit,
        onConfirm: (List<String>) -> Unit,
    ) {
        val selected = remember(fetched) {
            val initial = currentModels.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            mutableStateMapOf<String, Boolean>().apply { initial.forEach { put(it, true) } }
        }
        var search by remember { mutableStateOf("") }
        val selectedCount = selected.values.count { it }
        val filtered = fetched.filter { it.contains(search, ignoreCase = true) }
        WindowBottomSheet(
            show = show,
            title = "选择模型",
            endAction = {
                Text(
                    text = "已选 $selectedCount 个",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            },
            onDismissRequest = onDismiss,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                TextField(
                    value = search,
                    onValueChange = { search = it },
                    label = "搜索模型",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .heightIn(min = 240.dp, max = 420.dp)
                        .overScrollVertical(),
                    overscrollEffect = null,
                ) {
                    if (filtered.isEmpty()) {
                        item { EmptyHint("没有匹配的模型", "换个关键词试试") }
                    } else {
                        items(filtered, key = { it }) { model ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selected[model] = !(selected[model] ?: false) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(model, style = MiuixTheme.textStyles.body2, modifier = Modifier.weight(1f))
                                Checkbox(
                                    state = ToggleableState(selected[model] == true),
                                    onClick = { selected[model] = !(selected[model] ?: false) },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onConfirm(selected.filterValues { it }.keys.sorted()) },
                    enabled = selectedCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text("保存所选 $selectedCount 个") }
            }
        }
    }

    /** 账号卡片：点击展开详情（名称编辑、启用开关、删除），展开收起带高度过渡。 */
    @Composable
    private fun AccountDetailCard(account: AccountStatus, actions: ProviderUiActions) {
        var expanded by rememberSaveable(account.uid) { mutableStateOf(false) }
        SectionCard(modifier = Modifier.animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = account.nickname.ifEmpty { account.uid },
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = accountStatusText(account),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Icon(
                    if (expanded) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (expanded) {
                AccountDetailBody(account, actions)
            }
        }
    }

    @Composable
    private fun AccountDetailBody(account: AccountStatus, actions: ProviderUiActions) {
        var nickname by remember(account.uid, account.nickname) { mutableStateOf(account.nickname) }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = "名称",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlineActionButton("保存名称") {
                actions.onRenameAccount(id, account.uid, nickname)
            }

            SuperSwitch(
                title = "启用",
                summary = "关闭后该账号不参与请求调度",
                checked = account.enabled,
                onCheckedChange = { actions.onToggleAccount(id, account.uid, it) },
                insideMargin = PaddingValues(0.dp),
            )
            Text(
                text = "额度 ${if (account.creditsKnown) account.credits.toString() else "—"}",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.primary,
            )
            if (account.detail.isNotEmpty()) {
                Text(
                    text = account.detail,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ChoiceChip("删除账号", false) { actions.onRemoveAccount(id, account.uid) }
            }
        }
    }
}
