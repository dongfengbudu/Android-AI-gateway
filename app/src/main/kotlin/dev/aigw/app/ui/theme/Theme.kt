package dev.aigw.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

// 黑金极简设计规范 Token（默认值；实际生效值见 Appearance.kt 的 LocalAppColors）
val GoldAccent = Color(0xFFD4A843)
val GoldText = Color(0xFFB88A20)
val HairlineBorder = Color(0xFFEAEAEA)

/**
 * 用 [app] 的两端金构造浅色 Miuix scheme：金色作 primary，
 * 底色/文字沿旧黑金规范，语义色（成功绿/错误红）保持固定。
 */
private fun lightScheme(app: AppColors): Colors = lightColorScheme(
    primary = app.goldStart,
    onPrimary = Color.White,
    primaryVariant = app.goldStart,
    onPrimaryVariant = Color(0xFF6B4E0E),
    primaryContainer = Color(0xFFFBF6EA),
    onPrimaryContainer = Color(0xFF6B4E0E),
    background = Color(0xFFFCFCFD),
    onBackground = Color(0xFF0D0D0D),
    onBackgroundVariant = Color(0xFF777777),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0D0D0D),
    surfaceVariant = Color(0xFFF8F8FA),
    onSurfaceSecondary = Color(0xFF777777),
    onSurfaceVariantSummary = Color(0xFF777777),
    onSurfaceVariantActions = Color(0xFF777777),
    surfaceContainerHighest = Color(0xFFF0F0F2),
    onSurfaceContainerHighest = Color(0xFF0D0D0D),
    surfaceContainer = Color(0xFFFFFFFF),
    onSurfaceContainer = Color(0xFF0D0D0D),
    outline = HairlineBorder,
    dividerLine = Color(0xFFEDEDED),
    tertiaryContainer = Color(0xFFF0FDF4),
    onTertiaryContainer = Color(0xFF166534),
)

/** 用 [app] 的两端金构造深色 Miuix scheme。 */
private fun darkScheme(app: AppColors): Colors = darkColorScheme(
    primary = app.goldStart,
    onPrimary = Color(0xFF141414),
    primaryVariant = app.goldText,
    onPrimaryVariant = Color(0xFFE5C158),
    primaryContainer = Color(0xFF2A2210),
    onPrimaryContainer = Color(0xFFE5C158),
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFF5F5F5),
    onBackgroundVariant = Color(0xFF999999),
    surface = Color(0xFF141414),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceSecondary = Color(0xFF999999),
    onSurfaceVariantSummary = Color(0xFF999999),
    onSurfaceVariantActions = Color(0xFF999999),
    surfaceContainerHighest = Color(0xFF262626),
    onSurfaceContainerHighest = Color(0xFFF5F5F5),
    surfaceContainer = Color(0xFF141414),
    onSurfaceContainer = Color(0xFFF5F5F5),
    outline = Color(0xFF2A2A2A),
    dividerLine = Color(0xFF2A2A2A),
    tertiaryContainer = Color(0xFF064E3B),
    onTertiaryContainer = Color(0xFFD1FAE5),
)

/**
 * 应用主题。
 *
 * @param themeMode 明暗模式（跟随系统 / 深色 / 浅色）。
 * @param appColors 金色渐变两端；通过 [LocalAppColors] 下发给 UI。
 * @param backgroundOverride 只替换页面底色，卡片/表面色不受影响。
 * @param dynamicColor 动态取色（Android 12+）；开启时优先级最高，以金色为种子色。
 */
@Composable
fun AiGatewayTheme(
    dynamicColor: Boolean = false,
    themeMode: ThemeMode = ThemeMode.System,
    appColors: AppColors = defaultAppColors,
    backgroundOverride: Color? = null,
    content: @Composable () -> Unit,
) {
    val colorSchemeMode = when (themeMode) {
        ThemeMode.System -> if (dynamicColor) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
        ThemeMode.Light -> if (dynamicColor) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
        ThemeMode.Dark -> if (dynamicColor) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
    }
    CompositionLocalProvider(LocalAppColors provides appColors) {
        if (dynamicColor) {
            // Monet 动态取色：仅 Monet* 模式读取 keyColor，以金色为种子
            val controller = remember(colorSchemeMode, appColors) {
                ThemeController(colorSchemeMode, keyColor = appColors.goldStart)
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
            val scheme = remember(colorSchemeMode, appColors, backgroundOverride) {
                val base = when (colorSchemeMode) {
                    ColorSchemeMode.Light -> lightScheme(appColors)
                    ColorSchemeMode.Dark -> darkScheme(appColors)
                    else -> if (isSystemInDarkTheme()) darkScheme(appColors) else lightScheme(appColors)
                }
                if (backgroundOverride != null) base.copy(background = backgroundOverride) else base
            }
            MiuixTheme(colors = scheme, content = content)
        }
    }
}

/** Hero 黑金渐变（#1A1A1A → #080808）。 */
val HeroGradient: List<Color> = listOf(Color(0xFF1A1A1A), Color(0xFF080808))

/** 可直接用于 Modifier.background(brush) 的渐变画笔。 */
val HeroBrush: Brush get() = Brush.linearGradient(HeroGradient)
