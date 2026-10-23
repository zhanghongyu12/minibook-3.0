package com.minibook.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.BuildConfig
import com.minibook.domain.model.SmsSettings
import com.minibook.ui.components.SettingRow
import com.minibook.ui.components.SettingSection
import com.minibook.ui.components.TrailingChevron
import com.minibook.ui.home.LocalHomeNavigation

/**
 * 设置首页（TASK-027 → 2026-10-10 改版：**分组汇总页**，不再是一屏到底的长页）。
 *
 * 用户原话：「设置页面太长了，每种设置设计汇总，然后点击进入二级页面设置。」
 *
 * 结构 = 一张卡片 5 行，每行一个分组 + 一行**当前状态汇总** + `›`，点击进二级页：
 * - 账号 → `settings-account`（2026-10-12 起第一行：登录 / 资料 / 退出 / 注销）；
 * - 账本 → `ledger-manage`（P-11，经 [LocalHomeNavigation] 走根图导航）；
 * - 外观 → [SettingsAppearanceScreen]`settings-appearance`（暗色模式 + 主题色）；
 * - 安全 → [SettingsSecurityScreen]`settings-security`（应用锁 + 锁定时长 + 数据加密）；
 * - 数据 → [SettingsDataScreen]`settings-data`（备份导出/导入 + 账单导入 + 邮箱凭据）；
 * - 短信账单 → [SettingsSmsScreen]`settings-sms`（开关矩阵 + 白名单 + 权限状态）。
 *
 * 二级页注册在根图，经 [LocalSettingsNavigation] 注入根导航能力（同 [LocalHomeNavigation] 口径）。
 * 版本号作为页脚文本，不再单独占一页（关于页只有一行，不值得一次跳转）。
 *
 * @param viewModel 设置页 VM（外观/安全/数据汇总数据源）；默认经 [hiltViewModel] 解析。
 * @param smsViewModel 短信账单 VM（仅读汇总：开关 + 通知方式）；默认经 [hiltViewModel] 解析。
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    smsViewModel: SmsSettingsViewModel = hiltViewModel(),
) {
    // 账本管理 / 二级设置页统一经 CompositionLocal 读根图导航（不穿透 MainScreen 参数）。
    val homeNavigation = LocalHomeNavigation.current
    val settingsNavigation = LocalSettingsNavigation.current

    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val themeColor by viewModel.themeColor.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val lockTimeout by viewModel.lockTimeout.collectAsStateWithLifecycle()
    val mailAccountCount by viewModel.mailAccountCount.collectAsStateWithLifecycle()
    val accountSummary by viewModel.accountSummary.collectAsStateWithLifecycle()
    val smsSettings by smsViewModel.settings.collectAsStateWithLifecycle()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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

            // —— 分组汇总：一行一组，点进二级页 ——
            item {
                SettingSection(title = null, content = {
                    // 账号（2026-10-12，用户要求「账号设置提到设置页面第一行」）：登录 / 资料 /
                    // 退出 / 注销都在「账号」页，二级页标题与入口同名。它是唯一与"身份、跨设备"
                    // 相关的入口，未登录时也是这一屏最可能的行动点，故放第一行。
                    SettingRow(
                        title = "账号",
                        subtitle = accountSummary,
                        trailing = { TrailingChevron() },
                        onClick = settingsNavigation::openAccount,
                    )
                    SettingRow(
                        title = "账本",
                        subtitle = "切换、新增、重命名与删除账本",
                        trailing = { TrailingChevron() },
                        onClick = homeNavigation::openLedgerManage,
                    )
                    SettingRow(
                        title = "外观",
                        subtitle = "暗色模式：${darkMode.displayName()} · 主题色：" +
                            if (themeColor == null) "默认" else "自定义",
                        trailing = { TrailingChevron() },
                        onClick = settingsNavigation::openAppearance,
                    )
                    SettingRow(
                        title = "安全",
                        subtitle = if (appLockEnabled) {
                            "应用锁已开启 · ${lockTimeout.displayName()}"
                        } else {
                            "应用锁未开启（数据加密已启用，不可关闭）"
                        },
                        trailing = { TrailingChevron() },
                        onClick = settingsNavigation::openSecurity,
                    )
                    SettingRow(
                        title = "数据",
                        subtitle = "备份导出 / 导入 · " + if (mailAccountCount > 0) {
                            "邮箱凭据已配置 $mailAccountCount 个"
                        } else {
                            "邮箱凭据未配置"
                        },
                        trailing = { TrailingChevron() },
                        onClick = settingsNavigation::openData,
                    )
                    SettingRow(
                        title = "短信账单",
                        subtitle = smsSummary(smsSettings),
                        trailing = { TrailingChevron() },
                        onClick = settingsNavigation::openSms,
                    )
                })
            }

            // —— 关于：法务文本（TASK-076）。放在「设置」里，两跳可达，不做首启强制弹窗。 ——
            item {
                SettingSection(title = "关于", content = {
                    SettingRow(
                        title = "隐私政策",
                        subtitle = "数据只存在本机；只有你开启云同步才会上传账目",
                        trailing = { TrailingChevron() },
                        onClick = settingsNavigation::openPrivacyPolicy,
                    )
                    SettingRow(
                        title = "用户协议",
                        subtitle = "服务范围、账号责任、数据与备份说明",
                        trailing = { TrailingChevron() },
                        onClick = settingsNavigation::openTerms,
                    )
                })
            }

            // —— 版本（页脚；关于页只有一行，不占一次跳转） ——
            item {
                Text(
                    text = "MiniBook v" + BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

/** 短信账单分组汇总：未开启时写明「零权限申请」，避免用户以为在后台读短信。 */
private fun smsSummary(settings: SmsSettings): String = if (!settings.enabled) {
    "未开启（零权限申请、零读取）"
} else {
    "已开启 · ${settings.notifyMode.displayName()}"
}
