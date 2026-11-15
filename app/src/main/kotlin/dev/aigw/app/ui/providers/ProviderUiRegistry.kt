package dev.aigw.app.ui.providers

import dev.aigw.app.ui.providers.antigravity.AntigravityUi
import dev.aigw.app.ui.providers.codebuddy.CodeBuddyUi
import dev.aigw.app.ui.providers.custom.CustomUi
import dev.aigw.app.ui.providers.loomy.LoomyUi
import dev.aigw.app.ui.providers.longcat.LongCatUi
import dev.aigw.app.ui.providers.trae.TraeUi

/**
 * 供应商 UI 组件的登记处。
 *
 * 新增一个供应商 = 新增一个 `ProviderUi` 实现 + 在这里加一行。
 * 页面只按 `providerId` 取组件，不认识任何具体供应商。
 */
object ProviderUiRegistry {

    private val builtin: Map<String, ProviderUi> = listOf(
        TraeUi,
        LoomyUi,
        CodeBuddyUi,
        AntigravityUi,
        LongCatUi,
    ).associateBy { it.id }

    fun of(providerId: String): ProviderUi? = when {
        builtin.containsKey(providerId) -> builtin[providerId]
        // 每个自定义供应商一个实例：组件需要知道自己绑定的 providerId
        providerId.startsWith(CUSTOM_PREFIX) -> CustomUi(providerId)
        else -> null
    }

    /**
     * 取组件，未适配界面的供应商回退到 [GenericProviderUi]。
     *
     * 不这么做就会闪退：上游合进来的六个供应商只有 core 实现、没 Miuix 界面，
     * 供应商列表照旧会列出它们，点进详情页直接 error。
     */
    fun require(providerId: String, displayName: String = providerId): ProviderUi =
        of(providerId) ?: GenericProviderUi(providerId, displayName)

    const val CUSTOM_PREFIX = "custom:"
}
