package com.netguardian.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.netguardian.app.service.TrafficMonitorNotification
import com.netguardian.app.service.TrafficMonitorService
import com.netguardian.app.util.Constants
import com.netguardian.app.util.DataStoreHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 开机完成广播接收器
 * 监听ACTION_BOOT_COMPLETED，在设备启动完成后自动启动流量监控服务
 *
 * 工作流程：
 * 1. 接收到开机完成广播
 * 2. 从DataStore读取用户是否开启了"开机自启"选项
 * 3. 如果用户已开启开机自启，读取保存的权限模式信息
 * 4. 创建通知渠道并启动TrafficMonitorService前台服务
 *
 * 注意事项：
 * - 静态注册，需在AndroidManifest.xml中声明
 * - Android 10+ 需要REQUEST_PACKAGE_USAGE_STATS等权限
 * - 使用SupervisorJob的协程Scope执行DataStore读取操作
 * - 纯本地离线运行，不进行任何网络请求
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"

        /** 用于在BroadcastReceiver中启动协程的Scope */
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        // 仅处理ACTION_BOOT_COMPLETED
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "收到非预期Action: ${intent.action}，忽略")
            return
        }

        Log.i(TAG, "收到开机完成广播")

        // 在协程中读取DataStore（DataStore是suspend函数，不能在onReceive主线程直接调用）
        val pendingResult = goAsync()
        receiverScope.launch(Dispatchers.IO) {
            try {
                // 1. 读取用户是否开启了开机自启
                val bootAutoStart = DataStoreHelper.getBootAutoStart(context)
                if (!bootAutoStart) {
                    Log.i(TAG, "用户未开启开机自启，跳过自动启动服务")
                    pendingResult.finish()
                    return@launch
                }

                Log.i(TAG, "用户已开启开机自启，准备启动流量监控服务...")

                // 2. 读取保存的权限模式
                val privilegeMode = DataStoreHelper.getPrivilegeMode(context)
                Log.i(TAG, "开机自启权限模式: $privilegeMode")

                // 3. 确保通知渠道已创建
                TrafficMonitorNotification.createNotificationChannel(context)

                // 4. 启动流量监控前台服务
                val serviceIntent = Intent(context, TrafficMonitorService::class.java).apply {
                    action = Constants.ACTION_START_MONITOR
                    // 通过Extra传递权限模式信息，供服务在启动时参考
                    putExtra(Constants.KEY_PRIVILEGE_MODE, privilegeMode)
                    // Android 12+ 前台服务需要指定类型
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        putExtra("android.foregroundServiceType", 0x00000010) // FOREGROUND_SERVICE_DATA_SYNC
                    }
                }

                // 启动服务
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }

                Log.i(TAG, "已通过开机广播启动流量监控服务（模式: $privilegeMode）")

            } catch (e: Exception) {
                Log.e(TAG, "开机自启流量监控服务失败: ${e.message}", e)
            } finally {
                // 必须调用finish()释放BroadcastReceiver的WakeLock
                pendingResult.finish()
            }
        }
    }
}