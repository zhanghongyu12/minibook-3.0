package com.minibook.ui.sheets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import com.minibook.domain.usecase.category.ObserveCategoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * 分类选择器 ViewModel（TASK-028，UI §2 P-10）。
 *
 * 职责：
 * - 供既有 [CategoryPickerSheet]（C-02，四列网格，内部已按 type/excludeId 过滤）取数：
 *   暴露 [categories]（当前账本、按 [type] 过滤的分类流，账本切换经 ObserveCategoriesUseCase
 *   自动重订阅）；
 * - 入参 `type: CategoryType?`、`excludeId: String?`：优先从 [SavedStateHandle] 读取
 *   （导航路由传参场景），亦可经 [init] 运行时注入（编排者 `LaunchedEffect(type, excludeId)`
 *   调用，覆盖构造期初值）。null type = 全部分类（不过滤）。
 *
 * 本文件仅做数据接线（VM），UI 复用既有 [CategoryPickerSheet]，无需新建 Sheet。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryPickerViewModel @Inject constructor(
    observeCategories: ObserveCategoriesUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** 分类类型过滤（null=全部）。 */
    private val _type = MutableStateFlow(
        savedStateHandle.get<String>("type")?.let { runCatching { CategoryType.fromValue(it) }.getOrNull() },
    )
    val type: StateFlow<CategoryType?> = _type.asStateFlow()

    /** 排除分类 id（null=不排除；供转移/替换场景禁用当前分类）。 */
    private val _excludeId = MutableStateFlow(savedStateHandle.get<String>("excludeId"))
    val excludeId: StateFlow<String?> = _excludeId.asStateFlow()

    /** 候选分类流：随 [type] 变化经 `flatMapLatest` 重订阅（ObserveCategoriesUseCase 按
     * 账本 + type 过滤，账本切换自动重订阅）。冷流以 `WhileSubscribed(5_000)` 保活。 */
    val categories: StateFlow<List<Category>> = _type
        .flatMapLatest { observeCategories.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 运行时注入过滤参数（覆盖 SavedStateHandle 初值；编排者在 `LaunchedEffect` 中调用）。
     *
     * @param type 分类类型过滤（null=全部）。
     * @param excludeId 排除分类 id（null=不排除）。
     */
    fun init(type: CategoryType?, excludeId: String?) {
        _type.value = type
        _excludeId.value = excludeId
    }
}
