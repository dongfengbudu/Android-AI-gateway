package dev.aigw.core.usage

/**
 * 任务执行期间把 provider 内部发出的请求与响应接出来。
 *
 * 动作在调用方线程上同步跑完，所以用 ThreadLocal 传递采集口：只有正在执行动作的那条线程会被采集，
 * 同进程里并发的代理请求不会混进来。不在执行动作时 [emit] 是空操作，provider 侧不用判断。
 */
object TaskTrace {
    private val sink = ThreadLocal<(TaskRunKind, String) -> Unit>()

    /** 在 [block] 执行期间把 [TaskTrace] 的输出交给 [sink]；结束后恢复上一层（支持嵌套）。 */
    fun <T> collect(sink: (TaskRunKind, String) -> Unit, block: () -> T): T {
        val previous = this.sink.get()
        this.sink.set(sink)
        try {
            return block()
        } finally {
            if (previous == null) this.sink.remove() else this.sink.set(previous)
        }
    }

    fun progress(text: String) = emit(TaskRunKind.PROGRESS, text)

    fun request(text: String) = emit(TaskRunKind.REQUEST, text)

    fun response(text: String) = emit(TaskRunKind.RESPONSE, text)

    private fun emit(kind: TaskRunKind, text: String) {
        sink.get()?.invoke(kind, text)
    }
}
