package dev.aigw.core.usage

import dev.aigw.core.util.redactSecrets

/** 任务执行流水里一条记录的类型。 */
enum class TaskRunKind {
    /** 执行到哪一步：哪个账号、哪个动作、进行到第几项。 */
    PROGRESS,

    /** 发往上游的请求。 */
    REQUEST,

    /** 上游的响应（状态码 + 报文）。 */
    RESPONSE,

    /** 本次动作的最终结果。 */
    RESULT,
}

/** 任务执行流水的一条记录。 */
data class TaskRunEntry(
    val id: Long,
    val atMillis: Long,
    val kind: TaskRunKind,
    val text: String,
)

/** 面板一次取用的执行流水快照。 */
data class TaskRunSnapshot(
    /** 最新在前。 */
    val entries: List<TaskRunEntry> = emptyList(),
    val running: Boolean = false,
    /** 正在执行（或最近一次执行）的对象：`供应商/账号 · 动作`。 */
    val label: String = "",
) {
    companion object {
        val EMPTY = TaskRunSnapshot()
    }
}

/**
 * 任务执行的实时流水：动作发出的请求、上游的响应、以及执行到哪一步。
 *
 * 只在内存里留最近 [capacity] 条、不落盘。一次「一键完成成长任务」就有上百条请求原文，
 * 落盘既撑存储、也会把统一操作日志的环形缓冲挤爆；这里要的是「看着它跑」，重启即清。
 * 每轮执行开始时清空，所以面板只反映当前这一轮。
 */
class TaskRunLog(
    private val capacity: Int = 400,
    private val maxTextChars: Int = 600,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val entries = ArrayDeque<TaskRunEntry>()
    private var nextId = 1L
    private var running = false
    private var label = ""

    /** 开始一轮执行：清掉上一轮，并记下这一轮的对象（`供应商/账号 · 动作`）。 */
    @Synchronized
    fun start(label: String) {
        entries.clear()
        this.label = label
        running = true
        add(TaskRunKind.PROGRESS, "开始：$label")
    }

    /** 本轮结束（成功、失败或抛异常都走这里），[summary] 是给用户看的结果。 */
    @Synchronized
    fun finish(summary: String) {
        add(TaskRunKind.RESULT, summary)
        running = false
    }

    @Synchronized
    fun add(kind: TaskRunKind, text: String) {
        // 进面板前抹掉报文里的 token 字段：面板是给人看的，截图分享不该把凭证一起带走。
        val redacted = redactSecrets(text, maxTextChars)
        val body = if (text.length > maxTextChars) "$redacted…（已截断）" else redacted
        entries.addFirst(TaskRunEntry(nextId++, nowMillis(), kind, body))
        while (entries.size > capacity) entries.removeLast()
    }

    @Synchronized
    fun snapshot(): TaskRunSnapshot = TaskRunSnapshot(entries.toList(), running, label)

    @Synchronized
    fun clear() {
        entries.clear()
        label = ""
        running = false
    }
}
