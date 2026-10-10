package com.minibook.ui.sms

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.SmsAmbiguousHit
import com.minibook.domain.model.SmsPendingItem
import com.minibook.ui.components.EmptyState
import com.minibook.ui.components.LoadingState
import com.minibook.util.MoneyFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 「待确认 / 未识别」清单页（F-14，UI §11.1）。
 *
 * 两类需人工处理的条目：
 * - **未识别**：疑似账单但抽不出可信字段（脱敏摘要 + 原因）；
 * - **疑似重复**：同账本、同向、同额、同日存在 ≥2 条候选（不自动写、不自动删）。
 *
 * 隐私（DEC-026 / UI §11.5）：本页只展示 [SmsPendingItem.preview] 与自建 `description`，
 * **不展示短信原文、完整卡号与余额**。
 *
 * 「手工记一笔」「标记为非账单」「导出样本」等设计动作中，仅「手工记一笔」有可行入口
 * （跳既有记一笔表单；**无预填 API**），其余两项的存储与 fixture 导出未在 domain/data 层提供，
 * 本期不做、留 TODO（详见 [SmsPendingScreen] KDoc 末）。
 *
 * @param onBack 返回回调（根图 `popBackStack`）。
 * @param onAddTransaction 「手工记一笔」回调（根图跳 `transaction-detail` 新增态；无预填）。
 * @param viewModel 页面 ViewModel；默认经 Hilt 注入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsPendingScreen(
    onBack: () -> Unit,
    onAddTransaction: () -> Unit = {},
    viewModel: SmsPendingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 权限门：只做状态展示 + 引导，不在此发起权限请求（TODO(F-14 权限申请)）。
    val readSmsGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS,
    ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(Unit) { viewModel.load(readSmsGranted) }

    val errorMessage = uiState.errorMessage

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("待确认短信") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←", fontSize = 20.sp) }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.loading -> LoadingState(Modifier.padding(innerPadding))
            uiState.permissionNeeded -> EmptyState(
                icon = SmsPendingIcon,
                title = "需要短信权限；可去系统设置授予，或改用分享导入",
                subtitle = "授予后回到本页点击「重新检查权限」重试。",
                actionLabel = "重新检查权限",
                onAction = { viewModel.load(readSmsGranted) },
                modifier = Modifier.padding(innerPadding),
            )
            errorMessage != null -> EmptyState(
                icon = SmsPendingIcon,
                title = errorMessage,
                subtitle = null,
                actionLabel = "重试",
                onAction = { viewModel.load(readSmsGranted) },
                modifier = Modifier.padding(innerPadding),
            )
            uiState.isEmpty -> EmptyState(
                icon = SmsPendingIcon,
                title = "暂无待确认条目",
                subtitle = "扫描 ${uiState.scanTotal} 条短信，没有需要人工确认的条目。",
                actionLabel = null,
                onAction = null,
                modifier = Modifier.padding(innerPadding),
            )
            else -> SmsPendingList(
                uiState = uiState,
                onRescan = { viewModel.load(readSmsGranted) },
                onAddTransaction = onAddTransaction,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

/** 清单列表（私有）：页头扫描计数 + 未识别段 + 疑似重复段 + 钉底按钮。 */
@Composable
private fun SmsPendingList(
    uiState: SmsPendingUiState,
    onRescan: () -> Unit,
    onAddTransaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") {
                Text(
                    text = "本次扫描 ${uiState.scanTotal} 条短信，以下条目需人工处理。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (uiState.unrecognized.isNotEmpty()) {
                item(key = "unrecognized_title") {
                    Text(
                        text = "未识别（${uiState.unrecognized.size} 条）",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                items(uiState.unrecognized, key = { "unr_${it.id}_${it.reason}" }) { item ->
                    UnrecognizedRow(item)
                }
            }
            if (uiState.ambiguous.isNotEmpty()) {
                item(key = "ambiguous_title") {
                    Text(
                        text = "疑似重复（${uiState.ambiguous.size} 条）",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                // 用下标入 key：externalId 理论唯一，但重复短信（同内容同日期）可能撞键。
                itemsIndexed(uiState.ambiguous, key = { index, hit -> "amb_${index}_${hit.incoming.externalId}" }) { _, hit ->
                    AmbiguousRow(hit)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onRescan,
                modifier = Modifier.weight(1f),
            ) {
                Text("重新扫描")
            }
            Button(
                onClick = onAddTransaction,
                modifier = Modifier.weight(1f),
            ) {
                Text("手工记一笔")
            }
        }
    }
}

/** 未识别行（私有）：发送方 + 到达时间 + 脱敏摘要 + 原因。 */
@Composable
private fun UnrecognizedRow(item: SmsPendingItem) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row {
                Text(
                    text = item.sender,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = timeLabel(item.receivedAtMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "未识别原因：${item.reason}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** 疑似重复行（私有）：仅展示自建摘要 + 金额 + 候选数，不展示候选原文。 */
@Composable
private fun AmbiguousRow(hit: SmsAmbiguousHit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = hit.incoming.description,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "金额 ${MoneyFormatter.formatFen(hit.incoming.amountFen)} · ${hit.incoming.date} · " +
                    "存在 ${hit.candidates.size} 条同额同日期记录，请确认",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 到达时间 → 用户可读文案（本页专用小工具）。 */
private fun timeLabel(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))

/** 待确认清单空态图标：手写「对话气泡」ImageVector（material-icons 缺失，TASK-020 同口径）。 */
private val SmsPendingIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SmsPending",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z" +
                "M6 9h12v2H6zm8 5H6v-2h8zm4-6H6V6h12z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}
