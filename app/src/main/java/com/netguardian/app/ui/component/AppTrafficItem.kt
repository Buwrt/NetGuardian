package com.netguardian.app.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netguardian.app.model.AppTrafficInfo
import com.netguardian.app.ui.theme.TrafficBlue
import com.netguardian.app.ui.theme.TrafficGreen
import com.netguardian.app.util.TrafficFormatter

/**
 * 专业级应用流量列表项组件
 * ElevatedCard 包裹，展示单个应用的流量信息
 *
 * 布局结构：
 * - 左侧：40dp 应用图标（圆角 8dp clip）
 * - 中间：
 *   - 第一行：应用名称 (16sp Medium) + 总流量 (14sp)
 *   - 第二行：移动图标(绿) + 移动流量值 + WiFi图标(蓝) + WiFi流量值
 * - 右侧：进度条 (LinearProgressIndicator, 绿蓝双色)
 *
 * 深色模式适配：卡片使用 cardBackground 色，文字使用 onSurface 色
 *
 * @param appTraffic 应用流量信息
 * @param onClick 点击回调（导航到控制页）
 * @param onLongClick 长按回调（可选，可用于快速操作）
 * @param modifier 布局修饰符
 * @param isBlocked 是否已被屏蔽（影响背景色高亮提示）
 * @param totalLimit 总流量限额（用于进度条计算，0 表示不限）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTrafficItem(
    appTraffic: AppTrafficInfo,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isBlocked: Boolean = false,
    totalLimit: Long = 0L
) {
    // 被屏蔽时的背景色动画
    val cardColor by animateColorAsState(
        targetValue = if (isBlocked) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "cardColor"
    )

    // 进度条比例计算
    val mobileProgress = if (totalLimit > 0) {
        (appTraffic.totalMobile.toFloat() / totalLimit.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val wifiProgress = if (totalLimit > 0) {
        (appTraffic.totalWifi.toFloat() / totalLimit.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val totalProgress = mobileProgress + wifiProgress

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = cardColor
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ==================== 左侧：应用图标（40dp，圆角 8dp） ====================
            AppIcon(
                icon = appTraffic.icon,
                packageName = appTraffic.packageName,
                isBlocked = isBlocked,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            // ==================== 中间：应用信息 ====================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                // 第一行：应用名称 + 总流量
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = appTraffic.appName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = TrafficFormatter.formatBytes(appTraffic.totalBytes),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 第二行：移动流量(绿) + WiFi流量(蓝)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 移动网络流量
                    TrafficTypeLabel(
                        icon = Icons.Default.CellTower,
                        value = TrafficFormatter.formatBytes(appTraffic.totalMobile),
                        color = TrafficGreen
                    )

                    // WiFi 流量
                    TrafficTypeLabel(
                        icon = Icons.Default.Wifi,
                        value = TrafficFormatter.formatBytes(appTraffic.totalWifi),
                        color = TrafficBlue
                    )
                }
            }

            // ==================== 右侧：进度条（绿蓝双色） ====================
            if (totalLimit > 0 && appTraffic.totalBytes > 0) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(40.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { totalProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = TrafficGreen,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { wifiProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = TrafficBlue,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

/**
 * 流量类型标签（图标 + 数值）
 *
 * @param icon 类型图标
 * @param value 格式化后的流量数值
 * @param color 指示颜色
 */
@Composable
private fun TrafficTypeLabel(
    icon: ImageVector,
    value: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 应用图标组件
 * 有图标则显示，否则显示首字母占位图标
 *
 * @param icon 应用图标 Drawable
 * @param packageName 包名（用于生成首字母占位）
 * @param isBlocked 是否被屏蔽
 * @param modifier 布局修饰符
 */
@Composable
private fun AppIcon(
    icon: android.graphics.drawable.Drawable?,
    packageName: String,
    isBlocked: Boolean,
    modifier: Modifier = Modifier
) {
    if (icon != null) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { context ->
                android.widget.ImageView(context).apply {
                    setImageDrawable(icon)
                    clipToOutline = true
                    outlineProvider = android.view.ViewOutlineProvider.BOUNDS
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                }
            },
            modifier = modifier
        )
    } else {
        val initial = packageName
            .split(".")
            .lastOrNull()
            ?.take(1)
            ?.uppercase()
            ?: "?"

        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(8.dp),
            color = if (isBlocked) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (isBlocked) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }
        }
    }
}