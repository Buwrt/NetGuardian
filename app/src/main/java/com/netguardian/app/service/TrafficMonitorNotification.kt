package com.netguardian.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.netguardian.app.R
import com.netguardian.app.model.NetworkSpeedInfo
import com.netguardian.app.ui.MainActivity
import com.netguardian.app.util.Constants
import com.netguardian.app.util.TrafficFormatter

/**
 * 流量监控通知管理工具类
 * 负责创建通知渠道、构建前台服务常驻通知、显示限额超限提醒弹窗等
 *
 * 通知功能：
 * 1. 创建"流量监控"通知渠道（NOTIFICATION_CHANNEL_MONITOR）
 * 2. 构建"流量监控中"常驻通知，显示实时网速
 * 3. 提供"停止监控"和"打开应用"操作按钮
 * 4. 点击通知内容区域打开MainActivity
 * 5. 显示限额超限提醒通知
 */
object TrafficMonitorNotification {

    /** 通知渠道描述 - 流量监控 */
    private const val CHANNEL_MONITOR_DESC = "流量监控服务常驻通知，显示实时网速信息"

    /** 通知渠道描述 - 流量告警 */
    private const val CHANNEL_ALERT_DESC = "流量超限告警通知"

    // ==================== 通知渠道创建 ====================

    /**
     * 创建通知渠道
     * 需要在Android 8.0（Oreo）及以上版本调用
     * 应在应用启动时或服务启动前调用
     *
     * @param context 上下文
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        // 流量监控通知渠道 - 低重要性，不发出声音，用于前台服务常驻通知
        val monitorChannel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_MONITOR,
            "流量监控",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = CHANNEL_MONITOR_DESC
            // 前台服务通知不显示气泡、不发出声音
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(monitorChannel)

        // 流量告警通知渠道 - 高重要性，发出声音和振动
        val alertChannel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ALERT,
            "流量告警",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_ALERT_DESC
            setShowBadge(true)
            enableLights(true)
        }
        notificationManager.createNotificationChannel(alertChannel)
    }

    // ==================== 常驻监控通知 ====================

    /**
     * 构建流量监控常驻通知
     * 显示当前实时网速（下载/上传），提供操作按钮
     *
     * @param context 上下文
     * @param speed 当前网速信息，为null时显示"未检测到网络"
     * @return 构建好的Notification对象
     */
    fun buildMonitoringNotification(context: Context, speed: NetworkSpeedInfo?): Notification {
        // 根据网速信息构建通知内容文本
        val contentText = if (speed != null && speed.hasNetwork) {
            val downStr = TrafficFormatter.formatSpeed(speed.downloadSpeed)
            val upStr = TrafficFormatter.formatSpeed(speed.uploadSpeed)
            "${getNetworkTypeLabel(speed.networkType)} | \u2193 $downStr  \u2191 $upStr"
        } else {
            "未检测到网络连接"
        }

        // 构建大文本样式内容（展开通知后显示详细信息）
        val bigTextStyle = NotificationCompat.BigTextStyle()
        if (speed != null && speed.hasNetwork) {
            bigTextStyle.bigText(
                "实时网速\n" +
                "下载: ${TrafficFormatter.formatSpeed(speed.downloadSpeed)}\n" +
                "上传: ${TrafficFormatter.formatSpeed(speed.uploadSpeed)}\n" +
                "网络: ${getNetworkTypeLabel(speed.networkType)}\n" +
                "时间: ${TrafficFormatter.formatTime(speed.timestamp)}"
            )
            bigTextStyle.setSummaryText(contentText)
        } else {
            bigTextStyle.bigText("当前无网络连接")
        }

        // 点击通知内容区域 -> 打开MainActivity
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "打开应用"按钮
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "停止监控"按钮 -> 发送停止广播给服务
        val stopIntent = Intent(context, TrafficMonitorService::class.java).apply {
            action = Constants.ACTION_STOP_MONITOR
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_MONITOR)
            .setSmallIcon(android.R.drawable.ic_menu_manage) // 使用系统图标作为小图标
            .setContentTitle("流量监控中")
            .setContentText(contentText)
            .setStyle(bigTextStyle)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true) // 常驻通知，不可滑动删除
            .setSilent(true) // 静默更新，不发出提示音
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .addAction(
                android.R.drawable.ic_media_pause,
                "停止监控",
                stopPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_set_as,
                "打开应用",
                openAppPendingIntent
            )
            .build()
    }

    // ==================== 限额超限提醒通知 ====================

    /**
     * 显示流量限额超限提醒通知
     * 当应用流量超过设定的限额时弹出提醒
     *
     * @param context 上下文
     * @param appName 应用名称
     * @param packageName 应用包名
     * @param usedBytes 已用流量（字节）
     * @param limitBytes 限额（字节）
     * @param networkType 超限的网络类型："mobile" / "wifi" / "total"
     * @param notificationId 通知ID，确保每个应用的通知ID唯一
     */
    fun showLimitExceedNotification(
        context: Context,
        appName: String,
        packageName: String,
        usedBytes: Long,
        limitBytes: Long,
        networkType: String,
        notificationId: Int
    ) {
        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        val networkLabel = when (networkType) {
            "mobile" -> "移动网络"
            "wifi" -> "WiFi"
            else -> "总流量"
        }

        val contentText = "$appName 的${networkLabel}流量已超限！已用 ${TrafficFormatter.formatBytes(usedBytes)}，限额 ${TrafficFormatter.formatBytes(limitBytes)}"

        // 点击通知打开应用
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("target_package", packageName)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("流量超限提醒")
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$contentText\n\n请前往应用详情查看并处理。")
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // 点击后自动消失
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(
            Constants.NOTIFICATION_ID_ALERT_PREFIX + notificationId,
            notification
        )
    }

    // ==================== 偷跑检测告警通知 ====================

    /**
     * 显示偷跑流量检测告警通知
     *
     * @param context 上下文
     * @param appName 应用名称
     * @param packageName 应用包名
     * @param leakedBytes 偷跑的流量字节数
     */
    fun showStealthyTrafficNotification(
        context: Context,
        appName: String,
        packageName: String,
        leakedBytes: Long
    ) {
        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        val contentText = "检测到 $appName 在后台偷跑流量 ${TrafficFormatter.formatBytes(leakedBytes)}"

        // 点击通知打开应用
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("target_package", packageName)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            3000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("偷跑流量告警")
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$contentText\n\n该应用在后台产生了异常流量，建议检查或屏蔽其后台网络。")
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(
            Constants.NOTIFICATION_ID_ALERT_PREFIX + 3000,
            notification
        )
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取网络类型的中文名称
     * @param networkType 网络类型标识
     * @return 中文标签
     */
    private fun getNetworkTypeLabel(networkType: String): String {
        return when (networkType) {
            Constants.NETWORK_TYPE_WIFI -> "WiFi"
            Constants.NETWORK_TYPE_MOBILE -> "移动数据"
            else -> "未连接"
        }
    }

    /**
     * 取消指定ID的通知
     * @param context 上下文
     * @param notificationId 通知ID
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    /**
     * 取消所有限额超限提醒通知
     * @param context 上下文
     */
    fun cancelAllAlertNotifications(context: Context) {
        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        // 取消2001~2099范围内的通知ID
        for (id in Constants.NOTIFICATION_ID_ALERT_PREFIX..(Constants.NOTIFICATION_ID_ALERT_PREFIX + 99)) {
            notificationManager.cancel(id)
        }
    }
}