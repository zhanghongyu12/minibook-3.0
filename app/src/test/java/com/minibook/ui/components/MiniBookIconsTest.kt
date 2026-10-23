package com.minibook.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自带矢量图标集的兜底测试（TASK-079）。
 *
 * 图标路径是手写 SVG 字符串，**解析失败会在首帧抛异常**（参照 F-15o：编译绿、单测全绿
 * 仍可能运行时崩，因为崩溃点是渲染时才发现）。这里在 JVM 侧把全部图标构造一遍，
 * 让路径笔误在单测阶段就暴露，而不是等到界面上。
 */
class MiniBookIconsTest {

    @Test
    fun `所有图标路径可解析且 viewport 与节点非空`() {
        val icons: Map<String, ImageVector> = MiniBookIcons.all
        assertTrue("图标集不应为空", icons.isNotEmpty())
        icons.forEach { (name, vector) ->
            assertEquals("$name 的 viewport 宽度", 24f, vector.viewportWidth, 0.001f)
            assertEquals("$name 的 viewport 高度", 24f, vector.viewportHeight, 0.001f)
            assertTrue("$name 没有任何绘制节点（路径解析失败？）", vector.root.size > 0)
        }
    }

    @Test
    fun `图标名称唯一`() {
        val names = MiniBookIcons.all.keys.toList()
        assertEquals("图标名称不应重复", names.size, names.toSet().size)
    }
}
