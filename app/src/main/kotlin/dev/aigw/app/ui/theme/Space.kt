package dev.aigw.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 间距阶梯：4dp 基准，七档。
 *
 * 现状问题是「一个数当三种用途」——12dp 同时充当页边距、卡片间距与卡内间距，页面留白与组间留白
 * 在视觉上分不开，所以先有阶梯、再给语义别名，写页面时只准用 [Edge] 里的语义值。
 */
object Space {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 40.dp
}

/**
 * 语义间距：页面里真正该出现的那几个值。
 *
 * - [screen] 页面左右边距（内容与屏幕边缘的距离，也是所有卡片的左右外边）
 * - [groupGap] 同级卡片之间
 * - [cardPad] 卡片内边距
 * - [inline] 行内元素之间（图标与文字、标签与数值）
 * - [sectionGap] 大区块之间（页面级分组之间）
 * - [tight] 紧贴关系（标题与副标题、数值与单位）
 */
object Edge {
    val screen = Space.md
    val groupGap = Space.sm
    val cardPad = Space.md
    val inline = Space.xs
    val sectionGap = Space.lg
    val tight = Space.xxs
}

/**
 * 圆角阶梯。卡片 20dp 与 16dp 内边距同档，卡内嵌块 12dp，控件 12dp，胶囊走 [chip]。
 * 玻璃层的 shape 也取这里的值，避免「卡片 16、玻璃 14」这类看不出来但会错位的偏差。
 */
object Radius {
    val card = 20.dp
    val inner = 12.dp
    val control = 12.dp
    val chip = 999.dp
}

/** 控件尺寸。触控最小 48dp（比 Material 的 48 更保险，覆盖 miuix 自带内边距）。 */
object Size {
    val touchMin = 48.dp
    val button = 48.dp
    val chip = 32.dp
    val iconTile = 44.dp
    val hairline = 1.dp
    val barTrack = 6.dp
}
