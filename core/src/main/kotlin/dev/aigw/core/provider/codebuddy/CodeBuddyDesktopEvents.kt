package dev.aigw.core.provider.codebuddy

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.security.MessageDigest

/**
 * 桌面端 / Web 端「行为指纹」事件的构造。
 *
 * 上游点亮「需电脑端」类成长任务靠的不是独立端点，而是同一条 `POST /v2/report` 通道上
 * **不同的客户端指纹**：桌面端带 WorkBuddy 的 ide/ext 字段（走 `copilot.tencent.com`），
 * Web 端带浏览器形状的 os/userAgent（走官网域）。本对象只构造载荷，不做任何网络 IO。
 */
internal object CodeBuddyDesktopEvents {

    /** 实测桌面客户端 UA（5.5.6 内嵌 CLI 2.137.1）。 */
    const val DESKTOP_UA = "WorkBuddy/5.5.6 WorkBuddy/5.5.6 CLI/2.137.1"

    const val WEB_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/152.0.0.0 Safari/537.36"

    /** 企鹅教师助手：Buddy_App 与 Buddy_App_QQ 两个任务的共同判据应用。 */
    const val BUDDY_APP_ID = "cb_y5Dy46tPQGGWtueMxXbe"
    const val BUDDY_APP_NAME = "企鹅教师助手"

    /**
     * 由 uid 稳定派生 36 位 hex 设备标识（machineId / sessionId / qimei36 复用）。
     *
     * 同一账号每次生成相同值，模拟固定设备；换账号才变。
     */
    fun deriveId(uid: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$salt:$uid".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.substring(0, 36)
    }

    /** 桌面端公共指纹（注入每个事件，业务字段可覆盖同名键）。 */
    fun fingerprint(uid: String, nickname: String, now: Long): JsonObject = JsonObject().apply {
        addProperty("timezone", "Asia/Shanghai")
        addProperty("reportDelay", 2000)
        addProperty("userId", uid)
        addProperty("username", nickname)
        addProperty("userNickname", nickname)
        addProperty("product", "SaaS")
        addProperty("releaseDate", 1789036585355L)
        addProperty("commit", "5f9692923c93033111c51ad7b003eb80204a9b75")
        addProperty("ideName", "WorkBuddy")
        addProperty("ideType", "WorkBuddy")
        addProperty("ideVersion", "5.5.6")
        addProperty("machineId", deriveId(uid, "machine"))
        addProperty("sessionId", deriveId(uid, "session"))
        addProperty("extName", "workbuddy-desktop")
        addProperty("extVersion", "5.5.6")
        addProperty("os", "win32")
        addProperty("arch", "x64")
        addProperty("osVersion", "10.0.26220")
        addProperty("cpuCores", 20)
        addProperty("memorySize", 24)
        addProperty("timestamp", now)
        addProperty("presentAt", now)
    }

    /** 把公共指纹与业务载荷合并成一条事件（业务字段优先）。 */
    fun merge(fp: JsonObject, event: JsonObject): JsonObject = JsonObject().apply {
        fp.entrySet().forEach { add(it.key, it.value) }
        event.entrySet().forEach { add(it.key, it.value) }
    }

    private fun ev(code: String, block: JsonObject.() -> Unit = {}): JsonObject =
        JsonObject().apply {
            addProperty("eventCode", code)
            block()
        }

    /**
     * 一次「桌面端成功对话」的完整事件链。
     *
     * 上游对真实性有校验倾向（RichMeow_Chat 要求带成功回执），所以是六条而不是一条。
     */
    fun chatSequence(
        conversationId: String,
        requestId: String,
        messageId: String,
        modelId: String,
        modelName: String,
        now: Long,
    ): List<JsonObject> {
        val assistant = "$messageId-assistant"
        return listOf(
            ev("agent_task_created") {
                addProperty("source", "LOCAL")
                addProperty("name", "working")
                addProperty("task_target", "local")
                addProperty("mode", "craft")
                addProperty("requestModelId", modelId)
                addProperty("requestModelName", modelName)
                addProperty("has_repo", false)
                addProperty("repo_type", "none")
                addProperty("workspace_type", "empty")
                addProperty("has_connector", false)
                add("connector_types", JsonArray())
                addProperty("has_mention", false)
                add("mention_types", JsonArray())
                addProperty("has_template", false)
                addProperty("action", "")
                addProperty("template_name", "")
                addProperty("has_expert", false)
                addProperty("expert_id", "")
                addProperty("expert_name", "")
                addProperty("expert_industry_id", "")
                addProperty("has_skill", false)
                add("skill_names", JsonArray())
                addProperty("conversationId", conversationId)
                addProperty("messageId", messageId)
                addProperty("buddyId", "")
                addProperty("buddyName", "")
            },
            ev("chat_message_send") {
                addProperty("messageId", assistant)
                addProperty("historyCount", 0)
                addProperty("isContextTruncated", false)
                addProperty("currentStepCount", 1)
                addProperty("traceId", requestId)
                addProperty("rootRequestId", requestId)
                addProperty("parentConversationId", conversationId)
                addProperty("agentName", "cli")
                addProperty("agentType", "main")
            },
            ev("chat_request_send") {
                addProperty("inputLength", 24)
                addProperty("isPlan", false)
                addProperty("isAutoExecuteTerminal", false)
                addProperty("isAutoModify", false)
                addProperty("codebaseEnable", false)
                addProperty("maxToken", 0)
                addProperty("maxSteps", 500)
                addProperty("temperature", 0)
                addProperty("maxRetries", 0)
                add("mentionContexts", JsonArray())
                add("knowledgeId", JsonArray())
                add("knowledgeName", JsonArray())
                addProperty("codebaseId", "")
                addProperty("mentionContextCount", 0)
                addProperty("command", "")
                addProperty("recommendId", "")
                addProperty("skillId", "")
                addProperty("skillCount", 0)
                addProperty("totalCount", 0)
                addProperty("traceId", requestId)
                addProperty("rootRequestId", requestId)
                addProperty("parentConversationId", conversationId)
                addProperty("agentName", "cli")
                addProperty("agentType", "main")
                addProperty("codebuddy.session_id", conversationId)
                addProperty("codebuddy.conversation_request_id", requestId)
            },
            ev("chat_message_response") {
                addProperty("messageId", assistant)
                addProperty("responseModelId", modelId)
                addProperty("inputToken", 120)
                addProperty("outputToken", 80)
                addProperty("totalToken", 200)
                addProperty("cachedTokens", 0)
                addProperty("cachedWriteTokens", 0)
                addProperty("cachedMissTokens", 0)
                addProperty("isSuccessful", true)
                addProperty("messageErrorCode", "")
                addProperty("finishReason", "stop")
                addProperty("firstTokenAt", now)
                addProperty("traceId", requestId)
                addProperty("conversationId", conversationId)
                addProperty("rootRequestId", requestId)
                addProperty("parentConversationId", conversationId)
                addProperty("agentName", "cli")
                addProperty("agentType", "main")
                addProperty("codebuddy.session_id", conversationId)
                addProperty("codebuddy.conversation_request_id", requestId)
            },
            ev("chat_message_status") {
                addProperty("messageId", assistant)
                addProperty("messageErrorCode", "0")
                addProperty("traceId", requestId)
                addProperty("rootRequestId", requestId)
                addProperty("parentConversationId", conversationId)
                addProperty("agentName", "cli")
                addProperty("agentType", "main")
            },
            ev("chat_request_response") {
                addProperty("mode", "craft")
                addProperty("toolCallCount", 0)
                addProperty("inputToken", 120)
                addProperty("outputToken", 80)
                addProperty("totalToken", 200)
                addProperty("cachedTokens", 0)
                addProperty("cachedWriteTokens", 0)
                addProperty("cachedMissTokens", 0)
                addProperty("isSuccessful", true)
                addProperty("messageErrorCode", "")
                addProperty("finishReason", "stop")
                addProperty("rootRequestId", requestId)
                addProperty("parentConversationId", conversationId)
            },
        )
    }

    /** 「进入 Buddy 应用」五连事件（Buddy_App / Buddy_App_QQ 的判据）。 */
    fun buddyAppSequence(buddyId: String, buddyName: String): List<JsonObject> {
        fun base(code: String, block: JsonObject.() -> Unit = {}) = ev(code) {
            addProperty("mode", "LOCAL")
            addProperty("buddyId", buddyId)
            addProperty("buddyName", buddyName)
            block()
        }
        return listOf(
            base("buddyapp_discover_click"),
            base("buddyapp_show") {
                addProperty("elementId", buddyId)
                addProperty("elementName", buddyName)
                addProperty("position", 2)
            },
            base("buddyapp_enter_click") {
                addProperty("elementId", buddyId)
                addProperty("elementName", buddyName)
                addProperty("position", 2)
                addProperty("isFirstPage", "1")
            },
            base("buddyapp_auth_confirm_click") {
                addProperty("elementId", buddyId)
                addProperty("elementName", buddyName)
            },
            base("buddyapp_bindaccount_skip_click") {
                addProperty("elementId", buddyId)
                addProperty("elementName", buddyName)
            },
        )
    }

    /** 「定时任务创建成功」事件（automation_1 的判据）。 */
    fun automationCreateEvent(name: String): JsonObject = ev("automated_task_create_suc") {
        addProperty("name", name)
        addProperty("source", "manually")
        addProperty("modelId", "fast-model")
        addProperty("modelIsThinking", true)
        addProperty("connectorCount", 0)
        addProperty("skills", "")
        addProperty("skillCount", 0)
        addProperty("scheduleType", "once")
        addProperty("mode", "LOCAL")
    }

    /** 「使用模板创建任务」事件组（template_5 的判据）。 */
    fun templateUseSequence(
        conversationId: String,
        requestId: String,
        templateId: String,
        templateName: String,
        now: Long,
    ): List<JsonObject> {
        val events = chatSequence(conversationId, requestId, "msg-$templateId", "fast-model", "fast-model", now)
        return events + listOf(
            ev("agent_task_created_with_template") {
                addProperty("mode", "working")
                addProperty("isCustomModel", false)
                addProperty("id", templateId)
                addProperty("name", templateName)
                addProperty("requestId", requestId)
            },
            ev("template_used") {
                addProperty("template_id", templateId)
                addProperty("task_mode", "working")
            },
        )
    }

    /** 「灵感案例做同款」事件组（playbook_prompt 的判据）。 */
    fun playbookPromptSequence(
        conversationId: String,
        requestId: String,
        caseId: String,
        caseName: String,
        now: Long,
    ): List<JsonObject> {
        val events = chatSequence(conversationId, requestId, "msg-pb", "fast-model", "fast-model", now)
        return events + listOf(
            ev("web_element_click") {
                addProperty("pageName", "playbook_detail")
                addProperty("elementId", "playbook_ctaClick")
                addProperty("elementName", caseName)
                addProperty("source", "discover")
            },
            ev("playbook_cta_click") {
                addProperty("source", "discover")
                addProperty("position", 0)
                addProperty("id", caseId)
                addProperty("name", caseName)
                addProperty("type", "document")
                addProperty("categoryId", "")
                addProperty("categoryName", "")
            },
            ev("playbook_prompt_send") {
                addProperty("conversationId", conversationId)
                addProperty("requestId", requestId)
                addProperty("id", caseId)
                addProperty("name", caseName)
                addProperty("type", "document")
                addProperty("categoryId", "")
                addProperty("categoryName", "")
            },
        )
    }

    /** 「设计创意画布」事件组（create_canvas 的判据）。 */
    fun designCanvasSequence(
        conversationId: String,
        requestId: String,
        now: Long,
    ): List<JsonObject> {
        val events = chatSequence(conversationId, requestId, "msg-canvas", "fast-model", "fast-model", now)
        val suffix = requestId.takeLast(8)
        return events + listOf(
            ev("wbx_design_canvas_task_create") {
                addProperty("conversationId", conversationId)
                addProperty("requestId", requestId)
                addProperty("source", "summon_keyword")
                addProperty("cost", 12000)
                addProperty("isSuccessful", true)
            },
            ev("wbx_design_canvas_open") {
                addProperty("conversationId", conversationId)
                addProperty("requestId", requestId)
                addProperty("id", "ardot-file-$suffix")
                addProperty("source", "summon_keyword")
                addProperty("type", "page")
                addProperty("cost", 13000)
                addProperty("isSuccessful", true)
            },
        )
    }

    /** 专家市场里的一个专家。 */
    data class MarketExpert(
        val expertId: String,
        val expertType: String,
        val displayNameZh: String,
        val professionZh: String,
        val version: String,
        val categories: List<String>,
    ) {
        val category: String get() = categories.firstOrNull().orEmpty().ifEmpty { "expert-all" }
        val effectiveVersion: String get() = version.ifEmpty { "1.0.0" }
    }

    /** 「召唤平台专家」事件组（expert_summon 的判据）。 */
    fun expertSummonSequence(expert: MarketExpert): List<JsonObject> = listOf(
        ev("web_element_click") {
            addProperty("source", expert.expertId)
            addProperty("type", expert.category)
            addProperty("version", expert.effectiveVersion)
            addProperty("elementId", "expert_summon_click")
            addProperty("elementName", "立即召唤")
            addProperty(
                "pageURL",
                "/C:/Program%20Files/WorkBuddy/resources/app.asar/renderer/index.html",
            )
        },
        ev("expert_summon_click") {
            addProperty("id", expert.expertId)
            addProperty("name", expert.displayNameZh)
            addProperty("expertTitle", expert.professionZh)
            addProperty("type", "expert-all")
            addProperty("position", 0)
            addProperty("expertType", expert.expertType)
            addProperty("version", expert.effectiveVersion)
            addProperty("mode", "LOCAL")
        },
        ev("expert_summoned") {
            addProperty("id", expert.expertId)
            addProperty("name", expert.displayNameZh)
            addProperty("expertTitle", expert.professionZh)
            addProperty("type", "expert-all")
        },
    )

    /**
     * 「专家真实使用」事件（expert_5 / Expert_team_use_3 的判据）。
     *
     * requestId 必须是服务端在对话流里返回的 id——自造的不计数。
     */
    fun expertActualUseEvent(
        expert: MarketExpert,
        conversationId: String,
        requestId: String,
        mode: String,
    ): JsonObject = ev("expert_actual_use") {
        addProperty("id", expert.expertId)
        addProperty("name", expert.displayNameZh)
        addProperty("expertTitle", expert.professionZh)
        addProperty("type", expert.category)
        addProperty("expertType", expert.expertType)
        addProperty("source", "builtin")
        addProperty("version", expert.effectiveVersion)
        addProperty("cost", 9000)
        addProperty("characterCount", 14)
        addProperty("conversationId", conversationId)
        addProperty("requestId", requestId)
        addProperty("messageId", "msg-" + requestId.takeLast(8))
        addProperty("requestModelId", "fast-model")
        addProperty("requestModelName", "fast-model")
        addProperty("mode", mode)
    }

    /** Web 端单事件（Library_read 等页面行为类任务的判据）。 */
    fun webEvent(
        eventCode: String,
        pageUrl: String,
        elementId: String,
        elementName: String,
        uid: String,
        nickname: String,
        enterpriseId: String,
        now: Long,
    ): JsonObject = ev(eventCode) {
        addProperty("timestamp", now)
        addProperty("reportDelay", 0)
        addProperty("pageURL", pageUrl)
        addProperty("elementId", elementId)
        addProperty("elementName", elementName)
        addProperty("os", "Win32")
        addProperty("arch", "")
        addProperty("osVersion", "10.0")
        addProperty("userAgent", WEB_UA)
        addProperty("machineId", deriveId(uid, "webmachine"))
        addProperty("userId", uid)
        addProperty("userNickname", nickname)
        addProperty("enterpriseId", enterpriseId)
    }

    /**
     * 「技能加载」事件（skill_1 的判据）。
     *
     * 必须 JOIN 一次真实会话的服务端 requestId，单发本事件不计数。
     */
    fun skillInfoEvent(
        conversationId: String,
        requestId: String,
        messageId: String,
        modelId: String,
        modelName: String,
    ): JsonObject = ev("skill_info") {
        addProperty("id", "润泽小馆·日报撰写")
        addProperty("skillId", "skill_2097350077599879168")
        addProperty("skillVersion", "1.0.0")
        addProperty("toolStatus", "success")
        addProperty("fileCount", 56)
        addProperty("source", "workbuddy-desktop")
        addProperty("conversationId", conversationId)
        addProperty("requestId", requestId)
        addProperty("messageId", messageId)
        addProperty("requestModelId", modelId)
        addProperty("requestModelName", modelName)
        addProperty("traceId", requestId)
    }

    /**
     * 「皮肤生效」事件（Hp_Appearance 的判据）。
     *
     * 客户端在主题生效状态下离开设置页时上报；只调 `appearance/set` 不发本事件不计分。
     */
    fun appearanceSkinApply(resourceKey: String): JsonObject = ev("appearance_skin_apply") {
        addProperty("action", "apply")
        addProperty("source", "settings_close")
        addProperty("id", resourceKey)
        addProperty("vipLevel", 0)
        addProperty("series", "")
        addProperty("type", "unknown")
    }

    // ------------------------------------------------------------------ 小程序端

    /**
     * 小程序埋点公共指纹。
     *
     * 走 billingBase 的 `/v2/report`，与桌面端是两套 ide/ext 字段：`ideType` 为
     * `WorkBuddy_MP`、`extName` 为 `workbuddy-mp`、`platform` 为 `mini_program`。
     */
    fun mpFingerprint(uid: String, nickname: String, now: Long): JsonObject = JsonObject().apply {
        addProperty("timestamp", now)
        addProperty("ideType", "WorkBuddy_MP")
        addProperty("ideVersion", "2.4.0")
        addProperty("extName", "workbuddy-mp")
        addProperty("extVersion", "2.4.0")
        addProperty("product", "SaaS")
        addProperty("ideName", "wx_app_cloud")
        addProperty("platform", "mini_program")
        addProperty("os", "windows")
        addProperty("osVersion", "11")
        addProperty("arch", "x64")
        addProperty("machineId", "0655736a-607f-4d9d-b430-58176ee9a090")
        addProperty("timezone", "Asia/Shanghai")
        addProperty("userId", uid)
        addProperty("userNickname", nickname)
    }

    /**
     * 小程序「一次对话」事件（`Sequential_Tasks_1/3/6` 与校园日的判据载体）。
     *
     * [activityId] 非空时追加 `activityId`——校园日实测不带该字段不点亮；
     * [modelId]／[modelName] 非空时追加模型字段，供 `Sequential_Tasks_5` 使用
     * （裸对话事件不带模型，模型任务须用本形态）。
     */
    fun mpChatEvent(
        conversationId: String,
        activityId: String,
        modelId: String,
        modelName: String,
    ): JsonObject {
        val requestId = "aigw-" + deriveId(conversationId, "mpreq").take(32)
        return ev("chat_request_send") {
            addProperty("inputLength", 14)
            addProperty("isPlan", false)
            addProperty("isAutoExecuteTerminal", false)
            addProperty("isAutoModify", false)
            addProperty("codebaseEnable", false)
            addProperty("maxToken", 0)
            addProperty("maxSteps", 500)
            addProperty("temperature", 0)
            addProperty("maxRetries", 0)
            add("mentionContexts", JsonArray())
            add("knowledgeId", JsonArray())
            add("knowledgeName", JsonArray())
            addProperty("codebaseId", "")
            addProperty("mentionContextCount", 0)
            addProperty("command", "")
            addProperty("recommendId", "")
            addProperty("skillId", "")
            addProperty("skillCount", 0)
            addProperty("totalCount", 0)
            addProperty("traceId", requestId)
            addProperty("rootRequestId", requestId)
            addProperty("parentConversationId", conversationId)
            addProperty("conversationId", conversationId)
            addProperty("messageId", "msg-" + requestId.takeLast(8))
            addProperty("agentName", "mp")
            addProperty("agentType", "main")
            addProperty("codebuddy.session_id", conversationId)
            addProperty("codebuddy.conversation_request_id", requestId)
            if (activityId.isNotEmpty()) addProperty("activityId", activityId)
            if (modelId.isNotEmpty()) addProperty("requestModelId", modelId)
            if (modelName.isNotEmpty()) addProperty("requestModelName", modelName)
        }
    }

    /**
     * 小程序「选中专家并完成有效对话」事件（`Sequential_Tasks_2` 判据）。
     *
     * 与 school 域的同名事件是两套口径：不带 conversationId／activityId、
     * `extVersion` 用小程序自身 2.2.8、`type` 固定 `send_message`。
     */
    fun mpExpertUseEvent(expertId: String, expertName: String, expertType: String): JsonObject =
        ev("expert_actual_use") {
            addProperty("reportDelay", 0)
            addProperty("extVersion", "2.2.8")
            addProperty("source", "mini_program")
            addProperty("id", expertId)
            addProperty("name", expertId)
            addProperty("expertTitle", expertName.ifEmpty { expertId })
            addProperty("type", "send_message")
            addProperty("characterCount", 12)
            addProperty("expertType", expertType.ifEmpty { "agent" })
        }

    /** 小程序「灵感案例」事件组（`Sequential_Tasks_7` 的 mp 形态载体）。 */
    fun mpPlaybookEvents(caseId: String, caseName: String, conversationId: String): List<JsonObject> {
        fun caseFields(): JsonObject = JsonObject().apply {
            addProperty("id", caseId)
            addProperty("name", caseName)
            addProperty("type", "document")
            addProperty("categoryId", "")
            addProperty("categoryName", "")
            addProperty("skills", "")
            addProperty("skillNames", "")
        }
        return listOf(
            ev("playbook_cta_click") {
                caseFields().entrySet().forEach { add(it.key, it.value) }
                addProperty("source", "discover")
                addProperty("position", 1)
                addProperty("extVersion", "2.2.8")
            },
            ev("playbook_prompt_send") {
                caseFields().entrySet().forEach { add(it.key, it.value) }
                addProperty("source", "discover")
                addProperty("promptLength", 96)
                addProperty("isOfficial", 1)
                addProperty("conversationId", conversationId)
                addProperty("extVersion", "2.2.8")
            },
        )
    }
}
