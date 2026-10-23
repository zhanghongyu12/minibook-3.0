package com.minibook.domain.usecase.category

/**
 * 分类规则「核心词」归一化与匹配强度（TASK-084，2026-10-13；算法与取舍见
 * `docs/19_category_rule_normalization.md`）。
 *
 * **问题**（用户需求①）：同一条规则换个写法 / 换个门店就对不上。例：
 * `罗森(北京)有限公司朝阳门店` 学到规则后，`罗森(北京)有限公司房山店`、`罗森(北京)有限公司北京店`
 * 都匹配不上，于是又落「其他」。
 *
 * **做法**（只剥安全词类，绝不猜语义）：
 * 1. 去空白、全角转半角、统一小写；
 * 2. 剥括号内容（`(北京)`、`（朝阳门店）`）；
 * 3. 剥企业后缀（有限公司 / 股份有限公司 / 分公司 / 子公司 / 集团）与门店后缀
 *    （门店 / 分店 / 旗舰店 / 专营店 / 直营店 / 连锁店 / 营业部 / 办事处）；
 * 4. 三态匹配强度（[MatchStrength]）：STRONG 才允许参与 L2 自动记账，WEAK 只给手动导入预览出建议。
 *
 * **为什么不一刀切放宽**：假阳性会**静默写错账**（优先级①「不写错」压过「少打扰」），所以
 * 需要「猜」的匹配（公共前缀/归一化包含）只在**看得见、改得动**的预览里出现。
 *
 * **已知局限**（诚实记账）：公共前缀会把 `中国移动` 与 `中国联通` 判成 WEAK（公共前缀「中国」），
 * 因此 WEAK 只出建议；学习侧改存归一化核心词后，每遇到一个新门店/新写法最多再纠正一次。
 */
object CategoryRuleNormalizer {

    /** 规则词与对方全名（counterparty）的匹配强度。 */
    enum class MatchStrength {
        /** 不匹配。 */
        NONE,

        /** 可直接用于 L2 自动记账：原始串包含、归一化后相等、或归一化后核心词（≥3 字）被完整包含。 */
        STRONG,

        /** 只可用于手动导入预览的**建议**（需要「猜」，可能猜错）：归一化后公共前缀 ≥ 2 字。 */
        WEAK,
    }

    /**
     * 企业/门店后缀（**顺序敏感：长词在前**，否则「有限公司」会被「公司」先咬掉一半）。
     *
     * 刻意**不含**裸「店」「市」「区」「县」「中心」「广场」：它们会咬坏正常词
     * （如「如家酒店」→「如家酒」、「天猫超市」→「天猫超」），而误合并的代价是静默写错账。
     */
    private val SUFFIX_TOKENS = listOf(
        "有限责任公司",
        "股份有限公司",
        "有限公司",
        "分公司",
        "子公司",
        "旗舰店",
        "专营店",
        "直营店",
        "连锁店",
        "营业部",
        "办事处",
        "门店",
        "分店",
        "公司",
        "集团",
    )

    /** 归一化后参与「公共前缀」判定的最小长度（中文字符数）。 */
    private const val MIN_PREFIX = 2

    /** 归一化后「核心词被完整包含」算 STRONG 的最小长度（太短容易误命中）。 */
    private const val MIN_CONTAINED_CORE = 3

    /**
     * 归一化：去空白/全角→半角/小写 → 剥括号内容 → 剥企业门店后缀 → 去首尾标点。
     *
     * 纯函数、无副作用；输入 null / 空白 → `""`。**不保证可逆**（只用于匹配与规则词存储）。
     */
    fun normalize(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""

        // 1) 剥括号内容（中文/英文括号都按对处理；不配对的括号字符直接丢掉）。
        val withoutBrackets = StringBuilder(trimmed.length)
        var depth = 0
        for (ch in trimmed) {
            when (ch) {
                '（', '(' -> depth++
                '）', ')' -> if (depth > 0) depth--
                else -> if (depth == 0) withoutBrackets.append(ch)
            }
        }

        // 2) 全角→半角 + 去空白 + 小写。
        var text = toHalfWidth(withoutBrackets.toString())
        text = text.replace(WHITESPACE_REGEX, "").lowercase()

        // 3) 剥后缀（长词在前，全局替换；剩下的短词不会咬坏正常词）。
        for (token in SUFFIX_TOKENS) {
            if (text.contains(token)) text = text.replace(token, "")
        }

        // 4) 去首尾标点/符号。
        return text.trim(*TRIM_CHARS)
    }

    /**
     * 判定规则词 [ruleKeyword] 与对方全名 [counterparty] 的匹配强度（见 [MatchStrength]）。
     *
     * - 原始串包含规则词 = 旧行为，恒 STRONG（不改变既有语义）；空串、规则词短于 2 字且不包含 → NONE。
     */
    fun matchStrength(ruleKeyword: String, counterparty: String): MatchStrength {
        val keyword = ruleKeyword.trim()
        val other = counterparty.trim()
        if (keyword.isEmpty() || other.isEmpty()) return MatchStrength.NONE

        // ① 原始串包含：与引入本算法之前的行为完全一致（旧规则、旧库数据不迁移也照旧命中）。
        if (other.contains(keyword)) return MatchStrength.STRONG

        val normalizedKeyword = normalize(keyword)
        val normalizedOther = normalize(other)
        if (normalizedKeyword.isEmpty() || normalizedOther.isEmpty()) return MatchStrength.NONE

        // ② 归一化后相等（同商户不同写法/分店后缀差异的典型情形）。
        if (normalizedKeyword == normalizedOther) return MatchStrength.STRONG

        // ③ 归一化后核心词被完整包含（核心词要够长，避免「中」「国」这种碎片命中）。
        if (normalizedKeyword.length >= MIN_CONTAINED_CORE && normalizedOther.contains(normalizedKeyword)) {
            return MatchStrength.STRONG
        }

        // ④ 归一化后公共前缀（需要「猜」）：只作建议，不参与自动记账。
        if (commonPrefixLength(normalizedKeyword, normalizedOther) >= MIN_PREFIX) return MatchStrength.WEAK

        return MatchStrength.NONE
    }

    /** 两个串的公共前缀长度（按字符；已归一化后再调用）。 */
    fun commonPrefixLength(a: String, b: String): Int {
        val limit = minOf(a.length, b.length)
        var i = 0
        while (i < limit && a[i] == b[i]) i++
        return i
    }

    /** 用于排序的「核心词长度」：归一化后长度（空则退回原始长度），长的更具体、优先。 */
    fun coreLength(ruleKeyword: String): Int {
        val normalized = normalize(ruleKeyword)
        return if (normalized.isNotEmpty()) normalized.length else ruleKeyword.trim().length
    }

    /** 全角 ASCII（０-９Ａ-Ｚａ-ｚ）与全角空格 → 半角。 */
    private fun toHalfWidth(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(
                when (ch) {
                    '\u3000' -> ' '
                    in '\uFF01'..'\uFF5E' -> (ch.code - 0xFEE0).toChar()
                    else -> ch
                },
            )
        }
        return sb.toString()
    }

    private val WHITESPACE_REGEX = Regex("\\s+")

    private val TRIM_CHARS = charArrayOf(
        '-', '.', '·', '、', ',', '，', ':', '：', ';', '；', '"', '\'', '*', '_', '|', '/', '\\',
    )
}
