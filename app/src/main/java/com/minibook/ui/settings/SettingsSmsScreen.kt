package com.minibook.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minibook.ui.components.SettingsSubPageTopBar

/**
 * 设置 · 短信账单二级页（2026-10-10 设置页改版）。
 *
 * 内容 = 原设置页内嵌的 [SmsSettingsSection]（开关矩阵 + 白名单 + 权限状态 + 通知方式，
 * 自持 [SmsSettingsViewModel]，本页只加顶栏与滚动容器；区块内部已是独立卡片，不再套 [SettingSection]）。
 *
 * @param onBack 返回回调（NavHost 接线传入 `rootController.popBackStack()`）。
 */
@Composable
fun SettingsSmsScreen(onBack: () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { SettingsSubPageTopBar(title = "短信账单", onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SmsSettingsSection()
        }
    }
}
