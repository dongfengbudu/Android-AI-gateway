plugins {
    id("com.android.application") version "8.9.2" apply false
    // Kotlin 2.2.x：可读取 miuix 0.8.8（Kotlin 2.3.20 编译，元数据 2.3.0）的字节码；
    // 2.3+ 的 KGP 需要 Gradle 9，超出当前工具链（Gradle 8.14.2）。
    id("org.jetbrains.kotlin.jvm") version "2.2.21" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}
