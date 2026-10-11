package dev.aigw.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.aigw.core.security.OutboundEvidence
import dev.aigw.app.ui.DialogButtonRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.extra.WindowBottomSheet
import top.yukonga.miuix.kmp.extra.WindowDialog
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 安全防护与风控设置页：
 * 以可插拔的模块化组件结构组织，方便后续扩展更多防护能力（如 IP管控、指纹脱敏等）。
 */
@Composable
fun SecurityProtectionScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    var showEvidenceSheet by remember { mutableStateOf(false) }
    var showRateLimitDialog by remember { mutableStateOf(false) }

    PageScaffold(
        title = "安全防护",
        subtitle = "反审核脱敏与账号限速",
        leading = { BackButton(onBack) },
    ) {
        // 组件 1：反审核脱敏模块
        item {
            DesensitizationComponent(
                state = state,
                onToggle = { enabled ->
                    viewModel.updateSecuritySettings(state.security.copy(sanitizeEnabled = enabled))
                },
                onReloadPipeline = { viewModel.reloadSanitizerPipeline() },
                onViewEvidence = { showEvidenceSheet = true },
            )
        }

        // 组件 2：账号级限速模块
        item {
            RateLimitComponent(
                state = state,
                onOpenEdit = { showRateLimitDialog = true },
            )
        }
    }

    EvidenceSheet(
        show = showEvidenceSheet,
        evidences = state.evidences,
        onDismiss = { showEvidenceSheet = false },
        onClear = { viewModel.clearEvidences() },
    )

    RateLimitEditDialog(
        show = showRateLimitDialog,
        currentMin = state.security.minIntervalMillis,
        currentJitter = state.security.jitterMillis,
        onDismiss = { showRateLimitDialog = false },
        onConfirm = { newMin, newJitter ->
            viewModel.updateSecuritySettings(
                state.security.copy(
                    minIntervalMillis = newMin,
                    jitterMillis = newJitter,
                ),
            )
            showRateLimitDialog = false
        },
    )
}

// ==============================================================================
// 独立防护组件 1：反审核脱敏
// ==============================================================================

@Composable
fun DesensitizationComponent(
    state: AppUiState,
    onToggle: (Boolean) -> Unit,
    onReloadPipeline: () -> Unit,
    onViewEvidence: () -> Unit,
) {
    SmallTitle("反审核脱敏")
    SectionCard(enterIndex = 0) {
        SuperSwitch(
            title = "开启脱敏",
            summary = "客户端的合规声明里常有 DoS / exploit 等词，会被上游误拦。开启后在这些词内部插零宽空格，只改 system 消息，不影响用户输入",
            checked = state.security.sanitizeEnabled,
            onCheckedChange = onToggle,
            insideMargin = PaddingValues(0.dp),
        )

        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineActionButton("重新加载处理链") {
                onReloadPipeline()
            }
            OutlineActionButton("进入出网取证") {
                onViewEvidence()
            }
        }
    }
}

// ==============================================================================
// 独立防护组件 2：账号级限速
// ==============================================================================

@Composable
fun RateLimitComponent(
    state: AppUiState,
    onOpenEdit: () -> Unit,
) {
    SmallTitle("账号级限速")
    SectionCard(enterIndex = 1) {
        SuperArrow(
            title = "最小间隔",
            summary = "同一账号两次上游请求的最短间隔",
            endActions = {
                Text(
                    text = "${state.security.minIntervalMillis} 毫秒",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            },
            onClick = onOpenEdit,
            insideMargin = PaddingValues(vertical = 12.dp),
        )

        HorizontalDivider(modifier = Modifier.fillMaxWidth())

        SuperArrow(
            title = "抖动幅度",
            summary = "避免固定节拍本身成为特征",
            endActions = {
                Text(
                    text = "${state.security.jitterMillis} 毫秒",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            },
            onClick = onOpenEdit,
            insideMargin = PaddingValues(vertical = 12.dp),
        )
    }
}

// ==============================================================================
// 出网取证详情底部抽屉
// ==============================================================================

@Composable
private fun EvidenceSheet(
    show: Boolean,
    evidences: List<OutboundEvidence>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    // 清空不可恢复，需二次确认
    var confirmClear by remember { mutableStateOf(false) }

    WindowBottomSheet(
        show = show,
        title = "出网取证",
        onDismissRequest = onDismiss,
        endAction = {
            if (evidences.isNotEmpty()) {
                TextButton(text = "清空", onClick = { confirmClear = true })
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "共 ${evidences.size} 条脱敏证据（只展示 system 改写）",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            Spacer(Modifier.height(12.dp))

            if (evidences.isEmpty()) {
                EmptyHint("暂无取证记录", "当客户端发送包含 DoS / exploit 等词的 system 提示词时，脱敏穿透快照会记录在这里。")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .overScrollVertical(),
                    overscrollEffect = null,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(evidences, key = { i, e -> "${e.atMillis}_$i" }) { index, item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${timeFormat.format(Date(item.atMillis))} · ${item.model}",
                                    style = MiuixTheme.textStyles.footnote1,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "复制",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MiuixTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            clipboard.setText(AnnotatedString(item.sanitizedSnippet))
                                        }
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }

                            Text(
                                text = "命中词汇：${item.matchedTerms.joinToString(", ")}",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium,
                            )

                            Text(
                                text = "脱敏前片段：",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Text(
                                text = item.originalSnippet,
                                style = MiuixTheme.textStyles.footnote1,
                                fontFamily = FontFamily.Monospace,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )

                            Text(
                                text = "脱敏后（已注入零宽空格）：",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.primary,
                            )
                            Text(
                                text = item.sanitizedSnippet,
                                style = MiuixTheme.textStyles.footnote1,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }

    WindowDialog(
        show = confirmClear,
        title = "清空取证记录",
        onDismissRequest = { confirmClear = false },
    ) {
        Text(
            text = "将删除全部 ${evidences.size} 条脱敏证据，删除后不可恢复。",
            style = MiuixTheme.textStyles.main,
        )
        DialogButtonRow(
            confirmText = "清空",
            onCancel = { confirmClear = false },
            onConfirm = {
                onClear()
                confirmClear = false
            },
        )
    }
}

// ==============================================================================
// 限速参数修改弹窗
// ==============================================================================

@Composable
private fun RateLimitEditDialog(
    show: Boolean,
    currentMin: Long,
    currentJitter: Long,
    onDismiss: () -> Unit,
    onConfirm: (newMin: Long, newJitter: Long) -> Unit,
) {
    // 以 show 为 key：每次重新打开都从当前值初始化输入框
    var minText by remember(show, currentMin) { mutableStateOf(currentMin.toString()) }
    var jitterText by remember(show, currentJitter) { mutableStateOf(currentJitter.toString()) }
    var error by remember(show) { mutableStateOf("") }

    WindowDialog(
        show = show,
        title = "修改账号限速参数",
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextField(
                value = minText,
                onValueChange = { minText = it.filter(Char::isDigit).take(5) },
                label = "最小间隔（毫秒）",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = jitterText,
                onValueChange = { jitterText = it.filter(Char::isDigit).take(5) },
                label = "抖动幅度（毫秒）",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "同一账号连续请求将强制按“最小间隔 + 随机抖动”节拍等待，建议 1000~3000ms。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            if (error.isNotEmpty()) {
                Text(
                    text = error,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                )
            }
            DialogButtonRow(
                confirmText = "保存",
                onCancel = onDismiss,
                onConfirm = {
                    val min = minText.toLongOrNull()
                    val jitter = jitterText.toLongOrNull()
                    if (min == null || jitter == null) {
                        error = "请输入有效的毫秒数"
                    } else {
                        error = ""
                        onConfirm(min.coerceIn(0L, 60_000L), jitter.coerceIn(0L, 10_000L))
                    }
                },
            )
        }
    }
}
