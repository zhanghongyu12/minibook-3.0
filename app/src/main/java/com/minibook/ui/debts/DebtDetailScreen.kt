package com.minibook.ui.debts

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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.Debt
import com.minibook.domain.model.DebtDirection
import com.minibook.domain.model.DebtKind
import com.minibook.ui.components.ConfirmDialog
import com.minibook.ui.components.DatePickerField
import com.minibook.ui.components.LoadingState
import com.minibook.ui.components.MoneyInputField
import com.minibook.ui.components.MoneySemantic
import com.minibook.ui.components.MoneyText
import com.minibook.ui.components.RepayDirectionCard
import com.minibook.ui.sheets.DebtSheetViewModel
import com.minibook.util.TimeFormatter
import com.minibook.util.Validators
import kotlin.math.abs

/**
 * 债务详情全屏页（P-08/P-09 升级：新增 / 查看 / 编辑 / 还款 四态合一，替代原 ModalBottomSheet）。
 *
 * 由根图 `debt-detail?id={id}&party={party}&direction={direction}` 承载：
 * - **新增态**（debtId=null 且 prefillParty=null）：借还表单（借出/借入分段 + 「这是一笔还款」Switch →
 *   还款开启时 [RepayDirectionCard] 选方向 + 对方 + 金额 + 日期 + 备注 + 保存）；
 * - **查看态**（debtId 非空，默认只读）：完整字段明细 + 底部「编辑」「还款」→ 切编辑/还款态；
 * - **编辑态**（查看态点「编辑」）：借还表单预填 + 保存/删除（danger [ConfirmDialog]）；
 * - **还款态**（查看态点「还款」，或入口带 prefillParty/prefillDirection）：对方只读 + [RepayDirectionCard]
 *   只读 + 金额 + 日期 + 备注 + 「确认还款」。
 *
 * 复用 [DebtSheetViewModel]（不新建重复）：四态状态机与 save/delete 逻辑不变，新增
 * [DebtSheetViewModel.detailDebt]（查看态全字段回填）与 [DebtSheetViewModel.initRepay]（还款预填）。
 * 查看态↔编辑/还款态切换为页内 `remember { mutableStateOf }`（默认查看）。
 *
 * @param debtId 目标债务 id（null = 新增/还款预填）。
 * @param prefillParty 还款预填对方姓名（非空 = 直接进还款态）。
 * @param prefillDirection 还款预填方向（非空时与 prefillParty 配套）。
 * @param onBack 返回回调（NavHost 接线传入 `rootController.popBackStack()`）。
 * @param viewModel 复用债务 VM；默认经 Hilt 注入（作用域为当前 nav backStack entry）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtDetailScreen(
    debtId: String?,
    prefillParty: String?,
    prefillDirection: DebtDirection?,
    onBack: () -> Unit,
    viewModel: DebtSheetViewModel = hiltViewModel(),
) {
    val partyName by viewModel.partyName.collectAsStateWithLifecycle()
    val amountFen by viewModel.amountFen.collectAsStateWithLifecycle()
    val kind by viewModel.kind.collectAsStateWithLifecycle()
    val direction by viewModel.direction.collectAsStateWithLifecycle()
    val date by viewModel.date.collectAsStateWithLifecycle()
    val remark by viewModel.remark.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbar.collectAsStateWithLifecycle()
    val partyNetFen by viewModel.partyNetFen.collectAsStateWithLifecycle()
    val detailDebt by viewModel.detailDebt.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // 页态：isNew=新增表单；isPrefillRepay=入口直接还款；editing/repaying=查看态内切换。
    val isNew = debtId == null && prefillParty == null
    val isPrefillRepay = debtId == null && prefillParty != null
    var editing by remember(debtId, prefillParty) { mutableStateOf(false) }
    var repaying by remember(debtId, prefillParty) { mutableStateOf(false) }

    // 打开时初始化：编辑回填（含 detailDebt） / 还款预填 / 新增重置。
    LaunchedEffect(debtId, prefillParty, prefillDirection) {
        editing = false
        repaying = false
        when {
            debtId != null -> viewModel.initEdit(debtId)
            prefillParty != null -> viewModel.initRepay(prefillParty, prefillDirection ?: DebtDirection.OWE_ME)
            else -> viewModel.initNew()
        }
    }

    // snackbar：展示后立即清除（防重复触发）。
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // 保存/还款/删除成功 → 返回上一页。
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(viewModel) {
        viewModel.saved.collect { currentOnBack() }
    }

    // repay 态超净额提示（净额 null 视作 0：无历史记录时任意正数还款均超净额）。
    val amount = amountFen
    val showOverpayHint = kind == DebtKind.REPAY &&
        amount != null &&
        amount > abs(partyNetFen ?: 0L)

    val title = when {
        isNew -> "记一笔债务"
        isPrefillRepay || repaying -> if (direction == DebtDirection.OWE_ME) "收到还款" else "我还款"
        editing -> "编辑债务"
        else -> "债务详情"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { DetailTopBar(title = title, onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val viewDebt = detailDebt
            when {
                isNew || editing -> DebtForm(
                    partyName = partyName,
                    amountFen = amountFen,
                    kind = kind,
                    direction = direction,
                    date = date,
                    remark = remark,
                    showOverpayHint = showOverpayHint,
                    isEdit = editing,
                    onSelectLendOweMe = {
                        viewModel.setKind(DebtKind.LEND)
                        viewModel.setDirection(DebtDirection.OWE_ME)
                    },
                    onSelectLendIOwe = {
                        viewModel.setKind(DebtKind.LEND)
                        viewModel.setDirection(DebtDirection.I_OWE)
                    },
                    onRepaySwitch = { repay -> viewModel.setKind(if (repay) DebtKind.REPAY else DebtKind.LEND) },
                    onDirectionSelect = viewModel::setDirection,
                    onPartyNameChange = viewModel::setPartyName,
                    onAmountChange = viewModel::setAmount,
                    onDateChange = viewModel::setDate,
                    onRemarkChange = viewModel::setRemark,
                    onDelete = { showDeleteConfirm = true },
                    onSave = viewModel::save,
                    isSaving = isSaving,
                )
                isPrefillRepay || repaying -> RepayForm(
                    partyName = partyName,
                    direction = direction,
                    amountFen = amountFen,
                    date = date,
                    remark = remark,
                    showOverpayHint = showOverpayHint,
                    onAmountChange = viewModel::setAmount,
                    onDateChange = viewModel::setDate,
                    onRemarkChange = viewModel::setRemark,
                    onSave = viewModel::save,
                    isSaving = isSaving,
                )
                viewDebt == null -> LoadingState()
                else -> DebtViewContent(
                    debt = viewDebt,
                    onEdit = { editing = true },
                    onRepay = {
                        viewModel.initRepay(viewDebt.partyName, viewDebt.direction)
                        repaying = true
                    },
                    onBack = onBack,
                )
            }
        }
    }

    // 编辑态删除强确认。
    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "删除这笔记录",
            message = "确定删除这条债务记录吗？删除后不可恢复。",
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

/** 新增/编辑共用借还表单（对齐原 DebtSheet 内容）：借出/借入分段 + 还款 Switch + 还款方向卡 + 字段。 */
@Composable
private fun ColumnScope.DebtForm(
    partyName: String,
    amountFen: Long?,
    kind: DebtKind,
    direction: DebtDirection,
    date: String,
    remark: String,
    showOverpayHint: Boolean,
    isEdit: Boolean,
    onSelectLendOweMe: () -> Unit,
    onSelectLendIOwe: () -> Unit,
    onRepaySwitch: (Boolean) -> Unit,
    onDirectionSelect: (DebtDirection) -> Unit,
    onPartyNameChange: (String) -> Unit,
    onAmountChange: (Long?) -> Unit,
    onDateChange: (String) -> Unit,
    onRemarkChange: (String) -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    isSaving: Boolean,
) {
    // kind 分段：借出 / 借入（方向联动，构成四态中借的两态）
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = kind == DebtKind.LEND && direction == DebtDirection.OWE_ME,
            onClick = onSelectLendOweMe,
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = { Text("借出") },
        )
        SegmentedButton(
            selected = kind == DebtKind.LEND && direction == DebtDirection.I_OWE,
            onClick = onSelectLendIOwe,
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text("借入") },
        )
    }

    // 当前四态语义说明
    Text(
        text = if (kind == DebtKind.REPAY) {
            if (direction == DebtDirection.OWE_ME) "收到还款：对方还我欠款" else "我还款：归还对方欠款"
        } else {
            if (direction == DebtDirection.OWE_ME) "借出：对方欠我" else "借入：我欠对方"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // 这是一笔还款：Switch 切到 repay 态，浮现方向选择
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("这是一笔还款", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = kind == DebtKind.REPAY, onCheckedChange = onRepaySwitch)
    }

    if (kind == DebtKind.REPAY) {
        RepayDirectionCard(
            selected = direction,
            onSelect = onDirectionSelect,
            modifier = Modifier.fillMaxWidth(),
        )
        if (showOverpayHint) {
            Text(
                "金额超过当前净欠额，请确认",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    OutlinedTextField(
        value = partyName,
        onValueChange = onPartyNameChange,
        label = { Text("对方姓名") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    MoneyInputField(fen = amountFen, onFenChange = onAmountChange, label = "金额")

    DatePickerField(date = date, onDateChange = onDateChange)

    OutlinedTextField(
        value = remark,
        onValueChange = { new -> if (new.length <= Validators.MAX_NOTE_LENGTH) onRemarkChange(new) },
        label = { Text("备注") },
        supportingText = { Text("${remark.length}/${Validators.MAX_NOTE_LENGTH}") },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )

    if (isEdit) {
        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("删除") }
    }

    Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) { Text("保存") }
}

/** 还款表单（还款态）：对方只读 + 方向卡只读 + 金额/日期/备注 + 「确认还款」。 */
@Composable
private fun ColumnScope.RepayForm(
    partyName: String,
    direction: DebtDirection,
    amountFen: Long?,
    date: String,
    remark: String,
    showOverpayHint: Boolean,
    onAmountChange: (Long?) -> Unit,
    onDateChange: (String) -> Unit,
    onRemarkChange: (String) -> Unit,
    onSave: () -> Unit,
    isSaving: Boolean,
) {
    OutlinedTextField(
        value = partyName,
        onValueChange = {},
        label = { Text("对方姓名") },
        singleLine = true,
        enabled = false,
        modifier = Modifier.fillMaxWidth(),
    )

    RepayDirectionCard(
        selected = direction,
        onSelect = {},
        readOnly = true,
        modifier = Modifier.fillMaxWidth(),
    )

    if (showOverpayHint) {
        Text(
            "金额超过当前净欠额，请确认",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    MoneyInputField(fen = amountFen, onFenChange = onAmountChange, label = "金额")

    DatePickerField(date = date, onDateChange = onDateChange)

    OutlinedTextField(
        value = remark,
        onValueChange = { new -> if (new.length <= Validators.MAX_NOTE_LENGTH) onRemarkChange(new) },
        label = { Text("备注") },
        supportingText = { Text("${remark.length}/${Validators.MAX_NOTE_LENGTH}") },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )

    Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) { Text("确认还款") }
}

/** 查看态只读明细（P-08）：全字段 + 底部「编辑」「还款」。 */
@Composable
private fun DebtViewContent(
    debt: Debt,
    onEdit: () -> Unit,
    onRepay: () -> Unit,
    onBack: () -> Unit,
) {
    val (netFen, semantic) = debtNetContribution(debt)
    Column {
        DetailRow(label = "类型") {
            Text(debtKindLabel(debt), style = MaterialTheme.typography.bodyLarge)
        }
        DetailRow(label = "对方") {
            Text(debt.partyName, style = MaterialTheme.typography.bodyLarge)
        }
        DetailRow(label = "金额") {
            MoneyText(fen = netFen, semantic = semantic, style = MaterialTheme.typography.titleMedium)
        }
        DetailRow(label = "日期") {
            Text(TimeFormatter.formatDate(debt.date), style = MaterialTheme.typography.bodyLarge)
        }
        DetailRow(label = "备注") {
            Text(
                debt.remark ?: "—",
                style = MaterialTheme.typography.bodyLarge,
                color = if (debt.remark.isNullOrBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        DetailRow(label = "记录时间") {
            Text(TimeFormatter.formatDate(debt.createdAt), style = MaterialTheme.typography.bodyLarge)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("编辑") }
            Button(onClick = onRepay, modifier = Modifier.weight(1f)) { Text("还款") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }
    }
}

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

/** kind×direction → 中文标签（PRD F-02）：借出/借入/收到还款/我还款。 */
private fun debtKindLabel(debt: Debt): String = when {
    debt.kind == DebtKind.LEND && debt.direction == DebtDirection.OWE_ME -> "借出"
    debt.kind == DebtKind.LEND && debt.direction == DebtDirection.I_OWE -> "借入"
    debt.kind == DebtKind.REPAY && debt.direction == DebtDirection.OWE_ME -> "收到还款"
    else -> "我还款"
}

/** 债务净额贡献：分（可负）+ 语义色（PRD F-02）。 */
private fun debtNetContribution(debt: Debt): Pair<Long, MoneySemantic> = when {
    debt.kind == DebtKind.LEND && debt.direction == DebtDirection.OWE_ME -> debt.amount to MoneySemantic.INCOME
    debt.kind == DebtKind.LEND && debt.direction == DebtDirection.I_OWE -> -debt.amount to MoneySemantic.EXPENSE
    debt.kind == DebtKind.REPAY && debt.direction == DebtDirection.OWE_ME -> -debt.amount to MoneySemantic.EXPENSE
    else -> debt.amount to MoneySemantic.INCOME // repay × i_owe
}