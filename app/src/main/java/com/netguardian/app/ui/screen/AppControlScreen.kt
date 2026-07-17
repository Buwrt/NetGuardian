package com.netguardian.app.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netguardian.app.ui.theme.*
import com.netguardian.app.util.TrafficFormatter
import com.netguardian.app.viewmodel.AppControlViewModel

/**
 * 应用联网控制页面
 * 提供单个应用的网络权限管理和流量限额设置
 * 包含移动数据/WiFi/后台网络开关、流量限额输入、强制断网功能
 *
 * @param packageName 目标应用包名
 * @param viewModel AppControlViewModel实例
 * @param onBack 返回回调
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

    // 强制断网确认对话框
    var showForceDisconnectDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppControlTopBar(
                appName = appName,
                onBack = onBack
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            // 加载状态
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
            // 主内容
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. 应用信息头部
                AppInfoHeader(
                    appName = appName,
                    icon = appIcon,
                    packageName = packageName
                )

                // 2. 网络权限控制区域
                NetworkPermissionSection(
                    mobileAllowed = mobileAllowed,
                    wifiAllowed = wifiAllowed,
                    backgroundAllowed = backgroundAllowed,
                    isOperating = isOperating,
                    onMobileToggle = { viewModel.setMobileAllowed(it) },
                    onWifiToggle = { viewModel.setWifiAllowed(it) },
                    onBackgroundToggle = { viewModel.setBackgroundAllowed(it) }
                )

                // 3. 当前流量使用
                CurrentTrafficSection(
                    trafficInfo = trafficInfo,
                    mobileUsagePercent = mobileUsagePercent,
                    wifiUsagePercent = wifiUsagePercent,
                    mobileLimit = mobileLimitInput,
                    wifiLimit = wifiLimitInput
                )

                // 4. 流量限额设置区域
                TrafficLimitSection(
                    mobileLimitInput = mobileLimitInput,
                    wifiLimitInput = wifiLimitInput,
                    onMobileLimitChange = { mobileLimitInput = it },
                    onWifiLimitChange = { wifiLimitInput = it },
                    isOperating = isOperating,
                    onSave = {
                        viewModel.saveTrafficLimit(mobileLimitInput, wifiLimitInput)
                    }
                )

                // 5. 底部操作区
                BottomActionsSection(
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
                Button(
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
}

/**
 * 顶部标题栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppControlTopBar(
    appName: String,
    onBack: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = appName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
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
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

/**
 * 应用信息头部
 */
@Composable
private fun AppInfoHeader(
    appName: String,
    icon: android.graphics.drawable.Drawable?,
    packageName: String
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 应用图标
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
                        .size(48.dp)
                        .clip(CircleShape)
                )
            } else {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = appName.take(1),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 网络权限控制区域
 * 包含移动数据、WiFi、后台网络三个开关
 */
@Composable
private fun NetworkPermissionSection(
    mobileAllowed: Boolean,
    wifiAllowed: Boolean,
    backgroundAllowed: Boolean,
    isOperating: Boolean,
    onMobileToggle: (Boolean) -> Unit,
    onWifiToggle: (Boolean) -> Unit,
    onBackgroundToggle: (Boolean) -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "网络权限",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 移动数据开关
            PermissionSwitchRow(
                label = "移动数据",
                description = if (mobileAllowed) "已允许" else "已屏蔽",
                checked = mobileAllowed,
                enabled = !isOperating,
                color = TrafficBlue,
                onCheckedChange = onMobileToggle
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // WiFi开关
            PermissionSwitchRow(
                label = "WiFi网络",
                description = if (wifiAllowed) "已允许" else "已屏蔽",
                checked = wifiAllowed,
                enabled = !isOperating,
                color = TrafficGreen,
                onCheckedChange = onWifiToggle
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 后台网络开关
            PermissionSwitchRow(
                label = "后台网络",
                description = if (backgroundAllowed) "已允许" else "已屏蔽",
                checked = backgroundAllowed,
                enabled = !isOperating,
                color = MaterialTheme.colorScheme.tertiary,
                onCheckedChange = onBackgroundToggle
            )
        }
    }
}

/**
 * 权限开关行组件
 */
@Composable
private fun PermissionSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    color: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 状态指示点
        Surface(
            modifier = Modifier.size(8.dp),
            shape = CircleShape,
            color = if (checked) color else MaterialTheme.colorScheme.surfaceVariant
        ) {}

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = if (checked) color else TrafficRed
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = color,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

/**
 * 当前流量使用区域
 * 显示当前周期的流量使用情况和进度条
 */
@Composable
private fun CurrentTrafficSection(
    trafficInfo: com.netguardian.app.model.AppTrafficInfo?,
    mobileUsagePercent: Float,
    wifiUsagePercent: Float,
    mobileLimit: String,
    wifiLimit: String
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "当前周期流量",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (trafficInfo != null) {
                Spacer(modifier = Modifier.height(12.dp))

                // 移动网络使用
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "移动",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(40.dp),
                        color = TrafficBlue
                    )
                    LinearProgressIndicator(
                        progress = { mobileUsagePercent },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp),
                        color = TrafficBlue,
                        trackColor = TrafficBlue.copy(alpha = 0.15f),
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${TrafficFormatter.formatBytes(trafficInfo.totalMobile)}" +
                                if (mobileLimit.isNotBlank() && mobileLimit.toLongOrNull() ?: 0 > 0)
                                    " / ${mobileLimit}MB"
                                else "",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(100.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // WiFi使用
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "WiFi",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(40.dp),
                        color = TrafficGreen
                    )
                    LinearProgressIndicator(
                        progress = { wifiUsagePercent },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp),
                        color = TrafficGreen,
                        trackColor = TrafficGreen.copy(alpha = 0.15f),
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${TrafficFormatter.formatBytes(trafficInfo.totalWifi)}" +
                                if (wifiLimit.isNotBlank() && wifiLimit.toLongOrNull() ?: 0 > 0)
                                    " / ${wifiLimit}MB"
                                else "",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(100.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 总流量
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "总计",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = TrafficFormatter.formatBytes(trafficInfo.totalBytes),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "暂无流量数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 流量限额设置区域
 */
@Composable
private fun TrafficLimitSection(
    mobileLimitInput: String,
    wifiLimitInput: String,
    onMobileLimitChange: (String) -> Unit,
    onWifiLimitChange: (String) -> Unit,
    isOperating: Boolean,
    onSave: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "流量限额设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "留空表示不限制。单位：MB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 移动流量限额输入
            OutlinedTextField(
                value = mobileLimitInput,
                onValueChange = onMobileLimitChange,
                label = { Text("移动流量限额 (MB)") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // WiFi流量限额输入
            OutlinedTextField(
                value = wifiLimitInput,
                onValueChange = onWifiLimitChange,
                label = { Text("WiFi流量限额 (MB)") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 保存按钮
            Button(
                onClick = onSave,
                enabled = !isOperating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isOperating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("保存限额设置")
            }
        }
    }
}

/**
 * 底部操作区域
 * 包含强制断网和恢复所有权限按钮
 */
@Composable
private fun BottomActionsSection(
    onForceDisconnect: () -> Unit,
    onRestoreAll: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "快捷操作",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 恢复所有权限
            OutlinedButton(
                onClick = onRestoreAll,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("恢复所有网络权限")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 强制断网按钮（红色危险按钮）
            Button(
                onClick = onForceDisconnect,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TrafficRed
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("强制断网")
            }
        }
    }
}
