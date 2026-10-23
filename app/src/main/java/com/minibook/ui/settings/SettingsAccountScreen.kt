package com.minibook.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.ui.components.FullScreenBusyOverlay
import com.minibook.ui.components.MiniBookSnackbarHost
import com.minibook.ui.components.SettingRow
import com.minibook.ui.components.SettingSection
import com.minibook.ui.components.SettingsSubPageTopBar
import com.minibook.ui.components.TrailingChevron

/**
 * 设置 · 账号二级页（2026-10-12，用户要求；标题与入口同名「账号」）。
 *
 * 分工（第二轮改造后）：
 * - 本页 = **账号自己**：用户信息（昵称 / 用户名 / 邮箱 / 个性签名 / 地区 / 注册时间 / 上次登录）、
 *   编辑资料、改口令、退出登录、注销账号（2026-10-13 起**不展示账号 ID**）；
 * - 登录与注册各自是**独立整页**（`LoginScreen` / `RegisterScreen`），本页只放入口按钮；
 * - 云同步入口**只在「数据」页**（用户要求：两处重复只留一处）。
 *
 * 服务地址**不在界面出现**（正式版）：地址来自 `BuildConfig.SYNC_BASE_URL`（按构建类型内置），
 * 只有调试版的登录页才多一个输入框供开发临时改指向。
 *
 * @param onBack 返回（NavHost 传入 `popBackStack()`）。
 * @param onOpenLogin / onOpenRegister 进入独立的登录 / 注册页。
 * @param onOpenProfileEdit 进入「编辑资料」页。
 */
@Composable
fun SettingsAccountScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenRegister: () -> Unit,
    onOpenProfileEdit: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 每次进入本页都刷一次：登录 / 注册 / 编辑资料在别的页面完成后，回来要立刻看到新资料。
    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(ui.message) {
        ui.message?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { SettingsSubPageTopBar(title = "账号", onBack = onBack) },
        snackbarHost = { MiniBookSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (ui.loggedIn) {
                    ProfileSection(ui = ui, viewModel = viewModel, onOpenProfileEdit = onOpenProfileEdit)
                } else {
                    GuestLoginButtons(onOpenLogin = onOpenLogin, onOpenRegister = onOpenRegister)
                }

                Text(
                    text = SyncPresenter.PRIVACY_NOTICE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (ui.busy) {
                FullScreenBusyOverlay(message = ui.busyText)
            }
        }
    }

    Dialogs(ui = ui, viewModel = viewModel)
}

/**
 * 未登录：**只给一个登录按钮**（用户 2026-10-12 明确否定资料头卡：「不要展示一个大框，
 * 做成一个让用户登录的按钮即可」）。注册入口退成按钮下方一行文字链，不再占一个描边按钮；
 * 登录页自身也有「还没有账号？去注册」，两条路都在。
 */
@Composable
private fun GuestLoginButtons(onOpenLogin: () -> Unit, onOpenRegister: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Button(
            onClick = onOpenLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text("登录", style = MaterialTheme.typography.titleMedium)
        }
        TextButton(
            onClick = onOpenRegister,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("还没有账号？注册新账号")
        }
    }
}

/** 已登录：用户信息（7 项，2026-10-13 起不含「账号 ID」）+ 编辑资料 / 改口令 / 退出登录 / 注销账号。 */
@Composable
private fun ProfileSection(
    ui: AccountViewModel.UiState,
    viewModel: AccountViewModel,
    onOpenProfileEdit: () -> Unit,
) {
    SettingSection(
        title = "用户信息",
        content = {
            SettingRow(title = "昵称", subtitle = ui.displayName.ifEmpty { "—" })
            SettingRow(title = "用户名", subtitle = ui.accountName.ifEmpty { "—" })
            SettingRow(title = "邮箱", subtitle = ui.email.ifEmpty { "未填写" })
            SettingRow(title = "个性签名", subtitle = ui.bio.ifEmpty { "未填写" })
            SettingRow(title = "地区", subtitle = ui.region.ifEmpty { "未填写" })
            SettingRow(title = "注册时间", subtitle = SyncPresenter.fullTime(ui.createdAt))
            SettingRow(title = "上次登录", subtitle = SyncPresenter.fullTime(ui.lastLoginAt))
        },
    )

    SettingSection(
        title = "账号操作",
        content = {
            SettingRow(
                title = "编辑资料",
                subtitle = "昵称 / 邮箱 / 个性签名 / 地区",
                trailing = { TrailingChevron() },
                onClick = onOpenProfileEdit,
            )
            SettingRow(
                title = "修改口令",
                subtitle = "改完其它设备需重新登录",
                trailing = { TrailingChevron() },
                onClick = { viewModel.openDialog(AccountViewModel.Dialog.PASSWORD) },
            )
        },
    )

    SettingSection(
        title = "会话",
        content = {
            SettingRow(
                title = "退出登录",
                subtitle = "只退出账号，本地数据不动",
                trailing = { TrailingChevron() },
                onClick = { viewModel.openDialog(AccountViewModel.Dialog.LOGOUT) },
            )
            SettingRow(
                title = "注销账号",
                subtitle = "删除云端账号与全部云端数据（本地数据保留，不可撤销）",
                trailing = { TrailingChevron() },
                onClick = { viewModel.openDialog(AccountViewModel.Dialog.DELETE) },
            )
        },
    )
}

/** 三个对话框：改口令 / 退出确认 / 注销确认（注销需口令二次确认；编辑资料已独立成页）。 */
@Composable
private fun Dialogs(ui: AccountViewModel.UiState, viewModel: AccountViewModel) {
    when (ui.dialog) {
        AccountViewModel.Dialog.PASSWORD -> AlertDialog(
            onDismissRequest = { viewModel.closeDialog() },
            title = { Text("修改口令") },
            text = {
                Column {
                    OutlinedTextField(
                        value = ui.oldPassword,
                        onValueChange = viewModel::onOldPasswordChange,
                        label = { Text("原口令") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = ui.newPassword,
                        onValueChange = viewModel::onNewPasswordChange,
                        label = { Text("新口令（至少 8 位）") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.changePassword() }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { viewModel.closeDialog() }) { Text("取消") } },
        )

        AccountViewModel.Dialog.LOGOUT -> AlertDialog(
            onDismissRequest = { viewModel.closeDialog() },
            title = { Text("退出登录？") },
            text = { Text("只是退出账号：本地账目一条不动；下次登录后仍可继续同步。") },
            confirmButton = { TextButton(onClick = { viewModel.logout() }) { Text("退出登录") } },
            dismissButton = { TextButton(onClick = { viewModel.closeDialog() }) { Text("取消") } },
        )

        AccountViewModel.Dialog.DELETE -> AlertDialog(
            onDismissRequest = { viewModel.closeDialog() },
            title = { Text("注销账号（不可撤销）") },
            text = {
                Column {
                    Text(
                        text = "将删除云端账号与全部云端数据（账本 / 分类 / 交易 / 债务 / 预算 / 规则）。" +
                            "本机数据会保留；如需留底请先「导出数据」。",
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = ui.deletePassword,
                        onValueChange = viewModel::onDeletePasswordChange,
                        label = { Text("输入口令以确认") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteAccount() }) {
                    Text("确认注销", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.closeDialog() }) { Text("取消") } },
        )

        null -> Unit
    }
}
