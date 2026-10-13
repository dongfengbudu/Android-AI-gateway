package dev.aigw.core.usage

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.core.store.KeyValueStore

/** 日志级别。 */
enum class LogLevel { INFO, WARN, ERROR }

/** 统一操作日志的一条记录。 */
data class OperationEntry(
    /** 单调递增 id，既作稳定 key 也用于恢复写入序。 */
    val id: Long,
    val atMillis: Long,
    /** 开放类别：内置 request/login，新功能可用任意新类别，界面自动出现对应筛选。 */
    val category: String,
    val level: LogLevel,
    val message: String,
)

/**
 * 所有非调用类操作的统一日志：环形缓冲 + 节流落盘 + 类别标记。
 *
 * 引擎、登录流程、未来的任何功能都写到这里；调用记录（含请求/响应原文）
 * 体量大、有独立保留策略，仍由 [CallLogStore] 单独存，界面层负责合并展示。
 */
class OperationLog(
    private val store: KeyValueStore,
    private val capacity: Int = 1000,
    private val persistIntervalMillis: Long = 2_000,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val storeKey: String = STORE_KEY,
) {
    private val entries = ArrayDeque<OperationEntry>()
    private var lastPersistAt = 0L
    private var nextId = 1L

    init {
        load()
    }

    @Synchronized
    fun info(category: String, message: String) = append(category, LogLevel.INFO, message)

    @Synchronized
    fun warn(category: String, message: String) = append(category, LogLevel.WARN, message)

    @Synchronized
    fun error(category: String, message: String) = append(category, LogLevel.ERROR, message)

    /** 最新在前；[category] 非空时只取该类别。 */
    @Synchronized
    fun entries(category: String? = null): List<OperationEntry> =
        if (category == null) entries.toList() else entries.filter { it.category == category }

    @Synchronized
    fun clear() {
        entries.clear()
        persist()
    }

    @Synchronized
    fun flush() = persist()

    private fun append(category: String, level: LogLevel, message: String) {
        val now = nowMillis()
        entries.addFirst(OperationEntry(nextId++, now, category, level, message))
        while (entries.size > capacity) entries.removeLast()
        if (now - lastPersistAt >= persistIntervalMillis) persist()
    }

    private fun persist() {
        lastPersistAt = nowMillis()
        val array = JsonArray()
        for (entry in entries) {
            array.add(JsonObject().apply {
                addProperty("id", entry.id)
                addProperty("at", entry.atMillis)
                addProperty("category", entry.category)
                addProperty("level", entry.level.name)
                addProperty("message", entry.message)
            })
        }
        store.write(storeKey, array.toString())
    }

    private fun load() {
        val raw = store.read(storeKey)
        if (raw == null) {
            migrateLegacy()
            return
        }
        loadEntries(raw)
        removeLegacyOrphans()
    }

    private fun loadEntries(raw: String) {
        val array = runCatching { JsonParser.parseString(raw).asJsonArray }.getOrNull() ?: return
        for (element in array) {
            val obj = runCatching { element.asJsonObject }.getOrNull() ?: continue
            val entry = OperationEntry(
                id = obj.get("id")?.asLong ?: 0L,
                atMillis = obj.get("at")?.asLong ?: 0L,
                category = obj.get("category")?.asString.orEmpty(),
                level = runCatching { LogLevel.valueOf(obj.get("level")?.asString.orEmpty()) }
                    .getOrDefault(LogLevel.INFO),
                message = obj.get("message")?.asString.orEmpty(),
            )
            entries.addLast(entry)
            if (entry.id >= nextId) nextId = entry.id + 1
        }
    }

    /**
     * 老版本把请求日志与登录日志分存在两个键里（均最新在前）；合并迁入统一键后删除旧键。
     * 若上次迁移在「已写入新键、未删旧键」之间被杀，这里不会重跑（避免重复迁入），
     * 孤儿旧键由 [removeLegacyOrphans] 清理。
     */
    private fun migrateLegacy() {
        val legacy = ArrayList<OperationEntry>()
        readLegacy(LEGACY_REQUESTS_KEY, CATEGORY_REQUEST, legacy)
        readLegacy(LEGACY_LOGIN_KEY, CATEGORY_LOGIN, legacy)
        if (legacy.isEmpty()) return
        legacy.sortByDescending { it.atMillis }
        var id = 1L
        for (entry in legacy) {
            entries.addLast(entry.copy(id = id++))
        }
        nextId = id
        persist()
        store.delete(LEGACY_REQUESTS_KEY)
        store.delete(LEGACY_LOGIN_KEY)
    }

    /** 统一键已存在时，残留的旧键只是迁移中断留下的孤儿（此后无人再写），直接删除。 */
    private fun removeLegacyOrphans() {
        store.delete(LEGACY_REQUESTS_KEY)
        store.delete(LEGACY_LOGIN_KEY)
    }

    private fun readLegacy(key: String, category: String, into: MutableList<OperationEntry>) {
        val raw = store.read(key) ?: return
        val array = runCatching { JsonParser.parseString(raw).asJsonArray }.getOrNull() ?: return
        for (element in array) {
            val obj = runCatching { element.asJsonObject }.getOrNull() ?: continue
            into.add(
                OperationEntry(
                    id = 0L,
                    atMillis = obj.get("at")?.asLong ?: 0L,
                    category = category,
                    level = runCatching { LogLevel.valueOf(obj.get("level")?.asString.orEmpty()) }
                        .getOrDefault(LogLevel.INFO),
                    message = obj.get("text")?.asString.orEmpty(),
                ),
            )
        }
    }

    companion object {
        const val STORE_KEY = "logs/operations.json"
        private const val LEGACY_REQUESTS_KEY = "logs/requests.json"
        private const val LEGACY_LOGIN_KEY = "logs/login.json"

        /** 网关与上游的交互流水。 */
        const val CATEGORY_REQUEST = "request"

        /** 各供应商登录过程的端点、状态码与响应摘录。 */
        const val CATEGORY_LOGIN = "login"

        /** 账号动作（签到、任务领取、活跃上报等）的执行结果。 */
        const val CATEGORY_TASK = "task"
    }
}
