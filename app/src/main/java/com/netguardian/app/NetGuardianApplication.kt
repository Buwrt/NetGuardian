package com.netguardian.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.netguardian.app.core.RootManager
import com.netguardian.app.core.ShizukuManager
import com.netguardian.app.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * NetGuardian应用全局Application类
 * 负责以下全局初始化工作：
 * 1. 提供全局Context引用
 * 2. 初始化Room数据库实例
 * 3. 初始化Root权限检测
 * 4. 初始化Shizuku权限检测
 * 5. 启动核心引擎的后台监控协程
 *
 * 所有操作均为纯本地离线，不进行任何网络请求
 */
class NetGuardianApplication : Application() {

    companion object {
        private const val TAG = "NetGuardianApp"

        /** 全局Application引用，用于获取Context */
        @Volatile
        private lateinit var appInstance: NetGuardianApplication

        /**
         * 获取全局Application实例
         * @return Application实例
         */
        fun getInstance(): NetGuardianApplication {
            return appInstance
        }

        /**
         * 获取全局Context（便捷方法）
         * @return Application Context
         */
        fun getAppContext(): Context {
            return appInstance.applicationContext
        }
    }

    /** 应用级协程Scope，使用SupervisorJob避免子协程失败影响其他协程 */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 数据库实例引用 */
    private lateinit var database: AppDatabase

    /** Root管理器实例 */
    private val rootManager = RootManager.getInstance()

    /** Shizuku管理器实例 */
    private val shizukuManager = ShizukuManager.getInstance()

    override fun onCreate() {
        super.onCreate()
        appInstance = this

        Log.i(TAG, "NetGuardian Application初始化开始...")

        // 1. 初始化数据库（提前触发懒加载）
        initDatabase()

        // 2. 初始化Root权限检测
        initRootDetection()

        // 3. 初始化Shizuku检测
        initShizukuDetection()

        Log.i(TAG, "NetGuardian Application初始化完成")
    }

    /**
     * 初始化Room数据库
     * 提前获取数据库实例，确保后续使用时不会出现初始化延迟
     */
    private fun initDatabase() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                database = AppDatabase.getInstance(this@NetGuardianApplication)
                // 执行一次简单查询来验证数据库可用性
                val recentRecords = database.trafficDao().getRecentRecords(1)
                Log.i(TAG, "数据库初始化成功，已有 ${recentRecords.size} 条记录")

                // 清理过期数据（默认保留30天）
                cleanOldRecords()
            } catch (e: Exception) {
                Log.e(TAG, "数据库初始化失败: ${e.message}", e)
            }
        }
    }

    /**
     * 初始化Root权限检测
     * 检测Root权限并启动周期性监控
     */
    private fun initRootDetection() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                val hasRoot = rootManager.hasRootAccess()
                Log.i(TAG, "Root权限检测结果: $hasRoot")

                // 如果有Root权限，启动周期性监控
                if (hasRoot) {
                    rootManager.startRootMonitoring(applicationScope)
                    Log.i(TAG, "Root权限监控已启动")

                    // 检查iptables可用性
                    val iptablesAvailable = rootManager.isIptablesAvailable()
                    Log.i(TAG, "iptables可用: $iptablesAvailable")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Root检测初始化异常: ${e.message}")
            }
        }
    }

    /**
     * 初始化Shizuku检测
     * 检查Shizuku服务是否可用
     */
    private fun initShizukuDetection() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                val isRunning = shizukuManager.isShizukuRunning()
                val hasPermission = shizukuManager.checkShizukuPermission()
                Log.i(TAG, "Shizuku检测 - 运行中: $isRunning, 已授权: $hasPermission")

                if (isRunning && hasPermission) {
                    Log.i(TAG, "Shizuku可用，可作为权限模式使用")
                } else if (isRunning) {
                    Log.i(TAG, "Shizuku运行中但未授权，需要在Activity中请求权限")
                } else {
                    Log.i(TAG, "Shizuku不可用，将使用Root或普通模式")
                }
            } catch (e: Exception) {
                // Shizuku未安装时可能抛出异常，属于正常情况
                Log.d(TAG, "Shizuku检测异常（可能未安装）: ${e.message}")
            }
        }
    }

    /**
     * 清理过期的流量记录
     * 根据配置的保留天数删除旧数据
     */
    private suspend fun cleanOldRecords() {
        try {
            val retentionDays = com.netguardian.app.util.Constants.DEFAULT_RETENTION_DAYS
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.DAY_OF_MONTH, -retentionDays)
            val cutoffDate = com.netguardian.app.util.TrafficFormatter.formatDate(calendar.timeInMillis)

            val deletedCount = database.trafficDao().deleteOldRecords(cutoffDate)
            if (deletedCount > 0) {
                Log.i(TAG, "已清理 $deletedCount 条过期记录（早于 $cutoffDate）")
            }
        } catch (e: Exception) {
            Log.d(TAG, "清理过期记录失败: ${e.message}")
        }
    }

    /**
     * 获取应用级协程Scope
     * 供需要长生命周期协程的组件使用
     */
    fun getApplicationScope(): CoroutineScope {
        return applicationScope
    }

    /**
     * 获取数据库实例
     */
    fun getDatabase(): AppDatabase {
        if (!::database.isInitialized) {
            database = AppDatabase.getInstance(this)
        }
        return database
    }

    /**
     * 获取Root管理器实例
     */
    fun getRootManager(): RootManager {
        return rootManager
    }

    /**
     * 获取Shizuku管理器实例
     */
    fun getShizukuManager(): ShizukuManager {
        return shizukuManager
    }

    override fun onTerminate() {
        super.onTerminate()
        // 停止Root监控
        rootManager.stopRootMonitoring()
        // 释放Shizuku资源
        shizukuManager.release()
        Log.i(TAG, "NetGuardian Application已终止")
    }

    /**
     * 在低内存时清理缓存
     */
    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "系统内存不足，执行清理")
    }

    /**
     * 在内存紧张时释放非必要资源
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            TRIM_MEMORY_RUNNING_LOW -> {
                Log.w(TAG, "内存紧张(TRIM_MEMORY_RUNNING_LOW)")
            }
            TRIM_MEMORY_UI_HIDDEN -> {
                Log.d(TAG, "UI已隐藏(TRIM_MEMORY_UI_HIDDEN)，可释放UI相关资源")
            }
        }
    }
}