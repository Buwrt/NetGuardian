package com.netguardian.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.netguardian.app.ui.screen.*

/**
 * NetGuardian 应用导航图
 * 定义所有页面路由和对应的 Composable 页面
 * 使用 NavController 进行导航管理
 *
 * 路由定义：
 * - "dashboard"   -> DashboardScreen  总览页面
 * - "app_list"    -> AppListScreen     应用流量列表页面
 * - "app_control/{packageName}" -> AppControlScreen  应用控制页面（带包名参数）
 * - "statistics"  -> StatisticsScreen  统计页面
 * - "settings"    -> SettingsScreen    设置页面
 */

/** 路由常量 */
object Routes {
    /** 总览页路由 */
    const val DASHBOARD = "dashboard"

    /** 应用列表页路由 */
    const val APP_LIST = "app_list"

    /** 应用控制页路由模板（需替换 {packageName}） */
    const val APP_CONTROL = "app_control/{packageName}"

    /** 控制页路由前缀（用于底部导航栏判断是否隐藏） */
    const val APP_CONTROL_PREFIX = "app_control/"

    /** 统计页路由 */
    const val STATISTICS = "statistics"

    /** 设置页路由 */
    const val SETTINGS = "settings"
}

/** 页面切换动画时长（毫秒） */
private const val ANIM_DURATION_MS = 300

/** 页面切换滑入/滑出偏移量（像素） */
private const val ANIM_SLIDE_OFFSET = 300

/**
 * 应用导航宿主组件
 * 使用 NavHost 定义所有导航目标，包含页面切换动画
 *
 * 动画规格：
 * - 进入：slideInHorizontally + fadeIn（从右向左滑入）
 * - 退出：slideOutHorizontally + fadeOut（向右滑出）
 * - 返回进入：从左向右滑入 + 淡入
 * - 返回退出：向左滑出 + 淡出
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
            // 前进：从右侧滑入 + 淡入
            slideInHorizontally(
                initialOffsetX = { ANIM_SLIDE_OFFSET },
                animationSpec = tween(ANIM_DURATION_MS)
            ) + fadeIn(
                animationSpec = tween(ANIM_DURATION_MS)
            )
        },
        exitTransition = {
            // 前进：向左侧滑出 + 淡出
            slideOutHorizontally(
                targetOffsetX = { -ANIM_SLIDE_OFFSET / 3 },
                animationSpec = tween(ANIM_DURATION_MS)
            ) + fadeOut(
                animationSpec = tween(ANIM_DURATION_MS)
            )
        },
        popEnterTransition = {
            // 返回：从左侧滑入 + 淡入
            slideInHorizontally(
                initialOffsetX = { -ANIM_SLIDE_OFFSET / 3 },
                animationSpec = tween(ANIM_DURATION_MS)
            ) + fadeIn(
                animationSpec = tween(ANIM_DURATION_MS)
            )
        },
        popExitTransition = {
            // 返回：向右侧滑出 + 淡出
            slideOutHorizontally(
                targetOffsetX = { ANIM_SLIDE_OFFSET },
                animationSpec = tween(ANIM_DURATION_MS)
            ) + fadeOut(
                animationSpec = tween(ANIM_DURATION_MS)
            )
        }
    ) {
        // ==================== 总览页 ====================
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToApps = {
                    navController.navigate(Routes.APP_LIST)
                }
            )
        }

        // ==================== 应用列表页 ====================
        composable(Routes.APP_LIST) {
            AppListScreen(
                onAppClick = { packageName ->
                    navController.navigate("app_control/$packageName")
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