package com.minibook.ui.transactions

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.Category
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionSource
import com.minibook.domain.model.TransactionType
import com.minibook.ui.components.CategoryPickerSheet
import com.minibook.ui.components.ConfirmDialog
import com.minibook.ui.components.DatePickerField
import com.minibook.ui.components.LoadingState
import com.minibook.ui.components.MoneyInputField
import com.minibook.ui.components.MoneySemantic
import com.minibook.ui.components.MoneyText
import com.minibook.ui.components.TransactionTypeTab
import com.minibook.ui.components.chineseLabel
import com.minibook.ui.sheets.TransactionSheetViewModel
import com.minibook.ui.sheets.toCategoryType
import com.minibook.util.TimeFormatter
import com.minibook.util.Validators

/**
 * 交易详情全屏页（P-07 升级：新增 / 查看 / 编辑 三合一，替代原 ModalBottomSheet）。
 *
 * 由根图 `transaction-detail?id={id}` 承载（[com.minibook.ui.navigation.Destination.TransactionDetail]）：
 * - **新增态**（[txId] = null）：表单（类型/金额/分类/日期/备注），底部「保存」；
 * - **查看态**（[txId] 非空，默认）：只读明细（含记录时间/外部单号等全字段），底部「编辑」切编辑态；
 * - **编辑态**（查看态点「编辑」）：表单预填 + 只读信息块（来源/对方/支付方式）+ 保存/删除。
 *
 * 复用 [TransactionSheetViewModel]（不新建重复逻辑）：其 type/amountFen/categoryId/date/
 * description/isEditMode/saved/snackbar 状态与 save/delete 逻辑不变，新增 [TransactionSheetViewModel.detailTx]
 * 承载查看态全字段回填。查看态↔编辑态切换为页内 `remember { mutableStateOf }`（默认查看）。
 *
 * @param txId 目标交易 id（null = 新增）。
 * @param onBack 返回回调（NavHost 接线传入 `rootController.popBackStack()`）。
 * @param viewModel 复用记一笔 VM；默认经 Hilt 注入（作用域为当前 nav backStack entry）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    txId: String?,
    onBack: () -> Unit,
    viewModel: TransactionSheetViewModel = hiltViewModel(),
) {
    val type by viewModel.type.collectAsStateWithLifecycle()
    val amountFen by viewModel.amountFen.collectAsStateWithLifecycle()
    val categoryId by viewModel.categoryId.collectAsStateWithLifecycle()
    val date by viewModel.date.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbar.collectAsStateWithLifecycle()
    val detailTx by viewModel.detailTx.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 查看态↔编辑态（页内自持，默认查看态；新增态恒为表单）。
    var editing by remember(txId) { mutableStateOf(false) }

    val isNew = txId == null

    // 打开时初始化：编辑回填（含 detailTx 全字段）or 新增重置。
    LaunchedEffect(txId) {
        editing = false
        if (txId != null) viewModel.initEdit(txId) else viewModel.initNew()
    }

    // snackbar：展示后立即清除（防重复触发）。
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // 保存/删除成功 → 返回上一页。
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(viewModel) {
        viewModel.saved.collect { currentOnBack() }
    }

    val formCategory = remember(categories, categoryId) { categories.firstOrNull { it.id == categoryId } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            DetailTopBar(
                title = when {
                    isNew -> "记一笔"
                    editing -> "编辑记一笔"
                    else -> "交易详情"
                },
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val viewTx = detailTx
            when {
                isNew || editing -> TransactionFormContent(
                    type = type,
                    amountFen = amountFen,
                    date = date,
                    description = description,
                    formCategory = formCategory,
                    isEdit = !isNew,
                    originalTx = viewTx,
                    onTypeSelect = viewModel::setType,
                    onAmountChange = viewModel::setAmount,
                    onCategoryClick = { showCategoryPicker = true },
                    onDateChange = viewModel::setDate,
                    onDescriptionChange = viewModel::setDescription,
                    onDelete = { showDeleteConfirm = true },
                    onSave = viewModel::save,
                    isSaving = isSaving,
                )
                viewTx == null -> LoadingState()
                else -> TransactionViewContent(
                    tx = viewTx,
                    categories = categories,
                    onEdit = { editing = true },
                    onBack = onBack,
                )
            }
        }
    }

    // 分类选择弹层（复用 CategoryPickerSheet，按 type 过滤 + 初始选中）。
    if (showCategoryPicker) {
        CategoryPickerSheet(
            categories = categories,
            type = type.toCategoryType(),
            excludeId = null,
            initialId = categoryId,
            onPick = { category ->
                viewModel.setCategoryId(category.id)
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false },
        )
    }

    // 编辑态删除强确认。
    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "删除这笔记录",
            message = "确定删除这笔交易吗？删除后不可恢复。",
            confirmLabel = "删除",
            danger = true,
            onConfirm = {
                showDeleteConfirm = false
                viewModel.delete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

/** 顶栏：标题 + 返回 ←（material-icons 缺失，用 `Text("←")` 占位）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Text(
                    "←",
                    fontSize = 22.sp,
                    modifier = Modifier.semantics { contentDescription = "返回" },
                )
            }
        },
    )
}

/** 新增/编辑共用表单（P-07）：类型/金额/分类/日期/备注 + 编辑态删除/保存。 */
@Composable
private fun ColumnScope.TransactionFormContent(
    type: TransactionType,
    amountFen: Long?,
    date: String,
    description: String,
    formCategory: Category?,
    isEdit: Boolean,
    originalTx: Transaction?,
    onTypeSelect: (TransactionType) -> Unit,
    onAmountChange: (Long?) -> Unit,
    onCategoryClick: () -> Unit,
    onDateChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    isSaving: Boolean,
) {
    TransactionTypeTab(
        selected = type,
        onSelect = { selected -> selected?.let(onTypeSelect) },
        modifier = Modifier.fillMaxWidth(),
    )

    MoneyInputField(
        fen = amountFen,
        onFenChange = onAmountChange,
        label = "金额",
    )

    // 编辑态只读信息块（§9.3.3）：导入带入的 source/对方/支付方式不可编辑，
    // 于金额与分类之间聚簇展示；手动且两字段空则不显示（零噪音）。
    if (isEdit && originalTx != null && originalTx.hasImportedFields()) {
        ImportedFieldsInfoBlock(originalTx)
    }

    Surface(
        onClick = onCategoryClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (formCategory != null) {
                Text(formCategory.icon, fontSize = 20.sp)
                Spacer(Modifier.width(12.dp))
                Text(
                    formCategory.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    "请选择分类",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            Text("▸", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    DatePickerField(date = date, onDateChange = onDateChange)

    OutlinedTextField(
        value = description,
        onValueChange = { new -> if (new.length <= Validators.MAX_NOTE_LENGTH) onDescriptionChange(new) },
        label = { Text("备注") },
        supportingText = { Text("${description.length}/${Validators.MAX_NOTE_LENGTH}") },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )

    if (isEdit) {
        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Text("删除")
        }
    }

    Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) {
        Text("保存")
    }
}

/** 查看态只读明细（P-07）：全字段 + 底部「编辑」。 */
@Composable
private fun TransactionViewContent(
    tx: Transaction,
    categories: List<Category>,
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    val category = categories.firstOrNull { it.id == tx.categoryId }
    val fallback = MaterialTheme.colorScheme.outline
    val iconColor = remember(category?.color) { parseHexColor(category?.color, fallback) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DetailRow(label = "类型") {
            Text(if (tx.type == TransactionType.INCOME) "收入" else "支出", style = MaterialTheme.typography.bodyLarge)
        }
        DetailRow(label = "金额") {
            MoneyText(
                fen = tx.amount,
                semantic = if (tx.type == TransactionType.INCOME) MoneySemantic.INCOME else MoneySemantic.EXPENSE,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        DetailRow(label = "分类") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(category?.icon ?: "❔", fontSize = 18.sp, color = iconColor)
                Spacer(Modifier.width(6.dp))
                Text(
                    category?.name ?: "未分类",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DetailRow(label = "日期") {
            Text(TimeFormatter.formatDate(tx.date), style = MaterialTheme.typography.bodyLarge)
        }
        DetailRow(label = "对方") {
            Text(blankToDash(tx.counterparty), style = MaterialTheme.typography.bodyLarge, color = nullableTextColor(tx.counterparty))
        }
        DetailRow(label = "支付方式") {
            Text(blankToDash(tx.paymentMethod), style = MaterialTheme.typography.bodyLarge, color = nullableTextColor(tx.paymentMethod))
        }
        DetailRow(label = "来源") {
            Text(tx.source.chineseLabel, style = MaterialTheme.typography.bodyLarge)
        }
        DetailRow(label = "备注") {
            Text(
                tx.description ?: "—",
                style = MaterialTheme.typography.bodyLarge,
                color = nullableTextColor(tx.description),
            )
        }
        DetailRow(label = "记录时间") {
            Text(TimeFormatter.formatDate(tx.createdAt), style = MaterialTheme.typography.bodyLarge)
        }
        tx.externalId?.takeIf { it.isNotBlank() }?.let { externalId ->
            DetailRow(label = "外部单号") {
                Text(externalId, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("编辑") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }
    }
}

/**
 * 编辑态只读信息块（§9.3.3）：来源恒显示中文（手动记录/微信/支付宝），对方/支付方式
 * 空值「—」弱化占位，单行 `bodySmall + onSurfaceVariant`。
 */
@Composable
private fun ImportedFieldsInfoBlock(tx: Transaction) {
    val counterparty = tx.counterparty?.takeIf { it.isNotBlank() } ?: "—"
    val paymentMethod = tx.paymentMethod?.takeIf { it.isNotBlank() } ?: "—"
    Text(
        text = "来源：${tx.source.chineseLabel} · 对方：$counterparty · 支付方式：$paymentMethod",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** §9.3.3 展示门槛：来源非手动（或对方/支付方式任一非空）才显示只读信息块。 */
private fun Transaction.hasImportedFields(): Boolean =
    source != TransactionSource.MANUAL || !counterparty.isNullOrBlank() || !paymentMethod.isNullOrBlank()

/** 只读明细行：左侧定宽标签 + 右侧值槽（对齐明细页通用展示口径）。 */
@Composable
private fun DetailRow(
    label: String,
    modifier: Modifier = Modifier,
    value: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Box(Modifier.weight(1f)) { value() }
    }
}

/** 空/非法 hex → 主题默认；合法 → 解析为 Compose 色（同工程 Category* 口径）。 */
private fun parseHexColor(colorHex: String?, fallback: Color): Color =
    if (colorHex.isNullOrBlank()) {
        fallback
    } else {
        runCatching { Color(AndroidColor.parseColor(colorHex)) }.getOrDefault(fallback)
    }

/** 空串/空白 → "—" 占位。 */
private fun blankToDash(value: String?): String = value?.takeIf { it.isNotBlank() } ?: "—"

/** 可空字段文本色：有值 onSurface、无值 onSurfaceVariant（弱化占位对齐明细口径）。 */
@Composable
private fun nullableTextColor(value: String?): Color =
    if (value.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface