package com.netguardian.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.netguardian.app.model.AppControlState
import com.netguardian.app.model.TrafficLimit
import com.netguardian.app.model.TrafficRecord
import kotlinx.coroutines.flow.Flow

/**
 * 流量数据访问对象
 * 提供流量记录、流量限制、应用控制状态的数据库操作接口
 */
@Dao
interface TrafficDao {

    // ==================== 流量记录操作 ====================

    /**
     * 插入一条流量记录
     * @param record 流量记录实体
     * @return 插入行的ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: TrafficRecord): Long

    /**
     * 根据包名查询流量记录（按时间倒序）
     * @param packageName 应用包名
     * @return 该应用的流量记录列表
     */
    @Query("SELECT * FROM traffic_records WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun getRecordsByPackage(packageName: String): Flow<List<TrafficRecord>>

    /**
     * 根据日期范围查询流量记录
     * @param startDate 开始日期字符串（yyyy-MM-dd）
     * @param endDate 结束日期字符串（yyyy-MM-dd）
     * @return 指定日期范围内的流量记录列表
     */
    @Query("SELECT * FROM traffic_records WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC, timestamp ASC")
    fun getRecordsByDateRange(startDate: String, endDate: String): Flow<List<TrafficRecord>>

    /**
     * 查询指定日期的所有应用的总流量
     * @param date 日期字符串（yyyy-MM-dd）
     * @return 当天总接收字节数和总发送字节数的数组 [totalRx, totalTx]
     */
    @Query("SELECT COALESCE(SUM(mobileRx + wifiRx), 0), COALESCE(SUM(mobileTx + wifiTx), 0) FROM traffic_records WHERE date = :date")
    suspend fun getTotalTrafficByDate(date: String): LongArray

    /**
     * 查询所有流量记录（按时间倒序，分页）
     * @param limit 返回条数上限
     * @return 最近N条流量记录
     */
    @Query("SELECT * FROM traffic_records ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentRecords(limit: Int): List<TrafficRecord>

    /**
     * 删除指定日期之前的旧记录
     * @param date 日期字符串（yyyy-MM-dd），删除此日期之前的记录
     * @return 删除的记录数
     */
    @Query("DELETE FROM traffic_records WHERE date < :date")
    suspend fun deleteOldRecords(date: String): Int

    /** 查询指定时间戳范围内的所有流量记录 */
    @Query("SELECT * FROM traffic_records WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    suspend fun getRecordsByTimeRange(startTime: Long, endTime: Long): List<TrafficRecord>

    /** 查询指定月份的所有流量记录（按日期聚合） */
    @Query("""
        SELECT packageName,
               SUM(mobileRx) as mobileRx, SUM(mobileTx) as mobileTx,
               SUM(wifiRx) as wifiRx, SUM(wifiTx) as wifiTx,
               date, MIN(timestamp) as timestamp
        FROM traffic_records
        WHERE date LIKE :monthPattern || '%'
        GROUP BY packageName, date
        ORDER BY date DESC
    """)
    suspend fun getMonthlyAggregatedRecords(monthPattern: String): List<TrafficRecord>

    /** 删除超过3个月的历史数据（基于时间戳） */
    @Query("DELETE FROM traffic_records WHERE timestamp < :threeMonthsAgo")
    suspend fun deleteOldRecordsByTimestamp(threeMonthsAgo: Long): Int

    /**
     * 清空所有流量记录
     * 用于数据恢复前清理旧数据
     */
    @Query("DELETE FROM traffic_records")
    suspend fun clearAllRecords()

    /**
     * 批量插入流量记录
     * 用于数据恢复场景的批量导入
     * @param records 流量记录列表
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<TrafficRecord>)

    // ==================== 流量限制操作 ====================

    /**
     * 获取所有流量限制配置
     * @return 所有流量限制的列表
     */
    @Query("SELECT * FROM traffic_limits")
    fun getAllLimits(): Flow<List<TrafficLimit>>

    /**
     * 获取非全局的流量限制配置
     * @return 单个应用的流量限制列表
     */
    @Query("SELECT * FROM traffic_limits WHERE isGlobal = 0")
    suspend fun getAppSpecificLimits(): List<TrafficLimit>

    /**
     * 获取全局流量限制配置
     * @return 全局流量限制，可能为null
     */
    @Query("SELECT * FROM traffic_limits WHERE isGlobal = 1 LIMIT 1")
    suspend fun getGlobalLimit(): TrafficLimit?

    /**
     * 插入一条流量限制
     * @param limit 流量限制实体
     * @return 插入行的ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLimit(limit: TrafficLimit): Long

    /**
     * 根据包名查询流量限制
     * @param packageName 应用包名
     * @return 该应用的流量限制，可能为null
     */
    @Query("SELECT * FROM traffic_limits WHERE packageName = :packageName AND isGlobal = 0 LIMIT 1")
    suspend fun getLimitByPackage(packageName: String): TrafficLimit?

    /**
     * 删除指定流量限制
     * @param limit 要删除的流量限制实体
     */
    @androidx.room.Delete
    suspend fun deleteLimit(limit: TrafficLimit)

    /**
     * 根据ID删除流量限制
     * @param id 流量限制ID
     */
    @Query("DELETE FROM traffic_limits WHERE id = :id")
    suspend fun deleteLimitById(id: Long): Int

    // ==================== 应用控制状态操作 ====================

    /**
     * 获取指定应用的网络控制状态
     * @param packageName 应用包名
     * @return 控制状态，可能为null
     */
    @Query("SELECT * FROM app_control_states WHERE packageName = :packageName LIMIT 1")
    suspend fun getControlState(packageName: String): AppControlState?

    /**
     * 获取所有应用的网络控制状态
     * @return 所有控制状态的Flow
     */
    @Query("SELECT * FROM app_control_states")
    fun getAllControlStates(): Flow<List<AppControlState>>

    /**
     * 获取所有被屏蔽的应用（一次性查询）
     * @return 有任何屏蔽规则的应用控制状态列表
     */
    @Query("SELECT * FROM app_control_states WHERE mobileBlocked = 1 OR wifiBlocked = 1 OR backgroundBlocked = 1")
    suspend fun getBlockedStates(): List<AppControlState>

    /**
     * 插入或更新应用控制状态
     * 如果packageName已存在则更新，否则插入
     * @param state 应用控制状态实体
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateControlState(state: AppControlState)

    /**
     * 删除指定应用的控制状态
     * @param packageName 应用包名
     */
    @Query("DELETE FROM app_control_states WHERE packageName = :packageName")
    suspend fun deleteControlState(packageName: String): Int

    /**
     * 重置所有控制状态（清除所有屏蔽规则）
     */
    @Query("DELETE FROM app_control_states")
    suspend fun clearAllControlStates()
}