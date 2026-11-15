package dev.aigw.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 排版阶梯：按「信息角色」命名，不按字号命名。
 *
 * 现状问题是层级塌陷——全应用 72% 的文字落在 13sp/11sp，而 miuix 的 headline1 实际只有 17sp，
 * 与 main 同号，所以首页那些「大号细体数字」并不比正文大。这里重新拉开档距：正文 16sp、
 * 次要 14sp、关键数字 24/32sp，11sp 只留给徽标与单位这类真正的标记。
 *
 * 颜色不写进 style（保持 Unspecified），由调用处的 `color =` 决定，避免样式与主题耦合。
 */
object AppType {

    /** 页面主数字：一屏最多一个，用于首页网关状态的量级展示。 */
    val Display = TextStyle(
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Light,
    )

    /** 关键指标数字：六宫格、统计单元。与 [Display] 差两档，避免处处抢焦点。 */
    val Number = TextStyle(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Medium,
    )

    /** 页面内区块标题 / 卡片主标题。 */
    val Title = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** 分组小标题（卡片外的组名）。比 [Title] 小一档但更强调字重。 */
    val SectionTitle = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** 卡片正文：默认档。 */
    val Body = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    )

    /** 次要正文：设置行标题、按钮文字。 */
    val Label = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    )

    /** 辅助说明：设置行摘要、页脚注释。 */
    val Meta = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    )

    /** 标记：徽标、单位、极短标签。全应用只应出现在这几类位置。 */
    val Micro = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
    )
}
