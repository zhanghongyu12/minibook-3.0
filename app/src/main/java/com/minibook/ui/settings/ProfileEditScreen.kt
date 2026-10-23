package com.minibook.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 「编辑资料」页（2026-10-12 第二轮：资料字段从 1 个扩到 4 个，弹窗放不下，改成整页）。
 *
 * 空 = 不填写（昵称留空则回退显示用户名）。**只提交改动过的字段**，
 * 所以在这里改邮箱不会顺手把个性签名清掉（见 `AccountViewModel.saveProfile`）。
 */
@Composable
fun ProfileEditScreen(
    onBack: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // 保存成功 → 自动返回账号页（那边进入时会重新拉一次资料）
    LaunchedEffect(ui.savedTick) {
        if (ui.savedTick > 0) onBack()
    }
    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val emailError = SyncPresenter.profileError(ui.emailDraft)

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text(
                text = "编辑资料",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "昵称会显示在账号页；邮箱 / 个性签名 / 地区都可以留空。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            AuthField(
                value = ui.nicknameDraft,
                onValueChange = viewModel::onNicknameDraftChange,
                label = "昵称",
                supporting = "留空 = 回退显示用户名「${ui.accountName}」",
            )
            Spacer(Modifier.height(12.dp))
            AuthField(
                value = ui.emailDraft,
                onValueChange = viewModel::onEmailDraftChange,
                label = "邮箱",
                supporting = emailError ?: "用于你自己记录联系方式，可留空",
            )
            Spacer(Modifier.height(12.dp))
            AuthField(
                value = ui.bioDraft,
                onValueChange = viewModel::onBioDraftChange,
                label = "个性签名",
                supporting = "最多 80 字",
            )
            Spacer(Modifier.height(12.dp))
            AuthField(
                value = ui.regionDraft,
                onValueChange = viewModel::onRegionDraftChange,
                label = "地区",
                supporting = "如：北京（最多 32 字）",
            )
            Spacer(Modifier.height(24.dp))
            AuthPrimaryButton(text = "保存", busy = ui.busy, onClick = viewModel::saveProfile)
            Spacer(Modifier.height(16.dp))
        }
    }
}
