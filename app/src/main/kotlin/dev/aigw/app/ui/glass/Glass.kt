package dev.aigw.app.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.Rectangle
import com.kyant.shapes.RoundedRectangle
import com.kyant.shapes.RoundedCornerStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 质感档位：平面＝现状 HyperOS 观感，玻璃/清透＝液态玻璃的两档薄厚。 */
enum class GlassFinish(val label: String, val summary: String) {
    Flat(
        label = "平面",
        summary = "HyperOS 原生实心底，不做透明、模糊与折射，老设备与低电耗时选它",
    ),
    Regular(
        label = "玻璃",
        summary = "磨砂玻璃：背后内容被提高饱和度并模糊后透出，边缘折射并带高光与投影",
    ),
    Clear(
        label = "清透",
        summary = "更薄的玻璃：底色更透、折射与色散更强，氛围底最明显",
    ),
}

/** 默认质感：液态玻璃（可在外观主题页改回平面）。 */
val defaultGlassFinish = GlassFinish.Regular

/** 玻璃表面的种类。光学参数按种类给：卡片要静、栏要透、胶囊要弹，调用方不碰这些数值。 */
enum class GlassSurface { Card, Bar, Pill, Well }

/** 一次渲染所需的玻璃上下文：档位 + 氛围场。 */
@Immutable
class GlassContext internal constructor(
    val finish: GlassFinish,
    val field: GlassField,
)

private val FlatGlassContext = GlassContext(GlassFinish.Flat, GlassField.Disabled)

val LocalGlassContext: ProvidableCompositionLocal<GlassContext> = staticCompositionLocalOf { FlatGlassContext }

/** 在给定玻璃上下文里渲染 [content]。 */
@Composable
fun GlassScope(context: GlassContext, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlassContext provides context, content = content)
}

/**
 * 组装当前页面的玻璃上下文。[background] 要传实际生效的页面底色：氛围场在同一色底上展开，
 * 否则「底色档位」设置会在玻璃下面失真。平面档直接给空场，不绘也不采。
 */
@Composable
fun rememberGlassContext(finish: GlassFinish, background: Color, hue: Float, dark: Boolean): GlassContext {
    if (finish == GlassFinish.Flat) return FlatGlassContext
    val field = rememberGlassField(
        base = background,
        hue = hue,
        dark = dark,
        intense = finish == GlassFinish.Clear,
    )
    return remember(finish, field) { GlassContext(finish, field) }
}

/** 当前是否处于玻璃档。为假时所有 glass* 修饰符原样返回，页面回到现状观感。 */
@Composable
fun glassEnabled(): Boolean = LocalGlassContext.current.finish != GlassFinish.Flat

/** 卡片玻璃。圆角与 Radius.card 对齐（20dp），改用连续曲率边——iOS 的玻璃边不是圆弧。 */
@Composable
fun Modifier.glassCard(cornerRadius: Dp = 20.dp): Modifier {
    val shape = remember(cornerRadius) { RoundedRectangle(cornerRadius, RoundedCornerStyle.Continuous) }
    return glassSurface(GlassSurface.Card, shape)
}

/** 栏玻璃（顶栏、底部导航）。[pill] 为真时按胶囊收边，用于悬浮底栏。 */
@Composable
fun Modifier.glassBar(source: Backdrop, pill: Boolean = false): Modifier {
    val context = LocalGlassContext.current
    if (context.finish == GlassFinish.Flat) return this
    val shape = remember(pill) { if (pill) Capsule() else Rectangle }
    return glassSurface(GlassSurface.Bar, shape, rememberCombinedBackdrop(context.field, source))
}

/** 胶囊玻璃：状态胶囊、筛选胶囊、主按钮。 */
@Composable
fun Modifier.glassPill(): Modifier {
    val shape = remember { Capsule() }
    return glassSurface(GlassSurface.Pill, shape)
}

/** 凹槽玻璃：地址条这类内嵌输入位，折射量比胶囊再小一档。圆角传大于半高即为胶囊形。 */
@Composable
fun Modifier.glassWell(cornerRadius: Dp = 12.dp): Modifier {
    val shape = remember(cornerRadius) { RoundedRectangle(cornerRadius, RoundedCornerStyle.Continuous) }
    return glassSurface(GlassSurface.Well, shape)
}

/**
 * 玻璃表面统一装配：氛围场（栏类再叠上「表面背后那层真实内容」）→ 提高饱和度 → 模糊 → 边缘折射，
 * 最后铺 tint 压住对比度，高光/投影/内阴影由库按同一 shape 补齐。
 */
@Composable
private fun Modifier.glassSurface(surface: GlassSurface, shape: Shape, source: Backdrop? = null): Modifier {
    val context = LocalGlassContext.current
    if (context.finish == GlassFinish.Flat) return this
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val material = materialOf(surface, context.finish, dark)
    val tint = MiuixTheme.colorScheme.surface.copy(alpha = material.tintAlpha)
    return drawBackdrop(
        backdrop = source ?: context.field,
        shape = { shape },
        effects = {
            vibrancy()
            // 深底下把背后内容压暗一点，否则亮色内容滚到玻璃下会顶掉正文对比
            colorControls(
                brightness = if (dark) -0.12f else 0f,
                contrast = if (dark) 1.05f else 1f,
                saturation = if (dark) 1.05f else 1f,
            )
            blur(material.blur.toPx())
            lens(
                refractionHeight = material.refraction.toPx(),
                refractionAmount = (material.refraction * 1.9f).toPx(),
                depthEffect = true,
                chromaticAberration = material.dispersion,
            )
        },
        highlight = { Highlight.Default },
        shadow = { Shadow.Default },
        innerShadow = if (material.innerShadow) {
            { InnerShadow(radius = 12.dp, offset = DpOffset(0.dp, 4.dp), color = InnerShadowTint) }
        } else {
            null
        },
        onDrawSurface = { drawRect(tint) },
    )
}

/** 内阴影语义色：只做「玻璃内沿压暗」，不参与主题配色。 */
private val InnerShadowTint = Color.Black.copy(alpha = 0.18f)  // miuix-lint:allow 阴影语义色定义

private class Material(
    val blur: Dp,
    val refraction: Dp,
    val tintAlpha: Float,
    val dispersion: Boolean,
    val innerShadow: Boolean,
)

/**
 * 各表面在不同档位下的光学参数。原则：模糊半径越大越「厚」（栏），折射越大越「鼓」（胶囊），
 * tint 浓度保证玻璃上的正文在明暗两种极性下都有足够对比；清透档把 tint 降下、折射抬起。
 */
private fun materialOf(surface: GlassSurface, finish: GlassFinish, dark: Boolean): Material {
    val clear = finish == GlassFinish.Clear
    // 清透档相对本档 tint 的减量：栏减得最多（要真的看得穿），胶囊其次
    val clearDelta = when (surface) {
        GlassSurface.Bar -> -0.16f
        GlassSurface.Pill, GlassSurface.Well -> -0.18f
        GlassSurface.Card -> -0.14f
    }
    val tint = when (surface) {
        GlassSurface.Card -> if (dark) 0.60f else 0.66f
        GlassSurface.Bar -> if (dark) 0.66f else 0.72f
        GlassSurface.Pill -> if (dark) 0.64f else 0.70f
        GlassSurface.Well -> if (dark) 0.72f else 0.78f
    }
    return when (surface) {
        GlassSurface.Card -> Material(
            blur = if (clear) 5.dp else 9.dp,
            refraction = if (clear) 18.dp else 14.dp,
            tintAlpha = (tint + if (clear) clearDelta else 0f).coerceIn(0.2f, 0.95f),
            dispersion = clear,
            innerShadow = true,
        )

        GlassSurface.Bar -> Material(
            blur = if (clear) 14.dp else 24.dp,
            refraction = if (clear) 24.dp else 18.dp,
            tintAlpha = (tint + if (clear) clearDelta else 0f).coerceIn(0.2f, 0.95f),
            dispersion = clear,
            innerShadow = true,
        )

        GlassSurface.Pill -> Material(
            blur = if (clear) 3.dp else 6.dp,
            refraction = if (clear) 16.dp else 12.dp,
            tintAlpha = (tint + if (clear) clearDelta else 0f).coerceIn(0.2f, 0.95f),
            dispersion = clear,
            innerShadow = false,
        )

        GlassSurface.Well -> Material(
            blur = if (clear) 2.dp else 4.dp,
            refraction = if (clear) 10.dp else 8.dp,
            tintAlpha = (tint + if (clear) clearDelta else 0f).coerceIn(0.2f, 0.95f),
            dispersion = clear,
            innerShadow = false,
        )
    }
}
