# 进度

## 当前状态
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
