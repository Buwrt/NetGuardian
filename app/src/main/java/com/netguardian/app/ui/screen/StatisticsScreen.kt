package com.netguardian.app.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netguardian.app.model.TrafficRecord
import com.netguardian.app.ui.component.BarChart
import com.netguardian.app.ui.component.BarChartData
import com.netguardian.app.ui.theme.StatValueTextStyle
import com.netguardian.app.ui.theme.TrafficBlue
import com.netguardian.app.ui.theme.TrafficGreen
import com.netguardian.app.ui.theme.TrafficRed
import com.netguardian.app.util.TrafficFormatter
import com.netguardian.app.viewmodel.StatisticsViewModel
import com.netguardian.app.viewmodel.TrendDataPoint
import kotlinx.coroutines.delay

/**
 * 统计页面 - Statistics Screen
 * 展示流量统计图表和历史流量记录
 * 支持日/周/月维度切换
 *
 * 包含：
 * - 时间维度Tab（日/周/月）
 * - 总流量概览卡片（StatValue大号数字）
 * - 流量趋势折线图（Canvas自绘）
 * - 应用流量排行（水平柱状图）
 * - 历史记录列表
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

    // 上一周期对比百分比（模拟数据，实际从ViewModel获取）
    val mobileTotal = totalRx // 简化，实际应分移动/WiFi
    val comparisonPercent = remember(totalTraffic) { -12.5f } // 模拟值

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
                    // 3. 总流量概览卡片
                    item {
                        TrafficOverviewCard(
                            totalTraffic = totalTraffic,
                            totalRx = totalRx,
                            totalTx = totalTx,
                            comparisonPercent = comparisonPercent,
                            periodName = timePeriod.displayName
                        )
                    }

                    // 4. 流量趋势图（Canvas自绘折线图）
                    item {
                        TrendLineChartCard(trendData = trendData)
                    }

                    // 5. 应用流量排行
                    item {
                        AppTrafficRankingCard(barChartData = barChartData)
                    }

                    // 6. 历史流量记录标题
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "历史记录",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${historyRecords.size} 条记录",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                delay(3000)
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
 * 日/周/月三个Tab，使用Material3默认指示器
 */
@Composable
private fun TimePeriodTabs(
    currentPeriod: StatisticsViewModel.TimePeriod,
    onPeriodSelected: (StatisticsViewModel.TimePeriod) -> Unit
) {
    val selectedIndex = StatisticsViewModel.TimePeriod.entries.indexOf(currentPeriod)

    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        divider = { HorizontalDivider(thickness = 0.dp) }
    ) {
        StatisticsViewModel.TimePeriod.entries.forEach { period ->
            Tab(
                selected = currentPeriod == period,
                onClick = { onPeriodSelected(period) },
                text = {
                    Text(
                        text = period.displayName,
                        fontWeight = if (currentPeriod == period) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            )
        }
    }
}

/**
 * 总流量概览卡片
 * 大号数字显示周期总流量（StatValue样式）
 * 移动流量 + WiFi流量分开，与上一周期对比
 */
@Composable
private fun TrafficOverviewCard(
    totalTraffic: Long,
    totalRx: Long,
    totalTx: Long,
    comparisonPercent: Float,
    periodName: String
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 周期标题
            Text(
                text = when (periodName) {
                    "日" -> "今日总流量"
                    "周" -> "本周总流量"
                    "月" -> "本月总流量"
                    else -> "周期总流量"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 大号数字
            Text(
                text = TrafficFormatter.formatBytes(totalTraffic),
                style = StatValueTextStyle,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 与上一周期对比
            val comparisonColor = if (comparisonPercent >= 0) TrafficRed else TrafficGreen
            val comparisonSign = if (comparisonPercent >= 0) "+" else ""
            Text(
                text = "较上周期 $comparisonSign${String.format("%.1f", comparisonPercent)}%",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = comparisonColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 移动流量 + WiFi流量分开
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 移动流量
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = TrafficGreen.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = "移动流量",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = TrafficFormatter.formatBytes(totalRx),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TrafficGreen,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // WiFi流量
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = TrafficBlue.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = "WiFi流量",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = TrafficFormatter.formatBytes(totalTx),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TrafficBlue,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * 流量趋势折线图卡片
 * 使用Canvas自绘，不用MPAndroidChart
 * 双折线：移动(绿) + WiFi(蓝)，带面积填充
 */
@Composable
private fun TrendLineChartCard(
    trendData: List<TrendDataPoint>
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "流量趋势",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 图例
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 移动网络图例
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(TrafficGreen, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "移动",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                // WiFi图例
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(TrafficBlue, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "WiFi",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (trendData.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无趋势数据",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                TrafficTrendLineChart(
                    trendData = trendData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }
    }
}

/**
 * Canvas自绘流量趋势折线图
 * 双折线（移动绿色 + WiFi蓝色），面积填充，数据点圆点
 */
@Composable
private fun TrafficTrendLineChart(
    trendData: List<TrendDataPoint>,
    modifier: Modifier = Modifier
) {
    // 在外部获取颜色
    val mobileLineColor = TrafficGreen
    val wifiLineColor = TrafficBlue
    val mobileFillColor = TrafficGreen.copy(alpha = 0.15f)
    val wifiFillColor = TrafficBlue.copy(alpha = 0.15f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    val textMeasurer = rememberTextMeasurer()

    // 动画进度
    var animProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animProgress,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "chartAnim"
    )

    LaunchedEffect(trendData) {
        animProgress = 0f
        animProgress = 1f
    }

    Canvas(modifier = modifier) {
        if (trendData.isEmpty()) return@Canvas

        val dataCount = trendData.size
        if (dataCount < 2) return@Canvas

        // 计算区域
        val chartPaddingLeft = 40.dp.toPx()
        val chartPaddingRight = 16.dp.toPx()
        val chartPaddingTop = 8.dp.toPx()
        val chartPaddingBottom = 28.dp.toPx()

        val chartWidth = size.width - chartPaddingLeft - chartPaddingRight
        val chartHeight = size.height - chartPaddingTop - chartPaddingBottom

        // 最大值
        val maxMobile = trendData.maxOf { it.mobileValue.toFloat() }
        val maxWifi = trendData.maxOf { it.wifiValue.toFloat() }
        val maxCombined = maxOf(maxMobile + maxWifi, 1f)
        val maxValue = maxCombined * 1.1f // 留10%空间

        // X轴间距
        val xStep = chartWidth / (dataCount - 1)

        // 绘制Y轴参考线（3条）
        for (i in 1..3) {
            val y = chartPaddingTop + chartHeight * (1f - i / 4f)
            drawLine(
                color = gridColor,
                start = Offset(chartPaddingLeft, y),
                end = Offset(size.width - chartPaddingRight, y),
                strokeWidth = 1.dp.toPx()
            )

            // Y轴刻度值
            val yValue = maxValue * i / 4f
            val yLabel = TrafficFormatter.formatBytes(yValue.toLong())
            val yTextLayout = textMeasurer.measure(yLabel, fontSize = 9.sp)
            drawText(
                textLayoutResult = yTextLayout,
                topLeft = Offset(
                    4.dp.toPx(),
                    y - yTextLayout.size.height / 2
                ),
                color = textColor
            )
        }

        // 绘制X轴日期标签
        trendData.forEachIndexed { index, point ->
            val x = chartPaddingLeft + index * xStep
            val label = point.label.takeLast(5) // MM-DD
            val xTextLayout = textMeasurer.measure(label, fontSize = 9.sp)
            drawText(
                textLayoutResult = xTextLayout,
                topLeft = Offset(
                    x - xTextLayout.size.width / 2,
                    size.height - chartPaddingBottom + 4.dp.toPx()
                ),
                color = textColor
            )
        }

        // 计算数据点坐标
        fun getPointY(value: Float): Float {
            return chartPaddingTop + chartHeight * (1f - (value / maxValue))
        }

        // 构建移动流量路径
        val mobilePoints = trendData.mapIndexed { index, point ->
            Offset(
                x = chartPaddingLeft + index * xStep * animatedProgress,
                y = getPointY(point.mobileValue.toFloat())
            )
        }

        // 构建WiFi流量路径
        val wifiPoints = trendData.mapIndexed { index, point ->
            Offset(
                x = chartPaddingLeft + index * xStep * animatedProgress,
                y = getPointY(point.mobileValue.toFloat() + point.wifiValue.toFloat())
            )
        }

        // 绘制移动流量面积填充
        if (mobilePoints.size >= 2) {
            val mobileAreaPath = Path().apply {
                moveTo(mobilePoints.first().x, chartPaddingTop + chartHeight)
                mobilePoints.forEach { lineTo(it.x, it.y) }
                lineTo(mobilePoints.last().x, chartPaddingTop + chartHeight)
                close()
            }
            drawPath(
                path = mobileAreaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(mobileFillColor, Color.Transparent),
                    startY = chartPaddingTop,
                    endY = chartPaddingTop + chartHeight
                )
            )

            // 移动流量折线
            val mobileLinePath = Path().apply {
                moveTo(mobilePoints.first().x, mobilePoints.first().y)
                for (i in 1 until mobilePoints.size) {
                    lineTo(mobilePoints[i].x, mobilePoints[i].y)
                }
            }
            drawPath(
                path = mobileLinePath,
                color = mobileLineColor,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )

            // 移动流量数据点
            mobilePoints.forEach { point ->
                drawCircle(
                    color = mobileLineColor,
                    radius = 3.dp.toPx(),
                    center = point
                )
                drawCircle(
                    color = Color.White,
                    radius = 1.5.dp.toPx(),
                    center = point
                )
            }
        }

        // 绘制WiFi流量面积填充
        if (wifiPoints.size >= 2) {
            val wifiAreaPath = Path().apply {
                moveTo(wifiPoints.first().x, chartPaddingTop + chartHeight)
                wifiPoints.forEach { lineTo(it.x, it.y) }
                lineTo(wifiPoints.last().x, chartPaddingTop + chartHeight)
                close()
            }
            drawPath(
                path = wifiAreaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(wifiFillColor, Color.Transparent),
                    startY = chartPaddingTop,
                    endY = chartPaddingTop + chartHeight
                )
            )

            // WiFi流量折线
            val wifiLinePath = Path().apply {
                moveTo(wifiPoints.first().x, wifiPoints.first().y)
                for (i in 1 until wifiPoints.size) {
                    lineTo(wifiPoints[i].x, wifiPoints[i].y)
                }
            }
            drawPath(
                path = wifiLinePath,
                color = wifiLineColor,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )

            // WiFi流量数据点
            wifiPoints.forEach { point ->
                drawCircle(
                    color = wifiLineColor,
                    radius = 3.dp.toPx(),
                    center = point
                )
                drawCircle(
                    color = Color.White,
                    radius = 1.5.dp.toPx(),
                    center = point
                )
            }
        }
    }
}

/**
 * 应用流量排行卡片
 * 使用水平柱状图展示各应用流量
 */
@Composable
private fun AppTrafficRankingCard(
    barChartData: List<BarChartData>
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "应用流量排行",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (barChartData.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无排行数据",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val maxValue = barChartData.maxOf { it.mobileTraffic + it.wifiTraffic }
                BarChart(
                    data = barChartData,
                    maxValue = maxValue
                )
            }
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
    val surfaceColor = MaterialTheme.colorScheme.surface
    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(0.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 日期
                Text(
                    text = record.date,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(80.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 包名
                Text(
                    text = record.packageName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 移动流量
                Text(
                    text = TrafficFormatter.formatBytes(record.totalMobile),
                    fontSize = 12.sp,
                    color = TrafficGreen,
                    modifier = Modifier.width(64.dp),
                    textAlign = TextAlign.End
                )

                Spacer(modifier = Modifier.width(4.dp))

                // WiFi流量
                Text(
                    text = TrafficFormatter.formatBytes(record.totalWifi),
                    fontSize = 12.sp,
                    color = TrafficBlue,
                    modifier = Modifier.width(64.dp),
                    textAlign = TextAlign.End
                )

                Spacer(modifier = Modifier.width(4.dp))

                // 总流量
                Text(
                    text = TrafficFormatter.formatBytes(record.totalBytes),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(64.dp),
                    textAlign = TextAlign.End
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 4.dp),
                thickness = 0.5.dp,
                color = dividerColor
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "暂无流量记录",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "开始使用后，流量数据将在此展示",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
