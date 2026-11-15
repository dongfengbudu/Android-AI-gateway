package dev.aigw.app.ui.providers

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.aigw.app.ui.theme.AppType
import dev.aigw.core.pool.AccountStatus
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 未适配界面的供应商的兜底组件。
 *
 * 上游合进来的供应商（ChatGPT / Dumate / Qoder / 千问办公 / 搭子 / Today）只有 core 层实现，
 * Miuix 界面还没移植。没有兜底时 `require` 会直接 error，用户从供应商列表点进去就是闪退；
 * 这里退化成「能看账号、能删、能停用，但专属操作不可用」，并如实说明原因。
 */
class GenericProviderUi(
    override val id: String,
    private val displayName: String,
) : ProviderUi {

    override fun description(): String = "$displayName（界面待适配，仅通用账号管理可用）"

    @Composable
    override fun Icon(modifier: Modifier) {
        Icon(MiuixIcons.GridView, contentDescription = displayName, modifier = modifier)
    }

    @Composable
    override fun LoginEntry(accounts: List<AccountStatus>, actions: ProviderUiActions) {
        Text(
            text = "$displayName 的登录与专属操作界面还没适配 Miuix，暂时只能在网关核心里使用；" +
                "已导入的账号仍可正常参与路由，停用与删除在下方账号列表里操作。",
            style = AppType.Body,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
