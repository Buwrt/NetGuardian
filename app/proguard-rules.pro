# NetGuardian 流量卫士 - ProGuard 优化规则

# ============================================================
# Kotlin
# ============================================================
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# ============================================================
# Coroutines
# ============================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ============================================================
# Room 数据库
# ============================================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ============================================================
# Shizuku API
# ============================================================
-keep class rikka.shizuku.** { *; }
-keep class rikka.parcelablelist.** { *; }
-dontwarn rikka.shizuku.**

# ============================================================
# MPAndroidChart
# ============================================================
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# ============================================================
# DataStore
# ============================================================
-keepclassmembers class androidx.datastore.preferences.** {
    *;
}

# ============================================================
# Compose
# ============================================================
-dontwarn androidx.compose.**

# ============================================================
# 模型类保持序列化
# ============================================================
-keep class com.netguardian.app.model.** { *; }
-keep class com.netguardian.app.data.** { *; }
-keep class com.netguardian.app.core.ProcessTrafficData { *; }
-keep class com.netguardian.app.core.IptablesRule { *; }

# ============================================================
# 保留行号信息（便于调试）
# ============================================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
