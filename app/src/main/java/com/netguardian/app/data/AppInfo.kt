package com.netguardian.app.data

import android.graphics.drawable.Drawable

/**
 * 应用信息数据类
 * 用于承载已安装应用的基本信息
 * （注：这是纯数据类，非Room实体）
 */
data class AppInfo(
    /** 应用包名 */
    val packageName: String,
    /** 应用名称 */
    val appName: String,
    /** 应用UID */
    val uid: Int,
    /** 应用图标 */
    val icon: Drawable? = null,
    /** 是否为系统应用 */
    val isSystemApp: Boolean = false,
    /** 版本号 */
    val versionName: String = "",
    /** 目标SDK版本 */
    val targetSdk: Int = 0
)