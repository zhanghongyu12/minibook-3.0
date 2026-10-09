package com.minibook.domain.model

/**
 * 「一键收取」邮件账单（F-13 编排层）导入流程的 5 个步骤。
 *
 * [CONNECT]→[FETCH]→[FILTER]→[PROCESS]（逐封）→[DONE]，多邮箱时每个账号各走一遍；
 * 由 [com.minibook.domain.usecase.mail.MailBillImportUseCase] 逐步上报，UI 渲染 stepper。
 */
enum class MailImportStep { CONNECT, FETCH, FILTER, PROCESS, DONE }

/** 单个步骤的执行状态。 */
enum class MailImportStepStatus { PENDING, RUNNING, DONE, FAILED }

/**
 * 单步状态：进行中可带细节，完成后可带摘要。
 *
 * @property step 步骤
 * @property status 执行状态
 * @property summary 完成后的中文摘要（如「窗口内共 12 封」）；进行中/待执行为 null
 */
data class MailImportStepState(
    val step: MailImportStep,
    val status: MailImportStepStatus,
    val summary: String? = null,
)

/**
 * 第 4 步「处理账单邮件」内，单封邮件的子步骤。
 *
 * 依次为 [EXTRACT]（取附件/提取链接）→ [DOWNLOAD]（仅微信）→ [INSPECT] →
 * [CRACK] → [UNZIP] → [IMPORT]；不适用的子步骤（如支付宝无下载环节）直接跳过。
 */
enum class MailItemStep { EXTRACT, DOWNLOAD, INSPECT, CRACK, UNZIP, IMPORT }

/**
 * 导入进度快照（每一步都发一份**完整**快照，UI 直接渲染，无需自行拼接历史）。
 *
 * 快照的步骤状态**累积**：某个步骤一旦被发出过状态，后续快照里未提及它时保持上次
 * 状态（不回退 PENDING）；只有从未出现过的步骤才是 PENDING（顺序按 [MailImportStep]
 * 的枚举序）。累积逻辑见编排层的 `MailImportStepTracker`。
 *
 * 安全纪律：**授权码绝不出现在任何字段中**；[accountEmail]/[currentSubject] 为邮箱地址与
 * 邮件主题，仅展示用（多邮箱时区分来源所必需），不属于凭据。
 *
 * @property steps 5 个步骤的当前状态（完整列表；从未出现过的步骤为 PENDING，已出现过的保持上次状态）
 * @property accountEmail 当前正在处理的邮箱地址；账号解析失败前为 null
 * @property accountIndex 当前邮箱序号（1 起）
 * @property accountCount 本轮收取的邮箱总数
 * @property mailIndex 当前处理到第几封（1 起；未进入第 4 步为 0）
 * @property mailCount 本轮**待处理**邮件数（= 每种账单最新一封之和；同平台旧账单被跳过不计入；FILTER 完成后可得）
 * @property currentSubject 当前邮件主题；未在逐封处理时为 null
 * @property currentItemStep 当前邮件的子步骤；未在逐封处理时为 null
 * @property crackPercent 口令穷举百分比（0~100，整数变化才回调）；非 CRACK 子步骤为 null
 * @property itemLines 已完成邮件的逐封结果行（跨账号累积；✅ 成功计数 / ❌ 失败原因）
 */
data class MailImportProgress(
    val steps: List<MailImportStepState>,
    val accountEmail: String? = null,
    val accountIndex: Int = 1,
    val accountCount: Int = 1,
    val mailIndex: Int = 0,
    val mailCount: Int = 0,
    val currentSubject: String? = null,
    val currentItemStep: MailItemStep? = null,
    val crackPercent: Int? = null,
    val itemLines: List<String> = emptyList(),
)

/**
 * 单封账单邮件的失败原因（封闭集合，UI 据此给文案）。
 *
 * 实现约定：任何失败信息**不含**授权码、下载链接 URL 及账单内容（邮箱地址经
 * [MailImportProgress.accountEmail] 展示属多账号区分来源所需，不在禁列）。
 */
sealed interface MailItemFailure {
    /** 支付宝邮件没找到附件。 */
    data object NoAttachment : MailItemFailure

    /** 微信邮件正文没找到下载链接。 */
    data object NoLink : MailItemFailure

    /** 下载链接失效/过期（微信 7 天有效）。 */
    data object LinkExpired : MailItemFailure

    /** 下载失败（网络/超时等非链接失效原因）。 */
    data object DownloadFailed : MailItemFailure

    /** AES 或非法 zip，不支持。 */
    data object UnsupportedEncryption : MailItemFailure

    /** 6 位数字穷举未命中。 */
    data object PasswordNotFound : MailItemFailure

    /** 解压失败（口令校验不过/数据损坏等）。 */
    data object ExtractFailed : MailItemFailure

    /** 解析器不认识该账单。 */
    data object UnrecognizedBill : MailItemFailure

    /** 未预期异常；[message] 仅类别描述，不含凭据/URL 全文。 */
    data class Unexpected(val message: String) : MailItemFailure
}

/**
 * 单封账单邮件的处理结果。
 *
 * @property accountEmail 来源邮箱地址（多邮箱收取时结果页区分来源）
 * @property platform 账单平台（支付宝/微信）
 * @property subject 邮件主题（原始值，仅展示用）
 * @property sentDateMillis 邮件发送时间毫秒（服务端缺头时回退收件时间，皆缺为 0）
 * @property password 命中的 zip 口令；null=未破解/未走到破解
 * @property attachmentFileName 已解码的文件名（支付宝=解码后的附件名；微信=下载响应
 *   Content-Disposition 解析名）；拿不到为 null
 * @property importResult 成功送入导入链路时的统计；否则 null
 * @property failure 失败原因；成功时为 null
 */
data class MailBillItemResult(
    val accountEmail: String,
    val platform: BillPlatform,
    val subject: String,
    val sentDateMillis: Long,
    val password: String?,
    val attachmentFileName: String?,
    val importResult: BillImportResult?,
    val failure: MailItemFailure?,
)

/**
 * 一次「一键收取」的汇总（可跨多个邮箱账号）。
 *
 * @property totalMailCount 时间窗口内参与筛选的邮件总数（各账号窗口数之和；
 *   由 `ImapMailClient.fetchBillMails` 返回的窗口计数累加，不再恒等于 [billMailCount]）
 * @property billMailCount 命中账单过滤的邮件数（各账号命中数之和）
 * @property items 逐封处理结果（与各账号过滤命中顺序一致，跨账号按处理顺序拼接）
 */
data class MailImportSummary(
    val totalMailCount: Int,
    val billMailCount: Int,
    val items: List<MailBillItemResult>,
) {
    /** 处理成功的邮件数。 */
    val succeededCount: Int get() = items.count { it.failure == null }

    /** 失败邮件数。 */
    val failedCount: Int get() = items.count { it.failure != null }

    /** 累计新导入交易数。 */
    val totalImported: Int get() = items.sumOf { it.importResult?.success ?: 0 }

    /** 累计已存在（去重）数。 */
    val totalExisting: Int get() = items.sumOf { it.importResult?.existing ?: 0 }
}

/**
 * 「一键收取」结果。
 *
 * [MailAuthException] / [MailNetworkException] 不在此封装内——由
 * [com.minibook.domain.usecase.mail.MailBillImportUseCase] 直接向上抛，UI 负责映射文案。
 */
sealed interface MailImportOutcome {
    /** 未配置邮箱凭据（或凭据不可用）→ 引导用户去设置页。 */
    data object NotConfigured : MailImportOutcome

    /** 完成一轮收取（个别邮件失败不影响整体完成，详见 [MailImportSummary]）。 */
    data class Completed(val summary: MailImportSummary) : MailImportOutcome
}

// ---------------------------------------------------------------------- 展示文案（单一事实源）

/**
 * 失败原因 → 用户可读中文文案（`when` 穷尽 [MailItemFailure]，新增分支编译器强制补齐）。
 *
 * 放在 domain 层作为**单一事实源**：编排层进度快照的 `itemLines` 与 UI 展示
 * （`MailImportPresenter.failureLabel` 委托本函数）共用同一份文案，避免两处漂移。
 */
fun MailItemFailure.displayLabel(): String = when (this) {
    MailItemFailure.NoAttachment -> "邮件里没有找到账单附件"
    MailItemFailure.NoLink -> "邮件里没有找到下载链接"
    MailItemFailure.LinkExpired -> "下载链接已过期（微信链接 7 天有效）"
    MailItemFailure.DownloadFailed -> "账单下载失败"
    MailItemFailure.UnsupportedEncryption -> "账单压缩包使用了不支持的加密方式"
    MailItemFailure.PasswordNotFound -> "未能自动解出解压口令（非 6 位数字）"
    MailItemFailure.ExtractFailed -> "账单压缩包解压失败"
    MailItemFailure.UnrecognizedBill -> "账单格式未识别"
    is MailItemFailure.Unexpected -> "出现未预期的错误（$message）"
}

/** 平台展示名：ALIPAY→「支付宝」、WECHAT→「微信」。 */
fun BillPlatform.displayLabel(): String = when (this) {
    BillPlatform.ALIPAY -> "支付宝"
    BillPlatform.WECHAT -> "微信"
}

/**
 * 单封结果 → 一行结果文本（进度 `itemLines` 与结果页逐封行共用，`when` 穷尽
 * [MailItemFailure] 经 [displayLabel] 保证不漏分支）：
 * 成功「✅ 平台 · 主题 — 新导入 X 条 / 已存在 Y 条」；
 * 失败「❌ 平台 · 主题 — 失败原因」。
 */
fun MailBillItemResult.toResultLine(): String {
    val head = "${platform.displayLabel()} · $subject"
    return if (failure == null) {
        "✅ $head — 新导入 ${importResult?.success ?: 0} 条 / 已存在 ${importResult?.existing ?: 0} 条"
    } else {
        "❌ $head — ${failure.displayLabel()}"
    }
}
