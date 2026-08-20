package com.minibook.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import com.minibook.ui.components.CategoryIconTile
import com.minibook.ui.components.CategoryPickerSheet
import com.minibook.ui.components.ConfirmDialog
import com.minibook.ui.components.EmptyState
import com.minibook.ui.components.LoadingState
import com.minibook.ui.components.MigrationDialog
import com.minibook.ui.components.MiniBookSnackbarHost
import com.minibook.ui.components.PrimaryFAB

/**
 * 分类管理页（TASK-026，UI §2 P-05）。
 *
 * 页面结构：全量分类流按 `type` 分两组（支出/收入，组头带数量 `Text("支出（N）")`），
 * 4 列 [LazyVerticalGrid] 渲染 [CategoryIconTile]（C-03）；右下 [PrimaryFAB] 新建入口。
 *
 * 交互流程：
 * - **点击分类块** → [CategoryEditorSheet] 编辑态（内置可改 name/icon/color，`type`/`isBuiltIn`
 *   锁定，禁删无删除按钮；「其他」name 只读 DEC-007）；
 * - **自定义分类删除** → 编辑器内「删除分类」→ [ConfirmDialog]（C-18 danger）→
 *   [CategoriesViewModel.deleteCategory]：0 引用直接删（UseCase 成功路径）；关联交易>0 →
 *   异常「交易引用」→ VM emit [MigrationRequest] → [MigrationDialog]（C-19）→
 *   [CategoryPickerSheet]（C-02，同 `type` 排除自身）选目标 → VM 单调用
 *   [CategoriesViewModel.migrateCategory]（A-1 事务原子）；
 * - **新建** → [CategoryEditorSheet] 新建态（type 必填 SegmentedButton）→ VM create。
 *
 * 状态折叠（UI 层）：Loading=流未首发射（[CategoriesViewModel.isLoading]）、Empty=空列表
 * （[EmptyState]）、分组在 `remember { list.filter { it.type } }`。
 *
 * @param viewModel 页面 ViewModel；默认经 Hilt 注入。
 */
@Composable
fun CategoriesScreen(viewModel: CategoriesViewModel = hiltViewModel()) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.error.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbar.collectAsStateWithLifecycle()
    val serverError by viewModel.createUpdateError.collectAsStateWithLifecycle()
    val saving by viewModel.isSaving.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // —— 局部 UI 状态 ——
    var editorCategory by remember { mutableStateOf<Category?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Category?>(null) }
    var pendingMigration by remember { mutableStateOf<MigrationRequest?>(null) }
    var migrationTarget by remember { mutableStateOf<Category?>(null) }
    var showTargetPicker by remember { mutableStateOf(false) }

    // 轻提示：展示后经 VM 清除（避免重复展示）。
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // Create/Update 成功 → 关闭编辑器弹层（失败走 serverError 内联展示，弹层保持）。
    LaunchedEffect(viewModel) {
        viewModel.saved.collect {
            editorCategory = null
            showCreate = false
            viewModel.clearError()
        }
    }

    // 删除被引用分类 → 迁移请求事件落地为弹窗状态。
    LaunchedEffect(viewModel) {
        viewModel.migrationRequest.collect { request ->
            pendingMigration = request
            migrationTarget = null
            showTargetPicker = false
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { MiniBookSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            PrimaryFAB(onClick = { showCreate = true }, contentDescription = "新建分类")
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                isLoading -> LoadingState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                )
                errorMessage != null -> ErrorCategories(
                    message = errorMessage.orEmpty(),
                    onRetry = viewModel::retry,
                )
                categories.isEmpty() -> EmptyCategories()
                else -> CategoryGrid(
                    categories = categories,
                    onCategoryClick = { editorCategory = it },
                )
            }
        }
    }

    // 编辑弹层（编辑态）：内置分类不传 onDelete（UI 级禁删）；「其他」name 只读在表单内处理。
    editorCategory?.let { category ->
        CategoryEditorSheet(
            category = category,
            type = category.type,
            serverError = serverError,
            saving = saving,
            onDelete = if (category.isBuiltIn) null else {
                {
                    editorCategory = null
                    deleteTarget = category
                }
            },
            onConfirm = { name, icon, color, _ ->
                viewModel.updateCategory(category.id, name, icon, color, category.order)
            },
            onDismiss = {
                editorCategory = null
                viewModel.clearError()
            },
        )
    }

    // 新建弹层。
    if (showCreate) {
        CategoryEditorSheet(
            category = null,
            type = null,
            serverError = serverError,
            saving = saving,
            onConfirm = { name, icon, color, type ->
                viewModel.createCategory(name, icon, color, type)
            },
            onDismiss = {
                showCreate = false
                viewModel.clearError()
            },
        )
    }

    // 删除确认（C-18，danger）：确认 → VM deleteCategory（0 引用直接删 / 迁移 fallback）。
    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "删除分类",
            message = "确定删除「${target.name}」吗？若该分类下已有交易，将进入迁移流程。",
            confirmLabel = "删除",
            danger = true,
            onConfirm = {
                deleteTarget = null
                viewModel.deleteCategory(target)
            },
            onDismiss = { deleteTarget = null },
        )
    }

    // 迁移对话框（C-19）：targetName 为空时提示先选迁移目标；选定后展示目标并确认迁移。
    val migration = pendingMigration
    if (migration != null && !showTargetPicker) {
        MigrationDialog(
            refCount = migration.refCount,
            targetName = migrationTarget?.name,
            onConfirm = {
                val target = migrationTarget
                if (target != null) {
                    viewModel.migrateCategory(migration.category.id, target.id)
                    pendingMigration = null
                    migrationTarget = null
                } else {
                    showTargetPicker = true
                }
            },
            onDismiss = {
                pendingMigration = null
                migrationTarget = null
            },
        )
    }

    // 迁移目标选择（C-02）：同 `type`、排除源分类自身（组件内过滤）。
    if (showTargetPicker) {
        val source = pendingMigration?.category
        if (source != null) {
            CategoryPickerSheet(
                categories = categories,
                type = source.type,
                excludeId = source.id,
                initialId = migrationTarget?.id,
                title = "选择迁移分类",
                onPick = { target ->
                    migrationTarget = target
                    showTargetPicker = false
                },
                onDismiss = { showTargetPicker = false },
            )
        }
    }
}

/**
 * 分类网格：按 `type` 分两组渲染，组头带数量。
 *
 * 分组在 UI 层用 `remember(categories)` 完成（VM 只提供全量扁平列表）；组头以
 * [GridItemSpan] 全宽 item 渲染，与分类块 key 隔离。
 *
 * @param categories 全量分类列表（VM 已按 `order` 升序）。
 * @param onCategoryClick 分类块点击回调（打开编辑弹层）。
 */
@Composable
private fun CategoryGrid(
    categories: List<Category>,
    onCategoryClick: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expense = remember(categories) { categories.filter { it.type == CategoryType.EXPENSE } }
    val income = remember(categories) { categories.filter { it.type == CategoryType.INCOME } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header_expense") {
            CategorySectionHeader("支出（${expense.size}）")
        }
        items(expense, key = { it.id }) { category ->
            CategoryIconTile(
                category = category,
                selected = false,
                enabled = true,
                onClick = { onCategoryClick(category) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "header_income") {
            CategorySectionHeader("收入（${income.size}）")
        }
        items(income, key = { it.id }) { category ->
            CategoryIconTile(
                category = category,
                selected = false,
                enabled = true,
                onClick = { onCategoryClick(category) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 分组数量标头（如「支出（12）」）。 */
@Composable
private fun CategorySectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

/** 空态：无任何分类（首启 bootstrap 前 / 极端清空）。 */
@Composable
private fun EmptyCategories(modifier: Modifier = Modifier) {
    EmptyState(
        icon = EmptyCategoryIcon,
        title = "暂无分类",
        subtitle = "点击右下角「＋」新建分类",
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * 错误态：观察流收集异常（如账本未初始化 / 数据库异常），展示文案 + 「重试」按钮。
 *
 * @param message 错误文案（VM 已折叠）。
 * @param onRetry 「重试」回调（[com.minibook.ui.categories.CategoriesViewModel.retry]）。
 */
@Composable
private fun ErrorCategories(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = EmptyCategoryIcon,
        title = message,
        subtitle = "加载失败，请重试",
        actionLabel = "重试",
        onAction = onRetry,
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * 空态图标：手写「标签」ImageVector。
 *
 * TASK-020 偏差：工程未引入 material-icons 库，[EmptyState] 又要求注入 [ImageVector]，
 * 故用 [addPathNodes] 解析 Material「label」path 数据本地构造，不新增依赖、不改
 * toml/build.gradle。图标为纯填充矢量，tint 由 [EmptyState] 内 `Icon(tint=...)` 统一上色，
 * 此处 fill 色仅占位（Black，不参与渲染）。
 */
private val EmptyCategoryIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "EmptyCategories",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M17.63 5.84C17.27 5.33 16.67 5 16 5L5 5.01C3.9 5.01 3 5.9 3 7v10c0 1.1.9 1.99 " +
                "2 1.99L16 19c.67 0 1.27-.33 1.63-.84L22 12l-4.37-6.16z"
        ),
        fill = SolidColor(Color.Black),
    ).build()
}
