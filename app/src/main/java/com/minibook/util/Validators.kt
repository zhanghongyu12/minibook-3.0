package com.minibook.util

/**
 * 表单/输入校验门面，对齐 DATABASE §9.4（统一校验入口）。
 *
 * 全部为纯函数、无副作用：仅判定「是否合法」并返回 Boolean，不抛异常、
 * 不修改入参。金额/备注/姓名规则与 DATABASE §4.x 字段约束一一对应。
 */
object Validators {

    /** 金额上限（分）：`99_999_999_99` 分 ≈ 99,999,999.99 元（DATABASE §9.4）。 */
    const val MAX_AMOUNT_FEN: Long = 99_999_999_99L

    /** 备注/description 最大长度（DATABASE §4.1 description ≤ 200 字符）。 */
    const val MAX_NOTE_LENGTH: Int = 200

    /**
     * 金额为正且不超上限（DATABASE §9.4：拒绝 0 与负数、拒绝超限保存）。
     *
     * @return `fen > 0 && fen <= [MAX_AMOUNT_FEN]`。
     */
    fun validateAmount(fen: Long): Boolean = fen > 0L && fen <= MAX_AMOUNT_FEN

    /** 备注长度 ≤ [MAX_NOTE_LENGTH]；空串（可空字段）视为合法。 */
    fun validateNoteLength(s: String): Boolean = s.length <= MAX_NOTE_LENGTH

    /** 对方姓名/分类名非空：`isNotBlank()` 拒绝空串与纯空白（DATABASE §4.2）。 */
    fun validatePartyName(s: String): Boolean = s.isNotBlank()

    /**
     * 金额输入小数位 ≤ 2（DATABASE §9.4：输入层即拦截第 3 位小数）。
     *
     * 含指数标记 `e/E`（科学计数法，如 `1e-3`）一律视为非法金额输入；
     * 无小数点为合法；`.` 后含非数字视为非法；`"12."` 视为 0 位小数合法。
     *
     * @return `"12.345"` → false；`"12.34"`/`"12"`/`"12."` → true。
     */
    fun validateDecimalPlaces(input: String): Boolean {
        if (input.any { it == 'e' || it == 'E' }) return false
        val dotIndex = input.indexOf('.')
        if (dotIndex < 0) return true
        val fraction = input.substring(dotIndex + 1)
        if (fraction.isEmpty()) return true
        if (fraction.any { !it.isDigit() }) return false
        return fraction.length <= 2
    }
}
