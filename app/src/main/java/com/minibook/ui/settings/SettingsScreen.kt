package com.minibook.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.data.local.datastore.DarkModeSetting
import com.minibook.data.local.datastore.LockTimeout
import com.minibook.ui.components.ChoiceDialog
import com.minibook.ui.components.ChoiceOption
import com.minibook.ui.components.ConfirmDialog
import com.minibook.ui.components.FullScreenBusyOverlay
import com.minibook.ui.components.GuidanceCard
import com.minibook.ui.components.MiniBookSnackbarHost
import com.minibook.ui.components.SettingRow
import com.minibook.ui.components.SettingSection
import com.minibook.ui.components.SwitchRow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页（TASK-027，UI §2 P-06、ARCHITECTURE §9/§10/§11、DEC-002/DEC-003/DEC-005）。
 *
 * 分区结构（[SettingSection]/[SettingRow]/[SwitchRow]，C-23）：
 * - 账本：账本管理入口（经 [onNavigateToLedgerManage] 跳 `ledger-manage`，P-11，MainScreen 中转根图）。
 * - 外观：暗色模式三态 [ChoiceDialog]（跟随系统/亮色/暗色，软切换即时生效，F-09）。
 * - 安全：应用锁 [SwitchRow]（开启前 `isDeviceSecure` 检测，无锁屏经 [GuidanceCard] 引导
 *   DEC-003；关闭走 [ConfirmDialog] 提示「数据仍保持加密」DEC-002）+ 锁定时长五档
 *   [ChoiceDialog] + 加密态只读行（SQLCipher + Keystore，F-10 不可关）。
 * - 数据：导出（SAF `CreateDocument` + 复制明文文件，文件名
 *   `minibook_backup_yyyyMMdd_HHmmss.json`）/ 导入（SAF `OpenDocument` + danger 强确认
 *   `ConfirmDialog`，严格校验失败零落库）。
 * - 关于：版本 v1.0.0。
 *
 * 导入/导出期间经 [FullScreenBusyOverlay]（C-20）锁数据入口（禁返回/禁点击，遮罩覆盖本页
 * 内容区；底部 Tab 屏蔽需 MainScreen 配合，编排者另行接线）。结果经 [MiniBookSnackbarHost]
 * （C-21）播报统一轻提示。
 *
 * @param viewModel 设置页 VM；默认经 [hiltViewModel] 解析。
 * @param onNavigateToLedgerManage 账本管理入口回调（MainScreen 中转根图导航到 ledger-manage）。
 * @param onNavigateToImport 账单导入入口回调（MainScreen 中转根图导航到 `import`，§9.1 可选入口）。
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToLedgerManage: () -> Unit = {},
    onNavigateToImport: () -> Unit = {},
) {
    val context = LocalContext.current
    // 改父类为 FragmentActivity 后成立；取不到则记为不可操作态（防御，正常不出现）。
    val activity = context as? FragmentActivity
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val lockTimeout by viewModel.lockTimeout.collectAsStateWithLifecycle()
    val encryptionEnabled by viewModel.encryptionEnabled.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbar.collectAsStateWithLifecycle()

    var showDarkModeDialog by remember { mutableStateOf(false) }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showLockOffConfirm by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showDeviceSecureGuidance by remember { mutableStateOf(false) }

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

    // DEC-003：设备无系统锁屏 → 展示引导卡（跳系统安全设置）。
    LaunchedEffect(Unit) {
        viewModel.needDeviceSecure.collect { showDeviceSecureGuidance = true }
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
        snackbarHost = { MiniBookSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                // —— 账本 ——
                item {
                    SettingSection(title = "账本", content = {
                        SettingRow(
                            title = "账本管理",
                            subtitle = "切换、新增、重命名与删除账本",
                            trailing = { TrailingChevron() },
                            onClick = onNavigateToLedgerManage,
                        )
                    })
                }

                // —— 外观 ——
                item {
                    SettingSection(title = "外观", content = {
                        SettingRow(
                            title = "暗色模式",
                            subtitle = "跟随系统 / 亮色 / 暗色",
                            trailing = { TrailingValue(darkMode.displayName()) },
                            onClick = { showDarkModeDialog = true },
                        )
                    })
                }

                // —— 安全 ——
                item {
                    SettingSection(title = "安全", content = {
                        SwitchRow(
                            title = "应用锁",
                            checked = appLockEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    // 开启：VM 内 isDeviceSecure 检测（DEC-003，无锁屏走引导事件）；
                                    // 通过后经 BiometricPrompt 认证把 blob 重加密为 auth 包裹（DEC-002）。
                                    viewModel.setAppLockEnabled(true, activity)
                                } else {
                                    // 关闭：先强确认（数据仍保持加密，DEC-002）。
                                    showLockOffConfirm = true
                                }
                            },
                        )
                        SettingRow(
                            title = "锁定时长",
                            subtitle = "仅应用锁开启时生效",
                            trailing = { TrailingValue(lockTimeout.displayName()) },
                            onClick = if (appLockEnabled) {
                                { showTimeoutDialog = true }
                            } else {
                                null
                            },
                        )
                        SettingRow(
                            title = "数据加密",
                            subtitle = "SQLCipher + Keystore 已启用（架构级默认，不可关闭）",
                            trailing = { TrailingValue(if (encryptionEnabled) "已启用" else "未启用") },
                        )
                    })
                }

                // —— 数据 ——
                item {
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
                    })
                }

                // —— 关于 ——
                item {
                    SettingSection(title = "关于", content = {
                        SettingRow(
                            title = "版本",
                            trailing = { TrailingValue("v1.0.0") },
                        )
                    })
                }
            }

            // DEC-003 引导卡：设备无系统锁屏时展示，跳系统安全设置后关闭。
            if (showDeviceSecureGuidance) {
                GuidanceCard(
                    message = "未检测到系统锁屏，生物识别不可用。请前往系统设置开启锁屏密码后再开启应用锁。",
                    actionLabel = "去设置",
                    onAction = {
                        try {
                            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                            if (context !is Activity) {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // 系统安全设置不可达时静默忽略
                        }
                        showDeviceSecureGuidance = false
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                )
            }

            // 导入/导出忙碌态：全屏遮罩锁数据入口（禁返回、吞点击）。
            if (isBusy) {
                FullScreenBusyOverlay(message = "正在处理，请稍候…")
            }
        }
    }

    // —— 暗色模式三态（F-09，软切换即时生效） ——
    if (showDarkModeDialog) {
        ChoiceDialog(
            title = "暗色模式",
            options = listOf(
                ChoiceOption(DarkModeSetting.SYSTEM.name, "跟随系统"),
                ChoiceOption(DarkModeSetting.LIGHT.name, "亮色"),
                ChoiceOption(DarkModeSetting.DARK.name, "暗色"),
            ),
            selectedKey = darkMode.name,
            onSelect = { key ->
                showDarkModeDialog = false
                DarkModeSetting.entries.firstOrNull { it.name == key }?.let(viewModel::setDarkMode)
            },
            onDismiss = { showDarkModeDialog = false },
        )
    }

    // —— 锁闲置超时五档 ——
    if (showTimeoutDialog) {
        ChoiceDialog(
            title = "锁定时长",
            options = listOf(
                ChoiceOption(LockTimeout.IMMEDIATELY.name, "立即锁定"),
                ChoiceOption(LockTimeout.THIRTY_SECONDS.name, "30 秒后"),
                ChoiceOption(LockTimeout.ONE_MINUTE.name, "1 分钟后"),
                ChoiceOption(LockTimeout.FIVE_MINUTES.name, "5 分钟后"),
                ChoiceOption(LockTimeout.NEVER.name, "永不"),
            ),
            selectedKey = lockTimeout.name,
            onSelect = { key ->
                showTimeoutDialog = false
                LockTimeout.entries.firstOrNull { it.name == key }?.let(viewModel::setLockTimeout)
            },
            onDismiss = { showTimeoutDialog = false },
        )
    }

    // —— 关锁确认（DEC-002：数据仍保持加密） ——
    if (showLockOffConfirm) {
        ConfirmDialog(
            title = "关闭应用锁",
            message = "关闭后不再需要生物识别解锁。数据仍保持加密。",
            confirmLabel = "确认关闭",
            onConfirm = {
                showLockOffConfirm = false
                activity?.let { viewModel.setAppLockEnabled(false, it) }
            },
            onDismiss = { showLockOffConfirm = false },
        )
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

/** 设置行尾部值文本（onSurfaceVariant 色，C-23 trailing 槽）。 */
@Composable
private fun TrailingValue(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 设置行尾部「›」箭头（C-23 trailing 槽，指示可点跳转）。 */
@Composable
private fun TrailingChevron() {
    Text(
        text = "›",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 暗色模式三态 UI 文案。 */
private fun DarkModeSetting.displayName(): String = when (this) {
    DarkModeSetting.SYSTEM -> "跟随系统"
    DarkModeSetting.LIGHT -> "亮色"
    DarkModeSetting.DARK -> "暗色"
}

/** 锁闲置超时五档 UI 文案。 */
private fun LockTimeout.displayName(): String = when (this) {
    LockTimeout.IMMEDIATELY -> "立即锁定"
    LockTimeout.THIRTY_SECONDS -> "30 秒后"
    LockTimeout.ONE_MINUTE -> "1 分钟后"
    LockTimeout.FIVE_MINUTES -> "5 分钟后"
    LockTimeout.NEVER -> "永不"
}

/** 导出备份文件名规范（UI §2 P-06）：`minibook_backup_yyyyMMdd_HHmmss.json`。 */
private fun backupFileName(): String {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    return "minibook_backup_$timestamp.json"
}
