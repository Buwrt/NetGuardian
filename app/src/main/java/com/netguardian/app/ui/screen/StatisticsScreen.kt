package com.netguardian.app.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netguardian.app.model.TrafficRecord
import com.netguardian.app.ui.component.*
import com.netguardian.app.ui.theme.*
import com.netguardian.app.util.TrafficFormatter
import com.netguardian.app.viewmodel.StatisticsViewModel
import com.netguardian.app.viewmodel.TrendDataPoint

/**
 * 统计页面 - Statistics Screen
 * 展示流量统计图表和历史流量记录
 * 支持日/周/月维度切换
 *
 * @param viewModel StatisticsViewModel实例
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel = viewModel()
) {
    // 收集状态
    val timePeriod by viewModel.timePeriod.collectAsStateWithLifecycle()
    val totalRx by viewModel.totalRx.collectAsStateWithLifecycle()
    val totalTx by viewModel.totalTx.collectAsStateWithLifecycle()
    val totalTraffic by viewModel.totalTraffic.collectAsStateWithLifecycle()
    val barChartData by viewModel.barChartData.collectAsStateWithLifecycle()
    val trendData by viewModel.trendData.collectAsStateWithLifecycle()
    val historyRecords by viewModel.historyRecords.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            StatisticsTopBar()
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
            ) {
                // 1. 时间维度Tab切换
                TimePeriodTabs(
                    currentPeriod = timePeriod,
                    onPeriodSelected = { viewModel.setTimePeriod(it) }
                )

                // 2. 可滚动内容
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 3. 流量汇总卡片
                    item {
                        TrafficSummaryCard(
                            totalRx = totalRx,
                            totalTx = totalTx,
                            totalTraffic = totalTraffic
                        )
                    }

                    // 4. 流量趋势图表
                    item {
                        TrendChartCard(trendData = trendData)
                    }

                    // 5. 应用流量对比柱状图
                    item {
                        AppTrafficComparisonCard(barChartData = barChartData)
                    }

                    // 6. 历史流量明细标题
                    item {
                        Text(
                            text = "历史流量明细",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // 7. 历史流量记录列表
                    if (historyRecords.isEmpty()) {
                        item {
                            EmptyHistoryState()
                        }
                    } else {
                        items(
                            items = historyRecords,
                            key = { "${it.packageName}_${it.date}" }
                        ) { record ->
                            HistoryRecordItem(record = record)
                        }
                    }

                    // 底部留白
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        // 错误提示
        errorMessage?.let {
            LaunchedEffect(it) {
                kotlinx.coroutines.delay(3000)
                viewModel.clearError()
            }
            Snackbar(modifier = Modifier.padding(16.dp)) {
                Text(it)
            }
        }
    }
}

/**
 * 顶部标题栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatisticsTopBar() {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "流量统计",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "流量趋势与分析",
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
 * 时间维度Tab切换组件
 * 日/周/月三个Tab
 */
@Composable
private fun TimePeriodTabs(
    currentPeriod: StatisticsViewModel.TimePeriod,
    onPeriodSelected: (StatisticsViewModel.TimePeriod) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp
    ) {
        ScrollableTabRow(
            selectedTabIndex = StatisticsViewModel.TimePeriod.entries.indexOf(currentPeriod),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 16.dp,
            divider = {},
            indicator = { tabPositions ->
                if (tabPositions.isNotEmpty()) {
                    val selectedIndex = StatisticsViewModel.TimePeriod.entries.indexOf(currentPeriod)
                    val selectedTab = tabPositions[selectedIndex]
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val indicatorWidth = placeable.width
                            layout(constraints.maxWidth, placeable.height) {
                                placeable.place(
                                    x = (selectedTab.left.roundToPx() + selectedTab.right.roundToPx() - indicatorWidth) / 2,
                                    y = 0
                                )
                            }
                        },
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) {
            StatisticsViewModel.TimePeriod.entries.forEach { period ->
                Tab(
                    selected = currentPeriod == period,
                    onClick = { onPeriodSelected(period) },
                    text = {
                        Text(
                            text = period.displayName,
                            fontWeight = if (currentPeriod == period) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }
    }
}

/**
 * 流量汇总卡片
 * 显示当前周期的上传/下载/总流量
 */
@Composable
private fun TrafficSummaryCard(
    totalRx: Long,
    totalTx: Long,
    totalTraffic: Long
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "流量汇总",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 总流量（大字显示）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = TrafficFormatter.formatBytes(totalTraffic),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "总流量",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 下载/上传分列
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 下载
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TrafficBlue.copy(alpha = 0.1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = TrafficFormatter.formatBytes(totalRx),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TrafficBlue
                            )
                            Text(
                                text = "下载",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 上传
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = UploadPurple.copy(alpha = 0.1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = TrafficFormatter.formatBytes(totalTx),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = UploadPurple
                            )
                            Text(
                                text = "上传",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 趋势图表卡片
 * 使用垂直柱状图显示流量趋势
 */
@Composable
private fun TrendChartCard(
    trendData: List<TrendDataPoint>
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "流量趋势",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 图例
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                // 移动网络图例
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = TrafficBlue
                ) {}
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "移动",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                // WiFi图例
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = TrafficGreen
                ) {}
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "WiFi",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (trendData.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无趋势数据",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 将趋势数据转换为柱状图数据
                val chartData = trendData.map {
                    BarChartData(
                        label = it.label,
                        mobileTraffic = it.mobileValue,
                        wifiTraffic = it.wifiValue
                    )
                }
                VerticalBarChart(data = chartData)
            }
        }
    }
}

/**
 * 应用流量对比柱状图卡片
 * 使用水平柱状图对比各应用流量
 */
@Composable
private fun AppTrafficComparisonCard(
    barChartData: List<BarChartData>
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "应用流量对比",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            TrafficBarChart(
                data = barChartData,
                maxItems = 8
            )
        }
    }
}

/**
 * 历史流量记录列表项
 */
@Composable
private fun HistoryRecordItem(
    record: TrafficRecord
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 日期
            Column(modifier = Modifier.width(72.dp)) {
                Text(
                    text = record.date,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 包名
            Text(
                text = record.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 移动流量
            Text(
                text = TrafficFormatter.formatBytes(record.totalMobile),
                style = MaterialTheme.typography.labelSmall,
                color = TrafficBlue,
                modifier = Modifier.width(72.dp)
            )

            // WiFi流量
            Text(
                text = TrafficFormatter.formatBytes(record.totalWifi),
                style = MaterialTheme.typography.labelSmall,
                color = TrafficGreen,
                modifier = Modifier.width(72.dp)
            )

            // 总流量
            Text(
                text = TrafficFormatter.formatBytes(record.totalBytes),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(72.dp)
            )
        }
    }
}

/**
 * 空历史记录状态
 */
@Composable
private fun EmptyHistoryState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "暂无历史流量记录",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
