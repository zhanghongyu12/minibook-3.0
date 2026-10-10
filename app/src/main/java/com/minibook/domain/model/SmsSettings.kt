package com.minibook.domain.model

/** 通知方式（F-14 设计 §11.2；默认 [SILENT] 静默聚合）。 */
enum class SmsNotifyMode(val value: String) {
    /** 静默聚合：不发通知，只在 App 内累计「待确认」（默认；2026-10-10 起角标方案已撤销）。 */
    SILENT("silent"),

    /** 每日汇总：同日同发送方合并一条。 */
    DAILY("daily"),

    /** 逐条提醒。 */
    EACH("each"),
    ;

    companion object {
        fun fromValue(value: String?): SmsNotifyMode =
            entries.firstOrNull { it.value == value } ?: SILENT
    }
}

/**
 * 短信账单开关组（F-14 设计 §11.2 / DEC-025）。
 *
 * **全部默认关闭**（`autoScanEnabled` 默认开，但受总开关 `enabled` 门控——总开关关闭时零权限申请、
 * 零读取、零通知）。
 */
data class SmsSettings(
    val enabled: Boolean = false,
    val realtimeEnabled: Boolean = false,
    val autoImportEnabled: Boolean = false,
    val autoScanEnabled: Boolean = true,
    val notifyMode: SmsNotifyMode = SmsNotifyMode.SILENT,
    val lastWatermarkMs: Long? = null,
) {
    /**
     * L2 自动记账是否**当前可用**。
     *
     * 应用锁开启时 master key 为 auth-gated Keystore key，后台（无 Activity/生物识别）无法解密开库
     * → 自动写入不可用，UI 须置灰并说明原因（设计 §3.7；代码依据 `KeyManager` 双别名 +
     * `AppLockController.unlock(useAuth)`）。
     */
    fun autoImportUsable(appLockEnabled: Boolean): Boolean =
        enabled && autoImportEnabled && !appLockEnabled
}
