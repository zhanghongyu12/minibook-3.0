package com.minibook.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.ui.components.ConfirmDialog
import com.minibook.ui.components.FullScreenBusyOverlay
import com.minibook.ui.components.MiniBookSnackbarHost
import com.minibook.ui.components.SettingRow
import com.minibook.ui.components.SettingSection
import com.minibook.ui.components.SettingsSubPageTopBar
import com.minibook.ui.components.TrailingChevron
import com.minibook.ui.components.TrailingValue
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置 · 数据二级页（2026-10-10 设置页改版）。
 *
 * 内容 = 原设置页「数据」分区原样搬运：导出（SAF `CreateDocument` + 复制明文文件，文件名
 * `minibook_backup_yyyyMMdd_HHmmss.json`）/ 导入（SAF `OpenDocument` + danger 强确认
 * [ConfirmDialog]，严格校验失败零落库）+ 账单导入入口 + 邮箱凭据入口。
 *
 * 导入/导出期间经 [FullScreenBusyOverlay]（C-20）锁数据入口（禁返回/禁点击）；结果经
 * [MiniBookSnackbarHost]（C-21）播报统一轻提示。
 *
 * @param onBack 返回回调（NavHost 接线传入 `rootController.popBackStack()`）。
 * @param onNavigateToImport 账单导入入口回调（根图 `import`）。
 * @param onNavigateToMailAccount 邮箱凭据入口回调（根图 `mail_account`，F-13）。
 * @param viewModel 设置页 VM；默认经 [hiltViewModel] 解析。
 */
@Composable
fun SettingsDataScreen(
    onBack: () -> Unit,
    onNavigateToImport: () -> Unit = {},
    onNavigateToMailAccount: () -> Unit = {},
    onNavigateToCloudSync: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    // 改父类为 FragmentActivity 后成立；取不到则记为不可操作态（防御，正常不出现）。
    val activity = context as? FragmentActivity
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbar.collectAsStateWithLifecycle()
    val mailAccountCount by viewModel.mailAccountCount.collectAsStateWithLifecycle()

    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    // 导出：SAF CreateDocument 取目标 Uri → 生成明文文件 → contentResolver 复制到 Uri。
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                viewModel.setBusy(true)
                try {
                    val file = viewModel.export()
                    val copied = withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            file.inputStream().use { it.copyTo(out) }
                            true
                        } ?: false
                    }
                    if (copied) {
                        viewModel.showSnackbar("导出成功")
                    } else {
                        viewModel.showSnackbar("导出失败：无法写入目标位置")
                    }
                } catch (e: Exception) {
                    viewModel.showSnackbar("导出失败：${e.message}")
                } finally {
                    viewModel.setBusy(false)
                }
            }
        }
    }

    // 导入：SAF OpenDocument 取备份 Uri → danger 强确认后才执行（零落库校验见 VM/ImportAllUseCase）。
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportConfirm = true
        }
    }

    // 统一轻提示消费：VM snackbar → SnackbarHost → clearSnackbar。
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { SettingsSubPageTopBar(title = "数据", onBack = onBack) },
        snackbarHost = { MiniBookSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(Modifier.padding(16.dp)) {
                SettingSection(title = "数据", content = {
                    SettingRow(
                        title = "导出数据",
                        subtitle = "备份全部数据为 JSON 文件",
                        trailing = { TrailingChevron() },
                        onClick = { exportLauncher.launch(backupFileName()) },
                    )
                    SettingRow(
                        title = "导入数据",
                        subtitle = "从备份文件恢复，将覆盖全部现有数据",
                        trailing = { TrailingChevron() },
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                    )
                    SettingRow(
                        title = "导入账单",
                        subtitle = null,
                        trailing = { TrailingChevron() },
                        onClick = onNavigateToImport,
                    )
                    SettingRow(
                        title = "邮箱凭据",
                        subtitle = "管理用于自动收取账单的邮箱",
                        trailing = {
                            TrailingValue(
                                if (mailAccountCount > 0) "已配置 $mailAccountCount 个" else "未配置",
                            )
                        },
                        onClick = onNavigateToMailAccount,
                    )
                    SettingRow(
                        title = "云同步",
                        subtitle = "登录云端账号，手动上传 / 下载账目数据（本地数据不会丢）",
                        trailing = { TrailingChevron() },
                        onClick = onNavigateToCloudSync,
                    )
                })
            }

            // 导入/导出忙碌态：全屏遮罩锁数据入口（禁返回、吞点击）。
            if (isBusy) {
                FullScreenBusyOverlay(message = "正在处理，请稍候…")
            }
        }
    }

    // —— 导入强确认（danger：即将覆盖全部现有数据） ——
    if (showImportConfirm) {
        ConfirmDialog(
            title = "导入数据",
            message = "即将覆盖全部现有数据，且不可撤销。请确认备份文件来源可靠。",
            confirmLabel = "确认导入",
            danger = true,
            onConfirm = {
                showImportConfirm = false
                pendingImportUri?.let { uri ->
                    activity?.let { viewModel.importFromUri(it, uri) }
                }
                pendingImportUri = null
            },
            onDismiss = {
                showImportConfirm = false
                pendingImportUri = null
            },
        )
    }
}

/** 导出备份文件名规范（UI §2 P-06）：`minibook_backup_yyyyMMdd_HHmmss.json`。 */
private fun backupFileName(): String {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    return "minibook_backup_$timestamp.json"
}
