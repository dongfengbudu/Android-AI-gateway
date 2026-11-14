package dev.aigw.app.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.Backdrop

/**
 * 氛围场：一整屏的大尺度极光底。
 *
 * 玻璃得有东西可折射——页面底色本是纯色，纯色的模糊与折射结果仍是纯色，玻璃就「看不出来」。
 * 所以玻璃档位下在最底层画一层按强调色展开的极光，并把它作为 [Backdrop] 供每个玻璃表面采样：
 * 表面里的极光与真实可见的极光按屏幕坐标严格对齐，于是玻璃只呈现「自己那一片」极光切片。
 */
@Stable
class GlassField internal constructor(
    internal val base: Color,
    internal val blobs: List<AuroraBlob>,
) : Backdrop {

    /** 极光铺满的视口尺寸（px），由 [AmbientField] 上报；玻璃表面据此对齐切片。 */
    internal var viewport: IntSize by mutableStateOf(IntSize.Zero)

    /** 极光绘制区域在窗口里的起点（px）。页面底层时就是 (0,0)；嵌在小样张里就不是。 */
    internal var paintedAt: Offset by mutableStateOf(Offset.Zero)

    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val coordinates = coordinates ?: return
        val viewport = viewport
        if (viewport.width == 0 || viewport.height == 0) return
        // 极光上坐标为 a 的一点，在窗口里位于 paintedAt + a；当前玻璃表面位于 p，
        // 所以上局部坐标要画在 a + (paintedAt - p)，切片才能与屏幕上真实的极光对齐。
        val origin = paintedAt - coordinates.positionInWindow()
        withTransform({ translate(origin.x, origin.y) }) {
            drawAurora(base, blobs, Size(viewport.width.toFloat(), viewport.height.toFloat()))
        }
    }

    companion object {

        /** 平面档用的空场：画不出任何像素，被采样也无影响。 */
        internal val Disabled = GlassField(Color.Transparent, emptyList())
    }
}

/** 极光光斑：圆心与半径都按视口比例给，横竖屏与分屏下构图保持一致。 */
internal data class AuroraBlob(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val hue: Float,
    val saturation: Float,
    val lightness: Float,
    val alpha: Float,
)

/** 把氛围场铺在 [modifier] 给定的区域上，并把该区域的窗口位置与尺寸回报给场。 */
@Composable
fun AmbientField(field: GlassField, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .onSizeChanged { field.viewport = it }
            .onGloballyPositioned { field.paintedAt = it.positionInWindow() },
    ) {
        drawAurora(field.base, field.blobs, size)
    }
}

/**
 * 生成氛围场。[hue] 取主题强调色相，光斑之间拉开色相距离，做出 mesh gradient 的层次而非一团糊；
 * 深浅极性各有一套明度，保证压在玻璃上的文字始终落在可控亮度上。[intense] 为清透档（更浓）。
 */
@Composable
fun rememberGlassField(base: Color, hue: Float, dark: Boolean, intense: Boolean): GlassField {
    val blobs = remember(hue, dark, intense) { auroraBlobs(hue, dark, intense) }
    return remember(base, blobs) { GlassField(base, blobs) }
}

private fun auroraBlobs(hue: Float, dark: Boolean, intense: Boolean): List<AuroraBlob> {
    val saturation = if (dark) 0.62f else 0.52f
    val lightness = if (dark) 0.30f else 0.80f
    val alpha = (if (dark) 0.66f else 0.58f) * (if (intense) 1.25f else 1f)
    return listOf(
        AuroraBlob(0.16f, 0.06f, 0.92f, hue, saturation, lightness, alpha),
        AuroraBlob(0.94f, 0.20f, 0.72f, hue + 34f, saturation, lightness - 0.06f, alpha * 0.9f),
        AuroraBlob(0.34f, 0.66f, 0.88f, hue - 38f, saturation * 0.9f, lightness + 0.04f, alpha * 0.85f),
        AuroraBlob(0.86f, 0.92f, 0.80f, hue + 168f, saturation * 0.8f, lightness - 0.10f, alpha * 0.75f),
    )
}

/**
 * 极光本体：先铺页面底色（玻璃内部不能透穿到下层内容），再叠四枚径向渐变光斑。
 * 传入的 [viewport] 始终是极光「设计尺寸」，调用方负责把绘制坐标系对它。
 */
internal fun DrawScope.drawAurora(base: Color, blobs: List<AuroraBlob>, viewport: Size) {
    drawRect(base)
    if (blobs.isEmpty()) return
    val span = maxOf(viewport.width, viewport.height)
    blobs.forEach { blob ->
        val color = Color.hsl(
            hue = ((blob.hue % 360f) + 360f) % 360f,
            saturation = blob.saturation.coerceIn(0f, 1f),
            lightness = blob.lightness.coerceIn(0f, 1f),
            alpha = blob.alpha.coerceIn(0f, 1f),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(color, color.copy(alpha = 0f)),
                center = Offset(blob.centerX * viewport.width, blob.centerY * viewport.height),
                radius = blob.radius * span,
            ),
        )
    }
}
