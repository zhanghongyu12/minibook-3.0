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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minibook.domain.model.SmsImportSummary
import com.minibook.domain.model.SmsScanPreview
import com.minibook.ui.components.MiniBookIcons

/**
 * 「读取短信账单」流程 + 结果**同页合一**内容区（F-14，镜像 F-13 [MailImportContent]）：
 * 进行中（SCANNING/IMPORTING）只推 stepper 不显示按钮；干跑预览（PREVIEW，零写入）在
 * stepper 下方原地追加四类计数 + 余额链 + 逐条结果行（✅/⏭/🔁/⚠）与钉底「确认写入 N 笔」；
 * 完成后原地追加结果摘要卡、失败原地追加错误卡（**零版式替换**）。预览/结果有待确认条目时
 * 额外出现「查看待确认 K 条」（K = 未看过的未识别 + 疑似重复；全部已看过时退化为不带数字的
 * 「查看未识别 / 重复记录」，2026-10-10 用户二次反馈：数字不该永远消不掉）。
 *
 * 整页为**单一 `Column`**：上部内容区独占 `weight(1f)` 的 `verticalScroll`，底部按钮区钉在屏幕
 * 底部不参与滚动（与 F-13 布局纪律一致）。隐私（DEC-026）：只渲染 [SmsImportPresenter] 产出的
 * 脱敏文案，**不展示短信原文、完整卡号与余额**。
 * @param preview 干跑预览（扫描完成后非空）。@param summary 写入汇总（DONE 时非空）。
 * @param errorMessage 失败文案（ERROR 时非空）。@param failedStep 失败步骤（stepper 标 ✗）。
 * @param onConfirm 「确认写入 N 笔」（仅 PREVIEW）。@param onRescan 「重新扫描」（终态）。
 * @param onViewPending 「查看待确认 K 条」/「查看未识别 / 重复记录」（PREVIEW/DONE 且存在未识别或疑似重复记录时）。@param onBack 返回。
 * @param onSenderImportToggle 「导入来源」勾选（docs/15 第 2 条）：null = 不展示来源区
 *   （例如未接入该能力的老调用方）；非空则预览页出现「来源：已选 X / Y 家」入口，勾选立即生效并重扫。
 */
@Composable
fun SmsImportContent(
    phase: SmsImportPhase,
    preview: SmsScanPreview?,
    onConfirm: () -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit,
    summary: SmsImportSummary? = null,
    errorMessage: String? = null,
    failedStep: SmsImportStep? = null,
    onViewPending: (() -> Unit)? = null,
    onSenderImportToggle: ((String, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val running = phase == SmsImportPhase.SCANNING || phase == SmsImportPhase.IMPORTING
    val steps = remember(phase, preview, summary, failedStep) {
        SmsImportPresenter.stepStates(phase, preview, summary, failedStep)
    }
    val pendingCount = SmsImportPresenter.pendingCount(preview)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // —— 上部内容区：独占剩余高度的滚动容器（进度 / 预览 / 结果 / 错误共用，零版式替换） ——
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            steps.forEach { state -> SmsImportStepRow(state) }

            // —— 扫描方式说明（2026-10-10 两种扫描方式）：有扫描结果才展示（进行中还没有起点） ——
            preview?.let { p ->
                Text(
                    text = SmsImportPresenter.scanModeLine(p.scanMode, p.sinceMs, p.scannedAtMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // —— 导入来源（docs/15 第 2 条，2026-10-13）：多家银行只导入指定的一家 ——
            if (preview != null && preview.senders.isNotEmpty() && onSenderImportToggle != null) {
                SmsSenderSection(preview = preview, onToggle = onSenderImportToggle)
            }

            // —— 干跑预览区（零写入）：四类计数 + 余额链 + 逐条结果行 ——
            if (phase == SmsImportPhase.PREVIEW && preview != null) {
                SmsPreviewSection(preview)
            }
            // —— 结果区 / 错误区：完成后均为原地追加（不切换版式） ——
            summary?.let { value -> SmsResultSection(summary = value, pendingCount = pendingCount) }
            errorMessage?.let { message -> SmsErrorSection(message) }
        }

        // —— 底部按钮区：钉在屏幕底部、不参与滚动；进行中不渲染（内容区独占全高） ——
        if (!running) {
            Spacer(Modifier.height(16.dp))
            // 待确认入口：有记录才给（不遗漏：记录必须可找到）；数字只报「需要处理的」——
            // 全部已看过时数字归零、退化为不带数字的入口（2026-10-10 用户二次反馈）。
            val hasPendingRecords = (preview?.unrecognized?.isNotEmpty() == true) ||
                (preview?.ambiguous?.isNotEmpty() == true)
            if (hasPendingRecords && onViewPending != null && phase != SmsImportPhase.ERROR) {
                OutlinedButton(
                    onClick = onViewPending,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (pendingCount > 0) "查看待确认 $pendingCount 条" else "查看未识别 / 重复记录")
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onRescan,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("重新扫描")
                }
                if (phase == SmsImportPhase.PREVIEW) {
                    val importableCount = preview?.importable?.size ?: 0
                    Button(
                        onClick = onConfirm,
                        enabled = importableCount > 0,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("确认写入 $importableCount 笔")
                    }
                } else {
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
}

/** 干跑预览区（私有）：计数 headline + 余额链提示 + 逐条结果行（限高可滚动）。 */
@Composable
private fun SmsPreviewSection(preview: SmsScanPreview) {
    val headline = remember(preview) { SmsImportPresenter.previewHeadline(preview) }
    val balanceLine = remember(preview) { SmsImportPresenter.balanceLine(preview.balanceGaps) }
    val lines = remember(preview) { SmsImportPresenter.lines(preview) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "预览（未写入）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(text = headline, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = balanceLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (lines.isNotEmpty()) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                lines.forEach { line ->
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

    // 「此前已看过」（2026-10-10 用户要求：扫描过的做标记，重扫不再重复啰嗦）：
    // 默认折成一行；展开后仍可逐条看（不丢件，手工记一笔在待确认清单页），计数仍按全量在 headline。
    val seenLines = remember(preview) { SmsImportPresenter.seenLines(preview) }
    if (seenLines.isNotEmpty()) {
        var expanded by remember(preview) { mutableStateOf(false) }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = if (expanded) {
                        "▾ 此前已看过 ${seenLines.size} 条（点击收起）"
                    } else {
                        "▸ 此前已看过 ${seenLines.size} 条（点击展开）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(vertical = 2.dp),
                )
                if (expanded) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        seenLines.forEach { line ->
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
    }
}

/** 结果区（私有）：一句话摘要 + 四类计数 + 待确认引导。 */
@Composable
private fun SmsResultSection(summary: SmsImportSummary, pendingCount: Int) {
    val headline = remember(summary) { SmsImportPresenter.resultHeadline(summary) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(text = "导入完成", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = SmsImportPresenter.balanceLine(summary.balanceGaps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pendingCount > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "另有 $pendingCount 条待确认（未识别/疑似重复），请人工处理。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 错误区（私有）：失败标题 + 文案（不回 EmptyState 整页版式）。 */
@Composable
private fun SmsErrorSection(message: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "读取失败",
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

/**
 * 单步一行：状态图标（PENDING=`○`/RUNNING=小尺寸 [CircularProgressIndicator]/DONE=`✓`/FAILED=`✗`）
 * + 中文步骤名 + 右侧 summary。**布局纪律（沿用 F-13 回归教训）**：标签与摘要都带权重，
 * 摘要 `TextAlign.End`；摘要不带权重会把标签挤成竖排单字。
 */
@Composable
private fun SmsImportStepRow(state: SmsImportStepState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state.status) {
            SmsImportStepStatus.PENDING -> Text(
                text = "○",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SmsImportStepStatus.RUNNING -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            SmsImportStepStatus.DONE -> Text(
                text = "✓",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            SmsImportStepStatus.FAILED -> Text(
                text = "✗",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = state.step.label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(SmsStepLabelWeight),
        )
        state.summary?.let { summary ->
            Spacer(Modifier.width(8.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(SmsStepSummaryWeight),
            )
        }
    }
}

/** 步进行标签/摘要权重份额（防长摘要挤压标签，见 [SmsImportStepRow] KDoc）。 */
private const val SmsStepLabelWeight = 1f
private const val SmsStepSummaryWeight = 2.4f

/**
 * 「导入来源」区（docs/15 第 2 条，2026-10-13 用户口径「多家银行只导入指定的一家」）。
 *
 * 一行摘要（「已选 X / Y 家」）+ 点击弹勾选对话框；勾选**立即生效**（[onToggle] 由 ViewModel
 * 落库并重扫，用户当场看到该来源的短信从「可导入」变成「非账单·来源未勾选」）。
 */
@Composable
private fun SmsSenderSection(preview: SmsScanPreview, onToggle: (String, Boolean) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val senders = preview.senders
    val enabledCount = senders.count { it.importEnabled }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDialog = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "导入来源",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "已选 $enabledCount / ${senders.size} 家",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = MiniBookIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDialog) {
        SmsSenderDialog(preview = preview, onToggle = onToggle, onDismiss = { showDialog = false })
    }
}

/**
 * 来源勾选对话框：每家一行（机构名优先、否则号码）+ 账单/总数 + 勾选框；点「完成」关闭即生效。
 *
 * 文案口径：**不删除短信、不删除已入账的交易**；关掉的来源只是「下次扫描不计入账单」。
 */
@Composable
private fun SmsSenderDialog(
    preview: SmsScanPreview,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入来源") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "只导入勾选的发送方。未勾选来源的短信不计入账单（显示为「非账单」），" +
                        "不会删除任何已入账的交易。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                preview.senders.forEach { s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(s.sender, !s.importEnabled) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = s.importEnabled,
                            onCheckedChange = { onToggle(s.sender, it) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = s.institution ?: s.sender,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                            Text(
                                text = if (s.institution != null) s.sender else "未知机构",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${s.billCount} 笔账单 · 共 ${s.totalCount} 条",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}
