package com.minibook.ui.billimport

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minibook.domain.model.MailAccount
import com.minibook.domain.model.MailImportProgress
import com.minibook.domain.model.MailImportStep
import com.minibook.domain.model.MailImportStepState
import com.minibook.domain.model.MailImportStepStatus
import com.minibook.domain.model.MailImportSummary
import com.minibook.domain.model.MailItemStep

/**
 * 「一键收取」进度 + 结果**同页合一**内容（F-13）：收取中（[MailImportContent] 的
 * `running = true`）显示竖向 stepper（5 步，每步状态图标 + 中文步骤名 + summary）+
 * 第 4 步展开区（当前第 i/N 封 + 子步骤中文文案 + CRACK 百分比）+ 逐封结果行卡
 * （限高可滚动）；**结束后不切换版式**，在 stepper 下方原地追加摘要卡（[summary] 非空）
 * 或错误卡（[errorMessage] 非空），并出现底部「重新选择」/「返回」按钮。
 *
 * 整页为**单一 `Column`**：上部内容区（进度 + 结果/错误）独占一个 `weight(1f)` 的
 * `verticalScroll` 滚动容器，底部按钮区固定钉在屏幕底部不参与滚动（失败明细多时
 * 按钮不会被顶出屏幕；`running = true` 时不出现按钮区，内容区独占全高）。从进度到
 * 结果零版式替换、无页面跳转；多邮箱时顶部显示「邮箱 k/N · <email>」。
 *
 * 纯渲染组件，文案经 [MailImportPresenter] 纯函数映射；**不使用全屏遮罩单一文案**。
 *
 * @param progress 完整进度快照（编排层每步发一份；完成/失败时携带最后快照，失败时
 *   RUNNING 步骤已由 ViewModel 改标 FAILED）。null = 无进度可展示（防御兜底，此时
 *   不渲染 stepper 区，仅显示结果/错误区，仍留在本页版式）。
 * @param summary 完成时的收取汇总（非空即渲染结果区；收取中/失败为 null）。
 * @param errorMessage 失败时的错误文案（非空即渲染错误区；收取中/完成为 null）。
 * @param running true = 收取中（不显示底部按钮区）。
 * @param onRetry 「重新选择」回调（原地复位 Idle，可再选文件/再收取）。
 * @param onBack 「返回」回调（`popBackStack` 回入口页）。
 */
@Composable
fun MailImportContent(
    progress: MailImportProgress?,
    running: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    summary: MailImportSummary? = null,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // —— 上部内容区：独占剩余高度的滚动容器（进度 / 结果 / 错误共用，零版式替换） ——
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (progress != null) {
                MailImportProgressSection(progress = progress, running = running)
            }

            // —— 结果区：完成后原地追加在 stepper 下方（摘要卡 + 失败明细卡） ——
            summary?.let { value ->
                MailImportSummarySection(summary = value)
            }

            // —— 错误区：失败后原地追加（替代原整页 EmptyState，版式不变） ——
            errorMessage?.let { message ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            text = "收取失败",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // —— 底部按钮区：钉在屏幕底部、不参与滚动；仅收取结束后出现（完成或失败均可
        //     重试/返回）；running = true 时不渲染，内容区独占全高 ——
        if (!running) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("重新选择")
                }
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("返回")
                }
            }
        }
    }
}

/**
 * 进度区（私有）：多邮箱头部 + 5 步 stepper + 第 4 步展开区 + 逐封结果行卡。
 * 收取中与结束后渲染同一份结构（仅展开区随 `running` 收起），保证零版式替换。
 */
@Composable
private fun MailImportProgressSection(
    progress: MailImportProgress,
    running: Boolean,
) {
    // —— 多邮箱：当前处理到第几个邮箱 ——
    if (progress.accountCount > 1) {
        progress.accountEmail?.let { email ->
            Text(
                text = "邮箱 ${progress.accountIndex}/${progress.accountCount} · $email",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // —— 竖向 stepper：每步一行 ——
    progress.steps.forEach { state ->
        MailImportStepRow(state)
    }

    // —— 第 4 步展开区：当前封 + 子步骤（仅收取中且已进入处理阶段） ——
    val processState = progress.steps.firstOrNull { it.step == MailImportStep.PROCESS }
    if (running && processState?.status == MailImportStepStatus.RUNNING && progress.mailCount > 0) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "第 ${progress.mailIndex}/${progress.mailCount} 封 · ${progress.currentSubject.orEmpty()}",
            style = MaterialTheme.typography.bodyMedium,
        )
        progress.currentItemStep?.let { step ->
            val label = MailImportPresenter.itemStepLabel(step)
            val text = if (step == MailItemStep.CRACK && progress.crackPercent != null) {
                "$label · 口令穷举 ${progress.crackPercent}%"
            } else {
                label
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // —— 逐封结果行（跨账号累积，限高可滚动） ——
    if (progress.itemLines.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                progress.itemLines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * 结果区（私有，原结果页摘要卡 + 失败明细卡搬入同页）：[MailImportPresenter.present]
 * 渲染一句话摘要 + 计数；有失败邮件时附逐封失败明细卡（无失败不显示）。
 */
@Composable
private fun MailImportSummarySection(summary: MailImportSummary) {
    val display = remember(summary) { MailImportPresenter.present(summary) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = display.headline,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "新导入 ${display.importedCount} 条 · 已存在 ${display.existingCount} 条",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "成功 ${display.succeededMailCount} 封 · 失败 ${display.failedMailCount} 封",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (display.failureLines.isNotEmpty()) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(
                    text = "失败明细",
                    style = MaterialTheme.typography.titleSmall,
                )
                display.failureLines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * 单步一行：左侧状态图标（PENDING=`○`；RUNNING=小尺寸 [CircularProgressIndicator]；
 * DONE=`✓`；FAILED=`✗`）+ 中间中文步骤名 + 右侧 summary（有则显示）。
 *
 * **布局纪律（F-13 回归教训）**：`Row` 会**先量无权重子项**——若摘要不带权重，长摘要
 * （如「命中 3 封（…），每种账单只取最近一封 → 待处理 K 封」）会把整行宽度吃光，把
 * 标签挤到近乎 0 宽、竖排成单字（真机 2026-10-09 复现）。故标签与摘要**都带权重**
 * （`LABEL_WEIGHT : SUMMARY_WEIGHT`），摘要 `TextAlign.End` 右对齐、超宽时只在自己
 * 那一列折行；短摘要的渲染与旧版逐像素一致。
 */
@Composable
private fun MailImportStepRow(state: MailImportStepState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state.status) {
            MailImportStepStatus.PENDING -> Text(
                text = "○",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MailImportStepStatus.RUNNING -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            MailImportStepStatus.DONE -> Text(
                text = "✓",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            MailImportStepStatus.FAILED -> Text(
                text = "✗",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = MailImportPresenter.stepLabel(state.step),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(StepRowLabelWeight),
        )
        state.summary?.let { summary ->
            Spacer(Modifier.width(8.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(StepRowSummaryWeight),
            )
        }
    }
}

/** 步进行标签的权重份额（防长摘要挤压，见 [MailImportStepRow] KDoc）。 */
private const val StepRowLabelWeight = 1f

/** 步进行摘要的权重份额（> 标签，让长摘要换行时仍有最宽可用列）。 */
private const val StepRowSummaryWeight = 2.4f

/**
 * 多邮箱选择弹窗（F-13 多账号）：标题「选择邮箱」，逐行邮箱地址（单选，点击即开始
 * 收取该邮箱），确认位「全部收取」（一次传入全部 id），取消位「取消」。
 *
 * @param accounts 已配置账号列表
 * @param onSelect 单选某邮箱（传该账号 id）开始收取
 * @param onSelectAll 「全部收取」（全部 id 一次传入，按顺序处理）
 * @param onDismiss 「取消」/ 点击弹窗外关闭
 */
@Composable
fun MailChooseAccountDialog(
    accounts: List<MailAccount>,
    onSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择邮箱") },
        text = {
            Column {
                accounts.forEach { account ->
                    Text(
                        text = account.email,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(account.id) }
                            .padding(vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSelectAll) { Text("全部收取") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
