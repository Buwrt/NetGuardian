package com.netguardian.app.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.netguardian.app.model.AppTrafficInfo
import com.netguardian.app.ui.theme.TrafficBlue
import com.netguardian.app.ui.theme.TrafficGreen
import com.netguardian.app.util.TrafficFormatter

/**
 * 应用流量列表项组件
 * Material3 Card风格的列表项，展示单个应用的流量信息
 * 包含应用图标、名称、移动流量、WiFi流量和总流量
 *
 * @param appTraffic 应用流量信息
 * @param onClick 点击回调（导航到控制页）
 * @param onLongClick 长按回调（可选，可用于快速操作）
 * @param modifier 布局修饰符
 * @param isBlocked 是否已被屏蔽（影响背景色）
 * @param rank 排名（可选，显示在最左侧）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTrafficItem(
    appTraffic: AppTrafficInfo,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isBlocked: Boolean = false,
    rank: Int? = null
) {
    // 被屏蔽时的背景色动画
    val cardColor by animateColorAsState(
        targetValue = if (isBlocked) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "cardColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isBlocked) 0.dp else 1.dp
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 排名（可选）
            if (rank != null) {
                Text(
                    text = "#${rank}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // 应用图标
            AppIcon(
                icon = appTraffic.icon,
                packageName = appTraffic.packageName,
                isBlocked = isBlocked,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 应用名称（占左侧）
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = appTraffic.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isBlocked) {
                    Text(
                        text = "已屏蔽网络",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 流量信息列（移动 + WiFi + 总计）
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(min = 100.dp)
            ) {
                // 移动网络流量
                if (appTraffic.totalMobile > 0) {
                    TrafficValueText(
                        label = "移动",
                        value = TrafficFormatter.formatBytes(appTraffic.totalMobile),
                        color = TrafficBlue
                    )
                }

                // WiFi流量
                if (appTraffic.totalWifi > 0) {
                    TrafficValueText(
                        label = "WiFi",
                        value = TrafficFormatter.formatBytes(appTraffic.totalWifi),
                        color = TrafficGreen
                    )
                }

                // 总流量（加粗显示）
                Text(
                    text = TrafficFormatter.formatBytes(appTraffic.totalBytes),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * 应用图标组件
 * 如果有图标则显示，否则显示首字母占位图标
 *
 * @param icon 应用图标Drawable
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
        // 使用AndroidView嵌入原生ImageView显示Drawable
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { context ->
                android.widget.ImageView(context).apply {
                    setImageDrawable(icon)
                    clipToOutline = true
                    outlineProvider = android.view.ViewOutlineProvider.BOUNDS
                }
            },
            modifier = modifier.clip(CircleShape)
        )
    } else {
        // 无图标时显示首字母占位
        val initial = packageName
            .split(".")
            .lastOrNull()
            ?.take(1)
            ?.uppercase()
            ?: "?"

        Surface(
            modifier = modifier,
            shape = CircleShape,
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

/**
 * 流量标签+数值文本组合
 * 用于列表项中分类型显示流量
 *
 * @param label 类型标签（"移动"/"WiFi"）
 * @param value 格式化后的流量数值
 * @param color 指示颜色
 */
@Composable
private fun TrafficValueText(
    label: String,
    value: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 彩色圆点
        Surface(
            modifier = Modifier.size(6.dp),
            shape = CircleShape,
            color = color
        ) {}
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label $value",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 流量值格式化显示组件
 * 将字节数自动转换为合适单位并显示
 *
 * @param bytes 字节数
 * @param label 可选标签前缀
 * @param style 文字样式
 */
@Composable
fun TrafficBytesDisplay(
    bytes: Long,
    label: String? = null,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium
) {
    val text = if (label != null) {
        "$label ${TrafficFormatter.formatBytes(bytes)}"
    } else {
        TrafficFormatter.formatBytes(bytes)
    }

    Text(
        text = text,
        style = style
    )
}
