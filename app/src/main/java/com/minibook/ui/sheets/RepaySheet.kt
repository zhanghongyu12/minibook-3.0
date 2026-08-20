package com.minibook.ui.sheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.DebtDirection
import com.minibook.ui.components.DatePickerField
import com.minibook.ui.components.MoneyInputField
import com.minibook.ui.components.RepayDirectionCard
import com.minibook.ui.components.SheetFormScaffold
import com.minibook.util.Validators

/**
 * 快捷还款 Sheet（TASK-028，UI §2 P-09）。
 *
 * 入口于债务「按对象视图」点击 收到还款/我还款 按钮拉起：对方姓名与方向预填只读
 * （本文件不负责推导，方向由入口传入 [direction]），标题随方向切换
 * （收到还款（张三）/ 我还款（张三））。
 *
 * 构成：
 * - partyName [OutlinedTextField] 只读预填 + [RepayDirectionCard] 只读态展示方向；
 * - [MoneyInputField] 金额（默认 0，聚焦待输；0/负/超限内部拦截 + [RepaySheetViewModel.save] 双保险）；
 * - [DatePickerField] 日期 + 备注（≤200 字实时计数）；
 * - 确认按钮文案「确认还款」（UI §2 P-09）。
 *
 * 保存成功（VM [RepaySheetViewModel.saved]）→ [onDismiss] 关闭；校验/保存失败经 VM
 * [RepaySheetViewModel.snackbar] 内联 SnackbarHost 展示，不关闭。
 *
 * @param partyName 对方姓名（预填只读）。
 * @param direction 还款方向（预填只读，决定标题与展示）。
 * @param onDismiss 关闭回调（保存成功/取消均回调）。
 * @param viewModel 注入的 VM（默认 `hiltViewModel()`，编排者也可传入自定义作用域实例）。
 */
@Composable
fun RepaySheet(
    partyName: String,
    direction: DebtDirection,
    onDismiss: () -> Unit,
    viewModel: RepaySheetViewModel = hiltViewModel(),
) {
    val viewPartyName by viewModel.partyName.collectAsStateWithLifecycle()
    val viewDirection by viewModel.direction.collectAsStateWithLifecycle()
    val amountFen by viewModel.amountFen.collectAsStateWithLifecycle()
    val date by viewModel.date.collectAsStateWithLifecycle()
    val remark by viewModel.remark.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbar.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // 打开时预填对方姓名与方向
    LaunchedEffect(partyName, direction) {
        viewModel.init(partyName, direction)
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

    // 标题随方向：收到还款（张三）/ 我还款（张三）
    val title = when (viewDirection) {
        DebtDirection.OWE_ME -> "收到还款（$viewPartyName）"
        DebtDirection.I_OWE -> "我还款（$viewPartyName）"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SheetFormScaffold(
            title = title,
            onConfirm = { viewModel.save() },
            onDismiss = dismissAndClear,
            confirmEnabled = !isSaving,
            confirmLabel = "确认还款",
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = viewPartyName,
                onValueChange = {},
                label = { Text("对方姓名") },
                singleLine = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            RepayDirectionCard(
                selected = viewDirection,
                onSelect = {},
                readOnly = true,
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
}
