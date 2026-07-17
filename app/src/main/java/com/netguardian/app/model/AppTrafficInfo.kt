package com.netguardian.app.model

import android.graphics.drawable.Drawable

/**
 * 应用流量信息数据类
 * 用于展示单个应用的流量使用情况
 */
data class AppTrafficInfo(
    /** 应用UID */
    val uid: Int,
    /** 应用包名 */
    val packageName: String,
    /** 应用名称 */
    val appName: String,
    /** 应用图标 */
    val icon: Drawable? = null,
    /** 移动网络接收字节数 */
    val mobileRxBytes: Long = 0L,
    /** 移动网络发送字节数 */
    val mobileTxBytes: Long = 0L,
    /** WiFi接收字节数 */
    val wifiRxBytes: Long = 0L,
    /** WiFi发送字节数 */
    val wifiTxBytes: Long = 0L,
    /** 最后更新时间戳（毫秒） */
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /** 移动网络总流量（接收+发送） */
    val totalMobile: Long
        get() = mobileRxBytes + mobileTxBytes

    /** WiFi总流量（接收+发送） */
    val totalWifi: Long
        get() = wifiRxBytes + wifiTxBytes

    /** 全部总流量（移动+WiFi） */
    val totalBytes: Long
        get() = totalMobile + totalWifi
}