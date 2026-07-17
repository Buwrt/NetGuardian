package com.netguardian.app.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.TrafficStats
import android.util.Log
import com.netguardian.app.model.AppTrafficInfo
import com.netguardian.app.model.NetworkSpeedInfo
import com.netguardian.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.NetworkInterface

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
    }

    private val rootManager = RootManager.getInstance()
    private val shizukuManager = ShizukuManager.getInstance()

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

    /**
     * 获取所有应用的流量信息列表
     * 根据当前权限模式选择不同的数据采集方式：
     * - Root/Shizuku模式：从/proc/uid_stat/读取精确数据
     * - 普通模式：使用TrafficStats.getUidRxBytes()/getUidTxBytes()
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
     */
    private fun getPerAppTrafficRoot(apps: List<com.netguardian.app.data.AppInfo>): List<AppTrafficInfo> {
        val trafficList = mutableListOf<AppTrafficInfo>()

        for (app in apps) {
            try {
                val trafficData = rootManager.getProcessTraffic(app.uid)
                if (trafficData.totalBytes > 0) {
                    trafficList.add(
                        AppTrafficInfo(
                            uid = app.uid,
                            packageName = app.packageName,
                            appName = app.appName,
                            icon = app.icon,
                            // Root模式下无法直接区分移动/WiFi，需要通过接口判断
                            // 这里将/proc数据作为WiFi流量（估算）
                            mobileRxBytes = 0L,
                            mobileTxBytes = 0L,
                            wifiRxBytes = trafficData.rxBytes,
                            wifiTxBytes = trafficData.txBytes,
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
     */
    private fun getPerAppTrafficShizuku(apps: List<com.netguardian.app.data.AppInfo>): List<AppTrafficInfo> {
        val trafficList = mutableListOf<AppTrafficInfo>()

        for (app in apps) {
            try {
                val (rx, tx) = shizukuManager.getUidTrafficShizuku(app.uid)
                if (rx > 0 || tx > 0) {
                    trafficList.add(
                        AppTrafficInfo(
                            uid = app.uid,
                            packageName = app.packageName,
                            appName = app.appName,
                            icon = app.icon,
                            mobileRxBytes = 0L,
                            mobileTxBytes = 0L,
                            wifiRxBytes = rx,
                            wifiTxBytes = tx,
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
     * 使用Android TrafficStats API，精度较低，无法区分移动/WiFi
     */
    private fun getPerAppTrafficNormal(apps: List<com.netguardian.app.data.AppInfo>): List<AppTrafficInfo> {
        val trafficList = mutableListOf<AppTrafficInfo>()

        for (app in apps) {
            try {
                val uid = app.uid
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)

                // TrafficStats返回-1表示不支持
                if (rx != UNSUPPORTED.toLong() || tx != UNSUPPORTED.toLong()) {
                    val validRx = if (rx == UNSUPPORTED.toLong()) 0L else rx
                    val validTx = if (tx == UNSUPPORTED.toLong()) 0L else tx

                    if (validRx > 0 || validTx > 0) {
                        trafficList.add(
                            AppTrafficInfo(
                                uid = uid,
                                packageName = app.packageName,
                                appName = app.appName,
                                icon = app.icon,
                                // 普通模式无法区分移动/WiFi，统一归入WiFi
                                mobileRxBytes = 0L,
                                mobileTxBytes = 0L,
                                wifiRxBytes = validRx,
                                wifiTxBytes = validTx,
                                lastUpdated = System.currentTimeMillis()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "普通模式读取 ${app.packageName} 流量失败: ${e.message}")
            }
        }

        return trafficList.sortedByDescending { it.totalBytes }
    }

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

    /**
     * 检测当前网络类型
     * 通过NetworkInterface枚举网络接口来判断
     *
     * @return "WiFi" / "Mobile" / "None"
     */
    fun detectNetworkType(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            var hasWifi = false
            var hasMobile = false

            while (interfaces?.hasMoreElements() == true) {
                val ni = interfaces.nextElement()
                val name = ni.name.lowercase()
                if (name.startsWith("wlan") || name.startsWith("eth")) {
                    if (ni.isUp && !ni.isLoopback) {
                        hasWifi = true
                    }
                } else if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("rmnet_data")) {
                    if (ni.isUp) {
                        hasMobile = true
                    }
                }
            }

            when {
                hasWifi -> Constants.NETWORK_TYPE_WIFI
                hasMobile -> Constants.NETWORK_TYPE_MOBILE
                else -> Constants.NETWORK_TYPE_NONE
            }
        } catch (e: Exception) {
            Log.d(TAG, "检测网络类型失败: ${e.message}")
            Constants.NETWORK_TYPE_NONE
        }
    }

    /**
     * 获取已安装应用列表
     * 过滤系统应用（可选），获取包名、应用名、UID、图标等信息
     *
     * @param context 上下文
     * @param includeSystemApps 是否包含系统应用，默认false
     * @return 应用信息列表
     */
    fun getInstalledApps(context: Context, includeSystemApps: Boolean = false): List<com.netguardian.app.data.AppInfo> {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageInfo.MATCH_UNINSTALLED_PACKAGES)
        val appList = mutableListOf<com.netguardian.app.data.AppInfo>()

        for (packageInfo in packages) {
            try {
                val appInfo = packageInfo.applicationInfo

                // 过滤系统应用（可选）
                if (!includeSystemApps) {
                    if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
                        continue
                    }
                }

                // 跳过自身
                if (packageInfo.packageName == context.packageName) {
                    continue
                }

                val appName = appInfo.loadLabel(pm).toString()
                val icon = try {
                    appInfo.loadIcon(pm)
                } catch (e: Exception) {
                    null
                }

                appList.add(
                    com.netguardian.app.data.AppInfo(
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

        return when (mode) {
            Constants.MODE_ROOT -> {
                val data = rootManager.getProcessTraffic(uid)
                if (data.totalBytes > 0) {
                    AppTrafficInfo(
                        uid = uid,
                        packageName = packageName,
                        appName = appName,
                        wifiRxBytes = data.rxBytes,
                        wifiTxBytes = data.txBytes
                    )
                } else null
            }
            Constants.MODE_SHIZUKU -> {
                val (rx, tx) = shizukuManager.getUidTrafficShizuku(uid)
                if (rx > 0 || tx > 0) {
                    AppTrafficInfo(
                        uid = uid,
                        packageName = packageName,
                        appName = appName,
                        wifiRxBytes = rx,
                        wifiTxBytes = tx
                    )
                } else null
            }
            else -> {
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)
                if (rx > 0 && rx != UNSUPPORTED.toLong()) {
                    AppTrafficInfo(
                        uid = uid,
                        packageName = packageName,
                        appName = appName,
                        wifiRxBytes = rx,
                        wifiTxBytes = if (tx > 0) tx else 0L
                    )
                } else null
            }
        }
    }

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
}