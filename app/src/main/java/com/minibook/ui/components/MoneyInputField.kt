package com.minibook.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.minibook.ui.theme.TabularFigures
import com.minibook.util.MoneyFormatter

/**
 * 金额输入框（UI §4 C-01）。
 *
 * 36sp 大字 + 数字键盘（[KeyboardType.Decimal]），内部维护「元」字符串态：
 * - 输入层即拦截非法字符（仅数字+单个小数点、小数点后最多 2 位），见 [sanitizeAmount]；
 * - 每次合法输入即时经 [MoneyFormatter.toFen] 转为「分」回调 [onFenChange]；
 * - 空输入 / 解析失败（如仅 `.`）→ 回调 `null`，错误文案由外层 `isError`/`errorMessage` 驱动
 *   （校验（>0、不超上限）属页面业务，不在组件内判定，见 `Validators.validateAmount`）。
 *
 * 组件不承载业务文案：`label`/`errorMessage` 均由调用方注入。
 *
 * @param fen 当前金额（分）；外部修改（如表单重置）时组件会自动同步为元字符串。
 * @param onFenChange 分 → 回调；解析失败或为空时回调 `null`。
 * @param label 输入框标签（可空）。
 * @param isError 是否错误态（由外层校验驱动）。
 * @param errorMessage 错误提示文案（配合 `isError` 展示）。
 */
@Composable
fun MoneyInputField(
    fen: Long?,
    onFenChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
) {
    var text by rememberSaveable { mutableStateOf(fen?.toYuanInput() ?: "") }
    // 记录上次回调的分值，用于区分「外部改值」与「自身输入」，避免同步回写形成环路。
    var lastEmittedFen by remember { mutableStateOf(fen) }

    LaunchedEffect(fen) {
        if (fen != lastEmittedFen) {
            lastEmittedFen = fen
            text = fen?.toYuanInput() ?: ""
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val clean = sanitizeAmount(raw)
            text = clean
            val parsed = if (clean.isBlank()) {
                null
            } else {
                runCatching { MoneyFormatter.toFen(clean) }.getOrNull()
            }
            lastEmittedFen = parsed
            onFenChange(parsed)
        },
        modifier = modifier.fillMaxWidth(),
        label = label?.let { { Text(it) } },
        isError = isError,
        supportingText = errorMessage?.let { { Text(it) } },
        prefix = { Text("¥") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.displaySmall.merge(TabularFigures),
    )
}

/** Long（分）→ 元输入串：去千分位逗号（如 `123456 → "1234.56"`）。 */
private fun Long.toYuanInput(): String =
    MoneyFormatter.formatFen(this, withSymbol = false).replace(",", "")

/**
 * 金额输入过滤（拦截而非校验，UI §4 C-01）：
 * 仅保留数字与单个小数点，小数点后最多 2 位，其余字符一律丢弃。
 */
internal fun sanitizeAmount(raw: String): String {
    val sb = StringBuilder()
    var dotSeen = false
    var fractionCount = 0
    for (ch in raw) {
        when {
            ch.isDigit() -> {
                if (dotSeen) {
                    if (fractionCount < 2) {
                        sb.append(ch)
                        fractionCount++
                    }
                } else {
                    sb.append(ch)
                }
            }
            ch == '.' && !dotSeen -> {
                sb.append(ch)
                dotSeen = true
            }
        }
    }
    return sb.toString()
}
