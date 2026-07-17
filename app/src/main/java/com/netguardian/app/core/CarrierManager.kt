package com.netguardian.app.core

import android.content.Context
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 运营商信息数据类
 * 封装当前SIM卡的运营商名称、网络类型、信号强度等信息
 */
data class CarrierInfo(
    /** 运营商名称 */
    val carrierName: String,
    /** SIM卡运营商代码（MCC+MNC） */
    val simOperator: String,
    /** 网络类型（2G/3G/4G/5G） */
    val networkType: String,
    /** 信号强度 */
    val signalStrength: Int,
    /** SIM卡订阅ID */
    val subscriptionId: Int,
    /** 是否是默认数据卡 */
    val isDefault: Boolean
)

/**
 * 运营商管理器
 * 负责检测和提供当前SIM卡的运营商信息
 *
 * 功能：
 * 1. 检测当前运营商名称和网络类型（4G/5G/3G等）
 * 2. 判断是否为双卡双待
 * 3. 提供运营商的标准化中文名称
 * 4. 通过StateFlow暴露运营商信息供UI层观察
 *
 * 纯本地离线运行，不进行任何网络请求
 */
class CarrierManager(private val context: Context) {

    companion object {
        private const val TAG = "CarrierManager"
    }

    private val telephonyManager: TelephonyManager? by lazy {
        try {
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        } catch (e: Exception) {
            Log.d(TAG, "获取TelephonyManager失败: ${e.message}")
            null
        }
    }

    private val subscriptionManager: SubscriptionManager? by lazy {
        try {
            context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        } catch (e: Exception) {
            Log.d(TAG, "获取SubscriptionManager失败: ${e.message}")
            null
        }
    }

    private val _carrierInfo = MutableStateFlow<CarrierInfo?>(null)
    val carrierInfo: StateFlow<CarrierInfo?> = _carrierInfo.asStateFlow()

    /**
     * 检测当前运营商信息
     * 从TelephonyManager读取SIM卡信息并封装为CarrierInfo
     *
     * @return CarrierInfo 检测成功时返回运营商信息，失败返回null
     */
    fun detectCarrier(): CarrierInfo? {
        val tm = telephonyManager ?: return null

        return try {
            // 检查SIM卡是否就绪
            if (tm.simState != TelephonyManager.SIM_STATE_READY) {
                Log.d(TAG, "SIM卡未就绪，状态: ${tm.simState}")
                return null
            }

            val operator = tm.simOperator?.trim() ?: ""
            val rawName = tm.simOperatorName?.trim() ?: ""
            val carrierName = if (rawName.isNotEmpty()) rawName else "未知运营商"
            val networkType = getNetworkTypeName(tm.dataNetworkType)
            val subscriptionId = getDefaultSubscriptionId()

            val info = CarrierInfo(
                carrierName = carrierName,
                simOperator = operator,
                networkType = networkType,
                signalStrength = -1,
                subscriptionId = subscriptionId,
                isDefault = true
            )

            _carrierInfo.value = info
            Log.d(TAG, "运营商: $carrierName (SIM: $operator), 网络: $networkType")
            info
        } catch (e: SecurityException) {
            Log.w(TAG, "无READ_PHONE_STATE权限: ${e.message}")
            null
        } catch (e: Exception) {
            Log.d(TAG, "检测运营商失败: ${e.message}")
            null
        }
    }

    /**
     * 获取网络类型名称
     * 将TelephonyManager的网络类型常量转换为可读的中文字符串
     *
     * @param type TelephonyManager.NETWORK_TYPE_* 常量
     * @return 网络类型名称："2G" / "3G" / "4G" / "5G" / "未知"
     */
    fun getNetworkTypeName(type: Int): String {
        return when (type) {
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN -> "2G"

            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"

            TelephonyManager.NETWORK_TYPE_LTE,
            TelephonyManager.NETWORK_TYPE_IWLAN -> "4G"

            TelephonyManager.NETWORK_TYPE_NR -> "5G"

            // 未知类型
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> "未知"

            else -> "未知"
        }
    }

    /**
     * 获取运营商标准显示名称
     * 将各种运营商名称变体统一为标准的中文名称
     *
     * @return 标准运营商名称："中国移动" / "中国联通" / "中国电信" / 原始名称 / "未知运营商"
     */
    fun getCarrierDisplayName(): String {
        val name = telephonyManager?.simOperatorName?.trim() ?: return "未知运营商"
        if (name.isEmpty()) return "未知运营商"
        val mccMnc = telephonyManager?.simOperator?.trim() ?: ""

        return when {
            // 中文匹配
            name.contains("移动") -> "中国移动"
            name.contains("联通") -> "中国联通"
            name.contains("电信") -> "中国电信"
            name.contains("广电") -> "中国广电"
            // 英文缩写匹配
            name.equals("CMCC", ignoreCase = true) ||
                name.equals("China Mobile", ignoreCase = true) ||
                name.equals("中国移动", ignoreCase = true) -> "中国移动"
            name.equals("CUCC", ignoreCase = true) ||
                name.equals("China Unicom", ignoreCase = true) ||
                name.equals("中国联通", ignoreCase = true) -> "中国联通"
            name.equals("CTCC", ignoreCase = true) ||
                name.equals("China Telecom", ignoreCase = true) ||
                name.contains("电信", ignoreCase = true) -> "中国电信"
            name.equals("CBN", ignoreCase = true) ||
                name.contains("广电", ignoreCase = true) -> "中国广电"
            // 通过MCC+MNC识别
            else -> when (mccMnc) {
                "46000", "46002", "46004", "46007", "46008" -> "中国移动"
                "46001", "46006", "46009" -> "中国联通"
                "46003", "46005", "46011" -> "中国电信"
                "46015" -> "中国广电"
                else -> name.ifEmpty { "未知运营商" }
            }
        }
    }

    /**
     * 判断是否为双卡双待设备
     * 通过SubscriptionManager查询活跃的SIM卡数量
     *
     * @return true表示设备支持并插入了多张SIM卡
     */
    fun isDualSim(): Boolean {
        return try {
            val sm = subscriptionManager ?: return false
            val activeList = sm.activeSubscriptionInfoList
            activeList != null && activeList.size > 1
        } catch (e: SecurityException) {
            Log.w(TAG, "无权限查询SIM卡信息: ${e.message}")
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取所有活跃SIM卡的信息列表
     * 用于双卡设备上展示每个SIM卡的运营商信息
     *
     * @return CarrierInfo列表，每个元素代表一张SIM卡的信息
     */
    fun getAllCarrierInfos(): List<CarrierInfo> {
        val result = mutableListOf<CarrierInfo>()

        return try {
            val sm = subscriptionManager ?: return result
            val tm = telephonyManager ?: return result

            val activeList = sm.activeSubscriptionInfoList
            if (activeList.isNullOrEmpty()) {
                // 无活跃SIM卡列表时，返回默认卡信息
                detectCarrier()?.let { result.add(it) }
                return result
            }

            for (info in activeList) {
                val carrierName = info.carrierName?.toString() ?: "未知运营商"
                val simOperator = try {
                    info.mccString + info.mncString
                } catch (e: Exception) {
                    ""
                }

                result.add(
                    CarrierInfo(
                        carrierName = carrierName,
                        simOperator = simOperator,
                        networkType = getNetworkTypeName(tm.dataNetworkType),
                        signalStrength = -1,
                        subscriptionId = info.subscriptionId,
                        isDefault = info.subscriptionId == getDefaultSubscriptionId()
                    )
                )
            }

            result
        } catch (e: SecurityException) {
            Log.w(TAG, "无权限查询SIM卡信息: ${e.message}")
            detectCarrier()?.let { result.add(it) }
            result
        } catch (e: Exception) {
            Log.d(TAG, "获取多SIM卡信息失败: ${e.message}")
            detectCarrier()?.let { result.add(it) }
            result
        }
    }

    /**
     * 获取默认数据卡订阅ID
     * @return 默认SIM卡的订阅ID
     */
    private fun getDefaultSubscriptionId(): Int {
        return try {
            val sm = subscriptionManager ?: return SubscriptionManager.INVALID_SUBSCRIPTION_ID
            sm.activeSubscriptionInfoList?.firstOrNull()?.subscriptionId
                ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
        } catch (e: Exception) {
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
    }
}