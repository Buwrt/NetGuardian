package com.netguardian.app.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.sp
import com.netguardian.app.ui.theme.TrafficBlue
import com.netguardian.app.ui.theme.TrafficGreen
import com.netguardian.app.util.TrafficFormatter

/**
 * 流量水平柱状图组件
 * 使用Canvas自绘水平柱状图，展示各应用流量排行
 * 支持移动网络(绿)/WiFi(蓝)双色柱并排显示，带入场动画
 * Y轴最大值自适应，圆角柱条，数值标签在柱条右侧
 * 深色模式颜色在Canvas外部获取
 *
 * @param data 流量图表数据列表
 * @param modifier 布局修饰符
 * @param maxValue 数据最大值（传入用于比例计算）
 */
@Composable
fun BarChart(
    data: List<BarChartData>,
    modifier: Modifier = Modifier,
    maxValue: Long
) {
    if (data.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无流量数据",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // 颜色在Canvas外部获取，确保DrawScope不调用MaterialTheme
    val mobileBarColor = TrafficGreen
    val wifiBarColor = TrafficBlue
    val labelColor = MaterialTheme.colorScheme.onSurface
    val valueColor = MaterialTheme.colorScheme.onSurfaceVariant

    val itemHeight = 36.dp
    val itemSpacing = 4.dp
    val labelWidth = 72.dp
    val valueWidth = 64.dp

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
            Canvas(modifier = Modifier.size(10.dp)) {
                drawRoundRect(
                    color = mobileBarColor,
                    size = Size(10.dp.toPx(), 10.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "移动",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Canvas(modifier = Modifier.size(10.dp)) {
                drawRoundRect(
                    color = wifiBarColor,
                    size = Size(10.dp.toPx(), 10.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "WiFi",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 每个数据项
        data.forEach { item ->
            BarChartRow(
                label = item.label,
                mobileTraffic = item.mobileTraffic,
                wifiTraffic = item.wifiTraffic,
                maxVal = if (maxValue > 0) maxValue else 1L,
                itemHeight = itemHeight,
                itemSpacing = itemSpacing,
                labelWidth = labelWidth,
                valueWidth = valueWidth,
                mobileBarColor = mobileBarColor,
                wifiBarColor = wifiBarColor,
                labelColor = labelColor,
                valueColor = valueColor
            )
        }
    }
}

/**
 * 单行柱状图 - 使用Canvas绘制带动画的水平双柱
 */
@Composable
private fun BarChartRow(
    label: String,
    mobileTraffic: Long,
    wifiTraffic: Long,
    maxVal: Long,
    itemHeight: androidx.compose.ui.unit.Dp,
    itemSpacing: androidx.compose.ui.unit.Dp,
    labelWidth: androidx.compose.ui.unit.Dp,
    valueWidth: androidx.compose.ui.unit.Dp,
    mobileBarColor: Color,
    wifiBarColor: Color,
    labelColor: Color,
    valueColor: Color
) {
    // 动画进度
    val mobileProgress by animateFloatAsState(
        targetValue = (mobileTraffic.toFloat() / maxVal).coerceIn(0f, 1f),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "mobile"
    )
    val wifiProgress by animateFloatAsState(
        targetValue = (wifiTraffic.toFloat() / maxVal).coerceIn(0f, 1f),
        animationSpec = tween(600, delayMillis = 80, easing = FastOutSlowInEasing),
        label = "wifi"
    )

    val totalTraffic = mobileTraffic + wifiTraffic

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight + itemSpacing)
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧标签（应用名）
        Text(
            text = label,
            fontSize = 12.sp,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(labelWidth)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 柱状图区域 - 用Canvas绘制
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(itemHeight)
        ) {
            val chartWidth = size.width
            val barHeight = size.height
            val barSpacing = 2.dp.toPx()
            val singleBarHeight = (barHeight - barSpacing) / 2f
            val cornerRadius = 4.dp.toPx()

            // 移动流量柱（上方，绿色）
            val mobileBarWidth = chartWidth * mobileProgress
            if (mobileBarWidth > 1.dp.toPx()) {
                drawRoundRect(
                    color = mobileBarColor,
                    size = Size(mobileBarWidth, singleBarHeight),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                )
            }

            // WiFi流量柱（下方，蓝色）
            val wifiBarWidth = chartWidth * wifiProgress
            if (wifiBarWidth > 1.dp.toPx()) {
                drawRoundRect(
                    color = wifiBarColor,
                    topLeft = Offset(0f, singleBarHeight + barSpacing),
                    size = Size(wifiBarWidth, singleBarHeight),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 右侧数值标签
        Text(
            text = TrafficFormatter.formatBytes(totalTraffic),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            modifier = Modifier.width(valueWidth)
        )
    }
}

/**
 * 流量条形图数据项
 * 与 ViewModel 中使用的数据结构保持一致
 *
 * @param label 显示标签（应用名称或日期）
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
 * 折线图/柱状图通用数据点
 * 用于新的图表组件
 */
data class ChartData(
    val label: String,
    val mobileValue: Float,
    val wifiValue: Float
)
