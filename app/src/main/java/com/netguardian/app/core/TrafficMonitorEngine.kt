package com.netguardian.app.core

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import android.util.Log
import com.netguardian.app.data.AppInfo
import com.netguardian.app.model.AppTrafficInfo
import com.netguardian.app.model.NetworkSpeedInfo
import com.netguardian.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * 流量监控引擎核心
 * 负责采集系统级和应用级流量数据，提供实时网速监测
 * 支持三种模式：
 * 1. Root模式：通过/proc/uid_stat/读取精确的Per-UID流量
 * 2. Shizuku模式：通过Shizuku权限读取/proc/uid_stat/
 * 3. 普通模式：使用TrafficStats API（Android标准API，精度较低）
 *
 * 所有操作均为纯本地离线，不进行任何网络请求
 */
class TrafficMonitorEngine(private val context: Context) {

    companion object {
        private const val TAG = "TrafficMonitorEngine"

        /** 系统自身UID，不需要统计 */
        private const val ANDROID_SYSTEM_UID = 1000

        /** TrafficStats中获取不到数据时的返回值 */
        private const val UNSUPPORTED = -1L

        /** 用于流量比例分配的阈值，避免除零 */
        private const val TRAFFIC_SPLIT_MIN_TOTAL = 1L
    }

    private val rootManager = RootManager.getInstance()
    private val shizukuManager = ShizukuManager.getInstance()
    private val networkStatsManager: Any? =
        try {
            context.getSystemService("netstats")
        } catch (e: Exception) {
            null
        }

    /** 运营商管理器 */
    private val carrierManager = CarrierManager(context)

    // ==================== 运营商信息 ====================

    /**
     * 获取运营商信息
     * @return CarrierInfo 或 null
     */
    fun getCarrierInfo(): CarrierInfo? {
        return carrierManager.detectCarrier()
    }

    /**
     * 获取运营商显示名称
     * @return 运营商中文名（如"中国移动"、"中国联通"、"中国电信"）
     */
    fun getCarrierDisplayName(): String {
        return carrierManager.getCarrierDisplayName()
    }

    /**
     * 获取网络类型名称（4G/5G/3G等）
     * @return 网络类型字符串
     */
    fun getNetworkTypeName(): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            tm?.let { carrierManager.getNetworkTypeName(it.dataNetworkType) } ?: "未知"
        } catch (e: Exception) {
            "未知"
        }
    }

    // ==================== 权限模式 ====================

    /**
     * 获取当前权限模式
     * 优先使用 DataStore 中用户设置的模式，如果存储的是 Root 模式则验证 Root 是否可用。
     * 没有存储的模式时自动检测。
     * @return "root" / "shizuku" / "none"
     */
    private fun getPrivilegeMode(): String {
        return try {
            val storedMode = kotlinx.coroutines.runBlocking {
                com.netguardian.app.util.DataStoreHelper.getPrivilegeModeOnce(context)
            }
            if (storedMode != Constants.MODE_NONE) {
                if (storedMode == Constants.MODE_ROOT) {
                    if (rootManager.hasRootAccess()) {
                        rootManager.isRootAvailable.value = true
                        storedMode
                    } else {
                        rootManager.isRootAvailable.value = false
                        Constants.MODE_NONE
                    }
                } else {
                    storedMode
                }
            } else {
                when {
                    rootManager.hasRootAccess() -> {
                        rootManager.isRootAvailable.value = true
                        Constants.MODE_ROOT
                    }
                    shizukuManager.isShizukuAvailable() -> Constants.MODE_SHIZUKU
                    else -> Constants.MODE_NONE
                }
            }
        } catch (e: Exception) {
            when {
                rootManager.isRootAvailable.value -> Constants.MODE_ROOT
                shizukuManager.isShizukuAvailable() -> Constants.MODE_SHIZUKU
                else -> Constants.MODE_NONE
            }
        }
    }

    /**
     * 刷新 Root 状态
     * 重新检测 Root 权限并更新内部状态
     */
    fun refreshRootStatus() {
        val hasRoot = rootManager.hasRootAccess()
        rootManager.isRootAvailable.value = hasRoot
        Log.i(TAG, "Root状态刷新: $hasRoot")
    }

    // ==================== 系统流量统计 ====================

    /**
     * 获取系统总流量（接收 + 发送）
     * 使用TrafficStats API获取全局统计数据
     *
     * @return Pair<总接收字节数, 总发送字节数>
     */
    fun getSystemTotalTraffic(): Pair<Long, Long> {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()

        val validRx = if (rx == UNSUPPORTED.toLong()) 0L else rx
        val validTx = if (tx == UNSUPPORTED.toLong()) 0L else tx

        Log.d(TAG, "系统总流量 - RX: $validRx, TX: $validTx, 模式: ${getPrivilegeMode()}")
        return Pair(validRx, validTx)
    }

    /**
     * 获取移动网络总流量
     * @return Pair<移动网络接收字节数, 移动网络发送字节数>
     */
    fun getMobileTotalTraffic(): Pair<Long, Long> {
        val rx = TrafficStats.getMobileRxBytes()
        val tx = TrafficStats.getMobileTxBytes()

        val validRx = if (rx == UNSUPPORTED.toLong()) 0L else rx
        val validTx = if (tx == UNSUPPORTED.toLong()) 0L else tx

        return Pair(validRx, validTx)
    }

    // ==================== 应用流量采集 ====================

    /**
     * 获取所有应用的流量信息列表
     * 根据当前权限模式选择不同的数据采集方式：
     * - Root/Shizuku模式：从/proc/uid_stat/读取精确数据，结合ConnectivityManager区分移动/WiFi
     * - 普通模式：使用TrafficStats.getUidRxBytes()/getUidTxBytes()，通过全局流量比例区分移动/WiFi
     *
     * @return 按总流量降序排列的应用流量信息列表
     */
    fun getPerAppTraffic(): List<AppTrafficInfo> {
        val apps = getInstalledApps(context)
        val mode = getPrivilegeMode()
        Log.d(TAG, "开始采集应用流量，模式: $mode，应用数: ${apps.size}")

        return when (mode) {
            Constants.MODE_ROOT -> getPerAppTrafficRoot(apps)
            Constants.MODE_SHIZUKU -> getPerAppTrafficShizuku(apps)
            else -> getPerAppTrafficNormal(apps)
        }
    }

    /**
     * Root模式下获取应用流量
     * 使用 Root 权限读取系统级 per-UID 流量统计
     * Android 10+: dumpsys netstats
     * Android 9-: /proc/net/xt_qtaguid/stats
     */
    private fun getPerAppTrafficRoot(apps: List<AppInfo>): List<AppTrafficInfo> {
        val trafficList = mutableListOf<AppTrafficInfo>()

        // 获取当前网络状态用于流量分类
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val isWifi = isWifiConnected(cm)
        val isMobile = isMobileConnected(cm)

        // 一次性获取所有UID的流量数据（高效）
        val uidTrafficMap = rootManager.getAllUidTraffic()

        if (uidTrafficMap.isEmpty()) {
            Log.w(TAG, "Root模式: getAllUidTraffic 返回空，回退到 NetworkStatsManager")
            return getPerAppTrafficNormal(apps)
        }

        for (app in apps) {
            try {
                val traffic = uidTrafficMap[app.uid] ?: continue
                val rx = traffic.first
                val tx = traffic.second

                if (rx > 0 || tx > 0) {
                    // 根据当前网络类型分配移动/WiFi流量
                    val (mobileRx, mobileTx, wifiRx, wifiTx) = classifyTraffic(
                        totalRx = rx,
                        totalTx = tx,
                        isWifiConnected = isWifi,
                        isMobileConnected = isMobile
                    )

                    trafficList.add(
                        AppTrafficInfo(
                            uid = app.uid,
                            packageName = app.packageName,
                            appName = app.appName,
                            icon = app.icon,
                            mobileRxBytes = mobileRx,
                            mobileTxBytes = mobileTx,
                            wifiRxBytes = wifiRx,
                            wifiTxBytes = wifiTx,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                Log.d(TAG, "Root模式处理 ${app.packageName} 失败: ${e.message}")
            }
        }

        Log.d(TAG, "Root模式采集到 ${trafficList.size} 个有流量的应用")
        return trafficList.sortedByDescending { it.totalBytes }
    }

    /**
     * Shizuku模式下获取应用流量
     * 通过Shizuku远程执行命令读取/proc/uid_stat/
     * 通过ConnectivityManager区分移动数据和WiFi流量
     */
    private fun getPerAppTrafficShizuku(apps: List<AppInfo>): List<AppTrafficInfo> {
        val trafficList = mutableListOf<AppTrafficInfo>()

        // 获取当前网络状态用于流量分类
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val isWifi = isWifiConnected(cm)
        val isMobile = isMobileConnected(cm)

        for (app in apps) {
            try {
                val (rx, tx) = shizukuManager.getUidTrafficShizuku(app.uid)
                if (rx > 0 || tx > 0) {
                    // 根据当前网络类型分配移动/WiFi流量
                    val (mobileRx, mobileTx, wifiRx, wifiTx) = classifyTraffic(
                        totalRx = rx,
                        totalTx = tx,
                        isWifiConnected = isWifi,
                        isMobileConnected = isMobile
                    )

                    trafficList.add(
                        AppTrafficInfo(
                            uid = app.uid,
                            packageName = app.packageName,
                            appName = app.appName,
                            icon = app.icon,
                            mobileRxBytes = mobileRx,
                            mobileTxBytes = mobileTx,
                            wifiRxBytes = wifiRx,
                            wifiTxBytes = wifiTx,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                Log.d(TAG, "Shizuku模式读取 ${app.packageName} 流量失败: ${e.message}")
            }
        }

        return trafficList.sortedByDescending { it.totalBytes }
    }

    /**
     * 普通模式（无Root/Shizuku）下获取应用流量
     * 使用 NetworkStatsManager API 获取精确的Per-UID流量
     * 需要 PACKAGE_USAGE_STATS 权限
     */
    private fun getPerAppTrafficNormal(apps: List<AppInfo>): List<AppTrafficInfo> {
        val trafficList = mutableListOf<AppTrafficInfo>()
        val nsm = networkStatsManager

        if (!hasUsageStatsPermission()) {
            Log.w(TAG, "缺少 PACKAGE_USAGE_STATS 权限，回退到 TrafficStats")
            return getPerAppTrafficTrafficStatsFallback(apps)
        }

        if (nsm == null) {
            Log.w(TAG, "NetworkStatsManager 不可用，回退到 TrafficStats")
            return getPerAppTrafficTrafficStatsFallback(apps)
        }

        try {
            // 查询最近30天的数据（覆盖用户需要的跨月数据）
            val calendar = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_MONTH, -30)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()

            // 获取 NetworkStatsManager 类
            val nsmClass = Class.forName("android.net.NetworkStatsManager")
            val bucketClassName = "android.net.NetworkStats\$Bucket"
            val bucketClass = Class.forName(bucketClassName)

            // 获取 querySummary 方法
            val querySummaryMethod = nsmClass.getMethod(
                "querySummary",
                Int::class.javaPrimitiveType,
                String::class.java,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType
            )

            // 获取 DATA_USAGE_MOBILE 和 DATA_USAGE_WIFI 的值
            val tmClass = Class.forName("android.telephony.TelephonyManager")
            val DATA_USAGE_MOBILE = tmClass.getDeclaredField("DATA_USAGE_MOBILE").getInt(null)
            val DATA_USAGE_WIFI = tmClass.getDeclaredField("DATA_USAGE_WIFI").getInt(null)

            // 查询移动数据
            val mobileResult = querySummaryMethod.invoke(
                nsm, DATA_USAGE_MOBILE, null, startTime, endTime
            )
            // 查询 WiFi 数据
            val wifiResult = querySummaryMethod.invoke(
                nsm, DATA_USAGE_WIFI, null, startTime, endTime
            )

            // 遍历 bucket 的方法
            val hasNextBucketMethod = mobileResult.javaClass.getMethod("hasNextBucket")
            val getNextBucketMethod = mobileResult.javaClass.getMethod("getNextBucket", bucketClass)

            // Bucket 的字段
            val uidField = bucketClass.getDeclaredField("uid")
            val rxBytesField = bucketClass.getDeclaredField("rxBytes")
            val txBytesField = bucketClass.getDeclaredField("txBytes")
            uidField.isAccessible = true
            rxBytesField.isAccessible = true
            txBytesField.isAccessible = true

            val mobileTraffic = mutableMapOf<Int, Pair<Long, Long>>()
            val wifiTraffic = mutableMapOf<Int, Pair<Long, Long>>()

            // 遍历移动数据 buckets
            val mobileBucket = bucketClass.getDeclaredConstructor().newInstance()
            while (hasNextBucketMethod.invoke(mobileResult) as Boolean) {
                getNextBucketMethod.invoke(mobileResult, mobileBucket)
                val uid = uidField.getInt(mobileBucket)
                if (uid == Process.myUid()) continue
                val rx = rxBytesField.getLong(mobileBucket)
                val tx = txBytesField.getLong(mobileBucket)
                val current = mobileTraffic.getOrDefault(uid, 0L to 0L)
                mobileTraffic[uid] = (current.first + rx) to (current.second + tx)
            }

            // 遍历 WiFi buckets
            val wifiBucket = bucketClass.getDeclaredConstructor().newInstance()
            while (hasNextBucketMethod.invoke(wifiResult) as Boolean) {
                getNextBucketMethod.invoke(wifiResult, wifiBucket)
                val uid = uidField.getInt(wifiBucket)
                if (uid == Process.myUid()) continue
                val rx = rxBytesField.getLong(wifiBucket)
                val tx = txBytesField.getLong(wifiBucket)
                val current = wifiTraffic.getOrDefault(uid, 0L to 0L)
                wifiTraffic[uid] = (current.first + rx) to (current.second + tx)
            }

            // 合并数据
            val allUids = mobileTraffic.keys + wifiTraffic.keys
            for (uid in allUids) {
                val (mRx, mTx) = mobileTraffic.getOrDefault(uid, 0L to 0L)
                val (wRx, wTx) = wifiTraffic.getOrDefault(uid, 0L to 0L)

                if (mRx > 0 || mTx > 0 || wRx > 0 || wTx > 0) {
                    val app = apps.find { it.uid == uid }
                    if (app != null) {
                        trafficList.add(
                            AppTrafficInfo(
                                uid = uid,
                                packageName = app.packageName,
                                appName = app.appName,
                                icon = app.icon,
                                mobileRxBytes = mRx,
                                mobileTxBytes = mTx,
                                wifiRxBytes = wRx,
                                wifiTxBytes = wTx,
                                lastUpdated = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }

            Log.d(TAG, "NetworkStatsManager 查询到 ${trafficList.size} 个有流量的应用")

        } catch (e: SecurityException) {
            Log.w(TAG, "NetworkStatsManager 权限不足: ${e.message}")
            return getPerAppTrafficTrafficStatsFallback(apps)
        } catch (e: Exception) {
            Log.e(TAG, "NetworkStatsManager 查询失败: ${e.javaClass.simpleName}: ${e.message}", e)
            return getPerAppTrafficTrafficStatsFallback(apps)
        }

        return trafficList.sortedByDescending { it.totalBytes }
    }

    /**
     * TrafficStats 回退方案（精度较低）
     * Android 10+ 中 TrafficStats 只能获取自身应用的流量，
     * 所以此 fallback 在大多数情况下也会返回空列表。
     * 让 ViewModel 的合并逻辑处理——显示所有应用为"暂无数据"。
     */
    private fun getPerAppTrafficTrafficStatsFallback(apps: List<AppInfo>): List<AppTrafficInfo> {
        val trafficList = mutableListOf<AppTrafficInfo>()
        var hasAnyData = false

        for (app in apps) {
            try {
                val uid = app.uid
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)

                if (rx == UNSUPPORTED.toLong() && tx == UNSUPPORTED.toLong()) continue

                val validRx = if (rx == UNSUPPORTED.toLong()) 0L else rx
                val validTx = if (tx == UNSUPPORTED.toLong()) 0L else tx

                if (validRx > 0 || validTx > 0) {
                    hasAnyData = true
                    // TrafficStats 无法区分移动和WiFi，简单归为WiFi
                    trafficList.add(
                        AppTrafficInfo(
                            uid = uid,
                            packageName = app.packageName,
                            appName = app.appName,
                            icon = app.icon,
                            mobileRxBytes = 0L,
                            mobileTxBytes = 0L,
                            wifiRxBytes = validRx,
                            wifiTxBytes = validTx,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                Log.d(TAG, "TrafficStats回退读取 ${app.packageName} 失败: ${e.message}")
            }
        }

        return trafficList.sortedByDescending { it.totalBytes }
    }

    /**
     * 检查是否有 PACKAGE_USAGE_STATS 权限
     * 这是使用 NetworkStatsManager 的前提条件
     */
    private fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            val mode = appOps?.unsafeCheckOpNoThrow(
                "android:get_usage_stats",
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Log.d(TAG, "检查使用情况权限失败: ${e.message}")
            false
        }
    }

    // ==================== 实时网速监测 ====================

    /**
     * 实时网速监测
     * 通过定期采样系统总流量差值来计算实时下载/上传速率
     * 使用kotlinx.coroutines.flow.Flow实现响应式数据流
     *
     * @param intervalMs 采样间隔（毫秒），默认1000ms
     * @return Flow<NetworkSpeedInfo> 实时网速信息流
     */
    fun getNetworkSpeedRealtime(intervalMs: Long = Constants.SPEED_SAMPLE_INTERVAL_MS): Flow<NetworkSpeedInfo> {
        return flow {
            var lastRx = 0L
            var lastTx = 0L
            var lastTimestamp = 0L

            // 首次采样，获取基准值
            val (initialRx, initialTx) = getSystemTotalTraffic()
            lastRx = initialRx
            lastTx = initialTx
            lastTimestamp = System.currentTimeMillis()

            while (true) {
                delay(intervalMs)

                val currentTimestamp = System.currentTimeMillis()
                val (currentRx, currentTx) = getSystemTotalTraffic()

                // 计算时间差（秒）
                val timeDiffMs = currentTimestamp - lastTimestamp
                val timeDiffSec = if (timeDiffMs > 0) timeDiffMs / 1000.0 else 1.0

                // 计算速率（字节/秒）
                val downloadSpeed = if (currentRx >= lastRx) {
                    ((currentRx - lastRx) / timeDiffSec).toLong()
                } else {
                    // 流量重置（例如切换网络或系统重置计数器）
                    0L
                }

                val uploadSpeed = if (currentTx >= lastTx) {
                    ((currentTx - lastTx) / timeDiffSec).toLong()
                } else {
                    0L
                }

                val networkType = detectNetworkType()

                val speedInfo = NetworkSpeedInfo(
                    downloadSpeed = downloadSpeed,
                    uploadSpeed = uploadSpeed,
                    timestamp = currentTimestamp,
                    networkType = networkType
                )

                Log.d(TAG, "实时网速 - 下载: ${downloadSpeed}B/s, 上传: ${uploadSpeed}B/s, 类型: $networkType")
                emit(speedInfo)

                lastRx = currentRx
                lastTx = currentTx
                lastTimestamp = currentTimestamp
            }
        }.flowOn(Dispatchers.IO)
    }

    // ==================== 网络类型检测 ====================

    /**
     * 检测当前网络类型
     * 通过ConnectivityManager API精确判断网络连接类型
     *
     * @return "WiFi" / "Mobile" / "None"
     */
    fun detectNetworkType(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm == null) {
                Log.d(TAG, "无法获取ConnectivityManager")
                return Constants.NETWORK_TYPE_NONE
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network: android.net.Network? = cm.activeNetwork
                val capabilities = cm.getNetworkCapabilities(network)
                when {
                    capabilities == null -> Constants.NETWORK_TYPE_NONE
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Constants.NETWORK_TYPE_WIFI
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Constants.NETWORK_TYPE_MOBILE
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Constants.NETWORK_TYPE_WIFI
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> {
                        // VPN连接时，检查底层网络类型
                        detectUnderlyingNetworkType(cm)
                    }
                    else -> Constants.NETWORK_TYPE_NONE
                }
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = cm.activeNetworkInfo
                when (networkInfo?.type) {
                    ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_ETHERNET -> Constants.NETWORK_TYPE_WIFI
                    ConnectivityManager.TYPE_MOBILE, ConnectivityManager.TYPE_MOBILE_DUN,
                    ConnectivityManager.TYPE_MOBILE_HIPRI, ConnectivityManager.TYPE_MOBILE_MMS,
                    ConnectivityManager.TYPE_MOBILE_SUPL -> Constants.NETWORK_TYPE_MOBILE
                    else -> Constants.NETWORK_TYPE_NONE
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "检测网络类型失败: ${e.message}")
            Constants.NETWORK_TYPE_NONE
        }
    }

    /**
     * VPN连接时检测底层网络类型
     * 遍历所有网络，找到非VPN的活跃网络
     */
    @Suppress("DEPRECATION")
    private fun detectUnderlyingNetworkType(cm: ConnectivityManager): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val allNetworks = cm.allNetworks
                for (network in allNetworks) {
                    if (network == cm.activeNetwork) continue // 跳过VPN本身
                    val caps = cm.getNetworkCapabilities(network) ?: continue
                    val networkInfo = cm.getNetworkInfo(network) ?: continue
                    if (!networkInfo.isConnected) continue

                    return when {
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Constants.NETWORK_TYPE_WIFI
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Constants.NETWORK_TYPE_MOBILE
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Constants.NETWORK_TYPE_WIFI
                        else -> continue
                    }
                }
            }
            Constants.NETWORK_TYPE_NONE
        } catch (e: Exception) {
            Constants.NETWORK_TYPE_NONE
        }
    }

    // ==================== 已安装应用列表 ====================

    /**
     * 获取已安装应用列表
     * 使用 getInstalledApplications(MATCH_ALL) 获取所有应用
     * Android 11+ 需要 QUERY_ALL_PACKAGES 权限才能获取完整列表
     *
     * @param context 上下文
     * @param includeSystemApps 是否包含系统应用，默认false
     * @return 应用信息列表
     */
    fun getInstalledApps(context: Context, includeSystemApps: Boolean = false): List<AppInfo> {
        val pm = context.packageManager
        val appList = mutableListOf<AppInfo>()

        try {
            // MATCH_ALL (0x00020000) 确保获取所有应用
            val MATCH_ALL = 0x00020000
            val applications = pm.getInstalledApplications(MATCH_ALL)
            Log.d(TAG, "getInstalledApplications 返回 ${applications.size} 个应用")

            // 获取有 LAUNCHER intent 的包名集合
            val launcherPackages = try {
                val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                pm.queryIntentActivities(launcherIntent, 0)
                    .map { it.activityInfo.packageName }
                    .toSet()
            } catch (e: Exception) {
                Log.d(TAG, "queryIntentActivities 失败: ${e.message}")
                emptySet()
            }

            for (appInfo in applications) {
                try {
                    val packageName = appInfo.packageName
                    if (packageName == context.packageName) continue

                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                    if (!includeSystemApps && isSystemApp && packageName !in launcherPackages) {
                        continue
                    }

                    val appName = appInfo.loadLabel(pm).toString()
                    if (appName.isBlank()) continue

                    val icon = try {
                        appInfo.loadIcon(pm)
                    } catch (e: Exception) {
                        pm.getDefaultActivityIcon()
                    }

                    val versionName = try {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(packageName, 0).versionName ?: "未知"
                    } catch (e: Exception) {
                        "未知"
                    }

                    appList.add(
                        AppInfo(
                            packageName = packageName,
                            appName = appName,
                            uid = appInfo.uid,
                            icon = icon,
                            isSystemApp = isSystemApp,
                            versionName = versionName,
                            targetSdk = appInfo.targetSdkVersion
                        )
                    )
                } catch (e: Exception) {
                    Log.d(TAG, "获取应用 ${appInfo.packageName} 信息失败: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取已安装应用列表失败: ${e.message}", e)
        }

        Log.d(TAG, "最终检测到 ${appList.size} 个应用")
        return appList.sortedBy { it.appName.lowercase() }
    }

    // ==================== 单应用流量查询 ====================

    /**
     * 获取单个应用的流量信息
     *
     * @param uid 应用UID
     * @param packageName 包名
     * @param appName 应用名称
     * @return 应用流量信息，获取失败返回null
     */
    fun getAppTraffic(uid: Int, packageName: String, appName: String): AppTrafficInfo? {
        val mode = getPrivilegeMode()

        // 获取当前网络状态
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val isWifi = isWifiConnected(cm)
        val isMobile = isMobileConnected(cm)

        return when (mode) {
            Constants.MODE_ROOT -> {
                val data = rootManager.getProcessTraffic(uid)
                if (data.totalBytes > 0) {
                    val (mRx, mTx, wRx, wTx) = classifyTraffic(
                        totalRx = data.rxBytes,
                        totalTx = data.txBytes,
                        isWifiConnected = isWifi,
                        isMobileConnected = isMobile
                    )
                    AppTrafficInfo(
                        uid = uid,
                        packageName = packageName,
                        appName = appName,
                        mobileRxBytes = mRx,
                        mobileTxBytes = mTx,
                        wifiRxBytes = wRx,
                        wifiTxBytes = wTx
                    )
                } else null
            }
            Constants.MODE_SHIZUKU -> {
                val (rx, tx) = shizukuManager.getUidTrafficShizuku(uid)
                if (rx > 0 || tx > 0) {
                    val (mRx, mTx, wRx, wTx) = classifyTraffic(
                        totalRx = rx,
                        totalTx = tx,
                        isWifiConnected = isWifi,
                        isMobileConnected = isMobile
                    )
                    AppTrafficInfo(
                        uid = uid,
                        packageName = packageName,
                        appName = appName,
                        mobileRxBytes = mRx,
                        mobileTxBytes = mTx,
                        wifiRxBytes = wRx,
                        wifiTxBytes = wTx
                    )
                } else null
            }
            else -> {
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)
                if (rx > 0 && rx != UNSUPPORTED.toLong()) {
                    val validRx = if (rx == UNSUPPORTED.toLong()) 0L else rx
                    val validTx = if (tx == UNSUPPORTED.toLong()) 0L else tx

                    // 区分移动/WiFi流量
                    val totalRx = TrafficStats.getTotalRxBytes().let { if (it == UNSUPPORTED.toLong()) 0L else it }
                    val totalTx = TrafficStats.getTotalTxBytes().let { if (it == UNSUPPORTED.toLong()) 0L else it }
                    val mobileTotalRx = TrafficStats.getMobileRxBytes().let { if (it == UNSUPPORTED.toLong()) 0L else it }
                    val mobileTotalTx = TrafficStats.getMobileTxBytes().let { if (it == UNSUPPORTED.toLong()) 0L else it }

                    val allocation = when {
                        isWifi && !isMobile -> TrafficAllocation(0L, 0L, validRx, validTx)
                        isMobile && !isWifi -> TrafficAllocation(validRx, validTx, 0L, 0L)
                        else -> {
                            val globalTotal = totalRx + totalTx
                            val globalMobile = mobileTotalRx + mobileTotalTx
                            val mobileRatio = if (globalTotal >= TRAFFIC_SPLIT_MIN_TOTAL) {
                                globalMobile.toDouble() / globalTotal.toDouble()
                            } else 0.0
                            val mRx = (validRx * mobileRatio).toLong()
                            val mTx = (validTx * mobileRatio).toLong()
                            TrafficAllocation(mRx, mTx, validRx - mRx, validTx - mTx)
                        }
                    }

                    AppTrafficInfo(
                        uid = uid,
                        packageName = packageName,
                        appName = appName,
                        mobileRxBytes = allocation.mobileRx,
                        mobileTxBytes = allocation.mobileTx,
                        wifiRxBytes = allocation.wifiRx,
                        wifiTxBytes = allocation.wifiTx
                    )
                } else null
            }
        }
    }

    // ==================== 网络接口流量详情 ====================

    /**
     * 获取当前网络接口的详细流量信息
     * Root模式下读取/proc/net/dev获取每个接口的统计数据
     *
     * @return Map<接口名, Pair<RX, TX>>
     */
    fun getInterfaceTrafficDetail(): Map<String, Pair<Long, Long>> {
        return if (rootManager.isRootAvailable.value) {
            rootManager.getInterfaceTraffic()
        } else {
            // 普通模式只能返回TrafficStats的全局数据
            val (rx, tx) = getSystemTotalTraffic()
            mapOf("total" to Pair(rx, tx))
        }
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 流量分配结果数据类
     * 用于将总流量拆分为移动网络和WiFi两部分
     */
    private data class TrafficAllocation(
        val mobileRx: Long,
        val mobileTx: Long,
        val wifiRx: Long,
        val wifiTx: Long
    )

    /**
     * 根据当前网络连接状态分类流量
     * 当仅有一种网络连接时，流量全部归入该类型
     * 当同时连接WiFi和移动网络时，按全局流量比例分配
     *
     * @param totalRx 应用总接收字节数
     * @param totalTx 应用总发送字节数
     * @param isWifiConnected WiFi是否连接
     * @param isMobileConnected 移动数据是否连接
     * @return TrafficAllocation 包含分类后的移动和WiFi流量
     */
    private fun classifyTraffic(
        totalRx: Long,
        totalTx: Long,
        isWifiConnected: Boolean,
        isMobileConnected: Boolean
    ): TrafficAllocation {
        return when {
            // 仅WiFi连接
            isWifiConnected && !isMobileConnected -> {
                TrafficAllocation(mobileRx = 0L, mobileTx = 0L, wifiRx = totalRx, wifiTx = totalTx)
            }
            // 仅移动数据连接
            isMobileConnected && !isWifiConnected -> {
                TrafficAllocation(mobileRx = totalRx, mobileTx = totalTx, wifiRx = 0L, wifiTx = 0L)
            }
            // 两者同时连接：按全局流量比例分配
            isWifiConnected && isMobileConnected -> {
                val sysTotalRx = TrafficStats.getTotalRxBytes().let { if (it == UNSUPPORTED.toLong()) 0L else it }
                val sysTotalTx = TrafficStats.getTotalTxBytes().let { if (it == UNSUPPORTED.toLong()) 0L else it }
                val sysMobileRx = TrafficStats.getMobileRxBytes().let { if (it == UNSUPPORTED.toLong()) 0L else it }
                val sysMobileTx = TrafficStats.getMobileTxBytes().let { if (it == UNSUPPORTED.toLong()) 0L else it }

                val globalTotal = sysTotalRx + sysTotalTx
                val globalMobile = sysMobileRx + sysMobileTx
                val mobileRatio = if (globalTotal >= TRAFFIC_SPLIT_MIN_TOTAL) {
                    globalMobile.toDouble() / globalTotal.toDouble()
                } else {
                    0.5 // 无法确定比例时平均分配
                }

                val mRx = (totalRx * mobileRatio).toLong()
                val mTx = (totalTx * mobileRatio).toLong()
                TrafficAllocation(
                    mobileRx = mRx, mobileTx = mTx,
                    wifiRx = totalRx - mRx, wifiTx = totalTx - mTx
                )
            }
            // 都未连接：归入WiFi（兜底）
            else -> {
                TrafficAllocation(mobileRx = 0L, mobileTx = 0L, wifiRx = totalRx, wifiTx = totalTx)
            }
        }
    }

    /**
     * 检查WiFi是否已连接
     * @param cm ConnectivityManager实例，可为null
     * @return true表示WiFi已连接
     */
    private fun isWifiConnected(cm: ConnectivityManager?): Boolean {
        if (cm == null) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork
                val capabilities = cm.getNetworkCapabilities(network)
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查移动数据是否已连接
     * @param cm ConnectivityManager实例，可为null
     * @return true表示移动数据已连接
     */
    private fun isMobileConnected(cm: ConnectivityManager?): Boolean {
        if (cm == null) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork
                val capabilities = cm.getNetworkCapabilities(network)
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_MOBILE
            }
        } catch (e: Exception) {
            false
        }
    }
}