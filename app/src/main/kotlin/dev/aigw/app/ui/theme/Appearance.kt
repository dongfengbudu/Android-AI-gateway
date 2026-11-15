package dev.aigw.app.ui.theme

import androidx.compose.ui.graphics.Color

/** 明暗模式：跟随系统 / 强制深色 / 强制浅色。 */
enum class ThemeMode { Light, Dark, System }

/**
 * 主题强调色的调性来源。
 *
 * 只描述「用哪个色相、多浓」，具体色阶（tone）由 Theme.kt 的调色板生成器展开；
 * 这样调用方不需要了解任何具体色值，换一套色调只改这一个数。
 *
 * [chroma] 是饱和度（色相环拖动时圆心 0 → 边缘 1），它决定的是**整页中性底的染色浓度**，
 * 主色本身的彩度固定取 Theme.kt 的 AccentChroma（详见 [oklchChroma] 的理由说明）。
 * 保持 0..1 语义是为了让已落盘的偏好（settings/appearance.*）继续有效。
 */
data class ThemeAccent(
    val hue: Float,
    val chroma: Float = DefaultChroma,
) {
    /** 归一化后的色相（0..360）。 */
    val normalizedHue: Float get() = ((hue % 360f) + 360f) % 360f

    /** 归一化后的强度（0..1）。 */
    val normalizedChroma: Float get() = chroma.coerceIn(0f, 1f)

    companion object {
        const val DefaultChroma = 0.58f
        const val DefaultHue = 258f
    }
}

/**
 * 页面底色档位。
 *
 * 中性底方案下，四档只在「中性明度 + 染色浓度」上浮动，不改变明暗极性，也不影响正文对比度
 * （前景色由调色板按真实底色校验，见 Theme.kt 的 readableOn）。
 */
enum class BackgroundTone(val label: String) {
    /** 默认：中性近白 / 近黑，带极低的强调色染色。 */
    Theme("默认"),

    /** 去色偏的极简底（浅色模式纯白 / 深色模式纯黑）。 */
    Plain("纯净"),

    /** 比默认略强一点的染色。 */
    Soft("微染"),

    /** 明显能看出色调的底色。 */
    Rich("浓染"),
}

/** 默认强调色：靛蓝，中性底、冷调点缀。 */
val defaultThemeAccent = ThemeAccent(ThemeAccent.DefaultHue, ThemeAccent.DefaultChroma)

/**
 * 成功/运行态绿：语义色固定色相（[SuccessHue]），不随强调色变化，否则「正常」会被染成主色。
 * 明度落在新色阶体系里，与中性底并排时不会显得突兀。
 */
val SuccessGreen = toneColor(SuccessHue, 0.15f, 58f)

/** 警示/降速橙：请求中止、额度紧张、慢速账号等中间态（成功态见 [SuccessGreen]）。 */
val WarnAmber = toneColor(WarnHue, 0.16f, 60f)
