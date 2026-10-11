# 进度

## 当前状态
代码审查修复（2026-10-11）已完成：清理误提交到仓库根目录的构建产物并补 `.gitignore`、
`ARCHITECTURE.md` 与代码同步（START_NOT_STICKY、新增模块补登）、网关启停入口聚拢到
`GatewayService.start/stop`、首页 Tile 边框顺序修复、CodeBuddy 登录/账号卡片布局重排、
`PageScaffold` 副标题恢复显示（miuix 0.8.8 无 TopAppBar.subtitle 参数）。

## 构建环境（aarch64 PRoot 容器）
- Gradle 8.14.2 缓存已就绪；JDK: /opt/taixu/toolchains/android/jdk；AAPT2 ARM64 在 build-tools/35.0.0。
- `gradle.properties` 需含 `android.builder.sdkDownload=false`；AAPT2 override 参数：`-Pandroid.aapt2FromMavenOverride=$TAIXU_AAPT2_PATH`。
- 可用构建命令：`TAIXU_BUILD_ENGINE=1 JAVA_HOME=/opt/taixu/toolchains/android/jdk ANDROID_HOME=/opt/android-sdk /opt/taixu/bin/gradle :app:assembleDebug --no-daemon -Pandroid.builder.sdkDownload=false -Pandroid.aapt2FromMavenOverride=/opt/android-sdk/build-tools/35.0.0/aapt2`
- `taixu-build android` 脚本在 `/opt/taixu/scripts/taixu-build.sh:230` 硬编码 compileSdk 34 上限，本项目 compileSdk=36，绕行用上面的 gradle 命令。
