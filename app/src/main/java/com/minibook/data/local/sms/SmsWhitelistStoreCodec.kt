package com.minibook.data.local.sms

import com.minibook.domain.model.SmsWhitelistEntry

/**
 * 白名单/来源条目编解码（F-14，设计 §3.8）。
 *
 * 纯 Kotlin、**无 Android 依赖** → JVM 可完整单测（DataStore 交互本身属仪器测试范畴）。
 * 格式：`sender|enabled(0/1)|confirmedCount|lastErrorMs(0=无)|importEnabled(0/1)`。
 *
 * **向后兼容（docs/15 第 2 条，2026-10-13）**：第 5 段是后加的；只有 4 段的旧行按
 * `importEnabled = true` 解码（老用户升级后默认「允许导入」，不静默漏账，也不需要数据迁移）。
 */
internal object SmsWhitelistStoreCodec {

    fun encode(entry: SmsWhitelistEntry): String =
        "${entry.sender}|${if (entry.autoEnabled) 1 else 0}|${entry.confirmedCount}|" +
            "${entry.lastErrorAtMs ?: 0}|${if (entry.importEnabled) 1 else 0}"

    /** 畸形行一律忽略（不崩溃）；结果按发送方升序。 */
    fun decode(raw: Set<String>): List<SmsWhitelistEntry> = raw.mapNotNull { line ->
        val parts = line.split('|')
        if (parts.size < 4 || parts[0].isEmpty()) return@mapNotNull null
        SmsWhitelistEntry(
            sender = parts[0],
            autoEnabled = parts[1] == "1",
            confirmedCount = parts[2].toIntOrNull() ?: 0,
            lastErrorAtMs = parts[3].toLongOrNull()?.takeIf { it > 0L },
            // 旧格式（4 段）没有这一段 ⇒ 默认允许导入。
            importEnabled = parts.getOrNull(4)?.let { it != "0" } ?: true,
        )
    }.sortedBy { it.sender }
}
