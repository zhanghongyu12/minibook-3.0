package com.minibook.domain.usecase.sms

import com.minibook.domain.model.SmsParseOutcome
import com.minibook.domain.model.SmsWhitelistEntry

/**
 * 白名单准入策略（F-14，设计 §3.8；DEC-025）。
 *
 * **准入四条件（全部满足才自动记账）**：① 发送方在白名单且该项开启；② 模板命中且置信度达标；
 * ③ 匹配器判定为新记录（无跨源重复）；④ 渠道允许自动（`SmsChannel.allowsAutoImport`，
 * 即支付宝/财付通/微信渠道**不自动写**）。
 *
 * **晋级**：手动确认 {@link AUTO_PROMOTE_THRESHOLD} 次且期间无纠错 → 自动开启；
 * **降级**：用户删除/修改某笔自动记录（纠错信号）→ 立即关闭并清零计数。
 * 纯函数、无 IO → JVM 可完整单测。
 */
object SmsWhitelistPolicy {

    /** 晋级阈值：手动确认 3 次（设计 §12 拍板记录 #4）。 */
    const val AUTO_PROMOTE_THRESHOLD = 3

    /** 准入判定结果。 */
    sealed interface AutoDecision {
        /** 允许免确认写入。 */
        data object AutoImport : AutoDecision

        /** 不自动写，进「待确认」并给出原因（原因文案由 UI 映射）。 */
        data class Pending(val reason: String) : AutoDecision
    }

    /**
     * 四条件判定（顺序即优先级：白名单 → 置信度 → 重复 → 渠道）。
     */
    fun evaluate(
        entry: SmsWhitelistEntry?,
        parsed: SmsParseOutcome.Parsed,
        decision: SmsMatchDecision,
    ): AutoDecision {
        if (entry == null || !entry.autoEnabled) {
            return AutoDecision.Pending(reason = "发送方未在白名单（或已关闭自动记账）")
        }
        if (parsed.confidence < SmsBillParser.AUTO_IMPORT_MIN_CONFIDENCE) {
            return AutoDecision.Pending(reason = "解析置信度不足（${parsed.confidence}）")
        }
        when (decision) {
            is SmsMatchDecision.Duplicate -> return AutoDecision.Pending(reason = "疑似与已有记录重复，待确认")
            is SmsMatchDecision.Ambiguous -> return AutoDecision.Pending(reason = "存在多条同额同日期记录，待确认")
            SmsMatchDecision.New -> Unit
        }
        if (!parsed.channel.allowsAutoImport) {
            return AutoDecision.Pending(reason = "渠道（${parsed.channel.value}）会被账单文件覆盖，不自动记账")
        }
        return AutoDecision.AutoImport
    }

    /**
     * 用户手动确认入账一次：计数 +1；达到阈值即开启自动。
     *
     * 已经达阈值但 `autoEnabled=false` 视为**用户主动关闭**，不因再次确认而自动重开。
     */
    fun afterManualConfirm(entry: SmsWhitelistEntry?): SmsWhitelistEntry {
        val current = entry ?: SmsWhitelistEntry(sender = "", autoEnabled = false, confirmedCount = 0)
        val alreadyPromoted = current.confirmedCount >= AUTO_PROMOTE_THRESHOLD
        val count = current.confirmedCount + 1
        val autoEnabled = if (alreadyPromoted) current.autoEnabled else count >= AUTO_PROMOTE_THRESHOLD
        return current.copy(confirmedCount = count, autoEnabled = autoEnabled)
    }

    /** 纠错信号（删除/修改自动记录）：立即关闭自动 + 计数清零。 */
    fun afterUserCorrection(entry: SmsWhitelistEntry?): SmsWhitelistEntry? =
        entry?.copy(autoEnabled = false, confirmedCount = 0)

    /** 记录一次自动写入失败（留痕，供设置页展示）。 */
    fun afterError(entry: SmsWhitelistEntry?, nowMs: Long): SmsWhitelistEntry? =
        entry?.copy(lastErrorAtMs = nowMs)
}
