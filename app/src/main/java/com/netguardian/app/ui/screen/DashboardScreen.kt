package com.netguardian.app.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.netguardian.app.ui.component.SpeedMeterWidget
import com.netguardian.app.ui.theme.*
import com.netguardian.app.util.Constants
import com.netguardian.app.util.TrafficFormatter
import com.netguardian.app.viewmodel.DashboardViewModel

/**
 * 总览页面 - Dashboard Screen
 * 显示实时网速仪表盘、今日流量汇总、权限模式和监控服务状态
 * 支持下拉刷新和实时数据动画
 *
 * @param viewModel DashboardViewModel实例
 * @param onNavigateToApps 点击"查看全部应用"时的回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    onNavigateToApps: () -> Unit = {}
) {
    // 收集实时数据
    val networkSpeed by viewModel.networkSpeed.collectAsStateWithLifecycle()
    val todayMobile by viewModel.todayMobileTraffic.collectAsStateWithLifecycle()
    val todayWifi by viewModel.todayWifiTraffic.collectAsStateWithLifecycle()
    val todayTotal by viewModel.todayTotalTraffic.collectAsStateWithLifecycle()
    val privilegeMode by viewModel.privilegeMode.collectAsStateWithLifecycle()
    val isMonitorRunning by viewModel.isMonitorRunning.collectAsStateWithLifecycle()
    val networkType by viewModel.networkType.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    // 刷新状态
    var isRefreshingState by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Scaffold结构
    Scaffold(
        topBar = {
            DashboardTopBar(
                networkType = networkType,
                onRefresh = {
                    isRefreshingState = true
                    viewModel.refreshAll()
                    scope.launch {
                        kotlinx.coroutines.delay(1000)
                        isRefreshingState = false
                    }
                },
                isRefreshing = isRefreshingState
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                // 初始加载状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                // 主内容 - 使用垂直滚动
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. 实时网速仪表盘卡片
                    SpeedMeterCard(
                        downloadSpeed = networkSpeed.downloadSpeed,
                        uploadSpeed = networkSpeed.uploadSpeed
                    )

                    // 2. 今日流量汇总卡片
                    TodayTrafficCard(
                        mobileTraffic = todayMobile,
                        wifiTraffic = todayWifi,
                        totalTraffic = todayTotal
                    )

                    // 3. 权限模式状态卡片
                    PrivilegeModeCard(mode = privilegeMode)

                    // 4. 监控服务控制卡片
                    MonitorControlCard(
                        isRunning = isMonitorRunning,
                        onToggle = { running ->
                            viewModel.setMonitorRunning(running)
                        }
                    )

                    // 底部留白
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }

            // 错误提示
            errorMessage?.let { error ->
                LaunchedEffect(error) {
                    kotlinx.coroutines.delay(3000)
                    viewModel.clearError()
                }
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(error)
                }
            }
        }
    }
}

/**
 * 顶部标题栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar(
    networkType: String,
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "NetGuardian",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "流量监控总览",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            // 刷新按钮
            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "刷新",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 网络类型标签
            Surface(
                modifier = Modifier.padding(end = 8.dp),
                shape = MaterialTheme.shapes.small,
                color = when (networkType) {
                    Constants.NETWORK_TYPE_WIFI -> TrafficBlue.copy(alpha = 0.15f)
                    Constants.NETWORK_TYPE_MOBILE -> TrafficGreen.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Text(
                    text = when (networkType) {
                        Constants.NETWORK_TYPE_WIFI -> "WiFi"
                        Constants.NETWORK_TYPE_MOBILE -> "移动数据"
                        else -> "无网络"
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (networkType) {
                        Constants.NETWORK_TYPE_WIFI -> TrafficBlue
                        Constants.NETWORK_TYPE_MOBILE -> TrafficGreen
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

/**
 * 实时网速仪表盘卡片
 */
@Composable
private fun SpeedMeterCard(
    downloadSpeed: Long,
    uploadSpeed: Long
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 卡片标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "实时网速",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "实时网速",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 仪表盘组件
            SpeedMeterWidget(
                downloadSpeed = downloadSpeed,
                uploadSpeed = uploadSpeed,
                modifier = Modifier.size(200.dp)
            )
        }
    }
}

/**
 * 今日流量汇总卡片
 * 显示移动网络和WiFi的流量使用情况，带进度条
 */
@Composable
private fun TodayTrafficCard(
    mobileTraffic: Long,
    wifiTraffic: Long,
    totalTraffic: Long
) {
    // 假设日流量限额为1GB用于进度条显示
    val dailyLimit = 1024L * 1024L * 1024L // 1GB
    val mobileProgress by animateFloatAsState(
        targetValue = (mobileTraffic.toFloat() / dailyLimit).coerceIn(0f, 1f),
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "mobileProgress"
    )
    val wifiProgress by animateFloatAsState(
        targetValue = (wifiTraffic.toFloat() / dailyLimit).coerceIn(0f, 1f),
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "wifiProgress"
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 卡片标题
            Text(
                text = "今日流量",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 总流量
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "总流量",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = TrafficFormatter.formatBytes(totalTraffic),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 移动网络流量
            TrafficProgressRow(
                label = "移动网络",
                traffic = mobileTraffic,
                progress = mobileProgress,
                color = TrafficBlue
            )

            Spacer(modifier = Modifier.height(12.dp))

            // WiFi流量
            TrafficProgressRow(
                label = "WiFi",
                traffic = wifiTraffic,
                progress = wifiProgress,
                color = TrafficGreen
            )
        }
    }
}

/**
 * 流量进度行组件
 * 显示标签、流量数值和进度条
 */
@Composable
private fun TrafficProgressRow(
    label: String,
    traffic: Long,
    progress: Float,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 左侧标签
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )

        // 中间进度条
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .weight(1f)
                .height(8.dp),
            color = color,
            trackColor = color.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 右侧数值
        Text(
            text = TrafficFormatter.formatBytes(traffic),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.width(80.dp)
        )
    }
}

/**
 * 权限模式状态卡片
 * 显示当前权限模式（Root/Shizuku/无权限）及其状态
 */
@Composable
private fun PrivilegeModeCard(mode: String) {
    // 根据模式确定颜色和图标
    val (iconText, statusText, statusColor) = when (mode) {
        Constants.MODE_ROOT -> Triple("Root", "Root权限已获取", StatusRootGreen)
        Constants.MODE_SHIZUKU -> Triple("S", "Shizuku权限已获取", StatusShizukuBlue)
        else -> Triple("N", "无权限（仅监控模式）", StatusNoneGray)
    }

    // 状态色动画
    val animatedColor by animateColorAsState(
        targetValue = statusColor,
        animationSpec = tween(300),
        label = "statusColor"
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 权限模式图标
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = animatedColor.copy(alpha = 0.15f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp)
                ) {
                    Text(
                        text = iconText,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = animatedColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 状态文字
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "权限模式",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = animatedColor
                )
            }

            // 状态指示点
            Surface(
                modifier = Modifier.size(12.dp),
                shape = MaterialTheme.shapes.extraSmall,
                color = animatedColor
            ) {}
        }
    }
}

/**
 * 监控服务控制卡片
 * 包含一个大Toggle按钮来控制监控服务的启停
 */
@Composable
private fun MonitorControlCard(
    isRunning: Boolean,
    onToggle: (Boolean) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 监控图标
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (isRunning) MonitorRunning.copy(alpha = 0.15f)
                else MonitorStopped.copy(alpha = 0.15f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "监控服务",
                        tint = if (isRunning) MonitorRunning else MonitorStopped,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "流量监控服务",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (isRunning) "监控中..." else "已停止",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isRunning) MonitorRunning else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 开关按钮
            Switch(
                checked = isRunning,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MonitorRunning,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}
