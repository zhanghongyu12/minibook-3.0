package com.minibook.ui.mail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.ui.components.ConfirmDialog
import com.minibook.ui.components.MiniBookSnackbarHost

/**
 * 邮箱凭据设置页（F-13 多账号增强）：根图全屏页（路由 `mail_account` 不变），TopAppBar
 * 「邮箱凭据」＋ 返回 ←，**同路由内两态**切换：
 * - **列表态**：账号列表（每行邮箱地址 + 副标题「已配置授权码」，点击进编辑态；行尾
 *   「删除」按钮经 [ConfirmDialog] 二次确认）+ 底部「添加邮箱」+ 配置指引（IMAP/SMTP
 *   必须开启的警示文案保留）；空列表显示引导文案。
 * - **编辑态**：邮箱地址输入 + 授权码掩码输入（显示/隐藏切换）+「保存」；编辑已有账号
 *   时额外有「删除此邮箱」（[ConfirmDialog] 二次确认）；顶部返回箭头回列表态。
 *
 * 编辑已有账号时授权码输入框不回显旧授权码（明文不读出），重新输入后保存。
 *
 * @param onBack 返回回调（NavHost 接线传入 `rootController.popBackStack()`；编辑态下由
 *   页内拦截为「回列表态」）。
 * @param viewModel 页面 ViewModel；默认经 [hiltViewModel] 解析。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailSettingsScreen(
    onBack: () -> Unit,
    viewModel: MailSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // 待二次确认删除的账号 id（列表行「删除」与编辑态「删除此邮箱」共用）。
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    // 轻提示：一次性消费（VM savedTip → Snackbar → consumeSavedTip）。
    LaunchedEffect(uiState.savedTip) {
        uiState.savedTip?.let { tip ->
            snackbarHostState.showSnackbar(tip)
            viewModel.consumeSavedTip()
        }
    }

    val editing = uiState.editing
    val pendingDeleteAccount = uiState.accounts.firstOrNull { it.id == pendingDeleteId }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { MiniBookSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("邮箱凭据") },
                navigationIcon = {
                    IconButton(onClick = { if (editing != null) viewModel.onCancelEdit() else onBack() }) {
                        Text("←", fontSize = 20.sp)
                    }
                },
            )
        },
    ) { innerPadding ->
        if (editing == null) {
            MailAccountListContent(
                accounts = uiState.accounts,
                onEdit = viewModel::onEdit,
                onDelete = { pendingDeleteId = it },
                onAdd = viewModel::onAdd,
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            MailAccountEditContent(
                editing = editing,
                onEmailChange = viewModel::onEmailChange,
                onAuthCodeChange = viewModel::onAuthCodeChange,
                onToggleAuthCodeVisible = viewModel::toggleAuthCodeVisible,
                onSave = viewModel::onSave,
                onDelete = if (editing.id != null) {
                    { pendingDeleteId = editing.id }
                } else {
                    null
                },
                errorMessage = uiState.errorMessage,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    // —— 删除强确认（danger：抹除该账号授权码密文，二次确认后才执行） ——
    if (pendingDeleteAccount != null) {
        ConfirmDialog(
            title = "删除邮箱",
            message = "将删除「${pendingDeleteAccount.email}」及其授权码，该邮箱将不再自动收取账单。此操作不可撤销。",
            confirmLabel = "确认删除",
            danger = true,
            onConfirm = {
                viewModel.onDelete(pendingDeleteAccount.id)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

/**
 * 列表态：配置指引 + 账号列表（可滚动）+「添加邮箱」；空列表显示引导文案。
 */
@Composable
private fun MailAccountListContent(
    accounts: List<com.minibook.domain.model.MailAccount>,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // —— 配置指引（强调 IMAP/SMTP 而非 POP3，警示文案保留） ——
        Text(
            text = "用于自动收取 QQ 邮箱中的账单邮件，可配置多个邮箱。请先在 QQ 邮箱网页版" +
                "「设置 → 账号 → 开启 IMAP/SMTP 服务」中生成 16 位授权码，" +
                "再添加邮箱完成配置。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "注意：必须开启 IMAP/SMTP 服务，而不是仅开启 POP3，否则将无法收取邮件。",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
        )

        if (accounts.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "尚未配置邮箱，点击下方「添加邮箱」开始配置。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            accounts.forEach { account ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(account.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(account.email, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "已配置授权码",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onDelete(account.id) }) { Text("删除") }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("添加邮箱")
        }
    }
}

/**
 * 编辑态：邮箱地址输入 + 授权码掩码输入（显示/隐藏）+ 行内错误提示 +「保存」；
 * 编辑已有账号时额外提供「删除此邮箱」（[onDelete] 非空）。
 */
@Composable
private fun MailAccountEditContent(
    editing: MailEditState,
    onEmailChange: (String) -> Unit,
    onAuthCodeChange: (String) -> Unit,
    onToggleAuthCodeVisible: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (editing.id != null) {
            Text(
                text = "正在编辑已有邮箱，保存时将更新邮箱地址与授权码；授权码需重新输入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "请填入邮箱地址与 16 位 IMAP 授权码（非登录密码）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = editing.email,
            onValueChange = onEmailChange,
            label = { Text("邮箱地址") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = editing.authCode,
            onValueChange = onAuthCodeChange,
            label = { Text("IMAP 授权码") },
            singleLine = true,
            visualTransformation = if (editing.authCodeVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = onToggleAuthCodeVisible) {
                    Text(if (editing.authCodeVisible) "隐藏" else "显示")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // —— 行内错误提示（中文，不崩溃） ——
        errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("保存")
        }
        if (onDelete != null) {
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("删除此邮箱")
            }
        }
    }
}
