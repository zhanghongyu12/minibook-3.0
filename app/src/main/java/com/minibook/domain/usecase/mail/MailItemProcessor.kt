package com.minibook.domain.usecase.mail

import com.minibook.data.local.mail.BillDownloadException
import com.minibook.data.local.mail.MailBillDownloader
import com.minibook.data.local.mail.MailFilenameDecoder
import com.minibook.data.local.mail.WechatBillLinkExtractor
import com.minibook.domain.model.BillImportResult
import com.minibook.domain.model.BillPlatform
import com.minibook.domain.model.MailBillHeader
import com.minibook.domain.model.MailBillItemResult
import com.minibook.domain.model.MailBillMail
import com.minibook.domain.model.MailItemFailure
import com.minibook.domain.model.MailItemStep
import com.minibook.domain.usecase.bill.BillFilePreparer
import com.minibook.domain.usecase.bill.ImportBillUseCase
import com.minibook.domain.usecase.bill.zip.EncryptedZipExtractor
import com.minibook.domain.usecase.bill.zip.ZipArchiveInspector
import com.minibook.domain.usecase.bill.zip.ZipEncryptionInfo
import com.minibook.domain.usecase.bill.zip.ZipPasswordCracker
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * 单封账单邮件处理器（F-13 编排层内部件，从 `MailBillImportUseCase` 拆出以控单文件行数）。
 *
 * 单封链路：取件（支付宝附件 / 微信下载链接）→ zip 落盘 → 判型（仅 ZipCrypto）→
 * 6 位数字口令穷举（百分比经原子 CAS 节流）→ 解压 → 送入 [ImportBillUseCase.importLocalFile]。
 * 每进入一个子步骤经 [onItemStep] 上报（EXTRACT→DOWNLOAD（仅微信）→INSPECT→CRACK→
 * UNZIP→IMPORT；不适用的子步骤直接跳过），由编排层组装完整进度快照。
 *
 * 失败映射（单封，详见 [MailItemFailure] 各分支 KDoc）：
 * - 支付宝无附件 → [MailItemFailure.NoAttachment]；
 * - 微信正文无下载链接 → [MailItemFailure.NoLink]；
 * - 下载异常 message 含「失效或过期」（微信链接 7 天有效期的固定文案）→
 *   [MailItemFailure.LinkExpired]，其余 [BillDownloadException] →
 *   [MailItemFailure.DownloadFailed]（不透传异常原文，可能含 URL）；
 * - zip 非 ZipCrypto（AES/未加密/非法）→ [MailItemFailure.UnsupportedEncryption]；
 * - 6 位数字穷举未命中 → [MailItemFailure.PasswordNotFound]；
 * - 解压抛异常 → [MailItemFailure.ExtractFailed]；
 * - 导入返回 `unrecognized == true` → [MailItemFailure.UnrecognizedBill]；
 * - 其余未预期异常 → [MailItemFailure.Unexpected]（类别描述）。
 *
 * 临时文件纪律：zip 落盘文件与解压产物登记进调用方传入的 `tempFiles`（编排层统一
 * `try/finally` 清理）；解压产物在 `importLocalFile` 同步读完返回后才可删
 * （`PreparedBillFile` 惰性读，返回即安全）。
 */
@Singleton
class MailItemProcessor @Inject constructor(
    private val downloader: MailBillDownloader,
    private val filenameDecoder: MailFilenameDecoder,
    private val linkExtractor: WechatBillLinkExtractor,
    private val zipInspector: ZipArchiveInspector,
    private val zipCracker: ZipPasswordCracker,
    private val zipExtractor: EncryptedZipExtractor,
    private val filePreparer: BillFilePreparer,
    private val importBillUseCase: ImportBillUseCase,
) {

    /**
     * 处理一封邮件：未预期异常统一兜底为 [MailItemFailure.Unexpected]（仅类别描述，
     * 不含凭据/URL/账单内容），保证一封失败不中断整批；取消异常原样向上抛。
     */
    suspend fun process(
        mail: MailBillMail,
        accountEmail: String,
        ledgerId: String,
        tempFiles: MutableList<File>,
        onItemStep: (MailItemStep, Int?) -> Unit,
    ): MailBillItemResult = try {
        processInternal(mail, accountEmail, ledgerId, tempFiles, onItemStep)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        mail.header.toResult(
            accountEmail = accountEmail,
            failure = MailItemFailure.Unexpected(
                "处理邮件时出现未预期异常（${e.javaClass.simpleName}）",
            ),
        )
    }

    /** 单封编排主体（分支见类 KDoc 失败映射）。 */
    private suspend fun processInternal(
        mail: MailBillMail,
        accountEmail: String,
        ledgerId: String,
        tempFiles: MutableList<File>,
        onItemStep: (MailItemStep, Int?) -> Unit,
    ): MailBillItemResult {
        val header = mail.header

        // ---- 取 zip 字节（平台分叉）----
        onItemStep(MailItemStep.EXTRACT, null)
        val zipBytes: ByteArray
        var mailFileName: String? = null // 邮件侧已知文件名（已解码），填 MailBillItemResult
        when (header.platform) {
            BillPlatform.ALIPAY -> {
                val bytes = mail.attachmentBytes
                if (bytes == null) {
                    return header.toResult(accountEmail, failure = MailItemFailure.NoAttachment)
                }
                zipBytes = bytes
                mailFileName = filenameDecoder.decode(mail.attachmentFileName)
            }
            BillPlatform.WECHAT -> {
                val url = linkExtractor.extract(mail.bodyText)
                if (url == null) {
                    return header.toResult(accountEmail, failure = MailItemFailure.NoLink)
                }
                onItemStep(MailItemStep.DOWNLOAD, null)
                val downloaded = try {
                    downloader.download(url)
                } catch (e: BillDownloadException) {
                    // 区分「链接失效/过期」（微信 7 天有效期的固定文案）与一般下载失败；
                    // 不把异常原文（可能含 URL/签名 token）透传进失败信息。
                    val failure = if (e.message?.contains(LINK_EXPIRED_HINT) == true) {
                        MailItemFailure.LinkExpired
                    } else {
                        MailItemFailure.DownloadFailed
                    }
                    return header.toResult(accountEmail, failure = failure)
                }
                zipBytes = downloaded.bytes
                mailFileName = downloaded.fileName
            }
        }

        // ---- zip 落盘（临时文件，编排层 finally 统一清理）----
        val zipFile = filePreparer.createTempFile(ZIP_TEMP_PREFIX, ZIP_TEMP_SUFFIX)
        tempFiles += zipFile
        FileOutputStream(zipFile).use { it.write(zipBytes) }

        // ---- 判型：仅支持 ZipCrypto（AES/未加密/非法 zip 不支持）----
        onItemStep(MailItemStep.INSPECT, null)
        when (zipInspector.inspect(zipFile)) {
            is ZipEncryptionInfo.Unsupported ->
                return header.toResult(
                    accountEmail,
                    attachmentFileName = mailFileName,
                    failure = MailItemFailure.UnsupportedEncryption,
                )
            ZipEncryptionInfo.ZipCrypto -> Unit // 继续
        }

        // ---- 6 位数字口令穷举（百分比经原子 CAS 节流：整数变化才回调）----
        onItemStep(MailItemStep.CRACK, null)
        val lastPercent = AtomicInteger(-1)
        val password = zipCracker.crack(zipFile, PASSWORD_DIGITS, PASSWORD_CHARSET) { tried ->
            val percent = ((tried * 100L) / CRACK_TOTAL).toInt().coerceIn(0, 100)
            var last = lastPercent.get()
            while (percent != last) {
                if (lastPercent.compareAndSet(last, percent)) {
                    onItemStep(MailItemStep.CRACK, percent)
                    break
                }
                last = lastPercent.get()
            }
        }
        if (password == null) {
            return header.toResult(
                accountEmail,
                attachmentFileName = mailFileName,
                failure = MailItemFailure.PasswordNotFound,
            )
        }

        // ---- 解压到临时目录 ----
        // destDir 用临时占位文件的父目录（临时目录）；占位文件本身也登记清理。
        onItemStep(MailItemStep.UNZIP, null)
        val destDirHolder = filePreparer.createTempFile(DST_TEMP_PREFIX, DST_TEMP_SUFFIX)
        tempFiles += destDirHolder
        val destDir = destDirHolder.parentFile
        if (destDir == null) {
            return header.toResult(
                accountEmail,
                attachmentFileName = mailFileName,
                password = password,
                failure = MailItemFailure.Unexpected("无法确定临时解压目录"),
            )
        }
        val extractedFile = try {
            zipExtractor.extract(zipFile, password, destDir)
        } catch (e: Exception) {
            return header.toResult(
                accountEmail,
                attachmentFileName = mailFileName,
                password = password,
                failure = MailItemFailure.ExtractFailed,
            )
        }
        tempFiles += extractedFile

        // ---- 送入导入链路（写账本实际发生处）----
        // 展示名口径：优先邮件侧已解码文件名，但仅当其为可判型的账单扩展名（.csv/.xlsx）
        // 时采用——解析器工厂按扩展名分发（magic 嗅探仅覆盖 XLSX），邮件侧文件名若是
        // zip 名（如支付宝附件名）会误判「未识别」；此时回退用 zip 条目名（解压出的
        // 真实账单文件名，扩展名可靠）。
        onItemStep(MailItemStep.IMPORT, null)
        val importDisplayName = mailFileName?.takeIf { isBillFileName(it) } ?: extractedFile.name
        val importResult = importBillUseCase.importLocalFile(extractedFile, importDisplayName, ledgerId)
        if (importResult.unrecognized) {
            return header.toResult(
                accountEmail,
                attachmentFileName = mailFileName,
                password = password,
                failure = MailItemFailure.UnrecognizedBill,
            )
        }
        return header.toResult(
            accountEmail,
            attachmentFileName = mailFileName,
            password = password,
            importResult = importResult,
        )
    }

    // ------------------------------------------------------------------ 辅助

    /** 由邮件摘要装配单封结果（失败早退路径的便捷构造）。 */
    private fun MailBillHeader.toResult(
        accountEmail: String,
        password: String? = null,
        attachmentFileName: String? = null,
        importResult: BillImportResult? = null,
        failure: MailItemFailure? = null,
    ): MailBillItemResult = MailBillItemResult(
        accountEmail = accountEmail,
        platform = platform,
        subject = subject,
        sentDateMillis = sentDateMillis,
        password = password,
        attachmentFileName = attachmentFileName,
        importResult = importResult,
        failure = failure,
    )

    /** 文件名是否为解析器可判型的账单扩展名（.csv / .xlsx，大小写不敏感）。 */
    private fun isBillFileName(name: String): Boolean =
        name.endsWith(".csv", ignoreCase = true) || name.endsWith(".xlsx", ignoreCase = true)

    private companion object {
        const val ZIP_TEMP_PREFIX = "minibook_mail_"
        const val ZIP_TEMP_SUFFIX = ".zip"
        const val DST_TEMP_PREFIX = "minibook_mail_dst_"
        const val DST_TEMP_SUFFIX = ""
        val PASSWORD_DIGITS = 6..6
        const val PASSWORD_CHARSET = "0123456789"

        /** 穷举候选总数（6 位数字 = 10^6），CRACK 百分比分母。 */
        const val CRACK_TOTAL = 1_000_000L

        /**
         * 链接失效判定子串：`MailBillDownloader` 的固定文案「下载链接已失效或过期
         * （微信链接有效期 7 天，请重新申请）」中的稳定片段（常量在下游为 private，
         * 无法直接引用，故按子串匹配；文案变更时需同步）。
         */
        const val LINK_EXPIRED_HINT = "失效或过期"
    }
}
