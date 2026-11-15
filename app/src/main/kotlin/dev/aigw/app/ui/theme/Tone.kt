package dev.aigw.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 色阶生成的数学底座：OKLCH 感知均匀色阶 + WCAG 对比度校验。
 *
 * 为什么不用 HSL：HSL 的「明度」与人的亮度感知不相关——同一个 L 下，黄色看起来远比蓝色亮，
 * 所以按固定明度阶梯展开出来的色板，换色相时对比度会忽高忽低（换到黄绿色文字就开始糊）。
 * OKLCH 的 L 是感知均匀的，tone 相同就意味着看起来一样亮，色板才能跨色相复用同一套数值。
 *
 * 这里只暴露两个概念：[toneColor]（按 tone 取一个颜色）、[contrastWith] + [toneMeeting]
 * （保证前景色在指定背景上达到目标对比度）。调用方不需要知道 OKLCH 存在。
 */

/** 一个颜色在 OKLCH 里的坐标；[tone] 用 0..100 表示（人眼感知的明度档位）。 */
internal data class Oklch(val hue: Float, val chroma: Float, val tone: Float)

/**
 * 强调色的 OKLCH 彩度上限。色相环上不同角度的最大可显示彩度差异很大，这里只做上限，
 * 超出部分由 [toneColor] 收敛；中性阶用 0.004~0.02 的极低彩度做同色相染色。
 */
internal const val MaxChroma = 0.19f

/** tone（0..100）+ 色相 + 彩度 → sRGB 颜色。超出 sRGB 色域时自动降低彩度收敛（不裁切通道，避免色偏）。 */
internal fun toneColor(hue: Float, chroma: Float, tone: Float): Color {
    val l = (tone.coerceIn(0f, 100f) / 100f).toDouble()
    var c = chroma.coerceIn(0f, 0.4f).toDouble()
    var result = oklchToLinear(l, c, hue)
    var guard = 0
    while (!inGamut(result) && guard < 24) {
        c *= 0.88
        result = oklchToLinear(l, c, hue)
        guard++
    }
    return Color(
        red = gammaEncode(result[0]),
        green = gammaEncode(result[1]),
        blue = gammaEncode(result[2]),
    )
}

/**
 * 前景色 [fg] 落在背景 [bg] 上的 WCAG 对比度（1..21）。
 */
fun contrastWith(fg: Color, bg: Color): Float {
    val a = fg.luminance()
    val b = bg.luminance()
    val hi = if (a >= b) a else b
    val lo = if (a >= b) b else a
    return (hi + 0.05f) / (lo + 0.05f)
}

/**
 * 从 [startTone] 出发沿 [direction] 逐档挪明度，直到在 [background] 上至少达到 [minContrast]。
 *
 * 这是「换任何强调色都不会把文字做糊」的机制：色板里每个前景色都由调用点声明它要落在哪个底上、
 * 需要多少对比度，不达标就自动往深（或浅）走。极窄色域下可能仍不达标，此时返回能达到的最优点。
 */
internal fun toneMeeting(
    coord: Oklch,
    background: Color,
    minContrast: Float,
    startTone: Float,
    direction: Int,
): Color {
    var tone = startTone.coerceIn(0f, 100f)
    var best = toneColor(coord.hue, coord.chroma, tone)
    var bestContrast = contrastWith(best, background)
    if (bestContrast >= minContrast) return best
    var guard = 0
    while (bestContrast < minContrast && tone in 0f..100f && guard < 100) {
        tone += direction
        val candidate = toneColor(coord.hue, coord.chroma, tone)
        val contrast = contrastWith(candidate, background)
        // 对比度开始回落说明已越过最优点（深色底上往白走会重新并上来），停在上一个更优点
        if (contrast <= bestContrast) break
        best = candidate
        bestContrast = contrast
        guard++
    }
    return best
}

/** 前景落在背景上时该用的最小对比度：正文/小字 4.5（AA），大字与图形 3.0。 */
internal const val ContrastBody = 4.5f
internal const val ContrastLarge = 3.0f

private data class LinearRgb(val r: Double, val g: Double, val b: Double)

private fun oklchToLinear(l: Double, c: Double, hueDeg: Float): LinearRgb {
    val h = Math.toRadians(hueDeg.toDouble())
    val a = c * cos(h)
    val b = c * sin(h)
    val l_ = l + 0.3963377774 * a + 0.2158037573 * b
    val m_ = l - 0.1055613458 * a - 0.0638541728 * b
    val s_ = l - 0.0894841775 * a - 1.2914855480 * b
    val lc = l_ * l_ * l_
    val mc = m_ * m_ * m_
    val sc = s_ * s_ * s_
    return LinearRgb(
        r = 4.0767416621 * lc - 3.3077115913 * mc + 0.2309699292 * sc,
        g = -1.2684380046 * lc + 2.6097574011 * mc - 0.3413193965 * sc,
        b = -0.0041960863 * lc - 0.7034186147 * mc + 1.7076147010 * sc,
    )
}

/** 通道值略容许越界（浮点误差），越界明显时说明该彩度在这个 tone 下不在 sRGB 内。 */
private fun inGamut(v: LinearRgb): Boolean =
    v.r >= -0.002 && v.g >= -0.002 && v.b >= -0.002 && v.r <= 1.002 && v.g <= 1.002 && v.b <= 1.002

private fun gammaEncode(linear: Double): Float {
    val v = linear.coerceIn(0.0, 1.0)
    val encoded = if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1.0 / 2.4) - 0.055
    return encoded.toFloat().coerceIn(0f, 1f)
}
