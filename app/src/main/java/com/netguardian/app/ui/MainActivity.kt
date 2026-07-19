package com.netguardian.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.netguardian.app.ui.navigation.AppNavigation
import com.netguardian.app.ui.navigation.Routes
import com.netguardian.app.ui.screen.PermissionSetupScreen
import com.netguardian.app.ui.theme.NetGuardianTheme
import com.netguardian.app.service.TrafficMonitorService
import com.netguardian.app.util.Constants
import com.netguardian.app.util.DataStoreHelper
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * NetGuardian 主 Activity
 * 使用 Jetpack Compose 构建，采用 Material3 设计风格
 *
 * 主要职责：
 * 1. 设置 Material3 主题（支持浅色/深色/跟随系统三种模式）
 * 2. 初始化底部导航栏（5 个 Tab：总览/应用/控制/统计/设置）
 * 3. 使用 NavController 管理页面导航
 * 4. 请求必要权限（POST_NOTIFICATIONS 等）
 * 5. 配置 NavHost 定义各页面路由
 * 6. 支持返回键导航
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

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

        // 设置 Compose 内容
        setContent {
            MainContent()
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
 * 主内容 Composable
 * 包含底部导航栏和 NavHost 页面导航
 *
 * 主题切换逻辑：
 * - "system"：跟随系统 (isSystemInDarkTheme())
 * - "light"：强制浅色
 * - "dark"：强制深色
 * 主题模式通过 DataStoreHelper 持久化
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navController = rememberNavController()

    // ==================== 首次启动权限引导 ====================
    var isFirstLaunch by remember { mutableStateOf(true) }
    var showPermissionSetup by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 从 DataStore 读取首次启动标志
        DataStoreHelper.isFirstLaunch(context).collect { first ->
            isFirstLaunch = first
            showPermissionSetup = first
        }
    }

    // 首次启动显示权限引导页
    if (showPermissionSetup) {
        PermissionSetupScreen(
            onAllGranted = {
                showPermissionSetup = false
                // 标记非首次启动
                kotlinx.coroutines.GlobalScope.launch {
                    DataStoreHelper.setFirstLaunch(context, false)
                }
            }
        )
        return
    }

    // ==================== 后台流量监控服务管理 ====================
    var monitorEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 从 DataStore 读取监控开关状态
        DataStoreHelper.getMonitorEnabled(context).collect { enabled ->
            monitorEnabled = enabled
        }
    }

    // 根据开关状态启停服务
    LaunchedEffect(monitorEnabled) {
        if (monitorEnabled) {
            val intent = Intent(context, TrafficMonitorService::class.java).apply {
                action = Constants.ACTION_START_MONITOR
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            val intent = Intent(context, TrafficMonitorService::class.java).apply {
                action = Constants.ACTION_STOP_MONITOR
            }
            context.startService(intent)
        }
    }

    // ==================== 主题状态管理 ====================
    var themeMode by remember { mutableStateOf("system") }

    // 从 DataStore 读取主题模式
    LaunchedEffect(Unit) {
        DataStoreHelper.getThemeModeFlow(context)
            .distinctUntilChanged()
            .collect { mode ->
                themeMode = mode
            }
    }

    // 根据主题模式计算是否深色
    val isDarkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    // ==================== 导航状态 ====================
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // ==================== 底部导航栏显示逻辑 ====================
    // 控制页不在底部导航显示，且控制页不显示底部导航栏
    val showBottomBar = currentRoute != null &&
            !currentRoute.startsWith(Routes.APP_CONTROL_PREFIX)

    // ==================== 返回键处理 ====================
    // 在非起始页时，返回键执行 popBackStack
    BackHandler(enabled = navController.previousBackStackEntry != null) {
        navController.popBackStack()
    }

    // ==================== 底部导航项定义 ====================
    val bottomNavItems = listOf(
        BottomNavItem(
            route = Routes.DASHBOARD,
            label = "总览",
            icon = Icons.Default.Dashboard
        ),
        BottomNavItem(
            route = Routes.APP_LIST,
            label = "应用",
            icon = Icons.Outlined.Apps
        ),
        BottomNavItem(
            route = Routes.APP_CONTROL_PREFIX,
            label = "控制",
            icon = Icons.Default.Shield,
            enabled = false // 控制页由应用列表进入，不在底部导航直接显示
        ),
        BottomNavItem(
            route = Routes.STATISTICS,
            label = "统计",
            icon = Icons.Default.BarChart
        ),
        BottomNavItem(
            route = Routes.SETTINGS,
            label = "设置",
            icon = Icons.Default.Settings
        )
    )

    // ==================== 应用主题 ====================
    NetGuardianTheme(
        darkTheme = isDarkTheme,
        dynamicColor = true
    ) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    BottomNavigationBar(
                        items = bottomNavItems,
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            if (route == Routes.APP_CONTROL_PREFIX) return@BottomNavigationBar
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
        ) { _ ->
            AppNavigation(
                navController = navController
            )
        }
    }
}

/**
 * 底部导航栏组件
 * Material3 NavigationBar 样式，显示 5 个 Tab
 *
 * 特性：
 * - 选中态使用 primary 色（图标和文字）
 * - 深色模式下导航栏颜色自动适配（通过 MaterialTheme.colorScheme.surface）
 * - 选中时底部有 primaryContainer 色指示器
 * - "控制" Tab 默认禁用（enabled = false），由应用列表页进入
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
                        imageVector = item.icon,
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
 * @param label 显示标签（中文）
 * @param icon Material Icons 图标
 * @param enabled 是否启用（"控制" Tab 设为 false）
 */
private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true
)