package dev.aigw.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import dev.aigw.app.ui.glass.GlassFinish
import dev.aigw.app.ui.glass.GlassScope
import dev.aigw.app.ui.glass.defaultGlassFinish
import dev.aigw.app.ui.glass.rememberGlassContext
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 调色板：中性底 + 强调点缀。
 *
 * 三层结构：
 * 1. **中性阶**（[tintChroma] 级低彩度）承担页面底色、卡片、描边、正文——大面积永远不花；
 * 2. **强调色**（[ThemeAccent] 色相 + 彩度）只出现在主色按钮、选中态、链接、状态强调；
 * 3. **语义叠加**（[Overlays]）承接弱底、光环、轨道这类带 alpha 的角色。
 *
 * 每个前景色都不写死明度，而是由 [readableOn] 在它真实的背景上做 WCAG 校验后取档：
 * 换任何强调色、切任何底色档位，正文对比度都不会掉到不可读。
 */
/**
 * 强调色的固定 OKLCH 彩度。实际显示彩度仍由 [toneColor] 按色相收敛到 sRGB 色域内
 * （黄绿区会自动降彩度，否则会出现不可能存在的艳色）。
 */
private const val AccentChroma = 0.14f

/**
 * 强调色的 OKLCH 彩度。
 *
 * 不取 [ThemeAccent.chroma]：那个字段是色相环拖动时同时写入的**饱和度**（圆心→边缘 0→1），
 * 当强度用会把圆心附近选到的低饱和色推到 Tone 45 的深色上去、变得过艳且不和谐；
 * 饱和度在手选颜色里的体现是中性阶的染色（[tintChroma]），而不是主色本身。
 */
val ThemeAccent.oklchChroma: Float
    get() = AccentChroma

/**
 * 在 [bg] 上取一个至少 [min] 对比度的文字色。
 *
 * 往暗、往亮两个方向都走一遍再挑，而不是先判断底色亮暗再定方向：实测 tone-74 的主色亮度
 * 落在判据的模糊带（≈0.41），被误判成深底而配了白字，只剩 2.3:1（配黑字可到 9:1）。
 *
 * [faint] 决定取哪一端：正文取高对比端；次要文字取「仍达标但更淡」的一端。两者若从同一端
 * 出发，会一起收敛到纯黑/纯白，次要文字与正文同色、层级塌平（实测出来的坑）。
 */
private fun readableOn(
    bg: Color,
    hue: Float,
    min: Float,
    faint: Boolean = false,
    chroma: Float = 0.02f,
): Color {
    val coord = Oklch(hue, chroma, 0f)
    val darker = toneMeeting(coord, bg, min, if (faint) 66f else 20f, -1)
    val lighter = toneMeeting(coord, bg, min, if (faint) 48f else 96f, 1)
    val darkerContrast = contrastWith(darker, bg)
    val lighterContrast = contrastWith(lighter, bg)
    val darkerOk = darkerContrast >= min
    val lighterOk = lighterContrast >= min
    return when {
        // 两端都达标：次要要更淡的那个，正文要更强的那个
        darkerOk && lighterOk && faint -> if (darkerContrast <= lighterContrast) darker else lighter
        darkerOk && lighterOk -> if (darkerContrast >= lighterContrast) darker else lighter
        darkerOk -> darker
        lighterOk -> lighter
        // 两端都不达标（极窄色域）：取能达到的最优点
        else -> if (darkerContrast >= lighterContrast) darker else lighter
    }
}

/**
 * 中性阶的强调色染色浓度 = 档位基线 × 饱和度系数。
 *
 * [chroma] 来自色相环的径向位置（圆心 0 → 边缘 1）：拖得越靠外，整页底色/卡片/描边的
 * 同色相染色越重——这才是「饱和度」在 neutral-base 方案里的正确作用点。
 * 「纯净」档恒为 0（用户明确要无色偏），主色本身不受此影响（见 AccentChroma）。
 */
private fun tintChroma(tone: BackgroundTone, dark: Boolean, chroma: Float): Float {
    val base = when (tone) {
        // 「纯净」档恒为 0：用户明确要无色偏（主色本身不受此影响，见 AccentChroma）
        BackgroundTone.Plain -> return 0f
        BackgroundTone.Theme -> if (dark) 0.014f else 0.006f
        BackgroundTone.Soft -> if (dark) 0.024f else 0.020f
        BackgroundTone.Rich -> if (dark) 0.052f else 0.050f
    }
    return base * (0.35f + chroma.coerceIn(0f, 1f) * 1.15f)
}

/** 页面底色档位 → 实际颜色。四档只在「中性明度 + 染色浓度」上浮动，不改变明暗极性。 */
private fun backgroundFor(hue: Float, dark: Boolean, tone: BackgroundTone, chroma: Float): Color {
    val toneValue = when (tone) {
        BackgroundTone.Theme -> if (dark) 15f else 98.5f
        BackgroundTone.Plain -> if (dark) 0f else 100f
        BackgroundTone.Soft -> if (dark) 14f else 98f
        BackgroundTone.Rich -> if (dark) 12.5f else 96.5f
    }
    return toneColor(hue, tintChroma(tone, dark, chroma), toneValue)
}

/** 某档位实际生效的页面底色（设置页色点预览用；按强调色调性推导）。 */
fun themeBackgroundColor(accent: ThemeAccent, dark: Boolean, tone: BackgroundTone): Color =
    backgroundFor(accent.normalizedHue, dark, tone, accent.normalizedChroma)

/**
 * 卡片容器明度：必须与页面底色拉开一档，否则「纯净」底色（纯白/纯黑）下卡片边界直接消失。
 * 浅色底太亮时改为略暗于底色，深色底则一律提亮。
 */
private fun containerToneFor(bgTone: Float, dark: Boolean): Float = if (dark) {
    bgTone + 6f
} else {
    if (bgTone >= 99f) bgTone - 2.4f else bgTone + 1.4f
}

private fun backgroundToneValue(dark: Boolean, tone: BackgroundTone): Float = when (tone) {
    BackgroundTone.Theme -> if (dark) 15f else 98.5f
    BackgroundTone.Plain -> if (dark) 0f else 100f
    BackgroundTone.Soft -> if (dark) 14f else 98f
    BackgroundTone.Rich -> if (dark) 12.5f else 96.5f
}

/** 浅色方案：中性近白底 + 强调色点缀；所有前景色由 readableOn 校验。 */
private fun lightScheme(accent: ThemeAccent, tone: BackgroundTone): Colors {
    val h = accent.normalizedHue
    val c = accent.oklchChroma
    val nc = tintChroma(tone, dark = false, accent.normalizedChroma)
    val bgTone = backgroundToneValue(false, tone)
    val bg = backgroundFor(h, false, tone, accent.normalizedChroma)
    val containerTone = containerToneFor(bgTone, dark = false)

    val surface = toneColor(h, nc, 100f)
    val container = toneColor(h, nc, containerTone)
    val variant = toneColor(h, nc, bgTone - 2f)
    val containerHigh = toneColor(h, nc, containerTone - 3f)
    val containerHighest = toneColor(h, nc, containerTone - 6.5f)
    val outline = toneColor(h, 0.012f, 84f)
    val divider = toneColor(h, 0.010f, 91f)

    val primary = toneColor(h, c, 45f)
    val primaryVariant = toneColor(h, c, 38f)
    val primaryContainer = toneColor(h, c * 0.30f, 92f)
    val tertiaryContainer = toneColor(h, c * 0.20f, 94f)
    val secondary = toneColor(h, 0.014f, 93f)
    val secondaryContainer = toneColor(h, 0.012f, 95f)
    val error = toneColor(ErrorHue, 0.16f, 48f)
    val errorContainer = toneColor(ErrorHue, 0.04f, 95f)

    return lightColorScheme(
        primary = primary,
        onPrimary = readableOn(primary, h, ContrastBody),
        primaryVariant = primaryVariant,
        onPrimaryVariant = readableOn(primaryVariant, h, ContrastBody),
        primaryContainer = primaryContainer,
        onPrimaryContainer = readableOn(primaryContainer, h, ContrastBody, chroma = c * 0.5f),
        secondary = secondary,
        onSecondary = readableOn(secondary, h, ContrastBody),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = readableOn(secondaryContainer, h, ContrastBody),
        secondaryContainerVariant = toneColor(h, 0.012f, 96f),
        onSecondaryContainerVariant = readableOn(toneColor(h, 0.012f, 96f), h, ContrastBody),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = readableOn(tertiaryContainer, h, ContrastBody, chroma = c * 0.5f),
        tertiaryContainerVariant = toneColor(h, c * 0.14f, 96f),
        background = bg,
        onBackground = readableOn(bg, h, 7f),
        onBackgroundVariant = readableOn(bg, h, ContrastBody, faint = true),
        surface = surface,
        onSurface = readableOn(container, h, 7f),
        surfaceVariant = variant,
        onSurfaceSecondary = readableOn(container, h, 7f),
        onSurfaceVariantSummary = readableOn(container, h, ContrastBody, faint = true),
        onSurfaceVariantActions = readableOn(container, h, ContrastBody, faint = true),
        surfaceContainer = container,
        onSurfaceContainer = readableOn(container, h, 7f),
        onSurfaceContainerVariant = readableOn(container, h, ContrastBody, faint = true),
        surfaceContainerHigh = containerHigh,
        onSurfaceContainerHigh = readableOn(containerHigh, h, ContrastBody, faint = true),
        surfaceContainerHighest = containerHighest,
        onSurfaceContainerHighest = readableOn(containerHighest, h, 7f),
        outline = outline,
        dividerLine = divider,
        error = error,
        onError = readableOn(error, ErrorHue, ContrastBody),
        errorContainer = errorContainer,
        onErrorContainer = readableOn(errorContainer, ErrorHue, ContrastBody),
        disabledPrimary = toneColor(h, c * 0.30f, 86f),
        disabledOnPrimary = toneColor(h, 0.02f, 99f),
        disabledPrimaryButton = toneColor(h, c * 0.30f, 86f),
        disabledOnPrimaryButton = toneColor(h, 0.02f, 99f),
        disabledPrimarySlider = toneColor(h, c * 0.26f, 89f),
        disabledSecondary = toneColor(h, 0.008f, 96f),
        disabledOnSecondary = toneColor(h, 0.01f, 99f),
        disabledSecondaryVariant = toneColor(h, 0.008f, 97f),
        disabledOnSecondaryVariant = readableOn(toneColor(h, 0.008f, 97f), h, ContrastLarge),
        disabledOnSurface = readableOn(container, h, ContrastLarge, faint = true),
        sliderKeyPoint = primary.copy(alpha = 0.30f),
        sliderKeyPointForeground = primary,
        sliderBackground = toneColor(h, 0.010f, 90f),
        windowDimming = Color.Black.copy(alpha = 0.32f),
    )
}

/** 深色方案：同一套结构在深色极性的镜像；容器比底色亮，正文比容器亮。 */
private fun darkScheme(accent: ThemeAccent, tone: BackgroundTone): Colors {
    val h = accent.normalizedHue
    val c = accent.oklchChroma
    val nc = tintChroma(tone, dark = true, accent.normalizedChroma)
    val bgTone = backgroundToneValue(true, tone)
    val bg = backgroundFor(h, true, tone, accent.normalizedChroma)
    val containerTone = containerToneFor(bgTone, dark = true)

    val surface = toneColor(h, nc + 0.004f, bgTone + 4f)
    val container = toneColor(h, nc + 0.004f, containerTone)
    val variant = toneColor(h, nc + 0.004f, bgTone + 8f)
    val containerHigh = toneColor(h, nc + 0.004f, containerTone + 4f)
    val containerHighest = toneColor(h, nc + 0.004f, containerTone + 8f)
    val outline = toneColor(h, 0.014f, 40f)
    val divider = toneColor(h, 0.014f, 28f)

    val primary = toneColor(h, c * 0.92f, 74f)
    val primaryVariant = toneColor(h, c * 0.85f, 82f)
    val primaryContainer = toneColor(h, c * 0.45f, 30f)
    val tertiaryContainer = toneColor(h, c * 0.35f, 26f)
    val secondary = toneColor(h, 0.016f, 30f)
    val secondaryContainer = toneColor(h, 0.016f, 26f)
    val error = toneColor(ErrorHue, 0.14f, 68f)
    val errorContainer = toneColor(ErrorHue, 0.08f, 26f)

    return darkColorScheme(
        primary = primary,
        onPrimary = readableOn(primary, h, ContrastBody),
        primaryVariant = primaryVariant,
        onPrimaryVariant = readableOn(primaryVariant, h, ContrastBody),
        primaryContainer = primaryContainer,
        onPrimaryContainer = readableOn(primaryContainer, h, ContrastBody, chroma = c * 0.5f),
        secondary = secondary,
        onSecondary = readableOn(secondary, h, ContrastBody),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = readableOn(secondaryContainer, h, ContrastBody),
        secondaryContainerVariant = toneColor(h, 0.016f, 29f),
        onSecondaryContainerVariant = readableOn(toneColor(h, 0.016f, 29f), h, ContrastBody),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = readableOn(tertiaryContainer, h, ContrastBody, chroma = c * 0.5f),
        tertiaryContainerVariant = toneColor(h, c * 0.28f, 24f),
        background = bg,
        onBackground = readableOn(bg, h, 7f),
        onBackgroundVariant = readableOn(bg, h, ContrastBody, faint = true),
        surface = surface,
        onSurface = readableOn(container, h, 7f),
        surfaceVariant = variant,
        onSurfaceSecondary = readableOn(container, h, 7f),
        onSurfaceVariantSummary = readableOn(container, h, ContrastBody, faint = true),
        onSurfaceVariantActions = readableOn(container, h, ContrastBody, faint = true),
        surfaceContainer = container,
        onSurfaceContainer = readableOn(container, h, 7f),
        onSurfaceContainerVariant = readableOn(container, h, ContrastBody, faint = true),
        surfaceContainerHigh = containerHigh,
        onSurfaceContainerHigh = readableOn(containerHigh, h, ContrastBody, faint = true),
        surfaceContainerHighest = containerHighest,
        onSurfaceContainerHighest = readableOn(containerHighest, h, 7f),
        outline = outline,
        dividerLine = divider,
        error = error,
        onError = readableOn(error, ErrorHue, ContrastBody),
        errorContainer = errorContainer,
        onErrorContainer = readableOn(errorContainer, ErrorHue, ContrastBody),
        disabledPrimary = toneColor(h, c * 0.22f, 34f),
        disabledOnPrimary = toneColor(h, 0.02f, 62f),
        disabledPrimaryButton = toneColor(h, c * 0.22f, 34f),
        disabledOnPrimaryButton = toneColor(h, 0.02f, 62f),
        disabledPrimarySlider = toneColor(h, c * 0.20f, 32f),
        disabledSecondary = toneColor(h, 0.012f, 22f),
        disabledOnSecondary = toneColor(h, 0.012f, 55f),
        disabledSecondaryVariant = toneColor(h, 0.012f, 24f),
        disabledOnSecondaryVariant = readableOn(toneColor(h, 0.012f, 24f), h, ContrastLarge),
        disabledOnSurface = readableOn(container, h, ContrastLarge, faint = true),
        sliderKeyPoint = primary.copy(alpha = 0.34f),
        sliderKeyPointForeground = primary,
        sliderBackground = toneColor(h, 0.016f, 32f),
        windowDimming = Color.Black.copy(alpha = 0.55f),
    )
}

/** 当前模式下是否为深色（主题内部与设置页预览共用同一判据）。 */
@Composable
fun isDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
    ThemeMode.System -> isSystemInDarkTheme()
}

/**
 * 应用主题（深模块：调用方只给「模式 + 强调色调 + 质感」，色阶、材质与对比度校验全在内部）。
 *
 * @param themeMode 明暗模式（跟随系统 / 深色 / 浅色）。
 * @param accent 强调色调；换色相即整页换色，具体色值由内部调色板生成。
 * @param dynamicColor 动态取色（Android 12+）；开启时整盘色由系统调色板接管，[accent] 与
 *   [backgroundTone] 暂不参与（否则会出现两个来源互相覆写）。
 * @param backgroundTone 页面底色档位（中性轴上的染色浓度）。
 * @param glassFinish 质感档位；非平面时提供玻璃上下文（氛围场 + 材质）。
 */
@Composable
fun AiGatewayTheme(
    themeMode: ThemeMode = ThemeMode.System,
    accent: ThemeAccent = defaultThemeAccent,
    dynamicColor: Boolean = false,
    backgroundTone: BackgroundTone = BackgroundTone.Theme,
    glassFinish: GlassFinish = defaultGlassFinish,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(themeMode)
    val glass = glassFinish != GlassFinish.Flat
    val colorSchemeMode = when (themeMode) {
        ThemeMode.System -> if (dynamicColor) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
        ThemeMode.Light -> if (dynamicColor) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
        ThemeMode.Dark -> if (dynamicColor) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
    }

    if (dynamicColor) {
        // 动态取色必须传 keyColor = null：ThemeController.currentColors() 是
        // `keyColor?.let { colorsFromSeed(它) } ?: platformDynamicColors(dark)`，只要 keyColor 非空
        // 就只会用这个种子自己拼一套 MD3 方案，永远不读系统调色板（settings 里的
        // theme_customization_overlay_packages / system_palette）。之前恒传强调色种子，
        // 出来的色相跟用户自己选的差不多，开关看着就像没生效。
        val controller = remember(colorSchemeMode) {
            ThemeController(colorSchemeMode, keyColor = null)
        }
        MiuixTheme(controller = controller) {
            // 动态模式下不覆写 background：整盘色由系统接管，再用强调色算一个底色只会与系统打架
            ThemeLayer(
                colors = MiuixTheme.colorScheme,
                accent = accent,
                dark = dark,
                finish = glassFinish,
                content = content,
            )
        }
    } else {
        // isSystemInDarkTheme() 是 @Composable，必须在 remember 外调用
        val systemDark = isSystemInDarkTheme()
        val scheme = remember(colorSchemeMode, accent, backgroundTone, systemDark) {
            when (colorSchemeMode) {
                ColorSchemeMode.Light -> lightScheme(accent, backgroundTone)
                ColorSchemeMode.Dark -> darkScheme(accent, backgroundTone)
                else -> if (systemDark) darkScheme(accent, backgroundTone) else lightScheme(accent, backgroundTone)
            }
        }
        ThemeLayer(colors = scheme, accent = accent, dark = dark, finish = glassFinish, content = content)
    }
}

/** 把色板、语义叠加色与玻璃上下文一次性提供下去；调用方只需要 [content]。 */
@Composable
private fun ThemeLayer(
    colors: Colors,
    accent: ThemeAccent,
    dark: Boolean,
    finish: GlassFinish,
    content: @Composable () -> Unit,
) {
    // 活动实例，不能一次性 copy：动态取色下 miuix 就地更新同一个 Colors 对象，快照会把换壁纸前的色钉住。
    // 只从 colors 读、往 effective 写；反过来会在本作用域形成读后写自激重组。
    val effective = remember(colors) { colors.copy() }.apply {
        updateColorsFrom(colors)
        if (finish != GlassFinish.Flat) applyGlassSurfaceAlpha(colors, dark)
    }
    // 取实际参与计算的两个色值当 key（Colors 字段是 mutableStateOf，整对象当 key 不会随内容变化重组）
    val primary = effective.primary
    val background = effective.background
    val overlays = remember(primary, background, dark, accent.normalizedHue) {
        overlaysFor(primary = primary, background = background, dark = dark, tintHue = accent.normalizedHue)
    }
    MiuixTheme(colors = effective) {
        CompositionLocalProvider(LocalOverlays provides overlays) {
            GlassScope(
                rememberGlassContext(finish, background, accent.normalizedHue, dark),
                content = content,
            )
        }
    }
}

/**
 * 玻璃档下把控件层的 surface 系列降不透明度，结果写入接收者。
 *
 * 卡片本身改画玻璃后底色是透的，但卡内的开关轨道、下拉栏、进位条这些 miuix 子控件仍拿
 * surfaceContainer* 当实心底；不降不透明度，它们会在一块透底玻璃上显出一块块实心面板。
 * 文字类 token（on*）一律不动，对比度不受影响。色值一律从 [source] 取，保证可反复调用不累加 alpha。
 */
private fun Colors.applyGlassSurfaceAlpha(source: Colors, dark: Boolean) {
    surfaceContainer = source.surfaceContainer.copy(alpha = if (dark) 0.52f else 0.62f)
    surfaceContainerHigh = source.surfaceContainerHigh.copy(alpha = if (dark) 0.55f else 0.66f)
    surfaceContainerHighest = source.surfaceContainerHighest.copy(alpha = if (dark) 0.60f else 0.72f)
    surfaceVariant = source.surfaceVariant.copy(alpha = if (dark) 0.48f else 0.60f)
    dividerLine = source.dividerLine.copy(alpha = if (dark) 0.45f else 0.55f)
}
