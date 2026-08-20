package com.minibook.ui.lock

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.data.local.datastore.SettingsDataStore
import com.minibook.data.local.security.AppLockController
import com.minibook.data.local.security.BiometricHelper
import com.minibook.domain.usecase.ledger.BootstrapUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 解锁门 ViewModel（TASK-022，ARCHITECTURE §7.4/§9、DEC-002/DEC-003）。
 *
 * 消费 [AppLockController] 锁状态机与 [SettingsDataStore.appLockEnabled]，驱动解锁门两条
 * 路径：
 * - 锁「关闭」（appLockEnabled=false）：plain key 静默开库直达（§7.4 分诊 A），由 UI 层
 *   [LaunchedEffect] 自动触发，无用户交互。
 * - 锁「开启」（appLockEnabled=true）：需用户点按生物识别/系统凭据解锁
 *   （[AppLockController.unlock] `useAuth=true` → [BiometricHelper] 的 BiometricPrompt；
 *   CryptoObject 由 [AppLockController] 内部构造，UI 只触发，不在 UI 层直调 BiometricPrompt）。
 *
 * DEC-003：设备无系统锁屏时**不降级明文**——[isDeviceSecure] 返回 false 时 [unlock] 直接
 * 返回、不解锁，由 UI 层展示引导卡跳系统安全设置；用户从设置返回后 UI 经生命周期
 * onResume 回检设备锁屏。
 *
 * 冷启动：本类 `init` 不自动解锁（useAuth=true 路径必须用户点按触发 BiometricPrompt，
 * 不能自动弹）。锁关闭路径的自动静默开库由 UI 层 [LaunchedEffect] 驱动。
 *
 * 锁态下本页无任何数据绑定（DB 未开，无可达数据，验收点）。
 */
@HiltViewModel
class LockViewModel @Inject constructor(
    private val appLockController: AppLockController,
    settingsDataStore: SettingsDataStore,
    private val biometricHelper: BiometricHelper,
    private val bootstrapUseCase: BootstrapUseCase,
) : ViewModel() {

    /** 锁状态透传：true=已锁定（DB 关闭）；false=已解锁（DB 打开）。 */
    val isLocked: StateFlow<Boolean> = appLockController.isLocked

    /** 应用锁开关（无值默认关；锁关闭走 plain key 静默开库）。 */
    val appLockEnabled: StateFlow<Boolean> = settingsDataStore.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _unlockError = MutableStateFlow<String?>(null)

    /** 最近一次解锁失败/取消文案；null=无错误（成功即由导航图切离本页）。 */
    val unlockError: StateFlow<String?> = _unlockError.asStateFlow()

    /** 设备是否已设系统锁屏（DEC-003 分诊；UI 在 onResume 回检）。 */
    fun isDeviceSecure(): Boolean = biometricHelper.isDeviceSecure()

    /** 清除解锁错误（UI 在 appLockEnabled 翻真时调用，避免锁状态加载窗口的瞬时误报残留）。 */
    fun clearUnlockError() {
        _unlockError.value = null
    }

    /**
     * 按当前 [appLockEnabled] 走对应解锁路径（幂等：已在解锁态直接返回）。
     *
     * @param activity 当前 [FragmentActivity]（BiometricPrompt 宿主；仅锁开启路径需要）。
     */
    fun unlock(activity: FragmentActivity) {
        if (!appLockController.isLocked.value) return
        _unlockError.value = null

        // 锁关闭路径：plain key 静默开库直达（§7.4 分诊 A，无用户交互）。
        // 防瞬态：appLockEnabled 初值为 false 但 blob 仍为 auth 包裹时，静默解包会抛
        // GeneralSecurityException（GCM 校验失败），此处兜底不崩溃、失败停留锁屏。
        if (!appLockEnabled.value) {
            runCatching {
                appLockController.unlock(useAuth = false, onResult = { success ->
                    if (success) {
                        // 解锁成功 → DB 已打开（onResult(true) 时序保证）→ 触发首启引导。
                        bootstrapAfterUnlock()
                    } else {
                        _unlockError.value = "自动解锁失败，请重试"
                    }
                })
            }.onFailure {
                _unlockError.value = "自动解锁失败，请重试"
            }
            return
        }

        // 锁开启路径：先满足 DEC-003（设备已设系统锁屏），否则不降级明文、不解锁。
        if (!isDeviceSecure()) {
            return
        }

        runCatching {
            appLockController.unlock(
                useAuth = true,
                activity = activity,
                onResult = { success ->
                    if (success) {
                        // 解锁成功 → DB 已打开（onResult(true) 时序保证）→ 触发首启引导。
                        bootstrapAfterUnlock()
                    } else {
                        _unlockError.value = "认证失败或已取消，请重试"
                    }
                },
            )
        }.onFailure {
            _unlockError.value = "解锁失败，请重试"
        }
    }

    /**
     * 解锁成功（[AppLockController.unlock] `onResult(true)`，DB 已打开）后触发首启引导
     * （TASK-011 [BootstrapUseCase]）：插入默认账本 + 克隆 18 内置分类 + 写当前账本 id。
     *
     * **时序保证**：`onResult(true)` 仅在 [AppLockController.unlock] 开库成功（
     * [com.minibook.data.local.security.DatabaseGate] 已打开）之后回调，故本处协程内调用
     * 写库安全。BootstrapUseCase 幂等（默认账本已存在则直接返回），重复调用/多路径触发无副作用。
     *
     * **防取消**：解锁成功触发 `isLocked` 翻转 → NavHost `key(isLocked)` 重建、Lock 路由销毁
     * （本 ViewModel 随之清理、viewModelScope 取消）。首启引导事务须在 DB 打开后完整跑完，
     * 否则首页仍报「账本不存在」（TASK-011 验收点），故以 [NonCancellable] 隔离取消；
     * [runCatching] 兜底失败不崩溃（幂等，下次解锁/启动重试）。
     */
    private fun bootstrapAfterUnlock() {
        viewModelScope.launch {
            withContext(NonCancellable) {
                runCatching { bootstrapUseCase() }
            }
        }
    }
}
