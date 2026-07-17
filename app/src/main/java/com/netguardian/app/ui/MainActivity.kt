package com.netguardian.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.vectorResource
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.netguardian.app.R
import com.netguardian.app.ui.navigation.AppNavigation
import com.netguardian.app.ui.navigation.Routes
import com.netguardian.app.ui.theme.NetGuardianTheme

/**
 * NetGuardian 主Activity
 * 使用Jetpack Compose构建，采用Material3设计风格
 *
 * 主要职责：
 * 1. 设置Material3主题（支持深浅主题切换）
 * 2. 初始化底部导航栏（5个Tab）
 * 3. 使用NavController管理页面导航
 * 4. 请求必要权限（POST_NOTIFICATIONS等）
 * 5. 配置NavHost定义各页面路由
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    /** 导航控制器 */
    private lateinit var navController: androidx.navigation.NavController

    /** 权限请求启动器 - 通知权限 */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            android.util.Log.i(TAG, "通知权限已授予")
        } else {
            android.util.Log.w(TAG, "通知权限被拒绝")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 请求必要权限
        requestNecessaryPermissions()

        // 设置Compose内容
        setContent {
            // 深色主题状态（从SharedPreferences读取）
            val prefs = getSharedPreferences(
                com.netguardian.app.util.Constants.DATASTORE_NAME,
                MODE_PRIVATE
            )
            val isDarkTheme = remember {
                mutableStateOf(
                    prefs.getBoolean(com.netguardian.app.util.Constants.KEY_DARK_THEME, false)
                )
            }

            // 应用Material3主题
            NetGuardianTheme(
                darkTheme = isDarkTheme.value,
                dynamicColor = true
            ) {
                // 主内容，包含底部导航和NavHost
                MainContent(
                    isDarkTheme = isDarkTheme.value,
                    onDarkThemeChanged = { dark ->
                        isDarkTheme.value = dark
                        prefs.edit()
                            .putBoolean(com.netguardian.app.util.Constants.KEY_DARK_THEME, dark)
                            .apply()
                    }
                )
            }
        }
    }

    /**
     * 请求必要的运行时权限
     * 包括：通知权限（Android 13+）
     */
    private fun requestNecessaryPermissions() {
        // Android 13+ 需要请求通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

/**
 * 主内容Composable
 * 包含底部导航栏和NavHost页面导航
 *
 * @param isDarkTheme 当前是否使用深色主题
 * @param onDarkThemeChanged 主题切换回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContent(
    isDarkTheme: Boolean,
    onDarkThemeChanged: (Boolean) -> Unit = {}
) {
    val navController = rememberNavController()

    // 当前导航路由
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 底部导航项定义
    val bottomNavItems = listOf(
        BottomNavItem(
            route = Routes.DASHBOARD,
            label = "总览",
            iconResId = R.drawable.ic_speed
        ),
        BottomNavItem(
            route = Routes.APPS,
            label = "应用",
            iconResId = R.drawable.ic_apps
        ),
        BottomNavItem(
            route = Routes.CONTROL_MARKER,
            label = "控制",
            iconResId = R.drawable.ic_shield,
            enabled = false // 控制页由应用列表进入，不在底部导航直接显示
        ),
        BottomNavItem(
            route = Routes.STATISTICS,
            label = "统计",
            iconResId = R.drawable.ic_chart
        ),
        BottomNavItem(
            route = Routes.SETTINGS,
            label = "设置",
            iconResId = R.drawable.ic_settings
        )
    )

    // 判断是否显示底部导航栏（控制页不显示）
    val showBottomBar = currentRoute != null && !currentRoute.startsWith(Routes.APP_CONTROL_PREFIX)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(
                    items = bottomNavItems,
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        // 导航到目标页面，保留每个页面的回退栈
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        // 导航宿主
        AppNavigation(
            navController = navController
        )
    }
}

/**
 * 底部导航栏组件
 * Material3 NavigationBar样式，显示5个Tab
 *
 * @param items 导航项列表
 * @param currentRoute 当前路由
 * @param onNavigate 导航回调
 */
@Composable
private fun BottomNavigationBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        items.filter { it.enabled }.forEach { item ->
            val isSelected = currentRoute == item.route

            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(
                            id = item.iconResId
                        ),
                        contentDescription = item.label,
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                selected = isSelected,
                onClick = { onNavigate(item.route) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

/**
 * 底部导航项数据类
 *
 * @param route 路由路径
 * @param label 显示标签
 * @param iconResId 图标资源ID
 * @param enabled 是否启用
 */
private data class BottomNavItem(
    val route: String,
    val label: String,
    val iconResId: Int,
    val enabled: Boolean = true
)


