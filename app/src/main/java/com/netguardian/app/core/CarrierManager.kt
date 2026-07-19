package com.netguardian.app.core

import android.content.Context
import android.os.Build
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
 * 数据源优先级：SubscriptionManager > TelephonyManager
 * 运营商识别优先级：MCC+MNC > 名称关键字匹配
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
     * 优先使用 SubscriptionManager（Android标准SIM卡信息API，数据由系统telephony框架统一管理），
     * 失败时回退到 TelephonyManager
     *
     * @return CarrierInfo 检测成功时返回运营商信息，失败返回null
     */
    fun detectCarrier(): CarrierInfo? {
        return try {
            val sm = subscriptionManager
            if (sm != null) {
                // 优先使用 SubscriptionManager（最可靠）
                val infoList = sm.activeSubscriptionInfoList
                if (!infoList.isNullOrEmpty()) {
                    val defaultSubId = getDefaultSubscriptionId()

                    val info = infoList.firstOrNull { it.subscriptionId == defaultSubId }
                        ?: infoList.firstOrNull()
                        ?: return null

                    val displayName = info.carrierName?.toString()?.trim() ?: ""
                    val mcc = info.mccString?.trim() ?: ""
                    val mnc = info.mncString?.trim() ?: ""
                    val mccMnc = mcc + mnc

                    val carrierInfo = CarrierInfo(
                        carrierName = standardizeCarrierName(displayName, mccMnc),
                        simOperator = mccMnc,
                        networkType = getNetworkTypeName(getCurrentNetworkType()),
                        signalStrength = -1,
                        subscriptionId = info.subscriptionId,
                        isDefault = true
                    )
                    _carrierInfo.value = carrierInfo
                    Log.d(TAG, "运营商(SubMgr): ${carrierInfo.carrierName}, SIM: $mccMnc, 网络: ${carrierInfo.networkType}")
                    return carrierInfo
                }
            }

            // Fallback: 使用 TelephonyManager
            detectCarrierFallback()
        } catch (e: SecurityException) {
            Log.w(TAG, "无权限检测运营商: ${e.message}")
            detectCarrierFallback()
        } catch (e: Exception) {
            Log.d(TAG, "SubscriptionManager方式失败: ${e.message}")
            detectCarrierFallback()
        }
    }

    /** TelephonyManager fallback */
    private fun detectCarrierFallback(): CarrierInfo? {
        val tm = telephonyManager ?: return null
        return try {
            if (tm.simState != TelephonyManager.SIM_STATE_READY) return null
            val operator = tm.simOperator?.trim() ?: ""
            val rawName = tm.simOperatorName?.trim() ?: ""
            val name = if (rawName.isNotEmpty()) rawName else "未知运营商"

            val info = CarrierInfo(
                carrierName = standardizeCarrierName(name, operator),
                simOperator = operator,
                networkType = getNetworkTypeName(getCurrentNetworkType()),
                signalStrength = -1,
                subscriptionId = getDefaultSubscriptionId(),
                isDefault = true
            )
            _carrierInfo.value = info
            Log.d(TAG, "运营商(TM fallback): ${info.carrierName}, SIM: $operator")
            info
        } catch (e: Exception) {
            Log.d(TAG, "TelephonyManager fallback失败: ${e.message}")
            null
        }
    }

    /**
     * 获取当前网络类型
     * 优先通过 SubscriptionManager 获取（API 30+），回退到 TelephonyManager
     */
    private fun getCurrentNetworkType(): Int {
        return try {
            // 优先通过 SubscriptionManager 获取
            val sm = subscriptionManager ?: return TelephonyManager.NETWORK_TYPE_UNKNOWN
            val infoList = sm.activeSubscriptionInfoList
            val defaultSubId = getDefaultSubscriptionId()
            val info = infoList?.firstOrNull { it.subscriptionId == defaultSubId }

            // 直接通过 TelephonyManager 获取网络类型（最可靠）
            @Suppress("DEPRECATION")
            return telephonyManager?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
        } catch (e: Exception) {
            @Suppress("DEPRECATION")
            telephonyManager?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
        }
    }

    /**
     * 标准化运营商名称
     * 综合使用显示名称和MCC+MNC来准确识别运营商
     * 优先级：MCC+MNC > 名称关键字匹配
     */
    private fun standardizeCarrierName(displayName: String, mccMnc: String): String {
        // 优先用 MCC+MNC 判断（最准确，不会受ROM定制影响）
        return when (mccMnc) {
            "46000", "46002", "46004", "46007", "46008" -> "中国移动"
            "46001", "46006", "46009" -> "中国联通"
            "46003", "46005", "46011" -> "中国电信"
            "46015" -> "中国广电"
            else -> {
                // MCC+MNC 无法识别时，通过名称关键字匹配
                val name = displayName.trim()
                when {
                    name.isEmpty() -> "未知运营商"
                    name.contains("移动", ignoreCase = true) ||
                        name.contains("CMCC", ignoreCase = true) ||
                        name.contains("China Mobile", ignoreCase = true) ||
                        name.equals("中国移动", ignoreCase = true) -> "中国移动"
                    name.contains("联通", ignoreCase = true) ||
                        name.contains("CUCC", ignoreCase = true) ||
                        name.contains("China Unicom", ignoreCase = true) ||
                        name.contains("Unicom", ignoreCase = true) -> "中国联通"
                    name.contains("电信", ignoreCase = true) ||
                        name.contains("CTCC", ignoreCase = true) ||
                        name.contains("China Telecom", ignoreCase = true) ||
                        name.contains("Telecom", ignoreCase = true) -> "中国电信"
                    name.contains("广电", ignoreCase = true) ||
                        name.contains("CBN", ignoreCase = true) -> "中国广电"
                    else -> name // 返回原始名称
                }
            }
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
     * 使用 standardizeCarrierName() 进行标准化
     *
     * @return 标准运营商名称："中国移动" / "中国联通" / "中国电信" / 原始名称 / "未知运营商"
     */
    fun getCarrierDisplayName(): String {
        val tm = telephonyManager ?: return "未知运营商"
        return try {
            val name = tm.simOperatorName?.trim() ?: ""
            val mccMnc = tm.simOperator?.trim() ?: ""
            standardizeCarrierName(name, mccMnc)
        } catch (e: Exception) {
            "未知运营商"
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
                val carrierName = info.carrierName?.toString()?.trim() ?: "未知运营商"
                val simOperator = try {
                    info.mccString + info.mncString
                } catch (e: Exception) {
                    ""
                }

                // 获取该 SIM 卡的网络类型
                val subTm = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // API 31+ 使用 createForSubscriptionId
                        telephonyManager?.createForSubscriptionId(info.subscriptionId)
                    } else {
                        telephonyManager
                    }
                } catch (e: Exception) {
                    telephonyManager
                }
                @Suppress("DEPRECATION")
                val networkType = subTm?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN

                result.add(
                    CarrierInfo(
                        carrierName = standardizeCarrierName(carrierName, simOperator),
                        simOperator = simOperator,
                        networkType = getNetworkTypeName(networkType),
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
            // API 24+ 使用标准方法获取默认数据订阅ID
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                @Suppress("DEPRECATION")
                sm.defaultDataSubscriptionId
            } else {
                sm.activeSubscriptionInfoList?.firstOrNull()?.subscriptionId
                    ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
            }
        } catch (e: Exception) {
            try {
                val sm = subscriptionManager ?: return SubscriptionManager.INVALID_SUBSCRIPTION_ID
                sm.activeSubscriptionInfoList?.firstOrNull()?.subscriptionId
                    ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
            } catch (e2: Exception) {
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            }
        }
    }
}
