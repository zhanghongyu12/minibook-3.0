package com.minibook.ui.billimport

import com.minibook.domain.model.BillImportResult
import com.minibook.domain.model.BillPlatform
import com.minibook.domain.model.MailBillItemResult
import com.minibook.domain.model.MailImportStep
import com.minibook.domain.model.MailImportStepState
import com.minibook.domain.model.MailImportStepStatus
import com.minibook.domain.model.MailImportSummary
import com.minibook.domain.model.MailItemFailure
import com.minibook.domain.model.MailItemStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MailImportPresenter] 纯函数单测（F-13 增强 B，纯 JVM）。
 *
 * 覆盖：[MailImportPresenter.failureLabel] 全部 9 个 [MailItemFailure] 分支（含
 * [MailItemFailure.Unexpected] 带 message）；[MailImportPresenter.stepLabel] 全部 5 个
 * [MailImportStep] 分支；[MailImportPresenter.itemStepLabel] 全部 6 个 [MailItemStep]
 * 分支；[MailImportPresenter.stepLine] 各 [MailImportStepStatus] 状态（含 DONE/FAILED
 * 带与不带 summary）；[MailImportPresenter.mailItemLine] 成功与 9 种失败分支；
 * [MailImportPresenter.present] 计数正确性（混合成功/失败、多邮箱来源前缀、
 * `success`/`existing` 累加）；无失败时 [MailImportDisplay.failureLines] 为空。
 *
 * 本测试不使用任何真实邮箱地址（一律 `@example.com` 保留域）。
 */
class MailImportPresenterTest {

    // ---------- failureLabel：9 个分支穷尽 ----------

    @Test
    fun failureLabel_noAttachment() {
        assertEquals(
            "邮件里没有找到账单附件",
            MailImportPresenter.failureLabel(MailItemFailure.NoAttachment),
        )
    }

    @Test
    fun failureLabel_noLink() {
        assertEquals(
            "邮件里没有找到下载链接",
            MailImportPresenter.failureLabel(MailItemFailure.NoLink),
        )
    }

    @Test
    fun failureLabel_linkExpired() {
        assertEquals(
            "下载链接已过期（微信链接 7 天有效）",
            MailImportPresenter.failureLabel(MailItemFailure.LinkExpired),
        )
    }

    @Test
    fun failureLabel_downloadFailed() {
        assertEquals(
            "账单下载失败",
            MailImportPresenter.failureLabel(MailItemFailure.DownloadFailed),
        )
    }

    @Test
    fun failureLabel_unsupportedEncryption() {
        assertEquals(
            "账单压缩包使用了不支持的加密方式",
            MailImportPresenter.failureLabel(MailItemFailure.UnsupportedEncryption),
        )
    }

    @Test
    fun failureLabel_passwordNotFound() {
        assertEquals(
            "未能自动解出解压口令（非 6 位数字）",
            MailImportPresenter.failureLabel(MailItemFailure.PasswordNotFound),
        )
    }

    @Test
    fun failureLabel_extractFailed() {
        assertEquals(
            "账单压缩包解压失败",
            MailImportPresenter.failureLabel(MailItemFailure.ExtractFailed),
        )
    }

    @Test
    fun failureLabel_unrecognizedBill() {
        assertEquals(
            "账单格式未识别",
            MailImportPresenter.failureLabel(MailItemFailure.UnrecognizedBill),
        )
    }

    @Test
    fun failureLabel_unexpected_includesMessage() {
        assertEquals(
            "出现未预期的错误（连接中断）",
            MailImportPresenter.failureLabel(MailItemFailure.Unexpected("连接中断")),
        )
    }

    // ---------- stepLabel：5 个分支穷尽 ----------

    @Test
    fun stepLabel_allFiveSteps() {
        assertEquals("连接邮箱", MailImportPresenter.stepLabel(MailImportStep.CONNECT))
        assertEquals("拉取邮件", MailImportPresenter.stepLabel(MailImportStep.FETCH))
        assertEquals("筛选账单邮件", MailImportPresenter.stepLabel(MailImportStep.FILTER))
        assertEquals("处理账单邮件", MailImportPresenter.stepLabel(MailImportStep.PROCESS))
        assertEquals("完成汇总", MailImportPresenter.stepLabel(MailImportStep.DONE))
    }

    // ---------- itemStepLabel：6 个分支穷尽 ----------

    @Test
    fun itemStepLabel_allSixItemSteps() {
        assertEquals("提取链接或附件", MailImportPresenter.itemStepLabel(MailItemStep.EXTRACT))
        assertEquals("下载账单", MailImportPresenter.itemStepLabel(MailItemStep.DOWNLOAD))
        assertEquals("探测加密方式", MailImportPresenter.itemStepLabel(MailItemStep.INSPECT))
        assertEquals("破解解压口令", MailImportPresenter.itemStepLabel(MailItemStep.CRACK))
        assertEquals("解压账单", MailImportPresenter.itemStepLabel(MailItemStep.UNZIP))
        assertEquals("导入交易", MailImportPresenter.itemStepLabel(MailItemStep.IMPORT))
    }

    // ---------- stepLine：各状态语义 ----------

    @Test
    fun stepLine_pending() {
        assertEquals(
            "连接邮箱 · 待执行",
            MailImportPresenter.stepLine(MailImportStepState(MailImportStep.CONNECT, MailImportStepStatus.PENDING)),
        )
    }

    @Test
    fun stepLine_running() {
        assertEquals(
            "拉取邮件 · 进行中",
            MailImportPresenter.stepLine(MailImportStepState(MailImportStep.FETCH, MailImportStepStatus.RUNNING)),
        )
    }

    @Test
    fun stepLine_done_withSummary() {
        assertEquals(
            "筛选账单邮件 · 命中 3 封（微信 2 / 支付宝 1）",
            MailImportPresenter.stepLine(
                MailImportStepState(
                    MailImportStep.FILTER,
                    MailImportStepStatus.DONE,
                    "命中 3 封（微信 2 / 支付宝 1）",
                ),
            ),
        )
    }

    @Test
    fun stepLine_done_withoutSummary() {
        assertEquals(
            "完成汇总 · 完成",
            MailImportPresenter.stepLine(MailImportStepState(MailImportStep.DONE, MailImportStepStatus.DONE)),
        )
    }

    @Test
    fun stepLine_failed_withAndWithoutSummary() {
        assertEquals(
            "处理账单邮件 · 失败：连接中断",
            MailImportPresenter.stepLine(
                MailImportStepState(MailImportStep.PROCESS, MailImportStepStatus.FAILED, "连接中断"),
            ),
        )
        assertEquals(
            "连接邮箱 · 失败",
            MailImportPresenter.stepLine(MailImportStepState(MailImportStep.CONNECT, MailImportStepStatus.FAILED)),
        )
    }

    // ---------- mailItemLine：成功 + 9 种失败分支 ----------

    @Test
    fun mailItemLine_success() {
        assertEquals(
            "✅ 支付宝 · 支付宝账单 A — 新导入 3 条 / 已存在 2 条",
            MailImportPresenter.mailItemLine(
                item(
                    platform = BillPlatform.ALIPAY,
                    subject = "支付宝账单 A",
                    importResult = BillImportResult(success = 3, existing = 2),
                ),
            ),
        )
    }

    @Test
    fun mailItemLine_success_wechatZeroImported() {
        assertEquals(
            "✅ 微信 · 微信账单 B — 新导入 0 条 / 已存在 1 条",
            MailImportPresenter.mailItemLine(
                item(
                    platform = BillPlatform.WECHAT,
                    subject = "微信账单 B",
                    importResult = BillImportResult(success = 0, existing = 1),
                ),
            ),
        )
    }

    @Test
    fun mailItemLine_allNineFailureBranches() {
        val cases = listOf(
            MailItemFailure.NoAttachment to "❌ 支付宝 · 主题 X — 邮件里没有找到账单附件",
            MailItemFailure.NoLink to "❌ 微信 · 主题 X — 邮件里没有找到下载链接",
            MailItemFailure.LinkExpired to "❌ 微信 · 主题 X — 下载链接已过期（微信链接 7 天有效）",
            MailItemFailure.DownloadFailed to "❌ 微信 · 主题 X — 账单下载失败",
            MailItemFailure.UnsupportedEncryption to "❌ 支付宝 · 主题 X — 账单压缩包使用了不支持的加密方式",
            MailItemFailure.PasswordNotFound to "❌ 支付宝 · 主题 X — 未能自动解出解压口令（非 6 位数字）",
            MailItemFailure.ExtractFailed to "❌ 微信 · 主题 X — 账单压缩包解压失败",
            MailItemFailure.UnrecognizedBill to "❌ 支付宝 · 主题 X — 账单格式未识别",
            MailItemFailure.Unexpected("IO 超时") to "❌ 支付宝 · 主题 X — 出现未预期的错误（IO 超时）",
        )
        cases.forEach { (failure, expected) ->
            assertEquals(
                expected,
                MailImportPresenter.mailItemLine(
                    item(
                        platform = if (failure == MailItemFailure.NoLink ||
                            failure == MailItemFailure.LinkExpired ||
                            failure == MailItemFailure.DownloadFailed ||
                            failure == MailItemFailure.ExtractFailed
                        ) {
                            BillPlatform.WECHAT
                        } else {
                            BillPlatform.ALIPAY
                        },
                        subject = "主题 X",
                        importResult = null,
                        failure = failure,
                    ),
                ),
            )
        }
    }

    // ---------- present：计数与摘要 ----------

    @Test
    fun present_mixedItems_countsAndHeadline() {
        val summary = MailImportSummary(
            totalMailCount = 4,
            billMailCount = 4,
            items = listOf(
                // 成功：success=3 / existing=2
                item(
                    platform = BillPlatform.ALIPAY,
                    subject = "支付宝账单 A",
                    importResult = BillImportResult(success = 3, existing = 2),
                ),
                // 成功：success=0 / existing=1（全去重）
                item(
                    platform = BillPlatform.WECHAT,
                    subject = "微信账单 B",
                    importResult = BillImportResult(success = 0, existing = 1),
                ),
                // 失败（未走到导入链路，importResult=null）
                item(
                    platform = BillPlatform.WECHAT,
                    subject = "微信账单 C",
                    importResult = null,
                    failure = MailItemFailure.LinkExpired,
                ),
                // 失败但带 importResult 的防御分支：不计成功邮件，但计数随模型派生属性
                // （totalImported/totalExisting 按 importResult 求和、不看 failure）如实累加。
                item(
                    platform = BillPlatform.ALIPAY,
                    subject = "支付宝账单 D",
                    importResult = BillImportResult(success = 5, existing = 0),
                    failure = MailItemFailure.NoAttachment,
                ),
            ),
        )

        val display = MailImportPresenter.present(summary)

        assertEquals(8, display.importedCount)
        assertEquals(3, display.existingCount)
        assertEquals(2, display.succeededMailCount)
        assertEquals(2, display.failedMailCount)
        assertEquals("本次收取：新导入 8 条，已存在 3 条；成功 2 封，失败 2 封", display.headline)
        assertEquals(
            listOf(
                "微信 · 微信账单 C — 下载链接已过期（微信链接 7 天有效）",
                "支付宝 · 支付宝账单 D — 邮件里没有找到账单附件",
            ),
            display.failureLines,
        )
    }

    @Test
    fun present_noFailures_emptyFailureLines() {
        val summary = MailImportSummary(
            totalMailCount = 2,
            billMailCount = 2,
            items = listOf(
                item(
                    platform = BillPlatform.ALIPAY,
                    subject = "支付宝账单 A",
                    importResult = BillImportResult(success = 10, existing = 0),
                ),
                item(
                    platform = BillPlatform.WECHAT,
                    subject = "微信账单 B",
                    importResult = BillImportResult(success = 2, existing = 4),
                ),
            ),
        )

        val display = MailImportPresenter.present(summary)

        assertEquals(12, display.importedCount)
        assertEquals(4, display.existingCount)
        assertEquals(2, display.succeededMailCount)
        assertEquals(0, display.failedMailCount)
        assertTrue(display.failureLines.isEmpty())
        assertEquals("本次收取：新导入 12 条，已存在 4 条；成功 2 封，失败 0 封", display.headline)
    }

    @Test
    fun present_unexpectedFailureLine_containsMessage() {
        val summary = MailImportSummary(
            totalMailCount = 1,
            billMailCount = 1,
            items = listOf(
                item(
                    platform = BillPlatform.ALIPAY,
                    subject = "支付宝账单 E",
                    importResult = null,
                    failure = MailItemFailure.Unexpected("IO 超时"),
                ),
            ),
        )

        val display = MailImportPresenter.present(summary)

        assertEquals(1, display.failedMailCount)
        assertEquals(0, display.importedCount)
        assertEquals(0, display.existingCount)
        assertEquals(
            listOf("支付宝 · 支付宝账单 E — 出现未预期的错误（IO 超时）"),
            display.failureLines,
        )
    }

    @Test
    fun present_multipleAccountEmails_prefixesFailureLinesWithSource() {
        val summary = MailImportSummary(
            totalMailCount = 3,
            billMailCount = 3,
            items = listOf(
                item(
                    accountEmail = "one@example.com",
                    platform = BillPlatform.ALIPAY,
                    subject = "账单 A",
                    importResult = BillImportResult(success = 1, existing = 0),
                ),
                item(
                    accountEmail = "two@example.com",
                    platform = BillPlatform.WECHAT,
                    subject = "账单 B",
                    importResult = null,
                    failure = MailItemFailure.LinkExpired,
                ),
                item(
                    accountEmail = "one@example.com",
                    platform = BillPlatform.WECHAT,
                    subject = "账单 C",
                    importResult = null,
                    failure = MailItemFailure.NoLink,
                ),
            ),
        )

        val display = MailImportPresenter.present(summary)

        assertEquals(
            listOf(
                "two@example.com · 微信 · 账单 B — 下载链接已过期（微信链接 7 天有效）",
                "one@example.com · 微信 · 账单 C — 邮件里没有找到下载链接",
            ),
            display.failureLines,
        )
    }

    // ---------- fixtures ----------

    private fun item(
        platform: BillPlatform,
        subject: String,
        importResult: BillImportResult?,
        failure: MailItemFailure? = null,
        accountEmail: String = "user@example.com",
    ): MailBillItemResult = MailBillItemResult(
        accountEmail = accountEmail,
        platform = platform,
        subject = subject,
        sentDateMillis = 0L,
        password = null,
        attachmentFileName = null,
        importResult = importResult,
        failure = failure,
    )
}
