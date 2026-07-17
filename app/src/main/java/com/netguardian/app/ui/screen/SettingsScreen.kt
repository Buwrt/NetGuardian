package com.netguardian.app.ui.screen

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netguardian.app.ui.theme.*
import com.netguardian.app.util.Constants
import com.netguardian.app.viewmodel.SettingsViewModel

/**
 * 设置页面 - Settings Screen
 * 包含权限管理、功能设置、通知设置、数据管理和关于信息
 *
 * @param viewModel SettingsViewModel实例
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current

    // 收集状态
    val isRootAvailable by viewModel.isRootAvailable.collectAsStateWithLifecycle()
    val isShizukuAvailable by viewModel.isShizukuAvailable.collectAsStateWithLifecycle()
    val antiLeakEnabled by viewModel.antiLeakEnabled.collectAsStateWithLifecycle()
    val monitorRunning by viewModel.monitorServiceRunning.collectAsStateWithLifecycle()
    val autoDisconnect by viewModel.autoDisconnect.collectAsStateWithLifecycle()
    val limitReminder by viewModel.limitReminder.collectAsStateWithLifecycle()
    val monitorNotification by viewModel.monitorNotification.collectAsStateWithLifecycle()
    val idleCheckInterval by viewModel.idleCheckInterval.collectAsStateWithLifecycle()
    val dataRetentionDays by viewModel.dataRetentionDays.collectAsStateWithLifecycle()
    val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
    val isOperating by viewModel.isOperating.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    // 闲置检测间隔输入
    var idleIntervalInput by remember(idleCheckInterval) {
        mutableStateOf(idleCheckInterval.toString())
    }

    // 版本信息
    val versionInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "未知"
        } catch (e: Exception) {
            "未知"
        }
    }

    Scaffold(
        topBar = {
            SettingsTopBar()
        },
        snackbarHost = { hostState ->
            message?.let {
                LaunchedEffect(it) {
                    kotlinx.coroutines.MainScope().launch {
                        kotlinx.coroutines.delay(2000)
                        viewModel.clearMessage()
                    }
                }
                Snackbar(snackbarHostState = hostState) { Text(it) }
            }
            errorMessage?.let {
                LaunchedEffect(it) {
                    kotlinx.coroutines.MainScope().launch {
                        kotlinx.coroutines.delay(3000)
                        viewModel.clearError()
                    }
                }
                Snackbar(snackbarHostState = hostState) { Text("错误: $it") }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ==================== 1. 权限管理 ====================
            SectionHeader(title = "权限管理", icon = Icons.Default.Security)

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(4.dp)) {
                    // Root模式
                    SettingsSwitchItem(
                        title = "Root模式",
                        subtitle = if (isRootAvailable) "已获取Root权限" else "未检测到Root",
                        checked = isRootAvailable,
                        onCheckedChange = { viewModel.detectRoot() },
                        icon = Icons.Default.VerifiedUser,
                        iconColor = if (isRootAvailable) StatusRootGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        enabled = true
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Shizuku模式
                    SettingsItem(
                        title = "Shizuku模式",
                        subtitle = if (isShizukuAvailable) "Shizuku已连接并授权" else "未连接",
                        onClick = { viewModel.refreshShizukuStatus() },
                        icon = Icons.Default.Devices,
                        iconColor = if (isShizukuAvailable) StatusShizukuBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        trailing = {
                            Text(
                                text = if (isShizukuAvailable) "已连接" else "检测",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isShizukuAvailable) StatusShizukuBlue
                                else MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }
            }

            // ==================== 2. 功能设置 ====================
            SectionHeader(title = "功能设置", icon = Icons.Default.Tune)

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(4.dp)) {
                    // 防偷跑开关
                    SettingsSwitchItem(
                        title = "防偷跑流量",
                        subtitle = "检测后台异常流量并拦截",
                        checked = antiLeakEnabled,
                        onCheckedChange = { viewModel.setAntiLeakEnabled(it) },
                        icon = Icons.Default.Shield,
                        iconColor = TrafficOrange
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // 自动断网开关
                    SettingsSwitchItem(
                        title = "自动断网",
                        subtitle = "流量超限时自动断开网络",
                        checked = autoDisconnect,
                        onCheckedChange = { viewModel.setAutoDisconnect(it) },
                        icon = Icons.Default.DisableByDefault,
                        iconColor = TrafficRed
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // 监控服务开关
                    SettingsSwitchItem(
                        title = "流量监控服务",
                        subtitle = if (monitorRunning) "正在后台监控流量" else "监控服务已停止",
                        checked = monitorRunning,
                        onCheckedChange = { viewModel.setMonitorServiceRunning(it) },
                        icon = Icons.Default.Sensors,
                        iconColor = if (monitorRunning) MonitorRunning else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ==================== 3. 通知设置 ====================
            SectionHeader(title = "通知设置", icon = Icons.Default.Notifications)

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(4.dp)) {
                    // 流量限额提醒
                    SettingsSwitchItem(
                        title = "流量限额提醒",
                        subtitle = "应用流量接近限额时通知",
                        checked = limitReminder,
                        onCheckedChange = { viewModel.setLimitReminder(it) },
                        icon = Icons.Default.WarningAmber,
                        iconColor = TrafficOrange
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // 监控通知
                    SettingsSwitchItem(
                        title = "监控通知",
                        subtitle = "显示监控服务运行状态",
                        checked = monitorNotification,
                        onCheckedChange = { viewModel.setMonitorNotification(it) },
                        icon = Icons.Default.NotificationsActive,
                        iconColor = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ==================== 4. 数据管理 ====================
            SectionHeader(title = "数据管理", icon = Icons.Default.Storage)

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(4.dp)) {
                    // 清除历史数据
                    SettingsItem(
                        title = "清除历史数据",
                        subtitle = "删除所有流量记录和控制规则",
                        onClick = {
                            // 确认对话框
                        },
                        icon = Icons.Default.DeleteSweep,
                        iconColor = TrafficRed,
                        trailing = {
                            if (isOperating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                TextButton(onClick = { viewModel.clearAllData() }) {
                                    Text("清除", color = TrafficRed)
                                }
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // 导出数据
                    SettingsItem(
                        title = "导出数据",
                        subtitle = "将流量数据导出为CSV文件",
                        onClick = { viewModel.exportData() },
                        icon = Icons.Default.FileDownload,
                        iconColor = MaterialTheme.colorScheme.primary
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // 清除所有规则
                    SettingsItem(
                        title = "清除所有规则",
                        subtitle = "恢复所有应用的网络访问",
                        onClick = { viewModel.clearAllRules() },
                        icon = Icons.Default.Rule,
                        iconColor = TrafficOrange
                    )
                }
            }

            // ==================== 5. 关于 ====================
            SectionHeader(title = "关于", icon = Icons.Default.Info)

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(4.dp)) {
                    // 版本信息
                    SettingsItem(
                        title = "版本信息",
                        subtitle = "NetGuardian v$versionInfo",
                        onClick = {},
                        icon = Icons.Default.Update,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        trailing = {
                            Text(
                                text = versionInfo,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // 隐私声明
                    SettingsItem(
                        title = "隐私声明",
                        subtitle = "纯本地运行，零联网，不收集任何数据",
                        onClick = {},
                        icon = Icons.Default.PrivacyTip,
                        iconColor = StatusRootGreen
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // 开源许可
                    SettingsItem(
                        title = "开源许可",
                        subtitle = "本应用使用的开源库许可信息",
                        onClick = {},
                        icon = Icons.Default.Description,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }

            // 底部留白
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

/**
 * 设置页顶部标题栏
 */
@Composable
private fun SettingsTopBar() {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "权限、功能与数据管理",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

/**
 * Section标题组件
 */
@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 设置项 - 开关类型
 */
@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    iconColor: Color,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Surface(
            shape = CircleShape,
            color = iconColor.copy(alpha = 0.12f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .padding(8.dp)
                    .size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 文字
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 开关
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

/**
 * 设置项 - 点击类型
 */
@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: ImageVector,
    iconColor: Color,
    trailing: @Composable (RowScope.() -> Unit) = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Surface(
            shape = CircleShape,
            color = iconColor.copy(alpha = 0.12f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .padding(8.dp)
                    .size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 文字
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 右侧组件
        trailing()
    }
}
