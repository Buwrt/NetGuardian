package com.netguardian.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * NetGuardian 专业级排版系统
 * 基于 Material3 Typography 规范，针对流量监控场景深度优化。
 *
 * 排版层级：
 * - Display: 用于仪表盘中心的大字速率显示（32sp/28sp/24sp）
 * - Headline: 用于页面标题、卡片标题（18sp/16sp）
 * - Body: 用于正文内容（14sp）
 * - Label: 用于标签、标注文字（12sp）
 * - SpeedDisplay: 网速数值专用样式（42sp, bold, monospace）
 */

/**
 * 应用全局 Typography 配置
 * 使用系统默认字体族，中文界面自动适配系统字体
 */
val Typography = Typography(
    // ==================== Display 层级 - 用于仪表盘大字 ====================

    /** Display Large - 仪表盘超大速率数值（如网速仪表盘中心） */
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-1).sp
    ),

    /** Display Medium - 大尺寸速率数值 */
    displayMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),

    /** Display Small - 中等速率数值 */
    displaySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),

    // ==================== Headline 层级 - 页面/卡片标题 ====================

    /** Headline Large - 页面主标题 */
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),

    /** Headline Medium - 卡片标题、section 标题 */
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp
    ),

    /** Headline Small - 列表项标题 */
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // ==================== Title 层级 ====================

    /** Title Large - 弹出对话框标题、大区块标题 */
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),

    /** Title Medium - Tab 标题、Section 标题 */
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp
    ),

    /** Title Small - 小区块标题 */
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp
    ),

    // ==================== Body 层级 - 正文内容 ====================

    /** Body Large - 重要正文、描述段落 */
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.25.sp
    ),

    /** Body Medium - 列表项描述、卡片内容（主力正文样式） */
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),

    /** Body Small - 辅助信息、次要说明 */
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),

    // ==================== Label 层级 - 标签与标注 ====================

    /** Label Large - 按钮文字、重要标签 */
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp
    ),

    /** Label Medium - Tab 文字、Chip 文字、状态标签 */
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),

    /** Label Small - Badge、时间戳、极小标注文字 */
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)

// ==================== 网速数值专用样式 ====================

/**
 * 网速数值专用样式 - 42sp 大字、粗体、等宽字体
 * 专用于仪表盘中心、实时速率面板等核心数据展示区域。
 * 等宽字体确保数字变化时宽度一致，避免 UI 抖动。
 *
 * 使用方式：
 * ```
 * Text(
 *     text = "12.5",
 *     style = TextStyle.SpeedDisplay,
 *     color = MaterialTheme.colorScheme.primary
 * )
 * ```
 */
val TextStyle.Companion.SpeedDisplay: TextStyle
    get() = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        lineHeight = 48.sp,
        letterSpacing = (-1.5).sp
    )

/**
 * 网速数值中等样式 - 28sp，用于次级速率显示
 */
val TextStyle.Companion.SpeedDisplayMedium: TextStyle
    get() = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    )

/**
 * 网速数值小号样式 - 20sp，用于卡片内速率数字
 */
val TextStyle.Companion.SpeedDisplaySmall: TextStyle
    get() = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    )

// ==================== 流量数值扩展样式 ====================

/**
 * 流量数值样式 - 用于流量大小展示（如 1.2 GB）
 */
val TextStyle.Companion.TrafficValue: TextStyle
    get() = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    )

/**
 * 流量单位样式 - 用于速率/流量单位标注（如 KB/s、MB、GB）
 */
val TextStyle.Companion.TrafficUnit: TextStyle
    get() = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )

/**
 * 仪表盘标签样式 - 用于仪表盘下方/上方的文字标注
 */
val TextStyle.Companion.MeterLabel: TextStyle
    get() = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )

/**
 * 状态指示文字样式 - 用于状态栏/悬浮提示中的运行状态文字
 */
val TextStyle.Companion.StatusLabel: TextStyle
    get() = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )

/**
 * 数据统计卡片数值样式 - 用于统计卡片中的数字
 */
val TextStyle.Companion.StatValue: TextStyle
    get() = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp
    )

/**
 * 数据统计卡片标签样式 - 用于统计卡片数值下方的标签
 */
val TextStyle.Companion.StatLabel: TextStyle
    get() = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp
    )
