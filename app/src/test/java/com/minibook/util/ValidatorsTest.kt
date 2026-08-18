package com.minibook.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Validators 单测（TASK-010，DATABASE §9.4 边界与校验）。
 *
 * 覆盖：金额正数边界/拒绝 0 与负数/拒绝超上限、备注长度边界、
 * 姓名空与纯空白拒绝、金额小数位限制。
 */
class ValidatorsTest {

    // ---- validateAmount（§9.4：>0 且 ≤ MAX_AMOUNT_FEN） ----

    @Test
    fun `validateAmount 正数边界`() {
        assertEquals(true, Validators.validateAmount(1L))
        assertEquals(true, Validators.validateAmount(99_999_999_99L))
    }

    @Test
    fun `validateAmount 拒绝 0 与负数`() {
        assertEquals(false, Validators.validateAmount(0L))
        assertEquals(false, Validators.validateAmount(-1L))
        assertEquals(false, Validators.validateAmount(Long.MIN_VALUE))
    }

    @Test
    fun `validateAmount 拒绝超上限`() {
        assertEquals(false, Validators.validateAmount(99_999_999_99L + 1L))
        assertEquals(false, Validators.validateAmount(Long.MAX_VALUE))
    }

    // ---- validateNoteLength（≤ 200） ----

    @Test
    fun `validateNoteLength 边界`() {
        assertEquals(true, Validators.validateNoteLength(""))
        assertEquals(true, Validators.validateNoteLength("a".repeat(200)))
        assertEquals(false, Validators.validateNoteLength("a".repeat(201)))
    }

    // ---- validatePartyName（isNotBlank 非空） ----

    @Test
    fun `validatePartyName 空与纯空白拒绝`() {
        assertEquals(false, Validators.validatePartyName(""))
        assertEquals(false, Validators.validatePartyName("   "))
        assertEquals(false, Validators.validatePartyName("\n\t"))
        assertEquals(true, Validators.validatePartyName("张三"))
        assertEquals(true, Validators.validatePartyName(" Alice "))
    }

    // ---- validateDecimalPlaces（小数位 ≤ 2） ----

    @Test
    fun `validateDecimalPlaces 小数位限制`() {
        assertEquals(false, Validators.validateDecimalPlaces("12.345"))
        assertEquals(true, Validators.validateDecimalPlaces("12.34"))
        assertEquals(true, Validators.validateDecimalPlaces("12"))
        assertEquals(true, Validators.validateDecimalPlaces("0.1"))
        assertEquals(true, Validators.validateDecimalPlaces("12."))
        assertEquals(false, Validators.validateDecimalPlaces("0.001"))
        assertEquals(false, Validators.validateDecimalPlaces("1e-3"))
    }
}
