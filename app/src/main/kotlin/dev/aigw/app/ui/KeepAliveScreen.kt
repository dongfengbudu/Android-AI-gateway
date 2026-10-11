package dev.aigw.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.aigw.app.data.KeepAlive
import dev.aigw.app.ui.theme.SuccessGreen
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 保活与权限：把网关在后台持续运行所需的系统条件逐项检测出来，并给出一键跳转。
 *
 * 锁屏后端口还能被客户端调通，需要几件事同时成立：通知权限、应用不在电池优化名单里、
 * 系统没有限制它后台运行。这些都没有 API 能直接代改，只能检测 + 引导。
 */
@Composable
fun KeepAliveScreen(state: AppUiState, viewModel: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val status = state.keepAlive

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refresh() }

    PageScaffold(
        title = "保活与权限",
        subtitle = "让网关在后台持续运行",
        leading = { BackButton(onBack) },
    ) {
        item {
            SectionCard(enterIndex = 0) {
                StatusHeaderRow(
                    title = "保活状态",                    value = if (status?.allGood == true) "已就绪" else "有待处理",
                    valueColor = if (status?.allGood == true) {
                        MiuixTheme.colorScheme.onTertiaryContainer
                    } else {
                        MiuixTheme.colorScheme.error
                    },
                )
                Text(
                    text = "通知权限 + 电池优化白名单 + 不被限制后台，这几项都满足后才能锁屏稳定可用。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                if (!state.running) {
                    Text(
                        text = "当前服务未启动：先去首页启动，否则锁屏后没有进程监听端口。",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
            }
        }

        item { SmallTitle("系统条件") }
        item {
            SectionCard(enterIndex = 1) {
                StatusRow(
                    title = "通知权限",
                    detail = if (status?.notificationsGranted == true) "已允许，常驻通知可见" else "未允许，后台通知不会显示",
                    ok = status?.notificationsGranted == true,
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        launch(context, KeepAlive.notificationSettingsIntent(context), "无法打开通知设置")
                    }
                }
                HorizontalDivider(modifier = Modifier.fillMaxWidth())
                StatusRow(
                    title = "电池优化",
                    detail = if (status?.batteryUnrestricted == true) {
                        "已加入白名单（不限制耗电）"
                    } else {
                        "受系统电池优化限制，锁屏后可能被冻结、端口调不通"
                    },
                    ok = status?.batteryUnrestricted == true,
                ) {
                    // 部分 ROM 不支持直接请求，失败就退回到电池优化列表页
                    if (!launch(context, KeepAlive.batteryOptimizationIntent(context), null)) {
                        launch(context, KeepAlive.batterySettingsIntent(), "无法打开电池优化设置")
                    }
                }
                HorizontalDivider(modifier = Modifier.fillMaxWidth())
                StatusRow(
                    title = "后台运行限制",
                    detail = if (status?.backgroundRestricted == true) "系统已限制本应用后台运行" else "未被系统限制后台运行",
                    ok = status?.backgroundRestricted != true,
                ) {
                    launch(context, KeepAlive.appDetailsIntent(context), "无法打开应用详情")
                }
                HorizontalDivider(modifier = Modifier.fillMaxWidth())
                StatusRow(
                    title = "流量节省",
                    detail = if (status?.dataSaverRestricted == true) "已开启流量节省，后台联网可能受限" else "未开启流量节省",
                    ok = status?.dataSaverRestricted != true,
                ) {
                    launch(context, KeepAlive.appDetailsIntent(context), "无法打开应用详情")
                }
            }
        }

        item { SmallTitle("厂商后台管理") }
        item {
            SectionCard(enterIndex = 2) {
                Text(
                    text = "国产系统默认会限制第三方应用后台。下面的入口会尝试直接打开厂商的后台管理页；" +
                        "打不开就按提示手动设置。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                ChoiceChip("打开厂商后台设置", false) {
                    val opened = KeepAlive.autoStartIntents().any { launch(context, it, null) }
                    if (!opened) {
                        launch(context, KeepAlive.appDetailsIntent(context), "无法打开系统页面")
                    }
                    Toast.makeText(context, KeepAlive.manualHint(Build.MANUFACTURER), Toast.LENGTH_LONG).show()
                }
                Text(
                    text = KeepAlive.manualHint(Build.MANUFACTURER),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        item {
            Text(
                text = "说明：本应用用「常驻通知」保持进程存活，不需要额外的唤醒锁（唤醒锁更耗电且没必要）。" +
                    "如果系统把本应用放进电池优化名单或限制后台，锁屏一段时间后进程会被冻结，客户端就会连接失败——" +
                    "按上面的检测项逐条处理即可。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 12.dp),
            )
        }
    }
}

/** 状态点：绿色 = 已就绪，红色 = 有问题。 */
@Composable
private fun StatusDot(ok: Boolean) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (ok) SuccessGreen else MiuixTheme.colorScheme.error),
    )
}

/** 系统条件检测行：状态点 + 标题/说明，整行点击跳转对应系统设置。 */
@Composable
private fun StatusRow(
    title: String,
    detail: String,
    ok: Boolean,
    onAction: () -> Unit,
) {
    SuperArrow(
        title = title,
        summary = detail,
        startAction = { StatusDot(ok) },
        summaryColor = BasicComponentColors(
            color = if (ok) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.error,
            disabledColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        ),
        onClick = onAction,
        insideMargin = PaddingValues(vertical = 12.dp),
    )
}

/** 卡内标题行：左标题右状态值。 */
@Composable
private fun StatusHeaderRow(title: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MiuixTheme.textStyles.headline2,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MiuixTheme.textStyles.headline2, color = valueColor)
    }
}

private fun launch(context: Context, intent: Intent, failureMessage: String?): Boolean {
    return try {
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        if (failureMessage != null) {
            Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
        }
        false
    }
}
