package com.minibook.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 登录页（独立整页，2026-10-12 第二轮）。成功后 [onDone]（回上一页 = 账号页）。
 *
 * 调试版额外显示「服务器地址」输入框：正式版界面**不出现**地址，也不要用户填（DEC-039）。
 *
 * 第三轮：口令框的明文切换收进输入框 trailing 图标；声明 autofill 类型；忙碌时禁用输入；
 * 键盘「完成」直接提交。
 *
 * 第四轮（TASK-076）：底部「登录即表示你已阅读并同意」+ 可点开的《用户协议》《隐私政策》；
 * 登录页**不再放勾选框**（同意已在注册时取得，重复索要属于频繁征求同意）。
 *
 * @param onOpenPrivacyPolicy 打开隐私政策页。
 * @param onOpenTerms 打开用户协议页。
 */
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onOpenRegister: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTerms: () -> Unit,
    onDone: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var reveal by remember { mutableStateOf(false) }

    LaunchedEffect(ui.doneTick) {
        if (ui.doneTick > 0) onDone()
    }

    AuthPage(
        onBack = onBack,
        headline = "欢迎回来",
        subtitle = "登录后可自动或手动同步账目；本地数据始终保留在本机。",
    ) {
        AuthField(
            value = ui.username,
            onValueChange = viewModel::onUsernameChange,
            label = "用户名",
            supporting = "登录用的账号名（不是昵称）",
            imeAction = ImeAction.Next,
            contentType = ContentType.Username,
            enabled = !ui.busy,
        )
        Spacer(Modifier.height(12.dp))
        AuthField(
            value = ui.password,
            onValueChange = viewModel::onPasswordChange,
            label = "口令",
            password = true,
            reveal = reveal,
            onToggleReveal = { reveal = !reveal },
            imeAction = ImeAction.Done,
            onImeAction = viewModel::login,
            contentType = ContentType.Password,
            enabled = !ui.busy,
        )
        if (viewModel.showAddressField) {
            Spacer(Modifier.height(12.dp))
            AuthField(
                value = ui.baseUrl,
                onValueChange = viewModel::onBaseUrlChange,
                label = "服务器地址（仅调试版可见）",
                supporting = SyncPresenter.URL_HINT,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
                onImeAction = viewModel::login,
                enabled = !ui.busy,
            )
        }
        Spacer(Modifier.height(20.dp))
        AuthPrimaryButton(text = "登录", busy = ui.busy, onClick = viewModel::login)
        AuthErrorMessage(ui.error)
        AuthLegalNotice(
            agreed = null,
            onAgreedChange = null,
            onOpenTerms = onOpenTerms,
            onOpenPrivacy = onOpenPrivacyPolicy,
        )
        AuthSwitchRow(question = "还没有账号？", action = "去注册", onClick = onOpenRegister)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "登录只建立云端会话，不会上传或删除本地任何账目。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
