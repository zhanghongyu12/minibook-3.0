package com.minibook.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.minibook.R
import com.minibook.ui.components.MiniBookIcons

/**
 * 登录 / 注册页的共用组件（2026-10-12 第二轮：用户要求「要像 App 的登录注册页面」）。
 *
 * 设计口径：
 * - **独立整页**，不是嵌在账号页里的一小块表单；
 * - 顶部应用图标 + 一句主标题 + 一句说明（说清「登录能得到什么、本地数据不受影响」）；
 * - 输入框大而清晰，主按钮满宽且带忙碌态；
 * - 错误**写在按钮下方**（页面内文案，不会像 toast 一样一闪而过）；
 * - 底部一个切换到另一个页面的文字按钮。
 *
 * 第三轮（视觉现代化 + 正经 App 规范）追加：
 * - 返回键改用矢量图标（`MiniBookIcons.ArrowBack`），与全应用其它页一致；
 * - 输入框关掉自动纠错与首字母大写（用户名/口令被系统改写是真实故障源），
 *   给出 `imeAction`（下一步 / 完成）并声明 autofill `ContentType`（让口令管理器能填）；
 * - 口令框的明文切换收进 trailing 图标（Material 的既有形态）；
 * - 错误文案带 `liveRegion`，读屏会主动播报。
 */
@Composable
internal fun AuthPage(
    onBack: () -> Unit,
    headline: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = MiniBookIcons.ArrowBack,
                    contentDescription = "返回",
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 用**矢量**前台图（`ic_launcher_foreground`）而不是 `mipmap/ic_launcher`：
                // 后者在 release/新版 AGP 下是自适应图标 XML，`painterResource` 会抛
                // `IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported`
                // ——真机上表现为「一点注册就闪退」（2026-10-12 模拟器实测抓到）。
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = headline,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(28.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 认证页输入框。
 *
 * @param supporting 常态下的辅助说明（规则提示）。
 * @param errorText  字段级错误；非空时覆盖 `supporting` 并把输入框标红。
 * @param reveal     口令是否明文显示（由调用方持有状态）。
 * @param onToggleReveal 明文切换回调；为空则不显示 trailing 图标。
 * @param contentType autofill 语义类型（用户名 / 口令），让口令管理器与系统自动填充可用。
 * @param onImeAction 键盘「下一步 / 完成」的动作；为空时「下一步」默认把焦点移到下一项。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    errorText: String? = null,
    password: Boolean = false,
    reveal: Boolean = false,
    onToggleReveal: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    contentType: ContentType? = null,
    enabled: Boolean = true,
) {
    val focusManager = LocalFocusManager.current
    val hasError = !errorText.isNullOrBlank()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        isError = hasError,
        supportingText = when {
            hasError -> {
                { Text(errorText!!) }
            }

            supporting != null -> {
                { Text(supporting) }
            }

            else -> null
        },
        singleLine = true,
        // 关掉自动纠错与首字母大写：用户名/口令被输入法「顺手」改写是真实故障源。
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onNext = {
                if (onImeAction != null) onImeAction() else focusManager.moveFocus(FocusDirection.Down)
            },
            onDone = { onImeAction?.invoke() },
            onGo = { onImeAction?.invoke() },
        ),
        visualTransformation = if (password && !reveal) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = if (password && onToggleReveal != null) {
            {
                IconButton(onClick = onToggleReveal) {
                    Icon(
                        imageVector = if (reveal) MiniBookIcons.VisibilityOff else MiniBookIcons.Visibility,
                        contentDescription = if (reveal) "隐藏口令" else "显示口令",
                    )
                }
            }
        } else {
            null
        },
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (contentType != null) {
                    Modifier.semantics { this.contentType = contentType }
                } else {
                    Modifier
                },
            ),
    )
}

/** 主按钮：满宽 + 忙碌态（转圈时按钮不可重复点击）。 */
@Composable
internal fun AuthPrimaryButton(text: String, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * 页面内错误文案（比 toast 好读：不消失，就在按钮下方）。
 *
 * 带 `liveRegion = Polite`：错误出现时读屏会播报，不依赖用户用眼睛扫到。
 */
@Composable
internal fun AuthErrorMessage(message: String?) {
    if (message.isNullOrBlank()) return
    Spacer(Modifier.height(12.dp))
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** 底部切换链接：「还没有账号？去注册」/「已有账号？去登录」。 */
@Composable
internal fun AuthSwitchRow(question: String, action: String, onClick: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TextButton(onClick = onClick) {
            Text("$question$action")
        }
    }
}

/**
 * 认证页底部的法务提示行（TASK-076，2026-10-12）。
 *
 * 两种形态、一套组件：
 * - 注册页：`agreed != null` ⇒ 出现**默认未勾选**的 Checkbox（国信办秘字〔2019〕191 号三.4
 *   明确「不得默认勾选同意」），未勾选时注册会被拦下并直说原因；
 * - 登录页：`agreed == null` ⇒ 只显示「登录即表示你已阅读并同意」，不重复要一次同意
 *   （用户已在注册时勾过；登录页再放勾选框属于「频繁征求同意」，同一文件三.2 禁止）。
 *
 * 协议与政策都做成可点的链接（Role.Button 让读屏把它当按钮播报），保证「同意」之前
 * 真能读得到原文——否则勾选没有意义。
 *
 * @param agreed 勾选状态；null 表示无勾选形态（登录页）。
 * @param onAgreedChange 勾选回调；[agreed] 非 null 时必须提供。
 */
@Composable
internal fun AuthLegalNotice(
    agreed: Boolean?,
    onAgreedChange: ((Boolean) -> Unit)?,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    Spacer(Modifier.height(4.dp))
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (agreed != null && onAgreedChange != null) {
                Checkbox(checked = agreed, onCheckedChange = onAgreedChange)
            }
            Text(
                text = if (agreed != null) "我已阅读并同意以下条款" else "登录即表示你已阅读并同意",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegalLink(text = "《用户协议》", onClick = onOpenTerms)
            Text(
                text = "和",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LegalLink(text = "《隐私政策》", onClick = onOpenPrivacy)
        }
    }
}

/** 法务文本链接：点开可读原文。`padding` 放在 `clickable` **之后**，把点击区做到约 40dp 高。 */
@Composable
private fun LegalLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
    )
}
