package com.minibook.ui.mail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.data.local.mail.MailAccountStore
import com.minibook.domain.model.MailAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 邮箱凭据设置页 ViewModel（F-13 多账号增强：列表态 / 编辑态两态）。
 *
 * 列表态展示 [MailAccountStore.accounts]；`onAdd()`/`onEdit(id)` 进入编辑态，
 * `onSave()` 委托 [MailAccountStore.upsert]（校验 + Keystore 加密落盘）后回列表态，
 * `onDelete(id)` 委托 [MailAccountStore.delete]。校验失败的中文原因经
 * [MailSettingsUiState.errorMessage] 行内展示（不崩溃）；成功经
 * [MailSettingsUiState.savedTip] 轻提示。
 *
 * 安全纪律：**授权码绝不写入任何日志或异常信息**；本 VM 不持有 Log 工具，异常一律
 * 折叠为固定中文文案（[IllegalArgumentException.message] 来自 Store 的校验原因，仅含
 * 格式描述与长度数字，不含凭据内容）。编辑已有账号时授权码输入框**不回显旧授权码**
 * （明文不读出），留空保存视为重新输入。
 */
@HiltViewModel
class MailSettingsViewModel @Inject constructor(
    private val mailAccountStore: MailAccountStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MailSettingsUiState(accounts = mailAccountStore.accounts.value),
    )

    /** 页面 UI 状态（初值从 Store 的 StateFlow 同步读出，即构造时读盘结果）。 */
    val uiState: StateFlow<MailSettingsUiState> = _uiState.asStateFlow()

    /** 「添加邮箱」：进入新增编辑态（id=null）。 */
    fun onAdd() {
        _uiState.update {
            it.copy(editing = MailEditState(id = null), errorMessage = null)
        }
    }

    /** 点击列表某行：进入该账号编辑态（邮箱地址预填，授权码不回显）。 */
    fun onEdit(id: String) {
        val account = _uiState.value.accounts.firstOrNull { it.id == id } ?: return
        _uiState.update {
            it.copy(editing = MailEditState(id = id, email = account.email), errorMessage = null)
        }
    }

    /** 顶部返回箭头（编辑态）：放弃修改回列表态，清空输入与错误提示。 */
    fun onCancelEdit() {
        _uiState.update { it.copy(editing = null, errorMessage = null) }
    }

    /** 编辑态「邮箱地址」输入变化（清掉旧错误提示，允许即时改错重试）。 */
    fun onEmailChange(value: String) {
        updateEditing { it.copy(email = value) }
    }

    /** 编辑态「IMAP 授权码」输入变化（清掉旧错误提示）。 */
    fun onAuthCodeChange(value: String) {
        updateEditing { it.copy(authCode = value) }
    }

    /** 编辑态授权码「显示/隐藏」切换。 */
    fun toggleAuthCodeVisible() {
        updateEditing { it.copy(authCodeVisible = !it.authCodeVisible) }
    }

    /**
     * 保存：委托 [MailAccountStore.upsert]（内部完成格式校验 + Keystore 加密落盘）。
     * 校验失败 → 行内中文错误（留在编辑态）；成功 → 回列表态 + 轻提示。
     */
    fun onSave() {
        val editing = _uiState.value.editing ?: return
        viewModelScope.launch {
            try {
                mailAccountStore.upsert(editing.id, editing.email, editing.authCode)
                _uiState.update {
                    it.copy(
                        accounts = mailAccountStore.accounts.value,
                        editing = null,
                        errorMessage = null,
                        savedTip = "已保存",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _uiState.update { it.copy(errorMessage = e.message ?: "输入不合法，请检查后重试") }
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = "保存失败，请重试") }
            }
        }
    }

    /**
     * 删除账号（UI 层先经确认对话框）：委托 [MailAccountStore.delete]；无论从列表态还是
     * 编辑态发起，删除后都回列表态。失败不抛出，仅轻提示。
     */
    fun onDelete(id: String) {
        viewModelScope.launch {
            try {
                mailAccountStore.delete(id)
                _uiState.update {
                    it.copy(
                        accounts = mailAccountStore.accounts.value,
                        editing = null,
                        errorMessage = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 删除失败保持现状（下次进页面重新读盘自愈）；不写日志。
                _uiState.update { it.copy(savedTip = "删除失败，请重试") }
            }
        }
    }

    /** 消费（清空）行内错误提示。 */
    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /** 消费（清空）轻提示。 */
    fun consumeSavedTip() {
        _uiState.update { it.copy(savedTip = null) }
    }

    /** 编辑态内部更新（清掉行内错误提示；非编辑态调用静默忽略）。 */
    private inline fun updateEditing(transform: (MailEditState) -> MailEditState) {
        _uiState.update { state ->
            state.editing?.let { state.copy(editing = transform(it), errorMessage = null) } ?: state
        }
    }
}

/** 编辑态输入与可见性（id=null 表示新增；编辑已有账号时授权码不回显，重新输入）。 */
data class MailEditState(
    val id: String?,
    val email: String = "",
    val authCode: String = "",
    val authCodeVisible: Boolean = false,
)

/**
 * 邮箱凭据设置页 UI 状态（列表态 editing=null / 编辑态 editing 非空，同路由内两态切换）。
 *
 * @property accounts 已配置账号列表（列表态渲染）。
 * @property editing 编辑态输入；null=列表态。
 * @property errorMessage 行内中文错误提示（一次性，经 consumeError 清空）。
 * @property savedTip 轻提示（保存成功/删除失败，一次性，经 consumeSavedTip 清空）。
 */
data class MailSettingsUiState(
    val accounts: List<MailAccount> = emptyList(),
    val editing: MailEditState? = null,
    val errorMessage: String? = null,
    val savedTip: String? = null,
)
