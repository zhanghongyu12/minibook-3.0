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
import com.minibook.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BackupJsonMapper 单测（TASK-016，API §11 / DEC-004 / DEC-005 / DEC-006）。
 *
 * 覆盖：BackupV1 toJson/fromJson 往返等价；明文 JSON 顶层字段齐全（DEC-005）；
 * legacy 兼容缺 ledgers 字段可解析且 ledgers==null（DEC-004）；
 * normalizeDate 纯 ISO 日期原样、epoch 毫秒归一化；5 类 domain↔DTO 扩展函数
 * 往返等价（含 remark=null、categoryId=null、isBuiltIn=true 边界）。
 */
class BackupJsonMapperTest {

    // ---- domain 样本（覆盖 remark=null、isBuiltIn=true、CONFLICT/SYNCED 等） ----

    private val ledger = Ledger(
        id = "ledger-1",
        name = "日常账本",
        icon = "icon1",
        color = "#FF0000",
        isDefault = true,
        position = 1,
        createdAt = "2026-08-18T06:14:30.123Z",
        updatedAt = "2026-08-18T06:14:30.123Z",
    )

    private val category = Category(
        id = "cat-1",
        ledgerId = "ledger-1",
        name = "餐饮",
        icon = "restaurant",
        color = "#FF0000",
        type = CategoryType.EXPENSE,
        isBuiltIn = true,
        order = 99,
        createdAt = "2026-08-18T06:14:30.123Z",
        updatedAt = "2026-08-18T06:14:30.123Z",
    )

    private val transaction = Transaction(
        id = "tx-1",
        ledgerId = "ledger-1",
        type = TransactionType.EXPENSE,
        amount = 1234L,
        categoryId = "cat-1",
        description = null,
        date = "2026-08-18",
        createdAt = "2026-08-18T06:14:30.123Z",
        updatedAt = "2026-08-18T06:14:30.123Z",
        syncStatus = SyncStatus.CONFLICT,
    )

    private val debt = Debt(
        id = "debt-1",
        ledgerId = "ledger-1",
        partyName = "张三",
        amount = 5000L,
        kind = DebtKind.REPAY,
        direction = DebtDirection.I_OWE,
        date = "2026-08-18",
        remark = null,
        createdAt = "2026-08-18T06:14:30.123Z",
        updatedAt = "2026-08-18T06:14:30.123Z",
        syncStatus = SyncStatus.SYNCED,
    )

    private val backup: BackupV1 = with(BackupJsonMapper) {
        BackupV1(
            ledgers = listOf(ledger.toDto()),
            categories = listOf(category.toDto()),
            transactions = listOf(transaction.toDto()),
            debts = listOf(debt.toDto()),
            budgets = emptyList(),
        )
    }

    // ---- BackupV1 JSON 往返 ----

    @Test
    fun `toJson fromJson 往返等价`() {
        val roundTrip = BackupJsonMapper.fromJson(BackupJsonMapper.toJson(backup))
        assertEquals(backup, roundTrip)
    }

    @Test
    fun `toJson 明文包含全部顶层字段`() {
        // DEC-005：encodeDefaults=true 保证 version/scope/budgets 等必填字段落盘。
        val json = BackupJsonMapper.toJson(backup)
        assertTrue("缺 ledgers", json.contains("\"ledgers\""))
        assertTrue("缺 categories", json.contains("\"categories\""))
        assertTrue("缺 transactions", json.contains("\"transactions\""))
        assertTrue("缺 debts", json.contains("\"debts\""))
        assertTrue("缺 budgets", json.contains("\"budgets\""))
        assertTrue("缺 version", json.contains("\"version\""))
        assertTrue("缺 scope", json.contains("\"scope\""))
    }

    @Test
    fun `legacy JSON 缺 ledgers 字段可解析为 null`() {
        // DEC-004：旧版（Taro）JSON 顶层无 ledgers 字段 → 反序列化为 null。
        val legacy = """
            {
              "version": 1,
              "scope": "all",
              "categories": [],
              "transactions": [],
              "debts": []
            }
        """.trimIndent()
        val parsed = BackupJsonMapper.fromJson(legacy)
        assertNull(parsed.ledgers)
    }

    @Test
    fun `legacy JSON 缺 budgets 字段走默认空表`() {
        val json = """
            {
              "version": 1,
              "scope": "all",
              "ledgers": [],
              "categories": [],
              "transactions": [],
              "debts": []
            }
        """.trimIndent()
        val parsed = BackupJsonMapper.fromJson(json)
        assertEquals(emptyList<BudgetDto>(), parsed.budgets)
    }

    // ---- normalizeDate（DEC-004 时间归一化） ----

    @Test
    fun `normalizeDate 纯 ISO 日期原样`() {
        assertEquals("2026-08-18", BackupJsonMapper.normalizeDate("2026-08-18"))
    }

    @Test
    fun `normalizeDate epoch 毫秒转本地日`() {
        // "0" = 1970-01-01T00:00:00Z → 系统时区本地日（仅断言格式）。
        val result = BackupJsonMapper.normalizeDate("0")
        assertTrue(result.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
    }

    // ---- domain ↔ DTO 扩展函数 ----

    @Test
    fun `Transaction toDto toDomain 往返等价`() {
        // 导入恒写 local：源 syncStatus 需为 LOCAL 才逐字段相等。
        val source = transaction.copy(syncStatus = SyncStatus.LOCAL)
        with(BackupJsonMapper) {
            assertEquals(source, source.toDto().toDomain())
        }
    }

    @Test
    fun `Transaction CONFLICT 导入后落 local`() {
        // 说明：DTO.syncStatus 仅导出/可读用，导入目标设备恒写 local（API §11 尾部）。
        with(BackupJsonMapper) {
            val imported = transaction.toDto().toDomain()
            assertEquals(transaction.id, imported.id)
            assertEquals(transaction.ledgerId, imported.ledgerId)
            assertEquals(transaction.type, imported.type)
            assertEquals(transaction.amount, imported.amount)
            assertEquals(transaction.categoryId, imported.categoryId)
            assertEquals(transaction.description, imported.description)
            assertEquals(transaction.date, imported.date)
            assertEquals(transaction.createdAt, imported.createdAt)
            assertEquals(transaction.updatedAt, imported.updatedAt)
            assertEquals(SyncStatus.LOCAL, imported.syncStatus)
        }
    }

    @Test
    fun `Debt toDto toDomain 往返等价`() {
        // remark=null、kind=REPAY、direction=I_OWE；源 syncStatus=LOCAL。
        val source = debt.copy(syncStatus = SyncStatus.LOCAL)
        with(BackupJsonMapper) {
            assertEquals(source, source.toDto().toDomain())
        }
    }

    @Test
    fun `Category isBuiltIn true toDto toDomain 往返等价`() {
        with(BackupJsonMapper) {
            assertEquals(category, category.toDto().toDomain())
        }
    }

    @Test
    fun `Ledger toDto toDomain 往返等价`() {
        with(BackupJsonMapper) {
            assertEquals(ledger, ledger.toDto().toDomain())
        }
    }

    @Test
    fun `Budget categoryId null 与分类预算往返等价`() {
        val totalBudget = Budget(
            id = "budget-1",
            ledgerId = "ledger-1",
            month = "2026-08",
            categoryId = null,
            amount = 100000L,
            createdAt = "2026-08-18T06:14:30.123Z",
            updatedAt = "2026-08-18T06:14:30.123Z",
        )
        val catBudget = totalBudget.copy(id = "budget-2", categoryId = "cat-1", amount = 50000L)
        with(BackupJsonMapper) {
            assertEquals(totalBudget, totalBudget.toDto().toDomain())
            assertEquals(catBudget, catBudget.toDto().toDomain())
        }
    }

    @Test
    fun `DTO 非法枚举值 toDomain 抛 IllegalArgumentException`() {
        val badTypeTx = TransactionDto(
            id = "tx-x",
            ledgerId = "ledger-1",
            type = "unknown-type",
            amount = 1L,
            categoryId = "cat-1",
            date = "2026-08-18",
        )
        with(BackupJsonMapper) {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                badTypeTx.toDomain()
            }
        }
    }
}
