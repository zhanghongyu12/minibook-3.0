package com.minibook.ui.billimport

import com.minibook.domain.model.BalanceGap
import com.minibook.domain.model.SmsImportSummary
import com.minibook.domain.model.SmsPendingItem
import com.minibook.domain.model.SmsScanMode
import com.minibook.domain.model.SmsScanPreview
import com.minibook.domain.usecase.sms.BalanceChainAuditor
import com.minibook.ui.components.chineseLabel
import com.minibook.util.MoneyFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * F-14 短信导入 stepper 步骤（UI 层投影）。
 *
 * 与 F-13 [com.minibook.domain.model.MailImportStep] 的区别：F-13 的步骤模型在 domain 层
 * （编排层逐步推进），而短信导入由 `SmsBillImportUseCase.scan` 一次调用完成「读取 → 识别 →
 * 去重」三段（**不改 domain**），故步骤是 UI 层的展示投影，由 [SmsImportPresenter.stepStates]
 * 按 [SmsImportPhase] 推导。
 */
enum class SmsImportStep(val label: String) {
    READ("读取短信"),
    PARSE("识别账单"),
    DEDUP("去重比对"),
    WRITE("写入"),
    SUMMARY("汇总"),
}

/** 单步状态（镜像 F-13 `MailImportStepStatus` 的四态）。 */
enum class SmsImportStepStatus { PENDING, RUNNING, DONE, FAILED }

/** 单步状态快照（[summary] 仅在 DONE 时渲染）。 */
data class SmsImportStepState(
    val step: SmsImportStep,
    val status: SmsImportStepStatus,
    val summary: String? = null,
)

/**
 * 短信导入页流程阶段（[com.minibook.ui.billimport.ImportUiState] 的短信各态 → 本枚举映射）。
 *
 * SCANNING / IMPORTING 为进行中（不显示底部按钮），其余为终态（显示底部按钮区）。
 */
enum class SmsImportPhase { SCANNING, PREVIEW, IMPORTING, DONE, ERROR }

/**
 * 短信导入展示模型（纯函数、无 Android 依赖，便于 JVM 单测）。
 *
 * 隐私纪律（DEC-026 / UI §11.5）：**所有文案只使用脱敏摘要与自建 `description`**，
 * 不含短信原文、完整卡号与余额；余额链只用 domain 提供的
 * [BalanceChainAuditor.headline]（只有缺口计数与时间区间，无余额数值）。
 */
object SmsImportPresenter {

    /** 逐条结果行上限（防极长列表拖垮渲染；超出部分以省略行收口）。 */
    const val MAX_LINES = 100

    /** 逐条结果行省略提示（超过 [MAX_LINES] 时追加）。 */
    const val OMITTED_HINT = "…（其余结果行已省略）"

    /** 一次 `scan` 调用覆盖的步骤：读取 + 识别 + 去重（同时 RUNNING，见 [stepStates]）。 */
    private val scanningSteps = setOf(SmsImportStep.READ, SmsImportStep.PARSE, SmsImportStep.DEDUP)

    /**
     * 按阶段推导 5 步状态。
     *
     * - [SmsImportPhase.SCANNING]：`scan` 一次调用内含「读取 + 识别 + 去重」，故前三步同时
     *   RUNNING（比假装顺序推进更如实）；
     * - [SmsImportPhase.ERROR]：以 [failedStep] 为界，之前的步骤 DONE、该步 FAILED、之后 PENDING。
     */
    fun stepStates(
        phase: SmsImportPhase,
        preview: SmsScanPreview?,
        summary: SmsImportSummary?,
        failedStep: SmsImportStep?,
    ): List<SmsImportStepState> {
        val summaries: Map<SmsImportStep, String?> = mapOf(
            SmsImportStep.READ to preview?.let { "扫描 ${it.scanTotal} 条" },
            SmsImportStep.PARSE to preview?.let { "识别 ${it.parsedTotal} 条" },
            SmsImportStep.DEDUP to preview?.let { "重复/已存在 ${skipCount(it)} 条" },
            SmsImportStep.WRITE to summary?.let { "写入 ${it.imported} 笔" },
            SmsImportStep.SUMMARY to summary?.let { "新增 ${it.imported} 笔，跳过 ${it.existing + it.duplicates} 条" },
        )
        return SmsImportStep.entries.map { step ->
            val status = statusOf(phase, step, failedStep)
            SmsImportStepState(
                step = step,
                status = status,
                summary = summaries[step]?.takeIf { status == SmsImportStepStatus.DONE },
            )
        }
    }

    private fun statusOf(
        phase: SmsImportPhase,
        step: SmsImportStep,
        failedStep: SmsImportStep?,
    ): SmsImportStepStatus = when (phase) {
        SmsImportPhase.SCANNING ->
            if (step in scanningSteps) SmsImportStepStatus.RUNNING else SmsImportStepStatus.PENDING
        SmsImportPhase.PREVIEW ->
            if (step == SmsImportStep.WRITE || step == SmsImportStep.SUMMARY) {
                SmsImportStepStatus.PENDING
            } else {
                SmsImportStepStatus.DONE
            }
        SmsImportPhase.IMPORTING -> when (step) {
            SmsImportStep.WRITE -> SmsImportStepStatus.RUNNING
            SmsImportStep.SUMMARY -> SmsImportStepStatus.PENDING
            else -> SmsImportStepStatus.DONE
        }
        SmsImportPhase.DONE -> SmsImportStepStatus.DONE
        SmsImportPhase.ERROR -> {
            val failedIndex = failedStep?.let { SmsImportStep.entries.indexOf(it) } ?: -1
            val index = SmsImportStep.entries.indexOf(step)
            when {
                failedIndex < 0 -> SmsImportStepStatus.PENDING
                index < failedIndex -> SmsImportStepStatus.DONE
                index == failedIndex -> SmsImportStepStatus.FAILED
                else -> SmsImportStepStatus.PENDING
            }
        }
    }

    /**
     * 干跑预览 headline（UI §11.4 固定文案）：
     * `扫描 N 条 = 可导入 X + 未识别 Y + 非账单 Z + 已存在/重复 W`。
     *
     * W 含「同源已存在 + 跨源判重 + 疑似重复」三类（否则等式不闭合，[SmsScanPreview.parsedTotal] 口径）。
     */
    fun previewHeadline(preview: SmsScanPreview): String =
        "扫描 ${preview.scanTotal} 条 = 可导入 ${preview.importable.size}" +
            " + 未识别 ${preview.unrecognized.size}" +
            " + 非账单 ${preview.notBillReasons.size}" +
            " + 已存在/重复 ${skipCount(preview)}"

    /** 结果摘要一句话（写入完成后的结果区标题）。 */
    fun resultHeadline(summary: SmsImportSummary): String =
        "本次导入：新增 ${summary.imported} 笔，已存在 ${summary.existing} 条，" +
            "判重跳过 ${summary.duplicates} 条，疑似重复 ${summary.ambiguous} 条"

    /**
     * 余额链提示行（UI §11.4）：无缺口 → `余额链：无缺口`；有缺口 → 只用 domain 汇总
     * （缺口计数 + 时间区间），**不展示余额数值**。
     */
    fun balanceLine(gaps: List<BalanceGap>): String =
        BalanceChainAuditor.headline(gaps) ?: "余额链：无缺口"

    /**
     * 逐条结果行（脱敏）：可导入 ✅ / 已存在与判重跳过 ⏭ / 疑似重复 🔁 / 未识别 ⚠。
     * 超过 [MAX_LINES] 行时截断并追加 [OMITTED_HINT]。
     *
     * **「已看过」的未识别条目不在本列表**（2026-10-10 用户要求：扫描过的做标记，重扫不再
     * 重复啰嗦）—— 它们进 [seenLines] 的折叠区；`未识别 N` 计数仍按全量呈现（口径不变）。
     */
    fun lines(preview: SmsScanPreview): List<String> {
        val all = buildList {
            preview.importable.forEach { parsed ->
                add("✅ 可导入 ${parsed.row.description} · ${money(parsed.row.amountFen)} · ${parsed.row.date}")
            }
            preview.existing.forEach { parsed ->
                add(
                    "⏭ ${parsed.row.description} 与已有记录重复，已跳过" +
                        "（金额 ${money(parsed.row.amountFen)}，${parsed.row.date}）",
                )
            }
            preview.duplicates.forEach { hit ->
                add(
                    "⏭ ${hit.incoming.description} 与「${hit.matchedSource.chineseLabel}」记录重复，已跳过" +
                        "（金额 ${money(hit.incoming.amountFen)}，${hit.matchedDate}）",
                )
            }
            preview.ambiguous.forEach { hit ->
                add(
                    "🔁 ${hit.incoming.description} 存在多条同额同日期记录，请确认" +
                        "（金额 ${money(hit.incoming.amountFen)}，${hit.incoming.date}）",
                )
            }
            preview.unrecognized.filterNot { it.seen }.forEach { add(unrecognizedLine(it)) }
        }
        return cap(all)
    }

    /** 「此前已看过」的未识别行（折叠区内容；口径与 [lines] 里的 ⚠ 行完全一致）。 */
    fun seenLines(preview: SmsScanPreview): List<String> =
        cap(preview.unrecognized.filter { it.seen }.map(::unrecognizedLine))

    /** 单条未识别行（脱敏摘要 + 判定原因，不含原文以外信息）。 */
    fun unrecognizedLine(item: SmsPendingItem): String = "⚠ 未识别（${item.reason}）：${item.preview}"

    /**
     * 扫描方式说明行（2026-10-10 用户要求「全量 / 增量两种扫描」）：让用户随时知道本次数字是
     * 「全部历史」还是「只看新短信」，以及**增量时间节点**在哪。
     *
     * 用户二次口径（2026-10-10 13:4x）：「时间节点……不应该是最后一条短信的时间，应该是当前扫描的时间」
     * ⇒ 增量行同时给出「本次覆盖的起点（= 上次扫描时间）」与「起点已更新为本次扫描时间」。
     */
    fun scanModeLine(mode: SmsScanMode, sinceMs: Long, scannedAtMs: Long = 0L): String = when {
        mode == SmsScanMode.FULL -> "全量扫描：列出全部历史短信"
        sinceMs <= 0L && scannedAtMs > 0L ->
            "增量扫描：首次使用，起点已设为本次扫描时间 ${clock(scannedAtMs)}（历史短信请用「全量扫描」）"
        sinceMs <= 0L -> "增量扫描：首次使用已建立水位基线，历史短信请用「全量扫描」"
        scannedAtMs <= 0L -> "增量扫描：只看 ${clock(sinceMs)} 之后的新短信"
        else -> "增量扫描：本次覆盖 ${clock(sinceMs)} 之后的新短信，起点已更新为 ${clock(scannedAtMs)}"
    }

    /** 增量起点时刻（`MM-dd HH:mm`；只看时间戳，不含任何短信内容，DEC-026）。 */
    private fun clock(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(CLOCK_FORMAT)

    /** 行数上限截断（主列表与折叠区各自独立限流，避免折叠区把主列表挤爆）。 */
    private fun cap(all: List<String>): List<String> =
        if (all.size <= MAX_LINES) all else all.take(MAX_LINES) + OMITTED_HINT

    /**
     * 需人工处理的条数 = **未看过的**未识别条目 + 疑似重复（决定「查看待确认 N 条」的 N）。
     *
     * 「已看过」的未识别条目**不计入**（2026-10-10 用户二次反馈：「待确认的 1 条永远消除不掉」）——
     * 这个数字只应表达「有 N 件需要你决定的事」；历史遗留的未识别留在折叠区 / 待确认清单里备查
     * （不遗漏），但**不该每次都顶着一个数字**（不烦人）。注意与 [previewHeadline] 的 `未识别 N`
     * （全量统计，口径不变、`assertClosed()` 依赖它）是两回事。
     */
    fun pendingCount(preview: SmsScanPreview?): Int =
        preview?.let { it.unrecognized.count { !it.seen } + it.ambiguous.size } ?: 0

    /** 干跑预览 headline 的 W 项：已存在 + 判重跳过 + 疑似重复。 */
    private fun skipCount(preview: SmsScanPreview): Int =
        preview.existing.size + preview.duplicates.size + preview.ambiguous.size

    /** 金额文案统一走 [MoneyFormatter]（禁止手写元/分换算，DATABASE §9.3）。 */
    private fun money(fen: Long): String = MoneyFormatter.formatFen(fen)

    /** 增量起点时刻格式（纯展示，非解析路径；DEC-024 不受影响）。 */
    private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
}
