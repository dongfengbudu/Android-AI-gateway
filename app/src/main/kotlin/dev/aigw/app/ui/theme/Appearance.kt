package dev.aigw.app.ui.theme

import androidx.compose.ui.graphics.Color

/** 明暗模式：跟随系统 / 强制深色 / 强制浅色。 */
enum class ThemeMode { Light, Dark, System }

/**
 * 主题强调色的调性来源。
 *
 * 只描述「用哪个色相、多浓」，具体色阶（tone）由 Theme.kt 的调色板生成器展开；
 * 这样调用方不需要了解任何具体色值，换一套色调只改这一个数。
 */
data class ThemeAccent(
    val hue: Float,
    val chroma: Float = DefaultChroma,
) {
    /** 归一化后的色相（0..360）。 */
    val normalizedHue: Float get() = ((hue % 360f) + 360f) % 360f

    /** 归一化后的色度（0..1）。 */
    val normalizedChroma: Float get() = chroma.coerceIn(0f, 1f)

    companion object {
        const val DefaultChroma = 0.58f
        const val DefaultHue = 258f
    }
}

/**
 * 页面底色档位。
 *
 * 底色不走「自定义色值」，而是与强调色同色相展开、只调染色浓度：这样无论怎么换都
 * 不会出现底色与整套色阶脱节、或深浅模式配错导致文字看不清。
 */
enum class BackgroundTone(val label: String) {
    /** 跟随调色板自带的底色。 */
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

/** 成功/运行态绿色：语义色固定，不随主题强调色变化。 */
val SuccessGreen = Color(0xFF10B981)  // miuix-lint:allow 主题/品牌色定义

/** 警示/降速橙：请求中止、额度紧张、慢速账号等中间态（成功态见 [SuccessGreen]）。 */
val WarnAmber = Color(0xFFEF6C00)  // miuix-lint:allow 主题/品牌色定义
