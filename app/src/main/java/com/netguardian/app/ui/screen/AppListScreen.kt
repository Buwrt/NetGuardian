package com.netguardian.app.ui.screen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.ViewOutlineProvider
import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netguardian.app.model.AppTrafficInfo
import com.netguardian.app.ui.theme.TrafficBlue
import com.netguardian.app.ui.theme.TrafficGreen
import com.netguardian.app.ui.theme.TrafficValueTextStyle

import com.netguardian.app.util.TrafficFormatter
import com.netguardian.app.viewmodel.AppListViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 应用流量列表页面 - 全面重写
 * Material3 专业级设计，深色模式完美适配
 *
 * 功能：
 * - 顶栏：标题 + 搜索按钮 + 排序按钮
 * - 搜索栏：带图标的 OutlinedTextField，实时过滤
 * - 排序：Chip 芯片选择器（按流量/按名称/按移动/按WiFi）
 * - 列表：LazyColumn + ElevatedCard 卡片布局
 * - 每项：应用图标 + 名称 + 流量值 + 百分比条 + 右箭头
 * - 零流量应用显示"暂无数据"
 * - animateItem 入场动画
 * - 空状态 + 底部统计
 *
 * @param viewModel AppListViewModel 实例
 * @param onAppClick 点击应用项的回调，传递包名
 * @param onBack 返回按钮回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    viewModel: AppListViewModel = viewModel(),
    onAppClick: (String) -> Unit = {},
    onBack: () -> Unit = {}
) {
    // ==================== 收集 ViewModel 状态 ====================
    val displayApps by viewModel.displayApps.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val totalAppCount by viewModel.totalAppCount.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    // 搜索栏展开状态
    var isSearchExpanded by remember { mutableStateOf(false) }

    // 刷新动画状态
    var isLocalRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 触发刷新
    fun doRefresh() {
        if (isLocalRefreshing || isRefreshing) return
        isLocalRefreshing = true
        viewModel.refresh()
        scope.launch {
            delay(800)
            isLocalRefreshing = false
        }
    }

    // 错误自动清除
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            delay(3000)
            viewModel.clearError()
        }
    }

    // ==================== 主布局 ====================
    Scaffold(
        topBar = {
            // 顶栏：标题 + 搜索/排序/刷新按钮
            TopAppBar(
                title = {
                    Text(
                        text = "应用流量",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    // 返回按钮
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // 搜索切换按钮
                    IconButton(onClick = {
                        isSearchExpanded = !isSearchExpanded
                        if (!isSearchExpanded) viewModel.clearSearch()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = if (isSearchExpanded) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    // 刷新按钮
                    IconButton(
                        onClick = ::doRefresh,
                        enabled = !isLocalRefreshing && !isRefreshing
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ==================== 搜索栏（可折叠） ====================
            AnimatedVisibility(
                visible = isSearchExpanded,
                enter = fadeIn(animationSpec = tween(200)) +
                        slideInVertically(
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        ) { -it },
                exit = fadeOut(animationSpec = tween(200))
            ) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    onClear = { viewModel.clearSearch() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // ==================== 排序芯片选择器 ====================
            SortChipRow(
                currentMode = sortMode,
                onModeSelected = { viewModel.setSortMode(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // 水平分割线
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 0.5.dp
            )

            // ==================== 列表 / 状态区 ====================
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    // 加载中
                    isLoading -> {
                        LoadingState()
                    }

                    // 错误状态
                    errorMessage != null -> {
                        ErrorState(
                            message = errorMessage ?: "未知错误",
                            onRetry = { viewModel.loadAppList() }
                        )
                    }

                    // 无应用（可能未授权）
                    displayApps.isEmpty() && searchQuery.isBlank() -> {
                        EmptyPermissionState()
                    }

                    // 搜索无结果
                    displayApps.isEmpty() && searchQuery.isNotBlank() -> {
                        EmptySearchState(query = searchQuery)
                    }

                    // 正常列表
                    else -> {
                        AppTrafficList(
                            apps = displayApps,
                            totalAppCount = totalAppCount,
                            onAppClick = onAppClick
                        )
                    }
                }

                // 顶部刷新指示器
                if (isLocalRefreshing || isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

// ===================================================================================
// 搜索栏组件
// ===================================================================================

/**
 * 搜索栏
 * 带搜索图标的 OutlinedTextField，支持实时过滤和清除
 *
 * @param query 当前搜索关键词
 * @param onQueryChange 搜索关键词变更回调
 * @param onClear 清除搜索回调
 * @param modifier 布局修饰符
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = "搜索应用名称或包名...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "搜索图标",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            // 有输入内容时显示清除按钮
            if (query.isNotBlank()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "清除搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

// ===================================================================================
// 排序芯片选择器
// ===================================================================================

/**
 * 排序芯片行
 * 使用 FilterChip 显示所有排序方式，选中状态高亮
 *
 * @param currentMode 当前排序模式
 * @param onModeSelected 选择排序模式的回调
 * @param modifier 布局修饰符
 */
@Composable
private fun SortChipRow(
    currentMode: AppListViewModel.SortMode,
    onModeSelected: (AppListViewModel.SortMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 遍历所有排序模式，生成芯片
        AppListViewModel.SortMode.entries.forEach { mode ->
            val isSelected = mode == currentMode

            FilterChip(
                selected = isSelected,
                onClick = { onModeSelected(mode) },
                label = {
                    Text(
                        text = mode.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    // 未选中状态
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    enabled = true,
                    selected = isSelected
                )
            )
        }
    }
}

// ===================================================================================
// 应用流量列表
// ===================================================================================

/**
 * 应用流量列表
 * LazyColumn 实现高性能滚动，带 animateItem 入场动画和底部统计
 *
 * @param apps 过滤排序后的应用列表
 * @param totalAppCount 总应用数
 * @param onAppClick 点击应用项的回调
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTrafficList(
    apps: List<AppTrafficInfo>,
    totalAppCount: Int,
    onAppClick: (String) -> Unit
) {
    // 计算最大流量值（用于百分比条）
    val maxTraffic = apps.maxOfOrNull { it.totalBytes } ?: 1L

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 80.dp  // 底部留出空间显示统计
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 列表项
        items(
            items = apps,
            key = { it.packageName }
        ) { app ->
            ElevatedCard(
                onClick = { onAppClick(app.packageName) },
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                AppTrafficCardItem(
                    app = app,
                    maxTraffic = maxTraffic
                )
            }
        }

        // 底部统计信息
        item(key = "footer") {
            FooterInfo(appCount = totalAppCount)
        }
    }
}

// ===================================================================================
// 单个应用流量卡片内容
// ===================================================================================

/**
 * 应用流量卡片内容
 * 布局：图标 | 名称+流量详情 | 总流量+箭头
 *
 * @param app 应用流量信息
 * @param maxTraffic 列表中最大流量（用于百分比条）
 */
@Composable
private fun AppTrafficCardItem(
    app: AppTrafficInfo,
    maxTraffic: Long
) {
    // 是否有流量数据
    val hasTraffic = app.totalBytes > 0

    // 流量百分比
    val trafficPercentage = if (maxTraffic > 0) {
        (app.totalBytes.toFloat() / maxTraffic.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ==================== 左侧：应用图标（40dp，圆角裁剪） ====================
        AppIconRounded(
            icon = app.icon,
            packageName = app.packageName,
            modifier = Modifier.size(40.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // ==================== 中间：应用名称 + 流量详情 ====================
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // 应用名称（16sp bold）
            Text(
                text = app.appName,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (hasTraffic) {
                // 移动流量（绿色）+ WiFi流量（蓝色）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 移动网络流量
                    TrafficLabelValue(
                        label = "移动",
                        bytes = app.totalMobile,
                        color = TrafficGreen
                    )

                    // WiFi流量
                    TrafficLabelValue(
                        label = "WiFi",
                        bytes = app.totalWifi,
                        color = TrafficBlue
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 百分比进度条
                LinearProgressIndicator(
                    progress = { trafficPercentage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
            } else {
                // 零流量应用显示"暂无数据"
                Text(
                    text = "暂无数据",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // ==================== 右侧：总流量 + 箭头 ====================
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            if (hasTraffic) {
                // 总流量数值（TrafficValue 样式）
                Text(
                    text = TrafficFormatter.formatBytes(app.totalBytes),
                    style = TrafficValueTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }

            // 右箭头图标
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "查看详情",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .padding(top = if (hasTraffic) 2.dp else 0.dp)
                    .size(20.dp)
            )
        }
    }
}

// ===================================================================================
// 流量标签+数值组合
// ===================================================================================

/**
 * 带彩色圆点的流量标签+数值
 * 格式："● 移动 xx.x MB" 或 "● WiFi xx.x MB"
 *
 * @param label 类型标签
 * @param bytes 字节数
 * @param color 指示颜色
 */
@Composable
private fun TrafficLabelValue(
    label: String,
    bytes: Long,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 彩色圆点
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Spacer(modifier = Modifier.width(4.dp))
        // 标签
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(2.dp))
        // 精确到 MB 的数值
        Text(
            text = if (bytes > 0) formatMbShort(bytes) else "0",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * 将字节数格式化为短 MB 字符串
 * 如：1234567 -> "1.2 MB"，小于 1MB 时显示 KB
 *
 * @param bytes 字节数
 * @return 格式化字符串
 */
private fun formatMbShort(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format("%.1f MB", mb)
    } else {
        val kb = bytes.toDouble() / 1024.0
        if (kb >= 1.0) {
            String.format("%.0f KB", kb)
        } else {
            "${bytes} B"
        }
    }
}

// ===================================================================================
// 应用图标（圆角裁剪）
// ===================================================================================

/**
 * 圆角应用图标
 * 有图标时使用 AndroidView + RoundedCornerShape 裁剪
 * 无图标时显示首字母占位
 *
 * @param icon 应用图标 Drawable
 * @param packageName 包名（用于生成首字母占位）
 * @param modifier 布局修饰符
 */
@Composable
private fun AppIconRounded(
    icon: android.graphics.drawable.Drawable?,
    packageName: String,
    modifier: Modifier = Modifier
) {
    if (icon != null) {
        // 使用 AndroidView 嵌入原生 ImageView 显示 Drawable
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    setImageDrawable(icon)
                    clipToOutline = true
                    outlineProvider = ViewOutlineProvider.BOUNDS
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
            },
            modifier = modifier.clip(RoundedCornerShape(10.dp))
        )
    } else {
        // 无图标时显示首字母占位
        val initial = packageName
            .split(".")
            .lastOrNull()
            ?.take(1)
            ?.uppercase()
            ?: "?"

        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// ===================================================================================
// 状态页面
// ===================================================================================

/**
 * 加载中状态
 * 居中显示加载指示器和提示文字
 */
@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "正在加载应用列表...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 错误状态
 * 显示错误信息 + 重试按钮
 *
 * @param message 错误信息
 * @param onRetry 重试回调
 */
@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "加载失败",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("重试")
            }
        }
    }
}

/**
 * 空状态 - 未检测到应用
 * 当列表为空且非搜索状态时显示，提示用户检查权限
 */
@Composable
private fun EmptyPermissionState() {
    val context = LocalContext.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 手机图标
            Icon(
                imageVector = Icons.Default.Smartphone,
                contentDescription = "无应用",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            // 主提示文字
            Text(
                text = "未检测到应用",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            // 副提示文字
            Text(
                text = "请检查权限",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            // 引导按钮1：跳转到应用使用情况设置
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("检查权限设置")
            }
            Spacer(modifier = Modifier.height(8.dp))
            // 引导按钮2：跳转到使用情况访问设置
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    context.startActivity(intent)
                }
            ) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("打开使用情况访问")
            }
        }
    }
}

/**
 * 搜索无结果状态
 *
 * @param query 搜索关键词
 */
@Composable
private fun EmptySearchState(query: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "无搜索结果",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "未找到匹配应用",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "关键词: \"$query\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

// ===================================================================================
// 底部统计信息
// ===================================================================================

/**
 * 列表底部统计信息
 * 显示"共 N 个应用"
 *
 * @param appCount 应用总数
 */
@Composable
private fun FooterInfo(appCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 分割线
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            thickness = 0.5.dp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "共 $appCount 个应用",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ==================== 导入补充说明 ====================
// cardBackground 扩展属性定义在 Theme.kt 中
// 颜色常量 TrafficGreen, TrafficBlue 定义在 Color.kt 中