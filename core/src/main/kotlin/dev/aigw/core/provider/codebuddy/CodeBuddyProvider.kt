package dev.aigw.core.provider.codebuddy

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.core.provider.ACTION_ACTIVITY
import dev.aigw.core.provider.ACTION_AUTO_TASKS
import dev.aigw.core.provider.ACTION_BLACKCAT
import dev.aigw.core.provider.ACTION_CHECKIN
import dev.aigw.core.provider.ACTION_TASKS
import dev.aigw.core.provider.AggregatedChatCall
import dev.aigw.core.provider.AuthKind
import dev.aigw.core.provider.ChatCall
import dev.aigw.core.provider.CreditInfo
import dev.aigw.core.provider.DeviceAuthPoll
import dev.aigw.core.util.redactSecrets
import dev.aigw.core.provider.DeviceAuthTicket
import dev.aigw.core.provider.DeviceCodeSupport
import dev.aigw.core.provider.ErrorKind
import dev.aigw.core.provider.FailedChatCall
import dev.aigw.core.provider.OpenAiSseAggregator
import dev.aigw.core.provider.Provider
import dev.aigw.core.provider.ProviderAccount
import dev.aigw.core.provider.ProviderActionResult
import dev.aigw.core.provider.ProviderCapability
import dev.aigw.core.provider.ProviderModel
import dev.aigw.core.provider.ProviderModelCatalogView
import dev.aigw.core.provider.ProviderTask
import dev.aigw.core.provider.ProviderTaskListView
import dev.aigw.core.provider.QuotaPack
import dev.aigw.core.provider.StreamingChatCall
import dev.aigw.core.provider.UpstreamError
import dev.aigw.core.util.arrayOrNull
import dev.aigw.core.util.objOrNull
import dev.aigw.core.util.str
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 腾讯 WorkBuddy / CodeBuddy（`copilot.tencent.com`，国际版 `workbuddy.ai`）。
 *
 * 登录是设备授权：`POST /v2/plugin/auth/state?platform=CLI` 拿授权链接，
 * 用户在 WebView 里扫码/登录后，`GET /v2/plugin/auth/token?state=…` 轮询拿 token。
 * 对话走 `/v2/chat/completions`（OpenAI 兼容，但**上游拒绝非流式**，必须强制 `stream:true`）。
 */
class CodeBuddyProvider(
    private val region: () -> String = { REGION_CN },
    private val nowMillis: () -> Long = System::currentTimeMillis,
    /** 登录过程上报（请求/轮询的 URL、状态与响应摘录），由网关接住写进登录日志。 */
    private val onLoginLog: (String) -> Unit = {},
) : Provider, DeviceCodeSupport, dev.aigw.core.provider.RegionAwareSupport {

    override val id: String = ID
    override val displayName: String = "WorkBuddy"
    override val authKind: AuthKind = AuthKind.DEVICE_CODE
    override val capabilities: Set<ProviderCapability> =
        setOf(ProviderCapability.CREDIT_REFRESH, ProviderCapability.CHECKIN, ProviderCapability.TASKS)

    // ------------------------------------------------------------------ 模型

    /**
     * 模型目录**只在登录后**向官方接口拉取。
     *
     * 没有账号时不返回任何模型：内置快照只是「拉取失败」时的兜底，
     * 把它当成可用模型列出来，用户会以为能直接调用，实际一发请求就 401。
     */
    override fun listModels(account: ProviderAccount?): ProviderModelCatalogView {
        // 没有账号不算「失败」——登录前本就不该有模型，不能报红吓人。
        val credential = account?.let { parse(it) }
            ?: return ProviderModelCatalogView(emptyList(), fromFallback = false, error = "")
        val fallback = fallbackModels(regionOf(credential))
        return try {
            val models = fetchModels(credential)
            if (models.isEmpty()) {
                ProviderModelCatalogView(fallback, fromFallback = true, error = "上游返回空模型列表")
            } else {
                ProviderModelCatalogView(models, fromFallback = false, error = "")
            }
        } catch (e: Exception) {
            ProviderModelCatalogView(fallback, fromFallback = true, error = e.message ?: "拉取模型失败")
        }
    }

    /**
     * 兜底模型清单按区域区分。
     *
     * 打包的官方目录是**国际版**的（`codebuddy-international-models.json`）——
     * 里面的 `credits` 反映国际版定价，与国内版**不一致**。
     * 典型例子：`deepseek-v4.1-flash` 在国际版是无 `credits` 的（限时免费），
     * 国内版却是有倍率的；直接拿国际目录填国内账号，就会显示成「无限免费」。
     *
     * 所以国内版只用[极简清单][MINIMAL_MODELS]占位，真实倍率一律以上游实时返回为准。
     */
    private fun fallbackModels(region: String): List<ProviderModel> = if (region == REGION_GLOBAL) {
        FALLBACK_MODELS_INTL
    } else {
        FALLBACK_MODELS_CN
    }

    override fun resolveModel(requested: String): String {
        val model = requested.trim()
        // 默认走官方的 Auto（id = default-model）。不要用 FALLBACK_MODELS.first()——
        // 那份列表的顺序会随数据源变化，一旦首位不是 Auto 就会把请求打到别的模型上。
        if (model.isEmpty() || model == "auto") return DEFAULT_MODEL_ID
        return model
    }

    /**
     * 上游域名后缀（代理分流的判定依据）。
     *
     * 注意成长中心分为**插件链路**（`copilot.tencent.com` / `codebuddy.cn`）与
     * **网页链路**（`workbuddy.cn`），两条路用的是不同身份头，域名都要列上，
     * 否则代理开关对网页链路不生效。
     */
    override fun hosts(): List<String> =
        listOf("copilot.tencent.com", "codebuddy.cn", "workbuddy.cn", "codebuddy.ai", "workbuddy.ai")

    // ------------------------------------------------------------------ 对话

    override fun openChat(account: ProviderAccount, openAiBody: String): ChatCall {
        val credential = parse(account) ?: return FailedChatCall(401, "凭证无法解析")
        val streaming = isStreaming(openAiBody)
        val model = resolveModel(requestedModel(openAiBody))
        val prepared = prepareCodeBuddyBody(openAiBody)

        val conn = try {
            open("${chatBase(credential)}$PATH_CHAT", credential, prepared, chatHeaders(credential))
        } catch (e: Exception) {
            return FailedChatCall(0, e.message ?: "连接失败")
        }
        val status = try {
            conn.responseCode
        } catch (e: Exception) {
            conn.disconnect()
            return FailedChatCall(0, e.message ?: "连接失败")
        }
        if (status !in 200..299) {
            val errorBody = runCatching { readLimited(conn.errorStream) }.getOrDefault("")
            conn.disconnect()
            return FailedChatCall(status, errorBody)
        }

        val contentType = conn.contentType.orEmpty()
        val stream = conn.inputStream
        if (contentType.contains("event-stream", ignoreCase = true)) {
            if (streaming) {
                return StreamingChatCall(status, stream) { conn.disconnect() }
            }
            val aggregated = try {
                OpenAiSseAggregator.aggregate(stream, model)
            } finally {
                conn.disconnect()
            }
            return AggregatedChatCall(status, aggregated)
        }

        val text = runCatching { stream.use { readLimited(it) } }.getOrDefault("")
        conn.disconnect()
        return AggregatedChatCall(status, text)
    }

    override fun classify(status: Int, body: String): UpstreamError {
        val message = extractMessage(body)
        val kind = when {
            status == 402 -> ErrorKind.QUOTA
            status == 429 -> ErrorKind.SOFT_RATE
            status == 404 -> ErrorKind.NOT_FOUND
            status in 500..599 -> ErrorKind.SERVER
            matchesQuota(body) -> ErrorKind.QUOTA
            matchesSessionDead(body) -> ErrorKind.SESSION_DEAD
            status in 400..499 -> ErrorKind.CLIENT
            else -> ErrorKind.CLIENT
        }
        return UpstreamError(kind, message.ifEmpty { "上游 HTTP $status" })
    }

    override fun creditInfo(account: ProviderAccount): CreditInfo? {
        val credential = parse(account) ?: return null
        return try {
            val (status, text) = postJson(
                "${billingBase(credential)}$PATH_USER_RESOURCE",
                credential,
                userResourceBody(),
                billingHeaders(credential),
            )
            if (status !in 200..299) {
                return CreditInfo(0, known = false, detail = "上游 HTTP $status" + describeBody(text))
            }
            val data = envelopeData(text) ?: return CreditInfo(0, known = false, detail = "响应缺少 data")
            val accounts = data.objOrNull("Response")?.objOrNull("Data")?.arrayOrNull("Accounts")
            var remain = 0L
            accounts?.forEach { element ->
                val item = runCatching { element.asJsonObject }.getOrNull() ?: return@forEach
                val size = item.get("CycleCapacitySize")?.asLong ?: 0L
                val cycleRemain = item.get("CycleCapacityRemain")?.asLong ?: 0L
                val value = if (size > 0 || cycleRemain > 0) {
                    cycleRemain
                } else {
                    item.get("CapacityRemain")?.asLong ?: 0L
                }
                if (value > 0) remain += value
            }
            CreditInfo(remain, known = true, detail = "周期剩余 $remain")
        } catch (e: Exception) {
            CreditInfo(0, known = false, detail = e.message ?: "查询额度失败")
        }
    }

    override fun performAction(
        account: ProviderAccount,
        action: String,
        payload: JsonObject,
    ): ProviderActionResult {
        // 国际版（workbuddy.ai）没有签到制度与成长中心，这两个操作只对国内版有意义
        val credential = parse(account)
        if (credential != null && regionOf(credential) == REGION_GLOBAL) {
            return when (action) {
                ACTION_CHECKIN, ACTION_TASKS, ACTION_ACTIVITY, ACTION_BLACKCAT, ACTION_AUTO_TASKS ->
                    ProviderActionResult.failure("国际版没有签到与成长中心")
                else -> ProviderActionResult.unsupported(action)
            }
        }
        return when (action) {
            ACTION_CHECKIN -> checkin(account)
            ACTION_TASKS -> growth(account)
            ACTION_ACTIVITY -> reportActivity(account)
            ACTION_BLACKCAT -> blackCat(account)
            ACTION_AUTO_TASKS -> autoTasks(account, payload)
            else -> ProviderActionResult.unsupported(action)
        }
    }

    /**
     * 成长中心一键领取（WorkBuddy 的「任务」就在这里）。
     *
     * 顺序按官方客户端的行为链：
     *   签到日历 → 新手礼包 / 补偿 → 领养 Buddy → 连登档位兑奖 → 抽奖
     *   → 领旅行礼物 / 派 Buddy 出发 → 领任务 → 开盲盒 → 开学季
     *
     * 各步单独容错：某一步的业务拒绝（活动未开、无配额、条件未达）不该让其它步失败。
     */
    private fun growth(account: ProviderAccount): ProviderActionResult {
        val credential = parse(account) ?: return ProviderActionResult.failure("凭证无法解析")
        val base = "${chatBase(credential)}$PATH_GROWTH"
        val headers = billingHeaders(credential)
        val parts = ArrayList<String>()

        claimGiftAndCompensation(credential, base, headers, parts)
        adoptBuddy(credential, base, headers, parts)
        redeemStreak(credential, base, headers, parts)
        drawLottery(credential, base, headers, parts)
        claimTravel(credential, base, headers, parts)
        claimTasks(credential, base, headers, parts)
        openBlindBox(credential, base, headers, parts)
        claimSchoolSeason(credential, base, headers, parts)

        return ProviderActionResult.success(
            if (parts.isEmpty()) "成长中心没有可领取的内容" else parts.joinToString("；"),
        )
    }

    /** 新手礼包与补偿：两个独立的一次性领取，各自容错。 */
    private fun claimGiftAndCompensation(
        credential: Credential,
        base: String,
        headers: Map<String, String>,
        parts: MutableList<String>,
    ) {
        val billingRoot = chatBase(credential)
        for ((path, label) in listOf(
            "/billing/meter/claim-gift" to "新手礼包",
            "/billing/meter/claim-compensation" to "补偿",
        )) {
            runCatching {
                val (status, body) = postJson("${billingRoot}$path", credential, "{}", headers)
                if (status !in 200..299) return@runCatching
                val credit = jsonOf(body)?.firstNumber("credit", "reward_credit", "amount")
                if (credit != null) parts.add("$label +$credit")
            }
        }
    }

    /**
     * 领养 Buddy：先报「完成任务」，再同意协议，最后触发领养。
     *
     * 三步有先后依赖，任一步失败就停（上游会以 `first_buddy task not completed yet` 拒绝）。
     * 注意路径**没有 /v2 前缀**（与其它成长接口不同）。
     */
    private fun adoptBuddy(
        credential: Credential,
        base: String,
        headers: Map<String, String>,
        parts: MutableList<String>,
    ) {
        val root = chatBase(credential)
        runCatching {
            val (infoStatus, infoBody) = getJson("$root$PATH_GROWTH/buddy/info", headers)
            if (infoStatus !in 200..299) return@runCatching
            val adopted = jsonOf(infoBody)?.let { obj ->
                val buddy = obj.objOrNull("buddy") ?: obj.objOrNull("data")?.objOrNull("buddy")
                buddy != null && buddy.entrySet().isNotEmpty()
            } ?: false
            if (adopted) return@runCatching

            val agree = "${chatBase(credential)}/activity/growth/buddy/agreement"
            runCatching { postJson(agree, credential, """{"agree":true}""", headers) }
            val first = "${chatBase(credential)}/activity/growth/buddy/first"
            val (status, body) = postJson(first, credential, "{}", headers)
            if (status in 200..299) {
                val credit = jsonOf(body)?.firstNumber("credit", "reward_credit")
                parts.add(if (credit != null) "领养 Buddy +$credit" else "领养 Buddy")
            }
        }
    }

    /**
     * 连登档位兑奖：查连登状态，对**已达标但未领取**的档位调 redeem。
     *
     * 档位固定 `7d` / `14d` / `28d`；每次请求要带一个全新的小写 UUIDv4 作为幂等键。
     */
    private fun redeemStreak(
        credential: Credential,
        base: String,
        headers: Map<String, String>,
        parts: MutableList<String>,
    ) {
        runCatching {
            val root = chatBase(credential)
            val (status, body) = getJson("$root/activity/growth/streak", headers)
            if (status !in 200..299) return@runCatching
            val obj = jsonOf(body) ?: return@runCatching
            val entitle = obj.objOrNull("redemption_status") ?: obj.objOrNull("data")?.objOrNull("redemption_status")
            for (tier in listOf("7d", "14d", "28d")) {
                val state = entitle?.str("tier_${tier}_status").orEmpty().lowercase()
                // 已领过 / 未达标都跳过；只处理明确可领的
                if (state.isEmpty() || state == "claimed" || state == "redeemed" || state == "false") continue
                if (state != "true" && state != "available" && state != "claimable") continue
                val (rs, _) = postJson("$root/activity/growth/redeem", credential, """{"tier":"$tier","client_token":"${uuid4()}"}""", headers)
                if (rs in 200..299) parts.add("连登 $tier 兑奖")
            }
        }
    }

    /** 抽奖：先查次数，有剩余就抽（每次全新 client_token）。 */
    private fun drawLottery(
        credential: Credential,
        base: String,
        headers: Map<String, String>,
        parts: MutableList<String>,
    ) {
        runCatching {
            val root = chatBase(credential)
            val (status, body) = getJson("$root/activity/growth/lottery/summary", headers)
            if (status !in 200..299) return@runCatching
            val chances = jsonOf(body)?.firstNumber("chances", "chance", "count") ?: return@runCatching
            if (chances <= 0) return@runCatching
            val draws = minOf(chances.toInt(), MAX_LOTTERY_DRAWS)
            repeat(draws) {
                val (rs, rb) = postJson(
                    "$root/activity/growth/lottery/draw",
                    credential,
                    """{"client_token":"${uuid4()}"}""",
                    headers,
                )
                if (rs in 200..299) {
                    val prize = jsonOf(rb)?.firstString("prize_code").orEmpty()
                    parts.add(if (prize.isEmpty()) "抽奖" else "抽奖 $prize")
                }
            }
        }
    }

    /** 开学季：活动在期时领任务奖、再用获得的机会抽转盘。 */
    private fun claimSchoolSeason(
        credential: Credential,
        base: String,
        headers: Map<String, String>,
        parts: MutableList<String>,
    ) {
        runCatching {
            val root = chatBase(credential)
            val (status, body) = getJson("$root/portal/activity/school/tasks", headers)
            if (status !in 200..299) return@runCatching
            val obj = jsonOf(body) ?: return@runCatching
            val payload = obj.objOrNull("data") ?: obj
            if (payload.get("in_period")?.asBoolean != true) return@runCatching
            payload.arrayOrNull("tasks")?.forEach { element ->
                val task = runCatching { element.asJsonObject }.getOrNull() ?: return@forEach
                val code = task.firstString("task_code")
                if (code.isEmpty()) return@forEach
                val done = task.objOrNull("progress")?.let { p ->
                    val cur = p.firstNumber("current", "progress") ?: 0.0
                    val target = p.firstNumber("target", "target_count") ?: 1.0
                    cur >= target && target > 0
                } ?: false
                val st = task.firstString("status").lowercase()
                if (!done || st == "claimed" || st == "received") return@forEach
                val (rs, _) = postJson("$root/portal/activity/school/tasks/$code/claim", credential, "{}", headers)
                if (rs in 200..299) parts.add("开学季任务 $code")
            }
            // 有抽奖机会就转一次
            val (cs, cb) = getJson("$root/portal/activity/school/config", headers)
            if (cs in 200..299) {
                val balance = jsonOf(cb)?.let { o ->
                    (o.objOrNull("chance") ?: o.objOrNull("data")?.objOrNull("chance"))?.let {
                        it.firstNumber("balance", "count")
                    }
                } ?: 0.0
                if (balance > 0) {
                    val (ws, wb) = postJson(
                        "$root/portal/activity/school/wheel/draw",
                        credential,
                        """{"draw_uuid":"${uuid4()}"}""",
                        headers,
                    )
                    if (ws in 200..299) {
                        val prize = jsonOf(wb)?.firstString("prize_code").orEmpty()
                        parts.add(if (prize.isEmpty()) "开学季转盘" else "开学季转盘 $prize")
                    }
                }
            }
        }
    }

    /** 旅行：已到达先领礼物，空闲则派 Buddy 出发。 */
    private fun claimTravel(
        credential: Credential,
        base: String,
        headers: Map<String, String>,
        parts: MutableList<String>,
    ) {
        runCatching {
            val (status, body) = getJson("$base/buddy/travel/status", headers)
            if (status !in 200..299) return@runCatching
            val obj = jsonOf(body) ?: return@runCatching
            val state = obj.firstString("state")
            val dailyLimit = obj.get("daily_limit_reached")?.asBoolean ?: false
            when {
                state == "arrived" -> {
                    val recordId = obj.firstString("record_id")
                    if (recordId.isEmpty()) return@runCatching
                    val payload = JsonObject().apply { addProperty("record_id", recordId) }.toString()
                    val (code, text) = postJson("$base/buddy/travel/claim", credential, payload, headers)
                    if (code in 200..299) {
                        val credit = jsonOf(text)?.firstLong("reward_credit") ?: 0L
                        parts.add(if (credit > 0) "旅行礼物 +$credit" else "已领旅行礼物")
                    }
                }
                state == "idle" && !dailyLimit -> {
                    val (cfgCode, cfgBody) = getJson("$base/buddy/travel/config", headers)
                    if (cfgCode !in 200..299) return@runCatching
                    val location = jsonOf(cfgBody)?.arrayOrNull("locations")?.firstOrNull()
                        ?.let { runCatching { it.asJsonObject }.getOrNull() } ?: return@runCatching
                    val locationId = location.firstString("id")
                    if (locationId.isEmpty()) return@runCatching
                    val payload = JsonObject().apply { addProperty("location_id", locationId) }.toString()
                    val (code, text) = postJson("$base/buddy/travel/depart", credential, payload, headers)
                    if (code in 200..299) {
                        val name = jsonOf(text)?.objOrNull("location")?.firstString("name").orEmpty()
                        parts.add(if (name.isEmpty()) "已派 Buddy 出发" else "派 Buddy 去$name")
                    }
                }
            }
        }
    }

    /**
     * 任务：未领取的先接单（进度从接单那刻才开始计），已接单且达标的领奖。
     */
    private fun claimTasks(
        credential: Credential,
        base: String,
        headers: Map<String, String>,
        parts: MutableList<String>,
    ) {
        runCatching {
            val (status, body) = getJson("$base/tasks", headers)
            if (status !in 200..299) return@runCatching
            val tasks = jsonOf(body)?.arrayOrNull("tasks") ?: return@runCatching
            for (element in tasks) {
                val task = runCatching { element.asJsonObject }.getOrNull() ?: continue
                if (task.get("locked")?.asBoolean == true) continue
                val acceptStatus = task.firstString("accept_status")
                if (acceptStatus == "claimed") continue

                val progress = task.objOrNull("progress")
                val target = progress?.firstLong("target")?.takeIf { it > 0 } ?: 1L
                val current = progress?.firstLong("current") ?: 0L
                val done = current >= target
                // 已接单但还没做完：等用户完成，别重复提交
                if (!done && acceptStatus.isNotEmpty()) continue
                val claiming = done && acceptStatus.isNotEmpty()

                val taskCode = task.firstString("task_code")
                if (taskCode.isEmpty()) continue
                // 领奖与接单是两个端点：接单走 CLI 域 tasks/accept，领奖走 Web 域 tasks/{code}/claim
                if (claiming) {
                    val outcome = claimReward(credential, taskCode)
                    if (outcome.status !in 200..299) continue
                    val title = task.firstString("title").ifEmpty { taskCode }
                    parts.add(
                        if (outcome.credit > 0) "领任务奖「$title」+${outcome.credit}" else "已领「$title」",
                    )
                } else {
                    val payload = JsonObject().apply { addProperty("task_code", taskCode) }.toString()
                    val (code, _) = postJson("$base/tasks/accept", credential, payload, headers)
                    if (code !in 200..299) continue
                    val title = task.firstString("title").ifEmpty { taskCode }
                    parts.add("领取任务「$title」")
                }
            }
        }
    }

    /** 领奖结果：HTTP 状态 + 到账奖励。 */
    private data class ClaimOutcome(
        val status: Int,
        val credit: Long,
        val energy: Long,
        val message: String,
    )

    /**
     * 任务领奖：POST {webBase}/activity/growth/tasks/{code}/claim（task_code 在路径里，无请求体）。
     *
     * CLI 域（copilot.tencent.com）的同名路径不存在，会一直返回 400「task not completed」。
     */
    private fun claimReward(credential: Credential, taskCode: String): ClaimOutcome {
        val escaped = java.net.URLEncoder.encode(taskCode, "UTF-8")
        val (status, text) = postJson(
            "${webBase(credential)}/activity/growth/tasks/$escaped/claim",
            credential,
            null,
            webHeaders(credential),
        )
        return claimOutcome(status, text)
    }

    /**
     * 小程序口径领奖：chat 域同名路径 + mp 头；该域返回 400 时降级 Web 域。
     */
    private fun claimRewardMp(credential: Credential, taskCode: String): ClaimOutcome {
        val escaped = java.net.URLEncoder.encode(taskCode, "UTF-8")
        val (status, text) = postJson(
            "${chatBase(credential)}/activity/growth/tasks/$escaped/claim",
            credential,
            null,
            mpHeaders(credential),
        )
        if (status == 400) return claimReward(credential, taskCode)
        return claimOutcome(status, text)
    }

    /** 解析领奖响应：奖励在 `data.credit`／`data.energy`，重复领取时为 0。 */
    private fun claimOutcome(status: Int, body: String): ClaimOutcome {
        val data = envelopeData(body)
        return ClaimOutcome(
            status = status,
            credit = data?.firstLong("credit") ?: 0L,
            energy = data?.firstLong("energy") ?: 0L,
            message = extractMessage(body),
        )
    }

    /** 盲盒：有配额才开。 */
    private fun openBlindBox(
        credential: Credential,
        base: String,
        headers: Map<String, String>,
        parts: MutableList<String>,
    ) {
        runCatching {
            val (status, body) = getJson("$base/buddy/quota", headers)
            if (status !in 200..299) return@runCatching
            val quota = jsonOf(body) ?: return@runCatching
            val remain = quota.firstLong("remaining", "quota", "count", "balance", "left")
            if (remain <= 0) return@runCatching
            val (code, text) = postJson("$base/buddy/open", credential, "{}", headers)
            if (code !in 200..299) return@runCatching
            val credit = jsonOf(text)?.firstLong("credit", "reward_credit") ?: 0L
            parts.add(if (credit > 0) "开盲盒 +$credit" else "已开盲盒")
        }
    }

    /**
     * 把上游 `tasks[]` 里的一条映射成 [ProviderTask]。
     *
     * 进度既可能在顶层 `current`/`target`，也可能藏在 `progress` 对象里，后者优先。
     */
    private fun mapTask(element: JsonElement): ProviderTask? {
        val task = runCatching { element.asJsonObject }.getOrNull() ?: return null
        val code = task.firstString("task_code")
        if (code.isEmpty()) return null
        var current = task.firstLong("current")
        var target = task.firstLong("target")
        task.objOrNull("progress")?.let { progress ->
            val progressCurrent = progress.firstLong("current")
            val progressTarget = progress.firstLong("target")
            if (progressTarget > 0 || progressCurrent > 0) {
                current = progressCurrent
                target = progressTarget
            }
        }
        val acceptStatus = task.firstString("accept_status")
        return ProviderTask(
            code = code,
            title = task.firstString("title").ifEmpty { code },
            description = task.firstString("description"),
            current = current,
            target = target,
            status = acceptStatus,
            locked = task.get("locked")?.asBoolean ?: false,
            claimed = acceptStatus == "claimed",
            rewardCredit = task.firstLong("reward_credit"),
        )
    }

    /**
     * 成长任务明细：读 `/v2/activity/growth/tasks`，把 `tasks[]` 映射成 [ProviderTask]。
     *
     * 只读，不触发任何领取动作，供任务中心展示「哪些做了、哪些没做」。
     * 国际版（workbuddy.ai）没有成长中心，直接返回空列表。
     */
    override fun taskList(account: ProviderAccount): ProviderTaskListView? {
        val credential = parse(account) ?: return ProviderTaskListView(emptyList(), error = "凭证无法解析")
        if (regionOf(credential) == REGION_GLOBAL) return ProviderTaskListView(emptyList())
        return try {
            val (status, body) = getJson("${chatBase(credential)}$PATH_GROWTH/tasks", billingHeaders(credential))
            if (status !in 200..299) {
                return ProviderTaskListView(emptyList(), error = "上游 HTTP $status" + describeBody(body))
            }
            val obj = jsonOf(body) ?: return ProviderTaskListView(emptyList(), error = "响应不是合法 JSON")
            val inPeriod = obj.get("in_period")?.asBoolean ?: false
            val array = obj.arrayOrNull("tasks")
                ?: return ProviderTaskListView(emptyList(), inPeriod = inPeriod)
            ProviderTaskListView(array.mapNotNull { mapTask(it) }, inPeriod = inPeriod)
        } catch (e: Exception) {
            ProviderTaskListView(emptyList(), error = e.message ?: "拉取任务列表失败")
        }
    }

    /** WorkBuddy 的额度包明细（周期额度 / 容量包）。 */
    override fun creditPacks(account: ProviderAccount): List<QuotaPack> {
        val credential = parse(account) ?: return emptyList()
        return try {
            val (status, text) = postJson(
                "${billingBase(credential)}$PATH_USER_RESOURCE",
                credential,
                userResourceBody(),
                billingHeaders(credential),
            )
            if (status !in 200..299) return emptyList()
            val data = envelopeData(text) ?: return emptyList()
            val accounts = data.objOrNull("Response")?.objOrNull("Data")
                ?.arrayOrNull("Accounts") ?: return emptyList()
            accounts.mapNotNull { element ->
                val item = runCatching { element.asJsonObject }.getOrNull() ?: return@mapNotNull null
                val cycleSize = item.get("CycleCapacitySize")?.asLong ?: 0L
                val capacitySize = item.get("CapacitySize")?.asLong ?: 0L
                QuotaPack(
                    name = item.firstString("PackageName").ifEmpty { "额度包" },
                    group = if (cycleSize > 0) "周期额度" else "容量包",
                    limit = if (cycleSize > 0) cycleSize else capacitySize,
                    used = if (cycleSize > 0) {
                        item.get("CycleCapacityUsed")?.asLong ?: 0L
                    } else {
                        item.get("CapacityUsed")?.asLong ?: 0L
                    },
                    remain = if (cycleSize > 0) {
                        item.get("CycleCapacityRemain")?.asLong ?: 0L
                    } else {
                        item.get("CapacityRemain")?.asLong ?: 0L
                    },
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 对话活跃上报：POST {billingBase}/v2/report。
     *
     * 事件形状照抄客户端 chat_request_send 的全字段（不用最小 3 字段——上游随时可能加严），
     * `userId` 必填，缺失时服务端返回 200 但静默丢弃。一条上报同时点亮连登并解锁
     * 「领养第一只 Buddy」的前置任务。conversationId 由调用方生成，服务端不校验会话真实存在。
     */
    private fun reportChatActivity(
        credential: Credential,
        conversationId: String,
        requestId: String = "",
        modelId: String = "deepseek-v4-flash",
        modelName: String = "DeepSeek V4 Flash",
    ): Pair<Int, String> {
        val now = nowMillis()
        val rid = requestId.ifEmpty { conversationId }
        val model = modelId.ifEmpty { "deepseek-v4-flash" }
        val event = JsonObject().apply {
            addProperty("eventCode", "chat_request_send")
            addProperty("timestamp", now)
            addProperty("reportDelay", 0)
            addProperty("mode", "craft")
            addProperty("conversationId", conversationId)
            addProperty("requestId", rid)
            addProperty("inputLength", 12)
            addProperty("requestModelId", model)
            addProperty("requestModelName", modelName.ifEmpty { model })
            addProperty("isPlan", false)
            addProperty("isAutoExecuteTerminal", false)
            addProperty("isAutoModify", false)
            addProperty("codebaseEnable", false)
            addProperty("maxToken", 0)
            addProperty("maxSteps", 0)
            addProperty("temperature", 0)
            addProperty("maxRetries", 0)
            add("mentionContexts", JsonArray())
            add("knowledgeId", JsonArray())
            add("knowledgeName", JsonArray())
            addProperty("codebaseId", "")
            addProperty("mentionContextCount", 0)
            addProperty("command", "")
            addProperty("expertId", "")
            addProperty("recommendId", "")
            addProperty("skillId", "")
            addProperty("skillCount", 0)
            addProperty("totalCount", 0)
            addProperty("fileUri", "")
            addProperty("presentAt", now)
            addProperty("traceId", "")
            addProperty("rootRequestId", rid)
            addProperty("parentConversationId", conversationId)
            addProperty("agentName", "default")
            addProperty("agentType", "conversation")
            addProperty("userId", credential.uid)
        }
        val body = JsonArray().apply { add(event) }.toString()
        return postJson(
            "${billingBase(credential)}$PATH_REPORT",
            credential,
            body,
            billingHeaders(credential),
        )
    }

    /**
     * 活跃上报动作：每天一条即可（风控口径，不做多时点高频上报）。
     */
    private fun reportActivity(account: ProviderAccount): ProviderActionResult {
        val credential = parse(account) ?: return ProviderActionResult.failure("凭证无法解析")
        return try {
            val (status, text) = reportChatActivity(
                credential,
                conversationId = "aigw-activity-${nowMillis()}",
            )
            if (status !in 200..299) {
                return ProviderActionResult.failure("活跃上报失败（HTTP $status）" + describeBody(text))
            }
            val code = runCatching {
                JsonParser.parseString(text).asJsonObject.get("code")?.asLong
            }.getOrNull() ?: -1L
            if (code == 0L) {
                ProviderActionResult.success("活跃上报成功")
            } else {
                ProviderActionResult.failure(extractMessage(text).ifEmpty { "活跃上报失败（code=$code）" })
            }
        } catch (e: Exception) {
            ProviderActionResult.failure(e.message ?: "活跃上报失败")
        }
    }

    /** 夜猫子计数窗口：23:00–08:00（本地时区）。 */
    private fun inNightWindow(): Boolean {
        val hour = SimpleDateFormat("H", Locale.US).format(Date(nowMillis())).toIntOrNull() ?: 0
        return hour >= 23 || hour < 8
    }

    /**
     * 发一次真实对话并读干响应（不保留任何内容）。
     *
     * 部分成长任务只认「客户端真的发过对话」：只上报事件不够。失败返回 false，
     * 由调用方决定是否中止（不抛异常——单次失败不该让整个动作崩掉）。
     */
    private fun sendChatOnce(credential: Credential, modelId: String, prompt: String): Boolean {
        val body = JsonObject().apply {
            addProperty("model", modelId)
            add("messages", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", prompt)
                })
            })
            addProperty("stream", true)
        }.toString()
        return try {
            val conn = open(
                "${chatBase(credential)}$PATH_CHAT",
                credential,
                prepareCodeBuddyBody(body),
                chatHeaders(credential),
            )
            try {
                if (conn.responseCode !in 200..299) return false
                conn.inputStream?.use { input ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BODY_BYTES) break
                    }
                }
                true
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun blackCat(account: ProviderAccount): ProviderActionResult {
        val credential = parse(account) ?: return ProviderActionResult.failure("凭证无法解析")
        return try {
            ProviderActionResult.success(runBlackCat(credential))
        } catch (e: Exception) {
            ProviderActionResult.failure(e.message ?: "夜猫子执行失败")
        }
    }

    /**
     * 夜猫子（black_cat）：窗口内完成若干次 glm-5.2 对话并上报事件链。
     *
     * 窗口外直接跳过——上游不计分，发出去只是白花额度。
     */
    private fun runBlackCat(credential: Credential): String {
        if (!inNightWindow()) return "不在夜猫子时段（23:00–08:00），跳过"
        val task = taskByCode(credential, "black_cat") ?: return "没有夜猫子任务"
        val target = if (task.target > 0) task.target else 1L
        if (task.claimed || task.current >= target) return "夜猫子已完成"

        var done = 0L
        for (index in 0 until target - task.current) {
            if (!sendChatOnce(credential, "glm-5.2", "1+1等于几？直接回答。")) {
                throw IllegalStateException("第 ${index + 1} 次对话失败（已完成 $done 次）")
            }
            val (status, text) = reportChatActivity(
                credential,
                conversationId = "aigw-night-${nowMillis()}-$index",
                modelId = "glm-5.2",
                modelName = "GLM-5.2",
            )
            if (status !in 200..299) {
                throw IllegalStateException("第 ${index + 1} 次上报失败（HTTP $status）" + describeBody(text))
            }
            done++
            if (index < target - task.current - 1) Thread.sleep(NIGHT_CHAT_GAP_MS)
        }
        return "夜猫子补足 $done 次对话"
    }

    private fun checkin(account: ProviderAccount): ProviderActionResult {
        val credential = parse(account) ?: return ProviderActionResult.failure("凭证无法解析")
        return try {
            val (status, text) = postJson(
                "${billingBase(credential)}$PATH_CHECKIN",
                credential,
                "{}",
                billingHeaders(credential),
            )
            if (status !in 200..299) {
                return ProviderActionResult.failure(
                    "签到失败（HTTP $status）" + describeBody(text),
                )
            }
            val code = runCatching { JsonParser.parseString(text).asJsonObject.get("code")?.asLong }.getOrNull() ?: -1L
            if (code == 0L) return ProviderActionResult.success("签到成功")
            val message = extractMessage(text)
            if (message.contains("已签到") || message.contains("already")) {
                ProviderActionResult.success("今日已签到")
            } else {
                ProviderActionResult.failure(message.ifEmpty { "签到失败（code=$code）" })
            }
        } catch (e: Exception) {
            ProviderActionResult.failure(e.message ?: "签到失败")
        }
    }

    // -------------------------------------------------------------- 行为指纹上报

    /**
     * 桌面客户端上报头。
     *
     * 点亮「需电脑端」类成长任务靠的不是独立端点，而是同一 `POST /v2/report` 通道上
     * 不同的客户端指纹：桌面端带 WorkBuddy 的 UA 与 X-Domain（走 chatBase），
     * CLI 活跃上报走 billingBase。
     */
    private fun desktopHeaders(credential: Credential): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${credential.accessToken}")
        put("Accept", "application/json, text/plain, */*")
        put("Content-Type", "application/json;charset=UTF-8")
        put("User-Agent", CodeBuddyDesktopEvents.DESKTOP_UA)
        put("X-Domain", chatBase(credential))
        put("X-Product", "SaaS")
        put("X-Request-ID", CodeBuddyDesktopEvents.deriveId(credential.uid, "req") + nowMillis() % 1_000_000)
        if (credential.uid.isNotEmpty()) put("X-User-Id", credential.uid)
    }

    /** 桌面指纹批量上报：事件只带业务字段，公共指纹由构造层注入。 */
    private fun reportDesktopEvent(credential: Credential, events: List<JsonObject>): Pair<Int, String> {
        if (events.isEmpty()) return 0 to ""
        val fingerprint = CodeBuddyDesktopEvents.fingerprint(credential.uid, credential.nickname, nowMillis())
        val array = JsonArray().apply { events.forEach { add(CodeBuddyDesktopEvents.merge(fingerprint, it)) } }
        return postJson(
            "${chatBase(credential)}$PATH_REPORT",
            credential,
            array.toString(),
            desktopHeaders(credential),
        )
    }

    /** Web 域单事件上报（`Library_read` 等页面行为类任务的判据）。 */
    private fun reportWebEvent(
        credential: Credential,
        eventCode: String,
        pageUrl: String,
        elementId: String,
        elementName: String,
    ): Pair<Int, String> {
        val event = CodeBuddyDesktopEvents.webEvent(
            eventCode = eventCode,
            pageUrl = pageUrl,
            elementId = elementId,
            elementName = elementName,
            uid = credential.uid,
            nickname = credential.nickname,
            enterpriseId = credential.enterpriseId,
            now = nowMillis(),
        )
        val headers = buildMap {
            put("Authorization", "Bearer ${credential.accessToken}")
            put("Accept", "application/json")
            put("Content-Type", "application/json")
            put("x-client-platform", "web")
            put("Origin", webBase(credential))
            put("Referer", pageUrl)
            put("User-Agent", CodeBuddyDesktopEvents.WEB_UA)
            if (credential.uid.isNotEmpty()) put("X-User-Id", credential.uid)
        }
        return postJson(
            "${webBase(credential)}$PATH_REPORT",
            credential,
            JsonArray().apply { add(event) }.toString(),
            headers,
        )
    }

    /** 专家市场真实专家列表；[expertType] 取 `agent`（单专家）或 `team`（专家团）。 */
    private fun marketExpertList(
        credential: Credential,
        expertType: String,
    ): List<CodeBuddyDesktopEvents.MarketExpert> {
        val body = JsonObject().apply {
            addProperty("page", 1)
            addProperty("page_size", 20)
            addProperty("sort_by", "reco_rank")
            addProperty("sort_order", "desc")
            if (expertType.isNotEmpty()) addProperty("expert_type", expertType)
        }.toString()
        val (status, text) = postJson(
            "${chatBase(credential)}$PATH_EXPERT_LIST",
            credential,
            body,
            desktopHeaders(credential),
        )
        if (status !in 200..299) return emptyList()
        val experts = envelopeData(text)?.arrayOrNull("experts") ?: return emptyList()
        return experts.mapNotNull { element ->
            val item = runCatching { element.asJsonObject }.getOrNull() ?: return@mapNotNull null
            val id = item.firstString("expert_id")
            if (id.isEmpty()) return@mapNotNull null
            CodeBuddyDesktopEvents.MarketExpert(
                expertId = id,
                expertType = item.firstString("expert_type"),
                displayNameZh = item.firstString("display_name_zh"),
                professionZh = item.firstString("profession_zh"),
                version = item.firstString("version"),
                categories = item.arrayOrNull("categories")?.mapNotNull { category ->
                    runCatching { category.asString }.getOrNull()?.takeIf { it.isNotEmpty() }
                }.orEmpty(),
            )
        }
    }

    /**
     * 发一条真实桌面指纹对话，并抓服务端返回的 requestId。
     *
     * `expert_actual_use` 等事件的 requestId 必须是服务端在 SSE 里回的真实 id，
     * 自造 UUID 不计数（客户端 resolveRealRequestId 同款语义）。失败返回 null。
     */
    private fun desktopChatWithExpert(credential: Credential, expertId: String): Pair<String, String>? {
        val conversationId = "aigw-conv-${nowMillis()}"
        val body = JsonObject().apply {
            addProperty("model", "fast-model")
            add("messages", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", "You are a helpful assistant. 当前处于中文环境，使用简体中文回答。")
                })
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", "1+1等于几？直接回答。")
                })
            })
            addProperty("agent", "cli")
            addProperty("temperature", 1)
            addProperty("stream", true)
            add("stream_options", JsonObject().apply { addProperty("include_usage", true) })
        }.toString()
        val headers = buildMap {
            put("Authorization", "Bearer ${credential.accessToken}")
            put("Content-Type", "application/json")
            put("Accept", "text/event-stream")
            put("User-Agent", CodeBuddyDesktopEvents.DESKTOP_UA)
            put("X-Domain", chatBase(credential))
            put("X-Product", "SaaS")
            if (credential.uid.isNotEmpty()) put("X-User-Id", credential.uid)
            put("X-Conversation-ID", conversationId)
            put("X-Request-ID", nowMillis().toString())
            put("X-Agent-Intent", "craft")
            put("X-Agent-Type", "main")
            put("X-IDE-Name", "WorkBuddy")
            put("X-IDE-Type", "WorkBuddy")
            put("X-IDE-Version", "5.5.6")
            put("x-codebuddy-request", "1")
            if (expertId.isNotEmpty()) put("X-Expert-Id", expertId)
        }
        val conn = open("${chatBase(credential)}$PATH_CHAT", credential, body, headers)
        return try {
            if (conn.responseCode != 200) {
                null
            } else {
                conn.inputStream.use { input -> readServerRequestId(input) }?.let { conversationId to it }
            }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 从 SSE 流里抓第一个形如 `cmb-<32hex>`／`<32hex>` 的 `data.id`。
     *
     * 每命中一个候选都要把搜索游标前移：`"id":"` 也会出现在消息 id 等字段上，
     * 不推进就会一直重复命中同一处，读满上限后误判「没找到 requestId」。
     */
    private fun readServerRequestId(input: InputStream): String? {
        val buffer = StringBuilder()
        val chunk = ByteArray(8192)
        var searchFrom = 0
        var total = 0L
        while (total < MAX_BODY_BYTES) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            buffer.append(String(chunk, 0, read, StandardCharsets.UTF_8))
            var index = buffer.indexOf("\"id\":\"", searchFrom)
            while (index >= 0) {
                val start = index + 6
                val end = buffer.indexOf("\"", start)
                if (end < 0) break
                val id = buffer.substring(start, end)
                if (SERVER_REQUEST_ID.matches(id)) return id
                searchFrom = index + 1
                index = buffer.indexOf("\"id\":\"", searchFrom)
            }
        }
        return null
    }

    /** 应用外观主题；纯 API set 不计 `Hp_Appearance` 分，仅供调色与还原。 */
    private fun setAppearanceTheme(credential: Credential, resourceKey: String): Pair<Int, String> {
        val body = JsonObject().apply {
            addProperty("kind", "theme")
            addProperty("resource_key", resourceKey)
        }.toString()
        val headers = buildMap {
            put("Authorization", "Bearer ${credential.accessToken}")
            put("Accept", "application/json, text/plain, */*")
            put("Content-Type", "application/json;charset=UTF-8")
            put("User-Agent", CodeBuddyDesktopEvents.DESKTOP_UA)
            put("X-Product", "SaaS")
            if (credential.uid.isNotEmpty()) put("X-User-Id", credential.uid)
        }
        return postJson("${chatBase(credential)}$PATH_APPEARANCE_SET", credential, body, headers)
    }

    // -------------------------------------------------------------- 成长任务推进

    /** 成长任务里的一个可自动化动作：[code] 是任务 code，[desc] 是给人看的说明。 */
    private class AutoAction(
        val code: String,
        val desc: String,
        val run: (Credential) -> String,
    )

    /**
     * 成长任务动作表，**顺序即执行顺序**。
     *
     * `chat_5` 排最前：领养 Buddy 的门槛要求当日有活跃上报。各项彼此独立，
     * 单项失败不影响后续（每个动作内部自带幂等跳过与容错）。
     */
    private val autoActions: List<AutoAction> = listOf(
        AutoAction("chat_5", "上报 5 条对话活跃事件（自动补足差额）") { runChat5(it) },
        AutoAction("first_buddy", "活跃上报 → 同意协议 → 领取第一只 Buddy") { runFirstBuddy(it) },
        AutoAction("Model_chat_GLM5.2", "接单 → glm-5.2 真实对话 → 对齐模型上报") { runModelChat(it) },
        AutoAction("RichMeow_Chat", "桌面指纹完整对话事件链") { runRichMeow(it) },
        AutoAction("Buddy_App", "buddyapp 进入五连事件") { runBuddyApp(it) },
        AutoAction("Buddy_App_QQ", "buddyapp 进入五连事件（企鹅教师助手）") { runBuddyApp(it) },
        AutoAction("automation_1", "定时任务创建事件") { runAutomationCreate(it) },
        AutoAction("Library_read", "Web 域资料库介绍阅读事件") { runLibraryRead(it) },
        AutoAction("template_5", "用模板创建任务事件组 ×5") { runTemplateUse(it) },
        AutoAction("playbook_prompt", "灵感案例做同款事件组") { runPlaybookPrompt(it) },
        AutoAction("create_canvas", "设计创意画布创建事件组") { runCreateCanvas(it) },
        AutoAction("expert_5", "真实专家召唤 + 使用链 ×5") { runExpertBatch(it, "agent", 5) },
        AutoAction("Expert_team_use_3", "真实专家团召唤 + 使用链 ×3") { runExpertBatch(it, "team", 3) },
        AutoAction("Hp_Appearance", "设置主题 API + 皮肤生效事件") { runAppearance(it) },
        AutoAction("skill_1", "真实对话 + skill_info 技能加载事件") { runSkillFresh(it) },
        AutoAction("Expert_lighthouse", "轻量云专家召唤 + 使用链") { runExpertLighthouse(it) },
        AutoAction("black_cat", "夜猫子：窗口内 glm-5.2 对话补足") { runBlackCat(it) },
        AutoAction("school_season", "校园日：接单 → 带 activityId 的对话上报 → 领奖") {
            runMiniChatTask(it, "school_season", withActivityId = true)
        },
        AutoAction("Sequential_Tasks_1", "小程序首对话：接单 → 对话上报 → 领奖") {
            runMiniChatTask(it, "Sequential_Tasks_1", withActivityId = false)
        },
        AutoAction("Sequential_Tasks_2", "小程序选专家对话：市场专家 id → 接单 → 专家使用事件 → 领奖") {
            runMiniExpert(it)
        },
        AutoAction("Sequential_Tasks_3", "小程序五次对话：按差额补报 → 领奖") {
            runMiniChatTask(it, "Sequential_Tasks_3", withActivityId = false)
        },
        AutoAction("Sequential_Tasks_4", "小程序定时任务：接单 → 定时任务创建事件（PC 同源）→ 领奖") {
            runSequentialEventTask(it, "Sequential_Tasks_4", { runAutomationCreate(it) })
        },
        AutoAction("Sequential_Tasks_5", "小程序使用 GLM5.2：带模型字段的对话上报 → 领奖") {
            runSequentialEventTask(
                it,
                "Sequential_Tasks_5",
                {
                    reportMpOrThrow(
                        it,
                        "小程序对话事件",
                        CodeBuddyDesktopEvents.mpChatEvent(
                            "aigw-mp-glm-${nowMillis()}",
                            "",
                            "glm-5.2",
                            "GLM-5.2",
                        ),
                    )
                },
                {
                    reportChatActivityOrThrow(
                        it,
                        conversationId = "aigw-mp-glm-${nowMillis()}",
                        modelId = "glm-5.2",
                        modelName = "GLM-5.2",
                    )
                },
            )
        },
        AutoAction("Sequential_Tasks_6", "小程序十次对话：按差额补报 → 领奖") {
            runMiniChatTask(it, "Sequential_Tasks_6", withActivityId = false)
        },
        AutoAction("Sequential_Tasks_7", "体验灵感功能：灵感事件组（PC + mp 双形态）→ 领奖") {
            runSequentialEventTask(
                it,
                "Sequential_Tasks_7",
                {
                    val stamp = nowMillis()
                    reportDesktopOrThrow(
                        it,
                        "灵感案例事件组",
                        CodeBuddyDesktopEvents.playbookPromptSequence(
                            "aigw-pb-$stamp",
                            "aigw-pb-req-$stamp",
                            PLAYBOOK_CASE_ID,
                            PLAYBOOK_CASE_NAME,
                            stamp,
                        ),
                    )
                },
                {
                    reportMpOrThrow(
                        it,
                        "小程序灵感事件组",
                        *CodeBuddyDesktopEvents.mpPlaybookEvents(
                            PLAYBOOK_CASE_ID,
                            PLAYBOOK_CASE_NAME,
                            "aigw-mp-pb-${nowMillis()}",
                        ).toTypedArray(),
                    )
                },
            )
        },
    )

    /**
     * 一键完成成长任务。
     *
     * [payload] 的 `task_code` 为空表示跑全部可自动化任务（顺序即 [autoActions]）；
     * 指定 code 时只跑那一个。没登记动作的任务如实说明，不当成失败。
     */
    private fun autoTasks(account: ProviderAccount, payload: JsonObject): ProviderActionResult {
        val credential = parse(account) ?: return ProviderActionResult.failure("凭证无法解析")
        if (regionOf(credential) == REGION_GLOBAL) {
            return ProviderActionResult.failure("国际版没有成长任务")
        }
        val code = payload.firstString("task_code").trim()
        if (code.isEmpty()) {
            val lines = ArrayList<String>()
            for (action in autoActions) {
                lines.add("${action.code}：${runAutoAction(credential, action)}")
                Thread.sleep(REPORT_GAP_MS)
            }
            return ProviderActionResult.success(lines.joinToString("\n"))
        }
        val action = autoActions.firstOrNull { it.code == code }
            ?: return ProviderActionResult.failure("「$code」没有登记自动化动作，需要在客户端内完成")
        return ProviderActionResult.success("$code：${runAutoAction(credential, action)}")
    }

    /**
     * 跑单个动作：已领奖／已达标先跳过，跑完回读进度，达标顺手领奖。
     *
     * 动作自身的异常在这里兜住——批量执行时一项炸掉不该拖垮后面所有项。
     */
    private fun runAutoAction(credential: Credential, action: AutoAction): String {
        val mp = action.code in MP_TASK_CODES
        val before = taskByCode(credential, action.code)
        if (before?.claimed == true) return "已领取"
        if (before?.progressDone == true) return claimAndDescribe(credential, action.code, mp, before)
        val outcome = try {
            action.run(credential)
        } catch (e: Exception) {
            return "执行失败：${e.message ?: e.javaClass.simpleName}"
        }
        val after = taskByCodeWaiting(credential, action.code) ?: return outcome
        if (after.claimed) return "$outcome；已入账（claimed）"
        if (after.claimable) return "$outcome；${claimAndDescribe(credential, action.code, mp, after)}"
        return outcome
    }

    /** 领奖并汇报到账奖励；[mp] 决定走哪个口径。 */
    private fun claimAndDescribe(
        credential: Credential,
        code: String,
        mp: Boolean,
        task: ProviderTask,
    ): String {
        if (task.claimed) return "已领取"
        val outcome = if (mp) claimRewardMp(credential, code) else claimReward(credential, code)
        if (outcome.status !in 200..299) return "领奖失败（HTTP ${outcome.status}）${outcome.message}"
        return if (outcome.credit > 0 || outcome.energy > 0) {
            "领取奖励 +${outcome.credit}c +${outcome.energy}e"
        } else {
            "已领取"
        }
    }

    /** 小程序口径请求头：列表／accept／claim 都要带 `X-Client-Platform`。 */
    private fun mpHeaders(credential: Credential): Map<String, String> = buildMap {
        putAll(billingHeaders(credential))
        put("X-Client-Platform", MP_PLATFORM)
    }

    /**
     * 小程序指纹批量上报（billingBase 的 `/v2/report`）。
     *
     * 头里的客户端标识与 [mpHeaders] 不同：上报要 `mp-weixin`／`workbuddy-mp`。
     */
    private fun reportMpEvent(credential: Credential, events: List<JsonObject>): Pair<Int, String> {
        if (events.isEmpty()) return 0 to ""
        val fingerprint = CodeBuddyDesktopEvents.mpFingerprint(credential.uid, credential.nickname, nowMillis())
        val array = JsonArray().apply {
            events.forEach { add(CodeBuddyDesktopEvents.merge(fingerprint, it)) }
        }
        val headers = buildMap {
            put("Authorization", "Bearer ${credential.accessToken}")
            put("Content-Type", "application/json")
            put("Accept", "application/json")
            if (credential.uid.isNotEmpty()) put("X-User-Id", credential.uid)
            put("X-Client-Product", MP_CLIENT_PRODUCT)
            put("X-Client-Version", MP_CLIENT_VERSION)
            put("X-Client-Platform", MP_CLIENT_PLATFORM)
            put("X-Platform", "wechatmp")
        }
        return postJson("${billingBase(credential)}$PATH_REPORT", credential, array.toString(), headers)
    }

    private fun reportMpOrThrow(credential: Credential, label: String, vararg events: JsonObject) {
        val (status, text) = reportMpEvent(credential, events.toList())
        if (status !in 200..299) {
            throw IllegalStateException("$label 上报失败（HTTP $status）${describeBody(text)}")
        }
    }

    /** 上报一组桌面指纹事件；失败抛出带说明的异常。 */
    private fun reportDesktopOrThrow(credential: Credential, label: String, events: List<JsonObject>) {
        val (status, text) = reportDesktopEvent(credential, events)
        if (status !in 200..299) {
            throw IllegalStateException("$label 上报失败（HTTP $status）${describeBody(text)}")
        }
    }

    private fun reportChatActivityOrThrow(
        credential: Credential,
        conversationId: String,
        modelId: String,
        modelName: String,
    ) {
        val (status, text) = reportChatActivity(
            credential,
            conversationId = conversationId,
            modelId = modelId,
            modelName = modelName,
        )
        if (status !in 200..299 || !envelopeOk(text)) {
            throw IllegalStateException("对话活跃上报失败（HTTP $status）${describeBody(text)}")
        }
    }

    /** 按 code 取一条任务；mp 专属任务默认列表里没有，会自动落到 mp 口径再查一次。 */
    private fun taskByCode(credential: Credential, code: String): ProviderTask? {
        fetchTasks(credential, mp = false).firstOrNull { it.code == code }?.let { return it }
        if (code !in MP_TASK_CODES) return null
        return fetchTasks(credential, mp = true).firstOrNull { it.code == code }
    }

    private fun taskByCodeMp(credential: Credential, code: String): ProviderTask? =
        fetchTasks(credential, mp = true).firstOrNull { it.code == code }

    /** 回读任务：未达标时在有界预算内轮询（上游计分是异步的，数秒后才刷新）。 */
    private fun taskByCodeWaiting(credential: Credential, code: String): ProviderTask? {
        var task = taskByCode(credential, code) ?: return null
        if (task.claimable || task.claimed) return task
        for (round in 1 until CLAIM_POLL_ATTEMPTS) {
            Thread.sleep(CLAIM_POLL_GAP_MS)
            task = taskByCode(credential, code) ?: continue
            if (task.claimable || task.claimed) return task
        }
        return task
    }

    /** mp 口径回读两轮（异步计分，各隔 [CLAIM_POLL_GAP_MS]）。 */
    private fun pollTaskMp(credential: Credential, code: String, target: Long): ProviderTask? {
        var task = taskByCodeMp(credential, code)
        for (round in 0 until 2) {
            val current = task
            if (current != null && (current.claimable || current.claimed || current.current >= target)) return current
            Thread.sleep(CLAIM_POLL_GAP_MS)
            task = taskByCodeMp(credential, code) ?: task
        }
        return task
    }

    private fun fetchTasks(credential: Credential, mp: Boolean): List<ProviderTask> {
        val headers = if (mp) mpHeaders(credential) else billingHeaders(credential)
        val (status, body) = getJson("${chatBase(credential)}$PATH_GROWTH/tasks", headers)
        if (status !in 200..299) return emptyList()
        val array = jsonOf(body)?.arrayOrNull("tasks") ?: return emptyList()
        return array.mapNotNull { mapTask(it) }
    }

    /** 接单（accept）：只登记不产生进度，可幂等重放。 */
    private fun acceptTasks(credential: Credential, codes: List<String>, mp: Boolean): Boolean {
        if (codes.isEmpty()) return true
        val body = JsonObject().apply {
            add("task_codes", JsonArray().apply { codes.forEach { add(it) } })
        }.toString()
        val headers = if (mp) mpHeaders(credential) else billingHeaders(credential)
        val (status, text) = postJson("${chatBase(credential)}$PATH_GROWTH/tasks/accept", credential, body, headers)
        return status in 200..299 && envelopeOk(text)
    }

    /**
     * mp 口径接单并回读验证登记生效。
     *
     * 上游存在「200 + OK 但 accept 没真正落账」的形态，此后上报的事件全部不归账；
     * 判定以回读 `accept_status` 为准，未生效重试一次。
     */
    private fun acceptWithVerifyMp(credential: Credential, code: String): Boolean {
        for (attempt in 1..2) {
            if (!acceptTasks(credential, listOf(code), mp = true)) continue
            Thread.sleep(MP_ACTION_GAP_MS)
            val status = taskByCodeMp(credential, code)?.status.orEmpty()
            if (status.isNotEmpty() && status != "not_accepted") return true
        }
        return false
    }

    /** 响应信封里的业务码是否为 0（`data` 允许为空）。 */
    private fun envelopeOk(body: String): Boolean =
        runCatching { jsonOf(body)?.get("code")?.asLong == 0L }.getOrDefault(false)

    /** `chat_5`：按差额补活跃上报。 */
    private fun runChat5(credential: Credential): String {
        val task = taskByCode(credential, "chat_5") ?: return "任务不存在"
        val target = if (task.target > 0) task.target else 5L
        val need = target - task.current
        if (need <= 0) return "进度已达标，无需上报"
        for (index in 0 until need) {
            reportChatActivity(credential, conversationId = "aigw-chat5-${nowMillis()}-$index")
            if (index < need - 1) Thread.sleep(REPORT_GAP_MS)
        }
        return "已补报 $need 条对话事件"
    }

    /** `first_buddy`：活跃上报解锁 → 同意协议 → 领取。 */
    private fun runFirstBuddy(credential: Credential): String {
        reportChatActivity(credential, conversationId = "aigw-adopt-${nowMillis()}")
        Thread.sleep(REPORT_GAP_MS)
        val root = chatBase(credential)
        val headers = billingHeaders(credential)
        postJson("$root/activity/growth/buddy/agreement", credential, "{\"agree\":true}", headers)
        val (status, body) = postJson("$root/activity/growth/buddy/first", credential, "{}", headers)
        if (status in 200..299) return "已领取 Buddy（+300 分 +8 能量）"
        if (body.contains(BUDDY_TASK_INCOMPLETE)) {
            return "前置已上报，但领养门槛未过（上游要求当日活跃），稍后重试"
        }
        throw IllegalStateException("领取 Buddy 失败（HTTP $status）${describeBody(body)}")
    }

    /**
     * `Model_chat_GLM5.2`：接单 → 真实对话 → 带模型字段的上报。
     *
     * 接单失败不阻塞：行为事件才是进度的唯一判据。
     */
    private fun runModelChat(credential: Credential): String {
        acceptTasks(credential, listOf("Model_chat_GLM5.2"), mp = false)
        Thread.sleep(REPORT_GAP_MS)
        if (!sendChatOnce(credential, "glm-5.2", "1+1等于几？直接回答。")) {
            throw IllegalStateException("glm-5.2 真实对话失败")
        }
        Thread.sleep(REPORT_GAP_MS)
        reportChatActivityOrThrow(
            credential,
            conversationId = "aigw-glm52-${nowMillis()}",
            modelId = "glm-5.2",
            modelName = "GLM-5.2",
        )
        return "已完成 glm-5.2 对话并按模型字段上报"
    }

    /** `RichMeow_Chat`：桌面指纹的完整对话事件链。 */
    private fun runRichMeow(credential: Credential): String {
        val stamp = nowMillis()
        reportDesktopOrThrow(
            credential,
            "桌面对话事件链",
            CodeBuddyDesktopEvents.chatSequence(
                "aigw-rm-$stamp",
                "aigw-rm-req-$stamp",
                "req-$stamp-user",
                "fast-model",
                "fast-model",
                stamp,
            ),
        )
        return "已按桌面端指纹上报完整对话事件链"
    }

    /** `Buddy_App` / `Buddy_App_QQ`：同一组五连事件同时覆盖两项。 */
    private fun runBuddyApp(credential: Credential): String {
        reportDesktopOrThrow(
            credential,
            "buddyapp 事件链",
            CodeBuddyDesktopEvents.buddyAppSequence(
                CodeBuddyDesktopEvents.BUDDY_APP_ID,
                CodeBuddyDesktopEvents.BUDDY_APP_NAME,
            ),
        )
        return "已上报 buddyapp 进入五连事件"
    }

    /** `automation_1`：定时任务创建事件（纯上报即可，无需真建任务）。 */
    private fun runAutomationCreate(credential: Credential): String {
        reportDesktopOrThrow(
            credential,
            "定时任务创建事件",
            listOf(CodeBuddyDesktopEvents.automationCreateEvent("AI 网关自动化")),
        )
        return "已上报定时任务创建事件"
    }

    /** `Library_read`：Web 域资料库介绍页的点击事件。 */
    private fun runLibraryRead(credential: Credential): String {
        val (status, text) = reportWebEvent(
            credential = credential,
            eventCode = "web_element_click",
            pageUrl = LIBRARY_DOC_URL,
            elementId = "library_doc_intro_click",
            elementName = "WorkBuddy资料库介绍",
        )
        if (status !in 200..299) {
            throw IllegalStateException("资料库事件上报失败（HTTP $status）${describeBody(text)}")
        }
        return "已上报资料库介绍阅读事件"
    }

    /** `template_5`：五组「用模板创建任务」事件（模板 id 上游不校验真实性）。 */
    private fun runTemplateUse(credential: Credential): String {
        TEMPLATES.forEachIndexed { index, (templateId, templateName) ->
            val stamp = nowMillis()
            reportDesktopOrThrow(
                credential,
                "模板事件",
                CodeBuddyDesktopEvents.templateUseSequence(
                    "aigw-tpl-$stamp-$index",
                    "aigw-tpl-req-$stamp-$index",
                    templateId,
                    templateName,
                    stamp,
                ),
            )
            if (index < TEMPLATES.size - 1) Thread.sleep(TEMPLATE_GAP_MS)
        }
        return "已上报 template_used ×${TEMPLATES.size}"
    }

    /** `playbook_prompt`：灵感案例做同款事件组（判据是 prompt_send，不是卡片曝光）。 */
    private fun runPlaybookPrompt(credential: Credential): String {
        val stamp = nowMillis()
        reportDesktopOrThrow(
            credential,
            "灵感案例事件组",
            CodeBuddyDesktopEvents.playbookPromptSequence(
                "aigw-pb-$stamp",
                "aigw-pb-req-$stamp",
                PLAYBOOK_CASE_ID,
                PLAYBOOK_CASE_NAME,
                stamp,
            ),
        )
        return "已上报 playbook_cta_click + playbook_prompt_send"
    }

    /** `create_canvas`：设计创意画布创建事件组。 */
    private fun runCreateCanvas(credential: Credential): String {
        val stamp = nowMillis()
        reportDesktopOrThrow(
            credential,
            "画布创建事件组",
            CodeBuddyDesktopEvents.designCanvasSequence("aigw-cv-$stamp", "aigw-cv-req-$stamp", stamp),
        )
        return "已上报 wbx_design_canvas 事件组"
    }

    /** `expert_5` / `Expert_team_use_3`：真实专家召唤 + 使用链，逐个失败继续。 */
    private fun runExpertBatch(credential: Credential, expertType: String, count: Int): String {
        val experts = marketExpertList(credential, expertType)
        if (experts.isEmpty()) throw IllegalStateException("专家市场列表为空")
        var done = 0
        experts.forEachIndexed { index, expert ->
            if (done >= count) return@forEachIndexed
            val ok = runCatching {
                val request = desktopChatWithExpert(credential, expert.expertId)
                    ?: throw IllegalStateException("真实对话未拿到服务端 requestId")
                val (conversationId, requestId) = request
                reportDesktopOrThrow(
                    credential,
                    "专家召唤链",
                    CodeBuddyDesktopEvents.expertSummonSequence(expert),
                )
                reportDesktopOrThrow(
                    credential,
                    "专家使用链",
                    CodeBuddyDesktopEvents.chatSequence(
                        conversationId,
                        requestId,
                        "msg-${requestId.takeLast(8)}",
                        "fast-model",
                        "fast-model",
                        nowMillis(),
                    ) + CodeBuddyDesktopEvents.expertActualUseEvent(
                        expert,
                        conversationId,
                        requestId,
                        "craft",
                    ),
                )
            }.isSuccess
            if (!ok) return@forEachIndexed
            done++
            if (index < experts.size - 1) Thread.sleep(EXPERT_SUMMON_GAP_MS)
        }
        return "已对 $done 位真实专家完成召唤 + 使用链（类型 $expertType）"
    }

    /**
     * `Hp_Appearance`：设置主题 + 上报皮肤生效事件。
     *
     * 判据是 `appearance_skin_apply`（客户端在主题生效状态下离开设置页时上报），
     * 只调 set 接口不发事件不计分。
     */
    private fun runAppearance(credential: Credential): String {
        val (status, text) = setAppearanceTheme(credential, THEME_RESOURCE_KEY)
        if (status !in 200..299) {
            throw IllegalStateException("设置主题失败（HTTP $status）${describeBody(text)}")
        }
        Thread.sleep(APPEARANCE_APPLY_GAP_MS)
        reportDesktopOrThrow(
            credential,
            "皮肤生效事件",
            listOf(CodeBuddyDesktopEvents.appearanceSkinApply(THEME_RESOURCE_KEY)),
        )
        return "已设置主题并上报皮肤生效事件"
    }

    /**
     * `skill_1`：真实对话 + `skill_info` 技能加载事件。
     *
     * 对话链里的 `chat_message_response` 要把 finishReason 改成 `tool_calls`，
     * 表达「模型发起了工具调用（技能加载）」的语义。
     */
    private fun runSkillFresh(credential: Credential): String {
        val request = desktopChatWithExpert(credential, "")
            ?: throw IllegalStateException("真实对话未拿到服务端 requestId")
        val (conversationId, requestId) = request
        val messageId = "msg-${requestId.takeLast(8)}"
        val events = CodeBuddyDesktopEvents.chatSequence(
            conversationId,
            requestId,
            messageId,
            "fast-model",
            "fast-model",
            nowMillis(),
        ).map { event ->
            if (event.firstString("eventCode") == "chat_message_response") {
                event.apply { addProperty("finishReason", "tool_calls") }
            } else {
                event
            }
        } + CodeBuddyDesktopEvents.skillInfoEvent(
            conversationId,
            requestId,
            messageId,
            "fast-model",
            "fast-model",
        )
        reportDesktopOrThrow(credential, "skill_info 事件", events)
        return "已上报真实对话 + skill_info 技能加载事件"
    }

    /**
     * `Expert_lighthouse`：腾讯轻量云专家的召唤 + 使用链。
     *
     * 与 [runExpertBatch] 同构，两处差异：对话链的 `agent_task_created` 要带
     * `has_expert` + 专家 id，使用事件的 mode 是 `LOCAL`（非 craft）。
     */
    private fun runExpertLighthouse(credential: Credential): String {
        var expert = CodeBuddyDesktopEvents.MarketExpert(
            expertId = LIGHTHOUSE_EXPERT_ID,
            expertType = "agent",
            displayNameZh = "腾讯轻量云专家",
            professionZh = "腾讯轻量云专家",
            version = "1.0.2",
            categories = emptyList(),
        )
        marketExpertList(credential, "agent")
            .firstOrNull { it.expertId == LIGHTHOUSE_EXPERT_ID }
            ?.let { expert = it }
        reportDesktopOrThrow(
            credential,
            "专家召唤链",
            CodeBuddyDesktopEvents.expertSummonSequence(expert),
        )
        val request = desktopChatWithExpert(credential, expert.expertId)
            ?: throw IllegalStateException("真实对话未拿到服务端 requestId")
        val (conversationId, requestId) = request
        val events = CodeBuddyDesktopEvents.chatSequence(
            conversationId,
            requestId,
            "msg-${requestId.takeLast(8)}",
            "fast-model",
            "fast-model",
            nowMillis(),
        ).map { event ->
            if (event.firstString("eventCode") == "agent_task_created") {
                event.apply {
                    addProperty("has_expert", true)
                    addProperty("expert_id", expert.expertId)
                    addProperty("expert_name", expert.displayNameZh)
                    addProperty("expert_industry_id", "")
                }
            } else {
                event
            }
        } + CodeBuddyDesktopEvents.expertActualUseEvent(expert, conversationId, requestId, "LOCAL")
        reportDesktopOrThrow(credential, "专家使用链", events)
        return "已上报轻量云专家召唤 + 使用链（真实对话 requestId）"
    }

    /**
     * 小程序对话类任务：接单 → 按差额补 mini 对话事件 → 回读 → 领奖。
     *
     * 上报间隔取 [MP_CHAT_EVENT_GAP_MS] 加抖动：连发的会被上游反作弊先计数再回滚
     * （领奖返回 400 `task not completed`），宁可慢不可白报。
     */
    private fun runMiniChatTask(credential: Credential, code: String, withActivityId: Boolean): String {
        var task = taskByCodeMp(credential, code) ?: return "mp 口径未下发该任务（活动可能已结束）"
        if (task.claimed) return "已领取"
        if (task.status == "not_accepted" || task.status.isEmpty()) {
            if (!acceptWithVerifyMp(credential, code)) {
                return "accept 未登记生效（上游 200+OK 但未落账形态），待下次重试"
            }
            // 未接单时上游不下发 target（返回 0），接受后必须回读拿真实值
            taskByCodeMp(credential, code)?.let { task = it }
        }
        val target = if (task.target > 0) task.target else 1L
        if (task.current >= target || task.status == "completed") {
            val outcome = claimRewardMp(credential, code)
            if (outcome.status !in 200..299) return "领奖失败（HTTP ${outcome.status}）${outcome.message}"
            return "已领取奖励（+${outcome.credit}c +${outcome.energy}e）"
        }
        val need = target - task.current
        for (index in 0 until need) {
            Thread.sleep(MP_CHAT_EVENT_GAP_MS + kotlin.random.Random.nextLong(10_000))
            val event = CodeBuddyDesktopEvents.mpChatEvent(
                conversationId = "aigw-mp-${nowMillis()}-$index",
                activityId = if (withActivityId) SCHOOL_OPEN_DAY_ACTIVITY_ID else "",
                modelId = "",
                modelName = "",
            )
            reportMpOrThrow(credential, "小程序对话事件", event)
        }
        val after = pollTaskMp(credential, code, target) ?: return "已上报 $need 次，回读失败（下次重试）"
        if (after.claimed) return "本轮已入账（claimed）"
        if (after.current < target) {
            return "已上报 $need 次但进度未达 ${after.current}/$target（异步计分未归账，下次重试）"
        }
        val outcome = claimRewardMp(credential, code)
        if (outcome.status !in 200..299) return "领奖失败（HTTP ${outcome.status}）${outcome.message}"
        return "任务点亮并领取奖励（+${outcome.credit}c +${outcome.energy}e）"
    }

    /**
     * `Sequential_Tasks_2`：小程序内选中专家并完成有效对话。
     *
     * 判据是 mp 指纹的 `expert_actual_use`（不带 activityId/conversationId、
     * `extVersion=2.2.8`、`type=send_message`），与开学季的专家事件是两套口径。
     * 专家 id 必须是市场里真实存在的 `ex_` id，所以**先解析市场列表再 accept**，
     * 免得留下「已登记未上报」的半程态。
     */
    private fun runMiniExpert(credential: Credential): String {
        val code = "Sequential_Tasks_2"
        val task = taskByCodeMp(credential, code) ?: return "mp 口径未下发该任务（活动可能已结束）"
        if (task.claimed) return "已领取"
        val target = if (task.target > 0) task.target else 1L
        if (task.current >= target || task.status == "completed") {
            val outcome = claimRewardMp(credential, code)
            if (outcome.status !in 200..299) return "领奖失败（HTTP ${outcome.status}）${outcome.message}"
            return "已领取奖励（+${outcome.credit}c +${outcome.energy}e）"
        }
        val experts = marketExpertList(credential, "")
        if (experts.isEmpty()) return "专家市场不可用，跳过以防半程态"
        val expert = experts.first()
        val name = expert.displayNameZh.ifEmpty { expert.professionZh }
        if (task.status == "not_accepted" || task.status.isEmpty()) {
            if (!acceptWithVerifyMp(credential, code)) {
                return "accept 未登记生效（上游 200+OK 但未落账形态），待下次重试"
            }
        }
        reportMpOrThrow(
            credential,
            "expert_actual_use 事件",
            CodeBuddyDesktopEvents.mpExpertUseEvent(expert.expertId, name, expert.expertType),
        )
        val after = pollTaskMp(credential, code, target) ?: return "已上报但回读失败（下次重试）"
        if (after.claimed) return "本轮已入账（claimed）"
        if (after.current < target) return "已上报但进度未归账（异步计分，下次重试）"
        val outcome = claimRewardMp(credential, code)
        if (outcome.status !in 200..299) return "领奖失败（HTTP ${outcome.status}）${outcome.message}"
        return "任务点亮并领取奖励（+${outcome.credit}c +${outcome.energy}e）"
    }

    /**
     * Sequential 链里的事件型任务：接单 → 判据事件上报 → 回读 → 达标领奖。
     *
     * 每日零点解锁一环，locked 期间 accept 不落账，返回结果等下次调度重试。
     * [primary] 未点亮且给了 [fallback] 时，第一轮回读后再补报一轮。
     */
    private fun runSequentialEventTask(
        credential: Credential,
        code: String,
        primary: () -> Unit,
        fallback: (() -> Unit)? = null,
    ): String {
        val task = taskByCodeMp(credential, code)
            ?: return "mp 口径未下发该任务（前置任务未完成或活动未开始）"
        if (task.claimed) return "已领取"
        val target = if (task.target > 0) task.target else 1L
        if (task.current >= target || task.status == "completed") {
            val outcome = claimRewardMp(credential, code)
            if (outcome.status !in 200..299) return "领奖失败（HTTP ${outcome.status}）${outcome.message}"
            return "已领取奖励（+${outcome.credit}c +${outcome.energy}e）"
        }
        if (task.status == "not_accepted" || task.status.isEmpty()) {
            if (!acceptWithVerifyMp(credential, code)) {
                return "accept 未登记生效（任务可能处于每日锁定窗口，等解锁后自动重试）"
            }
        }
        primary()
        var latest: ProviderTask? = null
        for (round in 0 until 2) {
            Thread.sleep(CLAIM_POLL_GAP_MS)
            latest = taskByCodeMp(credential, code) ?: latest
            val current = latest
            if (current != null && (current.claimable || current.claimed || current.current >= target)) break
            if (round == 0 && fallback != null) fallback()
        }
        val after = latest ?: return "已上报但回读失败（下次重试）"
        if (after.claimed) return "本轮已入账（claimed）"
        if (after.current < target) return "已上报但进度未点亮（判据形态待解锁后校正，下次重试）"
        val outcome = claimRewardMp(credential, code)
        if (outcome.status !in 200..299) return "领奖失败（HTTP ${outcome.status}）${outcome.message}"
        return "任务点亮并领取奖励（+${outcome.credit}c +${outcome.energy}e）"
    }

    // ------------------------------------------------------------------ 凭证

    override fun refreshAccount(account: ProviderAccount, skewSeconds: Long): ProviderAccount? {
        val credential = parse(account) ?: return null
        val nowSeconds = nowMillis() / 1000
        if (credential.expiresAt > 0 && credential.expiresAt - nowSeconds > skewSeconds) return null
        if (credential.refreshToken.isEmpty()) return null
        return try {
            val (status, text) = postJson(
                "${chatBase(credential)}$PATH_TOKEN_REFRESH",
                credential,
                null,
                refreshHeaders(credential),
            )
            if (status !in 200..299) return null
            val data = envelopeData(text) ?: return null
            val access = data.get("accessToken")?.asString.orEmpty()
            if (access.isEmpty()) return null
            val expiresIn = data.get("expiresIn")?.asLong ?: 0L
            val refreshed = credential.copy(
                accessToken = access,
                refreshToken = data.get("refreshToken")?.asString.orEmpty().ifEmpty { credential.refreshToken },
                domain = data.get("domain")?.asString.orEmpty().ifEmpty { credential.domain },
                expiresAt = if (expiresIn > 0) nowSeconds + expiresIn else credential.expiresAt,
            )
            toProviderAccount(refreshed)
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------ 设备授权登录

    override fun startDeviceAuth(region: String): DeviceAuthTicket {
        val base = if (region == REGION_GLOBAL) GLOBAL_CHAT_BASE else CN_CHAT_BASE
        val url = "$base$PATH_AUTH_STATE?platform=CLI"
        onLoginLog("[$displayName] 请求授权链接 POST $url")
        val (status, text) = try {
            postJson(url, null, "{}", emptyMap())
        } catch (e: Exception) {
            onLoginLog("[$displayName] 请求授权链接异常：${e.message}")
            throw IllegalStateException("请求授权链接失败：${e.message}")
        }
        onLoginLog("[$displayName] 授权链接响应 HTTP $status：${redactSecrets(text, SNIPPET_LIMIT)}")
        if (status !in 200..299) throw IllegalStateException("请求授权链接失败（HTTP $status）")
        val data = envelopeData(text) ?: throw IllegalStateException("授权响应异常：${text.take(200)}")
        val state = data.get("state")?.asString.orEmpty()
        val authUrl = data.get("authUrl")?.asString.orEmpty()
        if (state.isEmpty() || authUrl.isEmpty()) throw IllegalStateException("授权响应缺少 state 或 authUrl")
        return DeviceAuthTicket(loginUrl = authUrl, state = state)
    }

    override fun pollDeviceAuth(state: String, region: String): DeviceAuthPoll {
        val base = if (region == REGION_GLOBAL) GLOBAL_CHAT_BASE else CN_CHAT_BASE
        val encoded = URLEncoder.encode(state, "UTF-8")
        val url = "$base$PATH_AUTH_TOKEN?state=$encoded"
        val (status, text) = try {
            getJson(url)
        } catch (e: Exception) {
            onLoginLog("[$displayName] 轮询请求异常 $url：${e.message}")
            return DeviceAuthPoll.Failed(e.message ?: "轮询失败")
        }
        if (status !in 200..299) {
            onLoginLog("[$displayName] 轮询响应 HTTP $status：${redactSecrets(text, SNIPPET_LIMIT)}")
            return DeviceAuthPoll.Failed("轮询失败（HTTP $status）")
        }
        val obj = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            ?: return DeviceAuthPoll.Failed("轮询响应不是合法 JSON")
        val code = obj.get("code")?.asLong ?: -1L
        if (code == CODE_LOGIN_PENDING) {
            onLoginLog("[$displayName] 轮询响应 HTTP $status code=$code（等待授权）")
            return DeviceAuthPoll.Pending
        }
        onLoginLog("[$displayName] 轮询响应 HTTP $status code=$code：${redactSecrets(text, SNIPPET_LIMIT)}")
        if (code != 0L) {
            val message = obj.get("msg")?.asString.orEmpty()
            return DeviceAuthPoll.Failed(message.ifEmpty { "登录未完成（code=$code）" })
        }
        val data = obj.objOrNull("data") ?: return DeviceAuthPoll.Failed("登录响应缺少 data")
        return DeviceAuthPoll.Success(toProviderAccount(credentialOf(data)))
    }

    // ------------------------------------------------------------------ 上游

    private fun fetchModels(credential: Credential): List<ProviderModel> {
        val (status, text) = getJson("${chatBase(credential)}$PATH_MODELS", chatHeaders(credential))
        if (status !in 200..299) throw IllegalStateException("模型接口 HTTP $status")
        val obj = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            ?: throw IllegalStateException("模型响应不是合法 JSON")
        if (obj.get("code")?.asLong != 0L) throw IllegalStateException("模型接口 code=${obj.get("code")}")
        val data = obj.objOrNull("data") ?: return emptyList()

        val cliIds = LinkedHashSet<String>()
        data.arrayOrNull("agents")?.forEach { element ->
            val agent = runCatching { element.asJsonObject }.getOrNull() ?: return@forEach
            if (agent.get("name")?.asString == "cli") {
                agent.arrayOrNull("models")?.forEach { cliIds.add(it.asString) }
            }
        }
        val result = ArrayList<ProviderModel>()
        val seen = HashSet<String>()
        data.arrayOrNull("models")?.forEach { element ->
            val item = runCatching { element.asJsonObject }.getOrNull() ?: return@forEach
            val modelId = item.get("id")?.asString.orEmpty()
            if (modelId.isEmpty() || seen.contains(modelId)) return@forEach
            if (cliIds.isNotEmpty() && !cliIds.contains(modelId)) return@forEach
            if (item.get("disabled")?.asBoolean == true) return@forEach
            seen.add(modelId)
            result.add(
                ProviderModel(
                    id = modelId,
                    name = item.get("name")?.asString.orEmpty().ifEmpty { modelId },
                    contextWindow = item.get("maxInputTokens")?.asLong ?: 0L,
                    extra = buildMap {
                        // 上游给的是 "x0.79 credits" 这种串，抽出数值作为倍率展示
                        parseCreditsStatic(item.get("credits")?.asString)?.let { put("rate", "$it×") }
                        item.get("maxOutputTokens")?.asLong?.takeIf { it > 0 }?.let {
                            put("maxOutput", it.toString())
                        }
                        if (item.get("supportsReasoning")?.asBoolean == true) put("reasoning", "true")
                        if (item.get("supportsImages")?.asBoolean == true) put("vision", "true")
                        if (item.get("supportsToolCall")?.asBoolean == true) put("tools", "true")
                        item.get("vendor")?.asString?.takeIf { it.isNotBlank() }?.let { put("vendor", it) }
                    },
                ),
            )
        }
        return result
    }

    // ------------------------------------------------------------------ HTTP

    private fun open(
        url: String,
        credential: Credential?,
        body: String?,
        headers: Map<String, String>,
        method: String = if (body == null) "GET" else "POST",
    ): HttpURLConnection {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            setRequestProperty("Accept-Encoding", "identity")
        }
        for ((key, value) in headers) conn.setRequestProperty(key, value)
        if (body != null) {
            conn.doOutput = true
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
        }
        return conn
    }

    private fun postJson(
        url: String,
        credential: Credential?,
        body: String?,
        headers: Map<String, String>,
    ): Pair<Int, String> {
        val conn = open(url, credential, body, headers)
        return try {
            val status = conn.responseCode
            val text = if (status in 200..299) readLimited(conn.inputStream) else readLimited(conn.errorStream)
            status to text
        } finally {
            conn.disconnect()
        }
    }

    private fun getJson(url: String, headers: Map<String, String> = emptyMap()): Pair<Int, String> {
        val conn = open(url, null, null, headers, method = "GET")
        return try {
            val status = conn.responseCode
            val text = if (status in 200..299) readLimited(conn.inputStream) else readLimited(conn.errorStream)
            status to text
        } finally {
            conn.disconnect()
        }
    }

    private fun commonHeaders(credential: Credential?): Map<String, String> {
        val origin = originFor(credential)
        return mapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json, text/plain, */*",
            "X-Requested-With" to "XMLHttpRequest",
            "Origin" to origin,
            "Referer" to "$origin/",
            "User-Agent" to CLIENT_UA,
        )
    }

    private fun chatHeaders(credential: Credential): Map<String, String> = buildMap {
        putAll(commonHeaders(credential))
        put("Authorization", "Bearer ${credential.accessToken}")
        if (credential.uid.isNotEmpty()) put("X-User-Id", credential.uid)
        if (credential.enterpriseId.isNotEmpty()) put("X-Enterprise-Id", credential.enterpriseId)
        if (credential.domain.isNotEmpty()) put("X-Domain", credential.domain)
        put("X-Product", "SaaS")
    }

    private fun billingHeaders(credential: Credential): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${credential.accessToken}")
        put("Accept", "application/json")
        put("Content-Type", "application/json")
        put("X-Requested-With", "XMLHttpRequest")
        put("User-Agent", CLIENT_UA)
        val origin = originFor(credential)
        put("Origin", origin)
        put("Referer", "$origin/")
        put("X-Product", "SaaS")
        if (credential.uid.isNotEmpty()) put("X-User-Id", credential.uid)
        if (credential.enterpriseId.isNotEmpty()) {
            put("X-Enterprise-Id", credential.enterpriseId)
            put("X-Tenant-Id", credential.enterpriseId)
        }
        if (credential.domain.isNotEmpty()) put("X-Domain", credential.domain)
    }

    /** refresh 是唯一允许携带 X-Refresh-Token 的端点。 */
    private fun refreshHeaders(credential: Credential): Map<String, String> = buildMap {
        putAll(commonHeaders(credential))
        put("X-Refresh-Token", credential.refreshToken)
        if (credential.enterpriseId.isNotEmpty()) put("X-Enterprise-Id", credential.enterpriseId)
        put("X-Auth-Refresh-Source", "workbuddy")
    }

    /**
     * Web 域请求头（形状对照官网成长中心页实际发出的请求）。
     *
     * 领奖接口只在官网域存在，上游按 `x-client-platform: web` 标记来源端。
     */
    private fun webHeaders(credential: Credential): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${credential.accessToken}")
        put("Accept", "application/json, text/plain, */*")
        put("Content-Type", "application/json")
        put("Origin", webBase(credential))
        put("Referer", "${webBase(credential)}/profile/growth-center")
        put("User-Agent", CLIENT_UA)
        put("x-client-platform", "web")
        if (credential.uid.isNotEmpty()) put("X-User-Id", credential.uid)
        if (credential.enterpriseId.isNotEmpty()) {
            put("X-Enterprise-Id", credential.enterpriseId)
            put("X-Tenant-Id", credential.enterpriseId)
        }
        if (credential.domain.isNotEmpty()) put("X-Domain", credential.domain)
    }

    private fun userResourceBody(): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val now = Date(nowMillis())
        val far = Date(nowMillis() + 101L * 365 * 24 * 3600 * 1000)
        return JsonObject().apply {
            addProperty("PageNumber", 1)
            addProperty("PageSize", 100)
            addProperty("ProductCode", PRODUCT_CODE)
            add("Status", JsonArray().apply { add(0); add(3) })
            addProperty("PackageEndTimeRangeBegin", format.format(now))
            addProperty("PackageEndTimeRangeEnd", format.format(far))
        }.toString()
    }

    // ------------------------------------------------------------------ 工具

    private fun regionOf(credential: Credential?): String {
        val domain = credential?.domain.orEmpty().lowercase()
        if (domain.endsWith(".workbuddy.ai") || domain == "workbuddy.ai") return REGION_GLOBAL
        if (domain.isNotEmpty()) return REGION_CN
        return region()
    }

    /** 网关按账号区域选号用的公开入口（见 [RegionAwareSupport]）。 */
    override fun regionOf(account: ProviderAccount): String? = regionOf(parse(account))

    override fun regions(): List<String> = listOf(REGION_CN, REGION_GLOBAL)

    private fun chatBase(credential: Credential?): String =
        if (regionOf(credential) == REGION_GLOBAL) GLOBAL_CHAT_BASE else CN_CHAT_BASE

    private fun billingBase(credential: Credential?): String =
        if (regionOf(credential) == REGION_GLOBAL) GLOBAL_BILLING_BASE else CN_BILLING_BASE

    private fun webBase(credential: Credential?): String =
        if (regionOf(credential) == REGION_GLOBAL) GLOBAL_WEB_BASE else CN_WEB_BASE

    private fun originFor(credential: Credential?): String =
        if (regionOf(credential) == REGION_GLOBAL) GLOBAL_ORIGIN else CN_ORIGIN

    private fun parse(account: ProviderAccount): Credential? {
        val obj = runCatching { JsonParser.parseString(account.secret).asJsonObject }.getOrNull() ?: return null
        return credentialOf(obj).takeIf { it.accessToken.isNotEmpty() }
    }

    private fun credentialOf(obj: JsonObject): Credential {
        // 上游不同版本/站点用过 camelCase 与 snake_case 两套字段名，都要兼容：
        // 少一个 uid 就会让 billing 接口缺 X-User-Id 头而报 400。
        val accessToken = obj.firstString("accessToken", "access_token")
        return Credential(
            accessToken = accessToken,
            refreshToken = obj.firstString("refreshToken", "refresh_token"),
            expiresAt = obj.firstLong("expiresAt", "expires_at"),
            domain = obj.firstString("domain"),
            // 响应里没给时从 access_token（JWT）里解，旧的存量账号也能自动修好
            uid = obj.firstString("uid", "userId", "user_id")
                .ifEmpty { jwtClaim(accessToken, "user_id", "userId", "uid", "sub") },
            enterpriseId = obj.firstString("enterpriseId", "enterprise_id", "entId", "tenantId", "tenant_id")
                .ifEmpty { jwtClaim(accessToken, "tenant_id", "tenantId", "enterprise_id", "enterpriseId") },
            nickname = obj.firstString("nickname", "nickName", "name"),
        )
    }

    /** 从 access_token（JWT）的 payload 里取某个声明；取不到返回空串。 */

    private fun toProviderAccount(credential: Credential): ProviderAccount {
        val secret = JsonObject().apply {
            addProperty("accessToken", credential.accessToken)
            addProperty("refreshToken", credential.refreshToken)
            addProperty("expiresAt", credential.expiresAt)
            addProperty("domain", credential.domain)
            addProperty("uid", credential.uid)
            addProperty("enterpriseId", credential.enterpriseId)
            addProperty("nickname", credential.nickname)
        }.toString()
        val uid = credential.uid.ifEmpty {
            "codebuddy-" + credential.accessToken.hashCode().toUInt().toString(16)
        }
        return ProviderAccount(id, uid, credential.nickname, secret)
    }

    private fun envelopeData(body: String): JsonObject? {
        val obj = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return null
        if (obj.get("code")?.asLong != 0L) return null
        return obj.objOrNull("data")
    }

    private fun extractMessage(body: String): String {
        if (body.isEmpty()) return ""
        val obj = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return ""
        obj.get("msg")?.asString?.takeIf { it.isNotEmpty() }?.let { return it }
        obj.objOrNull("error")?.get("message")?.asString?.takeIf { it.isNotEmpty() }?.let { return it }
        obj.get("message")?.asString?.takeIf { it.isNotEmpty() }?.let { return it }
        return ""
    }

    private fun matchesQuota(body: String): Boolean {
        val lower = body.lowercase()
        return QUOTA_MARKERS.any { lower.contains(it) || body.contains(it) }
    }

    private fun matchesSessionDead(body: String): Boolean =
        SESSION_DEAD_MARKERS.any { body.contains(it) }

    private fun readLimited(input: InputStream?): String {
        if (input == null) return ""
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (out.size() + read > MAX_BODY_BYTES) {
                out.write(buffer, 0, MAX_BODY_BYTES - out.size())
                break
            }
            out.write(buffer, 0, read)
        }
        return out.toString("UTF-8")
    }

    private fun requestedModel(body: String): String =
        runCatching { JsonParser.parseString(body).asJsonObject.get("model")?.asString.orEmpty() }
            .getOrDefault("")

    private fun isStreaming(body: String): Boolean =
        runCatching { JsonParser.parseString(body).asJsonObject.get("stream")?.asBoolean ?: false }
            .getOrDefault(false)

    private data class Credential(
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long,
        val domain: String,
        val uid: String,
        val enterpriseId: String,
        val nickname: String,
    )

    companion object {
        const val ID = "codebuddy"

        /** 登录日志里响应报文的摘录上限，避免环包缓冲被单条日志占满。 */
        private const val SNIPPET_LIMIT = 300

        const val ACTION_CHECKIN = dev.aigw.core.provider.ACTION_CHECKIN

        const val ACTION_ACTIVITY = dev.aigw.core.provider.ACTION_ACTIVITY

        const val ACTION_BLACKCAT = dev.aigw.core.provider.ACTION_BLACKCAT

        const val REGION_CN = "cn"
        const val REGION_GLOBAL = "global"

        const val CN_CHAT_BASE = "https://copilot.tencent.com"
        const val CN_BILLING_BASE = "https://www.codebuddy.cn"
        const val CN_ORIGIN = "https://www.codebuddy.cn"

        /** Web 域（官网）：部分「任务领奖」接口只在官网域提供，CLI 域同名路径返回 400。 */
        const val CN_WEB_BASE = "https://www.workbuddy.cn"
        const val GLOBAL_CHAT_BASE = "https://www.workbuddy.ai"
        const val GLOBAL_BILLING_BASE = "https://www.workbuddy.ai"
        const val GLOBAL_ORIGIN = "https://www.workbuddy.ai"
        const val GLOBAL_WEB_BASE = "https://www.workbuddy.ai"

        const val CLIENT_UA = "CLI/2.63.2 CodeBuddy/2.63.2"

        const val PATH_AUTH_STATE = "/v2/plugin/auth/state"
        const val PATH_AUTH_TOKEN = "/v2/plugin/auth/token"
        const val PATH_TOKEN_REFRESH = "/v2/plugin/auth/token/refresh"
        const val PATH_CHAT = "/v2/chat/completions"
        const val PATH_MODELS = "/console/enterprises/personal/models"
        const val PATH_USER_RESOURCE = "/v2/billing/meter/get-user-resource"
        const val PATH_CHECKIN = "/v2/billing/meter/daily-checkin"

        /** 对话活跃上报通道（CLI 走 billingBase、桌面指纹走 chatBase、Web 走 webBase）。 */
        const val PATH_REPORT = "/v2/report"

        /** 桌面端外观主题设置。 */
        const val PATH_APPEARANCE_SET = "/v2/user-asset/appearance/set"

        /** 专家市场列表（`expert_actual_use` 要求专家 id 是平台上真实存在的）。 */
        const val PATH_EXPERT_LIST = "/portal/operation-platform/market/expert/list"

        /** 小程序任务口径的客户端标识（列表／accept／claim 都要带）。 */
        private const val MP_PLATFORM = "miniprogram"

        /** 小程序埋点上报头里的客户端标识（与 [MP_PLATFORM] 不是同一个值）。 */
        private const val MP_CLIENT_PLATFORM = "mp-weixin"
        private const val MP_CLIENT_PRODUCT = "workbuddy-mp"
        private const val MP_CLIENT_VERSION = "2.4.0"

        /** 校园日活动的 activityId：事件不带它不点亮。 */
        private const val SCHOOL_OPEN_DAY_ACTIVITY_ID = "school_open_day_2026"

        /** 领养 Buddy 的前置未过时上游返回的文案标记。 */
        private const val BUDDY_TASK_INCOMPLETE = "first_buddy task not completed yet"

        /** `Hp_Appearance` 的判据主题（和平精英激战金秋）。 */
        private const val THEME_RESOURCE_KEY = "theme-tkmw7j"

        /** `Library_read` 判据事件的 pageURL。 */
        private const val LIBRARY_DOC_URL =
            "https://www.workbuddy.cn/space/d/o0KWYeynteVv06UnAZqIFm"

        /** `Expert_lighthouse` 的判据专家（腾讯轻量云专家）。 */
        private const val LIGHTHOUSE_EXPERT_ID = "ex_2cvvUZQhDyeJ"

        /** `playbook_prompt` / `Sequential_Tasks_7` 用的灵感案例。 */
        private const val PLAYBOOK_CASE_ID = "pm-gtm-launch-plan"
        private const val PLAYBOOK_CASE_NAME = "新产品上市 GTM 发布计划一页纸"

        /** `template_5` 要用的五个模板（上游不校验真实性）。 */
        private val TEMPLATES = listOf(
            "1" to "深度研究",
            "2" to "周报生成",
            "3" to "竞品分析",
            "4" to "活动策划",
            "5" to "代码评审",
        )

        /** `template_5` 五组事件之间的间隔。 */
        private const val TEMPLATE_GAP_MS = 300L

        /** 相邻两条上报之间的最小间隔。 */
        private const val REPORT_GAP_MS = 1_050L

        /** 小程序任务写动作之间的间隔（accept／上报／领奖）。 */
        private const val MP_ACTION_GAP_MS = 2_000L

        /**
         * 小程序对话事件之间的间隔。
         *
         * 上游对「N 次有效对话」有反作弊校验：秒级连发的事件会先被计入进度，
         * 随后判定无效整体回滚（进度回落、领奖返回 400 task not completed）。
         */
        private const val MP_CHAT_EVENT_GAP_MS = 45_000L

        /** 领奖前回读的间隔：上游计分是异步的，数秒后才刷新。 */
        private const val CLAIM_POLL_GAP_MS = 3_000L

        /** 领奖前回读的最大轮数（含首轮），超出即放弃本轮。 */
        private const val CLAIM_POLL_ATTEMPTS = 4

        /** 专家召唤链之间的间隔（按真实使用节奏）。 */
        private const val EXPERT_SUMMON_GAP_MS = 6_000L

        /** 换肤：`appearance/set` 与 `appearance_skin_apply` 之间的间隔。 */
        private const val APPEARANCE_APPLY_GAP_MS = 2_000L

        /** 小程序口径专属下发的成长任务：默认列表里没有，且全链路要求 mp 头。 */
        private val MP_TASK_CODES = setOf(
            "school_season",
            "Sequential_Tasks_1",
            "Sequential_Tasks_2",
            "Sequential_Tasks_3",
            "Sequential_Tasks_4",
            "Sequential_Tasks_5",
            "Sequential_Tasks_6",
            "Sequential_Tasks_7",
        )

        /** 服务端在 SSE 里回的 requestId 形状：`cmb-` 前缀 32 位 hex，或裸 32 位 hex。 */
        private val SERVER_REQUEST_ID = Regex("^(cmb-)?[0-9a-f]{32}$")

        /** 成长中心：任务、Buddy 旅行、盲盒都在这个前缀下。 */
        const val PATH_GROWTH = "/v2/activity/growth"

        /** 抽奖单次最多抽几次（官方客户端有硬上限，这里取一个保守值）。 */
        const val MAX_LOTTERY_DRAWS = 10

        /** 夜猫子每两次对话之间的间隔（上游按时间窗口计数，太密会被忽略）。 */
        private const val NIGHT_CHAT_GAP_MS = 4_000L

        /** 官方默认模型（`name = "Auto"`）。 */
        const val DEFAULT_MODEL_ID = "default-model"

        /** 设备授权等待中的业务码。 */
        const val CODE_LOGIN_PENDING = 11217L

        const val PRODUCT_CODE = "p_tcaca"

        /** 余额不足关键词（上游没有稳定错误码，按文案兜底）。 */
        private val QUOTA_MARKERS = listOf(
            "insufficient credit", "no credit", "credit exhausted", "out of credit",
            "quota exceeded", "quota exhaust", "payment required", "credit not enough",
            "not enough credit", "积分不足", "额度不足", "余额不足", "积分用完", "额度用尽", "没有积分",
        )

        private val SESSION_DEAD_MARKERS = listOf("Offline user session not found", "12153")

        /**
         * 内置模型快照：上游拉不到时兜底。
         *
         * 数据源是官方 CLI（`@tencent-ai/codebuddy-code`）自带的 `product*.json`，
         * **国内版与国际版是两份不同的清单**——同名模型的倍率并不一样，
         * 例如 `deepseek-v4.1-flash` 国内 `x0.03`、国际 `x0.00`（限免）。
         * 所以必须按账号区域取对应那份，不能混用。
         */
        val FALLBACK_MODELS_CN: List<ProviderModel> by lazy {
            loadCatalog("codebuddy-models-cn.json") ?: MINIMAL_MODELS
        }

        val FALLBACK_MODELS_INTL: List<ProviderModel> by lazy {
            loadCatalog("codebuddy-models-intl.json") ?: MINIMAL_MODELS
        }

        /** 极简兜底：资源文件读不到时才用。 */
        private val MINIMAL_MODELS: List<ProviderModel> = listOf(
            ProviderModel("default-model", "Auto", 176_000),
            ProviderModel("glm-5.2", "GLM-5.2", 1_000_000),
            ProviderModel("kimi-k2.6", "Kimi-K2.6", 256_000),
            ProviderModel("deepseek-v4.1-flash", "DeepSeek-V4.1-Flash", 96_000),
        )

        /** 解析打包的官方模型目录；任何异常都返回 null 交给上游实时接口。 */
        fun loadCatalog(resource: String): List<ProviderModel>? = try {
            CodeBuddyProvider::class.java.classLoader
                ?.getResourceAsStream(resource)
                ?.use { input ->
                    val text = input.readBytes().toString(Charsets.UTF_8)
                    val root = JsonParser.parseString(text).asJsonObject
                    val models = root.arrayOrNull("models") ?: return@use null
                    models.mapNotNull { element ->
                        val item = runCatching { element.asJsonObject }.getOrNull()
                            ?: return@mapNotNull null
                        val id = item.get("id")?.asString.orEmpty()
                        if (id.isEmpty()) return@mapNotNull null
                        ProviderModel(
                            id = id,
                            name = item.get("name")?.asString.orEmpty().ifEmpty { id },
                            contextWindow = item.get("maxInputTokens")?.asLong ?: 0L,
                            extra = buildMap {
                                parseCreditsStatic(item.get("credits")?.asString)?.let {
                                    put("rate", "$it×")
                                }
                                item.get("maxOutputTokens")?.asLong?.takeIf { it > 0 }?.let {
                                    put("maxOutput", it.toString())
                                }
                                if (item.get("supportsReasoning")?.asBoolean == true) put("reasoning", "true")
                                if (item.get("supportsImages")?.asBoolean == true) put("vision", "true")
                                if (item.get("supportsToolCall")?.asBoolean == true) put("tools", "true")
                                item.get("vendor")?.asString?.takeIf { it.isNotBlank() }?.let {
                                    put("vendor", it)
                                }
                                item.get("descriptionZh")?.asString?.takeIf { it.isNotBlank() }?.let {
                                    put("desc", it)
                                }
                            },
                        )
                    }.takeIf { it.isNotEmpty() }
                }
        } catch (_: Exception) {
            null
        }

        /** 测试入口：国内版内置目录。 */
        fun fallbackCnForTest(): List<ProviderModel> = FALLBACK_MODELS_CN

        /** 测试入口：国际版内置目录。 */
        fun fallbackIntlForTest(): List<ProviderModel> = FALLBACK_MODELS_INTL

        /** 解析 `"x0.79 credits"` 这类串；不合法返回 null。 */
        fun parseCreditsStatic(raw: String?): String? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            val token = text.removePrefix("x").removePrefix("X").trim().split(' ').firstOrNull().orEmpty()
            val value = token.toDoubleOrNull() ?: return null
            if (!value.isFinite() || value < 0) return null
            return if (value == value.toLong().toDouble()) value.toLong().toString()
            else value.toString().trimEnd('0').trimEnd('.')
        }

        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 300_000
        private const val MAX_BODY_BYTES = 1 shl 20
    }
}

/**
 * 改写发往 CodeBuddy 的请求体：
 * 1. 强制 `stream:true`（上游拒绝非流式）；
 * 2. `tool_choice` 归一化成字符串（上游该字段是 string，对象形式会 400）。
 */
internal fun prepareCodeBuddyBody(src: String): String {
    val obj = runCatching { JsonParser.parseString(src).asJsonObject }.getOrNull() ?: return src
    obj.addProperty("stream", true)
    normalizeCodeBuddyToolChoice(obj)
    return obj.toString()
}

internal fun normalizeCodeBuddyToolChoice(obj: JsonObject) {
    val choice = obj.get("tool_choice") ?: return
    when {
        choice.isJsonPrimitive -> {
            if (choice.asString.equals("none", ignoreCase = true)) {
                obj.remove("tool_choice")
                obj.remove("tools")
                obj.remove("functions")
            }
        }
        choice.isJsonObject -> {
            val type = choice.asJsonObject.get("type")?.asString.orEmpty().lowercase()
            when (type) {
                "none" -> {
                    obj.remove("tool_choice")
                    obj.remove("tools")
                    obj.remove("functions")
                }
                "auto", "required" -> obj.addProperty("tool_choice", type)
                "function" -> {
                    val name = choice.asJsonObject.objOrNull("function")?.get("name")?.asString.orEmpty()
                    obj.addProperty("tool_choice", name.ifEmpty { "auto" })
                }
                else -> obj.remove("tool_choice")
            }
        }
        else -> obj.remove("tool_choice")
    }
}

/** 解析响应体；不是合法 JSON 时返回 null。 */
private fun jsonOf(body: String): JsonObject? =
    runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()

/** 取第一个非空字段（兼容 camelCase / snake_case 两套命名）。 */
internal fun JsonObject.firstString(vararg keys: String): String {
    for (key in keys) {
        val value = get(key) ?: continue
        if (!value.isJsonPrimitive) continue
        val text = runCatching { value.asString }.getOrNull().orEmpty()
        if (text.isNotEmpty()) return text
    }
    return ""
}

internal fun JsonObject.firstLong(vararg keys: String): Long {
    for (key in keys) {
        val value = get(key) ?: continue
        if (!value.isJsonPrimitive) continue
        val number = runCatching { value.asLong }.getOrNull()
        if (number != null && number > 0) return number
    }
    return 0L
}

/**
 * 把上游响应体整理成一句可读的补充说明。
 *
 * 上游报错时只给 HTTP 状态码是不够的（400 可能是参数、头、或账号状态问题），
 * 必须把 `msg` 或响应原文带出来，用户才能反馈、我们才能定位。
 */
private fun describeBody(body: String): String {
    if (body.isBlank()) return ""
    val obj = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
    val message = obj?.firstString("msg", "message", "desc", "error") ?: ""
    if (message.isNotEmpty()) return "：$message"
    return "：${body.trim().take(160)}"
}

/**
 * 从 access_token（JWT）的 payload 里取某个声明；取不到返回空串。
 *
 * 登录响应里不一定带 uid，但 access_token 是 JWT，里面通常有 user_id ——
 * 靠这个兜底，已经存进账号池的旧凭证也能自动补上 uid。
 */
internal fun jwtClaim(accessToken: String, vararg keys: String): String {
    val payload = accessToken.split('.').getOrNull(1) ?: return ""
    if (payload.isEmpty()) return ""
    val json = runCatching {
        String(java.util.Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
    }.getOrNull() ?: return ""
    val obj = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return ""
    return obj.firstString(*keys)
}

/** 按顺序取第一个能解析成数字的字段（用于 credits / reward_credit 这类可能是字符串的字段）。 */
internal fun JsonObject.firstNumber(vararg keys: String): Double? {
    for (key in keys) {
        val value = get(key) ?: continue
        if (value.isJsonNull) continue
        if (value.isJsonPrimitive) {
            val prim = value.asJsonPrimitive
            if (prim.isNumber) return prim.asDouble
            prim.asString.trim().toDoubleOrNull()?.let { return it }
        }
    }
    return null
}

/** 生成一个小写 UUIDv4（幂等键用，每次请求都要全新）。 */
internal fun uuid4(): String = java.util.UUID.randomUUID().toString()
