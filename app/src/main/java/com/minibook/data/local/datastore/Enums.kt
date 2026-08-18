package com.minibook.data.local.datastore

/**
 * 暗色模式设置（ARCHITECTURE §11）。
 *
 * 存储于 [SettingsDataStore]（Preferences DataStore），随 Flow 软切换；
 * 取值对齐 ARCHITECTURE §11：跟随系统 / 手动亮 / 手动暗。
 *
 * 说明：此枚举为「UI / 设置态」枚举，非领域核心模型（domain 层 TASK-006 专属），
 * 故置于 data/local/datastore 包内（TASK-005 决策），由 TASK-019 Theme / TASK-027 设置页消费。
 */
enum class DarkModeSetting {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        /** 由持久化字符串反解；未知值回退 SYSTEM（默认跟随系统）。 */
        fun fromStorage(value: String): DarkModeSetting =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * 应用锁闲置超时五档（ARCHITECTURE §9）。
 *
 * 取值：立即 / 30s / 1min / 5min / 永不。
 * 存储于 [SettingsDataStore]，由 TASK-017 AppLockController 读取并驱动闲置重锁计时；
 * 档位到具体毫秒阈值的映射由 TASK-017 消费方实现。
 */
enum class LockTimeout {
    IMMEDIATELY,
    THIRTY_SECONDS,
    ONE_MINUTE,
    FIVE_MINUTES,
    NEVER;

    companion object {
        /** 由持久化字符串反解；未知值回退 ONE_MINUTE（默认 1 分钟）。 */
        fun fromStorage(value: String): LockTimeout =
            entries.firstOrNull { it.name == value } ?: ONE_MINUTE
    }
}
