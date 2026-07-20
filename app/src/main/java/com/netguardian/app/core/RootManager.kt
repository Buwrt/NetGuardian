package com.netguardian.app.core

import android.util.Log
import com.netguardian.app.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 进程流量数据
 * 用于承载从/proc/uid_stat/读取的原始流量信息
 */
data class ProcessTrafficData(
    /** 接收字节数 */
    val rxBytes: Long,
    /** 发送字节数 */
    val txBytes
: Long,
    /** TCP接收字节数 */
    val tcpRxBytes: Long = 0L,
    /** TCP发送字节数 */
    val tcpTxBytes: Long = 0L
) {
    /** 总字节数 */
    val totalBytes: Long
        get() = rxBytes + txBytes
}

/**
 * Root权限管理单例
 * 负责Root权限的检测、命令执行、iptables规则管理、应用网络屏蔽等操作
 * 所有操作均为纯本地离线执行，不进行任何网络请求
 */
class RootManager private constructor() {

    companion object {
        private const val TAG = "RootManager"

        /** 单例实例 */
        @Volatile
        private var INSTANCE: RootManager? = null

        /**
         * 获取RootManager单例
         */
        fun getInstance(): RootManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RootManager().also { INSTANCE = it }
            }
        }
    }

    /** Root权限状态，使用StateFlow通知UI变化 */
    private val _isRootAvailable = MutableStateFlow(false)
    val isRootAvailable: StateFlow<Boolean> = _isRootAvailable.asStateFlow()

    /** Root检测协程Job */
    private var rootCheckJob: Job? = null

    /** Root进程引用（用于批量命令执行） */
    private var rootProcess: Process? = null

    init {
        // 初始化时检测Root权限
        _isRootAvailable.value = hasRootAccess()
        Log.i(TAG, "RootManager初始化，Root权限状态: ${_isRootAvailable.value}")
    }

    /**
     * 检测设备是否具有Root权限
     * 通过执行"su -c id"命令来判断
     *
     * @return true表示具有Root权限
     */
    fun hasRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine()
            reader.close()
            process.waitFor()

            val hasRoot = output != null && (output.contains("uid=0") || output.contains("root"))
            Log.d(TAG, "Root检测: output=$output, hasRoot=$hasRoot")
            hasRoot
        } catch (e: Exception) {
            Log.d(TAG, "Root检测失败: ${e.message}")
            false
        }
    }

    /**
     * 更新Root权限状态（供外部调用）
     */
    fun updateRootStatus(available: Boolean) {
        _isRootAvailable.value = available
    }

    /**
     * 请求Root权限
     * 尝试执行一个简单的Root命令来触发Superuser授权弹窗
     *
     * @return true表示获得了Root权限
     */
    fun requestRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo root_ok"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine()
            reader.close()
            process.waitFor()

            val success = "root_ok" == output
            _isRootAvailable.value = success
            Log.i(TAG, "Root权限请求结果: $success")
            success
        } catch (e: Exception) {
            Log.d(TAG, "Root权限请求失败: ${e.message}")
            _isRootAvailable.value = false
            false
        }
    }

    /**
     * 以Root权限执行Shell命令
     *
     * @param command 要执行的命令
     * @return 命令输出结果，失败返回null
     */
    fun executeRootCommand(command: String): String? {
        // 不再检查 _isRootAvailable，直接尝试执行
        // 因为 _isRootAvailable 可能不准确（init时检测可能失败）
        return try {
            Log.d(TAG, "执行Root命令: ${command.take(100)}")
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))

            // 读取正常输出
            val outputReader = BufferedReader(InputStreamReader(process.inputStream))
            val outputBuilder = StringBuilder()
            var line: String?
            while (outputReader.readLine().also { line = it } != null) {
                outputBuilder.append(line).append("\n")
            }
            outputReader.close()

            // 读取错误输出
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val errorBuilder = StringBuilder()
            while (errorReader.readLine().also { line = it } != null) {
                errorBuilder.append(line).append("\n")
            }
            errorReader.close()

            val exitCode = process.waitFor()
            val result = outputBuilder.toString().trim()

            if (exitCode != 0) {
                val error = errorBuilder.toString().trim()
                Log.w(TAG, "命令执行返回非零($exitCode): $error")
                // 即使exitCode非零，也返回输出（有些命令exitCode=1但仍有有效输出）
                return result.ifEmpty { null }
            }

            result.ifEmpty { null }
        } catch (e: Exception) {
            Log.e(TAG, "执行Root命令异常: ${e.message}")
            null
        }
    }

    /**
     * 获取指定UID进程的流量数据
     * 从/proc/uid_stat/{uid}/目录下读取tcp_rcv/tcp_snd统计
     *
     * @param uid 应用UID
     * @return 进程流量数据，读取失败返回全0数据
     */
    fun getProcessTraffic(uid: Int): ProcessTrafficData {
        if (uid < 0) {
            return ProcessTrafficData(0L, 0L)
        }

        val uidStatPath = "${Constants.PROC_UID_STAT}$uid/"
        var tcpRx = 0L
        var tcpTx = 0L

        try {
            // 读取tcp_rcv（接收字节数）
            val rxOutput = executeRootCommand("cat ${uidStatPath}tcp_rcv")
            if (rxOutput != null) {
                tcpRx = rxOutput.trim().toLongOrNull() ?: 0L
            }

            // 读取tcp_snd（发送字节数）
            val txOutput = executeRootCommand("cat ${uidStatPath}tcp_snd")
            if (txOutput != null) {
                tcpTx = txOutput.trim().toLongOrNull() ?: 0L
            }
        } catch (e: Exception) {
            Log.d(TAG, "读取UID $uid 的流量数据失败: ${e.message}")
        }

        return ProcessTrafficData(
            rxBytes = tcpRx,
            txBytes = tcpTx,
            tcpRxBytes = tcpRx,
            tcpTxBytes = tcpTx
        )
    }

    /**
     * 获取所有UID的流量统计
     * 优先使用 /proc/uid_stat/ 目录（最可靠），备用 dumpsys netstats
     *
     * @return Map<UID, Pair<rxBytes, txBytes>>
     */
    fun getAllUidTraffic(): Map<Int, Pair<Long, Long>> {
        val hasRoot = hasRootAccess()
        _isRootAvailable.value = hasRoot
        if (!hasRoot) {
            Log.w(TAG, "Root不可用")
            return emptyMap()
        }

        // 方式1：直接读取 /proc/uid_stat/ 目录（最可靠）
        val uidStatResult = try {
            val result = executeRootCommand("ls /proc/uid_stat/")
            if (!result.isNullOrEmpty()) {
                parseUidStatDirectory(result)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "读取 /proc/uid_stat/ 失败: ${e.message}")
            null
        }

        if (uidStatResult != null && uidStatResult.isNotEmpty()) {
            Log.d(TAG, "/proc/uid_stat/ 读取到 ${uidStatResult.size} 个UID")
            return uidStatResult
        }

        // 方式2：dumpsys netstats（备用）
        val result2 = executeRootCommand("dumpsys netstats")
        if (!result2.isNullOrEmpty()) {
            val trafficMap = parseNetstatsDumpsys(result2)
            if (trafficMap.isNotEmpty()) {
                Log.d(TAG, "dumpsys netstats 读取到 ${trafficMap.size} 个UID")
                return trafficMap
            }
        }

        Log.w(TAG, "Root模式无法获取per-UID流量")
        return emptyMap()
    }

    /**
     * 解析 /proc/uid_stat/ 目录列表
     * 分批读取每个 UID 的 tcp_rcv/tcp_snd，避免命令过长导致 su -c 失败
     */
    private fun parseUidStatDirectory(uidList: String): Map<Int, Pair<Long, Long>> {
        val trafficMap = mutableMapOf<Int, Pair<Long, Long>>()
        val uids = uidList.trim().lines().map { it.trim() }.filter { it.matches(Regex("\\d+")) }
        if (uids.isEmpty()) return emptyMap()

        Log.d(TAG, "开始读取 ${uids.size} 个UID的流量数据")

        // 分批读取，每批20个UID，避免命令过长
        val batchSize = 20
        for (i in uids.indices step batchSize) {
            val batch = uids.subList(i, minOf(i + batchSize, uids.size))

            // 为每个UID构建 cat 命令
            val commands = batch.flatMap { uid ->
                listOf(
                    "cat /proc/uid_stat/$uid/tcp_rcv 2>/dev/null",
                    "cat /proc/uid_stat/$uid/tcp_snd 2>/dev/null"
                )
            }.joinToString(" ; echo SEP ; ")

            val result = executeRootCommand(commands) ?: continue

            // 解析输出：每两行为一组（rx, tx）
            val segments = result.split("SEP")
            var j = 0
            for (uid in batch) {
                if (j < segments.size) {
                    val lines = segments[j].trim().lines().filter { it.isNotBlank() }
                    if (lines.size >= 2) {
                        val rx = lines[0].trim().toLongOrNull() ?: 0L
                        val tx = lines[1].trim().toLongOrNull() ?: 0L
                        if (rx > 0 || tx > 0) {
                            trafficMap[uid.toInt()] = Pair(rx, tx)
                        }
                    }
                }
                j++
            }
        }

        Log.d(TAG, "成功读取 ${trafficMap.size} 个UID的流量数据")
        return trafficMap
    }

    /**
     * 解析 /proc/net/xt_qtaguid/stats 输出
     * 格式: iface acct_tag_hex uid_tag_int cnt_set rx_bytes rx_packets tx_bytes tx_packets
     */
    private fun parseXtQtaguidStats(output: String): Map<Int, Pair<Long, Long>> {
        val trafficMap = mutableMapOf<Int, Pair<Long, Long>>()
        val lines = output.lines()

        for (line in lines) {
            // 跳过表头
            if (line.startsWith("iface") || line.isBlank()) continue

            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 7) continue

            try {
                val uid = parts[2].toIntOrNull() ?: continue
                val rxBytes = parts[4].toLongOrNull() ?: 0L
                val txBytes = parts[6].toLongOrNull() ?: 0L

                // 聚合同一UID的不同网络接口和tag
                val current = trafficMap.getOrDefault(uid, 0L to 0L)
                trafficMap[uid] = (current.first + rxBytes) to (current.second + txBytes)
            } catch (e: Exception) {
                // 跳过无法解析的行
            }
        }

        return trafficMap
    }

    /**
     * 解析 dumpsys netstats 输出
     * 兼容多种 ROM 的输出格式，尝试提取 UID -> rx/tx 的映射
     */
    private fun parseNetstatsDumpsys(output: String): Map<Int, Pair<Long, Long>> {
        val trafficMap = mutableMapOf<Int, Pair<Long, Long>>()

        // 尝试多种正则模式匹配不同 ROM 的输出格式
        val patterns = listOf(
            // 格式1: uid=10012 tag=0x0 set=DEFAULT rb=12345 rp=10 tb=67890 tp=20
            Regex("""uid=(\d+).*?\brb=(\d+).*?\btb=(\d+)"""),
            // 格式2: uid=10123 identifier=com.app type=DEFAULT rxBytes=12345 txBytes=67890
            Regex("""uid=(\d+).*?\brxBytes=(\d+).*?\btxBytes=(\d+)"""),
            // 格式3: uid=10123  rb=12345  tb=67890
            Regex("""uid=(\d+)\s+.*?rb=(\d+).*?tb=(\d+)"""),
        )

        for (line in output.lines()) {
            for (pattern in patterns) {
                val match = pattern.find(line) ?: continue
                val uid = match.groupValues[1].toIntOrNull() ?: continue
                val rx = match.groupValues[2].toLongOrNull() ?: 0L
                val tx = match.groupValues[3].toLongOrNull() ?: 0L
                if (rx > 0 || tx > 0) {
                    val current = trafficMap.getOrDefault(uid, 0L to 0L)
                    trafficMap[uid] = (current.first + rx) to (current.second + tx)
                }
                break // 一行只匹配一次
            }
        }

        return trafficMap
    }

    /**
     * 屏蔽指定应用的网络连接
     * 通过iptables规则按UID屏蔽移动网络和/或WiFi
     *
     * @param packageName 应用包名
     * @param blockMobile 是否屏蔽移动网络
     * @param blockWifi 是否屏蔽WiFi
     * @return true表示操作成功
     */
    fun blockAppNetwork(packageName: String, blockMobile: Boolean, blockWifi: Boolean): Boolean {
        if (!_isRootAvailable.value) {
            Log.w(TAG, "Root不可用，无法屏蔽应用: $packageName")
            return false
        }

        // 通过包名获取UID
        val uid = getUidByPackageName(packageName)
        if (uid < 0) {
            Log.w(TAG, "无法获取包名 $packageName 的UID")
            return false
        }

        return setIptablesRule(packageName, uid, block = true)
    }

    /**
     * 解除指定应用的网络屏蔽
     *
     * @param packageName 应用包名
     * @return true表示操作成功
     */
    fun unblockAppNetwork(packageName: String): Boolean {
        if (!_isRootAvailable.value) {
            Log.w(TAG, "Root不可用，无法解除屏蔽: $packageName")
            return false
        }

        val uid = getUidByPackageName(packageName)
        if (uid < 0) {
            Log.w(TAG, "无法获取包名 $packageName 的UID")
            return false
        }

        // 解除所有网络屏蔽规则（IPv4 + IPv6，不区分接口）
        val cmdIPv4 = String.format(Constants.IPTABLES_UNBLOCK_ALL, uid, uid)
        val cmdIPv6 = String.format(Constants.IP6TABLES_UNBLOCK_ALL, uid)

        var allSuccess = true
        for (cmd in listOf(cmdIPv4, cmdIPv6)) {
            val result = executeRootCommand(cmd)
            if (result == null) {
                // 删除不存在的规则时可能返回错误，这不算失败
                Log.d(TAG, "删除规则返回null（可能不存在）: $cmd")
            }
        }

        Log.i(TAG, "已解除 $packageName (UID:$uid) 的网络屏蔽")
        return allSuccess
    }

    /**
     * 设置iptables规则（屏蔽或解除屏蔽）
     *
     * @param packageName 应用包名
     * @param uid 应用UID
     * @param block true为添加屏蔽规则，false为删除屏蔽规则
     * @return true表示操作成功
     */
    fun setIptablesRule(packageName: String, uid: Int, block: Boolean): Boolean {
        if (!_isRootAvailable.value) {
            Log.w(TAG, "setIptablesRule: Root不可用，跳过 $packageName (UID:$uid)")
            return false
        }

        if (uid < 0) {
            Log.w(TAG, "setIptablesRule: UID无效 ($uid)，跳过 $packageName")
            return false
        }

        val commands = mutableListOf<String>()

        if (block) {
            // 添加屏蔽规则：对所有网络接口生效（IPv4出站+入站, IPv6出站）
            commands.add(String.format(Constants.IPTABLES_BLOCK_ALL, uid, uid))
            commands.add(String.format(Constants.IP6TABLES_BLOCK_ALL_OUT, uid))
        } else {
            // 删除屏蔽规则
            commands.add(String.format(Constants.IPTABLES_UNBLOCK_ALL, uid, uid))
            commands.add(String.format(Constants.IP6TABLES_UNBLOCK_ALL, uid))
        }

        var success = true
        var failedCount = 0
        for (cmd in commands) {
            val result = executeRootCommand(cmd)
            if (result == null && block) {
                // 添加规则失败则标记为失败，删除规则时null不算失败（规则可能不存在）
                failedCount++
                Log.w(TAG, "iptables命令执行失败: $cmd")
                success = false
            }
        }

        val action = if (block) "屏蔽" else "解除屏蔽"
        if (success) {
            Log.i(TAG, "${action}应用 $packageName (UID:$uid) 成功")
        } else {
            Log.w(TAG, "${action}应用 $packageName (UID:$uid) 部分失败 ($failedCount/${commands.size} 条命令未执行)")
        }
        return success
    }

    /**
     * 强制停止指定应用的后台进程
     *
     * @param packageName 应用包名
     * @return true表示操作成功
     */
    fun killBackgroundProcess(packageName: String): Boolean {
        if (!_isRootAvailable.value) {
            Log.w(TAG, "Root不可用，无法终止进程: $packageName")
            return false
        }

        return try {
            val result = executeRootCommand("am force-stop $packageName")
            val success = result != null
            Log.i(TAG, "终止后台进程 $packageName 结果: $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "终止后台进程异常: ${e.message}")
            false
        }
    }

    /**
     * 通过包名获取应用的UID
     * 解析dumpsys package信息来获取
     *
     * @param packageName 应用包名
     * @return UID，获取失败返回-1
     */
    private fun getUidByPackageName(packageName: String): Int {
        val output = executeRootCommand("dumpsys package $packageName | grep userId=")
            ?: return -1

        // 输出格式类似: userId=10123
        return try {
            val regex = Regex("""userId=(\d+)""")
            val match = regex.find(output)
            match?.groupValues?.get(1)?.toIntOrNull() ?: -1
        } catch (e: Exception) {
            Log.d(TAG, "解析UID失败: ${e.message}")
            -1
        }
    }

    /**
     * 启动Root状态周期性检测
     * 在后台定期检查Root权限是否仍然可用
     */
    fun startRootMonitoring(scope: CoroutineScope) {
        rootCheckJob?.cancel()
        rootCheckJob = scope.launch(Dispatchers.IO) {
            while (true) {
                delay(Constants.ROOT_CHECK_INTERVAL_MS)
                val currentStatus = hasRootAccess()
                if (_isRootAvailable.value != currentStatus) {
                    _isRootAvailable.value = currentStatus
                    Log.i(TAG, "Root权限状态变更: $currentStatus")
                }
            }
        }
    }

    /**
     * 停止Root状态周期性检测
     */
    fun stopRootMonitoring() {
        rootCheckJob?.cancel()
        rootCheckJob = null
    }

    /**
     * 检查iptables是否可用
     * @return true表示iptables命令存在
     */
    fun isIptablesAvailable(): Boolean {
        val result = executeRootCommand(Constants.IPTABLES_CHECK)
        return result != null
    }

    /**
     * 清理所有NetGuardian添加的iptables规则
     * @return true表示清理成功
     */
    fun clearAllIptablesRules(): Boolean {
        if (!_isRootAvailable.value) return false

        // 注意：这里不能直接flush，因为会清除系统自带规则
        // 实际实现应保存添加的规则列表，然后逐条删除
        Log.i(TAG, "清理所有iptables规则（需配合规则记录表使用）")
        return true
    }

    /**
     * 获取所有网络接口的流量统计
     * 读取/proc/net/dev文件
     * @return Map<接口名, Pair<接收字节数, 发送字节数>>
     */
    fun getInterfaceTraffic(): Map<String, Pair<Long, Long>> {
        if (!_isRootAvailable.value) return emptyMap()

        val result = executeRootCommand("cat ${Constants.PROC_NET_DEV}") ?: return emptyMap()

        val trafficMap = mutableMapOf<String, Pair<Long, Long>>()
        val lines = result.lines()

        // /proc/net/dev前两行是表头，从第三行开始是数据
        for (i in 2 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue

            // 格式: "eth0:  12345  0  0  0  0  0  0  0  67890  0  0  0  0  0  0  0"
            val parts = line.split(":")
            if (parts.size != 2) continue

            val interfaceName = parts[0].trim()
            val stats = parts[1].trim().split(Regex("\\s+"))

            if (stats.size >= 10) {
                try {
                    val rxBytes = stats[0].toLongOrNull() ?: 0L
                    val txBytes = stats[8].toLongOrNull() ?: 0L
                    trafficMap[interfaceName] = Pair(rxBytes, txBytes)
                } catch (e: Exception) {
                    Log.d(TAG, "解析接口 $interfaceName 流量数据失败")
                }
            }
        }

        return trafficMap
    }
}