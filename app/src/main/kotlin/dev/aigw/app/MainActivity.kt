package dev.aigw.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDefaults
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.extra.WindowDialog
import dev.aigw.app.service.GatewayTileService
import dev.aigw.app.ui.pushPopSpec
import dev.aigw.app.ui.tabContentSpec
import dev.aigw.app.ui.AppUiState
import dev.aigw.app.ui.CookieLoginActivity
import dev.aigw.app.ui.DialogButtonRow
import dev.aigw.app.ui.ApiSettingsScreen
import dev.aigw.app.ui.AppViewModel
import dev.aigw.app.ui.AppearanceThemeScreen
import dev.aigw.app.ui.CustomProviderScreen
import dev.aigw.app.ui.CreditCenterScreen
import dev.aigw.app.ui.DataManagementScreen
import dev.aigw.app.ui.HomeScreen
import dev.aigw.app.ui.KeepAliveScreen
import dev.aigw.app.ui.LogsScreen
import dev.aigw.app.ui.MeScreen
import dev.aigw.app.ui.ModelsScreen
import dev.aigw.app.ui.ProviderDetailScreen
import dev.aigw.app.ui.ProviderListScreen
import dev.aigw.app.ui.ProviderCreditsScreen
import dev.aigw.app.ui.ProxyScreen
import dev.aigw.app.ui.SecurityProtectionScreen
import dev.aigw.app.ui.SubPage
import dev.aigw.app.ui.TaskCenterScreen
import dev.aigw.app.ui.UsageScreen
import dev.aigw.app.ui.providers.ProviderUiRegistry
import dev.aigw.app.ui.theme.AiGatewayTheme
import dev.aigw.app.ui.theme.ThemeAccent

class MainActivity : ComponentActivity() {

    private var viewModel: AppViewModel? = null

    private val gatewayToggledReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // 磁贴启停后通知界面刷新（磁贴无法直接触达 ViewModel）
            viewModel?.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        registerGatewayToggledReceiver()
        setContent {
            val vm: AppViewModel = viewModel(factory = appViewModelFactory())
            viewModel = vm
            val state by vm.state.collectAsStateWithLifecycle()

            // 内嵌登录页：ViewModel 把要打开的 URL 放进 state，这里负责拉起 Activity，
            // 并把结果（Cookie / 取消）回传给 ViewModel。全部在 App 内完成，不跳系统浏览器。
            val loginLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                val cookie = result.data?.getStringExtra(CookieLoginActivity.EXTRA_RESULT_COOKIE).orEmpty()
                vm.applyInAppLoginCookie(cookie)
            }
            LaunchedEffect(state.inAppLoginUrl) {
                val url = state.inAppLoginUrl
                if (url.isEmpty()) return@LaunchedEffect
                val intent = Intent(this@MainActivity, CookieLoginActivity::class.java).apply {
                    putExtra(CookieLoginActivity.EXTRA_START_URL, url)
                    putExtra(CookieLoginActivity.EXTRA_TITLE, state.inAppLoginTitle)
                    putExtra(
                        CookieLoginActivity.EXTRA_COOKIE_HOSTS,
                        state.inAppLoginCookieHosts.toTypedArray(),
                    )
                    // 成功信号按上游类型给，命中即自动收尾（用户不用点「完成」）：
                    //  - Trae / Antigravity / WorkBuddy：登录完会跳到本地回调端口；
                    //  - LongCat：站内跳转、没有回调地址，靠关键 Cookie 判断。
                    putExtra(
                        CookieLoginActivity.EXTRA_SUCCESS_URL_PREFIXES,
                        LocalHostCallbackPorts.map { "http://127.0.0.1:$it/" }.toTypedArray(),
                    )
                    if (state.inAppLoginCookieHosts.any { it.contains("longcat") }) {
                        putExtra(CookieLoginActivity.EXTRA_SUCCESS_COOKIE, "passport_token_key")
                    }
                }
                loginLauncher.launch(intent)
            }

            AiGatewayTheme(
                themeMode = state.themeMode,
                accent = ThemeAccent(state.accentHue),
                dynamicColor = state.dynamicColor,
                backgroundTone = state.backgroundTone,
            ) {
                AppRoot(vm, state)
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(gatewayToggledReceiver) }
        viewModel = null
        super.onDestroy()
    }

    private fun registerGatewayToggledReceiver() {
        val filter = IntentFilter(GatewayTileService.ACTION_GATEWAY_TOGGLED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(gatewayToggledReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(gatewayToggledReceiver, filter)
        }
    }

    private fun appViewModelFactory() = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppViewModel(application) as T
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("首页", MiuixIcons.CloudFill),
    Providers("供应商", MiuixIcons.Contacts),
    Models("模型", MiuixIcons.GridView),
    Logs("记录", MiuixIcons.Recent),
    Me("我的", MiuixIcons.Settings),
}

@Composable
private fun AppRoot(viewModel: AppViewModel, state: AppUiState) {
    var tab by remember { mutableStateOf(Tab.Home) }
    var subPage by remember { mutableStateOf<SubPage?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) {
        if (state.notice.isNotEmpty()) {
            snackbarHostState.showSnackbar(state.notice)
            viewModel.consumeNotice()
        }
    }
    LaunchedEffect(state.busy) {
        if (state.busy.isNotEmpty()) snackbarHostState.showSnackbar(state.busy)
    }

    // 系统返回与页内返回按钮同层级：二级页回它的上级，其余回主 tab
    BackHandler(enabled = subPage != null) {
        subPage = when (val page = subPage) {
            is SubPage.ProviderCredits -> SubPage.CreditCenter
            is SubPage.CustomProviderEdit ->
                page.key?.let { SubPage.ProviderDetail(ProviderUiRegistry.CUSTOM_PREFIX + it) }
            else -> null
        }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = subPage == null && tab == item,
                        onClick = {
                            subPage = null
                            tab = item
                        },
                        icon = item.icon,
                        label = item.label,
                    )
                }
            }
        },
    ) { insets ->
        // 外层 Scaffold 的 bottom padding 已含 bottomBar（含导航栏 inset）：把它用到内容上，
        // 并 consumeWindowInsets 消费掉，否则内层页面自己只补了导航栏 inset，
        // 差值（bottomBar 高度）正好把列表尾部挡在底栏下面（每页都拉不到底）。
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = insets.calculateBottomPadding())
                .consumeWindowInsets(PaddingValues(bottom = insets.calculateBottomPadding())),
        ) {
            // 二级页推拉过渡：推入从右滑入，返回向右滑出
            AnimatedContent(
                targetState = subPage,
                transitionSpec = { pushPopSpec() },
                label = "subPage",
            ) { page ->
                when (page) {
                is SubPage.ProviderDetail -> ProviderDetailScreen(
                    providerId = page.providerId,
                    state = state,
                    viewModel = viewModel,
                    onBack = { subPage = null },
                    onEditCustom = { subPage = SubPage.CustomProviderEdit(it.removePrefix("custom:")) },
                )

                                is SubPage.CustomProviderEdit -> CustomProviderScreen(
                    existing = state.customProviders.firstOrNull { it.key == page.key },
                    onSave = {
                        viewModel.saveCustomProvider(it)
                        subPage = SubPage.ProviderDetail(it.providerId)
                    },
                    onDelete = {
                        viewModel.removeCustomProvider(it)
                        subPage = null
                    },
                    onBack = {
                        subPage = page.key?.let { SubPage.ProviderDetail(ProviderUiRegistry.CUSTOM_PREFIX + it) }
                    },
                )

                SubPage.Usage -> UsageScreen(state, viewModel) { subPage = null }
                SubPage.TaskCenter -> TaskCenterScreen(state, viewModel) { subPage = null }
                SubPage.CreditCenter -> CreditCenterScreen(
                    state = state,
                    onOpen = { subPage = SubPage.ProviderCredits(it) },
                    onBack = { subPage = null },
                )
                is SubPage.ProviderCredits -> ProviderCreditsScreen(
                    providerId = page.providerId,
                    state = state,
                    viewModel = viewModel,
                    onBack = { subPage = SubPage.CreditCenter },
                )
                SubPage.KeepAlive -> KeepAliveScreen(state, viewModel) { subPage = null }
                SubPage.Proxy -> ProxyScreen(state, viewModel) { subPage = null }
                SubPage.ApiSettings -> ApiSettingsScreen(state, viewModel) { subPage = null }
                SubPage.SecurityProtection -> SecurityProtectionScreen(state, viewModel) { subPage = null }
                SubPage.DataManagement -> DataManagementScreen(state, viewModel) { subPage = null }
                SubPage.AppearanceTheme -> AppearanceThemeScreen(state, viewModel) { subPage = null }
                null -> MainTabs(
                    viewModel = viewModel,
                    state = state,
                    tab = tab,
                    onTab = { newTab ->
                        subPage = null
                        tab = newTab
                    },
                    onSubPage = { subPage = it },
                )
                }
            }
        }
    }

    WindowDialog(
        show = state.updateInfo != null,
        title = "发现新版本 ${state.updateInfo?.version.orEmpty()}",
        onDismissRequest = { viewModel.dismissUpdate() },
    ) {
        val info = state.updateInfo
        Text(
            text = info?.notes?.ifEmpty { null } ?: "该版本没有提供更新说明。",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
        )
        DialogButtonRow(
            confirmText = "前往下载",
            onCancel = { viewModel.dismissUpdate() },
            onConfirm = {
                info?.let { viewModel.openReleasePage(it.url) }
                viewModel.dismissUpdate()
            },
        )
    }
}

/** 主 Tab 页面：切换时按索引方向做小幅滑移 + 淡入淡出。 */
@Composable
private fun MainTabs(
    viewModel: AppViewModel,
    state: AppUiState,
    tab: Tab,
    onTab: (Tab) -> Unit,
    onSubPage: (SubPage?) -> Unit,
) {
    AnimatedContent(
        targetState = tab,
        transitionSpec = { tabContentSpec { it.ordinal } },
        label = "tab",
    ) { page ->
        when (page) {
            Tab.Home -> HomeScreen(
                state = state,
                viewModel = viewModel,
                openProviders = { onTab(Tab.Providers) },
                openTaskCenter = { onSubPage(SubPage.TaskCenter) },
            )
            Tab.Providers -> ProviderListScreen(
                state = state,
                onOpen = { onSubPage(SubPage.ProviderDetail(it)) },
                onAddCustom = { onSubPage(SubPage.CustomProviderEdit(null)) },
            )
            Tab.Models -> ModelsScreen(state, viewModel)
            Tab.Logs -> LogsScreen(state, viewModel)
            Tab.Me -> MeScreen(state, viewModel, open = { onSubPage(it) })
        }
    }
}

/** 网关本地回调端口：Trae 51120、Antigravity 51121。 */
private val LocalHostCallbackPorts = listOf(51120, 51121)
