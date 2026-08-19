package com.minibook.ui.sheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.domain.model.DebtDirection
import com.minibook.domain.model.DebtKind
import com.minibook.domain.usecase.debt.AddDebtUseCase
import com.minibook.util.TimeFormatter
import com.minibook.util.Validators
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 快捷还款 Sheet 的 ViewModel（TASK-028，UI §2 P-09）。
 *
 * 职责：
 * - 快捷还款只做新增（还款独立成行，kind 固定 [DebtKind.REPAY]），经 [AddDebtUseCase]
 *   落库；不涉及编辑/删除。
 * - [partyName]/[direction] 由入口预填（[init]，UI 侧只读展示），不可改；方向决定标题
 *   （收到还款/我还款）与四态组合；还款可超净额（UI 提示不拦截）。
 * - 金额默认 0（聚焦待输）；校验（金额 >0 且不超上限、partyName 非空、备注 ≤200）失败
 *   经 [snackbar] 内联提示。保存成功 emit [saved] + 设置 [snackbar]。
 *
 * @param partyName 对方姓名（预填）。
 * @param direction 还款方向（预填，决定收到还款/我还款）。
 */
@HiltViewModel
class RepaySheetViewModel @Inject constructor(
    private val addDebt: AddDebtUseCase,
) : ViewModel() {

    /** 对方姓名（预填只读）。 */
    private val _partyName = MutableStateFlow("")
    val partyName: StateFlow<String> = _partyName.asStateFlow()

    /** 还款金额（分）；默认 0，聚焦待输。 */
    private val _amountFen = MutableStateFlow(0L)
    val amountFen: StateFlow<Long?> = _amountFen.asStateFlow()

    /** 还款方向（预填只读）：收到还款（[DebtDirection.OWE_ME]）/ 我还款（[DebtDirection.I_OWE]）。 */
    private val _direction = MutableStateFlow(DebtDirection.OWE_ME)
    val direction: StateFlow<DebtDirection> = _direction.asStateFlow()

    /** 发生日期（yyyy-MM-dd，默认今天）。 */
    private val _date = MutableStateFlow(today())
    val date: StateFlow<String> = _date.asStateFlow()

    /** 备注（默认空串）。 */
    private val _remark = MutableStateFlow("")
    val remark: StateFlow<String> = _remark.asStateFlow()

    /** 保存进行中：确认按钮置灰防重复提交。 */
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /** 全局轻提示文案（校验失败/保存失败/成功）。 */
    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    /** 保存成功事件：UI 据此关闭弹层。 */
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    /**
     * 预填对方姓名与还款方向（Sheet 打开时由 UI 侧经 [LaunchedEffect] 调用一次）。
     *
     * @param partyName 对方姓名。
     * @param direction 还款方向。
     */
    fun init(partyName: String, direction: DebtDirection) {
        _partyName.value = partyName
        _direction.value = direction
    }

    /** 设置还款金额（分）。 */
    fun setAmount(fen: Long?) {
        _amountFen.value = fen ?: 0L
    }

    /** 设置发生日期（yyyy-MM-dd）。 */
    fun setDate(date: String) {
        _date.value = date
    }

    /** 设置备注。 */
    fun setRemark(remark: String) {
        _remark.value = remark
    }

    /**
     * 保存：校验（金额 >0 且不超上限、partyName 非空、备注 ≤200）→ 新增
     * [AddDebtUseCase]（kind 固定 [DebtKind.REPAY]，方向/姓名沿用预填）。成功 emit [saved]。
     */
    fun save() {
        val amount = _amountFen.value
        if (amount == null || !Validators.validateAmount(amount)) {
            _snackbar.value = "请输入有效金额"
            return
        }
        val partyName = _partyName.value.trim()
        if (!Validators.validatePartyName(partyName)) {
            _snackbar.value = "对方姓名不能为空"
            return
        }
        val remark = _remark.value.trim()
        if (!Validators.validateNoteLength(remark)) {
            _snackbar.value = "备注不能超过 ${Validators.MAX_NOTE_LENGTH} 字"
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            try {
                addDebt(
                    partyName = partyName,
                    amount = amount,
                    kind = DebtKind.REPAY,
                    direction = _direction.value,
                    date = _date.value,
                    remark = remark.takeIf { it.isNotEmpty() },
                )
                _snackbar.value = "已记还款"
                _saved.emit(Unit)
            } catch (e: Exception) {
                _snackbar.value = e.message?.ifBlank { null } ?: "保存失败"
            } finally {
                _isSaving.value = false
            }
        }
    }

    /** 清除轻提示（Sheet 展示后调用，避免重复展示）。 */
    fun clearSnackbar() {
        _snackbar.value = null
    }
}

/** 今日日期 `yyyy-MM-dd`（TimeFormatter 口径，DATABASE §9.5）。 */
private fun today(): String = TimeFormatter.formatDate(TimeFormatter.nowIso())
