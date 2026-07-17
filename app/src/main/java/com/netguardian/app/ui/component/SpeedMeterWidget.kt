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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.netguardian.app.ui.theme.TrafficBlue
import com.netguardian.app.ui.theme.TrafficCyan
import com.netguardian.app.ui.theme.TrafficGreen
import com.netguardian.app.ui.theme.TrafficOrange
import com.netguardian.app.ui.theme.UploadPurple
import com.netguardian.app.util.TrafficFormatter

/**
 * 网速仪表盘组件
 * 使用Canvas绘制圆形进度弧线展示实时网速
 * 支持下载/上传双速率显示，带平滑动画过渡
 *
 * @param downloadSpeed 当前下载速率（字节/秒）
 * @param uploadSpeed 当前上传速率（字节/秒）
 * @param maxSpeed 最大速率参考值（字节/秒），用于计算进度比例，默认10MB/s
 * @param modifier 布局修饰符
 */
@Composable
fun SpeedMeterWidget(
    downloadSpeed: Long,
    uploadSpeed: Long,
    maxSpeed: Long = 10L * 1024L * 1024L, // 10MB/s
    modifier: Modifier = Modifier
) {
    // 下载速率动画 - 平滑过渡
    val infiniteTransition = rememberInfiniteTransition(label = "downloadPulse")
    val downloadProgress by animateFloatAsState(
        targetValue = (downloadSpeed.toFloat() / maxSpeed).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "downloadProgress"
    )

    // 上传速率动画
    val uploadProgress by animateFloatAsState(
        targetValue = (uploadSpeed.toFloat() / maxSpeed).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "uploadProgress"
    )

    // 脉冲动画（当有活跃流量时）
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulsePhase"
    )

    val isActive = downloadSpeed > 0 || uploadSpeed > 0
    val strokeWidth = 12.dp

    // 使用Box包裹Canvas和文字叠加层
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // 绘制圆形进度弧线
        val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val arcSize = size.minDimension
            val topLeftOffset = Offset(
                x = (size.width - arcSize) / 2f,
                y = (size.height - arcSize) / 2f
            )

            // 1. 绘制底层灰色圆环（轨道）
            drawArc(
                color = surfaceVariant,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeftOffset,
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )

            // 2. 绘制下载速率弧线（主弧线，从135度顺时针）
            if (downloadProgress > 0.001f) {
                val downloadSweep = 270f * downloadProgress
                // 下载速率使用蓝色渐变
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            TrafficBlue,
                            TrafficCyan,
                            TrafficBlue.copy(alpha = 0.8f)
                        ),
                        center = center
                    ),
                    startAngle = 135f,
                    sweepAngle = downloadSweep,
                    useCenter = false,
                    topLeft = topLeftOffset,
                    size = Size(arcSize, arcSize),
                    style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                )
            }

            // 3. 绘制上传速率弧线（外圈，稍细）
            if (uploadProgress > 0.001f) {
                val outerStrokeWidth = (strokeWidth * 0.7f).toPx()
                val outerArcSize = arcSize - strokeWidth.toPx() * 1.6f
                val outerOffset = Offset(
                    x = (size.width - outerArcSize) / 2f,
                    y = (size.height - outerArcSize) / 2f
                )
                val uploadSweep = 270f * uploadProgress
                drawArc(
                    color = UploadPurple,
                    startAngle = 135f,
                    sweepAngle = uploadSweep,
                    useCenter = false,
                    topLeft = outerOffset,
                    size = Size(outerArcSize, outerArcSize),
                    style = Stroke(width = outerStrokeWidth, cap = StrokeCap.Round)
                )
            }

            // 4. 活跃状态脉冲效果
            if (isActive) {
                val pulseAlpha = 0.15f + 0.1f * (1f - pulsePhase)
                drawCircle(
                    color = TrafficBlue.copy(alpha = pulseAlpha),
                    radius = arcSize / 2f * (0.7f + 0.05f * (1f - pulsePhase)),
                    center = center,
                    alpha = pulseAlpha
                )
            }
        }

        // 中心数值和文字叠加层
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 下载速率数值
            Text(
                text = TrafficFormatter.formatSpeed(downloadSpeed),
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                    color = TrafficBlue
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // "下载" 标签
            Text(
                text = "下载",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 上传速率数值
            Text(
                text = TrafficFormatter.formatSpeed(uploadSpeed),
                style = TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                    color = UploadPurple
                ),
                textAlign = TextAlign.Center
            )

            // "上传" 标签
            Text(
                text = "上传",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 简化版仪表盘 - 仅显示单个速率值
 * 用于空间较小的场景
 *
 * @param speed 当前速率（字节/秒）
 * @param label 标签文字
 * @param color 弧线颜色
 * @param modifier 布局修饰符
 */
@Composable
fun MiniSpeedMeter(
    speed: Long,
    label: String,
    color: Color = TrafficBlue,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = (speed.toFloat() / (5L * 1024L * 1024L)).coerceIn(0f, 1f),
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "miniSpeed"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 小型进度弧
        val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
        Canvas(
            modifier = Modifier.size(48.dp)
        ) {
            val arcSize = 48.dp.toPx()
            drawArc(
                color = surfaceVariantColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset.Zero,
                size = Size(arcSize, arcSize),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
            if (progress > 0.001f) {
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = 270f * progress,
                    useCenter = false,
                    topLeft = Offset.Zero,
                    size = Size(arcSize, arcSize),
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = TrafficFormatter.formatSpeed(speed),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


