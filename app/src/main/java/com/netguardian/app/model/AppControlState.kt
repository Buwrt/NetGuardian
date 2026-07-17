package com.netguardian.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 应用网络控制状态实体
 * 存储每个应用的网络屏蔽/允许状态
 */
@Entity(tableName = "app_control_states")
data class AppControlState(
    /** 应用包名，作为主键 */
    @PrimaryKey
    val packageName: String,
    /** 是否屏蔽移动网络 */
    val mobileBlocked: Boolean = false,
    /** 是否屏蔽WiFi网络 */
    val wifiBlocked: Boolean = false,
    /** 是否屏蔽后台网络 */
    val backgroundBlocked: Boolean = false,
    /** 最后更新时间戳（毫秒） */
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /** 判断该应用是否被完全屏蔽（移动+WiFi都屏蔽） */
    val isFullyBlocked: Boolean
        get() = mobileBlocked && wifiBlocked

    /** 判断该应用是否有任何屏蔽规则 */
    val hasAnyBlock: Boolean
        get() = mobileBlocked || wifiBlocked || backgroundBlocked
}