package com.minibook.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.usecase.sync.SyncMode
import com.minibook.ui.components.ConfirmDialog
import com.minibook.ui.components.FullScreenBusyOverlay
import com.minibook.ui.components.MiniBookSnackbarHost
import com.minibook.ui.components.SettingRow
import com.minibook.ui.components.SettingSection
import com.minibook.ui.components.SettingsSubPageTopBar
import com.minibook.ui.components.TrailingChevron
import com.minibook.ui.components.TrailingValue

/**
 * 设置 · 云同步（F-15 / TASK-067，`docs/14_cloud_sync.md §7`）。
 *
 * 页面只做三件事：**登录** → **选模式看预览** → **确认执行看结果**。
 * 所有判定与文案都在 [SyncPresenter]（纯函数、有单测），本文件不写业务规则，
 * 以免出现「界面说的」和「代码做的」不一致。
 *
 * 「本地不丢」在界面上的体现：每次执行前弹**预览确认**（含删几条 / 冲突几条），
 * 结果卡明说「游标未推进、可重试」，冲突有独立入口可查两份副本。
 */
@Composable
fun SettingsCloudScreen(
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
    viewModel: SettingsCloudViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 统一轻提示消费（与数据页同口径）：VM message → Snackbar → clearMessage。
    LaunchedEffect(ui.message) {
        ui.message?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { SettingsSubPageTopBar(title = "云同步", onBack = onBack) },
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
                // 账号相关（登录 / 注册 / 改资料 / 退出 / 注销）已挪到「账号」页（2026-10-12 用户要求）：
                // 本页只管「数据怎么同步」，未登录时给一条直达「账号」的路。
                if (ui.loggedIn) {
                    AutoSyncSection(ui = ui, viewModel = viewModel)
                    SyncSection(ui = ui, viewModel = viewModel)
                } else {
                    LoginPromptRow(onOpenAccount = onOpenAccount)
                }
                StatusSection(ui = ui, viewModel = viewModel)

                Text(
                    text = SyncPresenter.PRIVACY_NOTICE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = SyncPresenter.SNAPSHOT_NOTICE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (ui.busy) {
                FullScreenBusyOverlay(message = ui.busyText)
            }
        }
    }

    // —— 同步预览确认（危险操作变体：会删数据或有冲突时按钮转 errorContainer） ——
    ui.prompt?.let { prompt ->
        ConfirmDialog(
            title = prompt.title,
            message = prompt.message,
            confirmLabel = "执行",
            danger = prompt.danger,
            onConfirm = { viewModel.confirmPreview() },
            onDismiss = { viewModel.cancelPreview() },
        )
    }

    // —— 结果卡（如实展示四个计数；失败时明说游标未推进） ——
    ui.result?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissResult() },
            title = { Text(if (result.ok) "同步结果" else "同步失败") },
            text = {
                Column {
                    Text(SyncPresenter.resultText(result))
                    SyncPresenter.snapshotLabel(result.snapshotPath)?.let { name ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "同步前备份：$name",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissResult() }) { Text("知道了") }
            },
        )
    }

    // —— 冲突留档（两条副本都给全，用户可手工恢复） ——
    if (ui.showConflicts) {
        AlertDialog(
            onDismissRequest = { viewModel.closeConflicts() },
            title = { Text("冲突留档（${ui.conflicts.size} 条）") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "两侧都改过同一条时，保留较新的一方，输的一方存在这里，不会丢。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ui.conflicts.forEach { conflict ->
                        Spacer(Modifier.height(12.dp))
                        Text(conflict.head, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "检测于 ${conflict.detectedAt}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "本地：${conflict.localJson}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = "云端：${conflict.remoteJson}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.closeConflicts() }) { Text("关闭") }
            },
        )
    }
}

/** 未登录提示行（2026-10-12）：同步入口藏在「数据」里，这里必须给一条直达「账号」的路。 */
@Composable
private fun LoginPromptRow(onOpenAccount: () -> Unit) {
    SettingSection(
        title = "云端账号",
        content = {
            SettingRow(
                title = "未登录",
                subtitle = "先在「账号」里登录，才能自动或手动同步",
                trailing = { TrailingChevron() },
                onClick = onOpenAccount,
            )
        },
    )
}

/**
 * 自动同步区（DEC-038）：**打开 / 回到 App 时静默双向同步一次**（间隔 ≥5 分钟）。
 *
 * 明确写清「失败不打扰」与「会删本地/有冲突的计划不自动跑」——这两条是用户敢把开关留在
 * 打开状态的前提（静默删账目比不自动同步糟得多）。
 */
@Composable
private fun AutoSyncSection(
    ui: SettingsCloudViewModel.UiState,
    viewModel: SettingsCloudViewModel,
) {
    SettingSection(
        title = "自动同步",
        content = {
            SettingRow(
                title = "打开 App 时自动同步",
                subtitle = when {
                    !ui.loggedIn -> "登录后生效：打开 / 回到 App 时静默双向同步一次"
                    ui.autoSync -> "已开启：打开 / 回到 App 时静默同步（≥5 分钟一次，失败不打扰）"
                    else -> "已关闭：只走下面的手动同步"
                },
                trailing = {
                    Switch(
                        checked = ui.autoSync,
                        onCheckedChange = { viewModel.setAutoSync(it) },
                        enabled = ui.loggedIn && !ui.busy,
                    )
                },
            )
        },
    )
}

/** 同步区：三个模式各一行；未登录/忙碌时不可点（不给出会失败的入口）。 */
@Composable
private fun SyncSection(
    ui: SettingsCloudViewModel.UiState,
    viewModel: SettingsCloudViewModel,
) {
    SettingSection(
        title = "手动同步",
        content = {
            SyncMode.entries.forEach { mode ->
                SettingRow(
                    title = mode.label,
                    subtitle = if (ui.syncEnabled) {
                        SyncPresenter.modeSubtitle(mode)
                    } else {
                        "登录后可同步"
                    },
                    trailing = { TrailingChevron() },
                    onClick = if (ui.syncEnabled) {
                        { viewModel.preview(mode) }
                    } else {
                        null
                    },
                )
            }
        },
    )
}

/** 状态区：账号/上次同步/冲突数；冲突数 >0 给「查看」入口。 */
@Composable
private fun StatusSection(
    ui: SettingsCloudViewModel.UiState,
    viewModel: SettingsCloudViewModel,
) {
    SettingSection(
        title = "同步状态",
        content = {
            SettingRow(
                title = "当前状态",
                subtitle = ui.statusText,
            )
            SettingRow(
                title = "待处理冲突",
                subtitle = if (ui.conflictCount == 0) "无" else "有冲突副本留档，可查看后手工处理",
                trailing = {
                    if (ui.conflictCount > 0) {
                        TextButton(onClick = { viewModel.openConflicts() }) { Text("查看") }
                    } else {
                        TrailingValue("0 条")
                    }
                },
            )
        },
    )
}
