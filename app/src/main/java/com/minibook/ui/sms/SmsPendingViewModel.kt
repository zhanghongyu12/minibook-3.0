package com.minibook.ui.sms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.data.local.sms.SmsNotificationHelper
import com.minibook.domain.common.LedgerNotFound
import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.model.SmsAmbiguousHit
import com.minibook.domain.model.SmsPendingItem
import com.minibook.domain.usecase.sms.SmsBillImportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 「待确认 / 未识别」清单页 ViewModel（F-14，UI §11.1）。
 *
 * **数据来源**：当前 domain 层只提供 `SmsBillImportUseCase.scan`（干跑，零写入）这一条读取路径
 * （实时 L1 的待确认条目只累加通知、未持久化），故本页以「重跑一次干跑扫描」取得
 * `unrecognized`（未识别）与 `ambiguous`（疑似重复）两类需人工处理的条目，**不新增存储**。
 *
 * 进入本页即清除「待确认」聚合通知（[SmsNotificationHelper.clearPending]）。
 *
 * 隐私（DEC-026）：只持有并展示脱敏摘要（[SmsPendingItem.preview]）与自建 `description`，
 * VM 不写任何日志、不持有短信原文。
 */
@HiltViewModel
class SmsPendingViewModel @Inject constructor(
    private val smsBillImport: SmsBillImportUseCase,
    private val ledgerProvider: LedgerProvider,
    private val notificationHelper: SmsNotificationHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmsPendingUiState())
    val uiState: StateFlow<SmsPendingUiState> = _uiState.asStateFlow()

    /**
     * 拉取清单（需已授予 `READ_SMS`）。
     *
     * @param readSmsGranted 由 UI 层经 `checkSelfPermission` 判定后传入；false 时只置
     *   [SmsPendingUiState.permissionNeeded]（引导文案），不发请求。
     */
    fun load(readSmsGranted: Boolean) {
        if (!readSmsGranted) {
            _uiState.value = SmsPendingUiState(loading = false, permissionNeeded = true)
            return
        }
        _uiState.value = SmsPendingUiState(loading = true)
        viewModelScope.launch {
            _uiState.value = try {
                val ledgerId = ledgerProvider.currentLedgerId()
                val preview = smsBillImport.scan(ledgerId)
                notificationHelper.clearPending()
                SmsPendingUiState(
                    loading = false,
                    unrecognized = preview.unrecognized,
                    ambiguous = preview.ambiguous,
                    scanTotal = preview.scanTotal,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: LedgerNotFound) {
                SmsPendingUiState(loading = false, errorMessage = LEDGER_ERROR_MESSAGE)
            } catch (_: SecurityException) {
                SmsPendingUiState(loading = false, permissionNeeded = true)
            } catch (_: Exception) {
                SmsPendingUiState(loading = false, errorMessage = LOAD_ERROR_MESSAGE)
            }
        }
    }

    companion object {
        /** 当前账本未初始化（防御；复用既有账本口径文案）。 */
        const val LEDGER_ERROR_MESSAGE = "账本不存在或当前账本未初始化"

        /** 清单读取失败兜底文案（不含任何短信内容）。 */
        const val LOAD_ERROR_MESSAGE = "读取待确认清单失败，请稍后重试。"
    }
}

/**
 * 待确认清单页 UI 状态。
 *
 * @property unrecognized 未识别条目（脱敏摘要 + 原因）。
 * @property ambiguous 疑似重复（同账本、同向、同额、同日多条候选）。
 * @property scanTotal 本次干跑扫描的短信总数（页头「扫描 N 条」）。
 * @property permissionNeeded 未授予 `READ_SMS`（只展示引导）。
 * @property errorMessage 读取失败文案（null = 无错误）。
 */
data class SmsPendingUiState(
    val loading: Boolean = true,
    val permissionNeeded: Boolean = false,
    val unrecognized: List<SmsPendingItem> = emptyList(),
    val ambiguous: List<SmsAmbiguousHit> = emptyList(),
    val scanTotal: Int = 0,
    val errorMessage: String? = null,
) {
    /** 无待确认条目（且非加载/错误/权限态）。 */
    val isEmpty: Boolean
        get() = !loading && !permissionNeeded && errorMessage == null &&
            unrecognized.isEmpty() && ambiguous.isEmpty()
}
