package com.netguardian.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netguardian.app.data.AppDatabase
import com.netguardian.app.model.TrafficRecord
import com.netguardian.app.ui.component.BarChartData
import com.netguardian.app.util.TrafficFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 统计页面ViewModel
 * 负责流量统计和历史数据展示：
 * 1. 日/周/月维度流量汇总
 * 2. 历史流量记录列表
 * 3. 图表数据格式化
 * 4. 应用级别流量对比数据
 */
class StatisticsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "StatisticsViewModel"

        /** 历史记录最大条数 */
        private const val MAX_HISTORY_RECORDS = 100
    }

    /** 数据库访问对象 */
    private val trafficDao = AppDatabase.getInstance(application).trafficDao()

    // ==================== 时间维度切换 ====================

    /** 当前选择的时间维度 */
    private val _timePeriod = MutableStateFlow(TimePeriod.DAILY)
    val timePeriod: StateFlow<TimePeriod> = _timePeriod.asStateFlow()

    // ==================== 月份选择 ====================

    /** 当前选中的月份，格式 "yyyy-MM" */
    private val _selectedMonth = MutableStateFlow(getCurrentMonth())
    val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    /** 当前选中月份的可读格式 "yyyy年MM月" */
    val selectedMonthDisplay: StateFlow<String> = _selectedMonth.map { month ->
        val parts = month.split("-")
        if (parts.size == 2) "${parts[0]}年${parts[1]}月" else month
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // ==================== 流量汇总数据 ====================

    /** 当前周期总下载流量（字节） */
    private val _totalRx = MutableStateFlow(0L)
    val totalRx: StateFlow<Long> = _totalRx.asStateFlow()

    /** 当前周期总上传流量（字节） */
    private val _totalTx = MutableStateFlow(0L)
    val totalTx: StateFlow<Long> = _totalTx.asStateFlow()

    /** 当前周期总流量（下载+上传） */
    val totalTraffic: StateFlow<Long> = combine(_totalRx, _totalTx) { rx, tx ->
        rx + tx
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // ==================== 图表数据 ====================

    /** 柱状图数据 - 各天/应用流量对比 */
    private val _barChartData = MutableStateFlow<List<BarChartData>>(emptyList())
    val barChartData: StateFlow<List<BarChartData>> = _barChartData.asStateFlow()

    /** 趋势图数据 - 每日流量趋势（日维度下是24小时，周维度是7天） */
    private val _trendData = MutableStateFlow<List<TrendDataPoint>>(emptyList())
    val trendData: StateFlow<List<TrendDataPoint>> = _trendData.asStateFlow()

    // ==================== 历史记录 ====================

    /** 历史流量记录列表 */
    private val _historyRecords = MutableStateFlow<List<TrafficRecord>>(emptyList())
    val historyRecords: StateFlow<List<TrafficRecord>> = _historyRecords.asStateFlow()

    // ==================== 加载状态 ====================

    /** 是否正在加载 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 错误信息 */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // 加载初始数据
        loadData()
    }

    /**
     * 根据当前时间维度加载所有数据
     */
    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                loadTrafficSummary()
                loadChartData()
                loadHistoryRecords()
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "加载统计数据失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 加载流量汇总数据
     * 根据时间维度查询对应日期范围的流量总和
     */
    private suspend fun loadTrafficSummary() {
        withContext(Dispatchers.IO) {
            try {
                val records = if (_timePeriod.value == TimePeriod.MONTHLY) {
                    // 月维度使用时间戳范围查询（支持跨月）
                    val (startTs, endTs) = getMonthTimeRange(_selectedMonth.value)
                    trafficDao.getRecordsByTimeRange(startTs, endTs)
                } else {
                    val (startDate, endDate) = getDateRange(_timePeriod.value)
                    trafficDao.getRecordsByDateRange(startDate, endDate).first()
                }

                _totalRx.value = records.sumOf { it.mobileRx + it.wifiRx }
                _totalTx.value = records.sumOf { it.mobileTx + it.wifiTx }
            } catch (e: Exception) {
                // 数据库可能还没有记录
                _totalRx.value = 0L
                _totalTx.value = 0L
            }
        }
    }

    /**
     * 加载图表数据
     * 根据时间维度生成不同的图表数据：
     * - 日视图：按小时统计
     * - 周视图：按天统计
     * - 月视图：按天统计
     */
    private suspend fun loadChartData() {
        withContext(Dispatchers.IO) {
            try {
                val records = if (_timePeriod.value == TimePeriod.MONTHLY) {
                    // 月维度使用时间戳范围查询（支持跨月）
                    val (startTs, endTs) = getMonthTimeRange(_selectedMonth.value)
                    trafficDao.getRecordsByTimeRange(startTs, endTs)
                } else {
                    val (startDate, endDate) = getDateRange(_timePeriod.value)
                    trafficDao.getRecordsByDateRange(startDate, endDate).first()
                }

                when (_timePeriod.value) {
                    TimePeriod.DAILY -> {
                        // 日视图 - 按天分组显示（最近7天）
                        val grouped = records.groupBy { it.date }
                        val chartData = grouped.map { (date, recs) ->
                            BarChartData(
                                label = date,
                                mobileTraffic = recs.sumOf { it.mobileRx + it.mobileTx },
                                wifiTraffic = recs.sumOf { it.wifiRx + it.wifiTx }
                            )
                        }.sortedBy { it.label }

                        _barChartData.value = chartData
                        _trendData.value = chartData.mapIndexed { index, data ->
                            TrendDataPoint(
                                label = data.label.takeLast(5), // MM-DD
                                value = data.totalTraffic,
                                mobileValue = data.mobileTraffic,
                                wifiValue = data.wifiTraffic
                            )
                        }
                    }
                    TimePeriod.WEEKLY -> {
                        // 周视图 - 按天分组（最近7天）
                        val grouped = records.groupBy { it.date }
                        val chartData = grouped.map { (date, recs) ->
                            BarChartData(
                                label = date,
                                mobileTraffic = recs.sumOf { it.mobileRx + it.mobileTx },
                                wifiTraffic = recs.sumOf { it.wifiRx + it.wifiTx }
                            )
                        }.sortedBy { it.label }

                        _barChartData.value = chartData
                        _trendData.value = chartData.map {
                            TrendDataPoint(
                                label = it.label.takeLast(5),
                                value = it.totalTraffic,
                                mobileValue = it.mobileTraffic,
                                wifiValue = it.wifiTraffic
                            )
                        }
                    }
                    TimePeriod.MONTHLY -> {
                        // 月视图 - 按天分组（最近30天）
                        val grouped = records.groupBy { it.date }
                        val chartData = grouped.map { (date, recs) ->
                            BarChartData(
                                label = date,
                                mobileTraffic = recs.sumOf { it.mobileRx + it.mobileTx },
                                wifiTraffic = recs.sumOf { it.wifiRx + it.wifiTx }
                            )
                        }.sortedBy { it.label }

                        _barChartData.value = chartData
                        _trendData.value = chartData.map {
                            TrendDataPoint(
                                label = it.label.takeLast(5),
                                value = it.totalTraffic,
                                mobileValue = it.mobileTraffic,
                                wifiValue = it.wifiTraffic
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _barChartData.value = emptyList()
                _trendData.value = emptyList()
            }
        }
    }

    /**
     * 加载历史流量记录
     * 获取最近的流量记录列表
     */
    private suspend fun loadHistoryRecords() {
        withContext(Dispatchers.IO) {
            try {
                val records = if (_timePeriod.value == TimePeriod.MONTHLY) {
                    // 月维度使用时间戳范围查询（支持跨月）
                    val (startTs, endTs) = getMonthTimeRange(_selectedMonth.value)
                    trafficDao.getRecordsByTimeRange(startTs, endTs)
                } else {
                    val (startDate, endDate) = getDateRange(_timePeriod.value)
                    trafficDao.getRecordsByDateRange(startDate, endDate).first()
                }
                // 去重并按时间降序
                _historyRecords.value = records
                    .distinctBy { "${it.packageName}_${it.date}" }
                    .sortedByDescending { it.timestamp }
                    .take(MAX_HISTORY_RECORDS)
            } catch (e: Exception) {
                _historyRecords.value = emptyList()
            }
        }
    }

    /**
     * 切换时间维度
     * @param period 新的时间维度
     */
    fun setTimePeriod(period: TimePeriod) {
        if (_timePeriod.value != period) {
            _timePeriod.value = period
            loadData()
        }
    }

    /**
     * 根据时间维度获取日期范围
     * @param period 时间维度
     * @return Pair<开始日期, 结束日期>，格式yyyy-MM-dd
     */
    private fun getDateRange(period: TimePeriod): Pair<String, String> {
        return when (period) {
            TimePeriod.DAILY -> TrafficFormatter.getDateRange(7)  // 最近7天
            TimePeriod.WEEKLY -> TrafficFormatter.getDateRange(7) // 最近7天
            TimePeriod.MONTHLY -> TrafficFormatter.getDateRange(30) // 最近30天
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    // ==================== 月份导航 ====================

    /**
     * 切换到上一个月
     */
    fun goToPreviousMonth() {
        val calendar = Calendar.getInstance().apply {
            time = SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(_selectedMonth.value)!!
            add(Calendar.MONTH, -1)
        }
        _selectedMonth.value = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)
        loadData()
    }

    /**
     * 切换到下一个月
     */
    fun goToNextMonth() {
        val calendar = Calendar.getInstance().apply {
            time = SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(_selectedMonth.value)!!
            add(Calendar.MONTH, 1)
        }
        _selectedMonth.value = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)
        loadData()
    }

    /**
     * 回到当前月份
     */
    fun goToCurrentMonth() {
        _selectedMonth.value = getCurrentMonth()
        loadData()
    }

    /**
     * 根据月份字符串计算该月的时间戳范围
     * @param month "yyyy-MM" 格式
     * @return Pair<月初时间戳, 月末时间戳>
     */
    private fun getMonthTimeRange(month: String): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            clear()
            val parts = month.split("-")
            set(Calendar.YEAR, parts[0].toInt())
            set(Calendar.MONTH, parts[1].toInt() - 1) // Calendar.MONTH 是 0-based
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        // 移动到下个月的第一天，再减1毫秒就是月末
        calendar.add(Calendar.MONTH, 1)
        val endTime = calendar.timeInMillis - 1
        return Pair(startTime, endTime)
    }

    /**
     * 获取当前月份字符串 "yyyy-MM"
     */
    private fun getCurrentMonth(): String {
        return SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
    }

    /**
     * 时间维度枚举
     */
    enum class TimePeriod(val displayName: String) {
        /** 日视图 */
        DAILY("日"),
        /** 周视图 */
        WEEKLY("周"),
        /** 月视图 */
        MONTHLY("月")
    }
}

/**
 * 趋势图数据点
 *
 * @param label 标签（日期/时间）
 * @param value 总流量值
 * @param mobileValue 移动流量值
 * @param wifiValue WiFi流量值
 */
data class TrendDataPoint(
    val label: String,
    val value: Long,
    val mobileValue: Long,
    val wifiValue: Long
)
