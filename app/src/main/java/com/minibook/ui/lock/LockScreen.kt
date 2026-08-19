package com.minibook.ui.lock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.ui.components.LogoLockScreen

/**
 * 解锁门页面（TASK-022、ARCHITECTURE §7.4/§9、DEC-002/DEC-003）。
 *
 * 组合 C-26 [LockComponents]（[LogoLockScreen]）并按 [LockViewModel.appLockEnabled] 分诊：
 * - 锁「关闭」：不展示解锁按钮——[LaunchedEffect] 自动触发静默开库（plain key，§7.4
 *   分诊 A），期间展示 LoadingState（品牌加载）。
 * - 锁「开启」+ 设备已设系统锁屏：[LogoLockScreen] 内 [com.minibook.ui.components.BioAuthButton]
 *   等待用户点按（CryptoObject 由 AppLockController 内部构造，UI 只触发）。
 * - 锁「开启」+ 设备无系统锁屏：[LogoLockScreen] 内 [com.minibook.ui.components.GuidanceCard]
 *   引导去系统安全设置（DEC-003，不降级明文）；用户返回后经 onResume 回检设备锁屏。
 *
 * 解锁成功即由 NavHost `key(isLocked)` 重建切离本页（不保留返回栈）；失败/取消经
 * [LockViewModel.unlockError] 展示错误并停留锁屏可重试。
 *
 * 锁态下本页无任何数据绑定（DB 未开，无可达数据，验收点）。
 *
 * @param viewModel 解锁门 VM；默认经 [hiltViewModel] 解析（Activity 级 owner）。
 */
@Composable
fun LockScreen(viewModel: LockViewModel = hiltViewModel()) {
    val context = LocalContext.current
    // 改父类为 FragmentActivity 后成立；取不到则记为不可解锁态（防御，正常不出现）。
    val activity = context as? FragmentActivity
    val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val unlockError by viewModel.unlockError.collectAsStateWithLifecycle()
    var isDeviceSecure by remember { mutableStateOf(viewModel.isDeviceSecure()) }

    // DEC-003：用户从系统设置返回后（onResume）回检设备锁屏状态。
    LifecycleResumeEffect(Unit) {
        isDeviceSecure = viewModel.isDeviceSecure()
        onPauseOrDispose { }
    }

    // 锁关闭路径：自动静默开库（plain key，无用户交互）；锁开启路径由用户点按触发。
    LaunchedEffect(appLockEnabled) {
        if (appLockEnabled) {
            // 清除 appLockEnabled 加载窗口的瞬时误报（初值 false 曾触发错误路径静默开库）。
            viewModel.clearUnlockError()
        } else if (isLocked) {
            activity?.let(viewModel::unlock)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (appLockEnabled) {
            LogoLockScreen(
                onUnlock = { activity?.let(viewModel::unlock) },
                isDeviceSecure = isDeviceSecure,
                guidanceMessage = "为保护数据，请先设置设备锁屏",
            )
        } else {
            // 锁关闭：自动静默开库中，展示 LoadingState（不展示解锁按钮）。
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        // 失败/取消提示：停留锁屏可重试（生物识别按钮仍可点按）。
        unlockError?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 48.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
