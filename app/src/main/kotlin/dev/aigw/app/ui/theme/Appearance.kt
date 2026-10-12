package dev.aigw.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.Locale

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

/** 默认强调色：靛蓝，中性底、冷调点缀。 */
val defaultThemeAccent = ThemeAccent(ThemeAccent.DefaultHue, ThemeAccent.DefaultChroma)

/**
 * 解析 `#RRGGBB` 或 `RRGGBB`（大小写不敏感）为不透明颜色；非法输入返回 null。
 */
fun parseHexColor(input: String): Color? {
    val raw = input.trim().removePrefix("#")
    if (raw.length != 6) return null
    val value = raw.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or value)  // miuix-lint:allow 主题/品牌色定义
}

/** 成功/运行态绿色：语义色固定，不随主题强调色变化。 */
val SuccessGreen = Color(0xFF10B981)  // miuix-lint:allow 主题/品牌色定义

/** 警示/降速橙：请求中止、额度紧张、慢速账号等中间态（成功态见 [SuccessGreen]）。 */
val WarnAmber = Color(0xFFEF6C00)  // miuix-lint:allow 主题/品牌色定义

/** 输出 `#RRGGBB`（大写）。 */
fun formatHex(color: Color): String =
    String.format(Locale.US, "#%06X", color.toArgb() and 0xFFFFFF)
