package com.minibook.ui.settings

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.data.local.datastore.LockTimeout
import com.minibook.ui.components.ChoiceDialog
import com.minibook.ui.components.ChoiceOption
import com.minibook.ui.components.ConfirmDialog
import com.minibook.ui.components.GuidanceCard
import com.minibook.ui.components.SettingRow
import com.minibook.ui.components.SettingSection
import com.minibook.ui.components.SettingsSubPageTopBar
import com.minibook.ui.components.SwitchRow
import com.minibook.ui.components.TrailingValue

/**
 * 设置 · 安全二级页（2026-10-10 设置页改版）。
 *
 * 内容 = 原设置页「安全」分区原样搬运：应用锁 [SwitchRow]（开启前 `isDeviceSecure` 检测，
 * 无锁屏经 [GuidanceCard] 引导 DEC-003；关闭走 [ConfirmDialog] 提示「数据仍保持加密」DEC-002）
 * + 锁定时长五档 [ChoiceDialog] + 加密态只读行（SQLCipher + Keystore，F-10 不可关）。
 *
 * @param onBack 返回回调（NavHost 接线传入 `rootController.popBackStack()`）。
 * @param viewModel 设置页 VM；默认经 [hiltViewModel] 解析。
 */
@Composable
fun SettingsSecurityScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    // 改父类为 FragmentActivity 后成立；取不到则记为不可操作态（防御，正常不出现）。
    val activity = context as? FragmentActivity

    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val lockTimeout by viewModel.lockTimeout.collectAsStateWithLifecycle()
    val encryptionEnabled by viewModel.encryptionEnabled.collectAsStateWithLifecycle()

    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showLockOffConfirm by remember { mutableStateOf(false) }
    var showDeviceSecureGuidance by remember { mutableStateOf(false) }

    // DEC-003：设备无系统锁屏 → 展示引导卡（跳系统安全设置）。
    LaunchedEffect(Unit) {
        viewModel.needDeviceSecure.collect { showDeviceSecureGuidance = true }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { SettingsSubPageTopBar(title = "安全", onBack = onBack) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(Modifier.padding(16.dp)) {
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
        }
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
}

/** 锁闲置超时五档 UI 文案。 */
internal fun LockTimeout.displayName(): String = when (this) {
    LockTimeout.IMMEDIATELY -> "立即锁定"
    LockTimeout.THIRTY_SECONDS -> "30 秒后"
    LockTimeout.ONE_MINUTE -> "1 分钟后"
    LockTimeout.FIVE_MINUTES -> "5 分钟后"
    LockTimeout.NEVER -> "永不"
}
