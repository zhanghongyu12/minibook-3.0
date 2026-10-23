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
 * 注册页（独立整页，2026-10-12 第二轮）。注册成功即自动登录，随后 [onDone] 返回账号页。
 *
 * 规则直接写在框下面（用户名 3–32 位、口令至少 8 位）——真机事故就是因为规则不可见，
 * 用户用 6 位口令注册后只看到一句没信息量的失败提示。
 *
 * 第三轮：两次口令不一致时，错误**直接挂在「确认口令」框下**（不是笼统地写在按钮下方）；
 * 明文切换收进 trailing 图标；声明 autofill 类型；忙碌时禁用输入；键盘「完成」直接提交。
 *
 * 第四轮（TASK-076）：加**默认不勾选**的协议勾选（《用户协议》+《隐私政策》都能点开读原文），
 * 未勾选时注册被拦下并直说原因——合规要求「注册时明示并取得同意」，但绝不默认勾选。
 *
 * @param onOpenPrivacyPolicy 打开隐私政策页（正文取自 `res/raw/privacy_policy.txt`）。
 * @param onOpenTerms 打开用户协议页。
 */
@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTerms: () -> Unit,
    onDone: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var revealPassword by remember { mutableStateOf(false) }
    var revealConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(ui.doneTick) {
        if (ui.doneTick > 0) onDone()
    }

    // 页面级错误里，如果正是「两次口令不一致」，就把它挂到确认口令框下，避免同一句话出现两遍。
    val confirmError = SyncPresenter.confirmError(ui.password, ui.confirm)
        ?.takeIf { it == ui.error }

    AuthPage(
        onBack = onBack,
        headline = "创建账号",
        subtitle = "注册后会直接登录；云同步只同步账目数据，本地数据始终保留在本机。",
    ) {
        AuthField(
            value = ui.username,
            onValueChange = viewModel::onUsernameChange,
            label = "用户名",
            supporting = "3–32 位字母 / 数字 / 点 / 下划线 / 连字符",
            imeAction = ImeAction.Next,
            contentType = ContentType.NewUsername,
            enabled = !ui.busy,
        )
        Spacer(Modifier.height(12.dp))
        AuthField(
            value = ui.password,
            onValueChange = viewModel::onPasswordChange,
            label = "口令",
            supporting = "至少 8 位",
            password = true,
            reveal = revealPassword,
            onToggleReveal = { revealPassword = !revealPassword },
            imeAction = ImeAction.Next,
            contentType = ContentType.NewPassword,
            enabled = !ui.busy,
        )
        Spacer(Modifier.height(12.dp))
        AuthField(
            value = ui.confirm,
            onValueChange = viewModel::onConfirmChange,
            label = "确认口令",
            supporting = "再输入一次，避免打错",
            errorText = confirmError,
            password = true,
            reveal = revealConfirm,
            onToggleReveal = { revealConfirm = !revealConfirm },
            imeAction = ImeAction.Done,
            onImeAction = viewModel::register,
            contentType = ContentType.NewPassword,
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
                onImeAction = viewModel::register,
                enabled = !ui.busy,
            )
        }
        Spacer(Modifier.height(20.dp))
        AuthPrimaryButton(text = "注册并登录", busy = ui.busy, onClick = viewModel::register)
        AuthErrorMessage(if (confirmError == null) ui.error else null)
        AuthLegalNotice(
            agreed = ui.agreed,
            onAgreedChange = viewModel::onAgreedChange,
            onOpenTerms = onOpenTerms,
            onOpenPrivacy = onOpenPrivacyPolicy,
        )
        AuthSwitchRow(question = "已有账号？", action = "去登录", onClick = onOpenLogin)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "云端只保存账目数据（账本 / 分类 / 交易 / 债务 / 预算 / 分类规则），" +
                "不含短信原文、邮箱凭据与应用锁设置。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
