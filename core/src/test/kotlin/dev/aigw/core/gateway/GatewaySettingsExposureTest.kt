package dev.aigw.core.gateway

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 模型暴露开关的语义。
 *
 * 回归用例：界面曾用「隐藏开关」表达「暴露开关」，回调链上取反了两次，
 * 点一下等于没点——已暴露的点不隐藏、已隐藏的点不回暴露，开关看起来卡死。
 * 现在暴露/隐藏的换算收敛到 [GatewaySettings.withModelExposed]，界面只传目标值。
 */
class GatewaySettingsExposureTest {

    @Test
    fun `关掉暴露会加入隐藏集合`() {
        val settings = GatewaySettings()
        val hidden = settings.withModelExposed("trae/gpt-5", exposed = false)
        assertEquals(setOf("trae/gpt-5"), hidden.hiddenModels)
    }

    @Test
    fun `已隐藏的模型能再点回暴露`() {
        val hidden = GatewaySettings().withModelExposed("trae/gpt-5", exposed = false)
        val exposed = hidden.withModelExposed("trae/gpt-5", exposed = true)
        assertTrue(exposed.hiddenModels.isEmpty(), "点回暴露后不应仍在隐藏集合里：${exposed.hiddenModels}")
    }

    @Test
    fun `切换不影响其它模型`() {
        val settings = GatewaySettings(hiddenModels = setOf("trae/a", "trae/b"))
        val next = settings.withModelExposed("trae/a", exposed = true)
        assertEquals(setOf("trae/b"), next.hiddenModels)
    }

    @Test
    fun `全部开启清空隐藏集合`() {
        val settings = GatewaySettings(hiddenModels = setOf("trae/a", "workbuddy/b"))
        val next = settings.withAllModelsExposed(true, listOf("trae/a", "trae/c"))
        assertEquals(emptySet<String>(), next.hiddenModels)
    }

    @Test
    fun `全部关闭隐藏目录里的全部模型`() {
        val ids = listOf("trae/a", "trae/b", "workbuddy/c")
        assertEquals(ids.toSet(), GatewaySettings().withAllModelsExposed(false, ids).hiddenModels)
    }

    @Test
    fun `隐藏集合能落盘再读回`() {
        val settings = GatewaySettings(hiddenModels = setOf("trae/a", "workbuddy/b"))
        val restored = GatewaySettings.fromJson(settings.toJson().toString())
        assertEquals(settings.hiddenModels, restored.hiddenModels)
    }
}
