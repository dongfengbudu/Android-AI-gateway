package dev.aigw.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 高保真调性配色生成器。
 *
 * 输入一个色相（[ThemeAccent]），输出整套明/暗色阶：主色、容器色、表面色、描边色
 * 都从同一色相按固定明度阶梯展开，因此换色相时整页色彩关系保持一致（调性统一）。
 * 具体色值属于实现细节，调用方只接触 [ThemeAccent] 这一个概念。
 */
private fun hueTone(hue: Float, saturation: Float, lightness: Float): Color =
    Color.hsl(hue, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))

/** 浅色方案：主色落在 42 明度，表面近白但带一点同色相染色，避免纯灰的廉价感。 */
private fun lightScheme(accent: ThemeAccent): Colors {
    val h = accent.normalizedHue
    val s = accent.normalizedChroma
    return lightColorScheme(
        primary = hueTone(h, s, 0.42f),
        onPrimary = hueTone(h, s * 0.3f, 0.99f),
        primaryVariant = hueTone(h, s, 0.34f),
        onPrimaryVariant = hueTone(h, s * 0.5f, 0.94f),
        primaryContainer = hueTone(h, s * 0.35f, 0.92f),
        onPrimaryContainer = hueTone(h, s * 0.8f, 0.22f),
        secondary = hueTone(wrapHue(h + 32f), s * 0.55f, 0.44f),
        onSecondary = hueTone(h, s * 0.3f, 0.99f),
        secondaryContainer = hueTone(wrapHue(h + 32f), s * 0.35f, 0.93f),
        onSecondaryContainer = hueTone(wrapHue(h + 32f), s * 0.7f, 0.24f),
        tertiaryContainer = hueTone(wrapHue(h - 46f), s * 0.35f, 0.93f),
        onTertiaryContainer = hueTone(wrapHue(h - 46f), s * 0.7f, 0.24f),
        background = hueTone(h, s * 0.08f, 0.985f),
        onBackground = hueTone(h, s * 0.18f, 0.12f),
        onBackgroundVariant = hueTone(h, s * 0.12f, 0.44f),
        surface = hueTone(h, s * 0.06f, 1f),
        onSurface = hueTone(h, s * 0.18f, 0.12f),
        surfaceVariant = hueTone(h, s * 0.12f, 0.965f),
        onSurfaceSecondary = hueTone(h, s * 0.12f, 0.44f),
        onSurfaceVariantSummary = hueTone(h, s * 0.12f, 0.44f),
        onSurfaceVariantActions = hueTone(h, s * 0.12f, 0.44f),
        surfaceContainerHighest = hueTone(h, s * 0.14f, 0.93f),
        onSurfaceContainerHighest = hueTone(h, s * 0.18f, 0.14f),
        surfaceContainer = hueTone(h, s * 0.06f, 1f),
        onSurfaceContainer = hueTone(h, s * 0.18f, 0.12f),
        outline = hueTone(h, s * 0.14f, 0.87f),
        dividerLine = hueTone(h, s * 0.12f, 0.90f),
    )
}

/** 深色方案：主色抬到 78 明度保证暗底可读，表面用同色相深灰拉开层次。 */
private fun darkScheme(accent: ThemeAccent): Colors {
    val h = accent.normalizedHue
    val s = accent.normalizedChroma
    return darkColorScheme(
        primary = hueTone(h, s * 0.85f, 0.78f),
        onPrimary = hueTone(h, s, 0.20f),
        primaryVariant = hueTone(h, s * 0.9f, 0.86f),
        onPrimaryVariant = hueTone(h, s, 0.18f),
        primaryContainer = hueTone(h, s * 0.6f, 0.26f),
        onPrimaryContainer = hueTone(h, s * 0.6f, 0.90f),
        secondary = hueTone(wrapHue(h + 32f), s * 0.5f, 0.78f),
        onSecondary = hueTone(wrapHue(h + 32f), s * 0.6f, 0.20f),
        secondaryContainer = hueTone(wrapHue(h + 32f), s * 0.5f, 0.28f),
        onSecondaryContainer = hueTone(wrapHue(h + 32f), s * 0.5f, 0.90f),
        tertiaryContainer = hueTone(wrapHue(h - 46f), s * 0.5f, 0.28f),
        onTertiaryContainer = hueTone(wrapHue(h - 46f), s * 0.5f, 0.90f),
        background = hueTone(h, s * 0.25f, 0.055f),
        onBackground = hueTone(h, s * 0.10f, 0.95f),
        onBackgroundVariant = hueTone(h, s * 0.12f, 0.64f),
        surface = hueTone(h, s * 0.22f, 0.09f),
        onSurface = hueTone(h, s * 0.10f, 0.95f),
        surfaceVariant = hueTone(h, s * 0.20f, 0.14f),
        onSurfaceSecondary = hueTone(h, s * 0.12f, 0.64f),
        onSurfaceVariantSummary = hueTone(h, s * 0.12f, 0.64f),
        onSurfaceVariantActions = hueTone(h, s * 0.12f, 0.64f),
        surfaceContainerHighest = hueTone(h, s * 0.22f, 0.19f),
        onSurfaceContainerHighest = hueTone(h, s * 0.10f, 0.95f),
        surfaceContainer = hueTone(h, s * 0.22f, 0.09f),
        onSurfaceContainer = hueTone(h, s * 0.10f, 0.95f),
        outline = hueTone(h, s * 0.20f, 0.28f),
        dividerLine = hueTone(h, s * 0.20f, 0.24f),
    )
}

private fun wrapHue(hue: Float): Float = ((hue % 360f) + 360f) % 360f

/**
 * 应用主题（深模块：调用方只给「模式 + 强调色调」，色阶展开全在内部）。
 *
 * @param themeMode 明暗模式（跟随系统 / 深色 / 浅色）。
 * @param accent 强调色调；换色相即整页换色，具体色值由内部调色板生成。
 * @param dynamicColor 动态取色（Android 12+）；开启时以 [accent] 为种子覆盖自定义调色板。
 * @param backgroundOverride 只替换页面底色，卡片/表面色不受影响。
 */
@Composable
fun AiGatewayTheme(
    themeMode: ThemeMode = ThemeMode.System,
    accent: ThemeAccent = defaultThemeAccent,
    dynamicColor: Boolean = false,
    backgroundOverride: Color? = null,
    content: @Composable () -> Unit,
) {
    val colorSchemeMode = when (themeMode) {
        ThemeMode.System -> if (dynamicColor) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
        ThemeMode.Light -> if (dynamicColor) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
        ThemeMode.Dark -> if (dynamicColor) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
    }

    if (dynamicColor) {
        // Monet 动态取色：仅 Monet* 模式读取 keyColor，以强调色为种子
        val controller = remember(colorSchemeMode, accent) {
            ThemeController(colorSchemeMode, keyColor = hueTone(accent.normalizedHue, accent.normalizedChroma, 0.5f))
        }
        MiuixTheme(controller = controller) {
            if (backgroundOverride != null) {
                // 动态取色下只覆写页面底色：嵌套一层 MiuixTheme 做局部改色（theme.md 认可的用法）
                MiuixTheme(
                    colors = MiuixTheme.colorScheme.copy(background = backgroundOverride),
                    content = content,
                )
            } else {
                content()
            }
        }
    } else {
        // isSystemInDarkTheme() 是 @Composable，必须在 remember 外调用
        val systemDark = isSystemInDarkTheme()
        val scheme = remember(colorSchemeMode, accent, backgroundOverride, systemDark) {
            val base = when (colorSchemeMode) {
                ColorSchemeMode.Light -> lightScheme(accent)
                ColorSchemeMode.Dark -> darkScheme(accent)
                else -> if (systemDark) darkScheme(accent) else lightScheme(accent)
            }
            if (backgroundOverride != null) base.copy(background = backgroundOverride) else base
        }
        MiuixTheme(colors = scheme, content = content)
    }
}
