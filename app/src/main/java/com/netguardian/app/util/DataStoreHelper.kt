package com.netguardian.app.util

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.netguardian.app.data.AppDatabase
import com.netguardian.app.model.TrafficRecord
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

    private const val TAG = "DataStoreHelper"

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

    /** 首次启动标志键 */
    private val KEY_FIRST_LAUNCH = booleanPreferencesKey(Constants.KEY_FIRST_RUN)

    /** 监控开关键 */
    private val KEY_MONITOR_ENABLED = booleanPreferencesKey(Constants.KEY_MONITOR_ENABLED)

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

    // ==================== 首次启动标志 ====================

    /** 读取首次启动标志 */
    fun isFirstLaunch(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_FIRST_LAUNCH] ?: true // 默认true
        }
    }

    /** 设置首次启动标志 */
    suspend fun setFirstLaunch(context: Context, isFirst: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FIRST_LAUNCH] = isFirst
        }
    }

    // ==================== 监控开关 ====================

    /** 监控开关状态 Flow */
    fun getMonitorEnabled(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_MONITOR_ENABLED] ?: true // 默认开启
        }
    }

    /** 读取监控开关状态（一次性） */
    suspend fun getMonitorEnabledOnce(context: Context): Boolean {
        return context.dataStore.data.first()[KEY_MONITOR_ENABLED] ?: true
    }

    /** 设置监控开关状态 */
    suspend fun setMonitorEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MONITOR_ENABLED] = enabled
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

    // ==================== 数据导入/导出 ====================

    /**
     * 从JSON字符串导入流量记录数据
     * 解析JSON格式的流量记录列表，并通过Room数据库的DAO进行批量插入恢复
     *
     * JSON格式要求：
     * ```json
     * [
     *   {
     *     "uid": 10086,
     *     "packageName": "com.example.app",
     *     "mobileRx": 1024,
     *     "mobileTx": 512,
     *     "wifiRx": 2048,
     *     "wifiTx": 1024,
     *     "date": "2026-07-17",
     *     "timestamp": 1689500000000
     *   }
     * ]
     * ```
     *
     * @param context 上下文
     * @param json JSON格式的流量记录字符串
     * @return 成功导入的记录数，失败返回-1
     */
    suspend fun importData(context: Context, json: String): Int {
        return try {
            // 解析JSON为TrafficRecord列表
            val records = parseTrafficRecordsFromJson(json)
            if (records.isEmpty()) {
                Log.w(TAG, "导入数据为空")
                return 0
            }

            Log.i(TAG, "开始导入 ${records.size} 条流量记录")

            // 通过AppDatabase的clearAndImportRecords方法批量导入
            val database = AppDatabase.getInstance(context)
            val result = database.clearAndImportRecords(records)

            if (result > 0) {
                Log.i(TAG, "数据导入成功，共导入 $result 条记录")
            } else {
                Log.e(TAG, "数据导入失败")
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "导入数据异常: ${e.message}", e)
            -1
        }
    }

    /**
     * 解析JSON字符串为TrafficRecord列表
     * 使用简单的JSON解析，不依赖Gson等第三方库
     *
     * @param json JSON数组字符串
     * @return TrafficRecord列表
     */
    private fun parseTrafficRecordsFromJson(json: String): List<TrafficRecord> {
        val records = mutableListOf<TrafficRecord>()

        try {
            // 使用org.json包（Android内置）进行解析
            val jsonArray = org.json.JSONArray(json)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                try {
                    val record = TrafficRecord(
                        uid = obj.optInt("uid", 0),
                        packageName = obj.optString("packageName", ""),
                        mobileRx = obj.optLong("mobileRx", 0L),
                        mobileTx = obj.optLong("mobileTx", 0L),
                        wifiRx = obj.optLong("wifiRx", 0L),
                        wifiTx = obj.optLong("wifiTx", 0L),
                        date = obj.optString("date", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )

                    // 跳过无效记录
                    if (record.packageName.isNotBlank() && record.date.isNotBlank()) {
                        records.add(record)
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "解析第 $i 条记录失败: ${e.message}")
                    continue
                }
            }
        } catch (e: org.json.JSONException) {
            Log.e(TAG, "JSON解析失败: ${e.message}")
        }

        return records
    }
}