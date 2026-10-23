package com.minibook.domain.usecase.sms

import com.minibook.domain.model.SmsChannel
import com.minibook.domain.model.SmsMessageRow
import com.minibook.domain.model.SmsParseOutcome
import com.minibook.domain.model.TransactionType
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F-14 解析器单测（设计 §3.4 / §9；DEC-025）。
 *
 * ⚠️ **验证限制（如实）**：当前**无真实短信账单样本**（用户明确说明），以下全部为**合成 fixture**；
 * 解析准确率的真实验证须等样本到位后回归，真机验收须先向用户申请。
 * fixture 覆盖：命中 / 金额边界 / 方向歧义 / 格式漂移 / 非账单 / 渠道抑制 / 幂等键稳定性。
 */
class SmsBillParserTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    /** 2026-10-09 12:00 (Asia/Shanghai) 作为参考时刻，保证年份断言确定。 */
    private val refMs = 1_791_518_400_000L

    private fun message(
        body: String,
        address: String = "95526",
        receivedAtMs: Long = refMs,
    ) = SmsMessageRow(
        id = 1L,
        address = address,
        receivedAtMs = receivedAtMs,
        type = 1,
        subId = 1,
        body = body,
        contentHash = "hash-fixture",
    )

    private fun parse(
        body: String,
        address: String = "95526",
        receivedAtMs: Long = refMs,
    ): SmsParseOutcome = SmsBillParser.parse(
        message(body, address, receivedAtMs),
        SmsTemplateRegistry.match(address, body),
        zone,
    )

    private fun parsed(
        body: String,
        address: String = "95526",
        receivedAtMs: Long = refMs,
    ): SmsParseOutcome.Parsed {
        val outcome = parse(body, address, receivedAtMs)
        assertTrue("应解析成功，实际=$outcome", outcome is SmsParseOutcome.Parsed)
        return outcome as SmsParseOutcome.Parsed
    }

    @Test
    fun `银行消费短信：金额方向日期卡尾号余额全抽取`() {
        val p = parsed(
            "【某某银行】您账户3170于10月8日19:45通过网银在线支付100.00元。活期余额605.91元。对方户名:京东商城。",
        )
        assertEquals("2026-10-08", p.row.date)
        assertEquals(TransactionType.EXPENSE, p.row.type)
        assertEquals(10_000L, p.row.amountFen)
        assertEquals("3170", p.cardTail)
        assertEquals(60_591L, p.balanceFen)
        assertEquals(SmsChannel.BANK_DIRECT, p.channel)
        assertEquals("网银在线", p.row.paymentMethod)
        assertEquals("京东商城", p.row.counterparty)
        assertEquals(3, p.confidence)
        assertTrue("externalId 应带 sms: 前缀", p.row.externalId.startsWith("sms:"))
    }

    @Test
    fun `银行消费短信（正文无第三方渠道提示词）：渠道兜底银行直连且允许自动记账`() {
        // 回归测试（实测缺陷 D，2026-10-10）：正文无 银联/网银在线/代扣 等提示词时，
        // 兜底渠道若为 OTHER，则 allowsAutoImport=false → L2 白名单自动记账永不触发。
        val p = parsed("【中国银行】您尾号3170的储蓄卡10月10日9时15分支出人民币100.00元，余额605.91元。")
        assertEquals(SmsChannel.BANK_DIRECT, p.channel)
        assertTrue("银行直连渠道必须允许自动记账（L2 四条件之一）", p.channel.allowsAutoImport)
        assertEquals(TransactionType.EXPENSE, p.row.type)
        assertEquals(10_000L, p.row.amountFen)
        assertEquals(60_591L, p.balanceFen)
    }

    @Test
    fun `银行入账短信：判为收入`() {
        val p = parsed(
            "【某某银行】您账户3170于10月9日11:17收到一笔入账，金额500.00元。活期余额1105.91元。",
        )
        assertEquals(TransactionType.INCOME, p.row.type)
        assertEquals(50_000L, p.row.amountFen)
        assertEquals(110_591L, p.balanceFen)
        assertEquals("2026-10-09", p.row.date)
    }

    @Test
    fun `金额边界：千分位与两位小数`() {
        val p = parsed("【某某银行】您账户9999于10月8日10:00通过网银在线支付1,234.56元。余额5,000.00元。")
        assertEquals(123_456L, p.row.amountFen)
        assertEquals(500_000L, p.balanceFen)
    }

    @Test
    fun `金额边界：最小分`() {
        val p = parsed("【某某银行】您账户9999于10月8日10:00通过网银在线支付0.01元。余额1.00元。")
        assertEquals(1L, p.row.amountFen)
    }

    @Test
    fun `收支词同时出现：取更靠前者`() {
        val p = parsed("【某某银行】您账户3170于10月8日10:00入账后支付100.00元。余额900.00元。")
        assertEquals(TransactionType.INCOME, p.row.type)
    }

    @Test
    fun `无方向词且非验证码营销：判未识别而非误记`() {
        val outcome = parse("【某某银行】您账户3170于10月8日10:00发生一笔交易100.00元。余额900.00元。")
        assertTrue("应为 Unrecognized，实际=$outcome", outcome is SmsParseOutcome.Unrecognized)
    }

    @Test
    fun `格式漂移：金额缺失 → 未识别`() {
        val outcome = parse("【某某银行】您账户3170于10月8日19:45通过网银在线支付元。活期余额605.91元。")
        assertTrue("应为 Unrecognized，实际=$outcome", outcome is SmsParseOutcome.Unrecognized)
    }

    @Test
    fun `格式漂移：日期缺失 → 未识别`() {
        val outcome = parse("【某某银行】您账户3170通过网银在线支付100.00元。活期余额605.91元。")
        assertTrue("应为 Unrecognized，实际=$outcome", outcome is SmsParseOutcome.Unrecognized)
    }

    @Test
    fun `只有余额没有交易金额 → 未识别`() {
        val outcome = parse("【某某银行】您账户3170于10月8日19:45活期余额605.91元。")
        assertTrue(outcome is SmsParseOutcome.Unrecognized)
    }

    @Test
    fun `验证码短信：非账单`() {
        val outcome = parse("【某某银行】验证码123456，5分钟内有效，请勿泄露。")
        assertTrue("应为 NotBill，实际=$outcome", outcome is SmsParseOutcome.NotBill)
    }

    @Test
    fun `营销短信：非账单`() {
        val outcome = parse("【某某银行】尊敬的客户您好，信用卡分期优惠活动开始，回复退订。")
        assertTrue("应为 NotBill，实际=$outcome", outcome is SmsParseOutcome.NotBill)
    }

    @Test
    fun `未登记发送方：未识别（不猜）`() {
        val outcome = parse("您账户3170于10月8日19:45支付100.00元。", address = "13800138000")
        assertTrue(outcome is SmsParseOutcome.Unrecognized)
    }

    @Test
    fun `支付宝渠道：判为 ALIPAY 且不允许自动记账（渠道抑制）`() {
        val p = parsed(
            "【某某银行】您账户3170于10月8日10:24通过支付宝支付100.00元。活期余额605.91元。对方户名:支付宝。",
        )
        assertEquals(SmsChannel.ALIPAY, p.channel)
        assertTrue("支付宝渠道必须禁止自动记账", !p.channel.allowsAutoImport)
    }

    @Test
    fun `财付通渠道：判为 WECHAT 且不允许自动记账`() {
        val p = parsed(
            "【某某银行】您账户3170于10月8日19:45通过财付通支付20.10元。活期余额505.91元。",
        )
        assertEquals(SmsChannel.WECHAT, p.channel)
        assertTrue(!p.channel.allowsAutoImport)
    }

    @Test
    fun `幂等键：同一短信哈希稳定`() {
        val body = "【某某银行】您账户3170于10月8日19:45通过网银在线支付100.00元。余额605.91元。"
        assertEquals(
            SmsBillParser.externalIdOf("95526", "2026-10-08", body),
            SmsBillParser.externalIdOf("95526", "2026-10-08", body),
        )
    }

    @Test
    fun `幂等键：金额不同则不同`() {
        val a = SmsBillParser.externalIdOf("95526", "2026-10-08", "支付100.00元")
        val b = SmsBillParser.externalIdOf("95526", "2026-10-08", "支付100.01元")
        assertTrue(a != b)
    }

    @Test
    fun `幂等键：空白与零宽字符不影响哈希`() {
        // 用 char code 构造（不写字面零宽字符，避免源码编码环节把它们吃掉）
        val zwsp = 0x200B.toChar()
        val ideographicSpace = 0x3000.toChar()
        val date = "2026-10-08"

        // ① 零宽字符（200B/200C/200D/FEFF/2060）是**删除**语义，不影响相邻字符
        assertEquals(
            SmsBillParser.externalIdOf("95526", date, "支付100.00元"),
            SmsBillParser.externalIdOf("95526", date, "支付${zwsp}100.00${zwsp}元"),
        )
        // ② 全角空格 U+3000 等价于普通空格
        assertEquals(
            SmsBillParser.externalIdOf("95526", date, "支付 100.00 元"),
            SmsBillParser.externalIdOf("95526", date, "支付${ideographicSpace}100.00${ideographicSpace}元"),
        )
        // ③ 连续空白折叠为一个
        assertEquals(
            SmsBillParser.externalIdOf("95526", date, "支付 100.00 元"),
            SmsBillParser.externalIdOf("95526", date, "支付   100.00\t元"),
        )
    }

    @Test
    fun `跨年短信：12月短信在1月收到时回退一年`() {
        // 2026-01-05 10:00 (Asia/Shanghai)
        val janMs = 1_767_571_200_000L
        val p = parsed("【某某银行】您账户3170于12月31日23:50通过网银在线支付100.00元。余额605.91元。", receivedAtMs = janMs)
        assertEquals("2025-12-31", p.row.date)
    }

    @Test
    fun `金额解析边界：三位小数非零 → 非法`() {
        assertNull(SmsBillParser.fenOf("1.234"))
        assertNull(SmsBillParser.fenOf("abc"))
        assertNull(SmsBillParser.fenOf(""))
    }

    @Test
    fun `金额解析：整数与千分位`() {
        assertEquals(1_200L, SmsBillParser.fenOf("12"))
        assertEquals(123_456L, SmsBillParser.fenOf("1,234.56"))
        assertEquals(1L, SmsBillParser.fenOf("0.01"))
    }

    @Test
    fun `摘要脱敏：含金额但不含完整正文`() {
        val body = "【某某银行】您账户3170于10月8日19:45通过网银在线支付100.00元。活期余额605.91元。对方户名:京东商城。"
        val preview = SmsBillParser.previewOf(message(body))
        assertTrue(preview.contains("金额 100.00 元"))
        assertNotNull(SmsTemplateRegistry.match("95526", body))
    }

    // ------------------------------------------------ TASK-084 ④ 贷款/授信营销短信过滤

    @Test
    fun `授信营销短信：用户样例一（农商银行 398000）判非账单`() {
        // 用户 2026-10-13 提供的真实原文（fixture）。
        val outcome = parse(
            "【农商银行】您好，我行授予您398000元，于6月30日停办，可用于日常消费，房屋装修，生意应急 " +
                "回6查利息 回8确认金额 拒收请回复R",
        )
        assertTrue("应为非账单，实际=$outcome", outcome is SmsParseOutcome.NotBill)
    }

    @Test
    fun `授信营销短信：用户样例二（建设银行 398000）判非账单`() {
        val outcome = parse(
            "【建设银行】尊敬的客户，我行可特批您398000元周转款，三年内灵活借还，5月16日截止，请及时回复 " +
                "回1查询利息 回2办理 拒收请回复R",
        )
        assertTrue("应为非账单，实际=$outcome", outcome is SmsParseOutcome.NotBill)
    }

    @Test
    fun `授信营销短信：没有退订后缀也能靠语义词筛掉`() {
        val outcome = parse("【某某银行】我行授予您398000元，于6月30日停办，可用于日常消费，房屋装修")
        assertTrue("应为非账单，实际=$outcome", outcome is SmsParseOutcome.NotBill)
    }

    @Test
    fun `回复指令型营销：回1查询利息 判非账单`() {
        val outcome = parse("【农业银行】我行现有优惠利率，回1查询利息。")
        assertTrue("应为非账单，实际=$outcome", outcome is SmsParseOutcome.NotBill)
    }

    @Test
    fun `贷款还款短信有强锚点：不被营销过滤误杀`() {
        val p = parsed("【某某银行】您尾号3170的贷款于10月8日扣款500.00元，余额605.91元。")
        assertEquals(TransactionType.EXPENSE, p.row.type)
        assertEquals(50_000L, p.row.amountFen)
    }

    @Test
    fun `真实交易短信带退订尾缀：不被非账单预筛误杀`() {
        val p = parsed("【某某银行】您账户3170于10月8日19:45通过网银在线支付100.00元。余额605.91元。退订回T")
        assertEquals(TransactionType.EXPENSE, p.row.type)
        assertEquals(10_000L, p.row.amountFen)
    }

    // ------------------------------------------------ TASK-084 ⑥ 退款口径（记收入 + 单独「退款」分类）

    @Test
    fun `退款短信：记为收入并把平台分类标成退款`() {
        val p = parsed("【某某银行】您账户3170于10月8日19:45收到退款100.00元。活期余额605.91元。")
        assertEquals(TransactionType.INCOME, p.row.type)
        assertEquals("退款", p.row.platformCategory)
        assertEquals(10_000L, p.row.amountFen)
    }

    @Test
    fun `退款短信：正文先出现消费字样也仍判收入`() {
        val p = parsed("【某某银行】您账户3170于10月8日19:45消费100.00元的订单已退款到账。余额605.91元。")
        assertEquals(TransactionType.INCOME, p.row.type)
        assertEquals("退款", p.row.platformCategory)
    }

    @Test
    fun `退款口径不影响非退款短信的方向判定`() {
        val p = parsed("【某某银行】您账户3170于10月8日19:45通过网银在线支付100.00元。余额605.91元。")
        assertEquals(TransactionType.EXPENSE, p.row.type)
        assertTrue(p.row.platformCategory != "退款")
    }
}
