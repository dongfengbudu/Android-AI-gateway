package dev.aigw.core.usage

import dev.aigw.core.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OperationLogTest {

    @Test
    fun `写入后最新在前且类别过滤生效`() {
        var now = 1000L
        val log = OperationLog(InMemoryKeyValueStore(), nowMillis = { now })

        log.info(OperationLog.CATEGORY_REQUEST, "收到请求")
        now += 10
        log.warn(OperationLog.CATEGORY_LOGIN, "轮询失败")
        now += 10
        log.error(OperationLog.CATEGORY_REQUEST, "上游 500")

        val all = log.entries()
        assertEquals(3, all.size)
        assertEquals("上游 500", all[0].message)
        assertEquals(LogLevel.ERROR, all[0].level)
        assertEquals("收到请求", all[2].message)

        val login = log.entries(OperationLog.CATEGORY_LOGIN)
        assertEquals(1, login.size)
        assertEquals(LogLevel.WARN, login[0].level)
    }

    @Test
    fun `落盘后新实例能完整读回`() {
        val store = InMemoryKeyValueStore()
        val log = OperationLog(store, persistIntervalMillis = 0, nowMillis = { 42L })
        log.info(OperationLog.CATEGORY_REQUEST, "第一条")
        log.warn(OperationLog.CATEGORY_LOGIN, "第二条")
        log.flush()

        val reloaded = OperationLog(store, nowMillis = { 42L })
        val entries = reloaded.entries()
        assertEquals(2, entries.size)
        assertEquals("第二条", entries[0].message)
        assertEquals(LogLevel.WARN, entries[0].level)
        assertEquals(OperationLog.CATEGORY_LOGIN, entries[0].category)
        assertEquals(42L, entries[0].atMillis)
        assertTrue(entries[0].id > entries[1].id, "id 应随时间单调递增")

        // 回读后继续追加，id 不与旧记录冲突
        reloaded.info(OperationLog.CATEGORY_REQUEST, "第三条")
        assertEquals(3, reloaded.entries().size)
    }

    @Test
    fun `超过容量时淘汰最旧记录`() {
        val log = OperationLog(InMemoryKeyValueStore(), capacity = 3, nowMillis = { 0L })
        repeat(5) { log.info(OperationLog.CATEGORY_REQUEST, "第 $it 条") }

        val entries = log.entries()
        assertEquals(3, entries.size)
        assertEquals("第 4 条", entries[0].message)
        assertEquals("第 2 条", entries[2].message)
    }

    @Test
    fun `首次加载时迁移旧的请求与登录日志键并删除它们`() {
        val store = InMemoryKeyValueStore()
        val legacyRequests = """
            [{"at":100,"level":"INFO","text":"旧请求日志"},
             {"at":300,"level":"ERROR","text":"旧错误日志"}]
        """.trimIndent()
        val legacyLogin = """[{"at":200,"level":"WARN","text":"旧登录日志"}]"""
        store.write("logs/requests.json", legacyRequests)
        store.write("logs/login.json", legacyLogin)

        val log = OperationLog(store)

        val entries = log.entries()
        assertEquals(3, entries.size)
        assertEquals(listOf("旧错误日志", "旧登录日志", "旧请求日志"), entries.map { it.message })
        assertEquals(listOf(OperationLog.CATEGORY_REQUEST, OperationLog.CATEGORY_LOGIN, OperationLog.CATEGORY_REQUEST), entries.map { it.category })
        assertEquals(LogLevel.ERROR, entries[0].level)

        assertNull(store.read("logs/requests.json"), "迁移后旧键应被删除")
        assertNull(store.read("logs/login.json"), "迁移后旧键应被删除")
        assertTrue(store.read(OperationLog.STORE_KEY)?.contains("旧登录日志") == true)
    }

    @Test
    fun `clear 清空内存与磁盘`() {
        val store = InMemoryKeyValueStore()
        val log = OperationLog(store, persistIntervalMillis = 0, nowMillis = { 0L })
        log.info(OperationLog.CATEGORY_REQUEST, "将被清空")
        log.clear()

        assertTrue(log.entries().isEmpty())
        assertEquals("[]", store.read(OperationLog.STORE_KEY))
    }

    @Test
    fun `迁移中断残留的旧键在下次启动时被清理且不重复迁入`() {
        val store = InMemoryKeyValueStore()
        // 模拟上次迁移已完成（新键已写入）但旧键未及删除
        val log = OperationLog(store, persistIntervalMillis = 0, nowMillis = { 0L })
        log.info(OperationLog.CATEGORY_REQUEST, "迁移后的新记录")
        log.flush()
        store.write("logs/requests.json", """[{"at":1,"level":"INFO","text":"孤儿旧记录"}]""")

        val reloaded = OperationLog(store)

        assertEquals(1, reloaded.entries().size, "孤儿旧键不应被重复迁入")
        assertEquals("迁移后的新记录", reloaded.entries()[0].message)
        assertNull(store.read("logs/requests.json"), "残留旧键应被删除")
        assertNull(store.read("logs/login.json"))
    }
}
