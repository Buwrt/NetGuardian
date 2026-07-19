package com.netguardian.app.ui.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/**
 * 首次启动权限引导页
 * 用户需要授权所有必要权限后才能进入应用主界面
 */

data class PermissionItem(
    val name: String,
    val description: String,
    val permission: String,
    val settingsAction: String? = null, // 需要跳转设置页的权限
    var isGranted: Boolean = false
)

@Composable
fun PermissionSetupScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // 定义所有需要的权限
    var permissions by remember {
        mutableStateOf(
            listOf(
                PermissionItem(
                    name = "通知权限",
                    description = "用于显示流量超限提醒和监控状态通知",
                    permission = Manifest.permission.POST_NOTIFICATIONS
                ),
                PermissionItem(
                    name = "使用情况访问",
                    description = "用于获取每个应用的精确流量统计数据",
                    permission = "android.permission.PACKAGE_USAGE_STATS",
                    settingsAction = Settings.ACTION_USAGE_ACCESS_SETTINGS
                ),
                PermissionItem(
                    name = "电话状态",
                    description = "用于检测运营商信息和网络类型（SIM卡识别）",
                    permission = Manifest.permission.READ_PHONE_STATE
                ),
                PermissionItem(
                    name = "应用信息",
                    description = "用于获取已安装应用列表和图标",
                    permission = Manifest.permission.QUERY_ALL_PACKAGES
                )
            )
        )
    }

    // 通知权限启动器
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        permissions = permissions.map { p ->
            if (p.permission == Manifest.permission.POST_NOTIFICATIONS) {
                p.copy(isGranted = ContextCompat.checkSelfPermission(context, p.permission) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            } else p
        }
    }

    // 电话状态权限启动器
    val phoneStateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        permissions = permissions.map { p ->
            if (p.permission == Manifest.permission.READ_PHONE_STATE) {
                p.copy(isGranted = ContextCompat.checkSelfPermission(context, p.permission) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            } else p
        }
    }

    // 检查权限状态
    fun checkPermissions() {
        permissions = permissions.map { p ->
            when {
                p.permission == "android.permission.PACKAGE_USAGE_STATS" -> {
                    // PACKAGE_USAGE_STATS 需要通过 AppOpsManager 检查
                    try {
                        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
                        val mode = appOps?.checkOpNoThrow(
                            android.app.AppOpsManager.OP_GET_USAGE_STATS,
                            android.os.Process.myUid(),
                            context.packageName
                        )
                        p.copy(isGranted = mode == android.app.AppOpsManager.MODE_ALLOWED)
                    } catch (e: Exception) {
                        p.copy(isGranted = false)
                    }
                }
                p.permission == Manifest.permission.QUERY_ALL_PACKAGES -> {
                    // QUERY_ALL_PACKAGES 在 Android 11+ 是自动授予的
                    p.copy(isGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                }
                else -> {
                    p.copy(isGranted = ContextCompat.checkSelfPermission(context, p.permission) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                }
            }
        }
    }

    // 初始检查
    LaunchedEffect(Unit) {
        checkPermissions()
    }

    val allGranted = permissions.all { it.isGranted }

    // 当全部授权后自动进入
    LaunchedEffect(allGranted) {
        if (allGranted) {
            kotlinx.coroutines.delay(500) // 延迟500ms让用户看到所有绿勾
            onAllGranted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("权限设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // 应用图标 + 标题
            Text(
                text = "流量管家",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "首次使用需要授权以下权限",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 权限列表
            permissions.forEachIndexed { index, perm ->
                PermissionCard(
                    permission = perm,
                    onRequestPermission = {
                        if (perm.permission == Manifest.permission.POST_NOTIFICATIONS) {
                            notificationLauncher.launch(perm.permission)
                        } else if (perm.permission == Manifest.permission.READ_PHONE_STATE) {
                            phoneStateLauncher.launch(perm.permission)
                        } else if (perm.settingsAction != null) {
                            // 跳转到系统设置
                            val intent = Intent(perm.settingsAction).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }
                )

                if (index < permissions.size - 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 进入应用按钮
            Button(
                onClick = {
                    checkPermissions()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = allGranted,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (allGranted)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (allGranted)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    if (allGranted) Icons.Default.CheckCircle else Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (allGranted) "进入应用" else "请先完成权限授权",
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (allGranted) "所有权限已授权完成" else "提示：使用情况访问需要手动在设置中开启",
                style = MaterialTheme.typography.bodySmall,
                color = if (allGranted)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    permission: PermissionItem,
    onRequestPermission: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (permission.isGranted) Icons.Default.CheckCircle else Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (permission.isGranted)
                        Color(0xFF34C759) // green
                    else
                        MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 权限信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = permission.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = permission.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 操作按钮
            if (!permission.isGranted) {
                FilledTonalButton(
                    onClick = onRequestPermission,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("授权")
                }
            } else {
                Text(
                    text = "已授权",
                    color = Color(0xFF34C759),
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
