package com.minibook.ui.billimport

import com.minibook.domain.model.BillPlatform
import com.minibook.domain.model.MailBillItemResult
import com.minibook.domain.model.MailImportStep
import com.minibook.domain.model.MailImportStepState
import com.minibook.domain.model.MailImportStepStatus
import com.minibook.domain.model.MailImportSummary
import com.minibook.domain.model.MailItemFailure
import com.minibook.domain.model.MailItemStep
import com.minibook.domain.model.displayLabel
import com.minibook.domain.model.toResultLine

/**
 * 收取结果展示模型（纯数据，便于单测）。
 *
 * @property importedCount 新导入交易数
 * @property existingCount 已存在（去重）数
 * @property succeededMailCount 处理成功的邮件数
 * @property failedMailCount 处理失败的邮件数
 * @property headline 一句话摘要
 * @property failureLines 每封失败邮件的「平台 + 原因」描述（多邮箱来源时前缀邮箱地址）
 */
data class MailImportDisplay(
    val importedCount: Int,
    val existingCount: Int,
    val succeededMailCount: Int,
    val failedMailCount: Int,
    val headline: String,
    val failureLines: List<String>,
)

/**
 * 「一键收取」结果/进度 → 展示模型（F-13，纯函数、无 Android 依赖，便于 JVM 单测）。
 *
 * UI（[ImportScreen]/[MailImportContent]）只负责渲染，不在 Composable 里做映射。
 * 失败原因文案的**单一事实源**在 domain 层（[displayLabel]，编排层进度 `itemLines` 与
 * 本处共用同一份，避免两处漂移）；[failureLabel] 及各 label/line 函数均为穷尽 `when`
 * 或对穷尽实现的委托，新增枚举分支时编译器强制补齐。
 */
object MailImportPresenter {

    /**
     * 失败原因 → 用户可读文案（委托 domain 层 [displayLabel] 单一事实源，
     * `when` 穷尽 [MailItemFailure]，新增分支编译器强制补齐）。
     */
    fun failureLabel(failure: MailItemFailure): String = failure.displayLabel()

    /**
     * 导入步骤 → 中文名（`when` 穷尽 [MailImportStep]，靠编译器保证不漏）。
     */
    fun stepLabel(step: MailImportStep): String = when (step) {
        MailImportStep.CONNECT -> "连接邮箱"
        MailImportStep.FETCH -> "拉取邮件"
        MailImportStep.FILTER -> "筛选账单邮件"
        MailImportStep.PROCESS -> "处理账单邮件"
        MailImportStep.DONE -> "完成汇总"
    }

    /**
     * 单封子步骤 → 中文名（`when` 穷尽 [MailItemStep]，靠编译器保证不漏）。
     */
    fun itemStepLabel(step: MailItemStep): String = when (step) {
        MailItemStep.EXTRACT -> "提取链接或附件"
        MailItemStep.DOWNLOAD -> "下载账单"
        MailItemStep.INSPECT -> "探测加密方式"
        MailItemStep.CRACK -> "破解解压口令"
        MailItemStep.UNZIP -> "解压账单"
        MailItemStep.IMPORT -> "导入交易"
    }

    /**
     * 单步状态 → 一行文本（含状态语义；`when` 穷尽 [MailImportStepStatus]）：
     * 待执行 / 进行中 / 完成时附 summary / 失败时附 summary。
     */
    fun stepLine(state: MailImportStepState): String {
        val label = stepLabel(state.step)
        return when (state.status) {
            MailImportStepStatus.PENDING -> "$label · 待执行"
            MailImportStepStatus.RUNNING -> "$label · 进行中"
            MailImportStepStatus.DONE -> "$label · ${state.summary ?: "完成"}"
            MailImportStepStatus.FAILED ->
                "$label · 失败${state.summary?.let { "：$it" } ?: ""}"
        }
    }

    /**
     * 单封结果 → 一行结果文本（委托 domain 层 [toResultLine]：成功
     * 「✅ 平台 · 主题 — 新导入 X 条 / 已存在 Y 条」；失败「❌ 平台 · 主题 — 失败原因」，
     * 失败原因经 [failureLabel] 映射）。
     */
    fun mailItemLine(result: MailBillItemResult): String = result.toResultLine()

    /**
     * 汇总 → 展示模型：计数取自 [MailImportSummary] 派生属性，失败逐封生成
     * `「平台 · 邮件主题 — 原因」` 行；**多邮箱来源**（不同 [MailBillItemResult.accountEmail]
     * 多于一个）时每行前缀 `邮箱地址 · ` 以区分来源；无失败时
     * [MailImportDisplay.failureLines] 为空。
     */
    fun present(summary: MailImportSummary): MailImportDisplay {
        val multipleAccounts = summary.items.map { it.accountEmail }.distinct().size > 1
        val failureLines = summary.items
            .filter { it.failure != null }
            .map { item ->
                val prefix = if (multipleAccounts) "${item.accountEmail} · " else ""
                "$prefix${platformLabel(item.platform)} · ${item.subject} — ${failureLabel(item.failure!!)}"
            }
        return MailImportDisplay(
            importedCount = summary.totalImported,
            existingCount = summary.totalExisting,
            succeededMailCount = summary.succeededCount,
            failedMailCount = summary.failedCount,
            headline = "本次收取：新导入 ${summary.totalImported} 条，" +
                "已存在 ${summary.totalExisting} 条；" +
                "成功 ${summary.succeededCount} 封，失败 ${summary.failedCount} 封",
            failureLines = failureLines,
        )
    }

    /** 平台展示名：ALIPAY→「支付宝」、WECHAT→「微信」。 */
    private fun platformLabel(platform: BillPlatform): String = when (platform) {
        BillPlatform.ALIPAY -> "支付宝"
        BillPlatform.WECHAT -> "微信"
    }
}
