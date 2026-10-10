package com.minibook.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 领域枚举单测（TASK-006，DATABASE §4）。
 *
 * 覆盖：每个枚举 `fromValue(E.value) == E` 往返一致；非法串/空串返回 null；
 * value 常量精确匹配 DATABASE §4（expense/income、lend/repay、owe_me/i_owe、
 * local/syncing/synced/conflict）。
 */
class EnumRoundTripTest {

    // ---- 往返一致：fromValue(E.value) == E ----

    @Test
    fun `TransactionType fromValue 往返一致`() {
        TransactionType.entries.forEach { e ->
            assertEquals(e, TransactionType.fromValue(e.value))
        }
    }

    @Test
    fun `CategoryType fromValue 往返一致`() {
        CategoryType.entries.forEach { e ->
            assertEquals(e, CategoryType.fromValue(e.value))
        }
    }

    @Test
    fun `DebtKind fromValue 往返一致`() {
        DebtKind.entries.forEach { e ->
            assertEquals(e, DebtKind.fromValue(e.value))
        }
    }

    @Test
    fun `DebtDirection fromValue 往返一致`() {
        DebtDirection.entries.forEach { e ->
            assertEquals(e, DebtDirection.fromValue(e.value))
        }
    }

    @Test
    fun `SyncStatus fromValue 往返一致`() {
        SyncStatus.entries.forEach { e ->
            assertEquals(e, SyncStatus.fromValue(e.value))
        }
    }

    @Test
    fun `TransactionSource fromValue 往返一致`() {
        TransactionSource.entries.forEach { e ->
            assertEquals(e, TransactionSource.fromValue(e.value))
        }
    }

    // ---- 非法串 / 空串 → null ----

    @Test
    fun `fromValue 非法串与空串返回 null`() {
        assertNull(TransactionType.fromValue("非法串"))
        assertNull(TransactionType.fromValue(""))
        assertNull(TransactionType.fromValue("unknown-type"))

        assertNull(CategoryType.fromValue("非法串"))
        assertNull(CategoryType.fromValue(""))
        assertNull(CategoryType.fromValue("unknown-type"))

        assertNull(DebtKind.fromValue("非法串"))
        assertNull(DebtKind.fromValue(""))
        assertNull(DebtKind.fromValue("unknown-kind"))

        assertNull(DebtDirection.fromValue("非法串"))
        assertNull(DebtDirection.fromValue(""))
        assertNull(DebtDirection.fromValue("unknown-direction"))

        assertNull(SyncStatus.fromValue("非法串"))
        assertNull(SyncStatus.fromValue(""))
        assertNull(SyncStatus.fromValue("unknown-status"))

        assertNull(TransactionSource.fromValue("非法串"))
        assertNull(TransactionSource.fromValue(""))
        assertNull(TransactionSource.fromValue("unknown-source"))
    }

    // ---- value 常量精确匹配 DATABASE §4 ----

    @Test
    fun `TransactionType value 常量匹配 DATABASE 4`() {
        assertEquals("expense", TransactionType.EXPENSE.value)
        assertEquals("income", TransactionType.INCOME.value)
    }

    @Test
    fun `CategoryType value 常量匹配 DATABASE 4`() {
        assertEquals("expense", CategoryType.EXPENSE.value)
        assertEquals("income", CategoryType.INCOME.value)
    }

    @Test
    fun `DebtKind value 常量匹配 DATABASE 4`() {
        assertEquals("lend", DebtKind.LEND.value)
        assertEquals("repay", DebtKind.REPAY.value)
    }

    @Test
    fun `DebtDirection value 常量匹配 DATABASE 4`() {
        assertEquals("owe_me", DebtDirection.OWE_ME.value)
        assertEquals("i_owe", DebtDirection.I_OWE.value)
    }

    @Test
    fun `SyncStatus value 常量匹配 DATABASE 4`() {
        assertEquals("local", SyncStatus.LOCAL.value)
        assertEquals("syncing", SyncStatus.SYNCING.value)
        assertEquals("synced", SyncStatus.SYNCED.value)
        assertEquals("conflict", SyncStatus.CONFLICT.value)
    }

    @Test
    fun `TransactionSource value 常量匹配 DEC-018`() {
        assertEquals("manual", TransactionSource.MANUAL.value)
        assertEquals("wechat_import", TransactionSource.WECHAT_IMPORT.value)
        assertEquals("alipay_import", TransactionSource.ALIPAY_IMPORT.value)
        assertEquals("sms_import", TransactionSource.SMS_IMPORT.value)   // F-14
    }

    // ---- 枚举数量守卫（防增删导致漏测） ----

    @Test
    fun `枚举条目数量与规格一致`() {
        assertEquals(2, TransactionType.entries.size)
        assertEquals(2, CategoryType.entries.size)
        assertEquals(2, DebtKind.entries.size)
        assertEquals(2, DebtDirection.entries.size)
        assertEquals(4, SyncStatus.entries.size)
        assertEquals(4, TransactionSource.entries.size)   // F-14 增补 SMS_IMPORT
        assertEquals(5, ImportSkipReason.entries.size)
    }
}
