package com.minibook.ui.budgets

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.BudgetProgress
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryBudgetProgress
import com.minibook.ui.components.EmptyState
import com.minibook.ui.components.LoadingState
import com.minibook.ui.components.MiniBookSnackbarHost
import com.minibook.ui.components.MoneySemantic
import com.minibook.ui.components.MoneyText
import com.minibook.ui.navigation.BillsFilterBus
import com.minibook.ui.theme.LocalSemanticColors
import com.minibook.util.TimeFormatter
import java.time.YearMonth

/**
 * 预算管理页（TASK-038，UI §8.3 P-14 / DEC-012 / DEC-008）。
 *
 * 页面结构：顶栏 [TopAppBar]「预算」+ 返回 ← + 月份切换（[MonthSelector]，默认当前月，
 * 切月即重订阅预算进度）；内容 [LazyColumn] 两区：
 * - **总额区**：[TotalBudgetCard]——未设=「未设置」+ 设预算按钮；已设=总额/已用/剩余 + 进度条，
 *   超支进度条满格 `semanticExpense` 红 + 「已超支 ¥X」（App 内页内告警，DEC-012）；
 * - **分类预算区**：区头「分类预算」+「[+ 添加]」；[CategoryBudgetRow] ×N（分类 IconTile + 名称、
 *   已用/预算、进度条、超支标红已超支文案）。
 *
 * 四态（[BudgetsUiState]，UI §8.3 状态说明）：Loading=[LoadingState] 骨架；Empty=未设任何预算
 * →「设置月度总预算」引导；Error=观察流异常 + 重试；Success=两区渲染。
 *
 * 跳转账单：总额卡（已用=当月支出汇总）或分类预算行点击 → [BillsFilterBus] post 当月支出筛选
 * （总额=全部支出 / 分类行=该分类支出）+ onBack 回主屏记账 Tab（账单页 applyBillsFilter 填入查询框）。
 *
 * 编辑入口：总额卡「编辑总额/设预算」→ [BudgetEditorSheet]（TOTAL mode）；区头「+ 添加」或
 * 分类行尾「编辑」→ [BudgetEditorSheet]（CATEGORY mode，可删除）。保存/删除走
 * [BudgetsViewModel]（总额先删后插 & 分类 UNIQUE，DEC-008）。
 *
 * @param onBack 顶栏返回回调（NavHost 接线传入 `popBackStack()`）。
 * @param viewModel 页面 ViewModel；默认经 Hilt 注入。
 */
@Composable
fun BudgetsScreen(
    onBack: () -> Unit,
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

    // 点击跳转账单：总额卡=当月全部支出、分类预算行=该分类当月支出。post + onBack 回主屏
    // （MainScreen LaunchedEffect 观察 Bus → 切记账 Tab，账单页 applyBillsFilter 填入查询框）。
    val jumpToExpenses: (CategoryBudgetProgress?) -> Unit = { item ->
        BillsFilterBus.post(buildBillsFilterRequest(month, item?.category))
        onBack()
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
 * 月度总预算卡（UI §8.3）：
 * - 已设（[BudgetProgress.totalBudgetFen] > 0）：总额/已用/剩余 + 进度条；超支进度条满格
 *   `semanticExpense` 红 + 「已超支 ¥X」（App 内页内告警，DEC-012）；
 * - 未设：展示「未设置」+ 右上「设预算」按钮。
 *
 * 点击整卡（含未设态）→ 跳转账单筛当月全部支出（[onClick]）；右上「编辑总额/设预算」
 * [onEditTotal] 仍负责预算编辑。
 *
 * @param progress 预算进度。
 * @param onEditTotal 「编辑总额/设预算」回调（弹 [BudgetEditorSheet]）。
 * @param onClick 整卡点击回调（跳转账单筛当月支出，[BillsFilterBus] post + onBack）。
 */
@Composable
private fun TotalBudgetCard(
    progress: BudgetProgress,
    onEditTotal: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "月度总预算",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEditTotal) {
                    Text(if (progress.totalBudgetFen > 0) "编辑总额" else "设预算")
                }
            }
            Spacer(Modifier.height(12.dp))
            if (progress.totalBudgetFen > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AmountColumn("已用", progress.totalUsedFen, modifier = Modifier.weight(1f))
                    AmountColumn("预算", progress.totalBudgetFen, modifier = Modifier.weight(1f))
                    AmountColumn(
                        "剩余",
                        progress.remainingFen,
                        semantic = if (progress.remainingFen < 0) MoneySemantic.EXPENSE else MoneySemantic.NONE,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                BudgetProgressBar(usedFen = progress.totalUsedFen, budgetFen = progress.totalBudgetFen)
                if (progress.overBudget) {
                    Spacer(Modifier.height(8.dp))
                    OverBudgetLabel(overAmountFen = progress.totalUsedFen - progress.totalBudgetFen)
                }
            } else {
                Text(
                    "未设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "点击右上方「设预算」设置本月总预算",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
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

/**
 * 分类预算进度行（UI §8.3）：分类 IconTile（图标/名称）+ 已用/预算 + 进度条；
 * 超支进度条满格 `semanticExpense` 红 + 「已超支 ¥X」。
 *
 * 点击整行 → 跳转账单筛该分类当月支出（[onClick]）；行尾「编辑」按钮 → 弹
 * [BudgetEditorSheet] 分类模式编辑（[onEdit]，可删除）。
 *
 * @param item 单个分类预算进度（含分类元信息）。
 * @param onClick 行点击回调（跳转账单筛该分类当月支出，[BillsFilterBus] post + onBack）。
 * @param onEdit 「编辑」按钮回调（弹 [BudgetEditorSheet] 分类模式编辑，可删除）。
 */
@Composable
private fun CategoryBudgetRow(
    item: CategoryBudgetProgress,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIconCircle(item.category)
                Spacer(Modifier.width(12.dp))
                Text(
                    item.category.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEdit) { Text("编辑") }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AmountColumn("已用", item.usedFen, modifier = Modifier.weight(1f))
                AmountColumn("预算", item.budgetFen, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            BudgetProgressBar(usedFen = item.usedFen, budgetFen = item.budgetFen)
            if (item.overBudget) {
                Spacer(Modifier.height(6.dp))
                OverBudgetLabel(overAmountFen = item.usedFen - item.budgetFen)
            }
        }
    }
}

/** 金额列（标签 + 金额，经 [MoneyText] 展示，tnum 等宽数字）。 */
@Composable
private fun AmountColumn(
    label: String,
    fen: Long,
    modifier: Modifier = Modifier,
    semantic: MoneySemantic = MoneySemantic.NONE,
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        MoneyText(fen = fen, semantic = semantic, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * 「已超支 ¥X」页内告警文案（DEC-012）：前缀文字 + 金额均用 `semanticExpense` 红。
 *
 * @param overAmountFen 超出金额（分，正值）。
 */
@Composable
private fun OverBudgetLabel(
    overAmountFen: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "已超支 ",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalSemanticColors.current.expense,
        )
        MoneyText(fen = overAmountFen, semantic = MoneySemantic.EXPENSE, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 预算进度条：未超支用 `primary`（已用）/`surfaceVariant`（剩余轨道）；超支满格用
 * `semanticExpense` 红（UI §8.3 超支态 / DEC-012）。
 */
@Composable
private fun BudgetProgressBar(
    usedFen: Long,
    budgetFen: Long,
    modifier: Modifier = Modifier,
) {
    val over = budgetFen > 0 && usedFen > budgetFen
    val fraction = if (budgetFen > 0) {
        (usedFen.toFloat() / budgetFen.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    LinearProgressIndicator(
        progress = { fraction },
        color = if (over) LocalSemanticColors.current.expense else MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth(),
    )
}

/** 分类图标圆底（C-03 弱化圆底，emoji 保持分类原色）。 */
@Composable
private fun CategoryIconCircle(
    category: Category,
    modifier: Modifier = Modifier,
) {
    val fallback = MaterialTheme.colorScheme.outline
    val circleColor = remember(category.color) { parseCategoryColor(category.color, fallback) }
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(circleColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(category.icon, fontSize = 22.sp, color = circleColor)
    }
}

/** 解析 DB 分类色（`#RRGGBB`）→ Compose Color；空/非法回退 fallback（同 C-03 口径）。 */
private fun parseCategoryColor(hex: String?, fallback: Color): Color =
    if (hex.isNullOrBlank()) {
        fallback
    } else {
        runCatching { Color(AndroidColor.parseColor(hex)) }.getOrDefault(fallback)
    }

/**
 * 预算页顶栏：「预算」+ 返回 ← + 月份切换（[MonthSelector]）。
 *
 * @param month 当前查看月份。
 * @param onMonthChange 月份切换回调。
 * @param onBack 返回回调（NavHost 接线传入 `popBackStack()`）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetsTopBar(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("预算", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(16.dp))
                MonthSelector(month = month, onMonthChange = onMonthChange)
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) { Text("←") }
        },
    )
}

/** 月份选择器（UI §8.3「2026-08 ▾」）：点击弹月份选择对话框。 */
@Composable
private fun MonthSelector(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable { showPicker = true }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(TimeFormatter.formatYearMonth(month), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(4.dp))
        Text("▾", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showPicker) {
        MonthPickerDialog(
            initial = month,
            onSelect = {
                onMonthChange(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

/** 月份选择对话框：◀ 上一月 / ▶ 下一月 步进，确定回传所选月份。 */
@Composable
private fun MonthPickerDialog(
    initial: YearMonth,
    onSelect: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    var displayed by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择月份") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { displayed = displayed.minusMonths(1) }) { Text("◀") }
                    Spacer(Modifier.width(12.dp))
                    Text(TimeFormatter.formatYearMonth(displayed), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    IconButton(onClick = { displayed = displayed.plusMonths(1) }) { Text("▶") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(displayed) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 空态：未设任何预算 → 「设置月度总预算」引导（含设置动作）。 */
@Composable
private fun EmptyBudgets(
    onSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = EmptyBudgetIcon,
        title = "设置月度总预算，追踪本月支出",
        subtitle = "从「月度总预算」或「分类预算」开始设置",
        actionLabel = "设置总预算",
        onAction = onSetup,
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * 错误态：观察流收集异常（如账本未初始化 / 数据库异常），展示文案 + 「重试」按钮。
 *
 * @param message 错误文案（[BudgetsViewModel] 已折叠）。
 * @param onRetry 「重试」回调（[BudgetsViewModel.refresh]）。
 */
@Composable
private fun ErrorBudgets(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = EmptyBudgetIcon,
        title = message,
        subtitle = "加载失败，请重试",
        actionLabel = "重试",
        onAction = onRetry,
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * 空态图标：手写「钱包」ImageVector。
 *
 * 工程未引入 material-icons 库，[EmptyState] 又要求注入 [ImageVector]，故用 [addPathNodes]
 * 解析 Material「account_balance_wallet」path 数据本地构造，不新增依赖、不改
 * toml/build.gradle（TASK-020 偏差同口径）。图标为纯填充矢量，tint 由 [EmptyState] 内
 * `Icon(tint=...)` 统一上色，此处 fill 色仅占位（Black，不参与渲染）。
 */
private val EmptyBudgetIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "EmptyBudgets",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M21 18v1c0 1.1-.9 2-2 2H5c-1.11 0-2-.9-2-2V5c0-1.1.89-2 2-2h14c1.1 0 2 .9 2 2v1h-9" +
                "c-1.11 0-2 .9-2 2v8c0 1.1.89 2 2 2h9zm-9-2h10V8H12v8zm4-2.5c-.83 0-1.5-.67-1.5-1.5" +
                "s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z"
        ),
        fill = SolidColor(Color.Black),
    ).build()
}
