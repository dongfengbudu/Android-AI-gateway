package dev.aigw.core.provider.trae

import dev.aigw.core.usage.TaskTrace
import dev.aigw.core.util.HttpSupport
import java.net.HttpURLConnection

/** 上游 HTTP 的小工具：只用到 Android 上同样存在的 `HttpURLConnection`。 */
internal object TraeHttp {

    const val CONNECT_TIMEOUT_MS = 30_000

    /** 短 JSON 请求的总超时兜底。 */
    const val READ_TIMEOUT_MS = 120_000

    /** SSE 流按「两次读到内容之间的空闲时长」超时，而不是整流时长。 */
    const val STREAM_IDLE_TIMEOUT_MS = 300_000

    /** 响应体读取上限，防止上游异常时撑爆内存。 */
    const val MAX_BODY_BYTES = HttpSupport.MAX_BODY_BYTES

    fun post(
        url: String,
        body: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        applyHeaders: (HttpURLConnection) -> Unit,
    ): HttpURLConnection =
        HttpSupport.openWithHeaders(url, "POST", connectTimeoutMs, readTimeoutMs, body, applyHeaders).also {
            // 任务执行流水的请求侧：只在 Trae 的 POST 出口采一次
            TaskTrace.request("POST ${url.removePrefix("https://")} $body")
        }

    /** 读取响应体（非 2xx 时自动落到 errorStream）。 */
    fun readBody(conn: HttpURLConnection, limit: Int = MAX_BODY_BYTES): String {
        val text = HttpSupport.readBody(conn, limit)
        // 任务执行流水的响应侧：状态码取不到时当 0，不能因为记录而弄挂请求
        TaskTrace.response("${runCatching { conn.responseCode }.getOrDefault(0)} $text")
        return text
    }
}

/** 把毫秒级时间戳归一化为 Unix 秒：1.7e12 量级判定为毫秒。 */
internal fun normalizeExpireAt(raw: Long): Long = if (raw > 1e12) raw / 1000 else raw

internal fun nowSeconds(): Long = System.currentTimeMillis() / 1000
