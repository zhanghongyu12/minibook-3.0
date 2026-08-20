package com.minibook.data.mapper

import com.minibook.data.local.db.entity.BudgetEntity
import com.minibook.data.local.db.entity.CategoryEntity
import com.minibook.data.local.db.entity.DebtEntity
import com.minibook.data.local.db.entity.LedgerEntity
import com.minibook.data.local.db.entity.TransactionEntity
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
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Mapper Entity↔Domain 往返单测（TASK-008，DATABASE §4）。
 *
 * 覆盖：Transaction/Debt/Category/Ledger/Budget 五类 domain → toEntity → toDomain
 * 逐字段往返全等；Entity 的 String 枚举列精确等于 `enum.value`；
 * 必覆盖边界：Transaction syncStatus=CONFLICT、Debt kind=LEND×direction=I_OWE、
 * remark=null、categoryId=null、isBuiltIn=true。
 */
class MapperRoundTripTest {

    // ===== Transaction =====

    @Test
    fun `Transaction CONFLICT 往返全等`() {
        val domain = Transaction(
            id = "tx-1",
            ledgerId = "ledger-1",
            type = TransactionType.EXPENSE,
            amount = 12345L,
            categoryId = "cat-1",
            description = null,
            date = "2026-08-18",
            createdAt = "2026-08-18T06:14:30.123Z",
            updatedAt = "2026-08-18T06:14:30.123Z",
            syncStatus = SyncStatus.CONFLICT,
        )
        val entity = TransactionMapper.toEntity(domain)
        // toEntity 枚举落盘为其 value 常量串
        assertEquals("expense", entity.type)
        assertEquals("conflict", entity.syncStatus)
        assertEquals(domain, TransactionMapper.toDomain(entity))
    }

    @Test
    fun `Transaction 各同步状态往返全等`() {
        SyncStatus.entries.forEach { status ->
            val domain = Transaction(
                id = "tx-${status.value}",
                ledgerId = "ledger-1",
                type = TransactionType.INCOME,
                amount = 100L,
                categoryId = "cat-1",
                description = "备注",
                date = "2026-08-18",
                createdAt = "2026-08-18T06:14:30.123Z",
                updatedAt = "2026-08-18T06:14:30.123Z",
                syncStatus = status,
            )
            assertEquals(domain, TransactionMapper.toDomain(TransactionMapper.toEntity(domain)))
        }
    }

    @Test
    fun `Transaction 非法枚举串 toDomain 抛 IllegalArgumentException`() {
        val entity = TransactionEntity(
            id = "tx-1",
            ledgerId = "ledger-1",
            type = "expense-unknown",
            amount = 1L,
            categoryId = "cat-1",
            description = null,
            date = "2026-08-18",
            createdAt = "2026-08-18T06:14:30.123Z",
            updatedAt = "2026-08-18T06:14:30.123Z",
        )
        assertThrows(IllegalArgumentException::class.java) {
            TransactionMapper.toDomain(entity)
        }
    }

    // ===== Debt =====

    @Test
    fun `Debt kind LEND direction I_OWE 往返全等`() {
        val domain = Debt(
            id = "debt-1",
            ledgerId = "ledger-1",
            partyName = "张三",
            amount = 5000L,
            kind = DebtKind.LEND,
            direction = DebtDirection.I_OWE,
            date = "2026-08-18",
            remark = null,
            createdAt = "2026-08-18T06:14:30.123Z",
            updatedAt = "2026-08-18T06:14:30.123Z",
            syncStatus = SyncStatus.CONFLICT,
        )
        val entity = DebtMapper.toEntity(domain)
        assertEquals("lend", entity.kind)
        assertEquals("i_owe", entity.direction)
        assertEquals("conflict", entity.syncStatus)
        assertEquals(domain, DebtMapper.toDomain(entity))
    }

    @Test
    fun `Debt 四种 kind x direction 组合往返全等`() {
        for (kind in DebtKind.entries) {
            for (direction in DebtDirection.entries) {
                val domain = Debt(
                    id = "debt-${kind.value}-${direction.value}",
                    ledgerId = "ledger-1",
                    partyName = "张三",
                    amount = 5000L,
                    kind = kind,
                    direction = direction,
                    date = "2026-08-18",
                    remark = "备注",
                    createdAt = "2026-08-18T06:14:30.123Z",
                    updatedAt = "2026-08-18T06:14:30.123Z",
                    syncStatus = SyncStatus.SYNCED,
                )
                val entity = DebtMapper.toEntity(domain)
                assertEquals(kind.value, entity.kind)
                assertEquals(direction.value, entity.direction)
                assertEquals(domain, DebtMapper.toDomain(entity))
            }
        }
    }

    // ===== Category =====

    @Test
    fun `Category isBuiltIn true 往返全等`() {
        val domain = Category(
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
        val entity = CategoryMapper.toEntity(domain)
        assertEquals("expense", entity.type)
        assertEquals(true, entity.isBuiltIn)
        assertEquals(99, entity.order)
        assertEquals(domain, CategoryMapper.toDomain(entity))
    }

    // ===== Ledger =====

    @Test
    fun `Ledger 往返全等`() {
        val domain = Ledger(
            id = "ledger-1",
            name = "日常账本",
            icon = "icon1",
            color = "#FF0000",
            isDefault = true,
            position = 2,
            createdAt = "2026-08-18T06:14:30.123Z",
            updatedAt = "2026-08-18T06:14:30.123Z",
        )
        val entity = LedgerMapper.toEntity(domain)
        assertEquals(true, entity.isDefault)
        assertEquals(2, entity.position)
        assertEquals(domain, LedgerMapper.toDomain(entity))
    }

    // ===== Budget =====

    @Test
    fun `Budget categoryId null 月度总预算往返全等`() {
        val domain = Budget(
            id = "budget-1",
            ledgerId = "ledger-1",
            month = "2026-08",
            categoryId = null,
            amount = 100000L,
            createdAt = "2026-08-18T06:14:30.123Z",
            updatedAt = "2026-08-18T06:14:30.123Z",
        )
        val entity = BudgetMapper.toEntity(domain)
        assertEquals(null, entity.categoryId)
        assertEquals(domain, BudgetMapper.toDomain(entity))
    }

    @Test
    fun `Budget 分类预算 categoryId 非空往返全等`() {
        val domain = Budget(
            id = "budget-2",
            ledgerId = "ledger-1",
            month = "2026-08",
            categoryId = "cat-1",
            amount = 50000L,
            createdAt = "2026-08-18T06:14:30.123Z",
            updatedAt = "2026-08-18T06:14:30.123Z",
        )
        val entity = BudgetMapper.toEntity(domain)
        assertEquals("cat-1", entity.categoryId)
        assertEquals(domain, BudgetMapper.toDomain(entity))
    }
}
