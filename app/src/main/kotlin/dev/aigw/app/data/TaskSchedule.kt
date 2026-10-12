package dev.aigw.app.data

import dev.aigw.core.provider.ACTION_ACTIVITY
import dev.aigw.core.provider.ACTION_AUTO_TASKS
import dev.aigw.core.provider.ACTION_BLACKCAT
import dev.aigw.core.provider.ACTION_CHECKIN
import dev.aigw.core.provider.ACTION_TASKS
import dev.aigw.core.store.KeyValueStore
import java.util.Calendar

/**
 * 自动化任务类别。时点写死在 [hours]（沿用 workbuddy2api 的默认排程），界面上只给开关。
 *
 * [actions] 按顺序执行：签到时段顺带把成长中心的可领内容一起领了——旅行礼物、连登兑奖、
 * 盲盒都挂在成长中心接口里，单独排一个时段只会重复请求同一批接口。
 */
enum class AutoTaskKind(
    val id: String,
    val label: String,
    val hours: List<Int>,
    val summary: String,
    val actions: List<String>,
) {
    CHECKIN(
        id = "checkin",
        label = "每日签到",
        hours = listOf(9, 21),
        summary = "签到并领取成长中心奖励（旅行、连登、盲盒）",
        actions = listOf(ACTION_CHECKIN, ACTION_TASKS),
    ),
    ACTIVITY(
        id = "activity",
        label = "活跃上报",
        hours = listOf(10),
        summary = "上报一次对话活跃，维持连登天数",
        actions = listOf(ACTION_ACTIVITY),
    ),
    GROWTH(
        id = "growth",
        label = "成长任务",
        hours = listOf(1),
        summary = "一键补齐并领取成长任务（逐项上报，耗时较长）",
        actions = listOf(ACTION_AUTO_TASKS),
    ),
    BLACKCAT(
        id = "blackcat",
        label = "夜猫子补足",
        hours = listOf(23),
        summary = "23:00–08:00 窗口内补齐夜猫子对话",
        actions = listOf(ACTION_BLACKCAT),
    ),
    ;

    /** 形如「09:00、21:00」，供界面展示时点。 */
    val hourText: String
        get() = hours.joinToString("、") { it.toString().padStart(2, '0') + ":00" }
}

/** 自动化任务开关的持久化；缺省全开，与 workbuddy2api 的默认排程一致。 */
class TaskScheduleStore(private val store: KeyValueStore) {

    fun enabled(kind: AutoTaskKind): Boolean = store.read(key(kind)) != "0"

    fun setEnabled(kind: AutoTaskKind, enabled: Boolean) {
        store.write(key(kind), if (enabled) "1" else "0")
    }

    /** 当前开启的全部类别，按枚举顺序。 */
    fun enabledKinds(): List<AutoTaskKind> = AutoTaskKind.entries.filter { enabled(it) }

    private fun key(kind: AutoTaskKind) = "settings/tasks.${kind.id}"
}

/**
 * 从 [now] 起往后找最近一个会触发的整点；[enabled] 为空时返回 null。
 *
 * 排程器与界面共用同一份计算，避免「界面显示的时间」与「实际触发时间」两套逻辑走岔。
 */
fun nextAutoTaskAt(enabled: Collection<AutoTaskKind>, now: Long = System.currentTimeMillis()): Long? {
    if (enabled.isEmpty()) return null
    val calendar = Calendar.getInstance()
    for (offset in 0..LOOKAHEAD_HOURS) {
        calendar.timeInMillis = now
        calendar.add(Calendar.HOUR_OF_DAY, offset)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val at = calendar.timeInMillis
        if (at < now) continue
        if (enabled.any { calendar.get(Calendar.HOUR_OF_DAY) in it.hours }) return at
    }
    return null
}

/** 「下次执行时间」往后找一周足够（最短周期是按天）。 */
private const val LOOKAHEAD_HOURS = 24 * 7
