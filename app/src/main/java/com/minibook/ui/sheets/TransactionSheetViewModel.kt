package com.minibook.ui.sheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionType
import com.minibook.domain.repository.TransactionRepository
import com.minibook.domain.usecase.category.ObserveCategoriesUseCase
import com.minibook.domain.usecase.transaction.AddTransactionUseCase
import com.minibook.domain.usecase.transaction.DeleteTransactionUseCase
import com.minibook.domain.usecase.transaction.UpdateTransactionUseCase
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 记一笔 Sheet 的 ViewModel（TASK-028，UI §2 P-07）。
 *
 * 职责：
 * - 持有交易表单全量状态（type/amountFen/categoryId/date/description），[save] 统一校验后
 *   走新增（[AddTransactionUseCase]）/ 编辑（[UpdateTransactionUseCase]）；
 * - 编辑态由 [initEdit] 经 [TransactionRepository.getById] 回填（含只读字段 id/ledgerId/
 *   createdAt 缓存于 [originalTx]，update 时以库内原值覆盖 createdAt，UI 不感知）；
 * - 候选分类 [categories] 随 [type] 变化经 `flatMapLatest` 重订阅对应类型分类流
 *   （ObserveCategoriesUseCase 内置账本切换重订阅）；切类型时重置 `categoryId=null`
 *   （避免支出/收入分类串选）；
 * - 删除由 [delete] 触发（UI 侧 danger [ConfirmDialog] 强确认后调用）。
 *
 * 校验口径（TASK-010 Validators / DATABASE §9.4）：金额 >0 且不超上限、分类必填、
 * 备注 ≤ 200 字；校验失败经 [snackbar] 内联提示（Sheet 内 SnackbarHost 展示，不关闭）。
 * 保存成功 emit [saved]（UI 据此关闭弹层）+ 设置 [snackbar]（编排者可收集上抛）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionSheetViewModel @Inject constructor(
    private val addTransaction: AddTransactionUseCase,
    private val updateTransaction: UpdateTransactionUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    observeCategories: ObserveCategoriesUseCase,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    /** 收支类型（默认支出）。 */
    private val _type = MutableStateFlow(TransactionType.EXPENSE)
    val type: StateFlow<TransactionType> = _type.asStateFlow()

    /** 金额（分）；null 表示未输入/解析失败。 */
    private val _amountFen = MutableStateFlow<Long?>(null)
    val amountFen: StateFlow<Long?> = _amountFen.asStateFlow()

    /** 所选分类 id（必填；null 表示未选）。 */
    private val _categoryId = MutableStateFlow<String?>(null)
    val categoryId: StateFlow<String?> = _categoryId.asStateFlow()

    /** 发生日期（yyyy-MM-dd，默认今天）。 */
    private val _date = MutableStateFlow(today())
    val date: StateFlow<String> = _date.asStateFlow()

    /** 备注（默认空串）。 */
    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

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

    /** 编辑回填的原始交易：update/delete 复用其 id/ledgerId/createdAt 等只读字段。 */
    private var originalTx: Transaction? = null

    /**
     * 候选分类流：随 [type] 变化经 `flatMapLatest` 重订阅（ObserveCategoriesUseCase 按
     * 账本 + type 过滤，账本切换自动重订阅）。冷流以 `WhileSubscribed(5_000)` 保活。
     */
    val categories: StateFlow<List<Category>> = _type
        .flatMapLatest { txType -> observeCategories.observe(txType.toCategoryType()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 切换收支类型：重置 `categoryId=null`（分类随类型重订阅，旧类型所选分类作废）。
     *
     * @param type 目标类型（支出/收入）。
     */
    fun setType(type: TransactionType) {
        _type.value = type
        _categoryId.value = null
    }

    /** 设置金额（分；null=未输入）。 */
    fun setAmount(fen: Long?) {
        _amountFen.value = fen
    }

    /** 设置所选分类 id（null=未选）。 */
    fun setCategoryId(categoryId: String?) {
        _categoryId.value = categoryId
    }

    /** 设置发生日期（yyyy-MM-dd）。 */
    fun setDate(date: String) {
        _date.value = date
    }

    /** 设置备注。 */
    fun setDescription(description: String) {
        _description.value = description
    }

    /** 重置为新增态（Sheet 以 `tx=null` 打开时调用，复用 VM 实例场景防串态）。 */
    fun initNew() {
        originalTx = null
        _isEditMode.value = false
        _editingId.value = null
        _type.value = TransactionType.EXPENSE
        _amountFen.value = null
        _categoryId.value = null
        _date.value = today()
        _description.value = ""
    }

    /**
     * 编辑回填：经 [TransactionRepository.getById] 取原值并回填各字段。
     *
     * @param txId 目标交易 id；不存在 → snackbar 提示「该笔已删除」。
     */
    fun initEdit(txId: String) {
        viewModelScope.launch {
            val tx = transactionRepository.getById(txId)
            if (tx == null) {
                _snackbar.value = "该笔记录已不存在"
                return@launch
            }
            originalTx = tx
            _isEditMode.value = true
            _editingId.value = tx.id
            _type.value = tx.type
            _amountFen.value = tx.amount
            _categoryId.value = tx.categoryId
            _date.value = tx.date
            _description.value = tx.description.orEmpty()
        }
    }

    /**
     * 保存：校验（金额 >0 且不超上限、分类必填、备注 ≤200）→ 新增 [AddTransactionUseCase] /
     * 编辑 [UpdateTransactionUseCase]（createdAt 以库内原值为准）。成功 emit [saved]。
     */
    fun save() {
        val amount = _amountFen.value
        if (amount == null || !Validators.validateAmount(amount)) {
            _snackbar.value = "请输入有效金额"
            return
        }
        val category = _categoryId.value
        if (category.isNullOrBlank()) {
            _snackbar.value = "请选择分类"
            return
        }
        val description = _description.value.trim()
        if (!Validators.validateNoteLength(description)) {
            _snackbar.value = "备注不能超过 ${Validators.MAX_NOTE_LENGTH} 字"
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val desc = description.takeIf { it.isNotEmpty() }
                if (_isEditMode.value) {
                    val original = originalTx
                    if (original == null) {
                        _snackbar.value = "原始记录不存在"
                        return@launch
                    }
                    updateTransaction(
                        original.copy(
                            type = _type.value,
                            amount = amount,
                            categoryId = category,
                            description = desc,
                            date = _date.value,
                        ),
                    )
                    _snackbar.value = "已保存"
                } else {
                    addTransaction(
                        type = _type.value,
                        amount = amount,
                        categoryId = category,
                        date = _date.value,
                        description = desc,
                    )
                    _snackbar.value = "已记一笔"
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
     * 删除当前编辑交易：经 [DeleteTransactionUseCase] 物理删除（UI 强确认后调用）。
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
                deleteTransaction(id)
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
}

/** 今日日期 `yyyy-MM-dd`（TimeFormatter 口径，DATABASE §9.5）。 */
private fun today(): String = TimeFormatter.formatDate(TimeFormatter.nowIso())

/** 交易类型 → 分类类型（两者为同值不同枚举，编辑/选择分类共用）。 */
internal fun TransactionType.toCategoryType(): CategoryType = when (this) {
    TransactionType.EXPENSE -> CategoryType.EXPENSE
    TransactionType.INCOME -> CategoryType.INCOME
}
