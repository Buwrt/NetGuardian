package com.netguardian.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 流量格式化工具类
 * 提供字节数、网速、日期等格式化方法
 */
object TrafficFormatter {

    /** 日期格式化器（yyyy-MM-dd） */
    private val dateFormat = SimpleDateFormat(Constants.DATE_FORMAT, Locale.getDefault())

    /** 时间格式化器（HH:mm:ss） */
    private val timeFormat = SimpleDateFormat(Constants.TIME_FORMAT, Locale.getDefault())

    /** 日期时间格式化器（yyyy-MM-dd HH:mm:ss） */
    private val dateTimeFormat = SimpleDateFormat(Constants.DATETIME_FORMAT, Locale.getDefault())

    /**
     * 格式化字节数为人类可读的字符串
     * 自动选择合适的单位：B / KB / MB / GB / TB
     *
     * @param bytes 字节数
     * @return 格式化后的字符串，如 "1.23 MB"
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())

        // 小于10时显示两位小数，否则显示一位小数
        val formattedValue = if (value < 10) {
            String.format(Locale.getDefault(), "%.2f", value)
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }

        return "$formattedValue ${units[index]}"
    }

    /**
     * 格式化网速为人类可读的字符串
     * 自动选择合适的单位：B/s / KB/s / MB/s / GB/s
     *
     * @param bytesPerSecond 每秒字节数
     * @return 格式化后的字符串，如 "1.23 MB/s"
     */
    fun formatSpeed(bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0) return "0 B/s"

        val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
        val digitGroups = (Math.log10(bytesPerSecond.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytesPerSecond / Math.pow(1024.0, index.toDouble())

        // 小于10时显示两位小数，否则显示一位小数
        val formattedValue = if (value < 10) {
            String.format(Locale.getDefault(), "%.2f", value)
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }

        return "$formattedValue ${units[index]}"
    }

    /**
     * 格式化时间戳为日期字符串（yyyy-MM-dd）
     *
     * @param timestamp 时间戳（毫秒）
     * @return 日期字符串
     */
    fun formatDate(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }

    /**
     * 格式化时间戳为时间字符串（HH:mm:ss）
     *
     * @param timestamp 时间戳（毫秒）
     * @return 时间字符串
     */
    fun formatTime(timestamp: Long): String {
        return timeFormat.format(Date(timestamp))
    }

    /**
     * 格式化时间戳为日期时间字符串（yyyy-MM-dd HH:mm:ss）
     *
     * @param timestamp 时间戳（毫秒）
     * @return 日期时间字符串
     */
    fun formatDateTime(timestamp: Long): String {
        return dateTimeFormat.format(Date(timestamp))
    }

    /**
     * 获取今天零点的时间范围
     * @return Pair(今天零点时间戳, 今天23:59:59时间戳)，单位毫秒
     */
    fun getDayRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis

        return Pair(startOfDay, endOfDay)
    }

    /**
     * 获取最近N天的日期范围
     * @param days 天数
     * @return Pair(N天前的日期字符串, 今天的日期字符串)，格式yyyy-MM-dd
     */
    fun getDateRange(days: Int): Pair<String, String> {
        val calendar = Calendar.getInstance()
        val endDate = formatDate(calendar.timeInMillis)

        calendar.add(Calendar.DAY_OF_MONTH, -(days - 1))
        val startDate = formatDate(calendar.timeInMillis)

        return Pair(startDate, endDate)
    }

    /**
     * 获取今天的日期字符串
     * @return yyyy-MM-dd格式
     */
    fun getTodayDate(): String {
        return formatDate(System.currentTimeMillis())
    }

    /**
     * 格式化流量为MB显示
     * @param bytes 字节数
     * @return MB为单位字符串
     */
    fun formatMb(bytes: Long): String {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return String.format(Locale.getDefault(), "%.2f MB", mb)
    }

    /**
     * 格式化流量为GB显示
     * @param bytes 字节数
     * @return GB为单位字符串
     */
    fun formatGb(bytes: Long): String {
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return String.format(Locale.getDefault(), "%.2f GB", gb)
    }

    /**
     * MB转字节数
     * @param mb 兆字节数
     * @return 字节数
     */
    fun mbToBytes(mb: Long): Long = mb * 1024L * 1024L

    /**
     * 字节数转MB（向下取整）
     * @param bytes 字节数
     * @return MB数
     */
    fun bytesToMb(bytes: Long): Long = bytes / (1024L * 1024L)

    /**
     * 计算百分比
     * @param used 已用量
     * @param total 总量
     * @return 0~100的百分比，如果total为0返回0
     */
    fun calculatePercentage(used: Long, total: Long): Int {
        if (total <= 0) return 0
        return ((used.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100)
    }
}