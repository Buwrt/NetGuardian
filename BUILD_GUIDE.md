# NetGuardian 流量卫士 - 构建指南

## 项目概述
NetGuardian 是一款纯本地离线的安卓流量管控应用，支持 Root 完整模式和无 Root Shizuku ADB 模式，适配 Android 11~14。

## 环境要求
- **Android Studio**: Hedgehog (2023.1.1) 或更新版本
- **JDK**: 17
- **Android SDK**: API Level 34
- **Gradle**: 8.5
- **Kotlin**: 1.9.22

## 构建步骤

### 1. 克隆项目
```bash
git clone <项目地址> NetGuardian
cd NetGuardian
```

### 2. 使用 Android Studio 构建
1. 打开 Android Studio
2. File → Open → 选择 NetGuardian 项目根目录
3. 等待 Gradle 同步完成（首次约需5-10分钟下载依赖）
4. Build → Build Bundle(s) / APK(s) → Build APK(s)
5. Release APK 输出路径: `app/build/outputs/apk/release/app-release.apk`

### 3. 使用命令行构建
```bash
# Linux/macOS
chmod +x gradlew
./gradlew assembleRelease

# APK 输出路径
# app/build/outputs/apk/release/app-release.apk

# Debug版本
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

## 签名配置
Release 构建需要签名，在 `app/build.gradle.kts` 中添加 `signingConfigs`:
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("your-keystore.jks")
            storePassword = "your-store-password"
            keyAlias = "your-key-alias"
            keyPassword = "your-key-password"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

## 项目架构

```
app/src/main/java/com/netguardian/app/
├── core/           # 核心引擎
│   ├── RootManager.kt          # Root权限管理与命令执行
│   ├── ShizukuManager.kt       # Shizuku ADB权限管理
│   ├── TrafficMonitorEngine.kt # 流量采集引擎（实时数据流）
│   ├── NetworkControlEngine.kt # 网络控制引擎（iptables/API）
│   ├── AntiLeakEngine.kt       # 防偷跑检测引擎
│   └── IptablesRule.kt         # iptables规则数据模型
├── data/           # 数据库层
│   ├── AppDatabase.kt         # Room数据库（单例）
│   ├── TrafficDao.kt           # 数据访问对象
│   └── AppInfo.kt              # 应用信息数据类
├── model/          # 数据模型
│   ├── AppTrafficInfo.kt       # 应用流量信息
│   ├── NetworkSpeedInfo.kt     # 网速信息
│   ├── TrafficRecord.kt        # 流量记录（Room实体）
│   ├── TrafficLimit.kt         # 流量限额（Room实体）
│   └── AppControlState.kt      # 应用控制状态（Room实体）
├── ui/             # UI层
│   ├── MainActivity.kt         # 主Activity
│   ├── theme/                  # Material3主题（深浅双套）
│   ├── component/              # 自定义组件（仪表盘/图表）
│   ├── screen/                 # 5个主页面
│   └── navigation/             # 导航路由
├── viewmodel/      # ViewModel层
├── service/        # 后台服务
├── receiver/       # 广播接收器
├── util/           # 工具类
└── NetGuardianApplication.kt   # Application入口
```

## 功能说明

### 双模式权限
| 功能 | Root模式 | Shizuku模式 | 无权限 |
|------|----------|-------------|--------|
| 实时网速 | 毫秒级精度 | 高精度 | 基础精度 |
| 流量统计 | 进程级细分 | 应用级 | 基础统计 |
| 联网控制 | iptables强制拦截 | 系统API | 不可用 |
| 防偷跑 | 深度检测+自动断网 | 基础检测 | 不可用 |
| 流量限额 | 超限自动断网 | 仅弹窗提醒 | 不可用 |

### 隐私安全
- 零联网行为，不收集任何设备信息
- 所有数据仅存储在本地设备
- 不含任何第三方广告SDK
- 不包含任何分析/追踪代码

## 注意事项
1. Root模式需要设备已获取Root权限（MagiskSU/KernelSU等）
2. Shizuku模式需要通过ADB或无线调试激活Shizuku服务
3. 流量统计功能需要用户授予「使用情况访问」权限
4. 前台服务需要通知权限
5. 此应用仅用于个人本机合规流量管理
