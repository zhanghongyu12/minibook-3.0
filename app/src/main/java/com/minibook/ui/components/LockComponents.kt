package com.minibook.ui.components

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minibook.ui.theme.FullShape

/**
 * 品牌解锁门组合（UI §4 C-26）：品牌页 + 生物识别按钮 + 引导卡。
 *
 * `isDeviceSecure = false`（未设置系统锁屏）时展示 [GuidanceCard]，点击跳系统安全设置
 * （`Settings.ACTION_SECURITY_SETTINGS`，组件内构造 Intent 并 try/catch startActivity）。
 *
 * 品牌/引导文案为解锁门固定展示词，可经以下可选参数覆盖：
 * @param brandName 品牌名，默认「MiniBook」。
 * @param tagline 副标语，默认「你的随身账本」。
 * @param guidanceMessage 未设锁屏引导文案。
 * @param guidanceActionLabel 引导动作按钮文案，默认「去设置」。
 */
@Composable
fun LogoLockScreen(
    onUnlock: () -> Unit,
    isDeviceSecure: Boolean,
    modifier: Modifier = Modifier,
    brandName: String = "MiniBook",
    tagline: String = "你的随身账本",
    guidanceMessage: String = "未检测到系统锁屏，生物识别不可用。请前往系统设置开启锁屏密码。",
    guidanceActionLabel: String = "去设置",
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            LedgerGlyph(
                icon = LedgerIcons.BOOK,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                size = 44.dp,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(brandName, style = MaterialTheme.typography.headlineMedium)
        if (tagline.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(48.dp))
        BioAuthButton(onClick = onUnlock, modifier = Modifier.fillMaxWidth())
        if (!isDeviceSecure) {
            Spacer(Modifier.height(16.dp))
            GuidanceCard(
                message = guidanceMessage,
                actionLabel = guidanceActionLabel,
                onAction = {
                    try {
                        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                        if (context !is Activity) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        // 系统安全设置不可达时静默忽略
                    }
                },
            )
        }
    }
}

/**
 * 生物识别解锁按钮（UI §4 C-26）。
 *
 * 全圆角大按钮，文案为「🔓 + label」，label 默认「生物识别解锁」可覆盖。
 */
@Composable
fun BioAuthButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    label: String = "生物识别解锁",
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = FullShape,
    ) {
        Text("🔓 ", fontSize = 16.sp)
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * 引导卡片（UI §4 C-26）：说明文案 + 可选动作按钮。
 *
 * 纯展示；文案与动作均由调用方注入（本组件不持有业务文案）。
 */
@Composable
fun GuidanceCard(
    message: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(actionLabel) }
            }
        }
    }
}
