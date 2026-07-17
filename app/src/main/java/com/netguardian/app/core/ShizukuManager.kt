package com.netguardian.app.core

import android.content.ComponentName
import android.util.Log
import androidx.activity.ComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Shizuku权限管理单例
 * 负责Shizuku服务可用性检测、权限请求、通过Shizuku执行系统级操作
 * 如果Shizuku不可用，所有方法安全降级返回false
 * 所有操作均为纯本地离线执行，不进行任何网络请求
 */
class ShizukuManager private constructor() {

    companion object {
        private const val TAG = "ShizukuManager"

        /** Shizuku权限请求码 */
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001

        /** 单例实例 */
        @Volatile
        private var INSTANCE: ShizukuManager? = null

        /**
         * 获取ShizukuManager单例
         */
        fun getInstance(): ShizukuManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ShizukuManager().also { INSTANCE = it }
            }
        }
    }

    /** Shizuku权限监听器 */
    private var permissionListener: Shizuku.OnRequestPermissionResultListener? = null

    /** 权限授予回调 */
    private var onPermissionGranted: (() -> Unit)? = null

    /** 权限拒绝回调 */
    private var onPermissionDenied: (() -> Unit)? = null

    init {
        // 初始化Shizuku权限状态变化监听
        try {
            Shizuku.addRequestPermissionResultListener(getPermissionListener())
            Shizuku.addBinderReceivedListener {
                Log.i(TAG, "Shizuku Binder已接收")
            }
            Shizuku.addBinderDeadListener {
                Log.w(TAG, "Shizuku Binder已断开")
            }
            Log.i(TAG, "ShizukuManager初始化完成，Shizuku运行状态: ${isShizukuRunning()}")
        } catch (e: Exception) {
            Log.d(TAG, "ShizukuManager初始化异常（Shizuku可能未安装）: ${e.message}")
        }
    }

    /**
     * 获取或创建权限请求结果监听器
     */
    private fun getPermissionListener(): Shizuku.OnRequestPermissionResultListener {
        if (permissionListener != null) return permissionListener!!

        permissionListener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                    if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        Log.i(TAG, "Shizuku权限已授予")
                        onPermissionGranted?.invoke()
                    } else {
                        Log.w(TAG, "Shizuku权限被拒绝")
                        onPermissionDenied?.invoke()
                    }
                }
            }
        }
        return permissionListener!!
    }

    /**
     * 检查Shizuku是否具有应用所需的权限
     *
     * @return true表示Shizuku可用且已授权
     */
    fun checkShizukuPermission(): Boolean {
        return try {
            if (!isShizukuRunning()) {
                Log.d(TAG, "Shizuku未运行")
                return false
            }
            val granted = Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Shizuku权限检查: $granted")
            granted
        } catch (e: Exception) {
            Log.d(TAG, "检查Shizuku权限异常: ${e.message}")
            false
        }
    }

    /**
     * 请求Shizuku权限
     * 需要在Activity中调用，会弹出Shizuku授权对话框
     *
     * @param activity 当前Activity
     * @param onGranted 权限授予回调
     * @param onDenied 权限拒绝回调
     */
    fun requestShizukuPermission(
        activity: ComponentActivity,
        onGranted: (() -> Unit)? = null,
        onDenied: (() -> Unit)? = null
    ) {
        this.onPermissionGranted = onGranted
        this.onPermissionDenied = onDenied

        try {
            if (!isShizukuRunning()) {
                Log.w(TAG, "Shizuku未运行，无法请求权限")
                onDenied?.invoke()
                return
            }

            if (checkShizukuPermission()) {
                Log.i(TAG, "Shizuku权限已拥有，无需再次请求")
                onGranted?.invoke()
                return
            }

            // 注册监听器后发起权限请求
            Shizuku.addRequestPermissionResultListener(getPermissionListener())
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            Log.i(TAG, "已发起Shizuku权限请求")
        } catch (e: Exception) {
            Log.e(TAG, "请求Shizuku权限异常: ${e.message}")
            onDenied?.invoke()
        }
    }

    /**
     * 检查Shizuku是否可用（已安装且运行中）
     *
     * @return true表示Shizuku服务可用
     */
    fun isShizukuAvailable(): Boolean {
        return try {
            val running = isShizukuRunning()
            val hasPermission = checkShizukuPermission()
            val available = running && hasPermission
            if (!available) {
                Log.d(TAG, "Shizuku不可用 - 运行中: $running, 已授权: $hasPermission")
            }
            available
        } catch (e: Exception) {
            Log.d(TAG, "检查Shizuku可用性异常: ${e.message}")
            false
        }
    }

    /**
     * 检查Shizuku服务是否正在运行
     * 不检查权限，仅检查服务状态
     *
     * @return true表示Shizuku服务正在运行
     */
    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            Log.d(TAG, "Shizuku ping失败: ${e.message}")
            false
        }
    }

    /**
     * 通过Shizuku屏蔽指定应用的网络连接
     * 使用ShizukuBinderWrapper获取INetworkPolicyManager来设置网络策略
     *
     * @param packageName 应用包名
     * @param blockMobile 是否屏蔽移动网络
     * @param blockWifi 是否屏蔽WiFi
     * @return true表示操作成功
     */
    fun blockAppViaShizuku(packageName: String, blockMobile: Boolean, blockWifi: Boolean): Boolean {
        if (!isShizukuAvailable()) {
            Log.w(TAG, "Shizuku不可用，无法屏蔽应用: $packageName")
            return false
        }

        return try {
            // 通过Shizuku执行shell命令来操作iptables
            // 这与Root模式使用相同的iptables命令，但通过Shizuku的su权限执行
            val uid = getUidByPackageShizuku(packageName)
            if (uid < 0) {
                Log.w(TAG, "Shizuku模式下无法获取包名 $packageName 的UID")
                return false
            }

            val commands = mutableListOf<String>()

            if (blockMobile) {
                commands.add(String.format("iptables -A OUTPUT -m owner --uid-owner %d -o rmnet+ -j REJECT", uid))
                commands.add(String.format("ip6tables -A OUTPUT -m owner --uid-owner %d -o rmnet+ -j REJECT", uid))
            }

            if (blockWifi) {
                commands.add(String.format("iptables -A OUTPUT -m owner --uid-owner %d -o wlan+ -j REJECT", uid))
                commands.add(String.format("ip6tables -A OUTPUT -m owner --uid-owner %d -o wlan+ -j REJECT", uid))
            }

            // 通过Shizuku远程执行shell命令
            val fullCommand = commands.joinToString(" && ")
            val process = Shizuku.newProcess(arrayOf("sh", "-c", fullCommand), null, null)
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            val success = exitCode == 0
            Log.i(TAG, "Shizuku屏蔽 $packageName (UID:$uid) 结果: $success, exitCode: $exitCode")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku屏蔽应用异常: ${e.message}")
            false
        }
    }

    /**
     * 通过Shizuku解除指定应用的网络屏蔽
     *
     * @param packageName 应用包名
     * @return true表示操作成功
     */
    fun unblockAppViaShizuku(packageName: String): Boolean {
        if (!isShizukuAvailable()) {
            Log.w(TAG, "Shizuku不可用，无法解除屏蔽: $packageName")
            return false
        }

        return try {
            val uid = getUidByPackageShizuku(packageName)
            if (uid < 0) {
                Log.w(TAG, "Shizuku模式下无法获取包名 $packageName 的UID")
                return false
            }

            val commands = listOf(
                String.format("iptables -D OUTPUT -m owner --uid-owner %d -o rmnet+ -j REJECT", uid),
                String.format("iptables -D OUTPUT -m owner --uid-owner %d -o wlan+ -j REJECT", uid),
                String.format("ip6tables -D OUTPUT -m owner --uid-owner %d -o rmnet+ -j REJECT", uid),
                String.format("ip6tables -D OUTPUT -m owner --uid-owner %d -o wlan+ -j REJECT", uid),
            )

            val fullCommand = commands.joinToString(" ; ")
            val process = Shizuku.newProcess(arrayOf("sh", "-c", fullCommand), null, null)
            process.waitFor()

            // 删除不存在的规则会返回非零，但不算失败
            Log.i(TAG, "已通过Shizuku解除 $packageName (UID:$uid) 的网络屏蔽")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku解除屏蔽异常: ${e.message}")
            false
        }
    }

    /**
     * 通过Shizuku强制停止应用后台进程
     *
     * @param packageName 应用包名
     * @return true表示操作成功
     */
    fun forceStopViaShizuku(packageName: String): Boolean {
        if (!isShizukuAvailable()) return false

        return try {
            val process = Shizuku.newProcess(
                arrayOf("sh", "-c", "am force-stop $packageName"),
                null, null
            )
            val exitCode = process.waitFor()
            val success = exitCode == 0
            Log.i(TAG, "Shizuku强制停止 $packageName 结果: $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku强制停止异常: ${e.message}")
            false
        }
    }

    /**
     * 通过Shizuku获取指定包名的UID
     *
     * @param packageName 应用包名
     * @return UID，获取失败返回-1
     */
    private fun getUidByPackageShizuku(packageName: String): Int {
        return try {
            val process = Shizuku.newProcess(
                arrayOf("sh", "-c", "dumpsys package $packageName | grep userId="),
                null, null
            )
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            val regex = Regex("""userId=(\d+)""")
            val match = regex.find(output)
            match?.groupValues?.get(1)?.toIntOrNull() ?: -1
        } catch (e: Exception) {
            Log.d(TAG, "Shizuku模式下解析UID失败: ${e.message}")
            -1
        }
    }

    /**
     * 通过Shizuku获取指定UID的流量数据
     *
     * @param uid 应用UID
     * @return Pair<接收字节数, 发送字节数>，获取失败返回(0, 0)
     */
    fun getUidTrafficShizuku(uid: Int): Pair<Long, Long> {
        if (!isShizukuAvailable() || uid < 0) return Pair(0L, 0L)

        return try {
            val uidStatPath = "/proc/uid_stat/$uid/"
            val process = Shizuku.newProcess(
                arrayOf("sh", "-c", "cat ${uidStatPath}tcp_rcv && echo ':::' && cat ${uidStatPath}tcp_snd"),
                null, null
            )
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            val parts = output.split(":::")
            val rx = parts.getOrNull(0)?.trim()?.toLongOrNull() ?: 0L
            val tx = parts.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L
            Pair(rx, tx)
        } catch (e: Exception) {
            Log.d(TAG, "Shizuku模式下读取UID $uid 流量失败: ${e.message}")
            Pair(0L, 0L)
        }
    }

    /**
     * 通过Shizuku执行任意shell命令
     *
     * @param command shell命令
     * @return 命令输出结果，失败返回null
     */
    fun executeCommand(command: String): String? {
        if (!isShizukuAvailable()) {
            Log.w(TAG, "Shizuku不可用，无法执行命令")
            return null
        }

        return try {
            val process = Shizuku.newProcess(
                arrayOf("sh", "-c", command),
                null, null
            )
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            output.ifEmpty { null }
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku执行命令异常: ${e.message}")
            null
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        try {
            permissionListener?.let {
                Shizuku.removeRequestPermissionResultListener(it)
            }
        } catch (e: Exception) {
            Log.d(TAG, "释放ShizukuManager资源异常: ${e.message}")
        }
    }
}