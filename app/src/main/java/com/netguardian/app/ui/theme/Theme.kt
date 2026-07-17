package com.netguardian.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * NetGuardian 专业级主题系统
 * 基于 Material3 设计规范，支持：
 * - 浅色/深色双主题完整切换
 * - Android 12+ 动态颜色（Dynamic Color）支持，带自定义 fallback
 * - Material3 组件样式覆盖（卡片、按钮、开关等）
 * - 沉浸式状态栏 & 导航栏
 */

// ==================== 主题扩展属性 ====================

/**
 * 扩展 ColorScheme，提供更丰富的语义化颜色访问
 */
val ColorScheme.cardBackground: Color
    get() = surfaceContainerLow

val ColorScheme.cardBorder: Color
    get() = outlineVariant.copy(alpha = 0.3f)

val ColorScheme.divider: Color
    get() = outlineVariant.copy(alpha = 0.5f)

// ==================== 浅色主题配色方案 ====================

/**
 * 浅色主题完整 ColorScheme
 * 使用 iOS 蓝 #0A84FF 作为主色调，清新现代风格
 */
private val LightColorScheme = lightColorScheme(
    // 主色
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    // 次要色
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    // 第三色
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    // 错误色
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    // 背景与表面
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceDim = SurfaceDim,
    surfaceBright = SurfaceBright,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    // 轮廓色
    outline = Outline,
    outlineVariant = OutlineVariant,
    // 反向色
    inversePrimary = InversePrimary,
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,
)

// ==================== 深色主题配色方案 ====================

/**
 * 深色主题完整 ColorScheme
 * 柔和护眼风格，表面色采用 #1B1B1E 低对比度深灰
 */
private val DarkColorScheme = darkColorScheme(
    // 主色
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    // 次要色
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    // 第三色
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    // 错误色
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    // 背景与表面
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    // 轮廓色
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    // 反向色
    inversePrimary = DarkInversePrimary,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
)

// ==================== 组件形状系统 ====================

/**
 * NetGuardian 组件圆角定义
 */
object NetGuardianShapes {
    /** 卡片圆角 - 12dp */
    val CardShape = RoundedCornerShape(12.dp)

    /** 大卡片圆角 - 16dp */
    val CardLarge = RoundedCornerShape(16.dp)

    /** 小卡片圆角 - 8dp */
    val CardSmall = RoundedCornerShape(8.dp)

    /** 按钮圆角 */
    val ButtonShape = RoundedCornerShape(20.dp)

    /** FAB 圆角 */
    val FabShape = RoundedCornerShape(16.dp)

    /** 对话框圆角 - 28dp */
    val DialogShape = RoundedCornerShape(28.dp)

    /** 底部面板圆角 - 28dp */
    val BottomSheetShape = RoundedCornerShape(28.dp)

    /** 芯片/标签圆角 */
    val ChipShape = RoundedCornerShape(8.dp)
}

// ==================== 主题入口 ====================

/**
 * NetGuardian 全局主题入口 Composable
 *
 * 包裹在应用最外层，提供完整的 Material3 主题体系。
 * 支持动态颜色（Android 12+），当动态颜色不可用时回退到
 * 自定义的专业级配色方案。
 *
 * @param darkTheme 是否启用深色主题，默认跟随系统设置
 * @param dynamicColor 是否启用 Android 12+ 动态颜色，默认 true。
 *                     仅在 Android 12+ 设备上生效，低版本自动回退到自定义配色
 * @param content 主题包裹的页面内容
 */
@Composable
fun NetGuardianTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    // 根据设备能力和用户偏好选择配色方案
    val colorScheme = when {
        // Android 12+ 动态颜色，仅在用户启用时生效
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }
        // 非 Android 12+ 或用户禁用动态颜色，使用自定义配色
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 沉浸式状态栏与导航栏适配
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 状态栏透明，实现沉浸式效果
            window.statusBarColor = Color.Transparent.toArgb()
            // 导航栏透明
            window.navigationBarColor = Color.Transparent.toArgb()
            // 根据主题设置状态栏图标深浅色
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    // 应用 Material3 主题
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}
