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
import dev.aigw.core.pool.AccountStatus
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
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
    val scaffoldBg = MiuixTheme.colorScheme.background
    Scaffold(
        containerColor = scaffoldBg,
        topBar = {
            TopAppBar(
                title = title,
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
                .overScrollVertical()
                .scrollEndHaptic()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
            overscrollEffect = null,
        ) {
            item {
                Column {
                    Spacer(Modifier.height(12.dp))
                    // miuix 0.8.8 的 TopAppBar 没有 subtitle 参数，副标题由页面内容区首行展示
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 26.dp)
                                .padding(bottom = 4.dp),
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

/** miuix 标准分组容器：横向 12dp + 底部 12dp 外边距，内部 0 边距（Super* 行自带 16dp 内边距）。
 *  仅用于装 Super* 设置行；普通内容卡用 [SectionCard]。 */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .then(modifier),
        content = content,
    )
}

/** 组内行间分割线：与卡片内文字左对齐（16dp 缩进）。每行之间加，最后一行后不加。 */
@Composable
fun GroupDivider() {
    HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
}

/** 内容卡：Miuix 卡片 + 16dp 内边距 + 横向 12dp 外边距；[enterIndex] 非空时带错峰入场动效。
 *  自带底部间距，列表项无需再包 Box。 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    enterIndex: Int? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .then(if (enterIndex != null) Modifier.staggeredAppear(enterIndex) else Modifier)
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .then(modifier),
        insideMargin = PaddingValues(16.dp),
        content = content,
    )
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
        Text(label, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
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
    val content by animateColorAsState(
        targetValue = MiuixTheme.colorScheme.onSurface,
        animationSpec = tween(200),
        label = "chipContent",
    )
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(start = 12.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(6.dp), // 固定占位尺寸，光环不参与对齐计算，圆点与文字始终垂直居中
            contentAlignment = Alignment.Center,
        ) {
            if (running) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)))
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
            style = MiuixTheme.textStyles.footnote1,
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
            style = MiuixTheme.textStyles.footnote2,
            letterSpacing = 0.8.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.headline1,
            fontWeight = FontWeight.Light,
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
        targetValue = if (selected) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.surfaceContainer,
        animationSpec = tween(200),
        label = "choiceContainer",
    )
    val content = if (selected) MiuixTheme.colorScheme.surface else MiuixTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .pressScale()
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MiuixTheme.textStyles.footnote1, color = content)
    }
}

/** 可点击复制的地址条。 */
@Composable
fun AddressTile(value: String, onCopy: () -> Unit) {
    Text(
        text = value,
        style = MiuixTheme.textStyles.body2.copy(fontFamily = FontFamily.Monospace),
        color = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onCopy)
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        colors = ButtonDefaults.buttonColorsPrimary(),
    ) {
        Text(text, style = MiuixTheme.textStyles.main)
    }
}

@Composable
fun EmptyHint(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MiuixTheme.textStyles.headline2, color = MiuixTheme.colorScheme.onSurface)
        Text(message, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

/** 小节头：左小灰大写标题 + 右侧可选主色小链接（仅用于内容卡内部，分组标题一律用 SmallTitle）。 */
@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val accentVariant = MiuixTheme.colorScheme.primaryVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 与卡内正文基线对齐：小节头文字偏小，底部补 2dp 视觉对齐
            .padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(
                text = action,
                style = MiuixTheme.textStyles.footnote1,
                color = accentVariant,
                modifier = if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier,
            )
        }
    }
}

/**
 * 额度包进度条：一行「名称（分组）+ 过期时间」，下方细进度条 + 右侧百分比。
 */
@Composable
fun QuotaPackBar(pack: dev.aigw.core.provider.QuotaPack) {
    val accent = MiuixTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = if (pack.group.isEmpty()) pack.name else "${pack.name}（${pack.group}）",
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (pack.expireAt > 0) java.text.DateFormat.getDateTimeInstance(
                    java.text.DateFormat.SHORT,
                    java.text.DateFormat.SHORT,
                ).format(java.util.Date(pack.expireAt * 1000)) else "",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val fraction = if (pack.limit > 0) {
                (pack.remain.toFloat() / pack.limit).coerceIn(0f, 1f)
            } else {
                0f
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.surfaceContainerHighest),
            ) {
                val barFraction by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = tween(Motion.BAR_FILL_MILLIS, easing = FastOutSlowInEasing),
                    label = "quotaBar",
                )
                Box(
                    Modifier
                        .fillMaxWidth(barFraction)
                        .height(6.dp)
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
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
