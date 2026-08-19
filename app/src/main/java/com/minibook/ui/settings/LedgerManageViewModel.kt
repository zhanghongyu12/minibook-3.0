package com.minibook.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.data.local.datastore.LedgerDataStore
import com.minibook.domain.model.Ledger
import com.minibook.domain.repository.LedgerRepository
import com.minibook.domain.usecase.ledger.CreateLedgerUseCase
import com.minibook.domain.usecase.ledger.DeleteLedgerUseCase
import com.minibook.domain.usecase.ledger.ObserveLedgersUseCase
import com.minibook.domain.usecase.ledger.SwitchLedgerUseCase
import com.minibook.util.TimeFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 账本管理页 ViewModel（TASK-029，UI §2 P-11）。
 *
 * 职责：
 * - 订阅 [ObserveLedgersUseCase.observe] 全量账本流（按 `position` 升序，含 `isDefault` 标记），
 *   与 [LedgerDataStore.currentLedgerId] 均以 `WhileSubscribed(5_000)` 保活（冷流语义同
 *   TASK-024/025）；当前账本 id 用于行高亮与「删除当前账本前先切换」判定。
 * - 切换：[switch] 纯状态写（[SwitchLedgerUseCase] 写 DataStore，全 app `flatMapLatest` Flow
 *   自动重订阅，ARCHITECTURE §8.3）。
 * - 新建：[create] 单调用 [CreateLedgerUseCase]——同一 DB 事务内 insert + 克隆 18 内置分类 +
 *   `setCurrentLedgerId` 原子完成（新建后自动切换，ARCHITECTURE §8.4）。
 * - 删除：[delete] 默认账本拒绝（snackbar 兜底，UI 已隐藏入口）；删除当前非默认账本前先切到
 *   默认账本，避免「无当前账本」悬空态（[DeleteLedgerUseCase] 明确不承载切换，见其 KDoc）；
 *   随后单调用 [DeleteLedgerUseCase]（事务内 4 表清数据 + 删 Ledger 行，且保证至少保留一个）。
 * - 编辑：[update] 直接经 [LedgerRepository.update]（TASK-029 决定：无 UpdateLedgerUseCase，
 *   VM 注入 Repository 直更）更新 name/icon/color 与 updatedAt。
 *
 * 统一轻提示通道 [snackbar]：切换/新建/删除/编辑结果均由 VM 写入，UI collect 后经
 * `MiniBookSnackbarHost` 展示并 [clearSnackbar]；Create/Update 成功另经 [saved] 事件通知
 * UI 关闭编辑器弹层（失败走 snackbar，弹层保持）。
 */
@HiltViewModel
class LedgerManageViewModel @Inject constructor(
    observeLedgers: ObserveLedgersUseCase,
    private val createLedger: CreateLedgerUseCase,
    private val deleteLedger: DeleteLedgerUseCase,
    private val switchLedger: SwitchLedgerUseCase,
    ledgerDataStore: LedgerDataStore,
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {

    /** 全量账本流（按 `position` 升序；切换账本后经 Repository 冷流自动重订阅）。 */
    val ledgers: StateFlow<List<Ledger>> = observeLedgers.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前账本 id（DataStore，进程级唯一状态；用于高亮与删除前切换判定）。 */
    val currentLedgerId: StateFlow<String> = ledgerDataStore.currentLedgerId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _snackbar = MutableStateFlow<String?>(null)

    /** 统一轻提示通道：非 null 即待展示，UI 展示后调 [clearSnackbar]。 */
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Create/Update 成功事件：UI 据此关闭编辑器弹层。 */
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    /** 清除待展示轻提示。 */
    fun clearSnackbar() {
        _snackbar.value = null
    }

    /**
     * 切换当前账本（纯状态变更，ARCHITECTURE §8.3）。
     *
     * 目标账本存在性由 [SwitchLedgerUseCase] 校验（不存在抛 [LedgerNotFound]，归 snackbar）。
     *
     * @param ledgerId 目标账本 id。
     */
    fun switch(ledgerId: String) {
        viewModelScope.launch {
            try {
                switchLedger(ledgerId)
                _snackbar.value = "已切换账本"
            } catch (e: Exception) {
                _snackbar.value = e.message?.ifBlank { null } ?: "切换失败"
            }
        }
    }

    /**
     * 新建账本。
     *
     * 单调用 [CreateLedgerUseCase]：同一 DB 事务内 insert + 克隆 18 内置分类 +
     * `setCurrentLedgerId` 原子完成（新建后自动切为当前账本，TASK-012 验收）。
     * 成功 → snackbar + emit [saved]（UI 关编辑器）。
     *
     * @param name 账本名（非空，编辑器必填校验）。
     * @param icon 账本图标 emoji。
     * @param color 账本主题色（`#RRGGBB`）。
     */
    fun create(name: String, icon: String, color: String) {
        viewModelScope.launch {
            try {
                createLedger(name, icon, color)
                _snackbar.value = "已创建账本「$name」"
                _saved.emit(Unit)
            } catch (e: Exception) {
                _snackbar.value = e.message?.ifBlank { null } ?: "创建失败"
            }
        }
    }

    /**
     * 删除账本。
     *
     * 默认账本在此兜底拒绝（UI 已隐藏入口）；删除前若目标为当前账本，先切到默认账本
     * （避免「无当前账本」悬空态，[DeleteLedgerUseCase] 不承载切换）。随后单调用
     * [DeleteLedgerUseCase]——事务内清 4 表数据 + 删 Ledger 行，且业务守卫保证至少保留一个、
     * 默认账本不可删（TASK-012 验收）。
     *
     * @param ledger 待删除账本（须非默认）。
     */
    fun delete(ledger: Ledger) {
        if (ledger.isDefault) {
            _snackbar.value = "默认账本不可删除"
            return
        }
        viewModelScope.launch {
            try {
                if (ledger.id == currentLedgerId.value) {
                    ledgerRepository.getDefault()?.let { switchLedger(it.id) }
                }
                deleteLedger(ledger.id)
                _snackbar.value = "已删除账本「${ledger.name}」"
            } catch (e: Exception) {
                _snackbar.value = e.message?.ifBlank { null } ?: "删除失败"
            }
        }
    }

    /**
     * 编辑账本（name/icon/color）。
     *
     * TASK-029 决定：无 UpdateLedgerUseCase，VM 注入 [LedgerRepository] 直接 [LedgerRepository.update]
     * 更新 name/icon/color 与 `updatedAt`（同 Create 口径，[TimeFormatter.nowIso]）。
     * 成功 → snackbar + emit [saved]（UI 关编辑器）。
     *
     * @param ledger 编辑目标账本（含原 name/icon/color/position 等，copy 覆盖三个可编辑字段）。
     * @param name 新账本名。
     * @param icon 新图标 emoji。
     * @param color 新主题色（`#RRGGBB`）。
     */
    fun update(ledger: Ledger, name: String, icon: String, color: String) {
        viewModelScope.launch {
            try {
                ledgerRepository.update(
                    ledger.copy(
                        name = name,
                        icon = icon,
                        color = color,
                        updatedAt = TimeFormatter.nowIso(),
                    ),
                )
                _snackbar.value = "已保存账本「$name」"
                _saved.emit(Unit)
            } catch (e: Exception) {
                _snackbar.value = e.message?.ifBlank { null } ?: "保存失败"
            }
        }
    }
}
