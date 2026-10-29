package com.minibook.util

import com.minibook.domain.model.Budget
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import com.minibook.domain.model.Debt
import com.minibook.domain.model.DebtDirection
import com.minibook.domain.model.DebtKind
import com.minibook.domain.model.Ledger
import com.minibook.domain.model.SyncStatus
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionSource
import com.minibook.domain.model.TransactionType
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.json.Json

/**
 * 备份 JSON ↔ 领域模型转换门面（TASK-016，API §11 / DEC-004 / DEC-005）。
 *
 * - [toJson]：**明文 JSON**（DEC-005，`prettyPrint=true` 人工可 inspect）；
 *   `encodeDefaults=true` 保证 `version/scope/budgets` 等必填字段与空数组落盘
 *   （默认 `encodeDefaults=false` 会省略等于默认值的字段，导致 `version` 缺失）。
 * - [fromJson]：`ignoreUnknownKeys=true` 容忍未知字段（向前兼容）；字段类型/必填失败
 *   原样抛 [kotlinx.serialization.SerializationException]，由 ImportAllUseCase 捕获
 *   转 [com.minibook.domain.usecase.export.ImportAllUseCase.ImportResult.Failure]。
 * - 扩展函数做 domain ↔ DTO 逐字段转换（字段与 API §11.2 一致）：金额 `Long` 不转换、
 *   `date` 透传、枚举经 `.value` / `fromValue` 互转。
 * - **legacy 时间归一化（DEC-004）**：非 ISO 时间戳（epoch 毫秒等）→ `yyyy-MM-dd`
 *   （见 [normalizeDate]）。
 */
object BackupJsonMapper {

    /** 导出序列化 Json：明文 + 必填字段全量落盘（DEC-005）。 */
    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    /** 导入反序列化 Json：容忍未知字段，类型/必填仍严格。 */
    private val lenientJson = Json {
        ignoreUnknownKeys = true
    }

    /** 序列化 [backup] 为明文 JSON 字符串（DEC-005，可人工 inspect）。 */
    fun toJson(backup: BackupV1): String = prettyJson.encodeToString(BackupV1.serializer(), backup)

    /** 反序列化 JSON 字符串 → [BackupV1]；未知字段忽略，类型/必填失败抛异常。 */
    fun fromJson(json: String): BackupV1 = lenientJson.decodeFromString(BackupV1.serializer(), json)

    // ===== domain → DTO（导出） =====

    fun Ledger.toDto(): LedgerDto = LedgerDto(
        id = id,
        name = name,
        icon = icon,
        color = color,
        isDefault = isDefault,
        position = position,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun Category.toDto(): CategoryDto = CategoryDto(
        id = id,
        ledgerId = ledgerId,
        name = name,
        icon = icon,
        color = color,
        type = type.value,
        isBuiltIn = isBuiltIn,
        order = order,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun Transaction.toDto(): TransactionDto = TransactionDto(
        id = id,
        ledgerId = ledgerId,
        type = type.value,
        amount = amount,
        categoryId = categoryId,
        description = description,
        date = date,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = syncStatus.value,
        externalId = externalId,
        counterparty = counterparty,
        paymentMethod = paymentMethod,
        source = source.value,
        debtId = debtId,
    )

    fun Debt.toDto(): DebtDto = DebtDto(
        id = id,
        ledgerId = ledgerId,
        partyName = partyName,
        amount = amount,
        kind = kind.value,
        direction = direction.value,
        date = date,
        remark = remark,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = syncStatus.value,
    )

    fun Budget.toDto(): BudgetDto = BudgetDto(
        id = id,
        ledgerId = ledgerId,
        month = month,
        categoryId = categoryId,
        amount = amount,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    // ===== DTO → domain（导入） =====

    fun LedgerDto.toDomain(): Ledger = Ledger(
        id = id,
        name = name,
        icon = icon,
        color = color,
        isDefault = isDefault,
        position = position,
        createdAt = createdAt ?: TimeFormatter.nowIso(),
        updatedAt = updatedAt ?: TimeFormatter.nowIso(),
    )

    fun CategoryDto.toDomain(): Category = Category(
        id = id,
        ledgerId = ledgerId,
        name = name,
        icon = icon,
        color = color,
        type = CategoryType.fromValue(type)
            ?: throw IllegalArgumentException("非法分类类型：$type"),
        isBuiltIn = isBuiltIn,
        order = order,
        createdAt = createdAt ?: TimeFormatter.nowIso(),
        updatedAt = updatedAt ?: TimeFormatter.nowIso(),
    )

    fun TransactionDto.toDomain(): Transaction = Transaction(
        id = id,
        ledgerId = ledgerId,
        type = TransactionType.fromValue(type)
            ?: throw IllegalArgumentException("非法交易类型：$type"),
        amount = amount,
        categoryId = categoryId,
        description = description,
        date = normalizeDate(date),
        createdAt = createdAt ?: TimeFormatter.nowIso(),
        updatedAt = updatedAt ?: TimeFormatter.nowIso(),
        // 导入目标设备恒写 local（API §11 尾部说明；DTO.syncStatus 仅导出/可读用）。
        syncStatus = SyncStatus.LOCAL,
        externalId = externalId,
        counterparty = counterparty,
        paymentMethod = paymentMethod,
        // 旧备份缺 source 键（null）或值非法 → 兜底 MANUAL（P1-1 向后兼容）。
        source = source?.let { TransactionSource.fromValue(it) } ?: TransactionSource.MANUAL,
        // 旧备份无 debtId 键 → 未转债务（DEC-056 向后兼容）。
        debtId = debtId,
    )

    fun DebtDto.toDomain(): Debt = Debt(
        id = id,
        ledgerId = ledgerId,
        partyName = partyName,
        amount = amount,
        kind = DebtKind.fromValue(kind)
            ?: throw IllegalArgumentException("非法债务 kind：$kind"),
        direction = DebtDirection.fromValue(direction)
            ?: throw IllegalArgumentException("非法债务 direction：$direction"),
        date = normalizeDate(date),
        remark = remark,
        createdAt = createdAt ?: TimeFormatter.nowIso(),
        updatedAt = updatedAt ?: TimeFormatter.nowIso(),
        syncStatus = SyncStatus.LOCAL,
    )

    fun BudgetDto.toDomain(): Budget = Budget(
        id = id,
        ledgerId = ledgerId,
        month = month,
        categoryId = categoryId,
        amount = amount,
        createdAt = createdAt ?: TimeFormatter.nowIso(),
        updatedAt = updatedAt ?: TimeFormatter.nowIso(),
    )

    /**
     * legacy 时间归一化（DEC-004 / D-3）。
     *
     * - 已是 `yyyy-MM-dd` 本地日 → 原样返回；
     * - 纯数字串（epoch 毫秒，如 Taro/Dexie `Date.getTime()`）→ 系统时区本地日
     *   `yyyy-MM-dd`（`Instant.ofEpochMilli → LocalDate → toString`）；
     * - 其余视为 ISO-8601 完整时间戳 → 转系统时区本地日（[TimeFormatter.formatDate]）。
     *
     * @throws IllegalArgumentException/DateTimeException 三种均无法解析时（导入前置校验拦截）。
     */
    fun normalizeDate(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isNotEmpty() && trimmed.all { it.isDigit() }) {
            // legacy epoch 毫秒（epoch 秒不在本期范围，任务口径「epoch 毫秒等」）。
            return Instant.ofEpochMilli(trimmed.toLong())
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toString()
        }
        // yyyy-MM-dd 原样；ISO 完整时间戳转本地日；非法抛 DateTimeException。
        return TimeFormatter.formatDate(trimmed)
    }
}
