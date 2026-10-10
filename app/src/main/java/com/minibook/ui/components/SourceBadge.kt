package com.minibook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.minibook.domain.model.TransactionSource

/**
 * 交易来源中文映射（UI §9.6 文案表 / §9.7 D-32；F-14 增补短信）。
 *
 * 全 UI 层共用的 `TransactionSource` 中文标签：`manual`→手动记录、`wechat_import`→微信、
 * `alipay_import`→支付宝、`sms_import`→短信。供 [SourceBadge]（列表行徽标）与
 * 交易只读明细（「来源」行）复用，避免两处各写一套映射。
 */
val TransactionSource.chineseLabel: String
    get() = when (this) {
        TransactionSource.MANUAL -> "手动记录"
        TransactionSource.WECHAT_IMPORT -> "微信"
        TransactionSource.ALIPAY_IMPORT -> "支付宝"
        TransactionSource.SMS_IMPORT -> "短信"
    }

/**
 * 来源徽标（UI §9.5 C-33）。
 *
 * 通用 status-tag 形态：`labelSmall` 文案、`secondaryContainer` 圆角底 + `onSecondaryContainer`
 * 字、左右 `6dp` / 上下 `2dp` 内边距。**仅 `source ≠ MANUAL` 时由调用方渲染**（手动记录零噪音，
 * §9.3.1）；分类名主文本行尾部内嵌时，分类名 `weight(1f)` 省略让位。
 *
 * @param source 交易来源（MANUAL 不应传入；防御性照常渲染，由调用方门控）。
 */
@Composable
fun SourceBadge(
    source: TransactionSource,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = source.chineseLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}