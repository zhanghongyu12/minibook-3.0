package com.minibook.ui.reports

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minibook.domain.model.TransactionType
import com.minibook.domain.usecase.report.BalanceTrend
import com.minibook.domain.usecase.report.CategorySlice
import com.minibook.domain.usecase.report.GRANULARITY
import com.minibook.domain.usecase.report.ReportTrend
import com.minibook.ui.components.SkeletonBlock
import com.minibook.ui.reports.components.BarChart
import com.minibook.ui.reports.components.BarChartSeries
import com.minibook.ui.reports.components.ChartEmptyState
import com.minibook.ui.reports.components.ChartLegend
import com.minibook.ui.reports.components.DonutChart
import com.minibook.ui.reports.components.DonutSlice
import com.minibook.ui.reports.components.LegendItem
import com.minibook.ui.reports.components.LineChart
import com.minibook.ui.reports.components.LineChartSeries
import com.minibook.ui.reports.components.buildDonutLegendItems
import com.minibook.ui.theme.LocalSemanticColors
import com.minibook.util.TimeFormatter
import java.time.YearMonth

/** 图表卡片占位高度（与图表默认高度一致，改此处需同步组件默认）。 */
private val CHART_HEIGHT = 220.dp

/**
 * 报表页（TASK-036，UI §2 P-13 / §8.2，F-07 / DEC-011）。
 *
 * 结构（对齐 §8.2 树）：[Scaffold] + 顶栏 [TopAppBar]（标题「报表」+ 返回 + 月/年 SegmentedButton）
 * + [LazyColumn]：周期切换器（◀ 当前月/年 ▶）+ 三张图表卡片（每卡独立空态）。
 *
 * - 图 1 收支趋势：折线 / 柱状（[TrendChartKind]）切换 + [ChartLegend]（收入 / 支出）；
 * - 图 2 分类占比：[DonutChart]（收入 / 支出二态 [categoryType]）+ [ChartLegend]（分类名 + 色块 + 占比）；
 * - 图 3 结余走势：[LineChart] 累计结余单系列（正负逐点语义色）。
 *
 * 页面只负责「喂数据 + 视图切换 + 图例」，五要素（口径 / 坐标系 / 图元 / 图例 / 主题）已由图表组件
 * 落地（TASK-035）；本页不参与任何算术（DEC-011）。限定当前账本（VM/UseCase `flatMapLatest`）。
 *
 * @param onBack 顶栏返回回调（NavHost 接线传入 `rootController.popBackStack()`）。
 * @param viewModel 页面 ViewModel；默认经 Hilt 注入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val viewType by viewModel.viewType.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val trendKind by viewModel.trendKind.collectAsStateWithLifecycle()
    val categoryType by viewModel.categoryType.collectAsStateWithLifecycle()
    val trendState by viewModel.trendState.collectAsStateWithLifecycle()
    val categoryState by viewModel.categoryState.collectAsStateWithLifecycle()
    val balanceState by viewModel.balanceState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            ReportsTopBar(
                onBack = onBack,
                viewType = viewType,
                onViewTypeChange = viewModel::setViewType,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                PeriodSwitcher(
                    period = period,
                    viewType = viewType,
                    onShift = viewModel::shiftPeriod,
                    onReset = viewModel::resetPeriod,
                )
            }
            item {
                TrendCard(
                    state = trendState,
                    kind = trendKind,
                    onKindChange = viewModel::setTrendKind,
                )
            }
            item {
                CategoryCard(
                    state = categoryState,
                    type = categoryType,
                    onTypeChange = viewModel::setCategoryType,
                )
            }
            item {
                BalanceCard(state = balanceState)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 顶栏与切换控件
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportsTopBar(
    onBack: () -> Unit,
    viewType: GRANULARITY,
    onViewTypeChange: (GRANULARITY) -> Unit,
) {
    TopAppBar(
        title = { Text("报表") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Text(
                    "←",
                    fontSize = 22.sp,
                    modifier = Modifier.semantics { contentDescription = "返回" },
                )
            }
        },
        actions = {
            ViewTypeToggle(
                viewType = viewType,
                onChange = onViewTypeChange,
                modifier = Modifier.padding(end = 12.dp),
            )
        },
    )
}

/** 月 / 年 视图切换（§8.2：TopAppBar SegmentedButton「月 | 年」）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewTypeToggle(
    viewType: GRANULARITY,
    onChange: (GRANULARITY) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            selected = viewType == GRANULARITY.MONTHLY,
            onClick = { onChange(GRANULARITY.MONTHLY) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = { Text("月") },
        )
        SegmentedButton(
            selected = viewType == GRANULARITY.YEARLY,
            onClick = { onChange(GRANULARITY.YEARLY) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text("年") },
        )
    }
}

/** 周期切换器：◀ 当前月/年 ▶（点击标签回当前月/年）。 */
@Composable
private fun PeriodSwitcher(
    period: YearMonth,
    viewType: GRANULARITY,
    onShift: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    val label = when (viewType) {
        GRANULARITY.MONTHLY -> TimeFormatter.formatYearMonth(period)
        GRANULARITY.YEARLY -> "${period.year} 年"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onShift(false) }) {
            Text("‹", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .clickable(onClick = onReset)
                .semantics { contentDescription = "回到当前${if (viewType == GRANULARITY.MONTHLY) "月" else "年"}" },
        )
        IconButton(onClick = { onShift(true) }) {
            Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------
// 图 1：收支趋势
// ---------------------------------------------------------------------------

@Composable
private fun TrendCard(
    state: ChartState<ReportTrend>,
    kind: TrendChartKind,
    onKindChange: (TrendChartKind) -> Unit,
) {
    ChartCard(
        title = "收支趋势",
        header = {
            TrendKindToggle(
                kind = kind,
                onChange = onKindChange,
            )
        },
    ) {
        when (state) {
            ChartState.Loading -> ChartLoadingBlock()
            ChartState.Empty -> ChartEmptyState(message = "暂无收支数据")
            is ChartState.Error -> ChartEmptyState(message = state.message)
            is ChartState.Success -> TrendChartContent(trend = state.value, kind = kind)
        }
    }
}

@Composable
private fun TrendChartContent(trend: ReportTrend, kind: TrendChartKind) {
    val incomeColor = LocalSemanticColors.current.income
    val expenseColor = LocalSemanticColors.current.expense
    val buckets = trend.buckets

    when (kind) {
        TrendChartKind.LINE -> LineChart(
            buckets = buckets,
            series = listOf(
                LineChartSeries(label = "收入", color = incomeColor, valuesFen = trend.incomeValues),
                LineChartSeries(label = "支出", color = expenseColor, valuesFen = trend.expenseValues),
            ),
        )
        TrendChartKind.BAR -> BarChart(
            buckets = buckets,
            series = listOf(
                BarChartSeries(label = "收入", color = incomeColor, valuesFen = trend.incomeValues),
                BarChartSeries(label = "支出", color = expenseColor, valuesFen = trend.expenseValues),
            ),
        )
    }
    Spacer(Modifier.height(12.dp))
    ChartLegend(
        items = listOf(
            LegendItem(label = "收入", color = incomeColor),
            LegendItem(label = "支出", color = expenseColor),
        ),
    )
}

/** 图 1 形态切换：折线 / 柱状。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrendKindToggle(
    kind: TrendChartKind,
    onChange: (TrendChartKind) -> Unit,
) {
    SingleChoiceSegmentedButtonRow {
        SegmentedButton(
            selected = kind == TrendChartKind.LINE,
            onClick = { onChange(TrendChartKind.LINE) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = { Text("折线") },
        )
        SegmentedButton(
            selected = kind == TrendChartKind.BAR,
            onClick = { onChange(TrendChartKind.BAR) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text("柱状") },
        )
    }
}

// ---------------------------------------------------------------------------
// 图 2：分类占比
// ---------------------------------------------------------------------------

@Composable
private fun CategoryCard(
    state: ChartState<List<CategorySlice>>,
    type: TransactionType,
    onTypeChange: (TransactionType) -> Unit,
) {
    ChartCard(
        title = "分类占比",
        header = {
            CategoryTypeToggle(
                type = type,
                onChange = onTypeChange,
            )
        },
    ) {
        when (state) {
            ChartState.Loading -> ChartLoadingBlock()
            ChartState.Empty -> ChartEmptyState(message = "暂无分类数据")
            is ChartState.Error -> ChartEmptyState(message = state.message)
            is ChartState.Success -> {
                val slices = state.value.toDonutSlices(fallbackColor = MaterialTheme.colorScheme.outline)
                DonutChart(slices = slices, modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT))
                if (slices.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    ChartLegend(items = buildDonutLegendItems(slices))
                }
            }
        }
    }
}

/** 占比口径切换：收入 / 支出（§8.2 图 2 下方 SegmentedButton，非 C-08 三态）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryTypeToggle(
    type: TransactionType,
    onChange: (TransactionType) -> Unit,
) {
    SingleChoiceSegmentedButtonRow {
        SegmentedButton(
            selected = type == TransactionType.INCOME,
            onClick = { onChange(TransactionType.INCOME) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = { Text("收入") },
        )
        SegmentedButton(
            selected = type == TransactionType.EXPENSE,
            onClick = { onChange(TransactionType.EXPENSE) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text("支出") },
        )
    }
}

/** [CategorySlice] → 环形切片（分类名 / 分类色 → [DonutSlice]；分类已删以 id 兜底 + outline 兜底色）。 */
@Composable
private fun List<CategorySlice>.toDonutSlices(fallbackColor: Color): List<DonutSlice> =
    map { slice ->
        val color = slice.category?.color?.let { parseCategoryColor(it, fallbackColor) } ?: fallbackColor
        DonutSlice(
            label = slice.category?.name ?: slice.categoryId.take(6),
            color = color,
            valueFen = slice.totalFen,
        )
    }

// ---------------------------------------------------------------------------
// 图 3：结余走势
// ---------------------------------------------------------------------------

@Composable
private fun BalanceCard(state: ChartState<BalanceTrend>) {
    ChartCard(title = "结余走势") {
        when (state) {
            ChartState.Loading -> ChartLoadingBlock()
            ChartState.Empty -> ChartEmptyState(message = "暂无结余数据")
            is ChartState.Error -> ChartEmptyState(message = state.message)
            is ChartState.Success -> {
                val balance = state.value
                val semantic = LocalSemanticColors.current
                LineChart(
                    buckets = balance.buckets,
                    series = listOf(
                        LineChartSeries(
                            label = "累计结余",
                            color = semantic.netZero,
                            valuesFen = balance.cumulativeBalanceValues,
                            // 结余逐点正负取语义色（正=收入绿、负=支出红），可穿零轴。
                            colorForValue = { fen -> if (fen >= 0L) semantic.income else semantic.expense },
                        ),
                    ),
                    showPoints = false,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 通用卡片骨架
// ---------------------------------------------------------------------------

/** 图表卡片容器：标题 + 可选 header（形态/口径切换）+ 内容。 */
@Composable
private fun ChartCard(
    title: String,
    header: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                header?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** 图表加载骨架：卡片内固定高度占位（C-05）。 */
@Composable
private fun ChartLoadingBlock() {
    SkeletonBlock(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT),
    )
}

/** 解析 DB 分类色（`#RRGGBB`）→ Compose Color；非法格式回退 fallback（同工程 CategoryComponents）。 */
private fun parseCategoryColor(colorHex: String, fallback: Color): Color =
    runCatching { Color(AndroidColor.parseColor(colorHex)) }.getOrDefault(fallback)
