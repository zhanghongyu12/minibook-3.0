package com.minibook.domain.model

/**
 * F-14 短信账单导入领域模型（设计 `docs/13_sms_bill_import.md` §3.3；DEC-025）。
 *
 * 纯 Kotlin：**不 import `android.*`**，可在 JVM 上完整单测（读取层 [SmsMessageRow] 是
 * Android `ContentResolver` 与领域层之间的唯一数据形态）。
 */

/**
 * 短信渠道归类 —— 决定「是否允许自动记账」（设计 §3.6 渠道抑制）。
 *
 * 经支付宝/财付通/微信渠道的消费**必然**会被 F-12/F-13 的账单文件覆盖，
 * 因此默认只提醒、不自动写入，避免"先自动写一条、导账单又来一条"。
 */
enum class SmsChannel(val value: String) {
    BANK_DIRECT("bank_direct"),
    ALIPAY("alipay"),
    WECHAT("wechat"),
    OTHER("other"),
    ;

    /** 是否允许进入 L2 白名单自动记账（渠道抑制口径，设计 §3.6）。 */
    val allowsAutoImport: Boolean get() = this == BANK_DIRECT
}

/** 短信存储类型常量（`content://sms.type`）。 */
object SmsMessageType {
    const val INBOX = 1
    const val SENT = 2
    const val DRAFT = 3
    const val OUTBOX = 4
    const val FAILED = 5
    const val QUEUED = 6

    /** 扫描范围：1..6 全覆盖（收件箱为主，其余标注来源）。 */
    fun isScannable(type: Int): Boolean = type in INBOX..QUEUED
}

/**
 * 读取层输出（Android 边界 → 领域层）：一条短信的可判定字段。
 *
 * [contentHash] 用于「`_id` 被系统复用」与同源幂等（设计 §3.2 / DEC-025）；
 * **原文 [body] 只在内存中短暂持有**，解析结束后除未识别留痕外不落库（DEC-026）。
 */
data class SmsMessageRow(
    val id: Long,
    val address: String,
    val receivedAtMs: Long,
    val type: Int,
    val subId: Int?,
    val body: String,
    val contentHash: String,
)

/** 单条短信的解析三态（设计 §3.3）。 */
sealed interface SmsParseOutcome {

    /**
     * 解析成功：[row] 为统一 [ParsedBillRow]（`externalId` 已带 `sms:` 前缀），
     * [channel] 决定渠道抑制，[balanceFen] 供余额链校验，[confidence] 为模板命中强度（0~3）。
     */
    data class Parsed(
        val row: ParsedBillRow,
        val channel: SmsChannel,
        val cardTail: String?,
        val balanceFen: Long?,
        val confidence: Int,
        val sender: String,
        val receivedAtMs: Long,
        val contentHash: String,
    ) : SmsParseOutcome

    /** 疑似账单但抽不出可信字段（金额缺失/方向不明/时间为空）→ 进「未识别」清单，**不静默丢弃**。 */
    data class Unrecognized(val reason: String) : SmsParseOutcome

    /** 判定非账单（验证码/营销/快递/运营商通知）→ 只记判定依据，不留原文。 */
    data class NotBill(val reason: String) : SmsParseOutcome
}

/** 待确认 / 未识别条目（UI 展示模型；[preview] 为脱敏摘要，非原文）。 */
data class SmsPendingItem(
    val id: Long,
    val sender: String,
    val receivedAtMs: Long,
    val preview: String,
    val reason: String,
    /**
     * 「已看过」标记（2026-10-10 用户要求：扫描过的做标记，重扫不再重复啰嗦）。
     *
     * 由 `scan()` 按「未识别已看水位」（短信设置 DataStore 里的一个时间戳）判定：
     * `receivedAtMs <= 水位` → 该条在上次清单里已经展示过 ⇒ UI 折叠进「此前已看过 N 条」。
     * **只影响展示分组，不影响任何计数**（`未识别 N` 仍按全量计，闭合断言不变）；
     * 也不落库、不记内容/号码（DEC-026），短信本体始终在系统收件箱里可再扫描找回。
     */
    val seen: Boolean = false,
)

/** 跨源判重命中留痕（设计 §3.6：永不自动删，只跳过并记录依据）。 */
data class SmsDuplicateHit(
    val incoming: ParsedBillRow,
    val matchedTransactionId: String,
    val matchedSource: TransactionSource,
    val matchedDate: String,
    val reason: String,
)

/** 余额链缺口（设计 §3.10；仅作提示，不作入库门禁）。 */
data class BalanceGap(
    val cardTail: String?,
    val fromLabel: String,
    val toLabel: String,
    val detail: String,
)

/** 白名单条目（L2 自动记账准入；设计 §3.8）。 */
data class SmsWhitelistEntry(
    val sender: String,
    val autoEnabled: Boolean,
    val confirmedCount: Int,
    val lastErrorAtMs: Long? = null,
)

/**
 * 短信导入汇总（设计 §3.3）：**两条计数闭合不变量**在构造时断言，任何口径漂移立即失败。
 *
 * - `scanTotal = parsed + unrecognized + notBill`
 * - `parsed    = imported + existing + duplicates + ambiguous`
 *
 * [ambiguous]：跨源候选 ≥2 的「疑似重复」（不自动写、进人工确认）；它属于已解析但未入库，
 * 必须在闭合式里显式占位，否则口径会静默漂移。
 */
data class SmsImportSummary(
    val scanTotal: Int = 0,
    val parsed: Int = 0,
    val imported: Int = 0,
    val existing: Int = 0,
    val duplicates: Int = 0,
    val ambiguous: Int = 0,
    val unrecognized: Int = 0,
    val notBill: Int = 0,
    val balanceGaps: List<BalanceGap> = emptyList(),
) {
    init {
        require(scanTotal == parsed + unrecognized + notBill) {
            "短信扫描计数不闭合：scanTotal=$scanTotal != parsed=$parsed + unrecognized=$unrecognized + notBill=$notBill"
        }
        require(parsed == imported + existing + duplicates + ambiguous) {
            "短信解析计数不闭合：parsed=$parsed != imported=$imported + existing=$existing + " +
                "duplicates=$duplicates + ambiguous=$ambiguous"
        }
    }
}

/** 跨源多候选（疑似重复，人工确认；**不自动写、不自动删**）。 */
data class SmsAmbiguousHit(
    val incoming: ParsedBillRow,
    val candidates: List<Transaction>,
)

/**
 * 干跑预览（scan → parse → 匹配，**零写入**）：UI 用它展示四类计数与逐条明细，
 * 用户确认后再调 `SmsBillImportUseCase.importConfirmed`。
 */
/**
 * 短信扫描方式（2026-10-10 用户要求：「做两种扫描方式，一种全量扫描，一种增量扫描，
 * 比如我 12:00 扫描过了，增量只扫描 12:00 以后的，只有全量的才全部扫描出来」）。
 *
 * - [INCREMENTAL]：只读**上次扫描水位之后**的新短信（严格 `date > 水位`，不走补扫的 3 天重叠窗口）；
 *   扫描完即推进水位 ⇒ 下次增量不会再把看过的翻出来。首次（无水位）只立基线、不回灌历史。
 * - [FULL]：读全部历史（受 [com.minibook.data.local.sms.SmsBillReader.MAX_MESSAGES] 上限约束），
 *   **不动水位** —— 历史永远可被全量找回，这是「不遗漏」的最后一层。
 */
enum class SmsScanMode { INCREMENTAL, FULL }

data class SmsScanPreview(
    val scanTotal: Int,
    val importable: List<SmsParseOutcome.Parsed>,
    val existing: List<SmsParseOutcome.Parsed>,
    val duplicates: List<SmsDuplicateHit>,
    val ambiguous: List<SmsAmbiguousHit>,
    val unrecognized: List<SmsPendingItem>,
    val notBillReasons: List<String>,
    val balanceGaps: List<BalanceGap> = emptyList(),
    /** 本次扫描方式（默认 [SmsScanMode.FULL]：既有构造点语义不变）。 */
    val scanMode: SmsScanMode = SmsScanMode.FULL,
    /** 本次**增量**覆盖的起点（= 上次扫描的时间节点）；0 = 全量，或增量首次（不覆盖历史）。 */
    val sinceMs: Long = 0L,
    /**
     * 本次扫描的**墙钟时间**（增量写入的新时间节点；全量不动水位 ⇒ 0）。
     *
     * 用户口径（2026-10-10）：「增量扫描的时间节点……应该是当前扫描的时间」，不是最后一条短信的时间。
     */
    val scannedAtMs: Long = 0L,
) {
    /** 已解析条数（= 可导入 + 同源已存在 + 跨源判重 + 疑似重复）。 */
    val parsedTotal: Int get() = importable.size + existing.size + duplicates.size + ambiguous.size

    /** 计数闭合断言（设计 G1）：扫描总数 = 已解析 + 未识别 + 非账单。 */
    fun assertClosed() {
        require(scanTotal == parsedTotal + unrecognized.size + notBillReasons.size) {
            "短信扫描计数不闭合：scanTotal=$scanTotal != parsed=$parsedTotal + " +
                "unrecognized=${unrecognized.size} + notBill=${notBillReasons.size}"
        }
    }
}
