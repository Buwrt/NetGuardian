package com.netguardian.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * NetGuardian 专业级颜色系统
 * 遵循 Material3 设计规范，包含浅色/深色双主题完整色板
 * 浅色主题采用清新现代风格，深色主题采用柔和护眼风格
 */

// ==================== 浅色主题 - 品牌主色 ====================

/** 主色调 - iOS 系统蓝，专业感 */
val Primary = Color(0xFF0A84FF)

/** 主色调 On 版本（主色上的文字/图标色） */
val OnPrimary = Color(0xFFFFFFFF)

/** 主色调容器色 */
val PrimaryContainer = Color(0xFFD4E3FF)

/** 主色调容器 On 版本 */
val OnPrimaryContainer = Color(0xFF001B3E)

// ==================== 浅色主题 - 次要色 ====================

val Secondary = Color(0xFF5C5D72)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFE0E0F1)
val OnSecondaryContainer = Color(0xFF191A2C)

// ==================== 浅色主题 - 第三色 ====================

val Tertiary = Color(0xFF775674)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFFFD7F6)
val OnTertiaryContainer = Color(0xFF2E152C)

// ==================== 浅色主题 - 错误色 ====================

val Error = Color(0xFFFF3B30)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF410002)

// ==================== 浅色主题 - 背景与表面色 ====================

val Background = Color(0xFFFBFCFF)
val OnBackground = Color(0xFF1B1B1E)

val Surface = Color(0xFFFBFCFF)
val OnSurface = Color(0xFF1B1B1E)

val SurfaceVariant = Color(0xFFE2E1EC)
val OnSurfaceVariant = Color(0xFF45454F)

val SurfaceDim = Color(0xFFDBD9E0)
val SurfaceBright = Color(0xFFFBFCFF)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF5F3FA)
val SurfaceContainer = Color(0xFFEFEDF4)
val SurfaceContainerHigh = Color(0xFFEAE7EE)
val SurfaceContainerHighest = Color(0xFFE4E2E9)

// ==================== 浅色主题 - 轮廓色 ====================

val Outline = Color(0xFF74747E)
val OutlineVariant = Color(0xFFC4C3D0)

// ==================== 浅色主题 - 反向色 ====================

val InversePrimary = Color(0xFF78A8FF)
val InverseSurface = Color(0xFF303034)
val InverseOnSurface = Color(0xFFF3F0F7)

// ==================== 深色主题 - 品牌主色 ====================

/** 深色主题主色调 - 柔和蓝 */
val DarkPrimary = Color(0xFFA8C8FF)

/** 深色主题主色 On 版本 */
val DarkOnPrimary = Color(0xFF003164)

/** 深色主题主色容器 */
val DarkPrimaryContainer = Color(0xFF004C8C)

/** 深色主题主色容器 On 版本 */
val DarkOnPrimaryContainer = Color(0xFFD4E3FF)

// ==================== 深色主题 - 次要色 ====================

val DarkSecondary = Color(0xFFC4C3DC)
val DarkOnSecondary = Color(0xFF2F2F42)
val DarkSecondaryContainer = Color(0xFF464659)
val DarkOnSecondaryContainer = Color(0xFFE0E0F1)

// ==================== 深色主题 - 第三色 ====================

val DarkTertiary = Color(0xFFE3B7DE)
val DarkOnTertiary = Color(0xFF472645)
val DarkTertiaryContainer = Color(0xFF5E3C5C)
val DarkOnTertiaryContainer = Color(0xFFFFD7F6)

// ==================== 深色主题 - 错误色 ====================

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

// ==================== 深色主题 - 背景与表面色 ====================

val DarkBackground = Color(0xFF1B1B1E)
val DarkOnBackground = Color(0xFFE3E2E8)

val DarkSurface = Color(0xFF1B1B1E)
val DarkOnSurface = Color(0xFFE3E2E8)

val DarkSurfaceVariant = Color(0xFF45454F)
val DarkOnSurfaceVariant = Color(0xFFC4C3D0)

val DarkSurfaceDim = Color(0xFF1B1B1E)
val DarkSurfaceBright = Color(0xFF424246)
val DarkSurfaceContainerLowest = Color(0xFF121216)
val DarkSurfaceContainerLow = Color(0xFF232327)
val DarkSurfaceContainer = Color(0xFF27272B)
val DarkSurfaceContainerHigh = Color(0xFF323236)
val DarkSurfaceContainerHighest = Color(0xFF3D3D41)

// ==================== 深色主题 - 轮廓色 ====================

val DarkOutline = Color(0xFF94949F)
val DarkOutlineVariant = Color(0xFF45454F)

// ==================== 深色主题 - 反向色 ====================

val DarkInversePrimary = Color(0xFF0064D6)
val DarkInverseSurface = Color(0xFFE3E2E8)
val DarkInverseOnSurface = Color(0xFF303034)

// ==================== 流量专用色（双主题共用） ====================

/** 移动网络流量色 - 绿色 */
val TrafficGreen = Color(0xFF34C759)

/** WiFi 流量色 - 蓝色 */
val TrafficBlue = Color(0xFF0A84FF)

/** 超限警告色 - 红色 */
val TrafficRed = Color(0xFFFF3B30)

/** 流量橙色 - 中等警告 */
val TrafficOrange = Color(0xFFFF9500)

/** 流量青色 - 辅助色 */
val TrafficCyan = Color(0xFF5AC8FA)

// ==================== 运营商品牌色 ====================

/** 中国移动 - 品牌红 */
val CarrierCMCC = Color(0xFFE60012)

/** 中国联通 - 品牌橙 */
val CarrierCUCC = Color(0xFFFF6600)

/** 中国电信 - 品牌蓝 */
val CarrierCTCC = Color(0xFF189CFF)

// ==================== 状态指示色（双主题共用） ====================

/** Root 权限 - 绿色 */
val StatusRootGreen = Color(0xFF34C759)

/** Shizuku 权限 - 蓝色 */
val StatusShizukuBlue = Color(0xFF0A84FF)

/** 无权限 - 灰色 */
val StatusNoneGray = Color(0xFF8E8E93)

/** 监控运行中 - 绿色 */
val MonitorRunning = Color(0xFF34C759)

/** 监控已停止 - 灰色 */
val MonitorStopped = Color(0xFF8E8E93)

// ==================== 功能性语义色 ====================

/** 上传速率色 - 紫色 */
val UploadPurple = Color(0xFFAF52DE)

/** 下载速率色 - 蓝绿色 */
val DownloadTeal = Color(0xFF5AC8FA)

/** 安全/通过 - 绿色 */
val SafeGreen = Color(0xFF34C759)

/** 危险/阻断 - 红色 */
val DangerRed = Color(0xFFFF3B30)

/** 提示/信息 - 蓝色 */
val InfoBlue = Color(0xFF0A84FF)

/** 警告 - 橙色 */
val WarningOrange = Color(0xFFFF9500)
