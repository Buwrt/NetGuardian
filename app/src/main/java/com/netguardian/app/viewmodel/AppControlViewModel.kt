package com.netguardian.app.viewmodel

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.netguardian.app.core.NetworkControlEngine
import com.netguardian.app.core.TrafficMonitorEngine
import com.netguardian.app.data.AppDatabase
import com.netguardian.app.data.AppInfo
import com.netguardian.app.model.AppControlState
import com.netguardian.app.model.AppTrafficInfo
import com.netguardian.app.model.TrafficLimit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 应用控制页面ViewModel
 * 负责单个应用的网络权限管理和流量限额设置：
 * 1. 联网权限控制（移动数据/WiFi/后台网络）
 * 2. 流量限额设置
 * 3. 当前周期流量使用详情
 * 4. 强制断网操作
 *
 * @param application Application上下文
 * @param savedStateHandle SavedStateHandle，用于接收导航参数packageName
 */
class AppControlViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AppControlViewModel"
        private const val KEY_PACKAGE_NAME = "packageName"
    }

    /** 目标应用的包名 */
    val packageName: String = savedStateHandle[KEY_PACKAGE_NAME] ?: ""

    /** 网络控制引擎 */
    private val networkControlEngine = NetworkControlEngine(application)

    /** 流量监控引擎 */
    private val trafficEngine = TrafficMonitorEngine(application)

    /** 数据库 */
    private val trafficDao = AppDatabase.getInstance(application).trafficDao()

    // ==================== 应用基本信息 ====================

    /** 应用名称 */
    private val _appName = MutableStateFlow("加载中...")
    val appName: StateFlow<String> = _appName.asStateFlow()

    /** 应用图标 */
    private val _appIcon = MutableStateFlow<Drawable?>(null)
    val appIcon: StateFlow<Drawable?> = _appIcon.asStateFlow()

    /** 应用UID */
    private val _appUid = MutableStateFlow(0)
    val appUid: StateFlow<Int> = _appUid.asStateFlow()

    // ==================== 权限控制状态 ====================

    /** 是否允许移动数据 */
    private val _mobileAllowed = MutableStateFlow(true)
    val mobileAllowed: StateFlow<Boolean> = _mobileAllowed.asStateFlow()

    /** 是否允许WiFi */
    private val _wifiAllowed = MutableStateFlow(true)
    val wifiAllowed: StateFlow<Boolean> = _wifiAllowed.asStateFlow()

    /** 是否允许后台网络 */
    private val _backgroundAllowed = MutableStateFlow(true)
    val backgroundAllowed: StateFlow<Boolean> = _backgroundAllowed.asStateFlow()

    // ==================== 流量限额 ====================

    /** 移动流量限额（MB），null表示不限制 */
    private val _mobileLimitMb = MutableStateFlow<String>("")
    val mobileLimitMb: StateFlow<String> = _mobileLimitMb.asStateFlow()

    /** WiFi流量限额（MB），null表示不限制 */
    private val _wifiLimitMb = MutableStateFlow<String>("")
    val wifiLimitMb: StateFlow<String> = _wifiLimitMb.asStateFlow()

    // ==================== 流量使用详情 ====================

    /** 当前流量使用信息 */
    private val _trafficInfo = MutableStateFlow<AppTrafficInfo?>(null)
    val trafficInfo: StateFlow<AppTrafficInfo?> = _trafficInfo.asStateFlow()

    /** 已使用移动流量占限额的百分比 */
    val mobileUsagePercent: StateFlow<Float> = combine(
        _trafficInfo,
        _mobileLimitMb
    ) { traffic, limitStr ->
        val limit = limitStr.toLongOrNull() ?: 0L
        if (limit <= 0 || traffic == null) 0f
        else (traffic.totalMobile.toFloat() / (limit * 1024f * 1024f)).coerceIn(0f, 1f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    /** 已使用WiFi流量占限额的百分比 */
    val wifiUsagePercent: StateFlow<Float> = combine(
        _trafficInfo,
        _wifiLimitMb
    ) { traffic, limitStr ->
        val limit = limitStr.toLongOrNull() ?: 0L
        if (limit <= 0 || traffic == null) 0f
        else (traffic.totalWifi.toFloat() / (limit * 1024f * 1024f)).coerceIn(0f, 1f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    // ==================== 操作状态 ====================

    /** 是否正在执行操作（显示加载指示器） */
    private val _isOperating = MutableStateFlow(false)
    val isOperating: StateFlow<Boolean> = _isOperating.asStateFlow()

    /** 是否正在加载初始数据 */
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 操作结果消息 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 错误信息 */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        if (packageName.isNotEmpty()) {
            loadAppInfo()
            loadControlState()
            loadTrafficInfo()
            loadTrafficLimit()
        } else {
            _errorMessage.value = "无效的应用包名"
            _isLoading.value = false
        }
    }

    /**
     * 加载应用基本信息
     * 从PackageManager获取应用名称、图标、UID
     */
    private fun loadAppInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = getApplication<Application>().packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                _appName.value = appInfo.loadLabel(pm).toString()
                _appIcon.value = appInfo.loadIcon(pm)
                _appUid.value = appInfo.uid
            } catch (e: Exception) {
                _errorMessage.value = "无法获取应用信息: ${e.message}"
                _appName.value = packageName
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 加载应用的网络控制状态
     * 从数据库读取已保存的权限配置
     */
    private fun loadControlState() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val state = trafficDao.getControlState(packageName)
                if (state != null) {
                    _mobileAllowed.value = !state.mobileBlocked
                    _wifiAllowed.value = !state.wifiBlocked
                    _backgroundAllowed.value = !state.backgroundBlocked
                } else {
                    // 无记录，默认全部允许
                    _mobileAllowed.value = true
                    _wifiAllowed.value = true
                    _backgroundAllowed.value = true
                }
            } catch (e: Exception) {
                _errorMessage.value = "加载控制状态失败: ${e.message}"
            }
        }
    }

    /**
     * 加载应用的流量使用信息
     */
    private fun loadTrafficInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 先用当前UID获取流量信息
                val uid = _appUid.value
                if (uid > 0) {
                    val info = trafficEngine.getAppTraffic(
                        uid = uid,
                        packageName = packageName,
                        appName = _appName.value
                    )
                    _trafficInfo.value = info
                }
            } catch (e: Exception) {
                // 流量获取失败不阻断页面
            }
        }
    }

    /**
     * 加载应用的流量限额设置
     */
    private fun loadTrafficLimit() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val limit = trafficDao.getLimitByPackage(packageName)
                if (limit != null) {
                    _mobileLimitMb.value = limit.mobileLimitMb?.toString() ?: ""
                    _wifiLimitMb.value = limit.wifiLimitMb?.toString() ?: ""
                }
            } catch (e: Exception) {
                // 限额获取失败不阻断页面
            }
        }
    }

    /**
     * 设置移动数据权限
     * @param allowed 是否允许移动数据
     */
    fun setMobileAllowed(allowed: Boolean) {
        _isOperating.value = true
        viewModelScope.launch {
            try {
                val uid = _appUid.value
                val success = networkControlEngine.setAppNetworkPermission(
                    packageName = packageName,
                    uid = uid,
                    allowMobile = allowed,
                    allowWifi = _wifiAllowed.value
                )
                if (success) {
                    _mobileAllowed.value = allowed
                    _message.value = if (allowed) "已允许移动数据" else "已屏蔽移动数据"
                } else {
                    _errorMessage.value = "设置失败，可能缺少Root/Shizuku权限"
                }
            } catch (e: Exception) {
                _errorMessage.value = "操作异常: ${e.message}"
            } finally {
                _isOperating.value = false
            }
        }
    }

    /**
     * 设置WiFi权限
     * @param allowed 是否允许WiFi
     */
    fun setWifiAllowed(allowed: Boolean) {
        _isOperating.value = true
        viewModelScope.launch {
            try {
                val uid = _appUid.value
                val success = networkControlEngine.setAppNetworkPermission(
                    packageName = packageName,
                    uid = uid,
                    allowMobile = _mobileAllowed.value,
                    allowWifi = allowed
                )
                if (success) {
                    _wifiAllowed.value = allowed
                    _message.value = if (allowed) "已允许WiFi" else "已屏蔽WiFi"
                } else {
                    _errorMessage.value = "设置失败，可能缺少Root/Shizuku权限"
                }
            } catch (e: Exception) {
                _errorMessage.value = "操作异常: ${e.message}"
            } finally {
                _isOperating.value = false
            }
        }
    }

    /**
     * 设置后台网络权限
     * @param allowed 是否允许后台网络
     */
    fun setBackgroundAllowed(allowed: Boolean) {
        _isOperating.value = true
        viewModelScope.launch {
            try {
                val success = networkControlEngine.enableAppBackground(
                    packageName = packageName,
                    enable = allowed
                )
                if (success) {
                    _backgroundAllowed.value = allowed
                    _message.value = if (allowed) "已允许后台网络" else "已屏蔽后台网络"
                } else {
                    _errorMessage.value = "设置失败"
                }
            } catch (e: Exception) {
                _errorMessage.value = "操作异常: ${e.message}"
            } finally {
                _isOperating.value = false
            }
        }
    }

    /**
     * 保存流量限额设置
     * @param mobileMb 移动流量限额（MB），0或空表示不限制
     * @param wifiMb WiFi流量限额（MB），0或空表示不限制
     */
    fun saveTrafficLimit(mobileMb: String, wifiMb: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val limit = TrafficLimit(
                    packageName = packageName,
                    appName = _appName.value,
                    mobileLimitMb = mobileMb.toLongOrNull()?.takeIf { it > 0 },
                    wifiLimitMb = wifiMb.toLongOrNull()?.takeIf { it > 0 }
                )
                trafficDao.insertLimit(limit)
                _mobileLimitMb.value = mobileMb
                _wifiLimitMb.value = wifiMb
                _message.value = "流量限额已保存"
            } catch (e: Exception) {
                _errorMessage.value = "保存限额失败: ${e.message}"
            }
        }
    }

    /**
     * 强制断网 - 同时屏蔽移动和WiFi
     */
    fun forceDisconnect() {
        _isOperating.value = true
        viewModelScope.launch {
            try {
                val uid = _appUid.value
                val success = networkControlEngine.setAppNetworkPermission(
                    packageName = packageName,
                    uid = uid,
                    allowMobile = false,
                    allowWifi = false
                )
                if (success) {
                    _mobileAllowed.value = false
                    _wifiAllowed.value = false
                    _message.value = "已强制断网"
                } else {
                    _errorMessage.value = "强制断网失败，可能缺少权限"
                }
            } catch (e: Exception) {
                _errorMessage.value = "操作异常: ${e.message}"
            } finally {
                _isOperating.value = false
            }
        }
    }

    /**
     * 恢复所有网络权限
     */
    fun restoreAll() {
        _isOperating.value = true
        viewModelScope.launch {
            try {
                val uid = _appUid.value
                val success = networkControlEngine.setAppNetworkPermission(
                    packageName = packageName,
                    uid = uid,
                    allowMobile = true,
                    allowWifi = true
                )
                if (success) {
                    _mobileAllowed.value = true
                    _wifiAllowed.value = true
                    _backgroundAllowed.value = true
                    _message.value = "已恢复所有网络权限"
                } else {
                    _errorMessage.value = "恢复失败"
                }
            } catch (e: Exception) {
                _errorMessage.value = "操作异常: ${e.message}"
            } finally {
                _isOperating.value = false
            }
        }
    }

    /**
     * 刷新流量数据
     */
    fun refreshTraffic() {
        loadTrafficInfo()
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
