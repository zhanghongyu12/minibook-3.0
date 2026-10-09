package com.minibook.domain.usecase.mail

import com.minibook.domain.model.BillPlatform
import com.minibook.domain.model.MailBillMail

/**
 * 「每种账单只取最近一封」的选取结果（F-13）。
 *
 * @property kept 待处理邮件（每个 [BillPlatform] 各一封，保持输入顺序）
 * @property skipped 因同平台已有更新账单而**不处理**的邮件（保持输入顺序）
 */
internal data class MailBillSelection(
    val kept: List<MailBillMail>,
    val skipped: List<MailBillMail>,
) {
    /** 命中总数（= kept + skipped）：FILTER 步摘要与汇总里的「命中 N 封」。 */
    val hitCount: Int get() = kept.size + skipped.size
}

/**
 * 账单邮件选取器（F-13，纯 Kotlin，JVM 可单测）。
 *
 * **口径：每种账单只取最近一封**。[BillPlatform] 本身就是「账单种类」——支付宝只对应
 * 「支付宝交易流水明细」、微信只对应「微信支付-账单流水文件」（判定规则见
 * [com.minibook.data.local.mail.MailBillMatcher]），故按平台分组即可，不存在更细的子类。
 *
 * 同一平台内保留 `sentDateMillis` 最大者；**同毫秒并列时按主题字典序取大者**，避免结果
 * 依赖输入顺序。`kept` / `skipped` 均保持输入顺序，使「第 i/K 封」的展示顺序与拉取顺序一致。
 *
 * 为什么只取最新：同类账单的覆盖区间相互重叠（如支付宝自定义区间流水），旧邮件只会重复
 * 导入重叠区间，并额外付出一次口令穷举耗时。本步**只做选取，不删除任何已导入数据**
 * ——历史行仍留在账本里。
 */
internal object MailBillSelector {

    /**
     * 按平台分组选出每类最新一封。
     *
     * @return [MailBillSelection]；输入为空时两侧皆空（不发跳过行）
     */
    fun select(mails: List<MailBillMail>): MailBillSelection {
        if (mails.isEmpty()) return MailBillSelection(emptyList(), emptyList())

        val newestByPlatform = HashMap<BillPlatform, MailBillMail>()
        for (mail in mails) {
            val current = newestByPlatform[mail.header.platform]
            if (current == null || isNewerThan(mail, current)) {
                newestByPlatform[mail.header.platform] = mail
            }
        }

        val kept = ArrayList<MailBillMail>(newestByPlatform.size)
        val skipped = ArrayList<MailBillMail>()
        for (mail in mails) {
            // 身份比较：只有被选中的那一封实例进 kept
            if (newestByPlatform[mail.header.platform] === mail) kept += mail else skipped += mail
        }
        return MailBillSelection(kept = kept, skipped = skipped)
    }

    /** 是否比 [other] 更新：先比发送时间，同毫秒再比主题（字典序更大者为新）。 */
    private fun isNewerThan(mail: MailBillMail, other: MailBillMail): Boolean {
        val time = mail.header.sentDateMillis
        val otherTime = other.header.sentDateMillis
        if (time != otherTime) return time > otherTime
        return mail.header.subject > other.header.subject
    }
}
