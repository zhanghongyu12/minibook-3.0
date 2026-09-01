package com.minibook.data.datasource

import com.minibook.domain.usecase.bill.BillParser

/**
 * 账单解析器工厂契约（F-12，API §13.4 / ARCHITECTURE §16.2）。
 *
 * 契约归位 `data/datasource`（与 [TransactionRunner] 同类「域可依赖」纯接口）：
 * 域侧 [com.minibook.domain.usecase.bill.ImportBillUseCase] 经本接口获得解析器，
 * 与域依赖 `data.datasource.TransactionRunner` 同径。唯一实现
 * [com.minibook.data.local.bill.DefaultBillParserFactory]（无状态）。
 *
 * [resolve] 判型：**内容嗅探主判**——先 `PK\x03\x04` 魔数嗅探，命中即按 `WechatXlsxParser`
 * （XLSX）；否则再按扩展名分发（`.xlsx`→微信、`.csv`→支付宝、无扩展名→按 CSV 试解）。
 * 修复「.csv 名但内容是 zip」被扩展名误分发到 CSV 的问题（DEC-017「扩展名失配→内容嗅探兜底」）。
 * CSV 自身表头探测失败 → `parse` 返回 `Unrecognized`，同样满足「探测失败 → 零落库」。
 * 判不出 → null → 「未识别账单格式」。
 */
interface BillParserFactory {
    fun resolve(displayName: String?, magicBytes: ByteArray?): BillParser?
}