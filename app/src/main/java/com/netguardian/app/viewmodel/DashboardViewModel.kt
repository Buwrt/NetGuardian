package com.netguardian.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netguardian.app.core.RootManager
import com.netguardian.app.core.ShizukuManager
import com.netguardian.app.core.TrafficMonitorEngine
import com.netguardian.app.data.AppDatabase
import com.netguardian.app.model.NetworkSpeedInfo
import com.netguardian.app.util.Constants
import com.netguardian.app.util.TrafficFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 总览页面ViewModel
 * 负责以下数据的采集和管理：
 * 1. 实时网速（下载/上传速率），通过TrafficMonitorEngine的Flow获取
 * 2. 今日总流量（移动+WiFi）
 * 3. 当前权限模式（Root/Shizuku/无权限）
 * 4. 监控服务状态
 * 5. 网络类型检测
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "DashboardViewModel"
    }

    /** 流量监控引擎 */
    private val trafficEngine = TrafficMonitorEngine(application)

    /** Root管理器 */
    private val rootManager = RootManager.getInstance()

    /** Shizuku管理器 */
    private val shizukuManager = ShizukuManager.getInstance()

    // ==================== 实时网速数据 ====================

    /** 实时网速信息（下载/上传速率、网络类型） */
    private val _networkSpeed = MutableStateFlow(NetworkSpeedInfo(0L, 0L))
    val networkSpeed: StateFlow<NetworkSpeedInfo> = _networkSpeed.asStateFlow()

    /** 当前网络类型（WiFi/Mobile/None） */
    private val _networkType = MutableStateFlow(Constants.NETWORK_TYPE_NONE)
    val networkType: StateFlow<String> = _networkType.asStateFlow()

    // ==================== 今日流量汇总 ====================

    /** 今日移动网络总流量（字节） */
    private val _todayMobileTraffic = MutableStateFlow(0L)
    val todayMobileTraffic: StateFlow<Long> = _todayMobileTraffic.asStateFlow()

    /** 今日WiFi总流量（字节） */
    private val _todayWifiTraffic = MutableStateFlow(0L)
    val todayWifiTraffic: StateFlow<Long> = _todayWifiTraffic.asStateFlow()

    /** 今日总流量（移动+WiFi） */
    val todayTotalTraffic: StateFlow<Long> = combine(
        _todayMobileTraffic,
        _todayWifiTraffic
    ) { mobile, wifi -> mobile + wifi }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0L
    )

    // ==================== 权限模式 ====================

    /** 当前权限模式: "root" / "shizuku" / "none" */
    val privilegeMode: StateFlow<String> = combine(
        rootManager.isRootAvailable,
        flow { emit(shizukuManager.isShizukuAvailable()) }
    ) { isRoot, isShizuku ->
        when {
            isRoot -> Constants.MODE_ROOT
            isShizuku -> Constants.MODE_SHIZUKU
            else -> Constants.MODE_NONE
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Constants.MODE_NONE)

    /** Root权限是否可用 */
    val isRootAvailable: StateFlow<Boolean> = rootManager.isRootAvailable

    // ==================== 监控服务状态 ====================

    /** 监控服务是否正在运行 */
    private val _isMonitorRunning = MutableStateFlow(false)
    val isMonitorRunning: StateFlow<Boolean> = _isMonitorRunning.asStateFlow()

    // ==================== 加载状态 ====================

    /** 是否正在加载初始数据 */
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 错误信息 */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // 启动实时网速采集
        startSpeedMonitoring()
        // 刷新今日流量
        refreshTodayTraffic()
        // 定期刷新流量和权限状态
        startPeriodicRefresh()
    }

    /**
     * 启动实时网速监测
     * 从TrafficMonitorEngine获取Flow数据流，在协程中持续收集
     */
    private fun startSpeedMonitoring() {
        viewModelScope.launch {
            trafficEngine.getNetworkSpeedRealtime(Constants.SPEED_SAMPLE_INTERVAL_MS)
                .catch { e ->
                    _errorMessage.value = "网速监测异常: ${e.message}"
                }
                .collect { speedInfo ->
                    _networkSpeed.value = speedInfo
                    _networkType.value = speedInfo.networkType
                    // 首次收到数据后取消加载状态
                    if (_isLoading.value) {
                        _isLoading.value = false
                    }
                }
        }
    }

    /**
     * 刷新今日流量汇总
     * 从数据库读取今日的增量流量数据（而非 TrafficStats 累计值）
     */
    fun refreshTodayTraffic() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dao = AppDatabase.getInstance(getApplication()).trafficDao()
                val today = TrafficFormatter.getTodayDate()
                val result = dao.getTrafficByNetworkType(today)
                _todayMobileTraffic.value = result[0] // mobileTotal
                _todayWifiTraffic.value = result[1] // wifiTotal
            } catch (e: Exception) {
                _errorMessage.value = "获取今日流量失败: ${e.message}"
            }
        }
    }

    /**
     * 启动定期刷新任务
     * 每30秒刷新一次流量数据和权限状态
     */
    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(Constants.APP_LIST_REFRESH_INTERVAL_MS)
                refreshTodayTraffic()
                refreshPrivilegeMode()
            }
        }
    }

    /**
     * 刷新权限模式状态
     * 重新检测Root和Shizuku的可用性
     */
    private fun refreshPrivilegeMode() {
        viewModelScope.launch(Dispatchers.IO) {
            // RootManager自带StateFlow会自动更新
            // Shizuku需要手动触发检测
            try {
                shizukuManager.isShizukuAvailable()
            } catch (_: Exception) {
                // Shizuku不可用时忽略
            }
        }
    }

    /**
     * 切换监控服务状态
     * @param running true=启动监控, false=停止监控
     */
    fun setMonitorRunning(running: Boolean) {
        _isMonitorRunning.value = running
    }

    /**
     * 手动刷新所有数据
     * 用于下拉刷新操作
     */
    fun refreshAll() {
        refreshTodayTraffic()
        refreshPrivilegeMode()
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel销毁时不需要特殊清理，Flow会自动取消
    }
}
