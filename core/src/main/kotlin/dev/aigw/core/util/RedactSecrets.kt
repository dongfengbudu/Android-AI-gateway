package dev.aigw.core.util

/**
 * 把报文摘录写进日志前抹掉凭证字段（accessToken / refreshToken / session / token / secret）。
 *
 * 登录日志会落盘并长期保留，摘录里带着真实 token 等于把凭证复制了一份到日志存储，
 * 所以进日志前一律替换成 `***`。只匹配 JSON 字符串值的场景，够用于报文摘录。
 */
private val SECRET_FIELD = Regex(
    pattern = """("(?:access[_-]?token|refresh[_-]?token|session[_-]?\w*|token|secret[_-]?\w*|client[_-]?secret|api[_-]?key)"\s*:\s*")([^"]*)(")""",
    options = setOf(RegexOption.IGNORE_CASE),
)

/** 返回脱敏后的文本；超过 [limit] 的部分截断，避免单条日志占满环形缓冲。 */
fun redactSecrets(text: String, limit: Int = 300): String =
    SECRET_FIELD.replace(text.take(limit), "$1***$3")
