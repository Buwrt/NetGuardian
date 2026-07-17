package com.netguardian.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.netguardian.app.model.AppControlState
import com.netguardian.app.model.TrafficLimit
import com.netguardian.app.model.TrafficRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用数据库
 * 使用Room框架管理本地数据持久化，包含流量记录、流量限制和应用控制状态三张表
 */
@Database(
    entities = [
        TrafficRecord::class,
        TrafficLimit::class,
        AppControlState::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /**
     * 获取流量数据访问对象
     */
    abstract fun trafficDao(): TrafficDao

    /**
     * 清空并批量导入流量记录
     * 在数据库事务中执行，保证原子性：
     * 1. 清空 traffic_records 表中的所有旧数据
     * 2. 批量插入新的流量记录列表
     *
     * 适用于数据恢复场景：用户从备份文件恢复流量历史数据
     *
     * @param records 要导入的流量记录列表
     * @return 成功插入的记录数
     */
    suspend fun clearAndImportRecords(records: List<TrafficRecord>): Int {
        return withContext(Dispatchers.IO) {
            try {
                // 在事务中执行清空和导入
                val dao = trafficDao()
                withTransaction {
                    // 先清空所有旧的流量记录
                    dao.clearAllRecords()
                    // 批量插入新记录
                    if (records.isNotEmpty()) {
                        dao.insertRecords(records)
                    }
                }
                records.size
            } catch (e: Exception) {
                // 导入失败时返回-1
                -1
            }
        }
    }

    companion object {
        /** 数据库文件名 */
        private const val DB_NAME = "netguardian.db"

        /** 单例实例，使用@Volatile保证多线程可见性 */
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * 获取数据库单例
         * 使用双重检查锁定（DCL）模式确保线程安全
         *
         * @param context 应用上下文
         * @return 数据库实例
         */
        fun getInstance(context: Context): AppDatabase {
            // 第一次检查（无锁，快速路径）
            return INSTANCE ?: synchronized(this) {
                // 第二次检查（有锁，确保只创建一次）
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        /**
         * 构建数据库实例
         * @param context 应用上下文
         * @return 新创建的数据库实例
         */
        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                // 允许在主线程查询（仅用于简单查询，复杂操作应在IO线程）
                .allowMainThreadQueries()
                // 数据库被破坏时重建而不是崩溃
                .fallbackToDestructiveMigration()
                .build()
        }

        /**
         * 销毁数据库实例（仅用于测试）
         * @param context 应用上下文
         */
        fun destroyInstance(context: Context) {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
                // 可选：删除数据库文件
                context.deleteDatabase(DB_NAME)
            }
        }
    }
}