package dev.aigw.app.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aigw.app.ui.theme.LocalAppColors
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperRadioButton
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import dev.aigw.app.ui.theme.ThemeMode
import dev.aigw.app.ui.theme.formatHex
import dev.aigw.app.ui.theme.parseHexColor
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** 页面底色预设：hex 为 null 表示不覆盖（用默认底色）。 */
private data class BgPreset(val label: String, val hex: String?)

private val bgPresets = listOf(
    BgPreset("默认金", null),
    BgPreset("冷银灰", "#F7F7F8"),
    BgPreset("暖象牙", "#FBF9F5"),
    BgPreset("曜石黑", "#121214"),
    BgPreset("深空黑", "#0A0A0B"),
)

/** 色相环尺寸参数（统一在此定义，保证拖动命中与绘制一致）。 */
private val WheelSize = 120.dp
private val WheelStroke = 12.dp
private val WheelThumb = 10.dp

/** 外观主题二级页：明暗模式、配色方案、金色文字、动态取色。 */
@Composable
fun AppearanceThemeScreen(state: AppUiState, viewModel: AppViewModel, onBack: () -> Unit) {
    val gold = LocalAppColors.current.goldStart

    PageScaffold(
        title = "外观主题",
        subtitle = "明暗模式 · 配色方案 · 金色文字",
        leading = { BackButton(onBack) },
    ) {
        item { SmallTitle("明暗模式") }
        item {
            SectionCard(enterIndex = 0) {
                ModeRow("自动 · 跟随系统", "跟随 Android 系统的深浅色设置自适应切换", state.themeMode == ThemeMode.System) {
                    viewModel.setThemeMode(ThemeMode.System)
                }
                HorizontalDivider(modifier = Modifier.fillMaxWidth())
                ModeRow("深色模式", "经典黑金暗色调，适合弱光与长时夜间使用", state.themeMode == ThemeMode.Dark) {
                    viewModel.setThemeMode(ThemeMode.Dark)
                }
                HorizontalDivider(modifier = Modifier.fillMaxWidth())
                ModeRow("浅色模式", "极简明亮排版，高对比度呈现黑金理性层次", state.themeMode == ThemeMode.Light) {
                    viewModel.setThemeMode(ThemeMode.Light)
                }
            }
        }

        item { SmallTitle("配色方案") }
        item {
            SectionCard(enterIndex = 1) {
                Text(
                    text = "预设黑金系调色基调，切换后仅替换页面底色（卡片/表面色不变）：",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val currentHex = state.backgroundOverride?.let { formatHex(it) }
                    bgPresets.forEach { preset ->
                        PresetChip(
                            label = preset.label,
                            dotColor = preset.hex?.let { parseHexColor(it) } ?: gold,
                            selected = currentHex == preset.hex,
                            onClick = { viewModel.setBackgroundOverride(preset.hex) },
                        )
                    }
                }
                HexField(
                    value = state.backgroundOverride?.let { formatHex(it) } ?: "",
                    label = "自定义页面底色",
                    onCommit = { viewModel.setBackgroundOverride(it) },
                    onClear = if (state.backgroundOverride != null) {
                        { viewModel.setBackgroundOverride(null) }
                    } else {
                        null
                    },
                    swatch = state.backgroundOverride,
                )
            }
        }

        item { SmallTitle("金色文字") }
        item {
            SectionCard(enterIndex = 2) {
                Text(
                    text = "应用主色渐变由起始色与结束色插值生成，可拖动色相环或直接输入代码：",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WheelColumn(
                        title = "起始色",
                        color = state.goldStart,
                        modifier = Modifier.weight(1f),
                        onPick = { viewModel.setGoldColors(formatHex(it), formatHex(state.goldEnd)) },
                    )
                    WheelColumn(
                        title = "结束色",
                        color = state.goldEnd,
                        modifier = Modifier.weight(1f),
                        onPick = { viewModel.setGoldColors(formatHex(state.goldStart), formatHex(it)) },
                    )
                }
                GradientPreview(state.goldStart, state.goldEnd)
            }
        }

        item { SmallTitle("动态取色") }
        item {
            SectionCard(enterIndex = 3) {
                SuperSwitch(
                    title = "动态取色",
                    summary = "从系统壁纸提取配色，开启后配色方案不再生效",
                    checked = state.dynamicColor,
                    onCheckedChange = { viewModel.setDynamicColor(it) },
                    insideMargin = PaddingValues(0.dp),
                )
            }
        }

        item {
            OutlineActionButton("重置默认") { viewModel.resetAppearance() }
        }
    }
}

/** 单选行：miuix 标准单选（选中态由组件给出金色高亮）。 */
@Composable
private fun ModeRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    SuperRadioButton(
        title = title,
        summary = subtitle,
        selected = selected,
        onClick = onClick,
        insideMargin = PaddingValues(vertical = 12.dp),
    )
}

/** 配色预设胶囊：色点 + 名称；选中时金色描边 + 浅金底。 */
@Composable
private fun PresetChip(label: String, dotColor: Color, selected: Boolean, onClick: () -> Unit) {
    val gold = LocalAppColors.current.goldStart
    val goldText = LocalAppColors.current.goldText
    Row(
        modifier = Modifier
            .pressScale()
            .clip(CircleShape)
            .background(if (selected) gold.copy(alpha = 0.12f) else MiuixTheme.colorScheme.surfaceVariant)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) gold else MiuixTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(dotColor))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) goldText else MiuixTheme.colorScheme.onSurface,
        )
    }
}

/** 等宽 HEX 输入框：满 6 位合法即提交；可选色彩示意块与「清除」入口。 */
@Composable
private fun HexField(
    value: String,
    label: String,
    onCommit: (String) -> Unit,
    onClear: (() -> Unit)? = null,
    swatch: Color? = null,
) {
    var text by remember(value) { mutableStateOf(value.removePrefix("#").uppercase()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextField(
            value = text,
            onValueChange = { input ->
                val cleaned = input.filter { it.isLetterOrDigit() }.take(6).uppercase()
                text = cleaned
                if (cleaned.length == 6 && parseHexColor(cleaned) != null) onCommit("#$cleaned")
            },
            label = label,
            leadingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (swatch != null) {
                        Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(swatch))
                    }
                    Text(
                        text = "#",
                        style = MiuixTheme.textStyles.main,
                        fontFamily = FontFamily.Monospace,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            },
            singleLine = true,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium),
            modifier = Modifier.fillMaxWidth(),
        )
        if (onClear != null) {
            Text(
                text = "清除覆盖",
                style = MiuixTheme.textStyles.footnote2,
                color = LocalAppColors.current.goldText,
                modifier = Modifier.clickable(onClick = onClear).padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

/** 单个色相环卡片：标题 + 色相环 + HEX 输入框。 */
@Composable
private fun WheelColumn(
    title: String,
    color: Color,
    modifier: Modifier,
    onPick: (Color) -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(6.dp)).background(color))
            Text(
                text = title,
                style = MiuixTheme.textStyles.footnote2,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${hueOf(color)}°",
                style = MiuixTheme.textStyles.footnote2,
                color = LocalAppColors.current.goldText,
            )
        }
        HueWheel(color = color, onPick = onPick)
        HexField(value = formatHex(color), label = "HEX", onCommit = { onPick(requireColor(it)) })
    }
}

/** 色相环：角度映射色相 H、半径比例映射饱和度 S，明度固定 1；可拖动或点击取色。 */
@Composable
private fun HueWheel(color: Color, onPick: (Color) -> Unit) {
    val (hue, saturation) = hueSatOf(color)
    val hueColors = remember { List(13) { Color.hsv(it * 30f, 1f, 1f) } }
    // 滑块用主题语义色：填充取 onPrimary，描边取 onSurface（替代硬编码黑/白）
    val thumbFill = MiuixTheme.colorScheme.onPrimary
    val thumbStroke = MiuixTheme.colorScheme.onSurface
    Canvas(
        modifier = Modifier
            .size(WheelSize)
            .pointerInput(Unit) {
                detectTapGestures { offset -> onPick(colorAt(offset, size.width, size.height, density)) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onPick(colorAt(change.position, size.width, size.height, density))
                }
            },
    ) {
        val stroke = WheelStroke.toPx()
        val thumbR = WheelThumb.toPx()
        val ringR = (minOf(size.width, size.height) / 2f - thumbR - stroke / 2f).coerceAtLeast(1f)
        val center = Offset(size.width / 2f, size.height / 2f)
        drawArc(
            brush = Brush.sweepGradient(hueColors),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(center.x - ringR, center.y - ringR),
            size = Size(ringR * 2f, ringR * 2f),
            style = Stroke(width = stroke),
        )
        val angleRad = Math.toRadians(hue.toDouble())
        val dist = (saturation.coerceIn(0f, 1f)) * ringR
        val thumb = Offset(
            center.x + (dist * cos(angleRad)).toFloat(),
            center.y + (dist * sin(angleRad)).toFloat(),
        )
        drawCircle(thumbFill, thumbR, thumb)
        drawCircle(thumbStroke, thumbR, thumb, style = Stroke(width = 2.dp.toPx()))
        drawCircle(color, thumbR * 0.45f, thumb)
    }
}

/** START→END 渐变预览条 + 两端代码标注 + 渐变文字实测。 */
@Composable
private fun GradientPreview(start: Color, end: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(start, end))),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = formatHex(start),
                style = MiuixTheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace),
                color = start,
            )
            Text(
                text = formatHex(end),
                style = MiuixTheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace),
                color = end,
            )
        }
        Text(
            text = "黑金渐变文字实测",
            style = MiuixTheme.textStyles.headline1.copy(
                brush = Brush.horizontalGradient(listOf(start, end)),
                fontWeight = FontWeight.Light,
                letterSpacing = 1.sp,
            ),
        )
    }
}

/** 触点 → 颜色：角度映射 H，半径比例映射 S，V 固定 1。[density] 为像素密度（同 PointerInputScope.density）。 */
private fun colorAt(offset: Offset, width: Int, height: Int, density: Float): Color {
    val cx = width / 2f
    val cy = height / 2f
    val dx = offset.x - cx
    val dy = offset.y - cy
    val thumbR = WheelThumb.value * density
    val stroke = WheelStroke.value * density
    val ringR = (minOf(width, height) / 2f - thumbR - stroke / 2f).coerceAtLeast(1f)
    val angle = ((Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()) + 360f) % 360f
    val saturation = (hypot(dx, dy) / ringR).coerceIn(0f, 1f)
    return Color.hsv(angle, saturation, 1f)
}

/** 取颜色的 HSV 色相与饱和度（0..360 / 0..1）。 */
private fun hueSatOf(color: Color): Pair<Float, Float> {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(color.toArgb(), hsv)
    return hsv[0] to hsv[1]
}

private fun hueOf(color: Color): Int = hueSatOf(color).first.toInt()

/** 已校验过的 HEX → 颜色；由 [parseHexColor] 保证非空。 */
private fun requireColor(hex: String): Color = parseHexColor(hex) ?: Color.Unspecified
