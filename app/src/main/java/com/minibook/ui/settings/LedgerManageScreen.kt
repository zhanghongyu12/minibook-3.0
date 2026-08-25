package com.minibook.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.Ledger
import com.minibook.ui.components.ConfirmDialog
import com.minibook.ui.components.EmptyState
import com.minibook.ui.components.MiniBookSnackbarHost
import com.minibook.ui.components.PrimaryFAB

/**
 * 账本管理页（TASK-029，UI §2 P-11）。
 *
 * 页面结构：顶栏 [TopAppBar]（标题「账本管理」+ 返回按钮，material-icons 缺失用 `Text("←")`
 * 占位）+ [LazyColumn] 渲染全部账本（[LedgerManageViewModel.ledgers]，按 `position` 升序），
 * 当前账本行（`id==currentLedgerId`）以 `primaryContainer` 高亮；右下 [PrimaryFAB] 新建入口。
 *
 * 交互流程：
 * - **点击行** → [LedgerManageViewModel.switch]（纯状态切换，全 app Flow 重订阅，
 *   ARCHITECTURE §8.3）；
 * - **新建** → [LedgerEditorSheet] 新建态（name/icon/color 三必填）→ VM create
 *   （事务克隆 24 分类 + 自动切换，TASK-012 验收）；
 * - **编辑** → 行尾「✏️」→ [LedgerEditorSheet] 编辑态（回填 name/icon/color）→
 *   VM update（[LedgerRepository.update]，TASK-029 决定）；
 * - **删除** → 行尾「🗑」（**默认账本不显示删除入口**）→ [ConfirmDialog]（C-18 danger，
 *   「将同时删除该账本下全部数据」）→ VM delete（[DeleteLedgerUseCase]，事务清 5 表 +
 *   删账本行，至少保留一个）。
 *
 * 空态：不应发生（TASK-011 Bootstrap 保证至少一个默认账本），仍以 [EmptyState] 兜底。
 * 图标经 `addPathNodes` 本地构造（工程未引入 material-icons，TASK-020 偏差同口径）。
 *
 * @param onBack 顶栏返回回调（NavHost 接线传入 `rootController.popBackStack()`）。
 * @param viewModel 页面 ViewModel；默认经 Hilt 注入。
 */
@Composable
fun LedgerManageScreen(
    onBack: () -> Unit,
    viewModel: LedgerManageViewModel = hiltViewModel(),
) {
    val ledgers by viewModel.ledgers.collectAsStateWithLifecycle()
    val currentLedgerId by viewModel.currentLedgerId.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbar.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // —— 局部 UI 状态 ——
    var showCreate by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Ledger?>(null) }
    var deleteTarget by remember { mutableStateOf<Ledger?>(null) }

    // 轻提示：展示后经 VM 清除（避免重复展示）。
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // Create/Update 成功 → 关闭编辑器弹层。
    LaunchedEffect(viewModel) {
        viewModel.saved.collect {
            showCreate = false
            editTarget = null
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { LedgerManageTopBar(onBack) },
        snackbarHost = { MiniBookSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            PrimaryFAB(onClick = { showCreate = true }, contentDescription = "新建账本")
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (ledgers.isEmpty()) {
                EmptyLedgers()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 96.dp,
                    ),
                ) {
                    items(ledgers, key = { it.id }) { ledger ->
                        LedgerRow(
                            ledger = ledger,
                            isCurrent = ledger.id == currentLedgerId,
                            onClick = { viewModel.switch(ledger.id) },
                            onEdit = { editTarget = ledger },
                            // 默认账本无删除入口（DELETE 业务守卫由 VM/UseCase 兜底）。
                            onDelete = if (ledger.isDefault) {
                                null
                            } else {
                                { deleteTarget = ledger }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                        )
                    }
                }
            }
        }
    }

    // 新建弹层（新建态）。
    if (showCreate) {
        LedgerEditorSheet(
            ledger = null,
            onConfirm = { name, icon, color -> viewModel.create(name, icon, color) },
            onDismiss = { showCreate = false },
        )
    }

    // 编辑弹层（编辑态，回填 name/icon/color）。
    editTarget?.let { ledger ->
        LedgerEditorSheet(
            ledger = ledger,
            onConfirm = { name, icon, color -> viewModel.update(ledger, name, icon, color) },
            onDismiss = { editTarget = null },
        )
    }

    // 删除确认（C-18，danger）：确认 → VM delete（事务清数据，UseCase 至少保留一个）。
    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "删除账本",
            message = "确定删除「${target.name}」吗？将同时删除该账本下全部数据。",
            confirmLabel = "删除",
            danger = true,
            onConfirm = {
                deleteTarget = null
                viewModel.delete(target)
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

/**
 * 账本管理顶栏：标题「账本管理」+ 返回按钮。
 *
 * material-icons 缺失（TASK-020 偏差），返回图标用 `Text("←")` 占位；[TopAppBar] 自行处理
 * 状态栏 inset。
 *
 * @param onBack 返回回调（NavHost 接线传入 `rootController.popBackStack()`）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerManageTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text("账本管理") },
        navigationIcon = {
            IconButton(onClick = onBack) { Text("←") }
        },
    )
}

/**
 * 账本行：图标 + 名称 + 徽标（当前/默认）+ 编辑/删除按钮。
 *
 * 当前账本行以 `primaryContainer` 底色高亮；整行可点（切换账本）。行尾「✏️」「🗑」用
 * [IconButton] + emoji 文本占位（工程未引入 material-icons，TASK-020 偏差同口径）；
 * 内层按钮点击不触发外层行点击（Compose 嵌套 clickable 由内层消费）。
 *
 * @param ledger 账本。
 * @param isCurrent 是否为当前账本（`id==currentLedgerId`）。
 * @param onClick 整行点击（切换账本）。
 * @param onEdit 编辑回调（打开编辑弹层）。
 * @param onDelete 删除回调；null 不显示删除按钮（默认账本）。
 */
@Composable
private fun LedgerRow(
    ledger: Ledger,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(ledger.icon, fontSize = 28.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ledger.name, style = MaterialTheme.typography.bodyLarge)
                if (isCurrent || ledger.isDefault) {
                    Text(
                        text = ledgerBadge(ledger, isCurrent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onEdit) { Text("✏️", fontSize = 18.sp) }
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Text("🗑", fontSize = 18.sp) }
            }
        }
    }
}

/** 行徽标文案：默认账本 / 当前（可叠加「默认账本 · 当前」）。 */
private fun ledgerBadge(ledger: Ledger, isCurrent: Boolean): String = buildString {
    if (ledger.isDefault) append("默认账本")
    if (isCurrent) {
        if (ledger.isDefault) append(" · ")
        append("当前")
    }
}

/** 空态：无任何账本（TASK-011 Bootstrap 保证至少默认账本，此处仅兜底）。 */
@Composable
private fun EmptyLedgers(modifier: Modifier = Modifier) {
    EmptyState(
        icon = EmptyLedgerIcon,
        title = "暂无账本",
        subtitle = "点击右下角「＋」新建账本",
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * 空态图标：手写「账本」ImageVector。
 *
 * 工程未引入 material-icons 库，[EmptyState] 又要求注入 [ImageVector]，故用 [addPathNodes]
 * 解析 Material「menu_book」path 数据本地构造，不新增依赖、不改 toml/build.gradle（TASK-020
 * 偏差同口径）。图标为纯填充矢量，tint 由 [EmptyState] 内 `Icon(tint=...)` 统一上色，
 * 此处 fill 色仅占位（Black，不参与渲染）。
 */
private val EmptyLedgerIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "EmptyLedgers",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M21 5c-1.11-.35-2.33-.5-3.5-.5-1.95 0-4.05.4-5.5 1.5-1.45-1.1-3.55-1.5-5.5-1.5" +
                "S2.45 4.9 1 6v14.65c0 .25.25.5.5.5.1 0 .15-.05.25-.05C3.1 20.45 5.05 20 6.5 20" +
                "c1.95 0 4.05.4 5.5 1.5 1.35-.85 3.8-1.5 5.5-1.5 1.65 0 3.35.3 4.75 1.05.1.05.15.05" +
                ".25.05.25 0 .5-.25.5-.5V6c-.6-.45-1.25-.75-2-1zm0 13.5c-1.1-.35-2.3-.5-3.5-.5" +
                "-1.7 0-4.15.65-5.5 1.5V8c1.35-.85 3.8-1.5 5.5-1.5 1.2 0 2.4.15 3.5.5v11.5z"
        ),
        fill = SolidColor(Color.Black),
    ).build()
}
