package dev.aigw.app.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.aigw.core.provider.RoutedModel
import dev.aigw.app.ui.theme.SuccessGreen
import dev.aigw.app.ui.theme.WarnAmber
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 模型页：汇总所有供应商的模型（id 带 `provider/` 前缀），按供应商分组展示与流式更新。 */
@Composable
fun ModelsScreen(state: AppUiState, viewModel: AppViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // 按 routePrefix 分组：区域型供应商（如 WorkBuddy 国内与国外）各成一个分组。
    // 用 derivedStateOf 而不是 remember(key)：模型列表在流式加载期间会被不断替换，
    // remember(key) 每次都是新引用、必然重算；derivedStateOf 只在真正读到的值变化时才失效，
    // 且能避免与之无关的重组（如折叠状态变化）触发分组重算。
    val grouped by remember {
        derivedStateOf {
            state.models.groupBy { it.routePrefix }
        }
    }
    // 折叠状态：记「被展开」的分组（默认全部折叠，点分组头才展开）。用 rememberSaveable 让旋转屏幕后保持。
    val expanded = rememberSaveable(
        saver = androidx.compose.runtime.saveable.listSaver(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        ),
    ) { mutableStateListOf<String>() }

    // 刷新按钮旋转动效：仅在加载中才创建无限过渡，停止后不空转每帧插值
    val spinAngle = if (state.modelsLoading) {
        val transition = rememberInfiniteTransition(label = "refreshSpin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "spinAngle",
        )
        angle
    } else {
        0f
    }
    val iconRotation by animateFloatAsState(
        targetValue = spinAngle,
        label = "refreshIconRotation",
    )

    // 动态副标题：流式加载中展示进度
    val totalProviders = state.modelLoadStates.size
    val doneProviders = state.modelLoadStates.values.count { it !is ModelLoadState.Loading }
    val subtitleText = if (state.modelsLoading) {
        if (totalProviders > 0) "共 ${state.models.size} 个 · 载入中 ($doneProviders/$totalProviders)" else "共 ${state.models.size} 个 · 更新中..."
    } else {
        "共 ${state.models.size} 个"
    }

    // 还在加载中的供应商列表
    val pendingProviders = state.modelLoadStates.filter { it.value is ModelLoadState.Loading }.keys

    PageScaffold(
        title = "模型",
        subtitle = subtitleText,
        actions = {
            // 「只看可用模型」：隐藏上游的非对话内部条目（如 Trae 的内部 config）
            val only = state.settings.onlyUsableModels
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (only) "只看可用" else "全部模型",
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (only) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            viewModel.updateSettings(
                                state.settings.copy(onlyUsableModels = !only),
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Icon(
                    MiuixIcons.Refresh,
                    contentDescription = "刷新",
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier
                        .bouncyClick { viewModel.refreshModels() }
                        .padding(6.dp)
                        .graphicsLayer { rotationZ = iconRotation },
                )
            }
        },
    ) {
        // 顶部细条流式进度指示
        item(key = "progress") {
            AnimatedVisibility(
                visible = state.modelsLoading,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = MiuixTheme.colorScheme.primary,
                        backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ),
                    height = 3.dp,
                )
            }
        }

        if (state.modelsError.isNotEmpty()) {
            item(key = "error") {
                SectionCard(enterIndex = 0) {
                    Text(
                        text = "拉取模型目录时有报错：${state.modelsError}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
            }
        }

        if (grouped.isEmpty()) {
            if (state.modelsLoading) {
                // 首屏流式载入中骨架屏
                val loadingList = pendingProviders.toList().ifEmpty { listOf("loading_1", "loading_2") }
                items(loadingList, key = { "skeleton-$it" }) { id ->
                    val name = state.providerOf(id)?.displayName ?: "正在拉取模型目录"
                    ModelSkeletonCard(providerName = name)
                }
            } else {
                item(key = "empty") {
                    SectionCard(enterIndex = 1) {
                        EmptyHint("还没有模型", "点右上角刷新；无可用账号的供应商可能不展示动态模型。")
                    }
                }
            }
        } else {
            // 已加载的模型按组展示。
            //
            // 性能注意：折叠状态不要在这里读——状态一变整页都会重组，
            // 模型多的时候点一下要卡好几帧。这里把「这一组是否折叠」的判断
            // 下沉到 [ModelGroupSection] 内部，由它自己订阅状态，
            // 这样点击只重组那一个分组。
            for ((routePrefix, models) in grouped) {
                modelGroupSection(
                    routePrefix = routePrefix,
                    models = models,
                    expanded = expanded,
                    testLatencies = state.modelTestLatencies,
                    onCopy = { id ->
                        clipboard.setText(AnnotatedString(id))
                        Toast.makeText(context, "已复制模型名称：$id", Toast.LENGTH_SHORT).show()
                    },
                    onTest = { viewModel.testModel(it) },
                )
            }

            // 若已有部分模型展示，而剩余供应商仍在加载中，在下方继续展示该供应商的骨架占位
            if (state.modelsLoading && pendingProviders.isNotEmpty()) {
                items(pendingProviders.toList(), key = { "skeleton-$it" }) { id ->
                    val name = state.providerOf(id)?.displayName ?: id
                    ModelSkeletonCard(providerName = name)
                }
            }
        }
    }
}

/** 单个模型行：左侧模型 id 与信息（点击复制），中部测速状态，右侧测速按钮。 */
@Composable
private fun ModelRow(
    model: RoutedModel,
    testStatus: ModelTestStatus?,
    modifier: Modifier = Modifier,
    onCopy: () -> Unit,
    onTest: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 左侧模型名与信息：点击复制模型名称
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onCopy)
                .padding(vertical = 4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = model.fullId,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    MiuixIcons.Copy,
                    contentDescription = "复制",
                    tint = MiuixTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = buildString {
                    append(model.model.name)
                    if (model.model.contextWindow > 0) {
                        append(" · 上下文 ${model.model.contextWindow}")
                    }
                },
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            // 能力标记：只有上游明确给了才显示
            val tags = buildList {
                if (model.model.extra["reasoning"] == "true") add("思考")
                if (model.model.extra["search"] == "true") add("联网")
                if (model.model.extra["vision"] == "true") add("识图")
                if (model.model.extra["tools"] == "true") add("工具")
                model.model.extra["maxOutput"]?.let { add("输出 ${it}") }
                model.model.extra["vendor"]?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            if (tags.isNotEmpty()) {
                Text(
                    text = tags.joinToString(" · "),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.75f),
                )
            }
            // 倍率：不是所有上游都有公开数据，没有就整行不渲染（不要编造）
            val rateLabel = model.model.extra["rate"]
            val rateDiscount = model.model.extra["rateDiscount"]
            if (!rateLabel.isNullOrEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = "倍率 $rateLabel",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    if (!rateDiscount.isNullOrEmpty()) {
                        Text(
                            text = "限时 $rateDiscount",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }

        // 按钮左侧显示延迟：<5000ms 绿色，>=5000ms 橙色，失败/超时 红色
        when (testStatus) {
            is ModelTestStatus.Testing -> {
                Text(
                    text = "测试中...",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            is ModelTestStatus.Success -> {
                val isFast = testStatus.latencyMs < 5000
                Text(
                    text = "${testStatus.latencyMs}ms",
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Bold,
                    color = if (isFast) SuccessGreen else WarnAmber,
                )
            }
            is ModelTestStatus.Timeout -> {
                Text(
                    text = "超时",
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.error,
                )
            }
            null -> Unit
        }

        // 右侧 Q 弹测试按钮
        Box(
            modifier = Modifier
                .bouncyClick(enabled = testStatus !is ModelTestStatus.Testing, onClick = onTest)
                .clip(RoundedCornerShape(8.dp))
                .background(MiuixTheme.colorScheme.primaryContainer)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "测试",
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** 骨架屏占位卡片：配合微光动效呈现正在流式拉取的状态。 */
@Composable
private fun ModelSkeletonCard(
    providerName: String,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = providerName,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "加载中",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
        repeat(2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(16.dp)
                            .shimmer(RoundedCornerShape(4.dp)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .height(12.dp)
                            .shimmer(RoundedCornerShape(3.dp)),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(width = 46.dp, height = 26.dp)
                        .shimmer(RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}

/**
 * 一个供应商分组的渲染单元。
 *
 * 把分组独立成 Composable 是为了让展开/折叠状态的变化**只重组这一组**：
 * 如果在调用方读 [expanded]，任何一次切换都会让整个 LazyColumn 重组，
 * 模型多时手感明显发涩。
 *
 * [expanded] 传的是同一个 [SnapshotStateList]，读取放在函数内部，
 * Compose 的按需重组会把它限定在本函数范围。
 */
private fun LazyListScope.modelGroupSection(
    routePrefix: String,
    models: List<RoutedModel>,
    expanded: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    testLatencies: Map<String, ModelTestStatus>,
    onCopy: (String) -> Unit,
    onTest: (RoutedModel) -> Unit,
) {
    // 注意：这里**不能**在 LazyListScope 作用域里读 expanded——
    // 它不是 @Composable，读了也不会订阅变更。所以把读取放进 item 的内容 lambda，
    // 让 Compose 在那一层做订阅，展开/折叠时只重建这一个分组。
    item(key = "header-$routePrefix") {
        val isExpanded = routePrefix in expanded
        val title = models.firstOrNull()?.providerName ?: routePrefix
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MiuixTheme.colorScheme.surface)
                // 标题栏不用 bouncyClick：那是给按钮用的超弹动画（spring damping 0.36），
                // 放在会滚动的列表项上每帧都要跑插值，是折叠发卡的另一个原因。
                .clickable {
                    if (routePrefix in expanded) expanded.remove(routePrefix)
                    else expanded.add(routePrefix)
                }
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (isExpanded) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,
                    contentDescription = if (isExpanded) "折叠" else "展开",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "${models.size} 款",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }

    if (routePrefix !in expanded) return
    items(models, key = { it.fullId }) { model ->
        ModelRow(
            model = model,
            testStatus = testLatencies[model.fullId],
            modifier = Modifier.padding(start = 28.dp, end = 28.dp),
            onCopy = { onCopy(model.fullId) },
            onTest = { onTest(model) },
        )
    }
}
