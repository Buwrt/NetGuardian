package com.netguardian.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.netguardian.app.ui.screen.*

/**
 * NetGuardian 应用导航图
 * 定义所有页面路由和对应的Composable页面
 * 使用NavController进行导航管理
 *
 * 路由定义：
 * - "dashboard" -> 总览页面
 * - "apps" -> 应用流量列表页面
 * - "control/{packageName}" -> 应用控制页面（带包名参数）
 * - "statistics" -> 统计页面
 * - "settings" -> 设置页面
 */

/** 路由常量 */
object Routes {
    /** 总览页路由 */
    const val DASHBOARD = "dashboard"

    /** 应用列表页路由 */
    const val APPS = "apps"

    /** 应用控制页路由模板（需替换{packageName}） */
    const val APP_CONTROL = "control/{packageName}"

    /** 控制页路由前缀（用于底部导航栏判断是否隐藏） */
    const val APP_CONTROL_PREFIX = "control/"

    /** 统计页路由 */
    const val STATISTICS = "statistics"

    /** 设置页路由 */
    const val SETTINGS = "settings"
}

/** 动画持续时间 */
private const val ANIM_DURATION_MS = 300

/**
 * 应用导航宿主组件
 * 使用NavHost定义所有导航目标，包含页面切换动画
 *
 * @param navController 导航控制器
 */
@Composable
fun AppNavigation(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
        enterTransition = {
            // 从右侧滑入 + 淡入
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(ANIM_DURATION_MS)
            ) + fadeIn(animationSpec = tween(ANIM_DURATION_MS))
        },
        exitTransition = {
            // 向左侧滑出 + 淡出
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(ANIM_DURATION_MS)
            ) + fadeOut(animationSpec = tween(ANIM_DURATION_MS))
        },
        popEnterTransition = {
            // 从左侧滑入（返回）
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(ANIM_DURATION_MS)
            ) + fadeIn(animationSpec = tween(ANIM_DURATION_MS))
        },
        popExitTransition = {
            // 向右侧滑出（返回）
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(ANIM_DURATION_MS)
            ) + fadeOut(animationSpec = tween(ANIM_DURATION_MS))
        }
    ) {
        // ==================== 总览页 ====================
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToApps = {
                    navController.navigate(Routes.APPS)
                }
            )
        }

        // ==================== 应用列表页 ====================
        composable(Routes.APPS) {
            AppListScreen(
                onAppClick = { packageName ->
                    navController.navigate("control/$packageName")
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // ==================== 应用控制页（带参数） ====================
        composable(
            route = Routes.APP_CONTROL,
            arguments = listOf(
                navArgument("packageName") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
            AppControlScreen(
                packageName = packageName,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // ==================== 统计页 ====================
        composable(Routes.STATISTICS) {
            StatisticsScreen()
        }

        // ==================== 设置页 ====================
        composable(Routes.SETTINGS) {
            SettingsScreen()
        }
    }
}
