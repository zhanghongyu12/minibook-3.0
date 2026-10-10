package com.minibook.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.SmsNotifyMode
import com.minibook.ui.components.ChoiceDialog
import com.minibook.ui.components.ChoiceOption
import com.minibook.ui.components.ConfirmDialog
import com.minibook.ui.components.SettingRow
import com.minibook.ui.components.SettingSection
import com.minibook.ui.components.SwitchRow

/**
 * 设置页「短信账单」区块（F-14，UI §11.1 / §11.2）。
 *
 * 开关矩阵（全部默认关闭，总开关关闭时不申请任何短信权限）：
 * - 总开关「启用短信账单识别」；
 * - 「实时识别新短信」（L1）+「通知方式」单选（静默聚合 / 每日汇总 / 逐条提醒）；
 * - 「自动记账」（L2）：**应用锁开启时置灰并注明原因**（后台无法解密数据库，R7）；
 * - 「打开 App 时自动补扫」（L3，默认开）；
 * - 白名单发送方逐项开关（仅总开关开启时展示）+「清空白名单」（danger 强确认）；
 * - 权限状态（短信读取 / 短信接收 / 通知）+ 引导（**只展示状态与引导，不发起请求**，
 *   `// TODO(F-14 权限申请)`）。
 *
 * 隐私（DEC-026）：区块只展示发送方号码与计数，**不含短信原文、卡号全文与余额**。
 *
 * @param viewModel 区块 ViewModel；默认经 Hilt 注入（独立于 [SettingsViewModel]）。
 */
@Composable
fun SmsSettingsSection(viewModel: SmsSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val whitelist by viewModel.whitelist.collectAsStateWithLifecycle()
    val providerCount by viewModel.providerCount.collectAsStateWithLifecycle()
    val snackbar by viewModel.snackbar.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val readSmsGranted = hasPermission(context, Manifest.permission.READ_SMS)
    val receiveSmsGranted = hasPermission(context, Manifest.permission.RECEIVE_SMS)
    val notificationsGranted = notificationsEnabled(context)

    var showNotifyDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // 权限状态变化（回到本页时）自动刷新白名单与短信库计数。
    LaunchedEffect(readSmsGranted) { viewModel.refresh(readSmsGranted) }

    SettingSection(title = "短信账单", content = {
        SwitchRow(
            title = "启用短信账单识别",
            checked = settings.enabled,
            onCheckedChange = viewModel::setEnabled,
        )

        // —— 实时识别（L1）与通知方式（降噪口径，UI §11.3） ——
        SwitchRow(
            title = "实时识别新短信",
            checked = settings.realtimeEnabled,
            enabled = settings.enabled,
            onCheckedChange = viewModel::setRealtimeEnabled,
        )
        SettingRow(
            title = "通知方式",
            subtitle = settings.notifyMode.description(),
            trailing = { Text(settings.notifyMode.displayName()) },
            onClick = if (settings.enabled && settings.realtimeEnabled) {
                { showNotifyDialog = true }
            } else {
                null
            },
        )

        // —— 自动记账（L2）：应用锁开启时置灰（R7） ——
        SwitchRow(
            title = "自动记账（仅白名单发送方）",
            checked = settings.autoImportEnabled,
            enabled = settings.enabled && !appLockEnabled,
            onCheckedChange = viewModel::setAutoImportEnabled,
        )
        if (appLockEnabled && settings.enabled) {
            HintText("自动记账需应用锁关闭时可用（后台无法解密数据库）")
        }

        SwitchRow(
            title = "打开 App 时自动补扫",
            checked = settings.autoScanEnabled,
            enabled = settings.enabled,
            onCheckedChange = viewModel::setAutoScanEnabled,
        )

        // —— 白名单（仅总开关开启时展示） ——
        // 空态只解释「怎么进白名单」，**不再显示无意义的「清空」按钮**（2026-10-10 用户反馈
        // 「白名单在哪看，就只有清空」——空的时候整页只剩一个清空按钮，看不出白名单在哪）；
        // 有内容时先给一行「这里是白名单」，再逐条列出可单独开关的发送方，最后才是清空。
        if (settings.enabled) {
            if (whitelist.isEmpty()) {
                HintText(
                    "白名单为空：对某个发送方手动确认入账 3 次（期间无纠错）后自动加入；" +
                        "加入后会在这里逐条列出，可单独关闭它的自动记账。",
                )
            } else {
                HintText("白名单（${whitelist.size} 个发送方，打开 = 允许该发送方自动记账）：")
                whitelist.forEach { entry ->
                    SwitchRow(
                        title = "${entry.sender}（已确认 ${entry.confirmedCount} 次）",
                        checked = entry.autoEnabled,
                        enabled = !appLockEnabled,
                        onCheckedChange = { viewModel.setWhitelistAutoEnabled(entry.sender, it) },
                    )
                }
                TextButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text("清空白名单")
                }
            }
        }

        // —— 权限状态 + 引导（只展示与引导，不发起请求） ——
        PermissionStatusRow(
            title = "短信读取权限",
            granted = readSmsGranted,
            onOpenSettings = { openAppSettings(context) },
        )
        PermissionStatusRow(
            title = "短信接收权限",
            granted = receiveSmsGranted,
            onOpenSettings = { openAppSettings(context) },
        )
        PermissionStatusRow(
            title = "通知权限",
            granted = notificationsGranted,
            onOpenSettings = { openAppSettings(context) },
        )
        HintText(
            if (readSmsGranted && providerCount != null) {
                // 口径诚实性（2026-10-10 真机实测）：MIUI/HyperOS 对非默认短信应用做受限视图，
                // 过滤掉「服务/通知类」会话（本机 App 可见 1 条、系统实际 9 条）。
                // 文案必须写明这是「App 可见条数」，否则用户会误以为短信被删了。
                "本机短信库共 $providerCount 条（App 可见数量；系统会把服务/通知类短信对第三方应用隐藏）。只读，不删除、不修改、不标记已读。"
            } else {
                "需要短信权限；可去系统设置授予，或改用分享导入。"
            },
        )

        snackbar?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    })

    // —— 通知方式三选一 ——
    if (showNotifyDialog) {
        ChoiceDialog(
            title = "通知方式",
            options = SmsNotifyMode.entries.map { ChoiceOption(it.name, it.displayName()) },
            selectedKey = settings.notifyMode.name,
            onSelect = { key ->
                showNotifyDialog = false
                SmsNotifyMode.entries.firstOrNull { it.name == key }?.let(viewModel::setNotifyMode)
            },
            onDismiss = { showNotifyDialog = false },
        )
    }

    // —— 清空白名单强确认（隐私清理，不可撤销） ——
    if (showClearConfirm) {
        ConfirmDialog(
            title = "清空短信白名单",
            message = "将移除全部发送方白名单（含自动记账开关与确认计数），之后所有短信账单都会回到待确认。" +
                "此操作不可撤销。",
            confirmLabel = "确认清空",
            danger = true,
            onConfirm = {
                showClearConfirm = false
                viewModel.clearWhitelist()
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}

/** 权限状态行（私有）：标题 + 已授予/未授予 + 未授予时「去设置」引导。 */
@Composable
private fun PermissionStatusRow(
    title: String,
    granted: Boolean,
    onOpenSettings: () -> Unit,
) {
    SettingRow(
        title = title,
        subtitle = if (granted) "已授予" else "未授予",
        trailing = {
            if (!granted) {
                TextButton(onClick = onOpenSettings) { Text("去设置") }
            }
        },
        onClick = if (granted) null else onOpenSettings,
    )
}

/** 区块内小字提示（私有）：统一 16dp 水平内边距与次要色。 */
@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
    Spacer(Modifier.height(2.dp))
}

/**
 * 跳应用详情页（系统设置）引导授权。
 *
 * **TODO(F-14 权限申请)**：本期只做「状态展示 + 引导文案」，不实现运行时权限请求流程；
 * 用户需在此页手动授予，返回 App 后由 [LaunchedEffect] 重新读取授权状态。
 */
private fun openAppSettings(context: Context) {
    runCatching {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

/** 单权限是否已授予。 */
private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** 通知是否可用：Android 13+ 需 POST_NOTIFICATIONS，且系统通知未关闭。 */
private fun notificationsEnabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    ) {
        return false
    }
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/** 通知方式短名（`when` 穷尽 [SmsNotifyMode]）。 */
internal fun SmsNotifyMode.displayName(): String = when (this) {
    SmsNotifyMode.SILENT -> "静默聚合"
    SmsNotifyMode.DAILY -> "每日汇总"
    SmsNotifyMode.EACH -> "逐条提醒"
}

/** 通知方式说明（降噪口径，UI §11.3）。 */
private fun SmsNotifyMode.description(): String = when (this) {
    SmsNotifyMode.SILENT -> "默认：不弹通知，只在 App 内累计待确认"
    SmsNotifyMode.DAILY -> "同日同发送方合并一条提醒"
    SmsNotifyMode.EACH -> "每次命中账单模板都刷新提醒"
}
