package com.netguardian.app.ui.screen

import android.view.ViewOutlineProvider
import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.netguardian.app.R
import com.netguardian.app.core.CarrierInfo
import com.netguardian.app.core.CarrierManager
import com.netguardian.app.core.TrafficMonitorEngine
import com.netguardian.app.model.AppTrafficInfo
import com.netguardian.app.ui.component.SpeedMeterWidget
import com.netguardian.app.ui.theme.CarrierCMCC
import com.netguardian.app.ui.theme.CarrierCTCC
import com.netguardian.app.ui.theme.CarrierCUCC
import com.netguardian.app.ui.theme.MonitorRunning
import com.netguardian.app.ui.theme.MonitorStopped
import com.netguardian.app.ui.theme.StatValueTextStyle
import com.netguardian.app.ui.theme.StatusNoneGray
import com.netguardian.app.ui.theme.TrafficValueTextStyle
import com.netguardian.app.ui.theme.StatusRootGreen
import com.netguardian.app.ui.theme.StatusShizukuBlue
import com.netguardian.app.ui.theme.TrafficBlue
import com.netguardian.app.ui.theme.TrafficGreen
import com.netguardian.app.ui.theme.TrafficOrange
import com.netguardian.app.ui.theme.TrafficRed
import com.netguardian.app.util.Constants
import com.netguardian.app.util.DataStoreHelper
import com.netguardian.app.util.TrafficFormatter
import com.netguardian.app.viewmodel.DashboardViewModel

/**
 * 总览页面 - Dashboard Screen
 *
 * 流量管家核心页面，展示以下区域：
 * 1. 运营商与网络状态卡片（支持双SIM卡）
 * 2. 实时网速仪表盘（Canvas 圆弧 + 动画）
 * 3. 今日流量统计（移动/WiFi 并排卡片，含进度条）
 * 4. 应用流量 TOP5 排行（含百分比条）
 * 5. 权限模式状态 + 监控服务开关
 *
 * 所有区域带级联入场动画，深色模式完美适配。
 *
 * @param viewModel DashboardViewModel 实例，提供实时网速、流量汇总、权限模式等数据
 * @param onNavigateToApps 点击"查看全部"时的导航回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    onNavigateToApps: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ==================== ViewModel 状态收集 ====================
    val networkSpeed by viewModel.networkSpeed.collectAsStateWithLifecycle()
    val todayMobile by viewModel.todayMobileTraffic.collectAsStateWithLifecycle()
    val todayWifi by viewModel.todayWifiTraffic.collectAsStateWithLifecycle()
    val privilegeMode by viewModel.privilegeMode.collectAsStateWithLifecycle()
    val isMonitorRunning by viewModel.isMonitorRunning.collectAsStateWithLifecycle()
    val networkType by viewModel.networkType.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    // ==================== 本地 UI 状态 ====================
    var isRefreshing by remember { mutableStateOf(false) }
    // 刷新计数器，递增时触发 LaunchedEffect 重新加载数据
    var refreshKey by remember { mutableStateOf(0) }

    // ==================== 运营商信息（直接通过 CarrierManager 获取） ====================
    val carrierManager = remember { CarrierManager(context) }
    var carrierInfos by remember { mutableStateOf<List<CarrierInfo>>(emptyList()) }

    // ==================== 今日流量 TOP5 应用 ====================
    var topApps by remember { mutableStateOf<List<AppTrafficInfo>>(emptyList()) }

    // ==================== 数据加载 ====================
    LaunchedEffect(refreshKey) {
        withContext(Dispatchers.IO) {
            // 加载运营商信息（支持双卡）
            try {
                val infos = carrierManager.getAllCarrierInfos()
                carrierInfos = if (infos.isEmpty()) {
                    carrierManager.detectCarrier()?.let { listOf(it) } ?: emptyList()
                } else {
                    infos
                }
            } catch (_: Exception) {
                // 无权限或异常时静默处理
            }

            // 加载 TOP5 应用流量数据
            try {
                val engine = TrafficMonitorEngine(context)
                topApps = engine.getPerAppTraffic()
                    .sortedByDescending { it.totalBytes }
                    .take(5)
            } catch (_: Exception) {
                // 加载失败时静默处理
            }
        }
    }

    // ==================== 从 DataStore 同步监控开关初始状态到 ViewModel ====================
    LaunchedEffect(Unit) {
        DataStoreHelper.getMonitorEnabled(context).collect { enabled ->
            viewModel.setMonitorRunning(enabled)
        }
    }

    // ==================== 刷新操作 ====================
    val onRefresh: () -> Unit = {
        isRefreshing = true
        refreshKey++
        viewModel.refreshAll()
        scope.launch {
            delay(1200)
            isRefreshing = false
        }
    }

    // ==================== 主布局 ====================
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "\u6D41\u91CF\u7BA1\u5BB6",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // 右侧刷新按钮
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                // 初始加载状态 - 居中加载指示器
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
                // 主内容 - 垂直滚动，各区域带级联入场动画
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ==================== 区域1: 运营商与网络状态 ====================
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(400)) + slideInVertically(
                            initialOffsetY = { it / 4 },
                            animationSpec = tween(400, easing = FastOutSlowInEasing)
                        )
                    ) {
                        CarrierNetworkCard(
                            carrierInfos = carrierInfos,
                            networkType = networkType
                        )
                    }

                    // ==================== 区域2: 实时网速仪表盘 ====================
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(400, delayMillis = 60)) + slideInVertically(
                            initialOffsetY = { it / 4 },
                            animationSpec = tween(
                                400,
                                delayMillis = 60,
                                easing = FastOutSlowInEasing
                            )
                        )
                    ) {
                        SpeedMeterSection(
                            downloadSpeed = networkSpeed.downloadSpeed,
                            uploadSpeed = networkSpeed.uploadSpeed
                        )
                    }

                    // ==================== 区域3: 今日流量统计 ====================
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(400, delayMillis = 120)) + slideInVertically(
                            initialOffsetY = { it / 4 },
                            animationSpec = tween(
                                400,
                                delayMillis = 120,
                                easing = FastOutSlowInEasing
                            )
                        )
                    ) {
                        TodayTrafficSection(
                            mobileTraffic = todayMobile,
                            wifiTraffic = todayWifi
                        )
                    }

                    // ==================== 区域4: 应用流量 TOP5 ====================
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(400, delayMillis = 180)) + slideInVertically(
                            initialOffsetY = { it / 4 },
                            animationSpec = tween(
                                400,
                                delayMillis = 180,
                                easing = FastOutSlowInEasing
                            )
                        )
                    ) {
                        TopAppsSection(
                            topApps = topApps,
                            onViewAll = onNavigateToApps
                        )
                    }

                    // ==================== 区域5: 权限模式与监控开关 ====================
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(400, delayMillis = 240)) + slideInVertically(
                            initialOffsetY = { it / 4 },
                            animationSpec = tween(
                                400,
                                delayMillis = 240,
                                easing = FastOutSlowInEasing
                            )
                        )
                    ) {
                        PermissionModeCard(
                            mode = privilegeMode,
                            isMonitorRunning = isMonitorRunning,
                            onMonitorToggle = { enabled ->
                                // 同步更新 ViewModel 状态和 DataStore（MainActivity 监听 DataStore 来启停服务）
                                viewModel.setMonitorRunning(enabled)
                                scope.launch {
                                    DataStoreHelper.setMonitorEnabled(context, enabled)
                                }
                            }
                        )
                    }

                    // 底部留白（为底部导航栏留出空间）
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }

            // 错误提示 Snackbar（3秒后自动消失）
            errorMessage?.let { error ->
                LaunchedEffect(error) {
                    delay(3000)
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

// ===================================================================================
// 运营商与网络状态卡片
// ===================================================================================

/**
 * 运营商与网络状态卡片
 * 显示当前 SIM 卡的运营商信息（支持双卡）和活跃网络类型
 *
 * @param carrierInfos 检测到的运营商信息列表（单卡1项，双卡2项）
 * @param networkType 当前活跃的网络类型（WiFi/Mobile/None）
 */
@Composable
private fun CarrierNetworkCard(
    carrierInfos: List<CarrierInfo>,
    networkType: String
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 卡片标题行：标题 + 当前网络类型标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "运营商",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))

                // 当前网络类型彩色标签
                val (netTypeText, netTypeColor) = when (networkType) {
                    Constants.NETWORK_TYPE_WIFI -> "WiFi" to TrafficBlue
                    Constants.NETWORK_TYPE_MOBILE -> "移动数据" to TrafficGreen
                    else -> "无网络" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = netTypeColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = netTypeText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = netTypeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (carrierInfos.isEmpty()) {
                // 无 SIM 卡信息
                Text(
                    text = "未检测到SIM卡信息",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // 逐行显示每张 SIM 卡信息
                carrierInfos.forEach { info ->
                    CarrierInfoRow(
                        info = info,
                        isLast = info == carrierInfos.last()
                    )
                }
            }
        }
    }
}

/**
 * 单张 SIM 卡信息行
 * 显示运营商颜色圆点、名称+网络类型、默认数据卡标识
 *
 * @param info 单张 SIM 卡的运营商信息
 * @param isLast 是否为列表最后一项（控制分割线显示）
 */
@Composable
private fun CarrierInfoRow(
    info: CarrierInfo,
    isLast: Boolean
) {
    // 运营商品牌色
    val carrierColor = getCarrierColor(info.carrierName)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 运营商颜色圆点
        Surface(
            modifier = Modifier.size(10.dp),
            shape = CircleShape,
            color = carrierColor
        ) {}

        Spacer(modifier = Modifier.width(10.dp))

        // 运营商名称 + 网络类型（如"中国移动 5G"）
        Text(
            text = "${info.carrierName} ${info.networkType}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.weight(1f))

        // 默认数据卡标签
        if (info.isDefault && info.subscriptionId > 0) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "默认数据",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }

    // 非最后一项时显示分割线
    if (!isLast) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

// ===================================================================================
// 实时网速仪表盘区域
// ===================================================================================

/**
 * 实时网速仪表盘区域
 * 包裹在 ElevatedCard 中，内部调用 SpeedMeterWidget 组件绘制圆弧仪表盘
 *
 * @param downloadSpeed 当前下载速率（字节/秒）
 * @param uploadSpeed 当前上传速率（字节/秒）
 */
@Composable
private fun SpeedMeterSection(
    downloadSpeed: Long,
    uploadSpeed: Long
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题行
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

            // 仪表盘组件（220dp 圆形区域）
            SpeedMeterWidget(
                downloadSpeed = downloadSpeed,
                uploadSpeed = uploadSpeed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
        }
    }
}

// ===================================================================================
// 今日流量统计区域
// ===================================================================================

/**
 * 今日流量统计区域
 * 两个并排的 ElevatedCard：左侧移动流量（绿色），右侧 WiFi 流量（蓝色）
 * 每个卡片包含图标、MB 精确数值、进度条、限额信息
 *
 * @param mobileTraffic 今日移动网络总流量（字节）
 * @param wifiTraffic 今日 WiFi 总流量（字节）
 */
@Composable
private fun TodayTrafficSection(
    mobileTraffic: Long,
    wifiTraffic: Long
) {
    // 每日流量限额（字节），null 表示未设置限额
    // TODO: 从 DataStore / 设置页面 中读取用户配置的每日限额
    val dailyLimitBytes: Long? = null

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 左卡片：移动流量
        TrafficStatCard(
            modifier = Modifier.weight(1f),
            label = "移动流量",
            traffic = mobileTraffic,
            limitBytes = dailyLimitBytes,
            iconPainter = painterResource(id = R.drawable.ic_mobile),
            iconColor = TrafficGreen,
            accentColor = TrafficGreen
        )

        // 右卡片：WiFi 流量
        TrafficStatCard(
            modifier = Modifier.weight(1f),
            label = "WiFi流量",
            traffic = wifiTraffic,
            limitBytes = dailyLimitBytes,
            iconPainter = painterResource(id = R.drawable.ic_wifi),
            iconColor = TrafficBlue,
            accentColor = TrafficBlue
        )
    }
}

/**
 * 单个流量统计卡片
 * 显示图标、流量数值（精确到 MB，StatValue 样式）、标签、进度条
 * 如果设了每日限额，显示已用/限额文本；超过 80% 变橙色，超过 100% 变红色
 *
 * @param modifier 布局修饰符
 * @param label 卡片标签（"移动流量" / "WiFi流量"）
 * @param traffic 今日已用流量（字节）
 * @param limitBytes 每日限额（字节），null 表示未设置
 * @param iconPainter 图标绘制器
 * @param iconColor 图标颜色
 * @param accentColor 主题色（用于数值和进度条）
 */
@Composable
private fun TrafficStatCard(
    modifier: Modifier = Modifier,
    label: String,
    traffic: Long,
    limitBytes: Long?,
    iconPainter: Painter,
    iconColor: Color,
    accentColor: Color
) {
    // 进度计算：以限额为 100%，无限额时以 1GB 为参考值
    val referenceLimit = limitBytes ?: (1024L * 1024L * 1024L)
    val rawProgress = (traffic.toFloat() / referenceLimit).coerceIn(0f, 1f)

    // 进度条动画
    val progress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "trafficProgress"
    )

    // 超限颜色：>80% 橙色，>100% 红色
    val displayColor by animateColorAsState(
        targetValue = when {
            limitBytes != null && traffic > limitBytes -> TrafficRed
            limitBytes != null && traffic > limitBytes * 0.8f -> TrafficOrange
            else -> accentColor
        },
        animationSpec = tween(300),
        label = "trafficDisplayColor"
    )

    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 图标
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = iconColor.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = iconPainter,
                        contentDescription = label,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 流量数值（精确到 MB，使用 StatValue 样式）
            Text(
                text = TrafficFormatter.formatMb(traffic),
                style = StatValueTextStyle,
                color = displayColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 标签
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 如果设了每日限额，显示"已用 / 限额"
            if (limitBytes != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${TrafficFormatter.formatMb(traffic)} / ${TrafficFormatter.formatMb(limitBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = displayColor
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 进度条
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = displayColor,
                trackColor = displayColor.copy(alpha = 0.12f),
                strokeCap = StrokeCap.Round
            )
        }
    }
}

// ===================================================================================
// 应用流量 TOP5 排行区域
// ===================================================================================

/**
 * 应用流量 TOP5 排行区域
 * 显示今日流量最高的 5 个应用，每项包含图标、名称、流量值、百分比条
 * 顶部有"查看全部"按钮可导航到完整应用列表
 *
 * @param topApps 今日流量 TOP5 应用列表
 * @param onViewAll 点击"查看全部"的回调
 */
@Composable
private fun TopAppsSection(
    topApps: List<AppTrafficInfo>,
    onViewAll: () -> Unit
) {
    // TOP1 的流量值，作为百分比条的 100% 参考
    val maxTraffic = topApps.firstOrNull()?.totalBytes ?: 1L

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题行 + "查看全部" 按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "应用流量排行",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onViewAll) {
                    Text(
                        text = "查看全部",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "查看全部",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (topApps.isEmpty()) {
                // 无应用数据时的空状态
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无应用流量数据",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // TOP5 列表
                topApps.forEachIndexed { index, app ->
                    TopAppItem(
                        app = app,
                        rank = index + 1,
                        maxTraffic = maxTraffic,
                        isLast = index == topApps.lastIndex
                    )
                }
            }
        }
    }
}

/**
 * 单个 TOP 应用流量项
 * 布局：应用图标 | 名称+百分比条 | 流量数值 | 右箭头
 *
 * @param app 应用流量信息
 * @param rank 排名序号
 * @param maxTraffic 列表中最大流量值（用于百分比计算）
 * @param isLast 是否为列表最后一项（控制分割线）
 */
@Composable
private fun TopAppItem(
    app: AppTrafficInfo,
    rank: Int,
    maxTraffic: Long,
    isLast: Boolean
) {
    // 百分比进度动画
    val percentage by animateFloatAsState(
        targetValue = if (maxTraffic > 0) {
            (app.totalBytes.toFloat() / maxTraffic).coerceIn(0f, 1f)
        } else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "appTrafficPercentage"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 应用图标（40dp，带圆角裁剪）
        AppIconSmall(
            icon = app.icon,
            appName = app.appName,
            modifier = Modifier.size(40.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 中间区域：应用名称 + 百分比进度条
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { percentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 流量数值（TrafficValue 样式）
        Text(
            text = TrafficFormatter.formatBytes(app.totalBytes),
            style = TrafficValueTextStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = 72.dp)
        )

        Spacer(modifier = Modifier.width(4.dp))

        // 右箭头
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "查看详情",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }

    // 非最后一项时显示分割线
    if (!isLast) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

// ===================================================================================
// 权限模式与监控开关卡片
// ===================================================================================

/**
 * 权限模式与监控服务控制卡片
 * 上半部分显示当前权限模式（Root/Shizuku/无权限）及对应颜色
 * 下半部分显示监控服务运行状态和开关
 *
 * @param mode 当前权限模式：Constants.MODE_ROOT / MODE_SHIZUKU / MODE_NONE
 * @param isMonitorRunning 监控服务是否正在运行
 * @param onMonitorToggle 切换监控服务的回调
 */
@Composable
private fun PermissionModeCard(
    mode: String,
    isMonitorRunning: Boolean,
    onMonitorToggle: (Boolean) -> Unit
) {
    // 根据权限模式确定显示内容和颜色
    val (modeLabel, modeDesc, modeColor) = when (mode) {
        Constants.MODE_ROOT -> Triple("Root", "Root权限已获取", StatusRootGreen)
        Constants.MODE_SHIZUKU -> Triple("Shizuku", "Shizuku权限已获取", StatusShizukuBlue)
        else -> Triple("无权限", "仅监控模式", StatusNoneGray)
    }

    // 模式颜色平滑动画
    val animatedColor by animateColorAsState(
        targetValue = modeColor,
        animationSpec = tween(300),
        label = "modeColor"
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 区域标题
            Text(
                text = "权限与服务",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ---- 权限模式信息行 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 模式图标（带颜色背景的圆角方块，显示首字母）
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = animatedColor.copy(alpha = 0.12f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Text(
                            text = modeLabel.take(1),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = animatedColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // 模式名称和描述
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = modeLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = animatedColor
                    )
                    Text(
                        text = modeDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 状态指示圆点
                Surface(
                    modifier = Modifier.size(10.dp),
                    shape = CircleShape,
                    color = animatedColor
                ) {}
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 分割线
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ---- 监控服务控制行 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 监控图标
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "流量监控",
                    tint = if (isMonitorRunning) MonitorRunning else MonitorStopped,
                    modifier = Modifier.size(22.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // 监控状态文字
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "流量监控",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isMonitorRunning) "监控运行中" else "监控已停止",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isMonitorRunning) MonitorRunning
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 监控开关
                Switch(
                    checked = isMonitorRunning,
                    onCheckedChange = onMonitorToggle,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MonitorRunning,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }
    }
}

// ===================================================================================
// 辅助工具
// ===================================================================================

/**
 * 获取运营商品牌色
 * 根据运营商名称返回对应的品牌颜色（移动红、联通橙、电信蓝）
 *
 * @param carrierName 运营商名称字符串
 * @return 对应的品牌色，未知运营商返回默认蓝色
 */
private fun getCarrierColor(carrierName: String): Color {
    return when {
        carrierName.contains("\u79FB\u52A8") -> CarrierCMCC    // 移动
        carrierName.contains("\u8054\u901A") -> CarrierCUCC    // 联通
        carrierName.contains("\u7535\u4FE1") -> CarrierCTCC    // 电信
        else -> Color(0xFF0A84FF) // 默认品牌蓝
    }
}

/**
 * 小号应用图标组件
 * 有真实图标时显示应用图标（带 8dp 圆角裁剪），否则显示首字母占位
 *
 * @param icon 应用图标 Drawable，可能为 null
 * @param appName 应用名称（用于生成首字母占位）
 * @param modifier 布局修饰符
 */
@Composable
private fun AppIconSmall(
    icon: android.graphics.drawable.Drawable?,
    appName: String,
    modifier: Modifier = Modifier
) {
    if (icon != null) {
        // 显示真实应用图标
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    setImageDrawable(icon)
                    clipToOutline = true
                    outlineProvider = ViewOutlineProvider.BOUNDS
                }
            },
            modifier = modifier.clip(RoundedCornerShape(8.dp))
        )
    } else {
        // 无图标时显示首字母占位
        val initial = appName.take(1)
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}