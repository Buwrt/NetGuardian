package com.netguardian.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 流量记录实体
 * 存储每个应用每日的流量数据快照
 */
@Entity(tableName = "traffic_records")
data class TrafficRecord(
    /** 自增主键 */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 应用UID */
    val uid: Int,
    /** 应用包名 */
    val packageName: String,
    /** 移动网络接收字节数 */
    val mobileRx: Long,
    /** 移动网络发送字节数 */
    val mobileTx: Long,
    /** WiFi接收字节数 */
    val wifiRx: Long,
    /** WiFi发送字节数 */
    val wifiTx: Long,
    /** 日期字符串（yyyy-MM-dd格式） */
    val date: String,
    /** 记录时间戳（毫秒） */
    val timestamp: Long = System.currentTimeMillis()
) {
    /** 该条记录的移动网络总流量 */
    val totalMobile: Long
        get() = mobileRx + mobileTx

    /** 该条记录的WiFi总流量 */
    val totalWifi: Long
        get() = wifiRx + wifiTx

    /** 该条记录的全部总流量 */
    val totalBytes: Long
        get() = totalMobile + totalWifi
}