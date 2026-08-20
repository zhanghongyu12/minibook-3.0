package com.minibook.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.domain.common.InvalidInput
import com.minibook.domain.common.LedgerNotFound
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import com.minibook.domain.usecase.category.CreateCategoryUseCase
import com.minibook.domain.usecase.category.DeleteCategoryUseCase
import com.minibook.domain.usecase.category.DeleteCategoryWithMigrationUseCase
import com.minibook.domain.usecase.category.ObserveCategoriesUseCase
import com.minibook.domain.usecase.category.UpdateCategoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 分类管理页 ViewModel（TASK-026，UI §2 P-05）。
 *
 * 职责：
 * - 订阅 [ObserveCategoriesUseCase.observe] 全量分类流（内置账本切换重订阅，按 `order` 升序），
 *   以 `WhileSubscribed(5_000)` 保活（冷流语义同 TASK-024/025）；UI 层按 `type` 分组渲染。
 *   流未首发射时 [isLoading] 为 true（`onEach` 置 false），Empty=空列表，均在 UI 层判断。
 * - 删除：[deleteCategory] 只接受自定义分类（内置由 UI 无入口 + VM 兜底拒绝）；直接调
 *   [DeleteCategoryUseCase]（0 引用成功路径）。若抛 `IllegalArgumentException` 且信息含
 *   「交易引用」→ emit [MigrationRequest] 供 UI 弹 [MigrationDialog]（P-05 迁移流程）；
 *   其他异常 → snackbar。
 * - 迁移：[migrateCategory] 单调用 [DeleteCategoryWithMigrationUseCase]（A-1 事务原子，
 *   本类不承载事务细节 DEC-001）。
 * - Create/Update 暴露方法 + 错误流 [createUpdateError]（编辑器内联展示）；成功经 [saved]
 *   事件通知 UI 关闭编辑器。
 *
 * **迁移判定**：不直接注入 Repository，改用 [DeleteCategoryUseCase] 异常信息判断
 * （「N 笔交易引用」→ [parseRefCount] 解析 N，TASK-026 决定）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    observeCategories: ObserveCategoriesUseCase,
    private val createCategoryUseCase: CreateCategoryUseCase,
    private val updateCategoryUseCase: UpdateCategoryUseCase,
    private val deleteCategoryUseCase: DeleteCategoryUseCase,
    private val deleteCategoryWithMigrationUseCase: DeleteCategoryWithMigrationUseCase,
) : ViewModel() {

    /** 流未首发射 / 首启加载中：true；[categories] 首次发射后置 false。 */
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 观察流错误文案；null=正常。异常后 UI 展示 Error 态并提供重试。 */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 手动刷新触发：递增使 `flatMapLatest` 重订阅分类观察流（冷流重连，供错误重试）。 */
    private val refreshTick = MutableStateFlow(0)

    /**
     * 全量分类流（UI 层按 `type` 分组 EXPENSE/INCOME，账本切换自动重订阅）。
     *
     * 观察流异常（账本未初始化 / 数据库异常）→ [error] 置文案、`isLoading` 置 false，
     * 页面折叠为 Error 态；[retry] 通过递增 [refreshTick] 重订阅恢复。
     */
    val categories: StateFlow<List<Category>> = refreshTick
        .flatMapLatest {
            observeCategories.observe(null)
                .onEach {
                    _error.value = null
                    _isLoading.value = false
                }
        }
        .catch { e ->
            if (e is CancellationException) throw e
            _isLoading.value = false
            _error.value = if (e is LedgerNotFound) LEDGER_ERROR_MESSAGE else LOAD_ERROR_MESSAGE
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 全局轻提示文案（删除/迁移/创建/保存结果）。 */
    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    /** Create/Update 内联错误流（编辑器内展示，成功后自动清除）。 */
    private val _createUpdateError = MutableStateFlow<String?>(null)
    val createUpdateError: StateFlow<String?> = _createUpdateError.asStateFlow()

    /** Create/Update 进行中：编辑器确认按钮置灰，防重复提交。 */
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /** Create/Update 成功事件：UI 据此关闭编辑器弹层。 */
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    /** 迁移请求事件：删除被引用分类时 emit，UI 弹 [MigrationDialog]。 */
    private val _migrationRequest = MutableSharedFlow<MigrationRequest>(extraBufferCapacity = 1)
    val migrationRequest: SharedFlow<MigrationRequest> = _migrationRequest.asSharedFlow()

    /** 清除轻提示（UI 展示后调用）。 */
    fun clearSnackbar() {
        _snackbar.value = null
    }

    /** 清除 Create/Update 错误（编辑器关闭/成功后调用）。 */
    fun clearError() {
        _createUpdateError.value = null
    }

    /**
     * 重试：清错误、回到加载态并重订阅分类观察流（错误态「重试」按钮回调）。
     */
    fun retry() {
        _error.value = null
        _isLoading.value = true
        refreshTick.value += 1
    }

    /**
     * 删除自定义分类（内置由 UI 无入口，此处兜底拒绝）。
     *
     * 直接调 [DeleteCategoryUseCase]：0 引用成功路径直接删；关联交易>0 → UseCase 抛
     * `IllegalArgumentException`（含「交易引用」）→ emit [MigrationRequest] 供 UI 走迁移；
     * 其他异常 → snackbar。
     *
     * @param category 待删除分类（须 `isBuiltIn=false`）。
     */
    fun deleteCategory(category: Category) {
        if (category.isBuiltIn) {
            _snackbar.value = "内置分类不可删除"
            return
        }
        viewModelScope.launch {
            try {
                deleteCategoryUseCase(category.id)
                _snackbar.value = "已删除分类「${category.name}」"
            } catch (e: InvalidInput) {
                val message = e.message.orEmpty()
                if (message.contains("交易引用")) {
                    _migrationRequest.emit(
                        MigrationRequest(category = category, refCount = parseRefCount(message)),
                    )
                } else {
                    _snackbar.value = message.ifBlank { "删除失败" }
                }
            } catch (e: Exception) {
                _snackbar.value = "删除失败：${e.message ?: "未知错误"}"
            }
        }
    }

    /**
     * 删除被引用分类并迁移其交易（A-1 单调用事务原子）。
     *
     * @param sourceId 源分类 id（被删除）。
     * @param targetId 迁移目标分类 id（同 `type`、非自身，UI 选择器已约束）。
     */
    fun migrateCategory(sourceId: String, targetId: String) {
        viewModelScope.launch {
            try {
                deleteCategoryWithMigrationUseCase(sourceId, targetId)
                _snackbar.value = "删除成功，交易已迁移"
            } catch (e: Exception) {
                _snackbar.value = e.message?.ifBlank { null } ?: "迁移失败"
            }
        }
    }

    /**
     * 新建自定义分类。
     *
     * 成功 → snackbar + emit [saved]（UI 关编辑器）；失败 → [createUpdateError]（内联展示）。
     *
     * @param name 分类名（非空，UseCase 校验）。
     * @param icon 图标 emoji。
     * @param color 主题色（`#RRGGBB`）。
     * @param type 收支类型（必填）。
     */
    fun createCategory(name: String, icon: String, color: String, type: CategoryType) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                createCategoryUseCase(name, icon, color, type)
                _snackbar.value = "已创建分类「$name」"
                _saved.emit(Unit)
            } catch (e: Exception) {
                _createUpdateError.value = e.message?.ifBlank { null } ?: "创建失败"
            } finally {
                _isSaving.value = false
            }
        }
    }

    /**
     * 更新分类（name/icon/color/order；type/isBuiltIn 由 UseCase 锁定）。
     *
     * 「其他」分类 name 只读（DEC-007）由编辑器禁用输入框保证；order 透传原值。
     * 成功 → snackbar + emit [saved]；失败 → [createUpdateError]。
     *
     * @param categoryId 目标分类 id。
     * @param name 新分类名。
     * @param icon 新图标 emoji。
     * @param color 新主题色。
     * @param order 原排序值（「其他」为 99，UseCase 校验见 TASK-015 口径）。
     */
    fun updateCategory(categoryId: String, name: String, icon: String, color: String, order: Int) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                updateCategoryUseCase(categoryId, name, icon, color, order)
                _snackbar.value = "已保存分类「$name」"
                _saved.emit(Unit)
            } catch (e: Exception) {
                _createUpdateError.value = e.message?.ifBlank { null } ?: "保存失败"
            } finally {
                _isSaving.value = false
            }
        }
    }

    /** 从异常信息解析引用计数（「该分类已有 N 笔交易引用…」）；解析失败回退 0。 */
    private fun parseRefCount(message: String): Int =
        Regex("""(\d+)""").find(message)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    companion object {
        /** 当前账本未初始化文案（对齐 HomeViewModel §8.5）。 */
        const val LEDGER_ERROR_MESSAGE = "账本不存在或当前账本未初始化"

        /** 未知异常兜底文案。 */
        const val LOAD_ERROR_MESSAGE = "加载失败，请稍后重试"
    }
}

/**
 * 迁移请求事件（TASK-026，P-05 迁移流程）。
 *
 * @property category 被删除（被引用）的源分类。
 * @property refCount 关联交易笔数（从 [DeleteCategoryUseCase] 异常信息解析）。
 */
data class MigrationRequest(
    val category: Category,
    val refCount: Int,
)
