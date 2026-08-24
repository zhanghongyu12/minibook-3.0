package com.minibook.ui.billimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.domain.common.LedgerNotFound
import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.model.BillImportResult
import com.minibook.domain.usecase.bill.ImportBillUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 账单导入页 ViewModel（TASK-044，UI §9.2 P-15）。
 *
 * 状态机 [ImportUiState] 四态原地切换，对齐 UI §9.2：
 * - `Idle`（提示页，初态 / SAF 取消静默保留 /「重新选择」复位）→
 * - `Busy`（选文件后立即锁入口，屏显 [com.minibook.ui.components.FullScreenBusyOverlay]）→
 * - `Result`（[ImportBillUseCase] 返回 [BillImportResult]，一次性展示成功/跳过分原因）或
 * - `Error`（未识别格式 / 事务回滚 / 账本未初始化，文案见 [ImportViewModel] companion）。
 *
 * SAF 生命周期由 [ImportScreen] 层 `rememberLauncherForActivityResult` 接线；本 VM 只收
 * `uri.toString()`（[com.minibook.data.local.bill.SafBillFilePreparer] 接收 uriString，
 * ARCH §13.6）。当前账本经 [LedgerProvider.currentLedgerId] 贯穿（ARCH §8、UI §9.7 D-28），
 * 切账本导入落入「当前账本」。错误文案采用 §9.6 精确中文映射。
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importBill: ImportBillUseCase,
    private val ledgerProvider: LedgerProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /**
     * SAF 选文件回调（§9.2 交互流 2/3）。
     *
     * `uriString == null` = 用户主动取消 → 静默保留 [ImportUiState.Idle]（非异常）。
     * 非空 → 立即 [ImportUiState.Busy]，取当前账本 → `importBill(uriString, ledgerId)`，
     * `unrecognized` 折叠为「未识别账单格式」错误；[LedgerNotFound]（防御）与其余异常
     * 分别映射 §9.6「事务回滚」文案。
     */
    fun onFilePicked(uriString: String?) {
        if (uriString == null) return
        _uiState.value = ImportUiState.Busy
        viewModelScope.launch {
            _uiState.value = try {
                val ledgerId = ledgerProvider.currentLedgerId()
                val result = importBill(uriString, ledgerId)
                if (result.unrecognized) {
                    ImportUiState.Error(UNRECOGNIZED_MESSAGE)
                } else {
                    ImportUiState.Result(result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: LedgerNotFound) {
                ImportUiState.Error(LEDGER_ERROR_MESSAGE)
            } catch (_: Exception) {
                ImportUiState.Error(ROLLBACK_ERROR_MESSAGE)
            }
        }
    }

    /** 「重新选择」：Error → 原地复位 [ImportUiState.Idle]（不 popBackStack，允许重选）。 */
    fun retry() {
        _uiState.value = ImportUiState.Idle
    }

    /** 关闭/离开复位（防御，Result 不持久，一次性反馈仅存于当前返回栈实例）。 */
    fun dismiss() {
        _uiState.value = ImportUiState.Idle
    }

    companion object {
        /** 未识别格式（含表头未命中，§9.6 精确文案）。 */
        const val UNRECOGNIZED_MESSAGE = "未识别账单格式，请选择微信 .xlsx 或支付宝 .csv 账单文件。"

        /** 事务回滚 / 解析 IO 异常（零落库，§9.6 精确文案）。 */
        const val ROLLBACK_ERROR_MESSAGE = "导入失败，数据未写入，请重试。"

        /** 当前账本未初始化（防御；复用既有账本口径文案，非 §9.6 表内，故仅兜底）。 */
        const val LEDGER_ERROR_MESSAGE = "账本不存在或当前账本未初始化"
    }
}

/**
 * 账单导入页 UI 四态（UI §9.2）。
 *
 * Empty 态不适用（导入无「无数据」态；全部跳过走 [Result] 的「本次无新增交易」分支，§9.2 状态说明）。
 */
sealed interface ImportUiState {

    /** 提示页：平台导出指引 + 「去选择文件」（初态 / 取消 / 重选复位）。 */
    data object Idle : ImportUiState

    /** 忙碌：全屏遮罩锁入口，文案「正在解析账单，请稍候…」。 */
    data object Busy : ImportUiState

    /** 结果：成功/跳过摘要 + 分原因明细（一次性展示）。 */
    data class Result(val result: BillImportResult) : ImportUiState

    /** 错误：未识别格式 / 事务回滚 / 账本未初始化，配「重新选择」回 Idle。 */
    data class Error(val message: String) : ImportUiState
}