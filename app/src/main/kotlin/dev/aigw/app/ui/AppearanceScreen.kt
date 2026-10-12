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
import dev.aigw.app.ui.theme.defaultThemeAccent
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
    BgPreset("默认", null),
    BgPreset("冷银灰", "#F7F7F8"),
    BgPreset("暖象牙", "#FBF9F5"),
    BgPreset("曜石黑", "#121214"),
    BgPreset("深空黑", "#0A0A0B"),
)

/** 色相环尺寸参数（统一在此定义，保证拖动命中与绘制一致）。 */
private val WheelSize = 120.dp
private val WheelStroke = 12.dp
private val WheelThumb = 10.dp

/** 外观主题二级页：明暗模式、页面底色、强调色相、动态取色。 */
@Composable
fun AppearanceThemeScreen(state: AppUiState, viewModel: AppViewModel, onBack: () -> Unit) {
    PageScaffold(
        title = "外观主题",
        subtitle = "明暗模式 · 强调色 · 底色",
        leading = { BackButton(onBack) },
    ) {
        item { SmallTitle("明暗模式") }
        item {
            SectionCard(enterIndex = 0) {
                ModeRow("自动 · 跟随系统", "跟随 Android 系统的深浅色设置自适应切换", state.themeMode == ThemeMode.System) {
                    viewModel.setThemeMode(ThemeMode.System)
                }
                HorizontalDivider(modifier = Modifier.fillMaxWidth())
                ModeRow("深色模式", "同套调性色阶的暗色展开，适合弱光与长时夜间使用", state.themeMode == ThemeMode.Dark) {
                    viewModel.setThemeMode(ThemeMode.Dark)
                }
                HorizontalDivider(modifier = Modifier.fillMaxWidth())
                ModeRow("浅色模式", "极简明亮排版，主色与点缀保持同一色相的调性层次", state.themeMode == ThemeMode.Light) {
                    viewModel.setThemeMode(ThemeMode.Light)
                }
            }
        }

        item { SmallTitle("页面底色") }
        item {
            SectionCard(enterIndex = 1) {
                Text(
                    text = "切换后仅替换页面底色（卡片/表面色不变）：",
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
                            dotColor = preset.hex?.let { parseHexColor(it) } ?: MiuixTheme.colorScheme.primary,
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

        item { SmallTitle("强调色") }
        item {
            SectionCard(enterIndex = 2) {
                Text(
                    text = "拖动色相环选择强调色，主色、按钮、点缀色都从同一色相按调性阶梯自动展开：",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                val accentSeed = Color.hsv(state.accentHue, 0.62f, 0.95f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HueWheel(
                            color = accentSeed,
                            onPick = { picked -> viewModel.setAccentHue(hueOf(picked).toFloat()) },
                        )
                        Text(
                            text = "色相 ${state.accentHue.toInt()}°",
                            style = MiuixTheme.textStyles.footnote1.copy(fontFamily = FontFamily.Monospace),
                            color = if (state.accentHue == defaultThemeAccent.normalizedHue) {
                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                            } else {
                                MiuixTheme.colorScheme.primary
                            },
                        )
                    }
                    AccentPreview(accentSeed, Modifier.weight(1f))
                }
            }
        }

        item { SmallTitle("动态取色") }
        item {
            SectionCard(enterIndex = 3) {
                SuperSwitch(
                    title = "动态取色",
                    summary = "从系统壁纸提取配色，开启后强调色不再生效",
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

/** 单选行：miuix 标准单选（选中态由组件给出主题高亮）。 */
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

/** 底色预设胶囊：色点 + 名称；选中时主色描边 + 浅主色底。 */
@Composable
private fun PresetChip(label: String, dotColor: Color, selected: Boolean, onClick: () -> Unit) {
    val primary = MiuixTheme.colorScheme.primary
    val primaryVariant = MiuixTheme.colorScheme.primaryVariant
    Row(
        modifier = Modifier
            .pressScale()
            .clip(CircleShape)
            .background(if (selected) primary.copy(alpha = 0.12f) else MiuixTheme.colorScheme.surfaceVariant)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) primary else MiuixTheme.colorScheme.outline, CircleShape)
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
            color = if (selected) primaryVariant else MiuixTheme.colorScheme.onSurface,
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
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onClear).padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
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

/** 强调色实测：色块 + 浅底/深底上的文字样张，直观检验两种模式下的可读性。 */
@Composable
private fun AccentPreview(accent: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)  // miuix-lint:allow 主题/品牌色定义（预览用固定浅底）
                    .border(1.dp, MiuixTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("浅色底", style = MiuixTheme.textStyles.footnote1, color = accent)
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141414))  // miuix-lint:allow 主题/品牌色定义（预览用固定深底）
                    .border(1.dp, MiuixTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("深色底", style = MiuixTheme.textStyles.footnote1, color = accent)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Text(
            text = formatHex(accent),
            style = MiuixTheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
