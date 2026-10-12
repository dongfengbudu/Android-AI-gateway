package dev.aigw.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.aigw.app.MainActivity
import dev.aigw.app.R
import dev.aigw.app.AiGatewayApp
import dev.aigw.app.data.AutoTaskGate
import dev.aigw.app.data.AutoTaskKind
import dev.aigw.app.data.TaskScheduleStore
import dev.aigw.app.data.nextAutoTaskAt
import dev.aigw.app.gatewayEngine
import dev.aigw.core.gateway.GatewayEngine
import dev.aigw.core.provider.ProviderActionResult
import dev.aigw.core.provider.ProviderCapability
import dev.aigw.core.usage.OperationLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 只负责「让进程活着」：网关本身由 UI 侧启动，成功后拉起本服务，
 * 这样锁屏或切后台后端口仍有人监听。
 */
class GatewayService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var scheduler: AutoTaskScheduler? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = applicationContext.gatewayEngine
        // 通知必须最先发：startForegroundService 拉起的服务不调 startForeground
        // 会被系统判定超时直接崩溃（ForegroundServiceDidNotStartInTime），
        // 而且「服务已启动」本身就是用户要的反馈，不应等引擎验证完才弹。
        // NanoHTTPD.start(timeout=0) 会同步等 bind 完成/抛出，engine.start() 返回
        // 即端口已就绪，所以这里不需要任何先导耗时操作。
        startForeground(NOTIFICATION_ID, buildNotification(engine.settings().port))
        val started = engine.isRunning() || runCatching { engine.start() }.isSuccess
        if (started) {
            engine.operationLog.info(OperationLog.CATEGORY_REQUEST, "前台服务已拉起，通知已发布")
            acquireLocks()
            // 定时任务挂在服务上：服务活着才排程，进程被回收后不重建（与网关存活策略一致）。
            if (scheduler == null) scheduler = AutoTaskScheduler(engine).also { it.start() }
            // START_NOT_STICKY：进程被杀后不自重建（自重建是灰色软件检测的典型特征）。
            // 代价：系统回收后网关不会自动恢复，用户需重新打开 App 或点磁贴。
            return START_NOT_STICKY
        }
        // 引擎没起来：撤掉刚发的通知再退出，避免「通知说运行中、端口没人听」
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scheduler?.stop()
        scheduler = null
        releaseLocks()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 锁屏后 CPU 与 Wi-Fi 会进省电，已有的 TCP 连接会被挂起，客户端只会看到「连接断开」；
     * 前台服务只保证进程不被杀，不保证网络与 CPU 常醒，所以这里显式持锁。
     */
    private fun acquireLocks() {
        if (wakeLock == null) {
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (wifiLock == null) {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifi.createWifiLock(mode, WIFI_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        wifiLock = null
    }

    private fun buildNotification(port: Int): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, AiGatewayApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_gateway)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("本地网关运行中 · 端口 $port")
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        private const val WAKE_LOCK_TAG = "aigw:gateway"
        private const val WIFI_LOCK_TAG = "aigw:gateway-wifi"

        /**
         * UI 与磁贴共用的启动入口：拉起前台服务并确保引擎已运行。
         * 成功返回 null，失败返回错误文案。
         */
        fun start(context: Context): String? {
            ContextCompat.startForegroundService(context, Intent(context, GatewayService::class.java))
            return runCatching { context.gatewayEngine.start() }
                .exceptionOrNull()?.let { it.message ?: "启动失败" }
        }

        /** UI 与磁贴共用的停止入口：停引擎并撤掉前台服务。 */
        fun stop(context: Context) {
            runCatching { context.gatewayEngine.stop() }
            context.stopService(Intent(context, GatewayService::class.java))
        }
    }
}

/**
 * 自动化任务的整点排程，跟着前台服务活着。
 *
 * 每个「日期-小时-任务」只触发一次，跨天自动清标记；同一时段内按账号串行、账号之间留间隔，
 * 避免多账号并发踩上游风控。任务本身耗时可能几分钟（成长任务要逐项上报并回读），
 * 所以每个类别单独起协程，不阻塞排程循环。
 */
class AutoTaskScheduler(private val engine: GatewayEngine) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val schedule = TaskScheduleStore(engine.store())

    /** 已触发的「日期-小时-任务」标记，跨天清空。 */
    private val fired = HashSet<String>()
    private var firedDay = ""

    private var loop: Job? = null

    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            while (isActive) {
                runCatching { tick() }.onFailure {
                    engine.operationLog.warn(OperationLog.CATEGORY_REQUEST, "定时任务排程异常：${it.message}")
                }
                delay(TICK_MS)
            }
        }
    }

    fun stop() {
        loop?.cancel()
        loop = null
    }

    /** 下一个执行时刻（毫秒）；全部关闭时返回 null。 */
    fun nextRunAt(now: Long = System.currentTimeMillis()): Long? =
        nextAutoTaskAt(schedule.enabledKinds(), now)

    private fun tick() {
        val now = System.currentTimeMillis()
        val day = dayKey(now)
        if (day != firedDay) {
            fired.clear()
            firedDay = day
        }
        val hour = hourOf(now)
        for (kind in AutoTaskKind.entries) {
            if (hour !in kind.hours || !schedule.enabled(kind)) continue
            val key = "$day-$hour-${kind.id}"
            if (key in fired) continue
            // 手动「一键完成成长任务」可能正在跑（共用 AutoTaskGate）。拿不到闸门就留到下个 tick 重试，
            // 不标 fired——否则手动跑一次就会把该时段的排程整个吞掉。
            if (!AutoTaskGate.tryAcquire()) continue
            fired.add(key)
            scope.launch {
                try {
                    runCatching { runKind(kind) }.onFailure {
                        engine.operationLog.warn(OperationLog.CATEGORY_REQUEST, "${kind.label}执行异常：${it.message}")
                    }
                } finally {
                    AutoTaskGate.release()
                }
            }
        }
    }

    private suspend fun runKind(kind: AutoTaskKind) {
        for (provider in engine.providers()) {
            if (!provider.enabled || ProviderCapability.TASKS !in provider.capabilities) continue
            for (account in engine.accounts(provider.id)) {
                if (!account.usable) continue
                val who = "${provider.displayName}/${account.nickname.ifBlank { account.uid.take(8) }}"
                for (action in kind.actions) {
                    val result = runCatching { engine.performAction(provider.id, account.uid, action) }
                        .getOrElse { ProviderActionResult.failure(it.message ?: "执行异常") }
                    // 供应商不支持该动作属常态（如国际版没有签到），不记日志、也不影响后续动作。
                    if (result.unsupported) continue
                    val outcome = if (result.ok) result.message.ifBlank { "完成" } else "失败·${result.message}"
                    engine.operationLog.info(OperationLog.CATEGORY_REQUEST, "${kind.label}·$who：$outcome")
                    delay(ACCOUNT_GAP_MS)
                }
            }
        }
    }

    private fun hourOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)

    private fun dayKey(millis: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        return "%04d-%02d-%02d".format(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )
    }

    private companion object {
        /** 轮询间隔：wb2api 也是按分钟核对挂钟，而不是按定时器睡眠到整点。 */
        const val TICK_MS = 60_000L

        /** 同一账号连续两个动作之间的间隔。 */
        const val ACCOUNT_GAP_MS = 3_000L
    }
}
