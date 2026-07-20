package com.netguardian.app.util

/**
 * 应用全局常量定义
 * 包含SharedPreferences/DataStore键名、iptables命令模板、刷新间隔、阈值等
 */
object Constants {

    // ==================== SharedPreferences / DataStore 键名 ====================

    /** DataStore文件名 */
    const val DATASTORE_NAME = "netguardian_settings"

    /** 权限模式：root或shizuku */
    const val KEY_PRIVILEGE_MODE = "privilege_mode"

    /** Root权限是否可用 */
    const val KEY_ROOT_AVAILABLE = "root_available"

    /** Shizuku是否可用 */
    const val KEY_SHIZUKU_AVAILABLE = "shizuku_available"

    /** 流量监控服务是否正在运行 */
    const val KEY_MONITOR_SERVICE_RUNNING = "monitor_service_running"

    /** 主题模式：dark/light/system */
    const val KEY_THEME_MODE = "theme_mode"

    /** 自动断网开关 */
    const val KEY_AUTO_DISCONNECT = "auto_disconnect"

    /** 开机自启开关 */
    const val KEY_BOOT_AUTO_START = "boot_auto_start"

    /** 限额提醒开关 */
    const val KEY_LIMIT_REMINDER = "limit_reminder"

    /** 监控通知开关 */
    const val KEY_MONITOR_NOTIFICATION = "monitor_notification"

    /** 闲置检测间隔（毫秒） */
    const val KEY_IDLE_CHECK_INTERVAL = "idle_check_interval"

    /** 防偷跑功能是否启用 */
    const val KEY_ANTI_LEAK_ENABLED = "anti_leak_enabled"

    /** 全局移动网络是否屏蔽 */
    const val KEY_GLOBAL_MOBILE_BLOCKED = "global_mobile_blocked"

    /** 全局WiFi是否屏蔽 */
    const val KEY_GLOBAL_WIFI_BLOCKED = "global_wifi_blocked"

    /** 后台屏蔽白名单（包名列表，逗号分隔） */
    const val KEY_BACKGROUND_WHITELIST = "background_whitelist"

    /** 流量统计重置日期 */
    const val KEY_TRAFFIC_RESET_DATE = "traffic_reset_date"

    /** 通知提醒是否启用 */
    const val KEY_NOTIFICATION_ENABLED = "notification_enabled"

    /** 数据保留天数 */
    const val KEY_DATA_RETENTION_DAYS = "data_retention_days"

    /** 深色主题模式 */
    const val KEY_DARK_THEME = "dark_theme"

    /** 首次运行标记 */
    const val KEY_FIRST_RUN = "first_run"

    /** 监控开关是否启用 */
    const val KEY_MONITOR_ENABLED = "monitor_enabled"

    // ==================== 权限模式 ====================

    /** Root权限模式 */
    const val MODE_ROOT = "root"

    /** Shizuku权限模式 */
    const val MODE_SHIZUKU = "shizuku"

    /** 无权限模式（仅监控） */
    const val MODE_NONE = "none"

    // ==================== iptables命令模板 ====================
    // 注意：不指定 -o/-i 接口名，对所有网络接口生效。
    // 这样避免了不同设备/Android版本下移动网络接口名不一致的问题
    // （如 rmnet+, ccmni+, rmnet_data+, data+ 等）。

    /** iptables屏蔽所有网络出站（按UID） */
    const val IPTABLES_BLOCK_ALL_OUT =
        "iptables -I OUTPUT -m owner --uid-owner %d -j REJECT --reject-with icmp-port-unreachable"

    /** iptables屏蔽所有网络出站+入站（按UID） */
    const val IPTABLES_BLOCK_ALL =
        "iptables -I OUTPUT -m owner --uid-owner %d -j REJECT --reject-with icmp-port-unreachable; iptables -I INPUT -m owner --uid-owner %d -j DROP"

    /** iptables解除所有网络屏蔽（按UID） */
    const val IPTABLES_UNBLOCK_ALL =
        "iptables -D OUTPUT -m owner --uid-owner %d -j REJECT --reject-with icmp-port-unreachable 2>/dev/null; iptables -D INPUT -m owner --uid-owner %d -j DROP 2>/dev/null"

    /** iptables刷新所有自定义规则（清理） */
    const val IPTABLES_FLUSH =
        "iptables -F"

    /** 检查iptables是否可用 */
    const val IPTABLES_CHECK = "which iptables"

    /** ip6tables屏蔽所有网络出站（按UID） */
    const val IP6TABLES_BLOCK_ALL_OUT =
        "ip6tables -I OUTPUT -m owner --uid-owner %d -j REJECT --reject-with icmpv6-port-unreachable"

    /** ip6tables解除所有网络屏蔽（按UID） */
    const val IP6TABLES_UNBLOCK_ALL =
        "ip6tables -D OUTPUT -m owner --uid-owner %d -j REJECT --reject-with icmpv6-port-unreachable 2>/dev/null"

    // ==================== 刷新间隔 ====================

    /** 实时网速采样间隔（毫秒） */
    const val SPEED_SAMPLE_INTERVAL_MS = 1000L

    /** 流量记录保存间隔（毫秒） */
    const val TRAFFIC_RECORD_INTERVAL_MS = 60_000L

    /** 应用列表刷新间隔（毫秒） */
    const val APP_LIST_REFRESH_INTERVAL_MS = 30_000L

    /** Root状态检测间隔（毫秒） */
    const val ROOT_CHECK_INTERVAL_MS = 30_000L

    /** Shizuku状态检测间隔（毫秒） */
    const val SHIZUKU_CHECK_INTERVAL_MS = 30_000L

    // ==================== 阈值常量 ====================

    /** 流量统计保留天数 */
    const val DEFAULT_RETENTION_DAYS = 30

    /** 最小流量阈值（字节），低于此值忽略（过滤噪声） */
    const val MIN_TRAFFIC_THRESHOLD_BYTES = 1024L

    /** 偷跑检测最小流量阈值（字节），超过此值视为异常 */
    const val STEALTHY_TRAFFIC_THRESHOLD_BYTES = 10 * 1024L  // 10KB

    /** 网速为零的超时时间（毫秒），超过此时间认为无活动 */
    const val IDLE_TIMEOUT_MS = 30_000L

    /** Root命令执行超时时间（毫秒） */
    const val ROOT_COMMAND_TIMEOUT_MS = 10_000L

    /** 默认全局流量限制（MB） */
    const val DEFAULT_GLOBAL_LIMIT_MB = 1024L  // 1GB

    /** 后台检测周期（毫秒） */
    const val BACKGROUND_CHECK_INTERVAL_MS = 5_000L

    /** Root模式流量采集间隔（毫秒） */
    const val ROOT_COLLECT_INTERVAL_MS = 5_000L

    /** Shizuku模式流量采集间隔（毫秒） */
    const val SHIZUKU_COLLECT_INTERVAL_MS = 10_000L

    /** 普通模式流量采集间隔（毫秒） */
    const val NONE_COLLECT_INTERVAL_MS = 5_000L

    /** 限额检测WorkManager周期（分钟） */
    const val LIMIT_CHECK_INTERVAL_MINUTES = 15L

    /** 限额检测WorkManager唯一名称 */
    const val WORK_NAME_LIMIT_CHECK = "netguardian_limit_check"

    /** 后台空闲判定时间（毫秒），应用在后台超过此时间无流量则视为空闲 */
    const val BACKGROUND_IDLE_THRESHOLD_MS = 60_000L

    // ==================== 通知相关 ====================

    /** 流量监控前台服务通知渠道ID */
    const val NOTIFICATION_CHANNEL_MONITOR = "netguardian_monitor"

    /** 流量告警通知渠道ID */
    const val NOTIFICATION_CHANNEL_ALERT = "netguardian_alert"

    /** 前台服务通知ID */
    const val NOTIFICATION_ID_MONITOR = 1001

    /** 流量超限告警通知ID前缀 */
    const val NOTIFICATION_ID_ALERT_PREFIX = 2001

    // ==================== Intent Action ====================

    /** 流量监控服务启动Action */
    const val ACTION_START_MONITOR = "com.netguardian.app.action.START_MONITOR"

    /** 流量监控服务停止Action */
    const val ACTION_STOP_MONITOR = "com.netguardian.app.action.STOP_MONITOR"

    /** 流量超限广播Action */
    const val ACTION_TRAFFIC_LIMIT_EXCEEDED = "com.netguardian.app.action.TRAFFIC_LIMIT_EXCEEDED"

    /** 偷跑检测告警广播Action */
    const val ACTION_STEALTHY_TRAFFIC_DETECTED = "com.netguardian.app.action.STEALTHY_TRAFFIC_DETECTED"

    // ==================== 网络类型 ====================

    /** WiFi网络 */
    const val NETWORK_TYPE_WIFI = "WiFi"

    /** 移动网络 */
    const val NETWORK_TYPE_MOBILE = "Mobile"

    /** 无网络 */
    const val NETWORK_TYPE_NONE = "None"

    // ==================== 其他 ====================

    /** 日期格式 */
    const val DATE_FORMAT = "yyyy-MM-dd"

    /** 时间格式 */
    const val TIME_FORMAT = "HH:mm:ss"

    /** 日期时间格式 */
    const val DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss"

    /** iptables规则标签（用于标识NetGuardian添加的规则） */
    const val IPTABLES_COMMENT = "netguardian"

    /** /proc/net/dev 文件路径 */
    const val PROC_NET_DEV = "/proc/net/dev"

    /** /proc/uid_stat/ 目录路径 */
    const val PROC_UID_STAT = "/proc/uid_stat/"

    /** TrafficStats中无法获取数据的UID标记 */
    const val UID_UNKNOWN = -1
}