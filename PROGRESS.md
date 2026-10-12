# 进度

## 当前状态
自动化任务移植（2026-10-12，来源：workbuddy2api-panel）：
- 上游能力层：`CodeBuddyProvider` 新增 `ACTION_AUTO_TASKS`（`auto_tasks`）与 `autoTasks(account, payload)`，
  内含 25 个任务码的动作表（`chat_5` / `first_buddy` / `Model_chat_GLM5.2` / `RichMeow_Chat` / `Buddy_App` /
  `Buddy_App_QQ` / `automation_1` / `Library_read` / `template_5` / `playbook_prompt` / `create_canvas` /
  `expert_5` / `Expert_team_use_3` / `Hp_Appearance` / `skill_1` / `Expert_lighthouse` / `black_cat` /
  `school_season` / `Sequential_Tasks_1..7`），顺序即执行顺序，与 wb2api `internal/panel/autotask.go` 逐条对齐。
- 新增 `CodeBuddyDesktopEvents.kt`（独立文件、纯数据、无网络 IO）：桌面端行为指纹与小程序端事件的载荷构造，
  由 provider 侧负责 POST。
- 门控：国际版跳过签到/成长/活跃/夜猫子；企业版（`credential.enterpriseId` 非空）同样跳过，返回成功并说明
  「企业版账号没有个人成长体系」——上游对这类账号一律 400/403，不执行才是正确结果。
- 定时排程：`app/data/TaskSchedule.kt` 定义 `AutoTaskKind`（签到 9/21、活跃 10、成长任务 1、夜猫子 23）
  与开关持久化（`settings/tasks.<id>`，默认开）；`AutoTaskScheduler` 挂在网关前台服务内，60 秒核对挂钟 + 
  按「日-时-类别」幂等标记触发，服务被杀即随之中止（`START_NOT_STICKY`）。
- 界面：任务中心页新增「一键完成成长任务」与四类定时开关，并显示下次执行时间。
- 未移植：wb2api 的旅行礼物（9/21）与保活（22）两类单独时段（其行为已挂在成长中心接口内，不另设开关）。
- 验证：CI（GitHub Actions）已编译通过 dfb3100；本轮排程与界面改动待 CI 复核（本容器无 JDK/Gradle）。

主题体系重做（2026-10-12）：
- 完全放弃黑金主题：删除 `AppColors`/`LocalAppColors`/`GoldAccent`/`GoldText`/`HairlineBorder`
  /`HeroGradient`/`HeroBrush` 与 `goldStart`/`goldEnd` 持久化键，全仓无 gold 引用残留。
- 引入主题强调色模式：`ThemeAccent(hue, chroma)` 一个概念描述配色，`Theme.kt` 内部
  按 HSL 调性阶梯展开整套明/暗 scheme（主色/容器色/表面色/描边色同色相展开，
  点缀色 secondary/tertiary 用色相偏移 +32°/-46° 的同调性衍生）；默认靛蓝 hue=258。
- `AiGatewayTheme` 接口为 `themeMode + accent + dynamicColor + backgroundTone`；动态取色
  开启时嵌套一层 `MiuixTheme` 只覆写 `background`；MainActivity 传
  `ThemeAccent(state.accentHue)`；强调色持久化键 `settings/appearance.accentHue`。
- 页面底色改「调性档位」（同日第二轮）：`BackgroundTone`（默认/纯净/微染/浓染）取代自定义
  HEX 色值，底色与强调色同色相、只改染色浓度，且只在当前明暗极性内浮动（浅档仍浅底、
  深档仍深底），故 `onBackground` 无需重算；`toneBackground` 用 HSV 调整，
  `themeBackgroundColor` 供设置页色点预览；键 `settings/appearance.backgroundTone`
  （旧 `backgroundHex` 弃用，遗留值自动忽略）。
- 外观页打磨：明暗模式/页面底色/强调色/动态取色四段 + 新增「效果预览」整页样张
  （底色 + 卡片 + 强调色按钮）；色相环居中；删掉自定义 HEX 输入与固定色值预设。
- 验证：CI（GitHub Actions）已编译通过 e7ad36c；据此确认 miuix 0.8.8 `Colors` 无
  `tertiary`/`onTertiary` 参数（已删），`tertiaryContainer`/`onTertiaryContainer` 等可用。
  本容器无 JDK/Gradle，本轮「调性档位」改动未本地编译，靠 CI 复核。

统一日志页重做（2026-10-11）：
- 新增 `core/usage/OperationLog`：全部非调用操作的统一日志（环形缓冲 + 节流落盘 + 开放类别），
  接口 `info/warn/error(category, message)` / `entries(category)` / `clear()`；
  旧 `logs/requests.json` 与 `logs/login.json` 首次启动自动迁入 `logs/operations.json` 并删旧键。
- `GatewayEngine.requestLog/loginLog` 合并为 `operationLog`（request/login 两个内置类别），
  `ProviderBootstrap.onLoginLog` 同步改写；`StorageAudit` 两个旧分组合并为 NAME_OPERATIONS。
- `LogsScreen` 重写：调用记录 + 操作日志按时间合并成统一流，类别/级别两级筛选
  （调用失败→错误、中断→警告）；新功能自带新类别即可自动出现在筛选胶囊；
  页面只用 MiuixTheme 令牌，不再引用金色 LocalAppColors/Hero 渐变。
- 新增 `core/usage/OperationLogTest`（迁移/持久化回读/容量/过滤/清空），
  `VerboseLoggingTest` 改读 `operationLog.entries()`。
- 代码评审修复：ARCHITECTURE.md 模块地图补登 usage 两模块；`StorageReport.recordsChars`
  注释纠正为「调用记录占用」；`StorageAudit`/`OperationLog` 行内全限定名改 import、
  `keysMatching` 改名 `exactKeys`；迁移中断残留的旧键在下次启动清理（新增测试锁定）；
  日志页进入即刷新一次（不再等 5 秒首帧旧数据）；日志页顶栏新增「清空」入口（二次确认）。

模型页账号联动修复（2026-10-11）：
- 暴露开关不可用：回调链上取反了三次，已隐藏的模型永远点不回暴露；去掉分组层多余取反。
- 账号全停用的分组仍正常显示：`/v1/models` 已剔除（filterByUsableAccounts），但模型页
  只在「只看可用」开启时过滤且用的是 providerId 粒度（区域型会误判）。改为：引擎新增
  `usableRoutePrefixes`（与 /v1/models 同口径、区域型按区域），AppUiState 新增
  `usableModelPrefixes`（refresh/模型流式就绪/目录覆盖三处更新）；模型页无可用账号的
  分组标题旁标注「无可用账号」并整组置灰，点开仍可管理暴露开关。

登录日志扩展到全部供应商（2026-10-11）：
- `ProviderHooks` 新增 `onLoginLog`，引擎统一接住写进 `loginLog`；所有登录路径都覆盖：
  - 引擎层（全部供应商通用）：网页登录/OAuth/设备授权开始、回调端口监听与超时、
    短信验证码发送、短信/OAuth/网页登录失败、导入失败、adopt 的成功终态；
  - Trae：生成登录链接（含 region）、收到回调（region/uid）、换 token、拉用户信息；
  - Antigravity：生成授权链接（redirect_uri）、token 接口状态与响应摘录；
  - Loomy：`LoomyAuthClient.call` 的路径/状态/响应摘录（`onLoginLog` 注入）；
  - CodeBuddy：沿用上一轮的 `onLoginLog` 参数；
  - LongCat / 自定义供应商：Cookie 与凭证导入走 `importAccountJson`，已被引擎层覆盖。
- 新增 `dev.aigw.core.util.redactSecrets`：登录响应摘录写日志前抹掉
  accessToken/refreshToken/session/token/secret 类字段，避免凭证随日志落盘。

WorkBuddy 国际版登录修复与登录日志（2026-10-11）：
- 根因：`beginBrowserLogin` 拿到 ticket 后调 `pollDeviceLogin` 未传 region，拿
  workbuddy.ai 签发的 state 去轮询国内端点（copilot.tencent.com），上游对未知 state 永远返回
  等待中（code=11217）→ 界面一直「等待授权完成」。另外 `beginDeviceLogin` 写区域设置
  走的是异步协程，与 `beginBrowserLogin` 读设置存在竞态。
- 修复：`pollDeviceLogin` 现在带上 `deviceRegions` 里的区域；`beginDeviceLogin` 改为
  同步落盘区域后再开登录；引擎 `pollDeviceAuth` 在 region 为空时回落到设置里的区域兜底。
- 新增登录日志：`GatewayEngine.loginLog`（独立存储键 `logs/login.json`，容量 600），
  CodeBuddyProvider 通过 `onLoginLog` 上报每次授权链接请求/轮询的 URL、HTTP 状态、
  code 与响应摘录（SNIPPET_LIMIT=300）；引擎侧记录开始登录、轮询次数与终态。
  「记录」页新增「登录日志」Tab（LogTab.Login）；存储占用页新增对应分组（NAME_LOGIN）。

代码审查修复（2026-10-11）已完成：清理误提交到仓库根目录的构建产物并补 `.gitignore`、
`ARCHITECTURE.md` 与代码同步（START_NOT_STICKY、新增模块补登）、网关启停入口聚拢到
`GatewayService.start/stop`、首页 Tile 边框顺序修复、CodeBuddy 登录/账号卡片布局重排、
`PageScaffold` 副标题恢复显示（miuix 0.8.8 无 TopAppBar.subtitle 参数）。

后续修复（同日）：
- 全页面卡片内控件零间距堆叠修复（8 处）；首页六宫格补横向 12dp 外边距对齐；
- 外层 AppRoot 内容 Box 应用并消费 bottom padding，修复每页列表尾部被底栏遮挡；
- CookieLoginActivity：hosts 为空（Trae/WorkBuddy 等不走 Cookie 的登录）时点「完成」直接关页，不再误报「还没读到登录信息」；
- 闪退修复：miuix 0.8.8 弹窗（WindowDialog → NavigationBackHandler）依赖
  `LocalNavigationEventDispatcherOwner`，由 ComponentActivity ≥1.12.0 提供；
  `activity-compose` 从 1.9.3 升到 1.12.4（见 app/build.gradle.kts 注释）。

第二轮修复（同日）：
- WorkBuddy 轮询健壮性：轮询异常转 Failed 不再静默死协程；等待期间每 10 秒刷新 busy 文案；
  超时/失败文案指明「需在页面点到授权/允许」，失败写入请求日志页可查；
- 账号块内部重排：新增 AccountInfoRow（左标签右值对齐），CodeBuddy/Trae 账号块加分割线；
- 首页六宫格补底部 12dp 外边距，与消耗趋势卡拉开；
- 模型页改为纯手动刷新 + 目录缓存（cache/models.json，启动直读，登录变化/切 Tab 不再自动拉取），
  分组默认全部折叠，点分组头展开；
- 日志页「请求日志」重排：级别彩点 + 时间 + 正文 + 分割线，替代一行一卡；
  点调用记录的底部抽屉闪退与 WindowDialog 同源，随 activity-compose 升级修复；
- 接入设置新增「随机生成 Key」按钮（sk- + 32 位随机串）；
- 修复 ApiSettingsScreen 上一轮缩进脚本造成的结构错误（所有小节被挤进第一个 item）。

弹层审查修复（同日）：
- 硬违规修复：「出网取证」清空、调用记录长按删除均加二次确认弹窗；
- 弹层 show 管理统一为「常驻组合 + show 表达式」（更新弹窗、代理类型、取证抽屉、限速弹窗、
  模型选择抽屉均去掉外层 if/let 门控），附带修复关闭无退场动画的问题；
- 限速弹窗保存不再静默回退旧值，非法输入给错误提示；提取 DialogButtonRow 共享确认按钮行。

CI 编译修复（同日）：
- 919594e 编译失败根因：MainActivity 更新弹窗改为常驻组合时写成了
  `info?.notes.ifEmpty { ... }`（可空接收者直接调 ifEmpty，非法 Kotlin）；
  已改为 `info?.notes?.ifEmpty { null } ?: "…"`。此前粘贴的「WindowDialog 缺
  NavigationEventDispatcherOwner」是运行时崩溃分析，与编译报错是两件事，后者已由
  activity-compose 1.12.4 升级覆盖。

## 构建环境（aarch64 PRoot 容器）
- Gradle 8.14.2 缓存已就绪；JDK: /opt/taixu/toolchains/android/jdk；AAPT2 ARM64 在 build-tools/35.0.0。
- `gradle.properties` 需含 `android.builder.sdkDownload=false`；AAPT2 override 参数：`-Pandroid.aapt2FromMavenOverride=$TAIXU_AAPT2_PATH`。
- 可用构建命令：`TAIXU_BUILD_ENGINE=1 JAVA_HOME=/opt/taixu/toolchains/android/jdk ANDROID_HOME=/opt/android-sdk /opt/taixu/bin/gradle :app:assembleDebug --no-daemon -Pandroid.builder.sdkDownload=false -Pandroid.aapt2FromMavenOverride=/opt/android-sdk/build-tools/35.0.0/aapt2`
- `taixu-build android` 脚本在 `/opt/taixu/scripts/taixu-build.sh:230` 硬编码 compileSdk 34 上限，本项目 compileSdk=36，绕行用上面的 gradle 命令。
