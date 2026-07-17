package com.netguardian.app.core

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
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
     * @return "root" / "shizuku" / "none"
     */
    private fun getPrivilegeMode(): String {
        return when {
            rootManager.isRootAvailable.value -> Constants.MODE_ROOT
            shizukuManager.isShizukuAvailable() -> Constants.MODE_SHIZUKU
            else -> Constants.MODE_NONE
        }
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
     * 使用/proc/uid_stat/{uid}/下的tcp_rcv和tcp_snd文件
     * 通过ConnectivityManager区分移动数据和WiFi流量
     */
    private fun getPerAppTrafficRoot(apps: List<AppInfo>): List<AppTrafficInfo> {
        val trafficList = mutableListOf<AppTrafficInfo>()

        // 获取当前网络状态用于流量分类
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val isWifi = isWifiConnected(cm)
        val isMobile = isMobileConnected(cm)

        for (app in apps) {
            try {
                val trafficData = rootManager.getProcessTraffic(app.uid)
                if (trafficData.totalBytes > 0) {
                    // 根据当前网络类型分配移动/WiFi流量
                    val (mobileRx, mobileTx, wifiRx, wifiTx) = classifyTraffic(
                        totalRx = trafficData.rxBytes,
                        totalTx = trafficData.txBytes,
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
                Log.d(TAG, "Root模式读取 ${app.packageName} 流量失败: ${e.message}")
            }
        }

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
     * 使用Android TrafficStats API获取精确的Per-UID流量
     * 通过ConnectivityManager获取当前网络类型，结合TrafficStats全局移动流量比例
     * 区分每个应用的移动数据和WiFi流量
     */
    private fun getPerAppTrafficNormal(apps: List<AppInfo>): List<AppTrafficInfo> {
        val trafficList = mutableListOf<AppTrafficInfo>()

        // 获取ConnectivityManager用于网络类型判断
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val isWifi = isWifiConnected(cm)
        val isMobile = isMobileConnected(cm)

        // 获取全局流量数据用于比例分配（当WiFi和移动同时连接时使用）
        val totalRx = TrafficStats.getTotalRxBytes().let { if (it == UNSUPPORTED.toLong()) 0L else it }
        val totalTx = TrafficStats.getTotalTxBytes().let { if (it == UNSUPPORTED.toLong()) 0L else it }
        val mobileTotalRx = TrafficStats.getMobileRxBytes().let { if (it == UNSUPPORTED.toLong()) 0L else it }
        val mobileTotalTx = TrafficStats.getMobileTxBytes().let { if (it == UNSUPPORTED.toLong()) 0L else it }

        for (app in apps) {
            try {
                val uid = app.uid
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)

                // TrafficStats返回-1表示不支持
                if (rx == UNSUPPORTED.toLong() && tx == UNSUPPORTED.toLong()) {
                    continue
                }

                val validRx = if (rx == UNSUPPORTED.toLong()) 0L else rx
                val validTx = if (tx == UNSUPPORTED.toLong()) 0L else tx

                if (validRx > 0 || validTx > 0) {
                    // 根据网络连接状态区分移动/WiFi流量
                    val (mobileRx, mobileTx, wifiRx, wifiTx) = when {
                        // 仅WiFi：全部归WiFi
                        isWifi && !isMobile -> TrafficAllocation(0L, 0L, validRx, validTx)
                        // 仅移动：全部归移动
                        isMobile && !isWifi -> TrafficAllocation(validRx, validTx, 0L, 0L)
                        // 两者同时连接或都未连接：按全局流量比例分配
                        else -> {
                            val globalTotal = totalRx + totalTx
                            val globalMobile = mobileTotalRx + mobileTotalTx
                            val mobileRatio = if (globalTotal >= TRAFFIC_SPLIT_MIN_TOTAL) {
                                globalMobile.toDouble() / globalTotal.toDouble()
                            } else {
                                0.0
                            }
                            val mRx = (validRx * mobileRatio).toLong()
                            val mTx = (validTx * mobileRatio).toLong()
                            TrafficAllocation(mRx, mTx, validRx - mRx, validTx - mTx)
                        }
                    }

                    trafficList.add(
                        AppTrafficInfo(
                            uid = uid,
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
                Log.d(TAG, "普通模式读取 ${app.packageName} 流量失败: ${e.message}")
            }
        }

        return trafficList.sortedByDescending { it.totalBytes }
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
     * 使用 PackageManager.MATCH_ALL (0x00020000) 获取所有已安装应用
     * 通过 Intent.ACTION_MAIN + Intent.CATEGORY_LAUNCHER 过滤出用户可见的启动器应用
     *
     * @param context 上下文
     * @param includeSystemApps 是否包含系统应用，默认false
     * @return 应用信息列表
     */
    fun getInstalledApps(context: Context, includeSystemApps: Boolean = false): List<AppInfo> {
        val pm = context.packageManager

        // 使用 PackageManager.MATCH_ALL (0x00020000) 代替已废弃的标志
        val flags = PackageManager.MATCH_ALL // 0x00020000

        @Suppress("DEPRECATION")
        val packages = pm.getInstalledPackages(flags)
        val appList = mutableListOf<AppInfo>()

        // 构建 LAUNCHER 意图，用于过滤用户可见应用
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val launchablePackages = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toSet()

        for (packageInfo in packages) {
            try {
                val appInfo = packageInfo.applicationInfo

                // 过滤系统应用（可选）
                if (!includeSystemApps) {
                    // 只保留有 LAUNCHER 的用户可见应用
                    if (packageInfo.packageName !in launchablePackages) {
                        continue
                    }
                    if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
                        continue
                    }
                }

                // 跳过自身
                if (packageInfo.packageName == context.packageName) {
                    continue
                }

                // 加载应用名称
                val appName = appInfo.loadLabel(pm).toString()
                if (appName.isBlank()) continue

                // 加载应用图标
                val icon = try {
                    appInfo.loadIcon(pm)
                } catch (e: Exception) {
                    Log.d(TAG, "加载 ${packageInfo.packageName} 图标失败: ${e.message}")
                    pm.getDefaultActivityIcon()
                }

                appList.add(
                    AppInfo(
                        packageName = packageInfo.packageName,
                        appName = appName,
                        uid = appInfo.uid,
                        icon = icon,
                        isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        versionName = packageInfo.versionName ?: "未知",
                        targetSdk = packageInfo.applicationInfo.targetSdkVersion
                    )
                )
            } catch (e: Exception) {
                // 部分应用可能无法加载信息，跳过
                Log.d(TAG, "获取应用 ${packageInfo.packageName} 信息失败: ${e.message}")
            }
        }

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