package com.netguardian.app.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.netguardian.app.ui.theme.DownloadTeal
import com.netguardian.app.ui.theme.MeterLabelTextStyle
import com.netguardian.app.ui.theme.SpeedDisplaySmallStyle
import com.netguardian.app.ui.theme.SpeedDisplayStyle
import com.netguardian.app.ui.theme.TrafficCyan
import com.netguardian.app.ui.theme.TrafficGreen
import com.netguardian.app.ui.theme.UploadPurple
import com.netguardian.app.util.TrafficFormatter

/**
 * 网速仪表盘组件 - 独立可复用
 *
 * 使用 Canvas 绘制 270 度圆弧仪表盘，展示实时下载/上传速率。
 * 外圈弧线表示网速占最大速率的比例，带蓝绿色渐变效果。
 * 深色模式下弧线颜色自动使用亮色变体以保持可视性。
 * 所有动画通过 animateFloatAsState 实现平滑过渡。
 *
 * 设计要点：
 * - 弧线范围 270 度（底部缺口 90 度），起点 135 度
 * - 下载速率弧线使用 trafficGreen -> trafficCyan 渐变
 * - 背景轨道弧线使用 surfaceVariant 色
 * - 弧线端点使用圆形端帽（StrokeCap.Round）
 * - 中心叠加文字层显示速率数值
 *
 * @param downloadSpeed 当前下载速率（字节/秒）
 * @param uploadSpeed 当前上传速率（字节/秒）
 * @param modifier 布局修饰符
 * @param maxSpeed 最大速率参考值（字节/秒），用于计算弧线比例，默认 10MB/s
 */
@Composable
fun SpeedMeterWidget(
    downloadSpeed: Long,
    uploadSpeed: Long,
    modifier: Modifier = Modifier,
    maxSpeed: Long = 10L * 1024L * 1024L // 10MB/s
) {
    // ==================== 在 Composable 上下文中获取所有颜色 ====================
    // 注意：颜色必须在 Composable 上下文中获取，不能传入 Canvas 的 DrawScope
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // 下载弧线渐变色：从绿色到青色
    val arcGradientStart = TrafficGreen
    val arcGradientEnd = TrafficCyan

    // ==================== 动画状态 ====================

    // 下载速率弧线进度（0f ~ 1f），平滑过渡
    val downloadProgress by animateFloatAsState(
        targetValue = (downloadSpeed.toFloat() / maxSpeed).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "downloadProgress"
    )

    // 上传速率弧线进度（0f ~ 1f），平滑过渡
    val uploadProgress by animateFloatAsState(
        targetValue = (uploadSpeed.toFloat() / maxSpeed).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "uploadProgress"
    )

    // 脉冲呼吸动画 - 当有活跃流量时产生呼吸效果
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulsePhase"
    )

    // 是否有活跃流量
    val isActive = downloadSpeed > 0 || uploadSpeed > 0

    // 弧线线宽
    val strokeWidth = 14.dp

    // ==================== 主容器 ====================
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // ==================== Canvas 绘制弧线 ====================
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            // 计算弧线尺寸和居中偏移
            val arcSize = size.minDimension
            val topLeftOffset = Offset(
                x = (size.width - arcSize) / 2f,
                y = (size.height - arcSize) / 2f
            )

            // --- 第1层：底层轨道弧线（270度，surfaceVariant 色） ---
            drawArc(
                color = surfaceVariant,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeftOffset,
                size = Size(arcSize, arcSize),
                style = Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round
                )
            )

            // --- 第2层：下载速率渐变弧线 ---
            if (downloadProgress > 0.005f) {
                val downloadSweep = 270f * downloadProgress
                // 使用线性渐变模拟弧线渐变效果
                // 从起点(135度)到终点，颜色从绿色过渡到青色
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            arcGradientStart,
                            arcGradientEnd,
                            arcGradientStart.copy(alpha = 0.6f)
                        ),
                        center = center
                    ),
                    startAngle = 135f,
                    sweepAngle = downloadSweep,
                    useCenter = false,
                    topLeft = topLeftOffset,
                    size = Size(arcSize, arcSize),
                    style = Stroke(
                        width = strokeWidth.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }

            // --- 第3层：上传速率弧线（内圈，稍细，紫色） ---
            if (uploadProgress > 0.005f) {
                val innerStrokeWidth = (strokeWidth * 0.55f).toPx()
                val innerArcSize = arcSize - strokeWidth.toPx() * 2.2f
                val innerOffset = Offset(
                    x = (size.width - innerArcSize) / 2f,
                    y = (size.height - innerArcSize) / 2f
                )
                val uploadSweep = 270f * uploadProgress
                drawArc(
                    color = UploadPurple,
                    startAngle = 135f,
                    sweepAngle = uploadSweep,
                    useCenter = false,
                    topLeft = innerOffset,
                    size = Size(innerArcSize, innerArcSize),
                    style = Stroke(
                        width = innerStrokeWidth,
                        cap = StrokeCap.Round
                    )
                )
            }

            // --- 第4层：活跃状态脉冲光晕 ---
            if (isActive) {
                val pulseAlpha = 0.08f + 0.07f * (1f - pulsePhase)
                drawCircle(
                    color = DownloadTeal.copy(alpha = pulseAlpha),
                    radius = arcSize / 2f * (0.65f + 0.05f * (1f - pulsePhase)),
                    center = center,
                    alpha = pulseAlpha
                )
            }
        }

        // ==================== 中心文字叠加层 ====================
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 下载速率 - 大号数字（SpeedDisplay 样式）
            Text(
                text = TrafficFormatter.formatSpeed(downloadSpeed),
                style = SpeedDisplayStyle,
                color = onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            // "下载" 标签（MeterLabel 样式）
            Text(
                text = "\u2193 \u4E0B\u8F7D",  // ↓ 下载
                style = MeterLabelTextStyle,
                color = onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 分割线（细横线）
            Box(
                modifier = Modifier
                    .height(1.dp)
                    .fillMaxSize(0.25f)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawLine(
                        color = onSurfaceVariant.copy(alpha = 0.3f),
                        start = Offset.Zero,
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 上传速率 - 中号数字（SpeedDisplaySmall 样式）
            Text(
                text = TrafficFormatter.formatSpeed(uploadSpeed),
                style = SpeedDisplaySmallStyle,
                color = UploadPurple,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            // "上传" 标签（MeterLabel 样式）
            Text(
                text = "\u2191 \u4E0A\u4F20",  // ↑ 上传
                style = MeterLabelTextStyle,
                color = onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}