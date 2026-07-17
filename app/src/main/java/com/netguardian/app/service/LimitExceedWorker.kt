package com.netguardian.app.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.netguardian.app.core.NetworkControlEngine
import com.netguardian.app.core.TrafficMonitorEngine
import com.netguardian.app.data.AppDatabase
import com.netguardian.app.model.AppTrafficInfo
import com.netguardian.app.util.Constants
import com.netguardian.app.util.DataStoreHelper
import com.netguardian.app.util.TrafficFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 流量限额超限检测Worker
 * 使用WorkManager定期执行（每15分钟），独立于前台服务运行
 * 即使前台服务被系统杀死，WorkManager仍会按时触发检测
 *
 * 执行流程：
 * 1. 获取所有已设置流量限额的应用配置
 * 2. 获取全局流量限制配置
 * 3. 采集当前各应用的实时流量数据
 * 4. 对比流量数据与限额，找出超限应用
 * 5. 对超限应用发送通知提醒
 * 6. 如果开启了自动断网且为Root模式，自动断开超限应用的网络
 *
 * 纯本地离线运行，不进行任何网络请求
 */
class LimitExceedWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "LimitExceedWorker"

        /** 记录已发送过通知的超限应用，避免重复通知 */
        private val notifiedPackages = mutableSetOf<String>()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "开始执行限额检测任务...")

        try {
            // 初始化组件
            val database = AppDatabase.getInstance(applicationContext)
            val trafficDao = database.trafficDao()
            val trafficMonitorEngine = TrafficMonitorEngine(applicationContext)

            // 读取用户设置
            val limitReminder = DataStoreHelper.getLimitReminder(applicationContext)
            val autoDisconnect = DataStoreHelper.getAutoDisconnect(applicationContext)
            val privilegeMode = DataStoreHelper.getPrivilegeMode(applicationContext)

            // 如果提醒和自动断网都关闭了，无需检测
            if (!limitReminder && !autoDisconnect) {
                Log.d(TAG, "限额提醒和自动断网都已关闭，跳过检测")
                return@withContext Result.success()
            }

            // 获取所有应用专属限额配置
            val appSpecificLimits = trafficDao.getAppSpecificLimits()
            val globalLimit = trafficDao.getGlobalLimit()

            if (appSpecificLimits.isEmpty() && globalLimit == null) {
                Log.d(TAG, "未设置任何流量限额，跳过检测")
                return@withContext Result.success()
            }

            // 采集当前所有应用的实时流量数据
            val appTrafficList = trafficMonitorEngine.getPerAppTraffic()
            val trafficMap = appTrafficList.associateBy { it.packageName }

            // 检查全局限额
            checkGlobalLimit(globalLimit, appTrafficList, limitReminder)

            // 检查各应用限额
            var exceededCount = 0
            for (limit in appSpecificLimits) {
                val appTraffic = trafficMap[limit.packageName] ?: continue
                val exceeded = checkAppLimit(
                    limit = limit,
                    appTraffic = appTraffic,
                    limitReminder = limitReminder,
                    autoDisconnect = autoDisconnect,
                    privilegeMode = privilegeMode
                )
                if (exceeded) exceededCount++
            }

            Log.i(TAG, "限额检测完成，超限应用数: $exceededCount")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "限额检测任务执行失败: ${e.message}", e)
            // 返回retry让WorkManager稍后重试
            Result.retry()
        }
    }

    /**
     * 检查全局限额是否超限
     *
     * @param globalLimit 全局流量限制配置
     * @param appTrafficList 所有应用的流量列表
     * @param limitReminder 是否发送提醒通知
     */
    private fun checkGlobalLimit(
        globalLimit: com.netguardian.app.model.TrafficLimit?,
        appTrafficList: List<AppTrafficInfo>,
        limitReminder: Boolean
    ) {
        if (globalLimit == null) return

        // 计算全局总流量
        var globalTotalBytes = 0L
        for (app in appTrafficList) {
            globalTotalBytes += app.totalBytes
        }

        // 检查移动网络全局限额
        if (globalLimit.hasMobileLimit) {
            val limitBytes = globalLimit.mbToBytes(globalLimit.mobileLimitMb!!)
            if (globalTotalBytes > limitBytes) {
                Log.w(TAG, "全局流量超限！已用 ${TrafficFormatter.formatBytes(globalTotalBytes)}，限额 ${TrafficFormatter.formatBytes(limitBytes)}")

                // 发送通知（去重：使用特殊key "__global__"）
                val key = "__global__mobile"
                if (limitReminder && key !in notifiedPackages) {
                    TrafficMonitorNotification.showLimitExceedNotification(
                        context = applicationContext,
                        appName = "全部应用",
                        packageName = "__global__",
                        usedBytes = globalTotalBytes,
                        limitBytes = limitBytes,
                        networkType = "total",
                        notificationId = 0
                    )
                    notifiedPackages.add(key)
                }
            } else {
                // 未超限，清除通知记录（允许再次提醒）
                notifiedPackages.remove("__global__mobile")
            }
        }
    }

    /**
     * 检查单个应用的流量限额
     *
     * @param limit 流量限制配置
     * @param appTraffic 该应用的当前流量信息
     * @param limitReminder 是否发送提醒通知
     * @param autoDisconnect 是否自动断网
     * @param privilegeMode 当前权限模式
     * @return true表示该应用已超限
     */
    private suspend fun checkAppLimit(
        limit: com.netguardian.app.model.TrafficLimit,
        appTraffic: AppTrafficInfo,
        limitReminder: Boolean,
        autoDisconnect: Boolean,
        privilegeMode: String
    ): Boolean {
        var exceeded = false
        var networkType = "total"
        var exceededLimitBytes = 0L

        // 检查移动网络限额
        if (limit.hasMobileLimit) {
            val limitBytes = limit.mbToBytes(limit.mobileLimitMb!!)
            if (appTraffic.totalMobile > limitBytes) {
                exceeded = true
                networkType = "mobile"
                exceededLimitBytes = limitBytes
                Log.w(TAG, "${limit.appName} 移动流量超限！已用 ${TrafficFormatter.formatBytes(appTraffic.totalMobile)}，限额 ${TrafficFormatter.formatBytes(limitBytes)}")
            }
        }

        // 如果移动网络未超限，检查WiFi限额
        if (!exceeded && limit.hasWifiLimit) {
            val limitBytes = limit.mbToBytes(limit.wifiLimitMb!!)
            if (appTraffic.totalWifi > limitBytes) {
                exceeded = true
                networkType = "wifi"
                exceededLimitBytes = limitBytes
                Log.w(TAG, "${limit.appName} WiFi流量超限！已用 ${TrafficFormatter.formatBytes(appTraffic.totalWifi)}，限额 ${TrafficFormatter.formatBytes(limitBytes)}")
            }
        }

        if (!exceeded) {
            // 未超限，清除该应用的通知记录
            notifiedPackages.remove("${limit.packageName}_$networkType")
            return false
        }

        // 发送超限提醒通知（去重：同类型超限只通知一次，直到用户处理）
        val notifyKey = "${limit.packageName}_$networkType"
        if (limitReminder && notifyKey !in notifiedPackages) {
            TrafficMonitorNotification.showLimitExceedNotification(
                context = applicationContext,
                appName = limit.appName,
                packageName = limit.packageName,
                usedBytes = appTraffic.totalBytes,
                limitBytes = exceededLimitBytes,
                networkType = networkType,
                notificationId = limit.packageName.hashCode() and 0xFFFF
            )
            notifiedPackages.add(notifyKey)
        }

        // 自动断网（仅Root模式下有效）
        if (autoDisconnect && privilegeMode == Constants.MODE_ROOT) {
            try {
                val engine = NetworkControlEngine(applicationContext)
                val blockMobile = networkType == "mobile" || networkType == "total"
                val blockWifi = networkType == "wifi" || networkType == "total"
                val success = engine.setAppNetworkPermission(
                    packageName = limit.packageName,
                    uid = appTraffic.uid,
                    allowMobile = !blockMobile,
                    allowWifi = !blockWifi
                )
                if (success) {
                    Log.i(TAG, "WorkManager: 已自动断开 ${limit.appName} 的网络")
                } else {
                    Log.w(TAG, "WorkManager: 自动断开 ${limit.appName} 网络失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "WorkManager自动断网异常: ${e.message}", e)
            }
        }

        return true
    }
}