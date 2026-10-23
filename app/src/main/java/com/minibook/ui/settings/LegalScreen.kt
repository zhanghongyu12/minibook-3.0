package com.minibook.ui.settings

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.minibook.R
import com.minibook.ui.components.SettingsSubPageTopBar

/**
 * 应用内法务文本（隐私政策 / 用户协议），2026-10-12（TASK-076）。
 *
 * 正文直接取自 `res/raw` 目录下的纯文本（`.txt`）——**唯一副本**：上架材料、应用内展示、
 * 导出的文本同源，避免「文档一份、界面一份、两份不一致」。因此正文里没有 Markdown
 * （界面是纯文本渲染），章节层级用「一、二、三」表达。
 *
 * @param title 页面与列表行的标题。
 * @param resId `res/raw` 下的纯文本资源 id。
 */
enum class LegalDocument(val title: String, @param:RawRes val resId: Int) {
    Privacy("隐私政策", R.raw.privacy_policy),
    Terms("用户协议", R.raw.terms_of_service),
}

/**
 * 法务文本二级页（设置 → 关于 → 隐私政策 / 用户协议）。
 *
 * 为什么要有这一页：注册页必须能点开协议与政策（《移动互联网应用程序信息服务管理规定》
 * 第十六条要求注册时明示并取得同意），而且不同意也能继续用本地记账——所以正文页只是
 * 只读展示，不做任何门控。
 */
@Composable
fun LegalScreen(document: LegalDocument, onBack: () -> Unit) {
    val context = LocalContext.current
    // 资源是静态文本：只在换文档时读一次，不必随重组重复读盘。
    val body = remember(document) {
        context.resources.openRawResource(document.resId)
            .bufferedReader()
            .use { it.readText() }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { SettingsSubPageTopBar(title = document.title, onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
