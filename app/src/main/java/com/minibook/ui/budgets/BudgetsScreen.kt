package com.minibook.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.BudgetProgress
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryBudgetProgress
import com.minibook.ui.components.LoadingState
import com.minibook.ui.components.MiniBookSnackbarHost
import com.minibook.ui.navigation.BillsFilterBus

/**
 * 预算管理页（TASK-038，UI §8.3 P-14 / DEC-012 / DEC-008）。
 *
 * 页面结构：顶栏 [BudgetsTopBar]「预算」+ 返回 ← + 月份切换（默认当前月，切月即重订阅预算进度）；
 * 内容 [LazyColumn] 两区：
 * - **总额区**：[TotalBudgetCard]——未设=「未设置」+ 设预算按钮；已设=总额/已用/剩余 + 进度条，
 *   超支进度条满格 `semanticExpense` 红 + 「已超支 ¥X」（App 内页内告警，DEC-012）；
 * - **分类预算区**：区头「分类预算」+「[+ 添加]」；[CategoryBudgetRow] ×N（分类 IconTile + 名称、
 *   已用/预算、进度条、超支标红已超支文案）。
 *
 * 四态（[BudgetsUiState]，UI §8.3 状态说明）：Loading=[com.minibook.ui.components.LoadingState] 骨架；
 * Empty=未设任何预算 →「设置月度总预算」引导；Error=观察流异常 + 重试；Success=两区渲染。
 *
 * 跳转账单：总额卡（已用=当月支出汇总）或分类预算行点击 → [BillsFilterBus] post 当月支出筛选
 * （总额=全部支出 / 分类行=该分类支出）+ [onOpenBills] 前压栈跳根图账单明细页（账单页
 * applyBillsFilter 填入查询框，返回一次回到本页）。
 *
 * 编辑入口：总额卡「编辑总额/设预算」→ [BudgetEditorSheet]（TOTAL mode）；区头「+ 添加」或
 * 分类行尾「编辑」→ [BudgetEditorSheet]（CATEGORY mode，可删除）。保存/删除走
 * [BudgetsViewModel]（总额先删后插 & 分类 UNIQUE，DEC-008）。
 *
 * @param onBack 顶栏返回回调（NavHost 接线传入 `popBackStack()`）。
 * @param onOpenBills 跳转账单明细回调（根图 `navigate(transactions)` 前压栈，返回回到本页；不再 `onBack`）。
 * @param viewModel 页面 ViewModel；默认经 Hilt 注入。
 */
@Composable
fun BudgetsScreen(
    onBack: () -> Unit,
    onOpenBills: () -> Unit = {},
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val expenseCategories by viewModel.expenseCategories.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbar.collectAsStateWithLifecycle()
    val editorError by viewModel.editorError.collectAsStateWithLifecycle()
    val saving by viewModel.isSaving.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // —— 局部 UI 状态 ——
    var showTotalEditor by remember { mutableStateOf(false) }
    var categoryEditorTarget by remember { mutableStateOf<CategoryEditorTarget?>(null) }

    // 编辑总额预填：取当前 Success 进度的总额（>0 才回填，否则按「设置」处理）。
    val successProgress = (uiState as? BudgetsUiState.Success)?.progress

    // 点击跳转账单：总额卡=当月全部支出、分类预算行=该分类当月支出。post + onOpenBills 前压栈
    // 跳根图账单明细页（返回一次回到本页；账单页 applyBillsFilter 填入查询框）。
    val jumpToExpenses: (CategoryBudgetProgress?) -> Unit = { item ->
        BillsFilterBus.post(buildBillsFilterRequest(month, item?.category))
        onOpenBills()
    }

    // 轻提示：展示后经 VM 清除（避免重复展示）。
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // 编辑保存/删除成功 → 关闭编辑弹层。
    LaunchedEffect(viewModel) {
        viewModel.saved.collect {
            showTotalEditor = false
            categoryEditorTarget = null
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            BudgetsTopBar(month = month, onMonthChange = viewModel::setMonth, onBack = onBack)
        },
        snackbarHost = { MiniBookSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val state = uiState) {
                BudgetsUiState.Loading -> LoadingState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                )
                is BudgetsUiState.Error -> ErrorBudgets(state.message, viewModel::refresh)
                BudgetsUiState.Empty -> EmptyBudgets(onSetup = { showTotalEditor = true })
                is BudgetsUiState.Success -> BudgetsContent(
                    progress = state.progress,
                    onEditTotal = { showTotalEditor = true },
                    onAddCategory = { categoryEditorTarget = CategoryEditorTarget(null, null) },
                    onEditCategory = { item ->
                        categoryEditorTarget = CategoryEditorTarget(item.category, item.budgetFen)
                    },
                    onViewAllExpenses = { jumpToExpenses(null) },
                    onViewCategoryExpenses = { item -> jumpToExpenses(item) },
                )
            }
        }
    }

    // 总额预算编辑弹层（TOTAL mode）。
    if (showTotalEditor) {
        BudgetEditorSheet(
            mode = BudgetEditorMode.TOTAL,
            initialCategory = null,
            initialAmountFen = successProgress?.totalBudgetFen?.takeIf { it > 0 },
            categories = expenseCategories,
            onConfirmTotal = viewModel::setTotalBudget,
            onConfirmCategory = { _, _ -> },
            onDelete = null,
            onDismiss = {
                showTotalEditor = false
                viewModel.clearEditorError()
            },
            serverError = editorError,
            saving = saving,
        )
    }

    // 分类预算编辑弹层（CATEGORY mode）。
    categoryEditorTarget?.let { target ->
        val deleteHandler = target.category?.let { category ->
            { viewModel.clearCategoryBudget(category.id) }
        }
        BudgetEditorSheet(
            mode = BudgetEditorMode.CATEGORY,
            initialCategory = target.category,
            initialAmountFen = target.amountFen,
            categories = expenseCategories,
            onConfirmTotal = { },
            onConfirmCategory = { category, amount ->
                viewModel.setCategoryBudget(category.id, amount)
            },
            onDelete = deleteHandler,
            onDismiss = {
                categoryEditorTarget = null
                viewModel.clearEditorError()
            },
            serverError = editorError,
            saving = saving,
        )
    }
}

/** 分类预算编辑弹层的局部目标状态（编辑目标分类 + 预填金额）。 */
private data class CategoryEditorTarget(
    val category: Category?,
    val amountFen: Long?,
)

/**
 * 预算页内容两区（Success 态）：总额卡 + 分类预算区。
 *
 * @param progress 预算进度（总额 + 分类）。
 * @param onEditTotal 编辑总额回调。
 * @param onAddCategory 添加分类预算回调。
 * @param onEditCategory 编辑指定分类预算回调。
 * @param onViewAllExpenses 总额卡点击回调（跳转账单筛当月全部支出）。
 * @param onViewCategoryExpenses 分类预算行点击回调（跳转账单筛该分类当月支出）。
 */
@Composable
private fun BudgetsContent(
    progress: BudgetProgress,
    onEditTotal: () -> Unit,
    onAddCategory: () -> Unit,
    onEditCategory: (CategoryBudgetProgress) -> Unit,
    onViewAllExpenses: () -> Unit,
    onViewCategoryExpenses: (CategoryBudgetProgress) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "total") {
            TotalBudgetCard(
                progress = progress,
                onEditTotal = onEditTotal,
                onClick = onViewAllExpenses,
            )
        }
        item(key = "category_header") {
            CategorySectionHeader(onAddCategory = onAddCategory)
        }
        if (progress.categoryProgress.isEmpty()) {
            item(key = "category_empty_hint") {
                CategoryEmptyHint(onAddCategory = onAddCategory)
            }
        } else {
            items(progress.categoryProgress, key = { it.category.id }) { item ->
                CategoryBudgetRow(
                    item = item,
                    onClick = { onViewCategoryExpenses(item) },
                    onEdit = { onEditCategory(item) },
                )
            }
        }
    }
}

/**
 * 分类预算区头：「分类预算」+「[+ 添加]」。
 *
 * @param onAddCategory 「+ 添加」回调（弹 [BudgetEditorSheet] 分类模式新增）。
 */
@Composable
private fun CategorySectionHeader(
    onAddCategory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "分类预算",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAddCategory) { Text("+ 添加") }
    }
}

/** 分类预算空提示（无任何分类预算时的引导行，点击即添加）。 */
@Composable
private fun CategoryEmptyHint(
    onAddCategory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onAddCategory)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "尚未设置分类预算，点击添加",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text("＋", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}