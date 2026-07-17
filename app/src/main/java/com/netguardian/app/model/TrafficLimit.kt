package com.netguardian.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 流量限制实体
 * 存储每个应用的流量配额设置
 */
@Entity(tableName = "traffic_limits")
data class TrafficLimit(
    /** 自增主键 */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 应用包名 */
    val packageName: String,
    /** 应用名称 */
    val appName: String,
    /** 移动网络流量限制（单位：MB），null表示不限制 */
    val mobileLimitMb: Long? = null,
    /** WiFi流量限制（单位：MB），null表示不限制 */
    val wifiLimitMb: Long? = null,
    /** 每日流量限额（单位：MB），null表示不限制 */
    val dailyLimitMb: Long? = null,
    /** 是否为全局限制（应用到所有应用） */
    val isGlobal: Boolean = false
) {
    /** 判断移动网络是否设置了限制 */
    val hasMobileLimit: Boolean
        get() = mobileLimitMb != null && mobileLimitMb > 0

    /** 判断WiFi是否设置了限制 */
    val hasWifiLimit: Boolean
        get() = wifiLimitMb != null && wifiLimitMb > 0

    /** 判断是否设置了每日限额 */
    val hasDailyLimit: Boolean
        get() = dailyLimitMb != null && dailyLimitMb > 0

    /**
     * 将MB转为字节数
     * @param mb 兆字节数
     * @return 字节数
     */
    fun mbToBytes(mb: Long): Long = mb * 1024L * 1024L
}