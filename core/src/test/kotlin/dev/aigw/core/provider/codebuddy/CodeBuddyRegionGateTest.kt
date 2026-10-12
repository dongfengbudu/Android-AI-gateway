package dev.aigw.core.provider.codebuddy

import com.google.gson.JsonObject
import dev.aigw.core.provider.ProviderAccount
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 国际版（workbuddy.ai）的区域门控。
 *
 * 背景：这些动作对国际版返回 `unsupported` 而不是 `failure`——排程侧只按 `unsupported` 跳过，
 * 标成 failure 会让国际版账号每次定时任务都被记一条「失败」。
 *
 * 门控在任何网络 IO 之前返回，所以本用例不触网。
 */
class CodeBuddyRegionGateTest {

    private fun account(domain: String) = ProviderAccount(
        providerId = CodeBuddyProvider.ID,
        uid = "u-1",
        nickname = "测试账号",
        secret = """{"accessToken":"token","domain":"$domain"}""",
    )

    private val gatedActions = listOf(
        CodeBuddyProvider.ACTION_CHECKIN,
        CodeBuddyProvider.ACTION_TASKS,
        CodeBuddyProvider.ACTION_ACTIVITY,
        CodeBuddyProvider.ACTION_BLACKCAT,
        CodeBuddyProvider.ACTION_AUTO_TASKS,
    )

    @Test
    fun `国际版账号的动作一律标为不支持`() {
        val provider = CodeBuddyProvider()
        val global = account("www.workbuddy.ai")
        gatedActions.forEach { action ->
            val result = provider.performAction(global, action, JsonObject())
            assertFalse(result.ok, "$action 不该成功")
            assertTrue(result.unsupported, "$action 应标 unsupported（排程据此跳过，而不是记失败）")
            assertTrue(result.message.isNotEmpty(), "$action 应带可展示的原因")
        }
    }

    @Test
    fun `国际版账号的区域判定为 global`() {
        val provider = CodeBuddyProvider()
        assertTrue(provider.regionOf(account("www.workbuddy.ai")) == CodeBuddyProvider.REGION_GLOBAL)
    }
}
