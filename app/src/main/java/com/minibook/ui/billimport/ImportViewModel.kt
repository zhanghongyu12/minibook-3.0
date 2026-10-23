package com.minibook.ui.billimport

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.data.local.mail.MailAccountStore
import com.minibook.data.local.mail.MailAuthException
import com.minibook.data.local.mail.MailNetworkException
import com.minibook.domain.common.LedgerNotFound
import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.model.BillImportResult
import com.minibook.domain.model.MailAccount
import com.minibook.domain.model.MailImportOutcome
import com.minibook.domain.model.MailImportProgress
import com.minibook.domain.model.MailImportStep
import com.minibook.domain.model.MailImportStepState
import com.minibook.domain.model.MailImportStepStatus
import com.minibook.domain.model.MailImportSummary
import com.minibook.domain.model.SmsImportSummary
import com.minibook.domain.model.SmsScanMode
import com.minibook.domain.model.SmsScanPreview
import com.minibook.domain.usecase.bill.ImportBillUseCase
import com.minibook.domain.usecase.mail.MailBillImportUseCase
import com.minibook.domain.usecase.sms.SmsBillImportUseCase
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
    private val mailBillImport: MailBillImportUseCase,
    private val smsBillImport: SmsBillImportUseCase,
    private val ledgerProvider: LedgerProvider,
    private val mailAccountStore: MailAccountStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    init {
        // F-14 L3：进入导入页即做一次**静默增量补扫**（补 `SMS_RECEIVED` 广播丢失的短信）。
        // 口径：开关关闭 → 零动作；首次启用 → 只立水位基线，不回灌历史（避免涌进「待确认」）；
        // 补扫不改 UI 状态（不打扰），自动写入与否仍由白名单四条件 + 应用锁门控决定。
        viewModelScope.launch {
            runCatching {
                val ledgerId = ledgerProvider.currentLedgerId()
                smsBillImport.catchUp(ledgerId)
            }
        }
    }

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

    /**
     * 「一键收取邮箱账单」（F-13 多账号，近 30 天窗口）入口分支：
     * - 0 个账号 → [ImportUiState.MailNotConfigured]（引导去设置页）；
     * - 1 个账号 → 直接开始收取（[onFetchAccounts] 传该账号 id）；
     * - ≥2 个账号 → [ImportUiState.MailChooseAccount] 弹窗由用户单选或「全部收取」。
     */
    fun onFetchMailBills() {
        val accounts = mailAccountStore.accounts.value
        when {
            accounts.isEmpty() -> _uiState.value = ImportUiState.MailNotConfigured
            accounts.size == 1 -> onFetchAccounts(listOf(accounts.first().id))
            else -> _uiState.value = ImportUiState.MailChooseAccount(accounts)
        }
    }

    /**
     * 对选定账号集合开始收取（弹窗单选或「全部收取」= 全部 id 一次传入，按顺序处理）。
     *
     * 点击立即 [ImportUiState.MailBusy]（全步骤 PENDING 初始快照）锁入口，取当前账本 →
     * [MailBillImportUseCase] 逐步推进 [ImportUiState.MailBusy]（每步发完整快照直刷 UI）：
     * - [MailImportOutcome.NotConfigured] → [ImportUiState.MailNotConfigured]（引导去设置页）；
     * - [MailImportOutcome.Completed] → [ImportUiState.MailResult]（携带最后一份进度快照，
     *   供**同一页面**原地追加结果区，摘要由 [MailImportPresenter] 渲染；个别邮件失败
     *   不影响整体完成）。
     *
     * 进度 → 结果/错误为**同页衔接**：本地 `lastProgress` 记录最后一份快照，完成与失败
     * 均随状态带出；失败（异常）时先把快照中 RUNNING 的步骤改标 FAILED（[MailImportStepState]
     * 为 data class，直接 copy），再进入 [ImportUiState.MailError]。
     *
     * 异常映射：[LedgerNotFound] → 账本文案；[MailAuthException] /
     * [MailNetworkException]（message 均为固定文案、不含凭据）→ 精确中文；
     * 其余 → 兜底文案。**任何 message / 状态都不携带授权码。**
     */
    fun onFetchAccounts(accountIds: List<String>) {
        if (accountIds.isEmpty()) {
            _uiState.value = ImportUiState.MailNotConfigured
            return
        }
        var lastProgress = initialProgress(accountIds.size)
        _uiState.value = ImportUiState.MailBusy(lastProgress)
        viewModelScope.launch {
            _uiState.value = try {
                val ledgerId = ledgerProvider.currentLedgerId()
                when (val outcome = mailBillImport(ledgerId, accountIds, sinceDays = 30) { progress ->
                    lastProgress = progress
                    _uiState.value = ImportUiState.MailBusy(progress)
                }) {
                    MailImportOutcome.NotConfigured -> ImportUiState.MailNotConfigured
                    is MailImportOutcome.Completed -> ImportUiState.MailResult(outcome.summary, lastProgress)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (mailException: Exception) {
                val failedProgress = lastProgress.copy(
                    steps = lastProgress.steps.map { state ->
                        if (state.status == MailImportStepStatus.RUNNING) {
                            state.copy(status = MailImportStepStatus.FAILED)
                        } else {
                            state
                        }
                    },
                )
                val message = when (mailException) {
                    is LedgerNotFound -> LEDGER_ERROR_MESSAGE
                    is MailAuthException -> MAIL_AUTH_MESSAGE
                    is MailNetworkException -> MAIL_NETWORK_MESSAGE
                    else -> MAIL_UNKNOWN_MESSAGE
                }
                ImportUiState.MailError(message, failedProgress)
            }
        }
    }

    /**
     * 「读取短信账单」入口（F-14，L0）：权限门在 UI 层判定后传入 [readSmsGranted]（未授予只引导、
     * 不发起请求）；扫描**零写入账单**（只可能推进扫描水位），无权限的 [SecurityException] 兜底回引导态。
     *
     * @param mode 两种扫描方式（2026-10-10 用户要求）：[SmsScanMode.INCREMENTAL] = 只看上次扫描水位
     *   之后的新短信（扫完推进水位，下次不再翻出看过的）；[SmsScanMode.FULL] = 全部历史（不动水位，
     *   看过的未识别条目折叠进「此前已看过」）。默认 [SmsScanMode.FULL] 保持既有调用方语义。
     */
    fun onReadSmsBills(readSmsGranted: Boolean, mode: SmsScanMode = SmsScanMode.FULL) {
        if (!readSmsGranted) {
            _uiState.value = ImportUiState.SmsPermissionNeeded
            return
        }
        _uiState.value = ImportUiState.SmsScanning
        viewModelScope.launch {
            _uiState.value = try {
                val ledgerId = ledgerProvider.currentLedgerId()
                val preview = smsBillImport.scan(ledgerId, mode)
                // 「已看过」标记（2026-10-10 用户要求）：本次结果即将上屏 ⇒ 推进未识别已看水位，
                // 下次重扫时这些条目折叠进「此前已看过 N 条」，不再与新增项混在一起（不动任何计数）。
                smsBillImport.acknowledgeUnrecognized(preview.unrecognized)
                ImportUiState.SmsPreview(preview)
            } catch (e: CancellationException) {
                throw e
            } catch (_: LedgerNotFound) {
                ImportUiState.SmsError(LEDGER_ERROR_MESSAGE, SmsImportStep.READ)
            } catch (_: SecurityException) {
                ImportUiState.SmsPermissionNeeded
            } catch (e: Exception) {
                // 诊断口径（对齐 DatabaseGate/ImapMailClient）：只记异常类型与栈，绝不记短信内容/号码。
                Log.e(TAG, "短信扫描失败：${e.javaClass.name}", e)
                ImportUiState.SmsError(SMS_SCAN_ERROR_MESSAGE, SmsImportStep.READ)
            }
        }
    }

    /** 干跑预览确认写入（F-14）：只写「可导入」行，复用 `importParsedRows`（单事务 + IGNORE 幂等）。 */
    fun onConfirmSmsImport() {
        val preview = (_uiState.value as? ImportUiState.SmsPreview)?.preview ?: return
        _uiState.value = ImportUiState.SmsImporting(preview)
        viewModelScope.launch {
            _uiState.value = try {
                val ledgerId = ledgerProvider.currentLedgerId()
                ImportUiState.SmsResult(smsBillImport.importConfirmed(preview, ledgerId), preview)
            } catch (e: CancellationException) {
                throw e
            } catch (_: LedgerNotFound) {
                ImportUiState.SmsError(LEDGER_ERROR_MESSAGE, SmsImportStep.WRITE)
            } catch (e: Exception) {
                Log.e(TAG, "短信导入写入失败：${e.javaClass.name}", e)
                ImportUiState.SmsError(SMS_IMPORT_ERROR_MESSAGE, SmsImportStep.WRITE)
            }
        }
    }

    /** 「重新选择」：Error → 原地复位 [ImportUiState.Idle]（不 popBackStack，允许重选）。 */
    fun retry() {
        _uiState.value = ImportUiState.Idle
    }

    /**
     * 「导入来源」勾选（docs/15 第 2 条，2026-10-13）：落库后**立即按原模式重扫**，
     * 让用户当场看到效果（被关掉的来源：短信从「可导入」变成「非账单·来源未勾选」）。
     *
     * 只改开关、不删短信、不删已入账交易；失败（存储异常）静默保留原状，不打断页面。
     */
    fun onSenderImportEnabled(sender: String, enabled: Boolean, mode: SmsScanMode) {
        viewModelScope.launch {
            runCatching { smsBillImport.setSenderImportEnabled(sender, enabled) }
            onReadSmsBills(readSmsGranted = true, mode = mode)
        }
    }

    /** 关闭/离开复位（防御，Result 不持久，一次性反馈仅存于当前返回栈实例）。 */
    fun dismiss() {
        _uiState.value = ImportUiState.Idle
    }

    /** 收取开始前的初始快照：5 步全 PENDING + 邮箱总数。 */
    private fun initialProgress(accountCount: Int): MailImportProgress = MailImportProgress(
        steps = MailImportStep.entries.map { MailImportStepState(it, MailImportStepStatus.PENDING) },
        accountCount = accountCount,
    )

    companion object {
        /** 未识别格式（含表头未命中，§9.6 精确文案）。 */
        const val UNRECOGNIZED_MESSAGE = "未识别账单格式，请选择微信 .xlsx 或支付宝 .csv 账单文件。"

        /** 事务回滚 / 解析 IO 异常（零落库，§9.6 精确文案）。 */
        const val ROLLBACK_ERROR_MESSAGE = "导入失败，数据未写入，请重试。"

        /** 当前账本未初始化（防御；复用既有账本口径文案，非 §9.6 表内，故仅兜底）。 */
        const val LEDGER_ERROR_MESSAGE = "账本不存在或当前账本未初始化"

        /** 邮箱登录失败（授权码失效 / 未开启 IMAP 等，F-13 精确文案，不含凭据）。 */
        const val MAIL_AUTH_MESSAGE = "邮箱登录失败，请确认已在 QQ 邮箱开启 IMAP/SMTP 服务且授权码为最新。"

        /** 邮件服务器网络异常（F-13 精确文案）。 */
        const val MAIL_NETWORK_MESSAGE = "网络异常，无法连接邮件服务器，请检查网络后重试。"

        /** 「一键收取」未预期失败兜底文案（不含任何凭据）。 */
        const val MAIL_UNKNOWN_MESSAGE = "收取失败，请稍后重试。"

        /** 诊断日志 TAG（只记异常类型/栈，不记短信内容）。 */
        const val TAG = "MiniBookImport"

        /** F-14 短信扫描失败兜底 / 写入失败（单事务回滚零落库）文案，均不含短信内容。 */
        const val SMS_SCAN_ERROR_MESSAGE = "读取短信失败，请稍后重试。"
        const val SMS_IMPORT_ERROR_MESSAGE = "导入失败，数据未写入，请重试。"
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

    /** 一键收取中：分步骤 stepper + 逐封子步骤展开区（每步一份完整快照，见 [MailImportProgress]）。 */
    data class MailBusy(val progress: MailImportProgress) : ImportUiState

    /**
     * 一键收取完成：逐封摘要（新导入/已存在/成功/失败），失败明细见 [MailImportPresenter]。
     * 携带最后一份进度快照，供进度与结果**同页合并渲染**（结果区原地追加在 stepper 下方）。
     */
    data class MailResult(
        val summary: MailImportSummary,
        val progress: MailImportProgress,
    ) : ImportUiState

    /** 多账号选择弹窗：单选某个邮箱立即收取，或「全部收取」一次传入全部 id。 */
    data class MailChooseAccount(val accounts: List<MailAccount>) : ImportUiState

    /** 未配置邮箱凭据：引导去「设置 → 邮箱凭据」填写。 */
    data object MailNotConfigured : ImportUiState

    /**
     * 一键收取失败：认证/网络/未预期异常已映射为固定中文文案（不含凭据）。
     * 携带失败时刻的进度快照（RUNNING 步骤已改标 FAILED）供同页渲染；null = 无进度可展示
     * （理论上不可达，防御兜底为仅错误区）。
     */
    data class MailError(
        val message: String,
        val progress: MailImportProgress?,
    ) : ImportUiState

    /** 扫描中（F-14 L0 全量干跑，一次调用覆盖「读取 → 识别 → 去重」）。 */
    data object SmsScanning : ImportUiState
    /** 干跑预览（零写入）：四类计数 + 逐条结果行 + 钉底「确认写入 N 笔」。 */
    data class SmsPreview(val preview: SmsScanPreview) : ImportUiState
    /** 确认写入中（只写「可导入」行，单事务 + IGNORE 幂等）。 */
    data class SmsImporting(val preview: SmsScanPreview) : ImportUiState
    /** 写入完成：摘要卡同页原地追加（不切换版式）。 */
    data class SmsResult(val summary: SmsImportSummary, val preview: SmsScanPreview) : ImportUiState
    /** 未授予 READ_SMS：只展示状态 + 引导，不发起权限请求（TODO(F-14 权限申请)）。 */
    data object SmsPermissionNeeded : ImportUiState
    /** 失败：异常已映射为固定中文文案；failedStep 用于 stepper 标 ✗。 */
    data class SmsError(val message: String, val failedStep: SmsImportStep?) : ImportUiState
}