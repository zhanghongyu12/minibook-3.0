package com.minibook.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.data.local.datastore.SettingsDataStore
import com.minibook.data.local.sms.SmsNotificationHelper
import com.minibook.data.local.sms.SmsSettingsStore
import com.minibook.data.local.sms.SmsWhitelistStore
import com.minibook.domain.model.SmsNotifyMode
import com.minibook.domain.model.SmsSettings
import com.minibook.domain.model.SmsWhitelistEntry
import com.minibook.domain.usecase.sms.SmsBillImportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页「短信账单」区块 ViewModel（F-14，UI §11.2）。
 *
 * 独立于 [SettingsViewModel]（不改既有构造签名，避免影响既有接线）：本 VM 只承载短信开关组、
 * 白名单与短信库计数。开关全部默认关闭（[SmsSettings] 默认值），总开关关闭时不申请任何短信权限
 * （权限请求本身不在本期 UI 范围内，仅做状态展示 + 引导）。
 *
 * 「自动记账」的可用性由 [SmsSettings.autoImportUsable] 口径决定：应用锁开启时后台无法解密数据库，
 * 区块置灰并注明原因（设计 §3.7 R7）。
 *
 * 隐私（DEC-026）：白名单只含发送方号码/开关/计数/错误时间，本 VM 不接触任何短信内容。
 */
@HiltViewModel
class SmsSettingsViewModel @Inject constructor(
    private val settingsStore: SmsSettingsStore,
    private val settingsDataStore: SettingsDataStore,
    private val smsBillImport: SmsBillImportUseCase,
    private val notificationHelper: SmsNotificationHelper,
    private val whitelistStore: SmsWhitelistStore,
) : ViewModel() {

    /** 短信开关组（默认全关；设置页与接收器共用同一 DataStore）。 */
    val settings: StateFlow<SmsSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SmsSettings())

    /** 应用锁开关（「自动记账」置灰门控，设计 R7）。 */
    val appLockEnabled: StateFlow<Boolean> = settingsDataStore.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _whitelist = MutableStateFlow<List<SmsWhitelistEntry>>(emptyList())

    /** 白名单条目（逐项开关；进入区块时 [refresh] 拉取）。 */
    val whitelist: StateFlow<List<SmsWhitelistEntry>> = _whitelist.asStateFlow()

    private val _providerCount = MutableStateFlow<Int?>(null)

    /** 本机短信库总数（`content://sms`，外部 oracle 对齐用）；未授权/不可读为 null。 */
    val providerCount: StateFlow<Int?> = _providerCount.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)

    /** 统一轻提示通道：非 null 即待展示，UI 展示后调 [clearSnackbar]。 */
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    /** 拉取白名单与本机短信计数（[readSmsGranted] 为 false 时跳过计数，不发权限请求）。 */
    fun refresh(readSmsGranted: Boolean) {
        viewModelScope.launch {
            _whitelist.value = runCatching { smsBillImport.whitelist() }.getOrDefault(emptyList())
            _providerCount.value = if (readSmsGranted) {
                runCatching { smsBillImport.countInProvider() }.getOrNull()
            } else {
                null
            }
        }
    }

    /** 总开关：开启时顺带建通知通道（幂等）；关闭后不申请任何短信权限。 */
    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setEnabled(enabled)
            if (enabled) notificationHelper.ensureChannels()
        }
    }

    /** 实时识别新短信（L1）。 */
    fun setRealtimeEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setRealtimeEnabled(enabled) }
    }

    /** 自动记账（L2，仅白名单发送方；应用锁开启时置灰不可改）。 */
    fun setAutoImportEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoImportEnabled(enabled) }
    }

    /** 打开 App 时自动补扫（L3，默认开）。 */
    fun setAutoScanEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoScanEnabled(enabled) }
    }

    /** 通知方式（静默聚合 / 每日汇总 / 逐条提醒）。 */
    fun setNotifyMode(mode: SmsNotifyMode) {
        viewModelScope.launch { settingsStore.setNotifyMode(mode) }
    }

    /** 白名单逐项开关（发送方级）。 */
    fun setWhitelistAutoEnabled(sender: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { smsBillImport.setAutoEnabled(sender, enabled) }
            _whitelist.value = runCatching { smsBillImport.whitelist() }.getOrDefault(_whitelist.value)
        }
    }

    /**
     * 清空短信白名单（隐私清理，UI 层先经 danger 强确认）。
     *
     * 备注：`SmsBillImportUseCase` 未暴露「清空」入口，故此处直接调 data 层
     * [SmsWhitelistStore.clear]（**只调用、未修改 data 层**）；若后续 use case 补上 clear，
     * 应改为经 use case 调用。
     */
    fun clearWhitelist() {
        viewModelScope.launch {
            try {
                whitelistStore.clear()
                _whitelist.value = runCatching { smsBillImport.whitelist() }.getOrDefault(emptyList())
                _snackbar.value = "已清空短信白名单"
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _snackbar.value = "清空失败，请重试"
            }
        }
    }

    /** 消费（清空）轻提示。 */
    fun clearSnackbar() {
        _snackbar.value = null
    }
}
