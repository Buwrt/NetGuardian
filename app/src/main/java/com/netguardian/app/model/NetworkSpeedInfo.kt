package com.netguardian.app.model

/**
 * 网络速度信息数据类
 * 用于实时展示当前网络的上传/下载速率
 */
data class NetworkSpeedInfo(
    /** 下载速度（字节/秒） */
    val downloadSpeed: Long,
    /** 上传速度（字节/秒） */
    val uploadSpeed: Long,
    /** 采样时间戳（毫秒） */
    val timestamp: Long = System.currentTimeMillis(),
    /** 网络类型：WiFi / Mobile / None */
    val networkType: String = "None"
) {
    /** 判断当前是否有网络连接 */
    val hasNetwork: Boolean
        get() = networkType != "None"

    /** 判断是否有数据传输活动 */
    val isActive: Boolean
        get() = downloadSpeed > 0 || uploadSpeed > 0
}