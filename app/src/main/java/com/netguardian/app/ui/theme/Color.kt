package com.netguardian.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * NetGuardian 应用颜色定义
 * 包含浅色/深色主题所需的全部颜色常量
 * 遵循Material3设计规范
 */

// ==================== 品牌主色 ====================

/** 主色调 - 科技蓝 */
val Primary = Color(0xFF1B6EF3)

/** 主色调（浅色变体） */
val PrimaryLight = Color(0xFF5A9CF6)

/** 主色调（深色变体） */
val PrimaryDark = Color(0xFF0050CB)

/** 主色调On版本（用于主色上的文字/图标） */
val OnPrimary = Color(0xFFFFFFFF)

/** 主色调容器色 */
val PrimaryContainer = Color(0xFFD7E3FF)

/** 主色调容器On版本 */
val OnPrimaryContainer = Color(0xFF001A41)

// ==================== 次要色 ====================

val Secondary = Color(0xFF565E71)
val SecondaryLight = Color(0xFF6E7787)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFDAE2F9)
val OnSecondaryContainer = Color(0xFF131C2B)

// ==================== 第三色 ====================

val Tertiary = Color(0xFF6F5D8E)
val TertiaryLight = Color(0xFF8774A8)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFEADDFF)
val OnTertiaryContainer = Color(0xFF271641)

// ==================== 错误/警告色 ====================

val Error = Color(0xFFBA1A1A)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF410002)

// ==================== 背景色 ====================

val Background = Color(0xFFFBFCFF)
val OnBackground = Color(0xFF1A1C1E)
val Surface = Color(0xFFFBFCFF)
val OnSurface = Color(0xFF1A1C1E)
val SurfaceVariant = Color(0xFFE0E2EC)
val OnSurfaceVariant = Color(0xFF43474E)

// ==================== 轮廓色 ====================

val Outline = Color(0xFF73777F)
val OutlineVariant = Color(0xFFC3C6CF)

// ==================== 流量专用色 ====================

/** 移动网络流量色 - 蓝色系 */
val TrafficBlue = Color(0xFF2196F3)

/** WiFi流量色 - 绿色系 */
val TrafficGreen = Color(0xFF4CAF50)

/** 流量警告色 - 橙色 */
val TrafficOrange = Color(0xFFFF9800)

/** 流量危险色 - 红色 */
val TrafficRed = Color(0xFFF44336)

/** 流量安全色 - 青色 */
val TrafficCyan = Color(0xFF00BCD4)

/** 上传速率色 - 紫色 */
val UploadPurple = Color(0xFF9C27B0)

// ==================== 状态指示色 ====================

/** Root权限色 - 绿色 */
val StatusRootGreen = Color(0xFF4CAF50)

/** Shizuku权限色 - 蓝色 */
val StatusShizukuBlue = Color(0xFF2196F3)

/** 无权限色 - 灰色 */
val StatusNoneGray = Color(0xFF9E9E9E)

/** 监控运行中 - 绿色 */
val MonitorRunning = Color(0xFF4CAF50)

/** 监控已停止 - 灰色 */
val MonitorStopped = Color(0xFF9E9E9E)

// ==================== 深色主题专用色 ====================

/** 深色主题主色 */
val DarkPrimary = Color(0xFFA9C7FF)

/** 深色主题主色On版本 */
val DarkOnPrimary = Color(0xFF00308E)

/** 深色主题主色容器 */
val DarkPrimaryContainer = Color(0xFF0046C6)

/** 深色主题主色容器On版本 */
val DarkOnPrimaryContainer = Color(0xFFD7E3FF)

/** 深色主题背景 */
val DarkBackground = Color(0xFF1A1C1E)

/** 深色主题On背景 */
val DarkOnBackground = Color(0xFFE3E2E6)

/** 深色主题表面 */
val DarkSurface = Color(0xFF1A1C1E)

/** 深色主题On表面 */
val DarkOnSurface = Color(0xFFE3E2E6)

/** 深色主题表面变体 */
val DarkSurfaceVariant = Color(0xFF43474E)

/** 深色主题On表面变体 */
val DarkOnSurfaceVariant = Color(0xFFC3C6CF)

/** 深色主题轮廓 */
val DarkOutline = Color(0xFF8D9199)

/** 深色主题轮廓变体 */
val DarkOutlineVariant = Color(0xFF43474E)
