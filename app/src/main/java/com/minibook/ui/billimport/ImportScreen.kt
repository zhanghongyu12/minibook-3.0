package com.minibook.ui.billimport

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.BillImportResult
import com.minibook.domain.model.SmsScanMode
import com.minibook.ui.components.EmptyState
import com.minibook.ui.components.FullScreenBusyOverlay

/**
 * 当场复查 `READ_SMS` 是否已授予（**不依赖组合期快照**）。
 *
 * 真机教训（2026-10-10）：组合期一次性读取会在「去系统设置授权 → 返回本页重试」时拿到旧值，
 * 使引导页无法脱离；故点击/onResume 时都按本函数当场复查。
 */
private fun readSmsGrantedNow(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
        PackageManager.PERMISSION_GRANTED

/**
 * 需要申请的短信相关权限（TASK-084 ⑤）：`READ_SMS` 扫描必需、`RECEIVE_SMS` 到达即记账、
 * `POST_NOTIFICATIONS` 静默聚合通知（仅 Android 13+ 存在，低版本不申请）。
 */
private fun smsPermissions(): Array<String> = buildList {
    add(Manifest.permission.READ_SMS)
    add(Manifest.permission.RECEIVE_SMS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

/**
 * 账单导入页（TASK-044，UI §9.2 P-15 / F-12）。
 *
 * 根图全屏页，TopAppBar「导入账单」＋ 返回 ←（任何态保留；返回 = 放弃回入口页）。内容区按
 * [ImportUiState] 状态机**原地切换**四态（§9.2 结构树），不做结果弹层 / 底部 Sheet：
 * - Idle：**三个入口**（账单文件 / 邮箱账单 / 短信账单 —— 各自标题 + 一行简短说明 + 按钮）+
 *   折叠的「如何导出账单？」（[PlatformGuideCard] ×2，默认收起，2026-10-10 改版）；
 * - Busy：[FullScreenBusyOverlay]（C-20，「正在解析账单，请稍候…」）覆盖全屏锁入口、屏蔽返回；
 * - Result：[ImportSummaryCard]（成功 N / 跳过 M）+ [SkippedBreakdownSection]（分原因 ×4 + 解析失败
 *   折叠明细）+「完成」→ [onBack]；
 * - Error：[EmptyState]（C-04）+ 错误文案 +「重新选择」→ [ImportViewModel.retry] 回 Idle。
 *
 * SAF 文件选择（D-31）：`rememberLauncherForActivityResult(OpenDocument)`，mime 不限（通配类型），
 * 判型由 [com.minibook.domain.usecase.bill.ImportBillUseCase] 按扩展名/魔数兜底；取消静默回 Idle。
 *
 * 返回语义（2026-10-10 改版）：入口页或进行中 → 退出本页；功能结果态 → 回三入口页（顶栏 ← 与系统
 * 返回键同语义，见 `leaveOrBackToLanding`）。
 *
 * @param onBack 返回回调（NavHost 接线传入 `rootController.popBackStack()`）。
 * @param onNavigateToMailAccount 「去配置」回调（未配置邮箱凭据态引导去「设置 → 邮箱账单」）。
 * @param onNavigateToSmsPending 「查看待确认」回调（F-14：短信干跑预览/结果区跳待确认清单页）。
 * @param viewModel 页面 ViewModel；默认经 Hilt 注入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    onNavigateToMailAccount: () -> Unit = {},
    onNavigateToSmsPending: () -> Unit = {},
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        viewModel.onFilePicked(uri?.toString())
    }

    // F-14 权限门：**导入前主动申请**（TASK-084 ⑤，2026-10-13 用户要求「有的权限先获取一下，
    // 如果权限不全提示让给权限，不要硬导入」）；未授予时仍然只展示引导态、不扫描不写库。
    //
    // ⚠️ 真机教训（2026-10-10，小米 HyperOS/Android 16）：此处原为**组合期一次性快照** →
    // 用户在系统设置授予后回到本页，快照仍是 false，「重新检查权限」永远拿旧值（引导页成死状态）。
    // 现在两处兜住：① onResume 重新求值（从系统设置返回即刷新）；② 点击时当场复查。
    val context = LocalContext.current
    var readSmsGranted by remember { mutableStateOf(readSmsGrantedNow(context)) }
    LifecycleResumeEffect(Unit) {
        readSmsGranted = readSmsGrantedNow(context)
        onPauseOrDispose { }
    }
    // 三个权限一次申请；授权后**接着跑用户刚点的那次扫描**（不让他再点一遍），被拒才落到引导态。
    var pendingSmsMode by remember { mutableStateOf(SmsScanMode.FULL) }
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val granted = readSmsGrantedNow(context)
        readSmsGranted = granted
        viewModel.onReadSmsBills(granted, pendingSmsMode)
    }
    // 短信入口统一走这里：已授权直接扫；未授权先申请（这就是「主动申请」）。
    val startSmsScan: (SmsScanMode) -> Unit = { mode ->
        if (readSmsGrantedNow(context)) {
            viewModel.onReadSmsBills(true, mode)
        } else {
            pendingSmsMode = mode
            smsPermissionLauncher.launch(smsPermissions())
        }
    }
    // SMS 流程「重新扫描」：当场复查权限后重跑扫描。模式**沿用当前模式**（2026-10-10 两种扫描方式：
    // 增量/全量由入口页选定；扫描结果里带着本次模式，故这里从 preview 读回，切模式请回入口页）。
    val currentSmsMode = when (val s = uiState) {
        is ImportUiState.SmsPreview -> s.preview.scanMode
        is ImportUiState.SmsImporting -> s.preview.scanMode
        is ImportUiState.SmsResult -> s.preview.scanMode
        else -> SmsScanMode.FULL
    }
    val rescanSms = { viewModel.onReadSmsBills(readSmsGrantedNow(context), currentSmsMode) }

    // 返回语义（2026-10-10 入口页改版）：入口页 / 进行中 → 退出本页（进行中退出 = 取消本次
    // 读取，VM 随返回栈销毁）；功能结果态 → 回「三个入口」页，可换个方式再导一次。
    val running = uiState is ImportUiState.Busy || uiState is ImportUiState.MailBusy ||
        uiState is ImportUiState.SmsScanning || uiState is ImportUiState.SmsImporting
    val canReturnToLanding = uiState !is ImportUiState.Idle && !running
    val leaveOrBackToLanding: () -> Unit = {
        if (canReturnToLanding) viewModel.retry() else onBack()
    }
    // 系统返回键与顶栏 ← 同语义（多邮箱选择弹窗自持返回，不受影响）。
    BackHandler(enabled = canReturnToLanding) { viewModel.retry() }

    // Busy 遮罩须覆盖含 TopAppBar 在内的整屏（锁入口 + 屏蔽返回），故置于 Scaffold 之外顶层。
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = { ImportTopBar(onBack = leaveOrBackToLanding) },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (val state = uiState) {
                    ImportUiState.Idle -> ImportIdleContent(
                        onSelectFile = { filePicker.launch(arrayOf("*/*")) },
                        onFetchMailBills = viewModel::onFetchMailBills,
                        onReadSmsIncremental = { startSmsScan(SmsScanMode.INCREMENTAL) },
                        onReadSmsFull = { startSmsScan(SmsScanMode.FULL) },
                    )
                    ImportUiState.Busy -> Unit // 由下方顶层遮罩承接
                    is ImportUiState.Result -> ImportResultContent(
                        result = state.result,
                        onDone = leaveOrBackToLanding,
                    )
                    is ImportUiState.Error -> ImportErrorContent(
                        message = state.message,
                        onRetry = viewModel::retry,
                    )
                    // 一键收取：进度与结果/错误**同页合一**（MailImportContent 单一版式，
                    // 收取中实时推进 stepper，结束后原地追加结果/错误区 + 底部按钮，
                    // 不做任何页面跳转 / 版式整体替换）。
                    is ImportUiState.MailBusy -> MailImportContent(
                        progress = state.progress,
                        running = true,
                        onRetry = viewModel::retry,
                        onBack = onBack,
                    )
                    is ImportUiState.MailResult -> MailImportContent(
                        progress = state.progress,
                        summary = state.summary,
                        running = false,
                        onRetry = viewModel::retry,
                        onBack = leaveOrBackToLanding,
                    )
                    // 多邮箱选择弹窗：单选立即收取 / 全部收取 / 取消。
                    is ImportUiState.MailChooseAccount -> MailChooseAccountDialog(
                        accounts = state.accounts,
                        onSelect = { viewModel.onFetchAccounts(listOf(it)) },
                        onSelectAll = {
                            viewModel.onFetchAccounts(state.accounts.map { account -> account.id })
                        },
                        onDismiss = viewModel::dismiss,
                    )
                    ImportUiState.MailNotConfigured -> ImportMailNotConfiguredContent(
                        onNavigateToMailAccount = onNavigateToMailAccount,
                    )
                    // 一键收取失败：仍在 MailImportContent 同一版式内原地追加错误区
                    //（progress 为 null 时退化为仅错误区，不回 EmptyState 整页）。
                    is ImportUiState.MailError -> MailImportContent(
                        progress = state.progress,
                        errorMessage = state.message,
                        running = false,
                        onRetry = viewModel::retry,
                        onBack = leaveOrBackToLanding,
                    )
                    // —— F-14 短信导入：与 F-13 同口径，进度/预览/结果/错误**同页合一** ——
                    ImportUiState.SmsScanning -> SmsImportContent(
                        phase = SmsImportPhase.SCANNING,
                        preview = null,
                        onConfirm = {},
                        onRescan = rescanSms,
                        onBack = onBack,
                    )
                    is ImportUiState.SmsPreview -> SmsImportContent(
                        phase = SmsImportPhase.PREVIEW,
                        preview = state.preview,
                        onConfirm = viewModel::onConfirmSmsImport,
                        onRescan = rescanSms,
                        onBack = leaveOrBackToLanding,
                        onViewPending = onNavigateToSmsPending,
                        onSenderImportToggle = { sender, enabled ->
                            viewModel.onSenderImportEnabled(sender, enabled, currentSmsMode)
                        },
                    )
                    is ImportUiState.SmsImporting -> SmsImportContent(
                        phase = SmsImportPhase.IMPORTING,
                        preview = state.preview,
                        onConfirm = {},
                        onRescan = rescanSms,
                        onBack = onBack,
                    )
                    is ImportUiState.SmsResult -> SmsImportContent(
                        phase = SmsImportPhase.DONE,
                        preview = state.preview,
                        summary = state.summary,
                        onConfirm = {},
                        onRescan = rescanSms,
                        onBack = leaveOrBackToLanding,
                        onViewPending = onNavigateToSmsPending,
                    )
                    is ImportUiState.SmsError -> SmsImportContent(
                        phase = SmsImportPhase.ERROR,
                        preview = null,
                        errorMessage = state.message,
                        failedStep = state.failedStep,
                        onConfirm = {},
                        onRescan = rescanSms,
                        onBack = leaveOrBackToLanding,
                    )
                    ImportUiState.SmsPermissionNeeded -> ImportSmsPermissionContent(
                        onRequestPermission = { startSmsScan(currentSmsMode) },
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
 * 导入入口页（2026-10-10 改版，用户要求）：**三个入口** = 账单文件 / 邮箱账单 / 短信账单，
 * 每个入口 = 标题 + 一行简短说明 + 按钮，点击进入不同功能。
 *
 * 微信/支付宝的两张导出指引卡不再常驻首页 —— 折叠进「如何导出账单？」（默认收起，
 * 需要时展开；导出路径文案原样保留，不丢可用性）。
 *
 * @param onSelectFile 「选择文件导入」回调（触发 SAF `OpenDocument`）。
 * @param onFetchMailBills 「一键收取邮箱账单」回调（[ImportViewModel.onFetchMailBills]）。
 * @param onReadSmsIncremental 「增量扫描」回调（只看上次扫描之后的新短信）。
 * @param onReadSmsFull 「全量扫描」回调（全部历史，看过的未识别折叠展示）。
 */
@Composable
private fun ImportIdleContent(
    onSelectFile: () -> Unit,
    onFetchMailBills: () -> Unit,
    onReadSmsIncremental: () -> Unit = {},
    onReadSmsFull: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "选择导入方式",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))

        // —— ① 账单文件导入 ——
        ImportEntryCard(
            emoji = "📄",
            title = "账单文件导入",
            description = "微信 / 支付宝导出的账单文件（.xlsx / .csv），选择后自动识别、去重导入。",
            actionLabel = "选择文件导入",
            onClick = onSelectFile,
        )

        // 导出指引折叠（2026-10-10：不再常驻首页，需要时展开）
        var showExportGuide by remember { mutableStateOf(false) }
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (showExportGuide) "▾ 如何导出账单？（点击收起）" else "▸ 如何导出账单？",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showExportGuide = !showExportGuide }
                    .padding(12.dp),
            )
        }
        if (showExportGuide) {
            Spacer(Modifier.height(8.dp))
            PlatformGuideCard(
                emoji = "💬",
                title = "微信账单",
                path = "我 → 服务 → 钱包 → 账单 → 常见问题 → 下载账单 → 用于个人对账",
                format = "导出格式：.xlsx（账单明细）",
                onClick = onSelectFile,
            )
            Spacer(Modifier.height(8.dp))
            PlatformGuideCard(
                emoji = "🔷",
                title = "支付宝账单",
                path = "我的 → 账单 → 右上「⋯」→ 开具交易流水证明 → 用于个人对账",
                format = "导出格式：.csv（交易流水证明）",
                onClick = onSelectFile,
            )
        }

        Spacer(Modifier.height(12.dp))
        // —— ② 邮箱账单自动导入 ——
        ImportEntryCard(
            emoji = "✉️",
            title = "邮箱账单自动导入",
            description = "配置邮箱授权码后一键收取，自动解密并导入（QQ 邮箱 IMAP）。",
            actionLabel = "一键收取邮箱账单",
            onClick = onFetchMailBills,
        )

        Spacer(Modifier.height(12.dp))
        // —— ③ 短信账单导入：两种扫描方式（2026-10-10 用户要求）——
        // 增量 = 只看上次扫描之后的新短信（扫完推进水位，不会再翻出看过的）；全量 = 全部历史（看过的折叠）。
        ImportEntryCard(
            emoji = "💬",
            title = "短信账单导入",
            description = "读取本机银行 / 支付短信，识别为账单后确认写入（只读，不删不改）。",
            actionLabel = "增量扫描",
            onClick = onReadSmsIncremental,
            secondaryLabel = "全量扫描",
            onSecondaryClick = onReadSmsFull,
            caption = "增量＝只看上次扫描之后的新短信；全量＝全部历史（看过的折叠在下面）。",
        )
    }
}

/**
 * 单个导入入口卡片（2026-10-10 改版「三个按钮上面简短的说明」）：标题 + 一行说明 + 全宽按钮。
 *
 * [secondaryLabel] 非空时渲染**两个并排按钮**（短信入口的「增量 / 全量」两种扫描方式）；
 * [caption] 非空时在按钮下方补一行小字（说明两种模式的差别）。
 */
@Composable
private fun ImportEntryCard(
    emoji: String,
    title: String,
    description: String,
    actionLabel: String,
    onClick: () -> Unit,
    secondaryLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    caption: String? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (secondaryLabel != null && onSecondaryClick != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(onClick = onClick, modifier = Modifier.weight(1f)) { Text(actionLabel) }
                    OutlinedButton(onClick = onSecondaryClick, modifier = Modifier.weight(1f)) {
                        Text(secondaryLabel)
                    }
                }
            } else {
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(actionLabel)
                }
            }
            if (caption != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * F-14 未授予短信读取权限的引导页（TASK-084 ⑤ 起**主动申请**）：主按钮直接拉起系统权限框
 * （三个权限一起申请），已授权后点它就继续扫描；系统不再弹窗时按副标题走系统设置。
 *
 * 文案口径见 UI §11.4「权限被拒」。
 *
 * @param onRequestPermission 「授予权限 / 继续」回调：未授权拉起系统弹窗，已授权直接继续这次扫描。
 */
@Composable
private fun ImportSmsPermissionContent(onRequestPermission: () -> Unit) {
    EmptyState(
        icon = ImportErrorIcon,
        title = "需要短信权限；可去系统设置授予，或改用分享导入",
        subtitle = "点下方按钮直接申请（读取短信 / 接收短信 / 通知）；若系统不再弹窗，请到" +
            "系统设置 → 应用 → MiniBook → 权限 → 短信授予，然后回到本页。" +
            "（本应用只读短信，不删除、不修改、不标记已读，也不申请为默认短信应用。）",
        actionLabel = "授予权限并继续",
        onAction = onRequestPermission,
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * 「一键收取」未配置页（F-13）：[EmptyState] 提示去「设置 → 邮箱账单」填写凭据 +
 * 「去配置」（NavHost 接线 [onNavigateToMailAccount] 跳转邮箱账单设置页）。
 *
 * @param onNavigateToMailAccount 「去配置」回调。
 */
@Composable
private fun ImportMailNotConfiguredContent(
    onNavigateToMailAccount: () -> Unit,
) {
    EmptyState(
        icon = ImportErrorIcon,
        title = "尚未配置邮箱凭据，请先到「设置 → 邮箱凭据」添加 QQ 邮箱与 IMAP 授权码。",
        subtitle = null,
        actionLabel = "去配置",
        onAction = onNavigateToMailAccount,
        modifier = Modifier.fillMaxSize(),
    )
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