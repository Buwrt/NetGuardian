package com.netguardian.app.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore偏好设置辅助类
 * 封装DataStore Preferences的读写操作，提供类型安全的API
 * 使用Flow暴露数据变化，支持响应式UI更新
 *
 * 所有数据存储在本地，纯离线运行，不进行任何网络请求
 */
object DataStoreHelper {

    /** 通过扩展属性创建DataStore实例，文件名为netguardian_settings.preferences_pb */
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = Constants.DATASTORE_NAME
    )

    // ==================== Preferences Keys ====================

    /** 主题模式键 */
    private val KEY_THEME = stringPreferencesKey(Constants.KEY_THEME_MODE)

    /** 防偷跑开关键 */
    private val KEY_ANTI_LEAK = booleanPreferencesKey(Constants.KEY_ANTI_LEAK_ENABLED)

    /** 自动断网开关键 */
    private val KEY_AUTO_DISCONNECT = booleanPreferencesKey(Constants.KEY_AUTO_DISCONNECT)

    /** 开机自启开关键 */
    private val KEY_BOOT_AUTO_START = booleanPreferencesKey(Constants.KEY_BOOT_AUTO_START)

    /** 限额提醒开关键 */
    private val KEY_LIMIT_REMINDER = booleanPreferencesKey(Constants.KEY_LIMIT_REMINDER)

    /** 监控通知开关键 */
    private val KEY_MONITOR_NOTIFICATION = booleanPreferencesKey(Constants.KEY_MONITOR_NOTIFICATION)

    /** 闲置检测间隔键 */
    private val KEY_IDLE_CHECK_INTERVAL = longPreferencesKey(Constants.KEY_IDLE_CHECK_INTERVAL)

    /** 当前权限模式键 */
    private val KEY_PRIVILEGE_MODE = stringPreferencesKey(Constants.KEY_PRIVILEGE_MODE)

    // ==================== 主题模式 ====================

    /**
     * 获取主题模式的Flow
     * @param context 上下文
     * @return Flow<String> 值为 "dark" / "light" / "system"
     */
    fun getThemeModeFlow(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[KEY_THEME] ?: "system"
        }
    }

    /**
     * 读取当前主题模式（一次性）
     * @param context 上下文
     * @return 主题模式字符串
     */
    suspend fun getThemeMode(context: Context): String {
        return context.dataStore.data.first()[KEY_THEME] ?: "system"
    }

    /**
     * 保存主题模式
     * @param context 上下文
     * @param mode 主题模式: "dark" / "light" / "system"
     */
    suspend fun setThemeMode(context: Context, mode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME] = mode
        }
    }

    // ==================== 防偷跑开关 ====================

    /**
     * 获取防偷跑开关的Flow
     * @param context 上下文
     * @return Flow<Boolean> true表示已启用防偷跑检测
     */
    fun getAntiLeakEnabledFlow(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[KEY_ANTI_LEAK] ?: false
        }
    }

    /**
     * 读取防偷跑开关状态（一次性）
     * @param context 上下文
     * @return true表示已启用
     */
    suspend fun getAntiLeakEnabled(context: Context): Boolean {
        return context.dataStore.data.first()[KEY_ANTI_LEAK] ?: false
    }

    /**
     * 设置防偷跑开关
     * @param context 上下文
     * @param enabled true=启用防偷跑检测
     */
    suspend fun setAntiLeakEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ANTI_LEAK] = enabled
        }
    }

    // ==================== 自动断网开关 ====================

    /**
     * 获取自动断网开关的Flow
     * @param context 上下文
     * @return Flow<Boolean> true表示超限时自动断网
     */
    fun getAutoDisconnectFlow(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[KEY_AUTO_DISCONNECT] ?: false
        }
    }

    /**
     * 读取自动断网开关状态（一次性）
     * @param context 上下文
     * @return true表示已启用自动断网
     */
    suspend fun getAutoDisconnect(context: Context): Boolean {
        return context.dataStore.data.first()[KEY_AUTO_DISCONNECT] ?: false
    }

    /**
     * 设置自动断网开关
     * @param context 上下文
     * @param enabled true=启用超限自动断网
     */
    suspend fun setAutoDisconnect(context: Context, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_DISCONNECT] = enabled
        }
    }

    // ==================== 开机自启开关 ====================

    /**
     * 获取开机自启开关的Flow
     * @param context 上下文
     * @return Flow<Boolean> true表示开机后自动启动监控服务
     */
    fun getBootAutoStartFlow(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[KEY_BOOT_AUTO_START] ?: false
        }
    }

    /**
     * 读取开机自启开关状态（一次性）
     * @param context 上下文
     * @return true表示已启用开机自启
     */
    suspend fun getBootAutoStart(context: Context): Boolean {
        return context.dataStore.data.first()[KEY_BOOT_AUTO_START] ?: false
    }

    /**
     * 设置开机自启开关
     * @param context 上下文
     * @param enabled true=启用开机自启
     */
    suspend fun setBootAutoStart(context: Context, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BOOT_AUTO_START] = enabled
        }
    }

    // ==================== 限额提醒开关 ====================

    /**
     * 获取限额提醒开关的Flow
     * @param context 上下文
     * @return Flow<Boolean> true表示启用限额超限提醒
     */
    fun getLimitReminderFlow(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[KEY_LIMIT_REMINDER] ?: true
        }
    }

    /**
     * 读取限额提醒开关状态（一次性）
     * @param context 上下文
     * @return true表示已启用限额提醒
     */
    suspend fun getLimitReminder(context: Context): Boolean {
        return context.dataStore.data.first()[KEY_LIMIT_REMINDER] ?: true
    }

    /**
     * 设置限额提醒开关
     * @param context 上下文
     * @param enabled true=启用限额提醒
     */
    suspend fun setLimitReminder(context: Context, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LIMIT_REMINDER] = enabled
        }
    }

    // ==================== 监控通知开关 ====================

    /**
     * 获取监控通知开关的Flow
     * @param context 上下文
     * @return Flow<Boolean> true表示显示监控常驻通知
     */
    fun getMonitorNotificationFlow(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[KEY_MONITOR_NOTIFICATION] ?: true
        }
    }

    /**
     * 读取监控通知开关状态（一次性）
     * @param context 上下文
     * @return true表示已启用监控通知
     */
    suspend fun getMonitorNotification(context: Context): Boolean {
        return context.dataStore.data.first()[KEY_MONITOR_NOTIFICATION] ?: true
    }

    /**
     * 设置监控通知开关
     * @param context 上下文
     * @param enabled true=启用监控常驻通知
     */
    suspend fun setMonitorNotification(context: Context, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MONITOR_NOTIFICATION] = enabled
        }
    }

    // ==================== 闲置检测间隔 ====================

    /**
     * 获取闲置检测间隔的Flow
     * @param context 上下文
     * @return Flow<Long> 闲置检测间隔（毫秒），默认60000ms（1分钟）
     */
    fun getIdleCheckIntervalFlow(context: Context): Flow<Long> {
        return context.dataStore.data.map { preferences ->
            preferences[KEY_IDLE_CHECK_INTERVAL] ?: 60_000L
        }
    }

    /**
     * 读取闲置检测间隔（一次性）
     * @param context 上下文
     * @return 闲置检测间隔（毫秒）
     */
    suspend fun getIdleCheckInterval(context: Context): Long {
        return context.dataStore.data.first()[KEY_IDLE_CHECK_INTERVAL] ?: 60_000L
    }

    /**
     * 设置闲置检测间隔
     * @param context 上下文
     * @param intervalMs 闲置检测间隔（毫秒）
     */
    suspend fun setIdleCheckInterval(context: Context, intervalMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_IDLE_CHECK_INTERVAL] = intervalMs
        }
    }

    // ==================== 当前权限模式 ====================

    /**
     * 获取当前权限模式的Flow
     * @param context 上下文
     * @return Flow<String> 值为 "root" / "shizuku" / "none"
     */
    fun getPrivilegeModeFlow(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[KEY_PRIVILEGE_MODE] ?: Constants.MODE_NONE
        }
    }

    /**
     * 读取当前权限模式（一次性）
     * @param context 上下文
     * @return 权限模式字符串
     */
    suspend fun getPrivilegeMode(context: Context): String {
        return context.dataStore.data.first()[KEY_PRIVILEGE_MODE] ?: Constants.MODE_NONE
    }

    /**
     * 设置当前权限模式
     * @param context 上下文
     * @param mode 权限模式: "root" / "shizuku" / "none"
     */
    suspend fun setPrivilegeMode(context: Context, mode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PRIVILEGE_MODE] = mode
        }
    }

    // ==================== 批量读取/写入 ====================

    /**
     * 一次性读取所有设置项
     * 适用于服务启动时批量加载配置，减少DataStore读取次数
     *
     * @param context 上下文
     * @return AllSettings 包含所有配置值的数据类
     */
    suspend fun getAllSettings(context: Context): AllSettings {
        val preferences = context.dataStore.data.first()
        return AllSettings(
            themeMode = preferences[KEY_THEME] ?: "system",
            antiLeakEnabled = preferences[KEY_ANTI_LEAK] ?: false,
            autoDisconnect = preferences[KEY_AUTO_DISCONNECT] ?: false,
            bootAutoStart = preferences[KEY_BOOT_AUTO_START] ?: false,
            limitReminder = preferences[KEY_LIMIT_REMINDER] ?: true,
            monitorNotification = preferences[KEY_MONITOR_NOTIFICATION] ?: true,
            idleCheckInterval = preferences[KEY_IDLE_CHECK_INTERVAL] ?: 60_000L,
            privilegeMode = preferences[KEY_PRIVILEGE_MODE] ?: Constants.MODE_NONE
        )
    }

    /**
     * 所有设置项的数据类
     * 用于一次性传递全部配置
     */
    data class AllSettings(
        /** 主题模式: "dark" / "light" / "system" */
        val themeMode: String,
        /** 防偷跑检测是否启用 */
        val antiLeakEnabled: Boolean,
        /** 超限自动断网是否启用 */
        val autoDisconnect: Boolean,
        /** 开机自启是否启用 */
        val bootAutoStart: Boolean,
        /** 限额提醒是否启用 */
        val limitReminder: Boolean,
        /** 监控通知是否启用 */
        val monitorNotification: Boolean,
        /** 闲置检测间隔（毫秒） */
        val idleCheckInterval: Long,
        /** 当前权限模式 */
        val privilegeMode: String
    )
}