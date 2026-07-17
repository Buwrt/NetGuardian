package com.netguardian.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Phonelink
import androidx.compose.material.icons.filled.SignalCellularOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netguardian.app.ui.theme.TrafficBlue
import com.netguardian.app.ui.theme.TrafficGreen
import com.netguardian.app.ui.theme.TrafficRed
import com.netguardian.app.util.TrafficFormatter
import com.netguardian.app.viewmodel.AppControlViewModel

/**
 * 应用联网控制页面
 * 提供单个应用的网络权限管理和流量限额设置
 * 包含移动数据/WiFi/后台网络开关、流量限额输入、强制断网功能
 *
 * 通过 AppNavigation 传递 packageName 参数
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppControlScreen(
    packageName: String,
    viewModel: AppControlViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    // 收集状态
    val appName by viewModel.appName.collectAsStateWithLifecycle()
    val appIcon by viewModel.appIcon.collectAsStateWithLifecycle()
    val mobileAllowed by viewModel.mobileAllowed.collectAsStateWithLifecycle()
    val wifiAllowed by viewModel.wifiAllowed.collectAsStateWithLifecycle()
    val backgroundAllowed by viewModel.backgroundAllowed.collectAsStateWithLifecycle()
    val mobileLimitMb by viewModel.mobileLimitMb.collectAsStateWithLifecycle()
    val wifiLimitMb by viewModel.wifiLimitMb.collectAsStateWithLifecycle()
    val trafficInfo by viewModel.trafficInfo.collectAsStateWithLifecycle()
    val mobileUsagePercent by viewModel.mobileUsagePercent.collectAsStateWithLifecycle()
    val wifiUsagePercent by viewModel.wifiUsagePercent.collectAsStateWithLifecycle()
    val isOperating by viewModel.isOperating.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    // 流量限额输入状态
    var mobileLimitInput by remember(mobileLimitMb) { mutableStateOf(mobileLimitMb) }
    var wifiLimitInput by remember(wifiLimitMb) { mutableStateOf(wifiLimitMb) }
    var dailyLimitInput by remember { mutableStateOf("") }

    // 流量详情周期选择
    var selectedPeriod by remember { mutableStateOf(0) } // 0=日, 1=周, 2=月

    // 强制断网确认对话框
    var showForceDisconnectDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = WindowInsets(top = 0.dp)
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. 应用信息卡
                AppInfoCard(
                    appName = appName,
                    icon = appIcon,
                    packageName = packageName,
                    trafficInfo = trafficInfo
                )

                // 2. 移动数据开关卡片
                NetworkSwitchCard(
                    title = "移动数据",
                    checked = mobileAllowed,
                    enabled = !isOperating,
                    statusText = if (mobileAllowed) "已允许" else "已禁止",
                    icon = if (mobileAllowed) Icons.Default.NetworkCell else Icons.Default.SignalCellularOff,
                    onCheckedChange = { viewModel.setMobileAllowed(it) }
                )

                // 3. WiFi网络开关卡片
                NetworkSwitchCard(
                    title = "WiFi网络",
                    checked = wifiAllowed,
                    enabled = !isOperating,
                    statusText = if (wifiAllowed) "已允许" else "已禁止",
                    icon = if (wifiAllowed) Icons.Default.Wifi else Icons.Default.WifiOff,
                    onCheckedChange = { viewModel.setWifiAllowed(it) }
                )

                // 4. 后台网络开关卡片
                NetworkSwitchCard(
                    title = "后台联网",
                    checked = backgroundAllowed,
                    enabled = !isOperating,
                    statusText = if (backgroundAllowed) "已允许" else "已禁止",
                    icon = if (backgroundAllowed) Icons.Default.Phonelink else Icons.Default.Phonelink,
                    onCheckedChange = { viewModel.setBackgroundAllowed(it) }
                )

                // 5. 流量限额设置区域
                TrafficLimitCard(
                    mobileLimitInput = mobileLimitInput,
                    wifiLimitInput = wifiLimitInput,
                    dailyLimitInput = dailyLimitInput,
                    onMobileLimitChange = { mobileLimitInput = it },
                    onWifiLimitChange = { wifiLimitInput = it },
                    onDailyLimitChange = { dailyLimitInput = it },
                    isOperating = isOperating,
                    onSave = {
                        viewModel.saveTrafficLimit(mobileLimitInput, wifiLimitInput)
                    }
                )

                // 6. 流量使用详情
                TrafficDetailCard(
                    trafficInfo = trafficInfo,
                    mobileUsagePercent = mobileUsagePercent,
                    wifiUsagePercent = wifiUsagePercent,
                    mobileLimit = mobileLimitInput,
                    wifiLimit = wifiLimitInput,
                    selectedPeriod = selectedPeriod,
                    onPeriodSelected = { selectedPeriod = it }
                )

                // 7. 底部危险区域
                DangerZoneCard(
                    onForceDisconnect = { showForceDisconnectDialog = true },
                    onRestoreAll = { viewModel.restoreAll() }
                )

                // 底部留白
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // 强制断网确认对话框
    if (showForceDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showForceDisconnectDialog = false },
            title = { Text("确认强制断网") },
            text = { Text("将同时屏蔽 $appName 的移动数据和WiFi网络，该应用将无法联网。") },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        viewModel.forceDisconnect()
                        showForceDisconnectDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TrafficRed
                    )
                ) {
                    Text("强制断网")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForceDisconnectDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 成功消息 Snackbar
    message?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearMessage()
        }
        androidx.compose.material3.Snackbar(
            modifier = Modifier.padding(16.dp),
            containerColor = TrafficGreen
        ) {
            Text(msg, color = Color.White)
        }
    }

    // 错误消息 Snackbar
    errorMessage?.let { err ->
        LaunchedEffect(err) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
        androidx.compose.material3.Snackbar(
            modifier = Modifier.padding(16.dp),
            containerColor = TrafficRed
        ) {
            Text(err, color = Color.White)
        }
    }
}

/**
 * 应用信息卡片 - 顶部大卡片
 */
@Composable
private fun AppInfoCard(
    appName: String,
    icon: android.graphics.drawable.Drawable?,
    packageName: String,
    trafficInfo: com.netguardian.app.model.AppTrafficInfo?
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 应用图标 64dp
                if (icon != null) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { context ->
                            android.widget.ImageView(context).apply {
                                setImageDrawable(icon)
                                clipToOutline = true
                                outlineProvider = android.view.ViewOutlineProvider.BOUNDS
                            }
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = appName.take(1),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = appName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = packageName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 当前流量使用情况
            if (trafficInfo != null) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 移动流量
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "今日移动",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = TrafficFormatter.formatMb(trafficInfo.totalMobile),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TrafficGreen
                        )
                    }

                    // WiFi流量
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "今日WiFi",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = TrafficFormatter.formatMb(trafficInfo.totalWifi),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TrafficBlue
                        )
                    }
                }
            }
        }
    }
}

/**
 * 网络开关卡片 - 每个控制项独立卡片
 */
@Composable
private fun NetworkSwitchCard(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    statusText: String,
    icon: ImageVector,
    onCheckedChange: (Boolean) -> Unit
) {
    val statusColor = if (checked) TrafficGreen else TrafficRed
    val switchTrackColor = if (checked) TrafficGreen else MaterialTheme.colorScheme.surfaceVariant

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧图标
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(28.dp),
                tint = statusColor
            )

            Spacer(modifier = Modifier.width(14.dp))

            // 中间标题+状态
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = statusText,
                    fontSize = 13.sp,
                    color = statusColor
                )
            }

            // 右侧Switch
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = switchTrackColor,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

/**
 * 流量限额设置卡片
 */
@Composable
private fun TrafficLimitCard(
    mobileLimitInput: String,
    wifiLimitInput: String,
    dailyLimitInput: String,
    onMobileLimitChange: (String) -> Unit,
    onWifiLimitChange: (String) -> Unit,
    onDailyLimitChange: (String) -> Unit,
    isOperating: Boolean,
    onSave: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "流量限额",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "留空表示不限制",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 移动流量限额
            OutlinedTextField(
                value = mobileLimitInput,
                onValueChange = onMobileLimitChange,
                label = { Text("移动流量限额") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                trailingIcon = { Text("MB", fontSize = 13.sp) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // WiFi流量限额
            OutlinedTextField(
                value = wifiLimitInput,
                onValueChange = onWifiLimitChange,
                label = { Text("WiFi流量限额") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                trailingIcon = { Text("MB", fontSize = 13.sp) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 每日限额
            OutlinedTextField(
                value = dailyLimitInput,
                onValueChange = onDailyLimitChange,
                label = { Text("每日限额") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                trailingIcon = { Text("MB", fontSize = 13.sp) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 保存按钮
            androidx.compose.material3.FilledTonalButton(
                onClick = onSave,
                enabled = !isOperating,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isOperating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("保存")
            }
        }
    }
}

/**
 * 流量使用详情卡片 - 含周期选择Tab和进度条
 */
@Composable
private fun TrafficDetailCard(
    trafficInfo: com.netguardian.app.model.AppTrafficInfo?,
    mobileUsagePercent: Float,
    wifiUsagePercent: Float,
    mobileLimit: String,
    wifiLimit: String,
    selectedPeriod: Int,
    onPeriodSelected: (Int) -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "流量使用详情",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 周期选择Tab
            val tabs = listOf("日", "周", "月")
            TabRow(
                selectedTabIndex = selectedPeriod,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedPeriod == index,
                        onClick = { onPeriodSelected(index) },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedPeriod == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (trafficInfo != null) {
                // 移动流量进度条
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "移动",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TrafficGreen,
                        modifier = Modifier.width(40.dp)
                    )
                    LinearProgressIndicator(
                        progress = { mobileUsagePercent },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp),
                        color = TrafficGreen,
                        trackColor = TrafficGreen.copy(alpha = 0.12f),
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = TrafficFormatter.formatBytes(trafficInfo.totalMobile) +
                                if (mobileLimit.isNotBlank() && mobileLimit.toLongOrNull() ?: 0 > 0)
                                    " / ${mobileLimit}MB"
                                else "",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(110.dp),
                        textAlign = TextAlign.End
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // WiFi流量进度条
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "WiFi",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TrafficBlue,
                        modifier = Modifier.width(40.dp)
                    )
                    LinearProgressIndicator(
                        progress = { wifiUsagePercent },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp),
                        color = TrafficBlue,
                        trackColor = TrafficBlue.copy(alpha = 0.12f),
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = TrafficFormatter.formatBytes(trafficInfo.totalWifi) +
                                if (wifiLimit.isNotBlank() && wifiLimit.toLongOrNull() ?: 0 > 0)
                                    " / ${wifiLimit}MB"
                                else "",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(110.dp),
                        textAlign = TextAlign.End
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 总流量
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "总计",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = TrafficFormatter.formatBytes(trafficInfo.totalBytes),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无流量数据",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 底部危险区域卡片
 */
@Composable
private fun DangerZoneCard(
    onForceDisconnect: () -> Unit,
    onRestoreAll: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "危险操作",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TrafficRed
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 强制断网 - 红色边框 OutlinedButton
            OutlinedButton(
                onClick = onForceDisconnect,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.5.dp,
                    color = TrafficRed
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TrafficRed
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Dangerous,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("强制断网")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 重置权限
            TextButton(
                onClick = onRestoreAll,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("重置权限")
            }
        }
    }
}
