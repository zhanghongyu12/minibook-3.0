package com.minibook.ui.sheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.Debt
import com.minibook.domain.model.DebtDirection
import com.minibook.domain.model.DebtKind
import com.minibook.ui.components.ConfirmDialog
import com.minibook.ui.components.DatePickerField
import com.minibook.ui.components.MoneyInputField
import com.minibook.ui.components.RepayDirectionCard
import com.minibook.ui.components.SheetFormScaffold
import com.minibook.util.Validators
import kotlin.math.abs

/**
 * 债务 / 编辑债务 Sheet（TASK-028，UI §2 P-08）。
 *
 * 构成：
 * - 借出/借入 [SegmentedButton]（kind 切换，方向联动：借出→对方欠我、借入→我欠对方）；
 * - 「这是一笔还款」[Switch]：开启后 [kind]=REPAY，下方浮现 [RepayDirectionCard]
 *   选择 收到还款/我还款（方向沿用切换前借出/借入的默认方向，P-08 四态组合）；
 * - repay 态金额超过对方当前净额时提示「金额超过当前净欠额，请确认」，不拦截保存
 *   （还款可超净额，PRD F-02 / UI §2 P-08 边界）；
 * - [MoneyInputField] 金额 + partyName [OutlinedTextField] + [DatePickerField] + 备注；
 * - 编辑态底部「删除」→ danger [ConfirmDialog] 强确认。
 *
 * 成功（VM [DebtSheetViewModel.saved]）→ [onDismiss] 关闭；校验/保存失败经 VM
 * [DebtSheetViewModel.snackbar] 内联 SnackbarHost 展示，不关闭。
 *
 * @param debt 编辑目标（null=新增）；由编排者按导航参数传入。
 * @param onDismiss 关闭回调（保存成功/取消均回调）。
 * @param viewModel 注入的 VM（默认 `hiltViewModel()`，编排者也可传入自定义作用域实例）。
 */
@Composable
fun DebtSheet(
    debt: Debt? = null,
    onDismiss: () -> Unit,
    viewModel: DebtSheetViewModel = hiltViewModel(),
) {
    val partyName by viewModel.partyName.collectAsStateWithLifecycle()
    val amountFen by viewModel.amountFen.collectAsStateWithLifecycle()
    val kind by viewModel.kind.collectAsStateWithLifecycle()
    val direction by viewModel.direction.collectAsStateWithLifecycle()
    val date by viewModel.date.collectAsStateWithLifecycle()
    val remark by viewModel.remark.collectAsStateWithLifecycle()
    val isEditMode by viewModel.isEditMode.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbar.collectAsStateWithLifecycle()
    val partyNetFen by viewModel.partyNetFen.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // 打开/切换目标时初始化：编辑回填 or 新增重置
    LaunchedEffect(debt) {
        if (debt != null) viewModel.initEdit(debt.id) else viewModel.initNew()
    }

    // snackbar：展示后立即清除（防重复触发）
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // 保存成功 → 关闭弹层（统一先清 snackbar 再关闭，防止 Sheet 移出组合时残留「已保存」下次重放）
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val dismissAndClear: () -> Unit = {
        viewModel.clearSnackbar()
        currentOnDismiss()
    }
    LaunchedEffect(viewModel) {
        viewModel.saved.collect { dismissAndClear() }
    }

    // repay 态超净额提示（净额 null 视作 0：无历史记录时任意正数还款均超净额）
    // 金额为委托属性不可智能转换，先取局部变量
    val amount = amountFen
    val showOverpayHint = kind == DebtKind.REPAY &&
        amount != null &&
        amount > abs(partyNetFen ?: 0L)

    Box(modifier = Modifier.fillMaxSize()) {
        SheetFormScaffold(
            title = if (isEditMode) "编辑债务" else "债务",
            onConfirm = { viewModel.save() },
            onDismiss = dismissAndClear,
            confirmEnabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // kind 分段：借出 / 借入（方向联动，构成四态中借的两态）
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = kind == DebtKind.LEND && direction == DebtDirection.OWE_ME,
                    onClick = {
                        viewModel.setKind(DebtKind.LEND)
                        viewModel.setDirection(DebtDirection.OWE_ME)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("借出") },
                )
                SegmentedButton(
                    selected = kind == DebtKind.LEND && direction == DebtDirection.I_OWE,
                    onClick = {
                        viewModel.setKind(DebtKind.LEND)
                        viewModel.setDirection(DebtDirection.I_OWE)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("借入") },
                )
            }

            Spacer(Modifier.height(8.dp))

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

            Spacer(Modifier.height(12.dp))

            // 这是一笔还款：Switch 切到 repay 态，浮现方向选择
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "这是一笔还款",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = kind == DebtKind.REPAY,
                    onCheckedChange = { checked ->
                        viewModel.setKind(if (checked) DebtKind.REPAY else DebtKind.LEND)
                    },
                )
            }

            if (kind == DebtKind.REPAY) {
                Spacer(Modifier.height(8.dp))

                RepayDirectionCard(
                    selected = direction,
                    onSelect = viewModel::setDirection,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (showOverpayHint) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "金额超过当前净欠额，请确认",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = partyName,
                onValueChange = viewModel::setPartyName,
                label = { Text("对方姓名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            MoneyInputField(
                fen = amountFen,
                onFenChange = viewModel::setAmount,
                label = "金额",
            )

            Spacer(Modifier.height(12.dp))

            DatePickerField(
                date = date,
                onDateChange = viewModel::setDate,
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = remark,
                onValueChange = { new -> if (new.length <= Validators.MAX_NOTE_LENGTH) viewModel.setRemark(new) },
                label = { Text("备注") },
                supportingText = { Text("${remark.length}/${Validators.MAX_NOTE_LENGTH}") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            if (isEditMode) {
                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("删除")
                }
            }
        }

        // 校验/失败提示浮于底部按钮上方
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp),
        )
    }

    // 编辑态删除强确认
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
