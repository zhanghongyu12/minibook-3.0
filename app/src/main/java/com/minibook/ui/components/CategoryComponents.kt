package com.minibook.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType

/**
 * 分类选择底部弹层（UI §4 C-02）。
 *
 * 4 列网格单选，复用 [CategoryIconTile] 渲染格子；点击即 [onPick] 回调所选分类，
 * 弹层关闭交由调用方。分类列表由调用方注入（组件不持有取数逻辑）。
 *
 * @param categories 候选分类（由调用方注入，组件内按 `type`/`excludeId` 过滤）。
 * @param type 目标类型；null 表示不按类型过滤。
 * @param excludeId 需排除的分类 id（如当前编辑的分类）。
 * @param initialId 初始选中高亮 id。
 * @param title 弹层标题；默认「选择分类」，可覆盖。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    categories: List<Category>,
    type: CategoryType?,
    excludeId: String?,
    initialId: String?,
    onPick: (Category) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "选择分类",
) {
    val filtered = remember(categories, type, excludeId) {
        categories.filter { (type == null || it.type == type) && it.id != excludeId }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.id }) { category ->
                    CategoryIconTile(
                        category = category,
                        selected = category.id == initialId,
                        enabled = true,
                        onClick = { onPick(category) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * 分类图标格子（UI §4 C-03）。
 *
 * emoji 置于「分类色弱化圆底」上（`color.copy(alpha = 0.15f)`，感知背景），emoji 本体保持
 * 分类原色；选中态以主题 primary 描边高亮，`enabled = false` 时整体淡化。分类色为 DB 数据
 * （唯一允许的硬编码色来源），解析失败时回退 [MaterialTheme.colorScheme.outline]。
 *
 * @param category 分类数据（icon/name/color 均展示自 DB）。
 * @param selected 是否选中。
 * @param enabled 是否可点（禁用时淡化）。
 */
@Composable
fun CategoryIconTile(
    category: Category,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fallback = MaterialTheme.colorScheme.outline
    val circleColor = remember(category.color) { parseCategoryHex(category.color, fallback) }
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp)
            .alpha(if (enabled) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(circleColor.copy(alpha = 0.15f))
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = category.icon, fontSize = 24.sp, color = circleColor)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 解析 DB 分类色（`#RRGGBB`）→ Compose Color；空值/非法格式回退 fallback。 */
private fun parseCategoryHex(colorHex: String?, fallback: Color): Color =
    if (colorHex.isNullOrBlank()) {
        fallback
    } else {
        runCatching { Color(AndroidColor.parseColor(colorHex)) }.getOrDefault(fallback)
    }
