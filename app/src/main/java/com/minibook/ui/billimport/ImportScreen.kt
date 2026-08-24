package com.minibook.ui.billimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.BillImportResult
import com.minibook.ui.components.EmptyState
import com.minibook.ui.components.FullScreenBusyOverlay

/**
 * 账单导入页（TASK-044，UI §9.2 P-15 / F-12）。
 *
 * 根图全屏页，TopAppBar「导入账单」＋ 返回 ←（任何态保留；返回 = 放弃回入口页）。内容区按
 * [ImportUiState] 状态机**原地切换**四态（§9.2 结构树），不做结果弹层 / 底部 Sheet：
 * - Idle：引导说明 + 两张 [PlatformGuideCard]（微信/支付宝，文案精确注入 §9.6）+「去选择文件」；
 * - Busy：[FullScreenBusyOverlay]（C-20，「正在解析账单，请稍候…」）覆盖全屏锁入口、屏蔽返回；
 * - Result：[ImportSummaryCard]（成功 N / 跳过 M）+ [SkippedBreakdownSection]（分原因 ×4 + 解析失败
 *   折叠明细）+「完成」→ [onBack]；
 * - Error：[EmptyState]（C-04）+ 错误文案 +「重新选择」→ [ImportViewModel.retry] 回 Idle。
 *
 * SAF 文件选择（D-31）：`rememberLauncherForActivityResult(OpenDocument)`，mime 不限（通配类型），
 * 判型由 [com.minibook.domain.usecase.bill.ImportBillUseCase] 按扩展名/魔数兜底；取消静默回 Idle。
 *
 * @param onBack 返回回调（NavHost 接线传入 `rootController.popBackStack()`）。
 * @param viewModel 页面 ViewModel；默认经 Hilt 注入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        viewModel.onFilePicked(uri?.toString())
    }

    // Busy 遮罩须覆盖含 TopAppBar 在内的整屏（锁入口 + 屏蔽返回），故置于 Scaffold 之外顶层。
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = { ImportTopBar(onBack = onBack) },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (val state = uiState) {
                    ImportUiState.Idle -> ImportIdleContent(
                        onSelectFile = { filePicker.launch(arrayOf("*/*")) },
                    )
                    ImportUiState.Busy -> Unit // 由下方顶层遮罩承接
                    is ImportUiState.Result -> ImportResultContent(
                        result = state.result,
                        onDone = onBack,
                    )
                    is ImportUiState.Error -> ImportErrorContent(
                        message = state.message,
                        onRetry = viewModel::retry,
                    )
                }
            }
        }

        if (uiState is ImportUiState.Busy) {
            FullScreenBusyOverlay(
                message = "正在解析账单，请稍候…",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 顶栏：标题「导入账单」＋ 返回 ←（emoji 占位，material-icons 缺失 TASK-020 同口径）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text("导入账单") },
        navigationIcon = {
            IconButton(onClick = onBack) { Text("←", fontSize = 20.sp) }
        },
    )
}

/**
 * Idle 提示页（§9.2）：引导说明 + 微信/支付宝两张 [PlatformGuideCard] + 主按钮「去选择文件」。
 *
 * @param onSelectFile 「去选择文件」回调（触发 SAF `OpenDocument`）。
 */
@Composable
private fun ImportIdleContent(onSelectFile: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "支持微信账单（.xlsx）与支付宝账单（.csv），先按下方步骤导出文件，再选择文件导入。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        PlatformGuideCard(
            emoji = "💬",
            title = "微信账单",
            path = "我 → 服务 → 钱包 → 账单 → 常见问题 → 下载账单 → 用于个人对账",
            format = "导出格式：.xlsx（账单明细）",
        )
        Spacer(Modifier.height(12.dp))
        PlatformGuideCard(
            emoji = "🔷",
            title = "支付宝账单",
            path = "我的 → 账单 → 右上「⋯」→ 开具交易流水证明 → 用于个人对账",
            format = "导出格式：.csv（交易流水证明）",
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onSelectFile,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("去选择文件")
        }
    }
}

/**
 * Result 结果页（§9.2）：摘要卡 + 分原因明细（LazyColumn 可滚动）+ 底部「完成」。
 *
 * 解析失败明细并入同一滚动容器（不另设弹层/分页，§9.2 DEC-020）。
 *
 * @param result 导入结果聚合。
 * @param onDone 「完成」回调（`popBackStack` 回入口页；交易 Flow 自动刷新无需手动跳转）。
 */
@Composable
private fun ImportResultContent(
    result: BillImportResult,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
        ) {
            item(key = "summary") {
                ImportSummaryCard(result = result)
            }
            item(key = "breakdown") {
                Spacer(Modifier.height(12.dp))
                SkippedBreakdownSection(result = result)
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("完成")
        }
    }
}

/**
 * Error 错误页（§9.2）：[EmptyState]（C-04）+ 错误文案 +「重新选择」。
 *
 * @param message 错误文案（[ImportViewModel] 已映射为 §9.6 精确中文）。
 * @param onRetry 「重新选择」回调（[ImportViewModel.retry] 原地复位 Idle）。
 */
@Composable
private fun ImportErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    EmptyState(
        icon = ImportErrorIcon,
        title = message,
        subtitle = null,
        actionLabel = "重新选择",
        onAction = onRetry,
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * 导入错误态图标：手写「放大镜」ImageVector（material-icons 缺失，TASK-020 偏差同口径；
 * §9.2 指定「🔍/⚠ 占位」）。fill 用 Black 占位，实际 tint 由 [EmptyState] 内 `Icon` 统一上色。
 */
private val ImportErrorIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ImportError",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 " +
                "5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5z" +
                "M9.5 14C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5s4.5 2.01 4.5 4.5S11.99 14 9.5 14z"
        ),
        fill = SolidColor(Color.Black),
    ).build()
}