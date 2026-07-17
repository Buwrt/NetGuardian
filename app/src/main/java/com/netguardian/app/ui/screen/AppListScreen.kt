package com.netguardian.app.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netguardian.app.model.AppControlState
import com.netguardian.app.ui.component.AppTrafficItem
import com.netguardian.app.util.TrafficFormatter
import com.netguardian.app.viewmodel.AppListViewModel

/**
 * 应用流量列表页面
 * 展示所有已安装应用的流量使用明细
 * 支持搜索过滤、排序切换、下拉刷新、点击导航到控制页
 *
 * @param viewModel AppListViewModel实例
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
    // 收集状态
    val displayApps by viewModel.displayApps.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val totalAppCount by viewModel.totalAppCount.collectAsStateWithLifecycle()
    val activeAppCount by viewModel.activeAppCount.collectAsStateWithLifecycle()
    val totalTraffic by viewModel.totalTraffic.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    // 排序菜单展开状态
    var showSortMenu by remember { mutableStateOf(false) }

    // 下拉刷新状态
    var isPullRefreshing by remember { mutableStateOf(false) }

    // 搜索栏展开状态
    var isSearchExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                AppListTopBar(
                    onBack = onBack,
                    activeAppCount = activeAppCount,
                    totalTraffic = totalTraffic
                )
                // 搜索栏（始终显示，可折叠）
                SearchAndSortBar(
                    searchQuery = searchQuery,
                    isSearchExpanded = isSearchExpanded,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onSearchExpandedChange = { isSearchExpanded = it },
                    sortMode = sortMode,
                    showSortMenu = showSortMenu,
                    onShowSortMenuChange = { showSortMenu = it },
                    onSortModeSelected = { mode ->
                        viewModel.setSortMode(mode)
                        showSortMenu = false
                    }
                )
            }
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isPullRefreshing || isRefreshing,
            onRefresh = {
                isPullRefreshing = true
                viewModel.refresh()
                kotlinx.coroutines.MainScope().launch {
                    kotlinx.coroutines.delay(800)
                    isPullRefreshing = false
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                // 加载中
                isLoading -> {
                    LoadingState()
                }
                // 错误
                errorMessage != null -> {
                    ErrorState(
                        message = errorMessage ?: "未知错误",
                        onRetry = { viewModel.loadAppList() }
                    )
                }
                // 无数据
                displayApps.isEmpty() && searchQuery.isNotBlank() -> {
                    EmptySearchState(query = searchQuery)
                }
                // 正常显示列表
                else -> {
                    AppTrafficList(
                        apps = displayApps,
                        onAppClick = onAppClick
                    )
                }
            }
        }
    }

    // 错误提示Snackbar
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }
}

/**
 * 顶部标题栏
 */
@Composable
private fun AppListTopBar(
    onBack: () -> Unit,
    activeAppCount: Int,
    totalTraffic: Long
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "应用流量",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$activeAppCount 个应用有流量",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "共 ${TrafficFormatter.formatBytes(totalTraffic)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
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
 * 搜索栏和排序栏
 */
@Composable
private fun SearchAndSortBar(
    searchQuery: String,
    isSearchExpanded: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    sortMode: AppListViewModel.SortMode,
    showSortMenu: Boolean,
    onShowSortMenuChange: (Boolean) -> Unit,
    onSortModeSelected: (AppListViewModel.SortMode) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "搜索应用名称...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 排序按钮
            Box {
                IconButton(onClick = { onShowSortMenuChange(true) }) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "排序",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 排序下拉菜单
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { onShowSortMenuChange(false) }
                ) {
                    AppListViewModel.SortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = mode.displayName)
                                    if (mode == sortMode) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "✓",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            },
                            onClick = { onSortModeSelected(mode) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 应用流量列表
 * 使用LazyColumn实现高性能滚动列表，带animateItem动画
 */
@Composable
private fun AppTrafficList(
    apps: List<com.netguardian.app.model.AppTrafficInfo>,
    onAppClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(
            items = apps,
            key = { it.packageName }
        ) { app ->
            AppTrafficItem(
                appTraffic = app,
                onClick = { onAppClick(app.packageName) },
                rank = apps.indexOf(app) + 1
            )
        }
    }
}

/**
 * 加载中状态
 */
@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "加载失败",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

/**
 * 搜索无结果状态
 */
@Composable
private fun EmptySearchState(query: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "未找到匹配应用",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "关键词: \"$query\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
