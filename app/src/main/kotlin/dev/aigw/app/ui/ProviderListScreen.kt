package dev.aigw.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.aigw.app.ui.providers.ProviderUiRegistry
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 供应商列表：每个供应商一张卡片（图标、说明、账号统计、启用开关），点进详情。
 *
 * 页面本身不认识任何具体供应商——图标与说明都由各自的 [ProviderUi] 组件提供。
 */
@Composable
fun ProviderListScreen(
    state: AppUiState,
    onOpen: (String) -> Unit,
    onAddCustom: () -> Unit,
) {
    PageScaffold(
        title = "供应商",
        subtitle = "共 ${state.providers.size} 个",
        actions = {
            Icon(
                MiuixIcons.Add,
                contentDescription = "添加自定义供应商",
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onAddCustom).padding(6.dp),
            )
        },
    ) {
        if (state.providers.isEmpty()) {
            item {
                SectionCard {
                    EmptyHint("还没有供应商", "内置供应商会在启动时自动登记；自定义供应商可点右上角「+」添加。")
                }
            }
        } else {
            itemsIndexed(state.providers, key = { _, p -> p.id }) { index, provider ->
                val ui = ProviderUiRegistry.of(provider.id)
                SectionCard(
                    modifier = Modifier
                        .staggeredAppear(index)
                        .clickable { onOpen(provider.id) },
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // 渐变背景图标框
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MiuixTheme.colorScheme.primaryContainer,
                                        MiuixTheme.colorScheme.secondaryContainer,
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        ui?.Icon(Modifier.size(28.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = provider.displayName,
                            style = MiuixTheme.textStyles.headline2,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = ui?.description() ?: provider.id,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        // 账号统计胶囊
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AccountBadge(
                                text = "账号 ${provider.accountCount}",
                                color = MiuixTheme.colorScheme.surfaceVariant,
                                textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            if (provider.usableCount > 0) {
                                AccountBadge(
                                    text = "可用 ${provider.usableCount}",
                                    color = MiuixTheme.colorScheme.tertiaryContainer,
                                    textColor = MiuixTheme.colorScheme.onTertiaryContainer,
                                )
                            } else {
                                AccountBadge(
                                    text = "不可用",
                                    color = MiuixTheme.colorScheme.errorContainer,
                                    textColor = MiuixTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                    Icon(
                        MiuixIcons.ChevronForward,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
                }
            }
        }
    }
}

@Composable
private fun AccountBadge(text: String, color: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = MiuixTheme.textStyles.footnote2, color = textColor)
    }
}
