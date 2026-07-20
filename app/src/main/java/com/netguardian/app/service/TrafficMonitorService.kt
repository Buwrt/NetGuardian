package com.netguardian.app.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.netguardian.app.core.AntiLeakEngine
import com.netguardian.app.core.TrafficMonitorEngine
import com.netguardian.app.data.AppDatabase
import com.netguardian.app.data.TrafficDao
import com.netguardian.app.model.NetworkSpeedInfo
import com.netguardian.app.model.TrafficLimit
import com.netguardian.app.model.TrafficRecord
import com.netguardian.app.util.Constants
import com.netguardian.app.util.DataStoreHelper
import com.netguardian.app.util.TrafficFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 流量监控前台服务
 * 作为应用的核心后台服务，负责持续采集和监控网络流量
 *
 * 功能清单：
 * 1. 以前台服务方式运行，显示"流量监控中"常驻通知
 * 2. 根据权限模式定时采集所有应用流量数据（Root:5秒, Shizuku:10秒, 无权限:5秒）
 * 3. 将采集到的流量数据存入Room数据库（TrafficRecord）
 * 4. 调用AntiLeakEngine检测后台偷跑流量
 * 5. 检测流量限额超限并触发提醒通知/自动断网
 * 6. 通过StateFlow实时暴露网速信息，供绑定的Activity订阅
 * 7. 通过NotificationManager显示限额超限提醒弹窗
 * 8. 支持bindService，让Activity获取实时数据和Service控制接口
 *
 * foregroundServiceType = FOREGROUND_SERVICE_DATA_SYNC
 *
 * 纯本地离线运行，不进行任何网络请求
 */
class TrafficMonitorService : Service() {

    companion object {
        private const val TAG = "TrafficMonitorService"
    }

    // ==================== 核心组件 ====================

    /** 流量监控引擎 - 负责底层流量数据采集 */
    private lateinit var trafficMonitorEngine: TrafficMonitorEngine

    /** 防偷跑引擎 - 负责检测后台异常流量 */
    private lateinit var antiLeakEngine: AntiLeakEngine

    /** 数据库访问对象 */
    private lateinit var trafficDao: TrafficDao

    /** 通知管理器 */
    private lateinit var notificationManager: android.app.NotificationManager

    // ==================== 协程管理 ====================

    /** 服务级协程Scope，使用SupervisorJob避免子协程互相影响 */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 流量采集定时任务Job */
    private var collectJob: Job? = null

    /** 网速监测定时任务Job */
    private var speedMonitorJob: Job? = null

    /** 偷跑检测定时任务Job */
    private var antiLeakJob: Job? = null

    // ==================== 实时数据 ====================

    /** 当前网速信息的MutableStateFlow，供内部更新 */
    private val _networkSpeedInfo = MutableStateFlow<NetworkSpeedInfo?>(
        NetworkSpeedInfo(downloadSpeed = 0L, uploadSpeed = 0L, networkType = "None")
    )

    /** 对外暴露的网速信息StateFlow，供绑定的Activity观察 */
    val networkSpeedInfo: StateFlow<NetworkSpeedInfo?> = _networkSpeedInfo.asStateFlow()

    /** 服务是否正在运行的标记 */
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // ==================== 网速计算用的上次采样数据 ====================

    /** 上一次系统总接收字节数 */
    private var lastTotalRx: Long = 0L

    /** 上一次系统总发送字节数 */
    private var lastTotalTx: Long = 0L

    /** 上一次采样时间戳 */
    private var lastSpeedTimestamp: Long = 0L

    /** 上一次清理旧数据的时间戳 */
    private var lastCleanupTime: Long = 0L

    // ==================== 差值计算用的上次采样数据 ====================

    /** 每个UID的上次采样累计值：uid -> (rx, tx) */
    private val lastUidTraffic = mutableMapOf<Int, Pair<Long, Long>>()

    /** 上次流量采集时间戳 */
    private var lastCollectionTimestamp: Long = 0L

    // ==================== 通知管理 ====================

    /** 是否显示监控通知（用户可配置） */
    private var showMonitorNotification: Boolean = true

    // ==================== 服务生命周期 ====================

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "流量监控服务 onCreate")

        // 初始化核心组件
        trafficMonitorEngine = TrafficMonitorEngine(applicationContext)
        antiLeakEngine = AntiLeakEngine(applicationContext)
        trafficDao = AppDatabase.getInstance(applicationContext).trafficDao()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager

        // 创建通知渠道
        TrafficMonitorNotification.createNotificationChannel(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "流量监控服务 onStartCommand, action: ${intent?.action}")

        // 先创建通知并启动前台服务
        val notification = TrafficMonitorNotification.buildMonitoringNotification(
            applicationContext, _networkSpeedInfo.value
        )
        startForeground(Constants.NOTIFICATION_ID_MONITOR, notification)

        when (intent?.action) {
            Constants.ACTION_START_MONITOR -> {
                // 启动监控
                serviceScope.launch {
                    loadSettingsAndStartMonitoring()
                }
            }
            Constants.ACTION_STOP_MONITOR -> {
                // 停止监控
                stopMonitoring()
            }
            else -> {
                // 默认启动监控
                serviceScope.launch {
                    loadSettingsAndStartMonitoring()
                }
            }
        }

        // 返回START_STICKY表示系统杀死服务后会尝试重建
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "流量监控服务 onBind")
        return MonitorBinder()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "流量监控服务 onUnbind")
        // 不自动rebind，让Activity重新绑定
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "流量监控服务 onDestroy")
        stopMonitoring()
        // 取消服务级协程Scope
        serviceScope.cancel()
    }

    // ==================== 监控启动/停止 ====================

    /**
     * 加载用户设置并启动所有监控任务
     * 从DataStore读取配置后，根据权限模式选择采集间隔
     */
    private suspend fun loadSettingsAndStartMonitoring() = withContext(Dispatchers.IO) {
        try {
            // 刷新 Root 状态
            trafficMonitorEngine.refreshRootStatus()

            // 批量读取所有设置
            val settings = DataStoreHelper.getAllSettings(applicationContext)
            showMonitorNotification = settings.monitorNotification

            // 检查是否已在运行，避免重复启动
            if (_isRunning.value) {
                Log.w(TAG, "监控已在运行中，跳过重复启动")
                return@withContext
            }

            _isRunning.value = true
            Log.i(TAG, "========== 流量监控服务已启动 ==========")
            Log.i(TAG, "启动流量监控，权限模式: ${settings.privilegeMode}")

            // 初始化网速基准值
            initSpeedBaseline()

            // 启动流量采集任务
            startTrafficCollection(settings.privilegeMode)

            // 启动网速监测任务（每秒更新）
            startSpeedMonitor()

            // 如果启用了防偷跑，启动偷跑检测任务
            if (settings.antiLeakEnabled) {
                startAntiLeakDetection()
            }

            // 调度限额检测WorkManager
            scheduleLimitCheckWorker()

            Log.i(TAG, "流量监控已全面启动")
        } catch (e: CancellationException) {
            Log.w(TAG, "监控启动被取消")
        } catch (e: Exception) {
            Log.e(TAG, "启动监控失败: ${e.message}", e)
        }
    }

    /**
     * 停止所有监控任务并停止前台服务
     */
    private fun stopMonitoring() {
        Log.i(TAG, "停止流量监控...")

        // 取消所有协程任务
        collectJob?.cancel()
        collectJob = null
        speedMonitorJob?.cancel()
        speedMonitorJob = null
        antiLeakJob?.cancel()
        antiLeakJob = null

        // 取消限额检测WorkManager
        cancelLimitCheckWorker()

        // 重置网速信息
        _networkSpeedInfo.value = NetworkSpeedInfo(
            downloadSpeed = 0L, uploadSpeed = 0L, networkType = "None"
        )
        _isRunning.value = false

        // 停止前台服务并移除通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        notificationManager.cancel(Constants.NOTIFICATION_ID_MONITOR)

        // 停止服务自身
        stopSelf()

        Log.i(TAG, "流量监控已停止")
    }

    // ==================== 网速监测 ====================

    /**
     * 初始化网速计算的基准值
     * 取第一次采样的系统总流量作为基准
     */
    private fun initSpeedBaseline() {
        val (rx, tx) = trafficMonitorEngine.getSystemTotalTraffic()
        lastTotalRx = rx
        lastTotalTx = tx
        lastSpeedTimestamp = System.currentTimeMillis()
        Log.d(TAG, "网速基准初始化 - RX: $rx, TX: $tx")
    }

    /**
     * 启动网速监测定时任务
     * 每秒采样一次系统总流量差值，计算实时下载/上传速率
     * 更新StateFlow并刷新前台通知
     */
    private fun startSpeedMonitor() {
        speedMonitorJob?.cancel()
        speedMonitorJob = serviceScope.launch(Dispatchers.IO) {
            Log.d(TAG, "网速监测任务已启动")
            try {
                while (isActive) {
                    val currentTimestamp = System.currentTimeMillis()
                    val (currentRx, currentTx) = trafficMonitorEngine.getSystemTotalTraffic()

                    // 计算时间差（秒）
                    val timeDiffMs = currentTimestamp - lastSpeedTimestamp
                    val timeDiffSec = if (timeDiffMs > 0) timeDiffMs / 1000.0 else 1.0

                    // 计算实时速率（字节/秒）
                    val downloadSpeed = if (currentRx >= lastTotalRx) {
                        ((currentRx - lastTotalRx) / timeDiffSec).toLong()
                    } else {
                        // 流量计数器被重置（切换网络等）
                        0L
                    }

                    val uploadSpeed = if (currentTx >= lastTotalTx) {
                        ((currentTx - lastTotalTx) / timeDiffSec).toLong()
                    } else {
                        0L
                    }

                    val networkType = trafficMonitorEngine.detectNetworkType()

                    val speedInfo = NetworkSpeedInfo(
                        downloadSpeed = downloadSpeed,
                        uploadSpeed = uploadSpeed,
                        timestamp = currentTimestamp,
                        networkType = networkType
                    )

                    // 更新StateFlow，供绑定的Activity观察
                    _networkSpeedInfo.value = speedInfo

                    // 更新前台通知显示实时网速
                    if (showMonitorNotification) {
                        val updatedNotification = TrafficMonitorNotification.buildMonitoringNotification(
                            applicationContext, speedInfo
                        )
                        notificationManager.notify(
                            Constants.NOTIFICATION_ID_MONITOR, updatedNotification
                        )
                    }

                    // 更新基准值
                    lastTotalRx = currentRx
                    lastTotalTx = currentTx
                    lastSpeedTimestamp = currentTimestamp

                    delay(Constants.SPEED_SAMPLE_INTERVAL_MS)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "网速监测任务已取消")
            } catch (e: Exception) {
                Log.e(TAG, "网速监测任务异常: ${e.message}", e)
            }
        }
    }

    // ==================== 流量采集 ====================

    /**
     * 启动应用流量采集定时任务
     * 根据权限模式选择不同的采集间隔：
     * - Root模式：每5秒采集一次，精度高
     * - Shizuku模式：每10秒采集一次，精度中等
     * - 无权限模式：每5秒采集，使用TrafficStats基础API
     *
     * 每次采集完成后：
     * 1. 将数据存入Room数据库（TrafficRecord）
     * 2. 检查各应用流量限额
     *
     * @param privilegeMode 当前权限模式
     */
    private fun startTrafficCollection(privilegeMode: String) {
        collectJob?.cancel()

        // 根据权限模式决定采集间隔
        val intervalMs = when (privilegeMode) {
            Constants.MODE_ROOT -> Constants.ROOT_COLLECT_INTERVAL_MS
            Constants.MODE_SHIZUKU -> Constants.SHIZUKU_COLLECT_INTERVAL_MS
            else -> Constants.NONE_COLLECT_INTERVAL_MS
        }

        Log.i(TAG, "启动流量采集任务，间隔: ${intervalMs}ms，模式: $privilegeMode")

        collectJob = serviceScope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    // 采集所有应用的流量数据
                    collectAndSaveTrafficData()

                    // 采集间隔
                    delay(intervalMs)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "流量采集任务已取消")
            } catch (e: Exception) {
                Log.e(TAG, "流量采集任务异常: ${e.message}", e)
            }
        }
    }

    /**
     * 执行一次流量采集并保存到数据库（差值计算版）
     *
     * /proc/uid_stat/{uid}/tcp_rcv 和 tcp_snd 是自设备开机以来的累计值，
     * 不会按天重置。本方法通过差值计算得到真实的增量流量：
     * - 每次采集时记录每个 UID 的当前累计值
     * - 下次采集时用当前值减去上次值得到增量
     * - 增量值按日期存入 Room 数据库
     * - 如果增量异常（负值或超过1GB），视为计数器被重置，跳过该次记录
     */
    private suspend fun collectAndSaveTrafficData() {
        try {
            Log.i(TAG, "collectAndSaveTrafficData 被调用, lastUidTraffic size: ${lastUidTraffic.size}")
            val apps = trafficMonitorEngine.getInstalledApps(applicationContext)
            val mode = kotlinx.coroutines.runBlocking {
                com.netguardian.app.util.DataStoreHelper.getPrivilegeModeOnce(applicationContext)
            }

            // 获取当前所有UID的流量累计值
            val currentTraffic: Map<Int, Pair<Long, Long>> = when (mode) {
                Constants.MODE_ROOT -> {
                    val rootManager = com.netguardian.app.core.RootManager.getInstance()
                    rootManager.getAllUidTraffic()
                }
                Constants.MODE_SHIZUKU -> {
                    val shizukuManager = com.netguardian.app.core.ShizukuManager.getInstance()
                    // Shizuku模式：逐个读取已安装应用的流量
                    val map = mutableMapOf<Int, Pair<Long, Long>>()
                    for (app in apps) {
                        try {
                            val (rx, tx) = shizukuManager.getUidTrafficShizuku(app.uid)
                            if (rx > 0 || tx > 0) {
                                map[app.uid] = Pair(rx, tx)
                            }
                        } catch (e: Exception) {
                            // 跳过读取失败的应用
                        }
                    }
                    map
                }
                else -> {
                    // 普通模式：使用 TrafficStats API（也是累计值，同样需要差值计算）
                    val map = mutableMapOf<Int, Pair<Long, Long>>()
                    for (app in apps) {
                        try {
                            val rx = android.net.TrafficStats.getUidRxBytes(app.uid)
                            val tx = android.net.TrafficStats.getUidTxBytes(app.uid)
                            if (rx != -1L && tx != -1L && (rx > 0 || tx > 0)) {
                                map[app.uid] = Pair(rx, tx)
                            }
                        } catch (e: Exception) {
                            // 跳过
                        }
                    }
                    map
                }
            }

            if (currentTraffic.isEmpty() && mode != Constants.MODE_NONE) {
                Log.w(TAG, "Root/Shizuku模式未获取到流量数据")
                return
            }

            val currentTime = System.currentTimeMillis()
            val todayDate = TrafficFormatter.getTodayDate()
            var savedCount = 0

            // 获取当前网络类型（用于区分移动/WiFi）
            val cm = getSystemService(CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val wifiConnected = isWifiConnected(cm)
            val mobileConnected = isMobileConnected(cm)

            for ((uid, currentRxTx) in currentTraffic) {
                val currentRx = currentRxTx.first
                val currentTx = currentRxTx.second

                val lastRxTx = lastUidTraffic[uid]

                if (lastRxTx != null) {
                    // 计算增量
                    val deltaRx = currentRx - lastRxTx.first
                    val deltaTx = currentTx - lastRxTx.second

                    // 如果增量异常（负值或超过1GB单次），视为计数器被重置
                    if (deltaRx < 0 || deltaTx < 0 || deltaRx > 1024L * 1024L * 1024L || deltaTx > 1024L * 1024L * 1024L) {
                        // 计数器被重置，用当前值作为新的基准，不记录
                        lastUidTraffic[uid] = Pair(currentRx, currentTx)
                        Log.w(TAG, "UID $uid 计数器异常重置（deltaRx=$deltaRx, deltaTx=$deltaTx），更新基准值")
                        continue
                    }

                    if (deltaRx > 0 || deltaTx > 0) {
                        // 根据当前网络类型分配移动/WiFi
                        var mobileRx = 0L
                        var mobileTx = 0L
                        var wifiRx = 0L
                        var wifiTx = 0L

                        when {
                            wifiConnected && !mobileConnected -> {
                                wifiRx = deltaRx; wifiTx = deltaTx
                            }
                            mobileConnected && !wifiConnected -> {
                                mobileRx = deltaRx; mobileTx = deltaTx
                            }
                            else -> {
                                // 两者同时连接或都未连接，按比例分配或全归移动
                                mobileRx = deltaRx; mobileTx = deltaTx
                            }
                        }

                        // 查找对应的应用包名
                        val app = apps.find { it.uid == uid }
                        if (app != null) {
                            val record = TrafficRecord(
                                uid = uid,
                                packageName = app.packageName,
                                mobileRx = mobileRx,
                                mobileTx = mobileTx,
                                wifiRx = wifiRx,
                                wifiTx = wifiTx,
                                date = todayDate,
                                timestamp = currentTime
                            )
                            trafficDao.insertRecord(record)
                            savedCount++
                        }
                    }
                }

                // 更新上次采样值（无论是否记录增量，都要更新基准）
                lastUidTraffic[uid] = Pair(currentRx, currentTx)
            }

            lastCollectionTimestamp = currentTime

            if (savedCount > 0) {
                Log.d(TAG, "流量采集完成（差值计算），保存 $savedCount 条增量记录")
            }

            // 采集后检查流量限额（从数据库读取聚合数据）
            checkTrafficLimitsFromDb(apps)

            // 每24小时清理一次超过3个月的旧数据
            cleanupOldData()

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "流量采集异常: ${e.message}", e)
        }
    }

    /**
     * 清理超过3个月的旧数据
     * 每24小时执行一次
     */
    private suspend fun cleanupOldData() {
        if (System.currentTimeMillis() - lastCleanupTime > 24 * 60 * 60 * 1000L) {
            val threeMonthsAgo = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
            val deletedCount = trafficDao.deleteOldRecordsByTimestamp(threeMonthsAgo)
            lastCleanupTime = System.currentTimeMillis()
            if (deletedCount > 0) {
                Log.d(TAG, "已清理 $deletedCount 条3个月前的旧数据")
            }
        }
    }

    /** 判断WiFi是否连接 */
    private fun isWifiConnected(cm: android.net.ConnectivityManager?): Boolean {
        if (cm == null) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.type == android.net.ConnectivityManager.TYPE_WIFI
            }
        } catch (e: Exception) {
            false
        }
    }

    /** 判断移动数据是否连接 */
    private fun isMobileConnected(cm: android.net.ConnectivityManager?): Boolean {
        if (cm == null) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.type == android.net.ConnectivityManager.TYPE_MOBILE
            }
        } catch (e: Exception) {
            false
        }
    }

    // ==================== 偷跑检测 ====================

    /**
     * 启动防偷跑检测定时任务
     * 调用AntiLeakEngine检测后台应用的异常流量行为
     * 检测周期由Constants.BACKGROUND_CHECK_INTERVAL_MS控制（默认5秒）
     */
    private fun startAntiLeakDetection() {
        antiLeakJob?.cancel()
        Log.i(TAG, "启动防偷跑检测任务")

        antiLeakJob = serviceScope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    // 调用AntiLeakEngine执行一次检测
                    val blockedApps = antiLeakEngine.monitorAndBlockIdleApps()

                    // 如果有应用被断网，记录日志
                    if (blockedApps.isNotEmpty()) {
                        Log.w(TAG, "防偷跑检测：已断开 ${blockedApps.size} 个偷跑应用")
                    }

                    delay(Constants.BACKGROUND_CHECK_INTERVAL_MS)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "防偷跑检测任务已取消")
            } catch (e: Exception) {
                Log.e(TAG, "防偷跑检测任务异常: ${e.message}", e)
            }
        }
    }

    // ==================== 限额检测 ====================

    /**
     * 从数据库读取当前月份的聚合流量数据，检查限额是否超限
     * 替代原来的 checkTrafficLimits()，不再依赖实时累计值
     *
     * @param apps 已安装应用列表（用于包名查找）
     */
    private suspend fun checkTrafficLimitsFromDb(apps: List<com.netguardian.app.data.AppInfo>) {
        try {
            // 获取所有设置了限额的应用（包括全局限额）
            val appSpecificLimits = trafficDao.getAppSpecificLimits()
            val globalLimit = trafficDao.getGlobalLimit()

            if (appSpecificLimits.isEmpty() && globalLimit == null) {
                return // 没有设置任何限额，跳过检查
            }

            // 读取用户设置
            val autoDisconnect = DataStoreHelper.getAutoDisconnect(applicationContext)
            val limitReminder = DataStoreHelper.getLimitReminder(applicationContext)

            if (!limitReminder && !autoDisconnect) {
                return // 提醒和自动断网都关闭了，无需检查
            }

            // 从数据库查询当前月份的聚合流量（按包名聚合）
            val now = java.util.Calendar.getInstance()
            val monthPattern = "${now.get(java.util.Calendar.YEAR)}-${String.format("%02d", now.get(java.util.Calendar.MONTH) + 1)}"
            val monthRecords = trafficDao.getMonthlyAggregatedRecords(monthPattern)

            // 构建包名->聚合流量的映射
            val trafficMap = monthRecords.associate { record ->
                record.packageName to com.netguardian.app.model.AppTrafficInfo(
                    uid = record.uid,
                    packageName = record.packageName,
                    appName = apps.find { it.packageName == record.packageName }?.appName ?: record.packageName,
                    mobileRxBytes = record.mobileRx,
                    mobileTxBytes = record.mobileTx,
                    wifiRxBytes = record.wifiRx,
                    wifiTxBytes = record.wifiTx
                )
            }

            // 计算全局总流量（用于全局限额检查）
            var globalTotalBytes = 0L
            for (record in monthRecords) {
                globalTotalBytes += record.totalBytes
            }

            // 检查全局限额
            if (globalLimit != null && globalLimit.hasMobileLimit) {
                val limitBytes = globalLimit.mbToBytes(globalLimit.mobileLimitMb!!)
                if (globalTotalBytes > limitBytes) {
                    Log.w(TAG, "全局流量超限！已用 ${TrafficFormatter.formatBytes(globalTotalBytes)}，限额 ${TrafficFormatter.formatBytes(limitBytes)}")
                    if (limitReminder) {
                        TrafficMonitorNotification.showLimitExceedNotification(
                            context = applicationContext,
                            appName = "全部应用",
                            packageName = "__global__",
                            usedBytes = globalTotalBytes,
                            limitBytes = limitBytes,
                            networkType = "total",
                            notificationId = 0
                        )
                    }
                }
            }

            // 检查各应用限额
            for (limit in appSpecificLimits) {
                val appTraffic = trafficMap[limit.packageName] ?: continue
                var exceeded = false
                var networkType = "total"

                // 检查移动网络限额
                if (limit.hasMobileLimit) {
                    val limitBytes = limit.mbToBytes(limit.mobileLimitMb!!)
                    if (appTraffic.totalMobile > limitBytes) {
                        exceeded = true
                        networkType = "mobile"
                        Log.w(TAG, "${limit.appName} 移动流量超限！已用 ${TrafficFormatter.formatBytes(appTraffic.totalMobile)}，限额 ${TrafficFormatter.formatBytes(limitBytes)}")
                    }
                }

                // 检查WiFi限额
                if (!exceeded && limit.hasWifiLimit) {
                    val limitBytes = limit.mbToBytes(limit.wifiLimitMb!!)
                    if (appTraffic.totalWifi > limitBytes) {
                        exceeded = true
                        networkType = "wifi"
                        Log.w(TAG, "${limit.appName} WiFi流量超限！已用 ${TrafficFormatter.formatBytes(appTraffic.totalWifi)}，限额 ${TrafficFormatter.formatBytes(limitBytes)}")
                    }
                }

                if (exceeded) {
                    val usedBytes = appTraffic.totalBytes

                    // 发送超限提醒通知
                    if (limitReminder) {
                        TrafficMonitorNotification.showLimitExceedNotification(
                            context = applicationContext,
                            appName = limit.appName,
                            packageName = limit.packageName,
                            usedBytes = usedBytes,
                            limitBytes = if (networkType == "mobile") {
                                limit.mbToBytes(limit.mobileLimitMb!!)
                            } else {
                                limit.mbToBytes(limit.wifiLimitMb!!)
                            },
                            networkType = networkType,
                            notificationId = limit.packageName.hashCode() and 0xFFFF
                        )
                    }

                    // 自动断网（仅Root模式下有效）
                    if (autoDisconnect) {
                        val mode = DataStoreHelper.getPrivilegeMode(applicationContext)
                        if (mode == Constants.MODE_ROOT) {
                            autoDisconnectApp(limit.packageName, appTraffic.uid, networkType)
                        } else {
                            Log.w(TAG, "自动断网需要Root权限，当前模式: $mode")
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "限额检测异常: ${e.message}", e)
        }
    }

    /**
     * 自动断开超限应用的网络
     * 通过NetworkControlEngine设置网络权限来断开指定类型的网络
     *
     * @param packageName 应用包名
     * @param uid 应用UID
     * @param networkType 超限的网络类型: "mobile" / "wifi" / "total"
     */
    private suspend fun autoDisconnectApp(packageName: String, uid: Int, networkType: String) {
        try {
            val engine = com.netguardian.app.core.NetworkControlEngine(applicationContext)
            val blockMobile = networkType == "mobile" || networkType == "total"
            val blockWifi = networkType == "wifi" || networkType == "total"
            val success = engine.setAppNetworkPermission(
                packageName = packageName,
                uid = uid,
                allowMobile = !blockMobile,
                allowWifi = !blockWifi
            )
            if (success) {
                Log.i(TAG, "已自动断开 $packageName 的${if (networkType == "total") "全部" else networkType}网络")
            } else {
                Log.w(TAG, "自动断开 $packageName 网络失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "自动断网异常: ${e.message}", e)
        }
    }

    // ==================== WorkManager调度 ====================

    /**
     * 调度限额检测的PeriodicWorkRequest
     * 每15分钟检查一次所有应用的流量限额
     * 即使服务被系统杀死，WorkManager仍会按时执行
     */
    private fun scheduleLimitCheckWorker() {
        try {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build()

            val limitCheckRequest = androidx.work.PeriodicWorkRequestBuilder<LimitExceedWorker>(
                Constants.LIMIT_CHECK_INTERVAL_MINUTES,
                java.util.concurrent.TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInitialDelay(1, java.util.concurrent.TimeUnit.MINUTES) // 首次延迟1分钟
                .build()

            androidx.work.WorkManager.getInstance(applicationContext)
                .enqueueUniquePeriodicWork(
                    Constants.WORK_NAME_LIMIT_CHECK,
                    androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                    limitCheckRequest
                )

            Log.d(TAG, "已调度限额检测WorkManager，周期: ${Constants.LIMIT_CHECK_INTERVAL_MINUTES}分钟")
        } catch (e: Exception) {
            Log.e(TAG, "调度限额检测Worker失败: ${e.message}", e)
        }
    }

    /**
     * 取消限额检测WorkManager
     */
    private fun cancelLimitCheckWorker() {
        try {
            androidx.work.WorkManager.getInstance(applicationContext)
                .cancelUniqueWork(Constants.WORK_NAME_LIMIT_CHECK)
            Log.d(TAG, "已取消限额检测WorkManager")
        } catch (e: Exception) {
            Log.e(TAG, "取消限额检测Worker失败: ${e.message}", e)
        }
    }

    // ==================== Binder ====================

    /**
     * 服务Binder类
     * 提供给绑定的Activity使用，暴露实时网速StateFlow和服务控制方法
     */
    inner class MonitorBinder : Binder() {

        /**
         * 获取服务实例
         * @return 当前TrafficMonitorService实例
         */
        fun getService(): TrafficMonitorService = this@TrafficMonitorService

        /**
         * 获取实时网速信息的StateFlow
         * Activity通过collect此Flow来实时更新UI
         */
        fun getNetworkSpeedFlow(): StateFlow<NetworkSpeedInfo?> = networkSpeedInfo

        /**
         * 获取服务运行状态
         * @return true表示服务正在运行
         */
        fun isMonitoring(): Boolean = _isRunning.value
    }
}