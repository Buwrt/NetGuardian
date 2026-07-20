package com.netguardian.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netguardian.app.core.TrafficMonitorEngine
import com.netguardian.app.data.AppInfo
import com.netguardian.app.model.AppTrafficInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 应用列表页面ViewModel
 * 负责以下功能：
 * 1. 获取并展示已安装应用的流量信息列表
 * 2. 支持按流量/名称/移动流量/WiFi流量排序
 * 3. 支持搜索过滤
 * 4. 定期刷新流量数据
 */
class AppListViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AppListViewModel"

        /** 每页显示数量 */
        private const val PAGE_SIZE = 50
    }

    /** 流量监控引擎 */
    private val trafficEngine = TrafficMonitorEngine(application)

    // ==================== 排序与过滤 ====================

    /** 排序方式 */
    private val _sortMode = MutableStateFlow(SortMode.BY_TRAFFIC)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    /** 搜索关键词 */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // ==================== 应用列表数据 ====================

    /** 完整应用流量列表（未经排序/过滤） */
    private val _allApps = MutableStateFlow<List<AppTrafficInfo>>(emptyList())
    val allApps: StateFlow<List<AppTrafficInfo>> = _allApps.asStateFlow()

    /** 经过排序和过滤后的显示列表 */
    val displayApps: StateFlow<List<AppTrafficInfo>> = combine(
        _allApps,
        _sortMode,
        _searchQuery
    ) { apps, sortMode, query ->
        var result = apps

        // 1. 搜索过滤
        if (query.isNotBlank()) {
            result = result.filter { app ->
                app.appName.contains(query, ignoreCase = true) ||
                        app.packageName.contains(query, ignoreCase = true)
            }
        }

        // 2. 排序
        result = when (sortMode) {
            SortMode.BY_TRAFFIC -> result.sortedByDescending { it.totalBytes }
            SortMode.BY_NAME -> result.sortedBy { it.appName.lowercase() }
            SortMode.BY_MOBILE -> result.sortedByDescending { it.totalMobile }
            SortMode.BY_WIFI -> result.sortedByDescending { it.totalWifi }
        }

        result
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // ==================== 统计信息 ====================

    /** 总应用数（仅用户应用） */
    private val _totalAppCount = MutableStateFlow(0)
    val totalAppCount: StateFlow<Int> = _totalAppCount.asStateFlow()

    /** 有流量的应用数 */
    private val _activeAppCount = MutableStateFlow(0)
    val activeAppCount: StateFlow<Int> = _activeAppCount.asStateFlow()

    /** 所有应用总流量 */
    private val _totalTraffic = MutableStateFlow(0L)
    val totalTraffic: StateFlow<Long> = _totalTraffic.asStateFlow()

    // ==================== 加载状态 ====================

    /** 是否正在加载 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 是否正在刷新 */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** 错误信息 */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // 首次加载应用列表：先获取已安装应用，再获取每个应用的流量
        loadAppList()
        // 启动定期刷新
        startPeriodicRefresh()
    }

    /**
     * 加载应用流量列表
     * 步骤：
     * 1. 通过 TrafficMonitorEngine.getInstalledApps() 获取所有已安装的用户可见应用
     * 2. 从数据库读取当前月份的流量记录（按包名聚合的增量数据）
     * 3. 合并已安装应用列表和数据库流量数据
     */
    fun loadAppList() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 第一步：获取已安装应用列表
                val installedApps: List<AppInfo> = withContext(Dispatchers.IO) {
                    trafficEngine.getInstalledApps(getApplication())
                }

                _totalAppCount.value = installedApps.size

                // 第二步：从数据库读取流量数据（按包名聚合）
                val db = com.netguardian.app.data.AppDatabase.getInstance(getApplication())
                val dao = db.trafficDao()

                // 查询当前月份的流量记录，按包名聚合
                val records: List<com.netguardian.app.model.TrafficRecord> = withContext(Dispatchers.IO) {
                    val now = java.util.Calendar.getInstance()
                    val monthPattern = "${now.get(java.util.Calendar.YEAR)}-${String.format("%02d", now.get(java.util.Calendar.MONTH) + 1)}"
                    dao.getMonthlyAggregatedRecords(monthPattern)
                }

                // 第三步：按包名聚合流量
                val trafficMap = mutableMapOf<String, AppTrafficInfo>()
                for (record in records) {
                    val existing = trafficMap[record.packageName]
                    if (existing != null) {
                        trafficMap[record.packageName] = AppTrafficInfo(
                            uid = record.uid,
                            packageName = record.packageName,
                            appName = existing.appName,
                            icon = existing.icon,
                            mobileRxBytes = existing.mobileRxBytes + record.mobileRx,
                            mobileTxBytes = existing.mobileTxBytes + record.mobileTx,
                            wifiRxBytes = existing.wifiRxBytes + record.wifiRx,
                            wifiTxBytes = existing.wifiTxBytes + record.wifiTx,
                            lastUpdated = System.currentTimeMillis()
                        )
                    } else {
                        trafficMap[record.packageName] = AppTrafficInfo(
                            uid = record.uid,
                            packageName = record.packageName,
                            appName = record.packageName,
                            icon = null, // 后面从installedApps补充
                            mobileRxBytes = record.mobileRx,
                            mobileTxBytes = record.mobileTx,
                            wifiRxBytes = record.wifiRx,
                            wifiTxBytes = record.wifiTx,
                            lastUpdated = System.currentTimeMillis()
                        )
                    }
                }

                // 第四步：合并已安装应用与数据库流量
                val allApps = installedApps.map { appInfo ->
                    val traffic = trafficMap[appInfo.packageName]
                    if (traffic != null) {
                        traffic.copy(appName = appInfo.appName, icon = appInfo.icon)
                    } else {
                        AppTrafficInfo(
                            uid = appInfo.uid,
                            packageName = appInfo.packageName,
                            appName = appInfo.appName,
                            icon = appInfo.icon,
                            mobileRxBytes = 0L,
                            mobileTxBytes = 0L,
                            wifiRxBytes = 0L,
                            wifiTxBytes = 0L,
                            lastUpdated = System.currentTimeMillis()
                        )
                    }
                }

                _allApps.value = allApps
                _activeAppCount.value = trafficMap.size
                _totalTraffic.value = allApps.sumOf { it.totalBytes }
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "加载应用列表失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 刷新应用列表
     * 用于下拉刷新操作，与loadAppList相同但设置不同的加载状态
     */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val installedApps: List<AppInfo> = withContext(Dispatchers.IO) {
                    trafficEngine.getInstalledApps(getApplication())
                }
                _totalAppCount.value = installedApps.size

                val db = com.netguardian.app.data.AppDatabase.getInstance(getApplication())
                val dao = db.trafficDao()

                val records: List<com.netguardian.app.model.TrafficRecord> = withContext(Dispatchers.IO) {
                    val now = java.util.Calendar.getInstance()
                    val monthPattern = "${now.get(java.util.Calendar.YEAR)}-${String.format("%02d", now.get(java.util.Calendar.MONTH) + 1)}"
                    dao.getMonthlyAggregatedRecords(monthPattern)
                }

                val trafficMap = mutableMapOf<String, AppTrafficInfo>()
                for (record in records) {
                    val existing = trafficMap[record.packageName]
                    if (existing != null) {
                        trafficMap[record.packageName] = AppTrafficInfo(
                            uid = record.uid,
                            packageName = record.packageName,
                            appName = existing.appName,
                            icon = existing.icon,
                            mobileRxBytes = existing.mobileRxBytes + record.mobileRx,
                            mobileTxBytes = existing.mobileTxBytes + record.mobileTx,
                            wifiRxBytes = existing.wifiRxBytes + record.wifiRx,
                            wifiTxBytes = existing.wifiTxBytes + record.wifiTx,
                            lastUpdated = System.currentTimeMillis()
                        )
                    } else {
                        trafficMap[record.packageName] = AppTrafficInfo(
                            uid = record.uid,
                            packageName = record.packageName,
                            appName = record.packageName,
                            icon = null,
                            mobileRxBytes = record.mobileRx,
                            mobileTxBytes = record.mobileTx,
                            wifiRxBytes = record.wifiRx,
                            wifiTxBytes = record.wifiTx,
                            lastUpdated = System.currentTimeMillis()
                        )
                    }
                }

                val allApps = installedApps.map { appInfo ->
                    val traffic = trafficMap[appInfo.packageName]
                    if (traffic != null) {
                        traffic.copy(appName = appInfo.appName, icon = appInfo.icon)
                    } else {
                        AppTrafficInfo(
                            uid = appInfo.uid,
                            packageName = appInfo.packageName,
                            appName = appInfo.appName,
                            icon = appInfo.icon,
                            mobileRxBytes = 0L,
                            mobileTxBytes = 0L,
                            wifiRxBytes = 0L,
                            wifiTxBytes = 0L,
                            lastUpdated = System.currentTimeMillis()
                        )
                    }
                }

                _allApps.value = allApps
                _activeAppCount.value = trafficMap.size
                _totalTraffic.value = allApps.sumOf { it.totalBytes }
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "刷新失败: ${e.message}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * 启动定期刷新
     * 每30秒自动刷新一次流量数据（从数据库读取，不再调用getPerAppTraffic）
     */
    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000L)
                try {
                    val installedApps: List<AppInfo> = withContext(Dispatchers.IO) {
                        trafficEngine.getInstalledApps(getApplication())
                    }
                    _totalAppCount.value = installedApps.size

                    val db = com.netguardian.app.data.AppDatabase.getInstance(getApplication())
                    val dao = db.trafficDao()

                    val records: List<com.netguardian.app.model.TrafficRecord> = withContext(Dispatchers.IO) {
                        val now = java.util.Calendar.getInstance()
                        val monthPattern = "${now.get(java.util.Calendar.YEAR)}-${String.format("%02d", now.get(java.util.Calendar.MONTH) + 1)}"
                        dao.getMonthlyAggregatedRecords(monthPattern)
                    }

                    val trafficMap = mutableMapOf<String, AppTrafficInfo>()
                    for (record in records) {
                        val existing = trafficMap[record.packageName]
                        if (existing != null) {
                            trafficMap[record.packageName] = AppTrafficInfo(
                                uid = record.uid,
                                packageName = record.packageName,
                                appName = existing.appName,
                                icon = existing.icon,
                                mobileRxBytes = existing.mobileRxBytes + record.mobileRx,
                                mobileTxBytes = existing.mobileTxBytes + record.mobileTx,
                                wifiRxBytes = existing.wifiRxBytes + record.wifiRx,
                                wifiTxBytes = existing.wifiTxBytes + record.wifiTx,
                                lastUpdated = System.currentTimeMillis()
                            )
                        } else {
                            trafficMap[record.packageName] = AppTrafficInfo(
                                uid = record.uid,
                                packageName = record.packageName,
                                appName = record.packageName,
                                icon = null,
                                mobileRxBytes = record.mobileRx,
                                mobileTxBytes = record.mobileTx,
                                wifiRxBytes = record.wifiRx,
                                wifiTxBytes = record.wifiTx,
                                lastUpdated = System.currentTimeMillis()
                            )
                        }
                    }

                    val allApps = installedApps.map { appInfo ->
                        val traffic = trafficMap[appInfo.packageName]
                        if (traffic != null) {
                            traffic.copy(appName = appInfo.appName, icon = appInfo.icon)
                        } else {
                            AppTrafficInfo(
                                uid = appInfo.uid,
                                packageName = appInfo.packageName,
                                appName = appInfo.appName,
                                icon = appInfo.icon,
                                mobileRxBytes = 0L,
                                mobileTxBytes = 0L,
                                wifiRxBytes = 0L,
                                wifiTxBytes = 0L,
                                lastUpdated = System.currentTimeMillis()
                            )
                        }
                    }

                    _allApps.value = allApps
                    _activeAppCount.value = trafficMap.size
                    _totalTraffic.value = allApps.sumOf { it.totalBytes }
                } catch (_: Exception) {
                    // 后台刷新失败静默处理
                }
            }
        }
    }

    /**
     * 设置排序方式
     * @param mode 排序模式
     */
    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    /**
     * 设置搜索关键词
     * @param query 搜索关键词
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * 清除搜索
     */
    fun clearSearch() {
        _searchQuery.value = ""
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 排序模式枚举
     */
    enum class SortMode(val displayName: String) {
        /** 按总流量降序 */
        BY_TRAFFIC("按流量"),
        /** 按应用名称升序 */
        BY_NAME("按名称"),
        /** 按移动流量降序 */
        BY_MOBILE("按移动流量"),
        /** 按WiFi流量降序 */
        BY_WIFI("按WiFi流量")
    }
}