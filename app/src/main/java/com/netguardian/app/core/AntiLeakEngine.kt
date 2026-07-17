package com.netguardian.app.core

import android.app.ActivityManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.Build
import android.util.Log
import com.netguardian.app.data.AppDatabase
import com.netguardian.app.model.AppControlState
import com.netguardian.app.model.AppTrafficInfo
import com.netguardian.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.SortedMap
import java.util.TreeMap

/**
 * 防偷跑引擎
 * 检测和阻止应用在后台悄悄消耗流量的行为
 *
 * 工作原理：
 * 1. 定期检查后台应用的网络活动
 * 2. 对于在后台产生异常流量的应用进行标记
 * 3. Root模式下可以进行深度检测（精确到每个进程的网络活动）
 * 4. Shizuku模式下进行基础检测
 * 5. 无权限模式下仅能提供告警，无法主动阻止
 *
 * 所有操作均为纯本地离线，不进行任何网络请求
 */
class AntiLeakEngine(private val context: Context) {

    companion object {
        private const val TAG = "AntiLeakEngine"

        /** 偷跑检测阈值 - 单次检测中后台应用产生超过此流量的视为偷跑（字节） */
        private const val LEAK_THRESHOLD_BYTES = Constants.STEALTHY_TRAFFIC_THRESHOLD_BYTES

        /** 检测周期（毫秒） */
        private const val CHECK_INTERVAL_MS = Constants.BACKGROUND_CHECK_INTERVAL_MS

        /** 连续检测到偷跑的次数阈值，达到后执行屏蔽 */
        private const val LEAK_CONFIRM_COUNT = 3
    }

    private val rootManager = RootManager.getInstance()
    private val shizukuManager = ShizukuManager.getInstance()
    private val trafficDao = AppDatabase.getInstance(context).trafficDao()

    /** 各应用连续偷跑计数器 <包名, 检测次数> */
    private val leakCounters = mutableMapOf<String, Int>()

    /** 上一次检测时的各应用流量快照 <包名, Pair<RX, TX>> */
    private val lastTrafficSnapshot = mutableMapOf<String, Pair<Long, Long>>()

    /**
     * 获取当前权限模式
     */
    private fun getPrivilegeMode(): String {
        return when {
            rootManager.isRootAvailable.value -> Constants.MODE_ROOT
            shizukuManager.isShizukuAvailable() -> Constants.MODE_SHIZUKU
            else -> Constants.MODE_NONE
        }
    }

    /**
     * 监控并屏蔽空闲时偷跑流量的应用
     * 一次性的检测与处理流程：
     * 1. 获取所有后台活跃应用
     * 2. 对比流量快照，找出异常流量的应用
     * 3. 对确认偷跑的应用执行网络断开
     *
     * @return 被屏蔽的应用包名列表
     */
    suspend fun monitorAndBlockIdleApps(): List<String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始偷跑检测...")

        // 获取后台有流量活动的应用
        val backgroundActiveApps = getBackgroundActiveApps()

        // 获取被标记为后台屏蔽的应用
        val blockedStates = trafficDao.getBlockedStates()
        val backgroundBlockedPackages = blockedStates
            .filter { it.backgroundBlocked }
            .map { it.packageName }
            .toSet()

        val blockedApps = mutableListOf<String>()
        val currentMode = getPrivilegeMode()

        for (app in backgroundActiveApps) {
            // 如果该应用不在后台屏蔽列表中，跳过
            if (app.packageName !in backgroundBlockedPackages) continue

            // 检测是否有偷跑行为
            val isStealthy = detectStealthyTraffic(app.packageName)

            if (isStealthy) {
                val count = (leakCounters[app.packageName] ?: 0) + 1
                leakCounters[app.packageName] = count

                Log.w(TAG, "${app.packageName} 疑似偷跑，连续检测: $count/$LEAK_CONFIRM_COUNT")

                if (count >= LEAK_CONFIRM_COUNT) {
                    // 确认偷跑，执行断开
                    val uid = getUidByPackageName(app.packageName)
                    if (uid >= 0) {
                        val disconnected = forceDisconnectApp(app.packageName, uid)
                        if (disconnected) {
                            blockedApps.add(app.packageName)
                            Log.i(TAG, "已断开偷跑应用: ${app.packageName}")
                        }
                    }
                    // 重置计数器
                    leakCounters[app.packageName] = 0
                }
            } else {
                // 没有偷跑，重置计数器
                leakCounters[app.packageName] = 0
            }
        }

        // 更新流量快照
        updateTrafficSnapshot(backgroundActiveApps)

        Log.d(TAG, "偷跑检测完成，处理了 ${blockedApps.size} 个应用")
        blockedApps
    }

    /**
     * 检测指定应用是否存在偷跑流量行为
     * 通过对比两次检测之间的流量差值来判断
     *
     * Root模式：使用/proc/uid_stat/精确读取
     * Shizuku模式：通过Shizuku读取/proc/uid_stat/
     * 普通模式：使用TrafficStats API
     *
     * @param packageName 应用包名
     * @return true表示检测到偷跑流量
     */
    suspend fun detectStealthyTraffic(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val uid = getUidByPackageName(packageName)
        if (uid < 0) return@withContext false

        val mode = getPrivilegeMode()
        var currentRx = 0L
        var currentTx = 0L

        when (mode) {
            Constants.MODE_ROOT -> {
                val data = rootManager.getProcessTraffic(uid)
                currentRx = data.rxBytes
                currentTx = data.txBytes
            }
            Constants.MODE_SHIZUKU -> {
                val (rx, tx) = shizukuManager.getUidTrafficShizuku(uid)
                currentRx = rx
                currentTx = tx
            }
            else -> {
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)
                if (rx > 0 && rx != -1L) currentRx = rx
                if (tx > 0 && tx != -1L) currentTx = tx
            }
        }

        // 与上次快照对比
        val lastTraffic = lastTrafficSnapshot[packageName]
        val leakedBytes = if (lastTraffic != null) {
            val rxDiff = currentRx - lastTraffic.first
            val txDiff = currentTx - lastTraffic.second
            (rxDiff + txDiff).coerceAtLeast(0L)
        } else {
            0L
        }

        // 更新快照
        lastTrafficSnapshot[packageName] = Pair(currentRx, currentTx)

        val isStealthy = leakedBytes > LEAK_THRESHOLD_BYTES
        if (isStealthy) {
            Log.w(TAG, "检测到 $packageName 偷跑流量: ${leakedBytes}字节")
        }

        isStealthy
    }

    /**
     * 获取当前在后台且有网络活动的应用列表
     * 通过UsageStatsManager判断应用是否在后台，结合流量统计判断是否有活动
     *
     * Root模式下深度检测：直接读取/proc/下所有UID的流量数据
     * Shizuku模式下基础检测：通过UsageStatsManager + TrafficStats
     * 普通模式：仅通过TrafficStats
     *
     * @return 后台有流量活动的应用列表
     */
    suspend fun getBackgroundActiveApps(): List<AppTrafficInfo> = withContext(Dispatchers.IO) {
        val mode = getPrivilegeMode()
        val foregroundPackages = getForegroundPackages()
        val activeApps = mutableListOf<AppTrafficInfo>()

        if (mode == Constants.MODE_ROOT) {
            // Root模式深度检测：遍历/proc/uid_stat/目录
            val output = rootManager.executeRootCommand("ls ${Constants.PROC_UID_STAT}")
            if (output != null) {
                val uidList = output.lines().filter { it.isNotBlank() }
                for (uidStr in uidList) {
                    try {
                        val uid = uidStr.trim().toIntOrNull() ?: continue
                        // 跳过系统UID
                        if (uid < 10000) continue

                        val packageName = getPackageNameByUid(uid) ?: continue

                        // 如果是前台应用则跳过
                        if (packageName in foregroundPackages) continue

                        val data = rootManager.getProcessTraffic(uid)
                        if (data.totalBytes > LEAK_THRESHOLD_BYTES) {
                            val appInfo = getBasicAppInfo(packageName)
                            activeApps.add(
                                AppTrafficInfo(
                                    uid = uid,
                                    packageName = packageName,
                                    appName = appInfo?.appName ?: packageName,
                                    icon = appInfo?.icon,
                                    wifiRxBytes = data.rxBytes,
                                    wifiTxBytes = data.txBytes,
                                    lastUpdated = System.currentTimeMillis()
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "处理UID $uidStr 异常: ${e.message}")
                    }
                }
            }
        } else {
            // Shizuku/普通模式基础检测：通过已安装应用列表 + UsageStats
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(0)

            for (pkg in packages) {
                try {
                    val packageName = pkg.packageName

                    // 跳过自身和系统应用
                    if (packageName == context.packageName) continue
                    if ((pkg.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) continue

                    // 跳过前台应用
                    if (packageName in foregroundPackages) continue

                    // 检查是否被标记为后台屏蔽
                    val state = trafficDao.getControlState(packageName)
                    if (state == null || !state.backgroundBlocked) continue

                    val uid = pkg.applicationInfo.uid
                    var rx = 0L
                    var tx = 0L

                    if (mode == Constants.MODE_SHIZUKU) {
                        val (sRx, sTx) = shizukuManager.getUidTrafficShizuku(uid)
                        rx = sRx
                        tx = sTx
                    } else {
                        val tRx = TrafficStats.getUidRxBytes(uid)
                        val tTx = TrafficStats.getUidTxBytes(uid)
                        if (tRx > 0 && tRx != -1L) rx = tRx
                        if (tTx > 0 && tTx != -1L) tx = tTx
                    }

                    if (rx + tx > LEAK_THRESHOLD_BYTES) {
                        val appName = pkg.applicationInfo.loadLabel(pm).toString()
                        val icon = try { pkg.applicationInfo.loadIcon(pm) } catch (_: Exception) { null }

                        activeApps.add(
                            AppTrafficInfo(
                                uid = uid,
                                packageName = packageName,
                                appName = appName,
                                icon = icon,
                                wifiRxBytes = rx,
                                wifiTxBytes = tx,
                                lastUpdated = System.currentTimeMillis()
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "检查后台应用 ${pkg.packageName} 异常: ${e.message}")
                }
            }
        }

        Log.d(TAG, "后台活跃应用数: ${activeApps.size} (模式: $mode)")
        activeApps.sortedByDescending { it.totalBytes }
    }

    /**
     * 强制断开指定应用的网络连接
     * Root模式下：添加iptables规则屏蔽 + 可选kill进程
     * Shizuku模式下：通过Shizuku执行iptables
     * 普通模式：只能记录日志，无法执行断开
     *
     * @param packageName 应用包名
     * @param uid 应用UID
     * @return true表示操作成功
     */
    suspend fun forceDisconnectApp(packageName: String, uid: Int): Boolean =
        withContext(Dispatchers.IO) {
            val mode = getPrivilegeMode()
            Log.w(TAG, "强制断开 $packageName (UID:$uid) 的网络连接，模式: $mode")

            when (mode) {
                Constants.MODE_ROOT -> {
                    // 添加iptables规则完全屏蔽
                    val rule = IptablesRule(
                        uid = uid,
                        packageName = packageName,
                        blockMobile = true,
                        blockWifi = true,
                        isAdd = true
                    )
                    val rulesApplied = rule.toCommands().all { cmd ->
                        rootManager.executeRootCommand(cmd) != null
                    }

                    // 可选：也尝试终止后台进程
                    rootManager.killBackgroundProcess(packageName)

                    rulesApplied
                }
                Constants.MODE_SHIZUKU -> {
                    shizukuManager.blockAppViaShizuku(
                        packageName,
                        blockMobile = true,
                        blockWifi = true
                    )
                }
                else -> {
                    Log.w(TAG, "无权限模式下无法强制断开应用网络")
                    false
                }
            }
        }

    /**
     * 获取当前前台应用的包名列表
     * 使用UsageStatsManager获取最近使用的应用
     */
    private fun getForegroundPackages(): Set<String> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                    ?: return emptySet()

                val endTime = System.currentTimeMillis()
                val startTime = endTime - Constants.BACKGROUND_IDLE_THRESHOLD_MS

                val stats: List<UsageStats> = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_BEST,
                    startTime,
                    endTime
                )

                // 按最后使用时间排序，取最近使用的几个应用作为"前台"
                val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
                sortedStats
                    .take(5) // 前台通常不超过5个（多窗口场景）
                    .map { it.packageName }
                    .toSet()
            } else {
                // 旧版本使用ActivityManager
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    ?: return emptySet()
                val tasks = am.getRunningTasks(5)
                tasks.mapNotNull { it.topActivity?.packageName }.toSet()
            }
        } catch (e: Exception) {
            Log.d(TAG, "获取前台应用列表失败: ${e.message}")
            emptySet()
        }
    }

    /**
     * 通过UID获取包名
     */
    private fun getPackageNameByUid(uid: Int): String? {
        return try {
            val pm = context.packageManager
            val packages = pm.getPackagesForUid(uid) ?: return null
            // 优先返回非系统包名
            packages.firstOrNull { !it.startsWith("android.") } ?: packages.firstOrNull()
        } catch (e: Exception) {
            Log.d(TAG, "UID $uid 获取包名失败")
            null
        }
    }

    /**
     * 获取应用基本信息
     */
    private fun getBasicAppInfo(packageName: String): AppTrafficInfo? {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            AppTrafficInfo(
                uid = appInfo.uid,
                packageName = packageName,
                appName = appInfo.loadLabel(pm).toString(),
                icon = try { appInfo.loadIcon(pm) } catch (_: Exception) { null }
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * 通过包名获取UID
     */
    private fun getUidByPackageName(packageName: String): Int {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0).uid
        } catch (e: PackageManager.NameNotFoundException) {
            -1
        }
    }

    /**
     * 更新流量快照
     */
    private fun updateTrafficSnapshot(apps: List<AppTrafficInfo>) {
        for (app in apps) {
            lastTrafficSnapshot[app.packageName] = Pair(
                app.wifiRxBytes + app.mobileRxBytes,
                app.wifiTxBytes + app.mobileTxBytes
            )
        }
    }

    /**
     * 重置偷跑计数器
     * 在应用网络策略变更时调用
     */
    fun resetLeakCounters() {
        leakCounters.clear()
        lastTrafficSnapshot.clear()
        Log.d(TAG, "偷跑计数器已重置")
    }

    /**
     * 重置指定应用的偷跑计数器
     */
    fun resetLeakCounter(packageName: String) {
        leakCounters.remove(packageName)
        lastTrafficSnapshot.remove(packageName)
    }

    /**
     * 获取当前偷跑检测结果
     * @return Map<包名, 累计检测次数>
     */
    fun getLeakStatus(): Map<String, Int> {
        return leakCounters.toMap()
    }
}