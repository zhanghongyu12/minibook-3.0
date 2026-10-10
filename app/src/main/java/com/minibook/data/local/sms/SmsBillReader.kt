package com.minibook.data.local.sms

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import com.minibook.di.IoDispatcher
import com.minibook.domain.model.SmsMessageRow
import com.minibook.domain.model.SmsMessageType
import com.minibook.domain.usecase.sms.SmsBillParser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * 短信读取层（F-14，设计 §3.2 / §3.10；DEC-025）。
 *
 * **全 App 唯一的短信 I/O 边界**：只读 `content://sms`，分页游标 `date ASC, _id ASC`，
 * 批次 200（防 CursorWindow 2MB 溢出）；水位 [suggestedWatermarkMs] + [OVERLAP_MS] 重叠回扫
 * （覆盖关机/后台被清理/广播丢失期间的迟到短信）。
 *
 * 只读语义（DEC-026）：**不删除、不修改、不标记已读**任何短信；不申请默认短信应用角色。
 * 权限：调用方需已获得 `READ_SMS`（未授予时本类会抛 SecurityException，由上层转明确提示）。
 */
@Singleton
class SmsBillReader @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val resolver: ContentResolver get() = context.contentResolver

    companion object {
        /** 水位回扫重叠窗口：3 天（设计 §3.3）。 */
        const val OVERLAP_MS: Long = 3L * 24 * 60 * 60 * 1000

        /** 分页批次大小：防 CursorWindow 溢出（设计 §6）。 */
        const val PAGE_SIZE: Int = 200

        /** 单次扫描上限（防极端库拖死 UI；超出部分由水位分次补齐）。 */
        const val MAX_MESSAGES: Int = 20_000

        internal val PROJECTION = arrayOf("_id", "address", "date", "type", "sub_id", "body")

        /**
         * 内容哈希：`sha256(address|receivedAtMs|规范化正文)`。
         *
         * 与 `externalId`（含日期）不同：**内容哈希不含日期**，用于「`_id` 被系统复用」判重
         * （设计 §3.3）；同一短信重投递时稳定。无 `_id` 的广播接收器复用同一式。
         */
        internal fun contentHashOf(address: String, receivedAtMs: Long, body: String): String {
            val payload = "$address|$receivedAtMs|${SmsBillParser.normalizeForHash(body)}"
            val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
            return buildString(digest.size * 2) {
                for (b in digest) {
                    val v = b.toInt() and 0xFF
                    append("0123456789abcdef"[v ushr 4])
                    append("0123456789abcdef"[v and 0x0F])
                }
            }
        }
    }

    /** 全量读取（L0 手动扫描；按时间升序）。 */
    suspend fun readAll(): List<SmsMessageRow> = withContext(ioDispatcher) {
        readPaged(dateFromMs = null)
    }

    /** 增量读取（L3 补扫）：从水位回退 [OVERLAP_MS] 开始。 */
    suspend fun readSince(watermarkMs: Long): List<SmsMessageRow> = withContext(ioDispatcher) {
        readPaged(dateFromMs = (watermarkMs - OVERLAP_MS).coerceAtLeast(0L))
    }

    /**
     * **严格增量读取**（L0 手动「增量扫描」，2026-10-10 用户要求两种扫描方式）：只读水位**之后**的短信。
     *
     * 与 [readSince] 的区别（**两者不可互相替代**）：
     * - [readSince] 回退 [OVERLAP_MS]（3 天）是给 L3 补扫做容错的 —— 宁可重读也不漏；
     * - 用户手动点「增量扫描」时要求「下次再重新导入就不要再标记出来」，重读 3 天会把已看过 /
     *   已处理的短信又翻出来，故这里严格 `date > 水位`（`>= 水位 + 1`）。
     *
     * 不遗漏由「全量扫描」兜底：严格增量**跳过**的历史，永远能用全量重新列出来。
     */
    suspend fun readAfter(watermarkMs: Long): List<SmsMessageRow> = withContext(ioDispatcher) {
        readPaged(dateFromMs = (watermarkMs + 1L).coerceAtLeast(1L))
    }

    /**
     * 建议水位（下次补扫起点）= 库内最新消息时间戳；空库返回 null。
     * 用「时间戳」而非 `max(_id)`：`_id` 会随删除复用，时间戳单调性更适合做水位。
     */
    suspend fun suggestedWatermarkMs(): Long? = withContext(ioDispatcher) {
        query { resolver.query(Telephony.Sms.CONTENT_URI, arrayOf("date"), selection(), null, "date DESC LIMIT 1") }
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
    }

    /** 库内总条数（外部 oracle 对齐用，设计 §3.10）。 */
    suspend fun countAll(): Int = withContext(ioDispatcher) {
        query { resolver.query(Telephony.Sms.CONTENT_URI, arrayOf("_id"), selection(), null, null) }
            ?.use { it.count } ?: 0
    }

    // ------------------------------------------------------------------ 实现

    /**
     * 扫件类型区间 selection：`type >= 1 AND type <= 6`（[SmsMessageType.INBOX]..[SmsMessageType.QUEUED]）。
     *
     * ⚠️ 真机教训（2026-10-10，小米 HyperOS/Android 16）：此处曾写成 `"$types.first"`，
     * 其中 `types` 是 `IntRange`——Kotlin 模板里 `$types` 只展开变量本身（`1..6`），
     * `.first` 成了**字面文本**，实际下发 `type >= 1..6.first` → 设备端
     * `SQLiteException: near ".6": syntax error`，表现为「读取失败」。
     * **必须用 `${...}` 显式取首尾**（或直接用常量，如下）。
     */
    private fun selection(dateFromMs: Long? = null): String {
        val base = "type >= ${SmsMessageType.INBOX} AND type <= ${SmsMessageType.QUEUED}"
        return if (dateFromMs == null) base else "$base AND date >= $dateFromMs"
    }

    private suspend fun readPaged(dateFromMs: Long?): List<SmsMessageRow> {
        val rows = ArrayList<SmsMessageRow>(minOf(PAGE_SIZE * 4, MAX_MESSAGES))
        var offset = 0
        while (rows.size < MAX_MESSAGES) {
            coroutineContext.ensureActive()
            val order = "date ASC, _id ASC LIMIT $PAGE_SIZE OFFSET $offset"
            val page = query {
                resolver.query(Telephony.Sms.CONTENT_URI, PROJECTION, selection(dateFromMs), null, order)
            }?.use { cursor -> readPage(cursor) } ?: emptyList()
            if (page.isEmpty()) break
            rows += page
            if (page.size < PAGE_SIZE) break
            offset += PAGE_SIZE
        }
        return rows
    }

    private fun readPage(cursor: Cursor): List<SmsMessageRow> {
        if (cursor.count == 0) return emptyList()
        val idIdx = cursor.getColumnIndexOrThrow("_id")
        val addrIdx = cursor.getColumnIndexOrThrow("address")
        val dateIdx = cursor.getColumnIndexOrThrow("date")
        val typeIdx = cursor.getColumnIndexOrThrow("type")
        val subIdx = cursor.getColumnIndex("sub_id")
        val bodyIdx = cursor.getColumnIndexOrThrow("body")

        val out = ArrayList<SmsMessageRow>(cursor.count)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIdx)
            val address = cursor.getString(addrIdx).orEmpty()
            val receivedAtMs = cursor.getLong(dateIdx)
            val type = cursor.getInt(typeIdx)
            val subId = if (subIdx >= 0 && !cursor.isNull(subIdx)) cursor.getInt(subIdx) else null
            val body = cursor.getString(bodyIdx).orEmpty()
            out += SmsMessageRow(
                id = id,
                address = address,
                receivedAtMs = receivedAtMs,
                type = type,
                subId = subId,
                body = body,
                contentHash = contentHashOf(address, receivedAtMs, body),
            )
        }
        return out
    }

    /** 查询包装：provider 可能抛 SecurityException/IllegalArgumentException，交由上层转明确提示。 */
    private fun <T> query(block: () -> T?): T? = block()
}
