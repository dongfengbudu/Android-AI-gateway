package dev.aigw.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aigw.app.ui.glass.glassBar
import dev.aigw.app.ui.glass.glassCard
import dev.aigw.app.ui.glass.glassEnabled
import dev.aigw.app.ui.glass.glassWell
import dev.aigw.app.ui.theme.AppType
import dev.aigw.app.ui.theme.Edge
import dev.aigw.app.ui.theme.LocalOverlays
import dev.aigw.app.ui.theme.Radius
import dev.aigw.app.ui.theme.Size
import dev.aigw.app.ui.theme.Space
import dev.aigw.app.ui.theme.WarnAmber
import dev.aigw.core.pool.AccountStatus
import dev.aigw.core.usage.LogLevel
import dev.aigw.core.usage.OperationLog
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 账号状态文案：只有凭证失效与手动停用两种异常态，正常即「可用」。 */
fun accountStatusText(account: AccountStatus): String = when {
    account.disabled -> "凭证失效，需重新登录"
    !account.enabled -> "已停用"
    else -> "可用"
}

/**
 * 统一页面骨架：折叠大标题（含副标题）+ 可滚动内容区。
 * [leading] 为二级页返回按钮；[content] 在 LazyColumn 上展开，各分组自包 item。
 */
@Composable
fun PageScaffold(
    title: String,
    subtitle: String,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    // 配色方案覆盖页面底色时，骨架与顶栏必须同步跟随，否则覆盖只改了
    // background 却被 Scaffold/TopAppBar 的默认 surface 色整页盖住（视觉上「无效」）。
    // 玻璃档：页面底色转透明（底色由最外层氛围场提供），顶栏材质改由玻璃画。
    val glass = glassEnabled()
    // 玻璃顶栏要采样滚动内容：内容层录进 barBackdrop，顶栏再按自己的屏幕位置取对应切片。
    val barBackdrop = rememberLayerBackdrop()
    val scaffoldBg = if (glass) Color.Transparent else MiuixTheme.colorScheme.background
    Scaffold(
        containerColor = scaffoldBg,
        topBar = {
            TopAppBar(
                title = title,
                modifier = if (glass) Modifier.glassBar(barBackdrop) else Modifier,
                color = scaffoldBg,
                largeTitle = title,
                navigationIcon = { leading?.invoke() },
                actions = actions,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(if (glass) Modifier.layerBackdrop(barBackdrop) else Modifier)
                .overScrollVertical()
                .scrollEndHaptic()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
            overscrollEffect = null,
        ) {
            item {
                Column {
                    Spacer(Modifier.height(Space.xs))
                    // miuix 0.8.8 的 TopAppBar 没有 subtitle 参数，副标题由页面内容区首行展示
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = AppType.Meta,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Edge.screen)
                                .padding(bottom = Space.xxs),
                        )
                    }
                }
            }
            content()
            item { Spacer(Modifier.height(paddingValues.calculateBottomPadding())) }
        }
    }
}

/** 二级页面的返回按钮。 */
@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(MiuixIcons.Back, contentDescription = "返回")
    }
}

/** 分组容器：横向 Edge.screen + 底部 Edge.groupGap 外边距，内部 0 边距（Super* 行自带自己的内边距）。
 *  仅用于装 Super* 设置行；普通内容卡用 [SectionCard]。 */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Edge.screen)
            .padding(bottom = Edge.groupGap)
            .then(if (glassEnabled()) Modifier.glassCard(Radius.card) else Modifier)
            .then(modifier),
        colors = glassCardColors(),
        content = content,
    )
}

/** 组内行间分割线：与卡片内文字左对齐（Edge.cardPad 缩进）。每行之间加，最后一行后不加。 */
@Composable
fun GroupDivider() {
    HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = Edge.cardPad))
}

/** 内容卡：Edge.cardPad 内边距 + Edge.screen 横向外边距 + Edge.groupGap 底部间距；
 *  [enterIndex] 非空时带错峰入场动效。自带底部间距，列表项无需再包 Box。 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    enterIndex: Int? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .then(if (enterIndex != null) Modifier.staggeredAppear(enterIndex) else Modifier)
            .padding(horizontal = Edge.screen)
            .padding(bottom = Edge.groupGap)
            .then(if (glassEnabled()) Modifier.glassCard(Radius.card) else Modifier)
            .then(modifier),
        insideMargin = PaddingValues(Edge.cardPad),
        colors = glassCardColors(),
        content = content,
    )
}

/**
 * 卡片底色：平面档走 miuix 默认实心底，玻璃档换成透底——材质由 [glassCard] 在卡片背面画，
 * 实心底叠在下面会把玻璃糊住。contentColor 保持默认，卡内文字取色不变。
 */
@Composable
private fun glassCardColors(): CardColors =
    if (glassEnabled()) {
        CardDefaults.defaultColors(color = Color.Transparent, contentColor = MiuixTheme.colorScheme.onSurfaceContainer)
    } else {
        CardDefaults.defaultColors()
    }

/**
 * 弹窗底部确认按钮行：左侧取消、右侧确认（主色）。供各 WindowDialog/WindowBottomSheet 复用。
 */
@Composable
fun DialogButtonRow(
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    cancelText: String = "取消",
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        TextButton(text = cancelText, onClick = onCancel, modifier = Modifier.weight(1f))
        TextButton(
            text = confirmText,
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}

/**
 * 账号信息行：左标签右值，同行对齐。供各供应商的 AccountExtra / AccountBlock 使用，
 * 避免「标签一行、值一行」的松散排布。
 */
@Composable
fun AccountInfoRow(
    label: String,
    value: String,
    valueColor: Color = MiuixTheme.colorScheme.primary,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppType.Label, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(
            text = value,
            style = AppType.Label,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

/** 「运行中 / 已停止」状态胶囊：圆点 + 文案（运行中主色带浅光环）。 */
@Composable
fun StatusChip(running: Boolean) {
    val accent = MiuixTheme.colorScheme.primary
    val overlays = LocalOverlays.current
    val content by animateColorAsState(
        targetValue = MiuixTheme.colorScheme.onSurface,
        animationSpec = tween(200),
        label = "chipContent",
    )
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .then(
                if (glassEnabled()) Modifier.glassWell(Radius.chip) // 胶囊形，但取凹槽这一档的弱折射
                else Modifier.background(overlays.track)
            )
            .padding(start = Space.sm, end = Space.md, top = Space.xs, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Box(
            modifier = Modifier.size(6.dp), // 固定占位尺寸，光环不参与对齐计算，圆点与文字始终垂直居中
            contentAlignment = Alignment.Center,
        ) {
            if (running) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(overlays.accentGlow))
            }
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (running) accent else MiuixTheme.colorScheme.onSurfaceVariantSummary),
            )
        }
        Text(
            text = if (running) "运行中" else "已停止",
            style = AppType.Label,
            color = content,
        )
    }
}

/** 统计单元：小灰标签在上、大号细体数字在下；[indicatorColor] 作数字颜色。 */
@Composable
fun StatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = true,
    indicatorColor: Color = Color.Unspecified,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = AppType.Meta.copy(letterSpacing = 0.8.sp),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
        )
        Text(
            text = value,
            style = AppType.Number,
            color = if (indicatorColor != Color.Unspecified) indicatorColor else MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * 分段/筛选胶囊。选中=深底浅字，未选中=浅底描边。
 *
 * [modifier] 供调用方传入 `Modifier.weight(1f)` 做等宽铺满；内容居中，
 * 胶囊比文字宽时文字不会贴左。
 */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (selected) MiuixTheme.colorScheme.onSurface else LocalOverlays.current.track,
        animationSpec = tween(200),
        label = "choiceContainer",
    )
    val glass = glassEnabled()
    val content = if (selected) MiuixTheme.colorScheme.surface else MiuixTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .pressScale()
            .clip(CircleShape)
            // 玻璃档只给未选中态上玻璃：选中态保持实心底，靠「实心 / 玻璃」区分选中
            .then(if (!glass || selected) Modifier.background(container) else Modifier.glassWell(Radius.chip))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppType.Label, color = content)
    }
}

/** 可点击复制的地址条。 */
@Composable
fun AddressTile(value: String, onCopy: () -> Unit) {
    Text(
        text = value,
        style = AppType.Label.copy(fontFamily = FontFamily.Monospace),
        color = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.inner))
            .then(
                if (glassEnabled()) Modifier.glassWell(Radius.inner)
                else Modifier.background(MiuixTheme.colorScheme.surfaceVariant)
            )
            .clickable(onClick = onCopy)
            .padding(horizontal = Space.md, vertical = Space.sm),
    )
}

/** 全宽主操作按钮：Miuix Button 主色（跟随主题金）。onClick 放最后以支持尾随 lambda。 */
@Composable
fun OutlineActionButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(Size.button),
        enabled = enabled,
        colors = ButtonDefaults.buttonColorsPrimary(),
    ) {
        Text(text, style = AppType.Label)
    }
}

@Composable
fun EmptyHint(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = Space.lg), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(title, style = AppType.Title, color = MiuixTheme.colorScheme.onSurface)
        Text(message, style = AppType.Body, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

/** 小节头：左小灰大写标题 + 右侧可选主色小链接（仅用于内容卡内部，页面级分组标题用 GroupTitle）。 */
@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val accentVariant = MiuixTheme.colorScheme.primaryVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 与卡内正文基线对齐：小节头文字偏小，底部补 2dp 视觉对齐
            .padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text(
            text = title,
            style = AppType.Meta.copy(letterSpacing = 1.0.sp),
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(
                text = action,
                style = AppType.Meta,
                color = accentVariant,
                modifier = if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier,
            )
        }
    }
}

/**
 * 分组标题：卡片之外的页面级分组名。
 *
 * 与卡内的 [SectionHeader] 是两个角色，视觉上必须一眼分开：这里是 17sp 半粗、高对比、上下留白更大；
 * 卡内小节是 13sp 灰字带字距。此前两者都是 13/14sp 灰字，页面层级因此糊成一片。
 */
@Composable
fun GroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AppType.SectionTitle,
        color = MiuixTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Edge.screen)
            .padding(top = Edge.sectionGap, bottom = Space.xs),
    )
}

/**
 * 额度包进度条：一行「名称（分组）+ 过期时间」，下方细进度条 + 右侧百分比。
 */
@Composable
fun QuotaPackBar(pack: dev.aigw.core.provider.QuotaPack) {
    val accent = MiuixTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = if (pack.group.isEmpty()) pack.name else "${pack.name}（${pack.group}）",
                style = AppType.Label,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (pack.expireAt > 0) java.text.DateFormat.getDateTimeInstance(
                    java.text.DateFormat.SHORT,
                    java.text.DateFormat.SHORT,
                ).format(java.util.Date(pack.expireAt * 1000)) else "",
                style = AppType.Micro,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            val fraction = if (pack.limit > 0) {
                (pack.remain.toFloat() / pack.limit).coerceIn(0f, 1f)
            } else {
                0f
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(Size.barTrack)
                    .clip(CircleShape)
                    .background(LocalOverlays.current.track),
            ) {
                val barFraction by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = tween(Motion.BAR_FILL_MILLIS, easing = FastOutSlowInEasing),
                    label = "quotaBar",
                )
                Box(
                    Modifier
                        .fillMaxWidth(barFraction)
                        .height(Size.barTrack)
                        .clip(CircleShape)
                        .background(
                            when {
                                fraction > 0.5f -> MiuixTheme.colorScheme.onSurface
                                fraction > 0.2f -> accent
                                else -> MiuixTheme.colorScheme.error
                            },
                        ),
                )
            }
            Text(
                text = if (pack.limit > 0) {
                    "${(fraction * 100).toInt()}% (${pack.remain}/${pack.limit})"
                } else {
                    "—"
                },
                style = AppType.Micro,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/** 日志类别的中文名；未内置的类别（新功能自带）原样显示。 */
internal fun categoryLabel(category: String): String = when (category) {
    KEY_CALL -> "调用"
    OperationLog.CATEGORY_REQUEST -> "请求"
    OperationLog.CATEGORY_LOGIN -> "登录"
    OperationLog.CATEGORY_TASK -> "任务"
    else -> category
}

/** 调用记录的伪类别：与 OperationEntry.category 同域，界面里统一当一类展示。 */
internal const val KEY_CALL = "call"

/** 级别语义色：错误红随主题，警示橙/信息灰是固定语义色（Miuix 无 warning token）。 */
@Composable
internal fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.WARN -> WarnAmber
    LogLevel.ERROR -> MiuixTheme.colorScheme.error
    LogLevel.INFO -> MiuixTheme.colorScheme.onSurfaceVariantSummary
}
