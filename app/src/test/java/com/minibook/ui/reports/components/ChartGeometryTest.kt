package com.minibook.ui.reports.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 图表命中几何纯函数单测（两步交互命中区与实际绘制一致）：
 * - [tooltipRect]（折线/柱状 tooltip 数据框矩形）；
 * - [donutCenterTextRect]（饼图中心三行文本矩形）。
 */
class ChartGeometryTest {

    /** density=2 使 dp→px 简单放大 2 倍，便于手算期望值。 */
    private val density = object : Density {
        override val density: Float get() = 2f
        override val fontScale: Float get() = 1f
    }

    @Test
    fun `tooltipRect 计算含内边距的矩形并水平钳在 plot 内`() {
        val plot = PlotArea(left = 46f, right = 254f, top = 10f, bottom = 192f)

        val rect = with(density) { tooltipRect(Offset(100f, 0f), plot, maxWidth = 40, lineHeight = 20, lineCount = 3) }!!

        // padH=6dp*2=12、padV=4dp*2=8：宽=40+24=64、高=20*3+16=76。
        // x=100-32=68（在 [46, 254-64=190] 内）、y=10+8=18。
        assertEquals(Rect(68f, 18f, 132f, 94f), rect)
    }

    @Test
    fun `tooltipRect 锚点过右时水平钳到 plot 右边界`() {
        val plot = PlotArea(left = 46f, right = 254f, top = 10f, bottom = 192f)

        val rect = with(density) { tooltipRect(Offset(10_000f, 0f), plot, maxWidth = 40, lineHeight = 20, lineCount = 3) }!!

        // 宽=64，钳到 x = 254-64 = 190。
        assertEquals(190f, rect.left, 0f)
        assertEquals(254f, rect.right, 0f)
    }

    @Test
    fun `tooltipRect 空行或零尺寸返回 null`() {
        val plot = PlotArea(left = 46f, right = 254f, top = 10f, bottom = 192f)

        assertNull(with(density) { tooltipRect(Offset(100f, 0f), plot, 40, 20, 0) })
        assertNull(with(density) { tooltipRect(Offset(100f, 0f), plot, 0, 20, 3) })
    }

    @Test
    fun `donutCenterTextRect 相对环心居中覆盖三行文本`() {
        val rect = with(density) { donutCenterTextRect(centerX = 100f, centerY = 100f, maxWidth = 40, lineHeight = 20, lineCount = 3) }

        // 宽=40、高=20*3=60；left=100-20=80、top=100-30=70。
        assertEquals(Rect(80f, 70f, 120f, 130f), rect)
    }
}