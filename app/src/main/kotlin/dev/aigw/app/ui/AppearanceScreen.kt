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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.aigw.app.ui.theme.BackgroundTone
import dev.aigw.app.ui.theme.ThemeAccent
import dev.aigw.app.ui.theme.ThemeMode
import dev.aigw.app.ui.theme.defaultThemeAccent
import dev.aigw.app.ui.theme.isDarkTheme
import dev.aigw.app.ui.theme.themeBackgroundColor
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperRadioButton
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** 色相环尺寸参数（统一在此定义，保证拖动命中与绘制一致）。 */
private val WheelSize = 120.dp
private val WheelStroke = 12.dp
private val WheelThumb = 10.dp

/** 外观主题二级页：明暗模式、页面底色、强调色相、动态取色。 */
@Composable
fun AppearanceThemeScreen(state: AppUiState, viewModel: AppViewModel, onBack: () -> Unit) {
    PageScaffold(
        title = "外观主题",
        subtitle = "明暗模式 · 底色 · 强调色",
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
                    text = "底色与强调色同色相展开，四档只改染色浓度；深浅由明暗模式自动配对：",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                val accent = ThemeAccent(state.accentHue)
                val dark = isDarkTheme(state.themeMode)
                val toneColors = remember(accent, dark) {
                    BackgroundTone.entries.associateWith { themeBackgroundColor(accent, dark, it) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BackgroundTone.entries.forEach { tone ->
                        PresetChip(
                            label = tone.label,
                            dotColor = toneColors.getValue(tone),
                            selected = state.backgroundTone == tone,
                            onClick = { viewModel.setBackgroundTone(tone) },
                        )
                    }
                }
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HueWheel(
                        color = accentSeed,
                        onPick = { picked -> viewModel.setAccentHue(hueOf(picked).toFloat()) },
                    )
                    Text(
                        text = if (state.accentHue == defaultThemeAccent.normalizedHue) {
                            "色相 ${state.accentHue.toInt()}° · 默认"
                        } else {
                            "色相 ${state.accentHue.toInt()}°"
                        },
                        style = MiuixTheme.textStyles.footnote1.copy(fontFamily = FontFamily.Monospace),
                        color = if (state.accentHue == defaultThemeAccent.normalizedHue) {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        } else {
                            MiuixTheme.colorScheme.primary
                        },
                    )
                }
            }
        }

        item { SmallTitle("动态取色") }
        item {
            SectionCard(enterIndex = 3) {
                SuperSwitch(
                    title = "动态取色",
                    summary = "由系统按壁纸取色生成整套配色（Android 12+），强调色作为取色种子参与生成",
                    checked = state.dynamicColor,
                    onCheckedChange = { viewModel.setDynamicColor(it) },
                    insideMargin = PaddingValues(0.dp),
                )
            }
        }

        item { SmallTitle("效果预览") }
        item {
            SectionCard(enterIndex = 4) {
                PagePreview()
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

/** 底色档位胶囊：色点 + 名称；选中时主色描边 + 浅主色底。 */
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
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(dotColor)
                .border(1.dp, MiuixTheme.colorScheme.outline, CircleShape),
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) primaryVariant else MiuixTheme.colorScheme.onSurface,
        )
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

/** 整页样张：底色 + 卡片 + 强调色按钮的组合效果，用于直接判断好不好看。 */
@Composable
private fun PagePreview() {
    val cs = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cs.background)
            .border(1.dp, cs.outline, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "页面标题",
                    style = MiuixTheme.textStyles.main,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onBackground,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(cs.primary)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text("操作", style = MiuixTheme.textStyles.footnote2, color = cs.onPrimary)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(cs.surfaceContainerHighest)
                    .padding(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "卡片标题",
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurfaceContainerHighest,
                    )
                    Text(
                        text = "卡片上的次要说明文字",
                        style = MiuixTheme.textStyles.footnote2,
                        color = cs.onSurfaceVariantSummary,
                    )
                }
            }
        }
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
