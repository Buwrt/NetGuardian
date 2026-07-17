package com.netguardian.app.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netguardian.app.core.AntiLeakEngine
import com.netguardian.app.core.NetworkControlEngine
import com.netguardian.app.core.RootManager
import com.netguardian.app.core.ShizukuManager
import com.netguardian.app.data.AppDatabase
import com.netguardian.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 设置页面ViewModel
 * 负责管理应用的各种设置和配置：
 * 1. Root/Shizuku权限管理
 * 2. 防偷跑功能开关
 * 3. 监控服务控制
 * 4. 主题切换
 * 5. 数据导出和清理
 * 6. 通知设置
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "SettingsViewModel"

        /** 数据导出文件名 */
        private const val EXPORT_FILE_NAME = "netguardian_export.csv"
    }

    /** Root管理器 */
    private val rootManager = RootManager.getInstance()

    /** Shizuku管理器 */
    private val shizukuManager = ShizukuManager.getInstance()

    /** 网络控制引擎 */
    private val networkControlEngine = NetworkControlEngine(application)

    /** 数据库 */
    private val database = AppDatabase.getInstance(application)

    /** SharedPreferences/DataStore */
    private val prefs = application.getSharedPreferences(
        Constants.DATASTORE_NAME, Context.MODE_PRIVATE
    )

    // ==================== 权限状态 ====================

    /** Root权限是否可用 */
    val isRootAvailable: StateFlow<Boolean> = rootManager.isRootAvailable

    /** Shizuku是否可用 */
    private val _isShizukuAvailable = MutableStateFlow(false)
    val isShizukuAvailable: StateFlow<Boolean> = _isShizukuAvailable.asStateFlow()

    // ==================== 功能开关 ====================

    /** 防偷跑流量开关 */
    private val _antiLeakEnabled = MutableStateFlow(
        prefs.getBoolean(Constants.KEY_ANTI_LEAK_ENABLED, false)
    )
    val antiLeakEnabled: StateFlow<Boolean> = _antiLeakEnabled.asStateFlow()

    /** 监控服务开关 */
    private val _monitorServiceRunning = MutableStateFlow(
        prefs.getBoolean(Constants.KEY_MONITOR_SERVICE_RUNNING, false)
    )
    val monitorServiceRunning: StateFlow<Boolean> = _monitorServiceRunning.asStateFlow()

    /** 自动断网开关（流量超限时自动断网） */
    private val _autoDisconnect = MutableStateFlow(
        prefs.getBoolean("auto_disconnect", false)
    )
    val autoDisconnect: StateFlow<Boolean> = _autoDisconnect.asStateFlow()

    // ==================== 通知设置 ====================

    /** 流量限额提醒开关 */
    private val _limitReminder = MutableStateFlow(
        prefs.getBoolean(Constants.KEY_NOTIFICATION_ENABLED, true)
    )
    val limitReminder: StateFlow<Boolean> = _limitReminder.asStateFlow()

    /** 监控通知开关 */
    private val _monitorNotification = MutableStateFlow(true)
    val monitorNotification: StateFlow<Boolean> = _monitorNotification.asStateFlow()

    // ==================== 数据管理 ====================

    /** 闲置检测间隔（秒） */
    private val _idleCheckInterval = MutableStateFlow(
        prefs.getInt("idle_check_interval", 30)
    )
    val idleCheckInterval: StateFlow<Int> = _idleCheckInterval.asStateFlow()

    /** 数据保留天数 */
    private val _dataRetentionDays = MutableStateFlow(
        prefs.getInt(Constants.KEY_DATA_RETENTION_DAYS, Constants.DEFAULT_RETENTION_DAYS)
    )
    val dataRetentionDays: StateFlow<Int> = _dataRetentionDays.asStateFlow()

    // ==================== 主题 ====================

    /** 是否启用深色主题 */
    private val _darkTheme = MutableStateFlow(
        prefs.getBoolean(Constants.KEY_DARK_THEME, false)
    )
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    // ==================== 操作状态 ====================

    /** 是否正在执行操作 */
    private val _isOperating = MutableStateFlow(false)
    val isOperating: StateFlow<Boolean> = _isOperating.asStateFlow()

    /** 操作结果消息 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 错误信息 */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // 初始化Shizuku可用性检测
        refreshShizukuStatus()
    }

    // ==================== 权限管理操作 ====================

    /**
     * 手动检测Root权限
     * 尝试执行su命令来验证Root权限是否可用
     */
    fun detectRoot() {
        _isOperating.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val hasRoot = rootManager.hasRootAccess()
                if (!hasRoot) {
                    // 尝试请求Root权限
                    rootManager.requestRootAccess()
                }
                _message.value = if (rootManager.isRootAvailable.value) {
                    "Root权限已获取"
                } else {
                    "Root权限不可用"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Root检测失败: ${e.message}"
            } finally {
                _isOperating.value = false
            }
        }
    }

    /**
     * 刷新Shizuku状态
     */
    fun refreshShizukuStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isShizukuAvailable.value = shizukuManager.isShizukuAvailable()
            } catch (_: Exception) {
                _isShizukuAvailable.value = false
            }
        }
    }

    // ==================== 功能开关操作 ====================

    /**
     * 设置防偷跑开关
     * @param enabled 是否启用
     */
    fun setAntiLeakEnabled(enabled: Boolean) {
        _antiLeakEnabled.value = enabled
        prefs.edit().putBoolean(Constants.KEY_ANTI_LEAK_ENABLED, enabled).apply()
        _message.value = if (enabled) "已启用防偷跑保护" else "已关闭防偷跑保护"
    }

    /**
     * 设置监控服务开关
     * @param running 是否运行
     */
    fun setMonitorServiceRunning(running: Boolean) {
        _monitorServiceRunning.value = running
        prefs.edit().putBoolean(Constants.KEY_MONITOR_SERVICE_RUNNING, running).apply()
        // 发送广播通知服务启动/停止
        val action = if (running) {
            Constants.ACTION_START_MONITOR
        } else {
            Constants.ACTION_STOP_MONITOR
        }
        try {
            val intent = Intent(action).setPackage(getApplication<Application>().packageName)
            getApplication<Application>().sendBroadcast(intent)
            _message.value = if (running) "监控服务已启动" else "监控服务已停止"
        } catch (e: Exception) {
            _errorMessage.value = "控制监控服务失败: ${e.message}"
        }
    }

    /**
     * 设置自动断网开关
     * @param enabled 是否启用
     */
    fun setAutoDisconnect(enabled: Boolean) {
        _autoDisconnect.value = enabled
        prefs.edit().putBoolean("auto_disconnect", enabled).apply()
        _message.value = if (enabled) "已启用自动断网" else "已关闭自动断网"
    }

    // ==================== 通知设置操作 ====================

    /**
     * 设置流量限额提醒开关
     */
    fun setLimitReminder(enabled: Boolean) {
        _limitReminder.value = enabled
        prefs.edit().putBoolean(Constants.KEY_NOTIFICATION_ENABLED, enabled).apply()
    }

    /**
     * 设置监控通知开关
     */
    fun setMonitorNotification(enabled: Boolean) {
        _monitorNotification.value = enabled
    }

    // ==================== 间隔设置 ====================

    /**
     * 设置闲置检测间隔
     * @param seconds 间隔秒数
     */
    fun setIdleCheckInterval(seconds: Int) {
        _idleCheckInterval.value = seconds.coerceIn(5, 300)
        prefs.edit().putInt("idle_check_interval", _idleCheckInterval.value).apply()
    }

    // ==================== 主题切换 ====================

    /**
     * 切换深色主题
     * @param dark 是否启用深色模式
     */
    fun setDarkTheme(dark: Boolean) {
        _darkTheme.value = dark
        prefs.edit().putBoolean(Constants.KEY_DARK_THEME, dark).apply()
    }

    // ==================== 数据管理操作 ====================

    /**
     * 清除所有历史数据
     * 删除数据库中的所有流量记录、限制和控制状态
     */
    fun clearAllData() {
        _isOperating.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dao = database.trafficDao()
                // 获取一个很早的日期来清除所有记录
                val oldDate = "2000-01-01"
                dao.deleteOldRecords(oldDate)
                dao.clearAllControlStates()
                _message.value = "所有历史数据已清除"
            } catch (e: Exception) {
                _errorMessage.value = "清除数据失败: ${e.message}"
            } finally {
                _isOperating.value = false
            }
        }
    }

    /**
     * 导出数据为CSV文件
     * 将流量记录导出到应用私有目录
     */
    fun exportData() {
        _isOperating.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dao = database.trafficDao()
                val records = dao.getRecentRecords(1000)

                if (records.isEmpty()) {
                    _message.value = "没有数据可导出"
                    _isOperating.value = false
                    return@launch
                }

                // 构建CSV内容
                val csvBuilder = StringBuilder()
                csvBuilder.appendLine("包名,日期,移动下载,移动上传,WiFi下载,WiFi上传,总流量")

                for (record in records) {
                    csvBuilder.appendLine(
                        "${record.packageName},${record.date}," +
                                "${record.mobileRx},${record.mobileTx}," +
                                "${record.wifiRx},${record.wifiTx}," +
                                "${record.totalBytes}"
                    )
                }

                // 写入文件
                val exportDir = getApplication<Application>().getExternalFilesDir(null)
                if (exportDir != null) {
                    val exportFile = java.io.File(exportDir, EXPORT_FILE_NAME)
                    exportFile.writeText(csvBuilder.toString())
                    _message.value = "数据已导出到: ${exportFile.absolutePath}"
                } else {
                    _errorMessage.value = "无法访问存储目录"
                }
            } catch (e: Exception) {
                _errorMessage.value = "导出失败: ${e.message}"
            } finally {
                _isOperating.value = false
            }
        }
    }

    /**
     * 清除所有网络规则
     * 恢复所有应用的网络访问
     */
    fun clearAllRules() {
        _isOperating.value = true
        viewModelScope.launch {
            try {
                val success = networkControlEngine.clearAllRules()
                if (success) {
                    _message.value = "已清除所有网络规则"
                } else {
                    _errorMessage.value = "清除规则失败"
                }
            } catch (e: Exception) {
                _errorMessage.value = "清除规则异常: ${e.message}"
            } finally {
                _isOperating.value = false
            }
        }
    }

    /**
     * 清除消息
     */
    fun clearMessage() {
        _message.value = null
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
