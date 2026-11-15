package dev.aigw.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 语义叠加色：页面里那些「主色 12% 的选中底」「状态点 18% 的光环」不再手写 alpha。
 *
 * 手调 alpha 的问题是同一个语义在不同页面取了不同值（现有代码里 0.12/0.18/0.45/0.5/0.6 各出现几次），
 * 且深浅模式下这些值的最优解不同。收进 token 后由主题按极性算一次，页面只表达语义。
 */
@Immutable
class Overlays(
    /** 主色弱底：选中胶囊、浅色强调块。 */
    val accentSoft: Color,
    /** 主色光环：运行状态点外圈、图标底。 */
    val accentGlow: Color,
    /** 主色描边：未选中的强调框、聚焦环。 */
    val accentLine: Color,
    /** 成功弱底与成功文字。 */
    val successSoft: Color,
    val successText: Color,
    /** 警示弱底与警示文字。 */
    val warnSoft: Color,
    val warnText: Color,
    /** 错误弱底（错误文字直接用 colorScheme.error）。 */
    val errorSoft: Color,
    /** 进度条、开关未激活轨道。 */
    val track: Color,
    /** 平面档卡片的 1dp 描边。 */
    val hairline: Color,
    /** 图表弱化填充。 */
    val chartFaint: Color,
)

val LocalOverlays = staticCompositionLocalOf {
    Overlays(
        accentSoft = Color.Unspecified,
        accentGlow = Color.Unspecified,
        accentLine = Color.Unspecified,
        successSoft = Color.Unspecified,
        successText = Color.Unspecified,
        warnSoft = Color.Unspecified,
        warnText = Color.Unspecified,
        errorSoft = Color.Unspecified,
        track = Color.Unspecified,
        hairline = Color.Unspecified,
        chartFaint = Color.Unspecified,
    )
}

/** 状态色相固定：成功偏绿、警示偏橙、错误偏红，不随强调色走，否则「失败」会被染成蓝色。 */
internal const val SuccessHue = 152f
internal const val WarnHue = 62f
internal const val ErrorHue = 25f

/**
 * 由当前色板算出一套叠加色。[primary] 与 [dark] 决定浓度：深色模式需要更高的 alpha 才看得见，
 * 浅色模式反之；成功/警示文字在各自弱底上做对比度校验。
 */
internal fun overlaysFor(
    primary: Color,
    background: Color,
    dark: Boolean,
    tintHue: Float,
): Overlays {
    val softAlpha = if (dark) 0.20f else 0.10f
    val glowAlpha = if (dark) 0.26f else 0.16f
    val successText = toneMeeting(Oklch(SuccessHue, 0.13f, 0f), background, ContrastBody, 45f, if (dark) 1 else -1)
    val warnText = toneMeeting(Oklch(WarnHue, 0.13f, 0f), background, ContrastBody, 48f, if (dark) 1 else -1)
    return Overlays(
        accentSoft = primary.copy(alpha = softAlpha),
        accentGlow = primary.copy(alpha = glowAlpha),
        accentLine = primary.copy(alpha = if (dark) 0.55f else 0.38f),
        successSoft = toneColor(SuccessHue, 0.10f, if (dark) 26f else 94f),
        successText = successText,
        warnSoft = toneColor(WarnHue, 0.10f, if (dark) 28f else 94f),
        warnText = warnText,
        errorSoft = toneColor(ErrorHue, 0.08f, if (dark) 26f else 94f),
        track = toneColor(tintHue, 0.01f, if (dark) 30f else 91f),
        hairline = toneColor(tintHue, 0.012f, if (dark) 30f else 89f),
        chartFaint = primary.copy(alpha = if (dark) 0.22f else 0.14f),
    )
}
