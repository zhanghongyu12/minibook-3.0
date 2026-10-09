package com.minibook.data.local.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HTML → 纯文本单测（F-13，纯 JVM，不发网络请求）。
 *
 * 核心回归：用真实微信账单邮件的 HTML 结构（`multipart/ALTERNATIVE` 的 HTML 部分，
 * 3 个 imgcache/gtimg 干扰 URL + 1 个 tenpay 下载链接）作为输入，断言
 * [MailHtmlText.toPlainText] 保留账单链接、[WechatBillLinkExtractor] 能从中提取。
 * 旧实现（先插入 URL 再 `<[^>]*>` 去标签）会把 URL 连标签一起删掉，本用例在其上必失败。
 */
class MailHtmlTextTest {

    private val extractor = WechatBillLinkExtractor()

    // ------------------------------------------------------------------ 端到端回归（真实邮件形态）

    @Test
    fun `真实微信邮件HTML转纯文本后仍保留tenpay下载链接`() {
        // 形态与真实邮件一致：ALTERNATIVE 的 HTML 部分，含 3 个干扰 URL + 1 个账单链接
        // （token/sign 为合成值，非真实凭证）。
        val html = """
            <div><img src="http://imgcache.qq.com/bossweb/pay/images/mailmsg/email_bg.png"></div>
            <div><img src="https://wx.gtimg.com/static/images/201808/2ae565626ec97c58f20ca582b67dcd3b.png"></div>
            <div><a href="https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?token=SYNTHETIC_TOKEN_ABC&amp;sign=SYNTHETIC_SIGN" target="_blank">点击下载</a></div>
            <div><img src="https://wx.gtimg.com/static/images/201808/71e63b4290158489bf558dad5ca7e5a3.png"></div>
        """.trimIndent()

        val plain = MailHtmlText.toPlainText(html)

        // 旧实现在此失败：`<[^>]*>` 去标签把 tenpay 链接连 URL 一起删掉。
        assertTrue(
            "去标签后正文必须保留 tenpay 下载链接",
            plain.contains("https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail"),
        )
    }

    @Test
    fun `真实微信邮件HTML端到端可提取账单链接`() {
        val html = """
            <div><img src="http://imgcache.qq.com/bossweb/pay/images/mailmsg/email_bg.png"></div>
            <div><img src="https://wx.gtimg.com/static/images/201808/2ae565626ec97c58f20ca582b67dcd3b.png"></div>
            <div><a href="https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?token=SYNTHETIC_TOKEN_ABC&amp;sign=SYNTHETIC_SIGN" target="_blank">点击下载</a></div>
            <div><img src="https://wx.gtimg.com/static/images/201808/71e63b4290158489bf558dad5ca7e5a3.png"></div>
        """.trimIndent()

        val extracted = extractor.extract(MailHtmlText.toPlainText(html))

        assertNotNull("纯文本喂给提取器必须命中账单链接", extracted)
        assertTrue(
            "提取结果须以账单下载路径开头",
            extracted!!.startsWith("https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?"),
        )
        // `&amp;` 已还原为 `&`，且结果里不再含实体残留。
        assertTrue(extracted.contains("token=SYNTHETIC_TOKEN_ABC&sign=SYNTHETIC_SIGN"))
        assertFalse("URL 中的 &amp; 须已还原为 &", extracted.contains("&amp;"))
        // host 精确匹配生效：不能误选干扰链接。
        assertFalse("不得误选 imgcache 干扰链接", extracted.contains("imgcache.qq.com"))
        assertFalse("不得误选 gtimg 干扰链接", extracted.contains("gtimg.com"))
    }

    // ------------------------------------------------------------------ 边界：标签与属性形态

    @Test
    fun `大写HREF与单引号也能取出URL`() {
        val plain = MailHtmlText.toPlainText("<a HREF='https://example.com/a?x=1'>下载</a>")
        assertTrue(plain.contains("https://example.com/a?x=1"))
        assertEquals("https://example.com/a?x=1 下载", plain)
    }

    @Test
    fun `标签内多属性仍能取出URL`() {
        val plain = MailHtmlText.toPlainText("""<a class="x" href="https://example.com/b" data-y="1">点我</a>""")
        assertEquals("https://example.com/b 点我", plain)
    }

    @Test
    fun `无href的普通标签被去掉且不产生URL`() {
        val plain = MailHtmlText.toPlainText("<p>第一段<br><b>加粗</b></p><div>第二段</div>")
        // 每个标签替换为一个空格：相邻标签产生连续空格，但文本绝不粘连。
        assertEquals("第一段  加粗   第二段", plain)
        assertFalse(plain.contains("http"))
    }

    @Test
    fun `相邻块文本不因去标签粘连`() {
        val plain = MailHtmlText.toPlainText("<td>账单</td><td>已生成</td>")
        // 旧实现用空串替换会得到粘连的「账单已生成」；空格替换后两词间至少一个空格。
        assertEquals("账单  已生成", plain)
        assertFalse(plain.contains("账单已生成"))
    }

    // ------------------------------------------------------------------ 边界：引号感知与标签形态（手写扫描新增）

    @Test
    fun `引号内含右尖括号的属性不提前截断标签`() {
        // 旧正则 `[^>]*?` 会把 title 值里的 `>` 误当标签结束；引号感知扫描不受影响。
        val plain = MailHtmlText.toPlainText("""<a title="a>b" href="https://example.com/quote">点击</a>""")
        assertTrue(plain.contains("https://example.com/quote"))
        assertEquals("https://example.com/quote 点击", plain)
    }

    @Test
    fun `大写SRC双引号属性值能取出URL`() {
        val plain = MailHtmlText.toPlainText("""<IMG SRC="http://example.com/x.png">""")
        assertEquals("http://example.com/x.png", plain)
    }

    @Test
    fun `自闭合标签被去掉且URL保留`() {
        // `<br/>` 与 `<img .../>` 各替换为一个空格，img 的 URL 落在标签外。
        val plain = MailHtmlText.toPlainText("""前文<br/><img src="http://example.com/a.png"/>后文""")
        assertEquals("前文  http://example.com/a.png 后文", plain)
    }

    @Test
    fun `小于号后无闭合尖括号丢弃剩余半个标签`() {
        // 不产出半个标签：`<b 未闭合` 起的剩余部分全部丢弃。
        assertEquals("前文", MailHtmlText.toPlainText("前文 <b 未闭合"))
    }

    @Test
    fun `文本中的小于号原样保留`() {
        assertEquals("a < b", MailHtmlText.toPlainText("a < b"))
        assertEquals("价格 < 100 元", MailHtmlText.toPlainText("价格 < 100 元"))
    }

    @Test
    fun `结束标签被替换为空格`() {
        assertEquals("点击 下载", MailHtmlText.toPlainText("点击</a>下载"))
    }

    @Test
    fun `注释被整体去掉`() {
        assertEquals("前 后", MailHtmlText.toPlainText("前<!-- 注释内容 -->后"))
    }

    @Test
    fun `xhref与data-href不算href属性`() {
        // 属性名边界：`xhref` / `data-href` 的 href 部分不得误判为 href 属性。
        val plain = MailHtmlText.toPlainText(
            """<a xhref="https://a.example/x" data-href="https://b.example/y">点</a>""",
        )
        assertFalse(plain.contains("http"))
        assertEquals("点", plain)
    }

    // ------------------------------------------------------------------ 边界：实体反转义

    @Test
    fun `常见实体被反转义`() {
        val plain = MailHtmlText.toPlainText("&lt;a&gt;&quot;你好&quot;&nbsp;&#39;世界&#39;&amp;更多")
        assertEquals("""<a>"你好" '世界'&更多""", plain)
    }

    @Test
    fun `amp最后替换防止二次反转义`() {
        // `&amp;lt;` 还原后应为字面量 `&lt;`，不得二次反转义成 `<`。
        val plain = MailHtmlText.toPlainText("&amp;lt;")
        assertEquals("&lt;", plain)
        assertFalse(plain.contains("<"))
    }

    // ------------------------------------------------------------------ 边界：健壮性

    @Test
    fun `空字符串不抛异常`() {
        assertEquals("", MailHtmlText.toPlainText(""))
    }

    @Test
    fun `纯文本无标签原样返回`() {
        assertEquals("点击下载", MailHtmlText.toPlainText("点击下载"))
    }

    @Test
    fun `URL后紧跟中文不被吞掉`() {
        val plain = MailHtmlText.toPlainText("""<a href="https://tenpay.example.cn/dl?x=1">点击下载</a>""")
        assertTrue(plain.contains("https://tenpay.example.cn/dl?x=1"))
        assertEquals("https://tenpay.example.cn/dl?x=1 点击下载", plain)
    }
}
