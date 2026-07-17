package com.netguardian.app.ui.screen

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netguardian.app.ui.theme.*
import com.netguardian.app.util.Constants
import com.netguardian.app.util.DataStoreHelper
import com.netguardian.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * 设置中心页面 - Settings Screen
 * 专业级设置页面设计，包含权限管理、流量管控、通知设置、限额设置、数据管理、外观和关于信息
 *
 * @param viewModel SettingsViewModel实例
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ==================== 收集 ViewModel 状态 ====================
    val isRootAvailable by viewModel.isRootAvailable.collectAsStateWithLifecycle()
    val isShizukuAvailable by viewModel.isShizukuAvailable.collectAsStateWithLifecycle()
    val antiLeakEnabled by viewModel.antiLeakEnabled.collectAsStateWithLifecycle()
    val autoDisconnect by viewModel.autoDisconnect.collectAsStateWithLifecycle()
    val limitReminder by viewModel.limitReminder.collectAsStateWithLifecycle()
    val monitorNotification by viewModel.monitorNotification.collectAsStateWithLifecycle()
    val idleCheckInterval by viewModel.idleCheckInterval.collectAsStateWithLifecycle()
    val isOperating by viewModel.isOperating.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    // ==================== 主题模式 ====================
    var themeMode by remember {
        mutableStateOf("system")
    }
    LaunchedEffect(Unit) {
        DataStoreHelper.getThemeMode(context).let { themeMode = it }
    }

    // ==================== 运营商信息 ====================
    val carrierInfo = remember {
        getCarrierInfo(context)
    }

    // ==================== 偷跑告警开关 ====================
    var stealthyAlarmEnabled by remember {
        mutableStateOf(false)
    }

    // ==================== 限额输入 ====================
    var mobileLimitInput by remember { mutableStateOf("") }
    var wifiLimitInput by remember { mutableStateOf("") }

    // ==================== 闲置检测间隔 Dropdown ====================
    val intervalOptions = listOf(10, 30, 60, 120)
    var intervalDropdownExpanded by remember { mutableStateOf(false) }

    // ==================== 存储使用量 ====================
    var storageUsed by remember { mutableStateOf("计算中...") }

    LaunchedEffect(Unit) {
        try {
            val dbPath = context.getDatabasePath("netguardian.db")
            if (dbPath.exists()) {
                val sizeBytes = dbPath.length()
                val sizeMB = sizeBytes / (1024.0 * 1024.0)
                storageUsed = String.format("已使用 %.1f MB", sizeMB)
            } else {
                storageUsed = "已使用 0 MB"
            }
        } catch (_: Exception) {
            storageUsed = "已使用 0 MB"
        }
    }

    // ==================== Snackbar ====================
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar("错误: $it")
            viewModel.clearError()
        }
    }

    // ==================== 确认清除对话框 ====================
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除历史数据") },
            text = { Text("确定要删除所有流量记录和控制规则吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    viewModel.clearAllData()
                }) {
                    Text("确定删除", color = TrafficRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // ==================== 保存限额操作 ====================
    fun saveLimits() {
        scope.launch {
            val mobileMB = mobileLimitInput.toLongOrNull() ?: 0L
            val wifiMB = wifiLimitInput.toLongOrNull() ?: 0L
            // 保存到 SharedPreferences
            val prefs = context.getSharedPreferences(Constants.DATASTORE_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("global_mobile_limit_mb", mobileMB)
                .putLong("global_wifi_limit_mb", wifiMB)
                .apply()
            snackbarHostState.showSnackbar("限额已保存")
        }
    }

    // ==================== 导出数据 ====================
    fun exportData() {
        scope.launch {
            viewModel.exportData()
        }
    }

    // ==================== 恢复数据 ====================
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreResult by remember { mutableStateOf<String?>(null) }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("恢复数据") },
            text = { Text("将从文件恢复流量数据，当前数据将被覆盖。请确保已提前导出备份。") },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreDialog = false
                    // 打开文件选择器
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    try {
                        (context as? android.app.Activity)?.startActivityForResult(intent, 1001)
                        snackbarHostState.showSnackbar("请选择要恢复的文件")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("无法打开文件选择器")
                    }
                }) {
                    Text("选择文件")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // ==================== 页面主体 ====================
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ==================== 1. 权限管理 Section ====================
            item {
                SectionTitle(text = "权限管理")
            }

            item {
                // Root 模式卡片
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // # 符号图标
                        Surface(
                            shape = CircleShape,
                            color = if (isRootAvailable) StatusRootGreen.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "#",
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = if (isRootAvailable) StatusRootGreen
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Root 模式",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // 状态 badge
                        StatusBadge(
                            text = if (isRootAvailable) "已连接" else "未检测到",
                            color = if (isRootAvailable) StatusRootGreen else StatusNoneGray
                        )
                    }

                    // 运营商信息 + 按钮
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        // 运营商信息行
                        if (carrierInfo != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                // 运营商颜色标识点
                                Surface(
                                    modifier = Modifier.size(8.dp),
                                    shape = CircleShape,
                                    color = carrierInfo.carrierColor
                                ) {}
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${carrierInfo.carrierName} ${carrierInfo.networkType}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = { viewModel.detectRoot() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isOperating
                        ) {
                            if (isOperating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("检测 Root")
                        }
                    }
                }
            }

            item {
                // Shizuku 模式卡片
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // S 符号图标
                        Surface(
                            shape = CircleShape,
                            color = if (isShizukuAvailable) StatusShizukuBlue.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "S",
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = if (isShizukuAvailable) StatusShizukuBlue
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Shizuku 模式",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // 状态 badge
                        StatusBadge(
                            text = if (isShizukuAvailable) "已连接" else "未连接",
                            color = if (isShizukuAvailable) StatusShizukuBlue else StatusNoneGray
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                try {
                                    val intent = Intent().apply {
                                        setClassName(
                                            "moe.shizuku.privileged.api",
                                            "moe.shizuku.privileged.api.request.RequestActivity"
                                        )
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Shizuku 未安装，打开应用市场
                                    val marketIntent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("https://github.com/RikkaApps/Shizuku/releases")
                                    }
                                    context.startActivity(marketIntent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Devices,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("启动 Shizuku")
                        }
                    }
                }
            }

            // ==================== 2. 流量管控 Section ====================
            item {
                SectionTitle(text = "流量管控")
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // 防偷跑流量 Switch
                        SettingsSwitchRow(
                            title = "防偷跑流量",
                            description = "自动断开闲置应用网络",
                            checked = antiLeakEnabled,
                            onCheckedChange = { viewModel.setAntiLeakEnabled(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.divider
                        )

                        // 自动断网 Switch
                        SettingsSwitchRow(
                            title = "自动断网",
                            description = "超过限额自动断网",
                            checked = autoDisconnect,
                            onCheckedChange = { viewModel.setAutoDisconnect(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.divider
                        )

                        // 闲置检测间隔 DropdownMenu
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "闲置检测间隔",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "检测应用闲置的周期",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Box {
                                OutlinedButton(
                                    onClick = { intervalDropdownExpanded = true }
                                ) {
                                    Text("$idleCheckInterval 秒")
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = intervalDropdownExpanded,
                                    onDismissRequest = { intervalDropdownExpanded = false }
                                ) {
                                    intervalOptions.forEach { seconds ->
                                        DropdownMenuItem(
                                            text = { Text("$seconds 秒") },
                                            onClick = {
                                                viewModel.setIdleCheckInterval(seconds)
                                                intervalDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==================== 3. 通知设置 Section ====================
            item {
                SectionTitle(text = "通知设置")
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // 流量限额提醒
                        SettingsSwitchRow(
                            title = "流量限额提醒",
                            description = "超过限额时弹出通知",
                            checked = limitReminder,
                            onCheckedChange = { viewModel.setLimitReminder(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.divider
                        )

                        // 监控通知
                        SettingsSwitchRow(
                            title = "监控通知",
                            description = "显示前台监控通知",
                            checked = monitorNotification,
                            onCheckedChange = { viewModel.setMonitorNotification(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.divider
                        )

                        // 偷跑告警
                        SettingsSwitchRow(
                            title = "偷跑告警",
                            description = "检测到偷跑流量时告警",
                            checked = stealthyAlarmEnabled,
                            onCheckedChange = { stealthyAlarmEnabled = it }
                        )
                    }
                }
            }

            // ==================== 4. 限额设置 Section ====================
            item {
                SectionTitle(text = "每日限额")
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 全局移动流量限额
                        OutlinedTextField(
                            value = mobileLimitInput,
                            onValueChange = { mobileLimitInput = it.filter { c -> c.isDigit() } },
                            label = { Text("全局移动流量限额") },
                            placeholder = { Text("如 1024") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            trailingIcon = {
                                Text(
                                    text = "MB",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // 全局 WiFi 流量限额
                        OutlinedTextField(
                            value = wifiLimitInput,
                            onValueChange = { wifiLimitInput = it.filter { c -> c.isDigit() } },
                            label = { Text("全局 WiFi 流量限额") },
                            placeholder = { Text("如 2048") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            trailingIcon = {
                                Text(
                                    text = "MB",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // 保存限额按钮
                        Button(
                            onClick = { saveLimits() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("保存限额")
                        }
                    }
                }
            }

            // ==================== 5. 数据管理 Section ====================
            item {
                SectionTitle(text = "数据管理")
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 导出数据按钮
                        OutlinedButton(
                            onClick = { exportData() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("导出数据")
                        }

                        // 恢复数据按钮
                        OutlinedButton(
                            onClick = { showRestoreDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("恢复数据")
                        }

                        // 清除历史数据按钮（红色文字）
                        OutlinedButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TrafficRed
                            ),
                            border = BorderStroke(
                                1.dp, TrafficRed.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("清除历史数据")
                        }

                        // 存储使用量
                        Text(
                            text = storageUsed,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }

            // ==================== 6. 外观 Section ====================
            item {
                SectionTitle(text = "外观")
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "主题切换",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // 三选一 SingleChoiceSegmentedButtonRow
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(
                                "light" to "浅色",
                                "dark" to "深色",
                                "system" to "跟随系统"
                            ).forEachIndexed { index, (mode, label) ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index, 3),
                                    selected = themeMode == mode,
                                    onClick = {
                                        themeMode = mode
                                        scope.launch {
                                            DataStoreHelper.setThemeMode(context, mode)
                                        }
                                    },
                                    icon = {}
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }
                }
            }

            // ==================== 7. 关于 Section ====================
            item {
                SectionTitle(text = "关于")
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 版本信息
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "版本信息",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "流量管家 v1.0.0",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.divider
                        )

                        // 隐私声明
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = StatusRootGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "隐私声明",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "纯本地离线，零联网",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.divider
                        )

                        // 开源许可
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "开源许可",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "查看使用的开源库许可信息",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 底部留白
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

// ==================== 辅助组件 ====================

/**
 * Section 标题组件
 * 12sp primary 色
 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )
}

/**
 * 状态 Badge 组件
 */
@Composable
private fun StatusBadge(
    text: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * 设置项 Switch 行
 */
@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

/**
 * 运营商信息数据类
 */
private data class CarrierInfo(
    val carrierName: String,
    val networkType: String,
    val carrierColor: Color
)

/**
 * 获取运营商信息
 */
@SuppressLint("HardwareIds")
@Composable
private fun getCarrierInfo(context: Context): CarrierInfo? {
    return remember {
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm != null && tm.simState == TelephonyManager.SIM_STATE_READY) {
                val operator = tm.simOperator ?: ""
                val carrierName = when {
                    operator.startsWith("46000") || operator.startsWith("46002") ||
                            operator.startsWith("46004") || operator.startsWith("46007") -> "中国移动"
                    operator.startsWith("46001") || operator.startsWith("46006") ||
                            operator.startsWith("46009") -> "中国联通"
                    operator.startsWith("46003") || operator.startsWith("46005") ||
                            operator.startsWith("46011") -> "中国电信"
                    else -> "未知运营商"
                }
                val networkType = when (tm.networkType) {
                    TelephonyManager.NETWORK_TYPE_GPRS,
                    TelephonyManager.NETWORK_TYPE_EDGE,
                    TelephonyManager.NETWORK_TYPE_CDMA,
                    TelephonyManager.NETWORK_TYPE_1xRTT,
                    TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
                    TelephonyManager.NETWORK_TYPE_UMTS,
                    TelephonyManager.NETWORK_TYPE_EVDO_0,
                    TelephonyManager.NETWORK_TYPE_EVDO_A,
                    TelephonyManager.NETWORK_TYPE_HSDPA,
                    TelephonyManager.NETWORK_TYPE_HSUPA,
                    TelephonyManager.NETWORK_TYPE_HSPA,
                    TelephonyManager.NETWORK_TYPE_EVDO_B,
                    TelephonyManager.NETWORK_TYPE_EHRPD,
                    TelephonyManager.NETWORK_TYPE_HSPAP,
                    TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
                    TelephonyManager.NETWORK_TYPE_LTE,
                    TelephonyManager.NETWORK_TYPE_IWLAN -> "4G"
                    TelephonyManager.NETWORK_TYPE_NR -> "5G"
                    else -> ""
                }
                val carrierColor = when {
                    carrierName == "中国移动" -> CarrierCMCC
                    carrierName == "中国联通" -> CarrierCUCC
                    carrierName == "中国电信" -> CarrierCTCC
                    else -> StatusNoneGray
                }
                CarrierInfo(carrierName, networkType, carrierColor)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}