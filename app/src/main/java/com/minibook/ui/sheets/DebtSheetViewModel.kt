package com.minibook.ui.sheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.domain.common.LedgerNotFound
import com.minibook.domain.model.Debt
import com.minibook.domain.model.DebtDirection
import com.minibook.domain.model.DebtKind
import com.minibook.domain.usecase.debt.AddDebtUseCase
import com.minibook.domain.usecase.debt.DeleteDebtUseCase
import com.minibook.domain.usecase.debt.NetByPartyUseCase
import com.minibook.domain.usecase.debt.ObserveDebtsUseCase
import com.minibook.domain.usecase.debt.UpdateDebtUseCase
import com.minibook.util.TimeFormatter
import com.minibook.util.Validators
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 债务 / 编辑债务 Sheet 的 ViewModel（TASK-028，UI §2 P-08）。
 *
 * 职责：
 * - 四态录入状态机：`借出(LEND×OWE_ME)`、`借入(LEND×I_OWE)`、`收到还款(REPAY×OWE_ME)`、
 *   `我还款(REPAY×I_OWE)`（AddDebtUseCase KDoc 四态字典 / PRD F-02）；
 *   本 VM 只维护 [kind]/[direction] 两个正交枚举，四态由两者组合而成，save 时原样透传。
 *   还款可超净额（业务允许，UI 仅提示不拦截）。
 * - [partyNetFen] 随 partyName + kind==REPAY 实时计算对方净额（分），供 UI 渲染
 *   「金额超过当前净欠额，请确认」提示（经 [NetByPartyUseCase] 聚合，正值=对方欠我）。
 * - 编辑回填：经 [ObserveDebtsUseCase] 当前账本首元素 `firstOrNull { it.id == debtId }`
 *   取原值（DebtRepository 无 getById，故走 observe 流口径）；原值缓存于 [originalDebt]
 *   以保留 ledgerId/createdAt。
 * - 删除由 [delete] 触发（UI 侧 danger [ConfirmDialog] 强确认后调用）。
 *
 * 校验口径（TASK-010 Validators）：partyName 必填非空、金额 >0 且不超上限、备注 ≤200；
 * 失败经 [snackbar] 内联提示。保存成功 emit [saved] + 设置 [snackbar]。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DebtSheetViewModel @Inject constructor(
    private val addDebt: AddDebtUseCase,
    private val updateDebt: UpdateDebtUseCase,
    private val deleteDebt: DeleteDebtUseCase,
    private val observeDebts: ObserveDebtsUseCase,
    private val netByParty: NetByPartyUseCase,
) : ViewModel() {

    /** 对方姓名（必填）。 */
    private val _partyName = MutableStateFlow("")
    val partyName: StateFlow<String> = _partyName.asStateFlow()

    /** 金额（分）；null=未输入/解析失败。 */
    private val _amountFen = MutableStateFlow<Long?>(null)
    val amountFen: StateFlow<Long?> = _amountFen.asStateFlow()

    /** 债项类型：借（[DebtKind.LEND]）/ 还（[DebtKind.REPAY]）。 */
    private val _kind = MutableStateFlow(DebtKind.LEND)
    val kind: StateFlow<DebtKind> = _kind.asStateFlow()

    /** 方向：对方欠我（[DebtDirection.OWE_ME]）/ 我欠对方（[DebtDirection.I_OWE]）。 */
    private val _direction = MutableStateFlow(DebtDirection.OWE_ME)
    val direction: StateFlow<DebtDirection> = _direction.asStateFlow()

    /** 发生日期（yyyy-MM-dd，默认今天）。 */
    private val _date = MutableStateFlow(today())
    val date: StateFlow<String> = _date.asStateFlow()

    /** 备注（默认空串）。 */
    private val _remark = MutableStateFlow("")
    val remark: StateFlow<String> = _remark.asStateFlow()

    /** 是否编辑态（true=update/delete；false=新增）。 */
    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    /** 编辑目标 id（删除用）。 */
    private val _editingId = MutableStateFlow<String?>(null)
    val editingId: StateFlow<String?> = _editingId.asStateFlow()

    /** 保存/删除进行中：确认按钮置灰防重复提交。 */
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /** 全局轻提示文案（校验失败/保存失败/成功）。 */
    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    /** 保存/删除成功事件：UI 据此关闭弹层。 */
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    /** 编辑回填的原始债务：update/delete 复用其 id/ledgerId/createdAt 等只读字段。 */
    private var originalDebt: Debt? = null

    /**
     * 对方当前净额（分）：正值=对方欠我，负值=我欠对方（NetByPartyUseCase 口径）。
     *
     * 仅当 partyName 非空且 [kind]==REPAY 时计算；其余为 null（UI 侧 `null` 视作 0，
     * 即无历史记录时任意还款均超净额）。随 partyName 键入实时重算，`flatMapLatest`
     * 取消旧查询、防乱序覆盖。
     */
    val partyNetFen: StateFlow<Long?> = combine(_partyName, _kind) { name, k -> name to k }
        .flatMapLatest { (name, k) ->
            if (name.isBlank() || k != DebtKind.REPAY) {
                flowOf<Long?>(null)
            } else {
                flow { emit(safeNetFor(name.trim())) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 设置对方姓名。 */
    fun setPartyName(name: String) {
        _partyName.value = name
    }

    /** 设置金额（分；null=未输入）。 */
    fun setAmount(fen: Long?) {
        _amountFen.value = fen
    }

    /** 设置债项类型（借/还）。 */
    fun setKind(kind: DebtKind) {
        _kind.value = kind
    }

    /** 设置方向（对方欠我/我欠对方）。 */
    fun setDirection(direction: DebtDirection) {
        _direction.value = direction
    }

    /** 设置发生日期（yyyy-MM-dd）。 */
    fun setDate(date: String) {
        _date.value = date
    }

    /** 设置备注。 */
    fun setRemark(remark: String) {
        _remark.value = remark
    }

    /** 重置为新增态（Sheet 以 `debt=null` 打开时调用）。 */
    fun initNew() {
        originalDebt = null
        _isEditMode.value = false
        _editingId.value = null
        _partyName.value = ""
        _amountFen.value = null
        _kind.value = DebtKind.LEND
        _direction.value = DebtDirection.OWE_ME
        _date.value = today()
        _remark.value = ""
    }

    /**
     * 编辑回填：经 [ObserveDebtsUseCase] 当前账本全量取首元素命中 `debtId`。
     *
     * @param debtId 目标债务 id；不存在 → snackbar 提示「该笔已删除」。
     */
    fun initEdit(debtId: String) {
        viewModelScope.launch {
            val debt = observeDebts.observe().first().firstOrNull { it.id == debtId }
            if (debt == null) {
                _snackbar.value = "该笔记录已不存在"
                return@launch
            }
            originalDebt = debt
            _isEditMode.value = true
            _editingId.value = debt.id
            _partyName.value = debt.partyName
            _amountFen.value = debt.amount
            _kind.value = debt.kind
            _direction.value = debt.direction
            _date.value = debt.date
            _remark.value = debt.remark.orEmpty()
        }
    }

    /**
     * 保存：校验（partyName 非空、金额 >0 且不超上限、备注 ≤200）→ 新增 [AddDebtUseCase] /
     * 编辑 [UpdateDebtUseCase]（kind×direction 四态原样透传）。成功 emit [saved]。
     */
    fun save() {
        val partyName = _partyName.value.trim()
        if (!Validators.validatePartyName(partyName)) {
            _snackbar.value = "请输入对方姓名"
            return
        }
        val amount = _amountFen.value
        if (amount == null || !Validators.validateAmount(amount)) {
            _snackbar.value = "请输入有效金额"
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
                val remarkOrNull = remark.takeIf { it.isNotEmpty() }
                if (_isEditMode.value) {
                    val original = originalDebt
                    if (original == null) {
                        _snackbar.value = "原始记录不存在"
                        return@launch
                    }
                    updateDebt(
                        original.copy(
                            partyName = partyName,
                            amount = amount,
                            kind = _kind.value,
                            direction = _direction.value,
                            date = _date.value,
                            remark = remarkOrNull,
                        ),
                    )
                    _snackbar.value = "已保存"
                } else {
                    addDebt(
                        partyName = partyName,
                        amount = amount,
                        kind = _kind.value,
                        direction = _direction.value,
                        date = _date.value,
                        remark = remarkOrNull,
                    )
                    _snackbar.value = "已记录"
                }
                _saved.emit(Unit)
            } catch (e: Exception) {
                _snackbar.value = e.message?.ifBlank { null } ?: "保存失败"
            } finally {
                _isSaving.value = false
            }
        }
    }

    /**
     * 删除当前编辑债务：经 [DeleteDebtUseCase] 物理删除（UI 强确认后调用）。
     * 成功 emit [saved] 关闭弹层。
     */
    fun delete() {
        val id = _editingId.value
        if (id == null) {
            _snackbar.value = "无法删除：记录不存在"
            return
        }
        viewModelScope.launch {
            try {
                deleteDebt(id)
                _snackbar.value = "已删除"
                _saved.emit(Unit)
            } catch (e: Exception) {
                _snackbar.value = e.message?.ifBlank { null } ?: "删除失败"
            }
        }
    }

    /** 清除轻提示（Sheet 展示后调用，避免重复展示）。 */
    fun clearSnackbar() {
        _snackbar.value = null
    }

    /** 查询对方净额，账本未初始化等异常视为无净额（null）。 */
    private suspend fun safeNetFor(partyName: String): Long? =
        try {
            netByParty()[partyName]
        } catch (e: LedgerNotFound) {
            null
        }
}

/** 今日日期 `yyyy-MM-dd`（TimeFormatter 口径，DATABASE §9.5）。 */
private fun today(): String = TimeFormatter.formatDate(TimeFormatter.nowIso())
