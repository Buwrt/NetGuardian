package com.netguardian.app.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.netguardian.app.ui.theme.TrafficBlue
import com.netguardian.app.ui.theme.TrafficGreen
import com.netguardian.app.util.TrafficFormatter

/**
 * 流量条形图组件
 * 使用Canvas绘制水平柱状图，展示各应用流量对比
 * 支持移动网络/WiFi双色柱并排显示，带入场动画
 *
 * @param data 流量图表数据列表
 * @param modifier 布局修饰符
 * @param maxItems 最多显示条目数，默认10
 */
@Composable
fun TrafficBarChart(
    data: List<BarChartData>,
    modifier: Modifier = Modifier,
    maxItems: Int = 10
) {
    if (data.isEmpty()) {
        // 空数据状态
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无流量数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val displayData = data.take(maxItems)
    // 计算最大值用于比例缩放
    val maxValue = displayData.maxOf { it.mobileTraffic + it.wifiTraffic }
    val barHeight = 24.dp
    val barSpacing = 8.dp
    val labelWidth = 80.dp
    val valueWidth = 72.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        // 图例
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 移动网络图例
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .padding(end = 4.dp)
            )
            Canvas(modifier = Modifier.size(8.dp)) {
                drawRoundRect(
                    color = TrafficBlue,
                    size = Size(8.dp.toPx(), 8.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "移动",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(16.dp))

            // WiFi图例
            Canvas(modifier = Modifier.size(8.dp)) {
                drawRoundRect(
                    color = TrafficGreen,
                    size = Size(8.dp.toPx(), 8.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "WiFi",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 柱状图条目
        displayData.forEachIndexed { _, item ->
            BarChartItem(
                label = item.label,
                mobileTraffic = item.mobileTraffic,
                wifiTraffic = item.wifiTraffic,
                maxValue = maxValue,
                barHeight = barHeight,
                barSpacing = barSpacing,
                labelWidth = labelWidth,
                valueWidth = valueWidth
            )
        }
    }
}

/**
 * 单个柱状图条目
 * 使用Canvas绘制带动画的水平双柱
 *
 * @param label 应用名称
 * @param mobileTraffic 移动网络流量（字节）
 * @param wifiTraffic WiFi流量（字节）
 * @param maxValue 数据最大值（用于计算比例）
 * @param barHeight 柱高度
 * @param barSpacing 柱间距
 * @param labelWidth 标签宽度
 * @param valueWidth 数值显示宽度
 */
@Composable
private fun BarChartItem(
    label: String,
    mobileTraffic: Long,
    wifiTraffic: Long,
    maxValue: Long,
    barHeight: androidx.compose.ui.unit.Dp,
    barSpacing: androidx.compose.ui.unit.Dp,
    labelWidth: androidx.compose.ui.unit.Dp,
    valueWidth: androidx.compose.ui.unit.Dp
) {
    val safeMax = if (maxValue > 0) maxValue else 1L

    // 动画：移动流量柱从0增长到目标值
    val mobileAnimProgress by animateFloatAsState(
        targetValue = (mobileTraffic.toFloat() / safeMax).coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = 600,
            delayMillis = 0,
            easing = FastOutSlowInEasing
        ),
        label = "mobileBar"
    )

    // 动画：WiFi流量柱从0增长到目标值
    val wifiAnimProgress by animateFloatAsState(
        targetValue = (wifiTraffic.toFloat() / safeMax).coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = 600,
            delayMillis = 100, // WiFi柱稍微延迟，形成错落感
            easing = FastOutSlowInEasing
        ),
        label = "wifiBar"
    )

    val totalTraffic = mobileTraffic + wifiTraffic

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight + barSpacing)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 应用名称
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(labelWidth)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 柱状图区域
        Row(
            modifier = Modifier
                .weight(1f)
                .height(barHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 移动网络柱（蓝色）
            Canvas(
                modifier = Modifier
                    .weight(mobileAnimProgress.coerceIn(0.1f, 1f))
                    .fillMaxHeight()
            ) {
                val barWidth = size.width
                drawRoundRect(
                    color = TrafficBlue,
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
            }

            // WiFi柱（绿色）
            if (wifiAnimProgress > 0.001f) {
                Canvas(
                    modifier = Modifier
                        .weight(wifiAnimProgress.coerceIn(0.1f, 1f))
                        .fillMaxHeight()
                ) {
                    val barWidth = size.width
                    drawRoundRect(
                        color = TrafficGreen,
                        size = Size(barWidth, size.height),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 总流量数值
        Text(
            text = TrafficFormatter.formatBytes(totalTraffic),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(valueWidth)
        )
    }
}

/**
 * 流量条形图数据项
 *
 * @param label 显示标签（应用名称）
 * @param mobileTraffic 移动网络流量（字节）
 * @param wifiTraffic WiFi流量（字节）
 */
data class BarChartData(
    val label: String,
    val mobileTraffic: Long,
    val wifiTraffic: Long
) {
    /** 总流量 */
    val totalTraffic: Long get() = mobileTraffic + wifiTraffic
}

/**
 * 垂直柱状图 - 用于日/周/月流量趋势展示
 * 显示多天的流量柱，每日包含移动和WiFi双柱
 *
 * @param data 日期流量数据列表
 * @param modifier 布局修饰符
 */
@Composable
fun VerticalBarChart(
    data: List<BarChartData>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val maxValue = data.maxOf { it.totalTraffic }.coerceAtLeast(1L)
    val chartHeight = 200.dp

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // 柱状图绘制区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
        ) {
            // Y轴参考线
            val lineColor = MaterialTheme.colorScheme.outlineVariant
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 绘制3条水平参考线
                for (i in 1..3) {
                    val y = size.height * (1f - i / 4f)
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            // 柱子
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                data.forEach { item ->
                    val mobileRatio = item.mobileTraffic.toFloat() / maxValue
                    val wifiRatio = item.wifiTraffic.toFloat() / maxValue

                    val mobileHeight by animateFloatAsState(
                        targetValue = mobileRatio,
                        animationSpec = tween(500, easing = FastOutSlowInEasing),
                        label = "vMobileBar"
                    )
                    val wifiHeight by animateFloatAsState(
                        targetValue = wifiRatio,
                        animationSpec = tween(500, delayMillis = 50, easing = FastOutSlowInEasing),
                        label = "vWifiBar"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f)
                    ) {
                        // 叠加的柱子区域
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(chartHeight * 0.85f),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            // 移动柱（底部）
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(mobileHeight.coerceAtLeast(0.01f))
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawRoundRect(
                                        color = TrafficBlue,
                                        size = Size(size.width, size.height),
                                        cornerRadius = CornerRadius(3.dp.toPx())
                                    )
                                }
                            }

                            // WiFi柱（叠加在移动柱上方）
                            if (wifiHeight > 0.001f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(wifiHeight.coerceAtLeast(0.01f))
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawRoundRect(
                                            color = TrafficGreen.copy(alpha = 0.8f),
                                            size = Size(size.width, size.height),
                                            cornerRadius = CornerRadius(3.dp.toPx())
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // X轴日期标签
                        Text(
                            text = item.label.takeLast(5), // 显示MM-DD
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
