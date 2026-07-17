package com.netguardian.app.core

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.netguardian.app.data.AppDatabase
import com.netguardian.app.model.AppControlState
import com.netguardian.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 网络控制引擎
 * 负责应用网络权限的设置和管理，包括：
 * - 按应用屏蔽/允许移动网络和WiFi
 * - 后台网络控制
 * - 批量操作
 * - iptables规则管理
 *
 * 自动检测当前权限模式（Root / Shizuku / 无权限）并选择合适的实现方式
 * 所有操作均为纯本地离线执行，不进行任何网络请求
 */
class NetworkControlEngine(private val context: Context) {

    companion object {
        private const val TAG = "NetworkControlEngine"
    }

    private val rootManager = RootManager.getInstance()
    private val shizukuManager = ShizukuManager.getInstance()
    private val trafficDao = AppDatabase.getInstance(context).trafficDao()

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
     * 设置应用的网络权限
     * 根据当前权限模式自动选择实现方式
     *
     * @param packageName 应用包名
     * @param uid 应用UID
     * @param allowMobile 是否允许移动网络
     * @param allowWifi 是否允许WiFi
     * @return true表示操作成功
     */
    suspend fun setAppNetworkPermission(
        packageName: String,
        uid: Int,
        allowMobile: Boolean,
        allowWifi: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val blockMobile = !allowMobile
        val blockWifi = !allowWifi

        // 如果既不屏蔽移动也不屏蔽WiFi，则直接解除所有屏蔽
        if (!blockMobile && !blockWifi) {
            return@withContext unblockAppInternal(packageName)
        }

        val mode = getPrivilegeMode()
        Log.i(TAG, "设置 $packageName 网络权限 - 屏蔽移动: $blockMobile, 屏蔽WiFi: $blockWifi, 模式: $mode")

        val success = when (mode) {
            Constants.MODE_ROOT -> {
                rootManager.blockAppNetwork(packageName, blockMobile, blockWifi)
            }
            Constants.MODE_SHIZUKU -> {
                shizukuManager.blockAppViaShizuku(packageName, blockMobile, blockWifi)
            }
            else -> {
                Log.w(TAG, "无权限模式，无法设置网络权限")
                false
            }
        }

        // 成功后同步更新数据库
        if (success) {
            val currentState = trafficDao.getControlState(packageName)
            trafficDao.insertOrUpdateControlState(
                AppControlState(
                    packageName = packageName,
                    mobileBlocked = blockMobile,
                    wifiBlocked = blockWifi,
                    backgroundBlocked = currentState?.backgroundBlocked ?: false,
                    lastUpdated = System.currentTimeMillis()
                )
            )
            Log.i(TAG, "已更新 $packageName 控制状态到数据库")
        }

        success
    }

    /**
     * 启用或禁用应用的后台网络
     * 注意：Android没有直接的"禁止后台网络"API，
     * 这里通过iptables规则在检测到应用在后台时动态添加/删除来实现
     * 实际效果取决于后台检测的准确性
     *
     * @param packageName 应用包名
     * @param enable true=允许后台网络，false=屏蔽后台网络
     * @return true表示操作成功
     */
    suspend fun enableAppBackground(packageName: String, enable: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val currentState = trafficDao.getControlState(packageName)

            trafficDao.insertOrUpdateControlState(
                AppControlState(
                    packageName = packageName,
                    mobileBlocked = currentState?.mobileBlocked ?: false,
                    wifiBlocked = currentState?.wifiBlocked ?: false,
                    backgroundBlocked = !enable,
                    lastUpdated = System.currentTimeMillis()
                )
            )

            // 如果禁用后台网络，同时检查该应用是否在前台
            // 如果不在前台，则立即屏蔽其网络
            if (!enable) {
                val uid = getUidByPackageName(packageName)
                if (uid >= 0) {
                    val isForeground = isAppForeground(packageName)
                    if (!isForeground) {
                        Log.i(TAG, "$packageName 不在前台且后台被禁用，立即屏蔽网络")
                        return@withContext blockAppNetworkImmediate(packageName, uid)
                    }
                }
            } else {
                // 启用后台网络，确保规则被移除
                return@withContext unblockAppInternal(packageName)
            }

            Log.i(TAG, "已更新 $packageName 后台网络状态: ${if (enable) "允许" else "屏蔽"}")
            true
        }

    /**
     * 批量屏蔽后台网络
     *
     * @param list 要屏蔽后台网络的应用包名列表
     * @return Map<包名, 是否成功>
     */
    suspend fun batchBlockBackground(list: List<String>): Map<String, Boolean> =
        withContext(Dispatchers.IO) {
            val results = mutableMapOf<String, Boolean>()

            for (packageName in list) {
                try {
                    val success = enableAppBackground(packageName, enable = false)
                    results[packageName] = success
                } catch (e: Exception) {
                    Log.w(TAG, "批量屏蔽后台网络 $packageName 失败: ${e.message}")
                    results[packageName] = false
                }
            }

            Log.i(TAG, "批量屏蔽后台网络完成，成功: ${results.count { it.value }}/${list.size}")
            results
        }

    /**
     * 批量应用iptables规则
     * 将多条规则合并为一条命令执行以提高效率
     *
     * @param rules iptables规则列表
     * @return true表示全部执行成功
     */
    suspend fun applyIptablesRules(rules: List<IptablesRule>): Boolean = withContext(Dispatchers.IO) {
        if (rules.isEmpty()) return@withContext true

        val mode = getPrivilegeMode()
        Log.i(TAG, "批量应用iptables规则，规则数: ${rules.size}，模式: $mode")

        if (mode == Constants.MODE_NONE) {
            Log.w(TAG, "无权限模式，无法应用iptables规则")
            return@withContext false
        }

        // 将所有规则的命令合并
        val allCommands = mutableListOf<String>()
        for (rule in rules) {
            allCommands.addAll(rule.toCommands())
        }

        val fullCommand = allCommands.joinToString(" && ")

        return@withContext when (mode) {
            Constants.MODE_ROOT -> {
                val result = rootManager.executeRootCommand(fullCommand)
                val success = result != null
                if (!success) {
                    Log.w(TAG, "Root模式下批量执行iptables规则失败")
                    // 逐条重试
                    var retrySuccess = true
                    for (cmd in allCommands) {
                        val singleResult = rootManager.executeRootCommand(cmd)
                        if (singleResult == null) {
                            retrySuccess = false
                        }
                    }
                    retrySuccess
                } else {
                    success
                }
            }
            Constants.MODE_SHIZUKU -> {
                try {
                    val process = Shizuku_newProcess(fullCommand)
                    val exitCode = process.waitFor()
                    exitCode == 0
                } catch (e: Exception) {
                    Log.e(TAG, "Shizuku模式下批量执行iptables规则异常: ${e.message}")
                    false
                }
            }
            else -> false
        }
    }

    /**
     * 移除所有网络控制规则（恢复所有应用的网络访问）
     * @return true表示操作成功
     */
    suspend fun clearAllRules(): Boolean = withContext(Dispatchers.IO) {
        val mode = getPrivilegeMode()

        if (mode == Constants.MODE_NONE) {
            return@withContext false
        }

        // 获取所有有控制状态的应用
        val blockedStates = trafficDao.getBlockedStates()
        if (blockedStates.isEmpty()) {
            Log.i(TAG, "没有需要清除的规则")
            return@withContext true
        }

        var allSuccess = true
        for (state in blockedStates) {
            val success = unblockAppInternal(state.packageName)
            if (!success) {
                allSuccess = false
                Log.w(TAG, "清除 ${state.packageName} 的规则失败")
            }
        }

        // 清除数据库中的控制状态
        if (allSuccess) {
            trafficDao.clearAllControlStates()
        }

        Log.i(TAG, "清除所有规则完成: $allSuccess")
        allSuccess
    }

    /**
     * 恢复应用网络（内部方法）
     */
    private suspend fun unblockAppInternal(packageName: String): Boolean {
        val mode = getPrivilegeMode()

        val success = when (mode) {
            Constants.MODE_ROOT -> rootManager.unblockAppNetwork(packageName)
            Constants.MODE_SHIZUKU -> shizukuManager.unblockAppViaShizuku(packageName)
            else -> false
        }

        if (success) {
            // 保留后台设置，只清除移动/WiFi屏蔽
            val currentState = trafficDao.getControlState(packageName)
            if (currentState != null && currentState.hasAnyBlock) {
                trafficDao.insertOrUpdateControlState(
                    currentState.copy(
                        mobileBlocked = false,
                        wifiBlocked = false,
                        lastUpdated = System.currentTimeMillis()
                    )
                )
            } else if (currentState != null) {
                // 完全没有规则了，删除记录
                trafficDao.deleteControlState(packageName)
            }
        }

        return success
    }

    /**
     * 立即屏蔽应用网络（内部方法）
     */
    private fun blockAppNetworkImmediate(packageName: String, uid: Int): Boolean {
        val mode = getPrivilegeMode()
        return when (mode) {
            Constants.MODE_ROOT -> {
                rootManager.setIptablesRule(packageName, uid, block = true)
            }
            Constants.MODE_SHIZUKU -> {
                shizukuManager.blockAppViaShizuku(packageName, blockMobile = true, blockWifi = true)
            }
            else -> false
        }
    }

    /**
     * 通过包名获取UID
     */
    private fun getUidByPackageName(packageName: String): Int {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            appInfo.uid
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "找不到包名: $packageName")
            -1
        }
    }

    /**
     * 判断应用是否在前台
     * 通过ActivityManager获取当前前台任务来判断
     */
    private fun isAppForeground(packageName: String): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE)
                as? android.app.ActivityManager ?: return false

            val runningTasks = am.getRunningTasks(10)
            for (task in runningTasks) {
                if (task.topActivity?.packageName == packageName) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.d(TAG, "判断前台状态失败: ${e.message}")
            false
        }
    }

    /**
     * Shizuku模式下创建进程的辅助方法
     */
    private fun Shizuku_newProcess(command: String): Process {
        return rikka.shizuku.Shizuku.newProcess(
            arrayOf("sh", "-c", command),
            null, null
        )
    }

    /**
     * 获取应用当前的网络控制状态
     *
     * @param packageName 应用包名
     * @return 控制状态，null表示未设置
     */
    suspend fun getAppControlState(packageName: String): AppControlState? {
        return trafficDao.getControlState(packageName)
    }

    /**
     * 重新应用所有已保存的网络控制规则
     * 在设备重启后或服务重启时调用，恢复之前的iptables规则
     *
     * @return 成功应用的规则数
     */
    suspend fun reapplyAllRules(): Int = withContext(Dispatchers.IO) {
        val mode = getPrivilegeMode()
        if (mode == Constants.MODE_NONE) return@withContext 0

        val states = trafficDao.getBlockedStates()
        var successCount = 0

        for (state in states) {
            val uid = getUidByPackageName(state.packageName)
            if (uid < 0) continue

            val shouldBlockMobile = state.mobileBlocked
            val shouldBlockWifi = state.wifiBlocked

            if (shouldBlockMobile || shouldBlockWifi) {
                val rule = IptablesRule(
                    uid = uid,
                    packageName = state.packageName,
                    blockMobile = shouldBlockMobile,
                    blockWifi = shouldBlockWifi,
                    isAdd = true
                )
                val success = applyIptablesRules(listOf(rule))
                if (success) successCount++
            }
        }

        Log.i(TAG, "重新应用规则完成，成功: $successCount/${states.size}")
        successCount
    }
}