package com.minibook.ui.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 应用内法务文本（`res/raw/privacy_policy.txt` / `res/raw/terms_of_service.txt`）的守卫单测。
 *
 * 为什么要单测测文本：这两份文件是**唯一副本**——界面直接读它、上架材料也据此整理。
 * 一旦有人「顺手精简」掉权限说明或注销条款，界面不会报错、编译也不会失败，用户却看不到了。
 * 这里把几条硬要求（收集范围 / 权限 / 存储安全 / 注销与本地保留 / 主体待补声明）钉成断言。
 *
 * 文本是纯文本渲染（Compose `Text`），因此**不允许出现 Markdown 记号**，否则用户会看到裸的
 * `##` 与 `**`（此前删除确认框就踩过这个坑）。
 */
class LegalTextTest {

    private fun legalText(name: String): String {
        val candidates = listOf(
            File("src/main/res/raw/$name.txt"),
            File("app/src/main/res/raw/$name.txt"),
        )
        val file = candidates.firstOrNull { it.isFile }
        assertTrue(
            "找不到 res/raw/$name.txt（候选：${candidates.joinToString { it.absolutePath }}）",
            file != null,
        )
        return file!!.readText()
    }

    @Test
    fun `隐私政策 覆盖收集范围 权限 存储安全 用户权利与主体待补声明`() {
        val text = legalText("privacy_policy")
        // 收集范围：本地不收集 + 只有云同步才上传账目
        assertTrue(text.contains("本地优先"))
        assertTrue(text.contains("云同步"))
        // 权限说明（清单里真实声明的四项 + 生物识别）
        assertTrue(text.contains("READ_SMS"))
        assertTrue(text.contains("POST_NOTIFICATIONS"))
        assertTrue(text.contains("INTERNET"))
        // 存储安全
        assertTrue(text.contains("SQLCipher"))
        assertTrue(text.contains("argon2id"))
        // 用户权利：注销删云端且本地保留 / 退出登录
        assertTrue(text.contains("注销"))
        assertTrue(text.contains("本地账目继续保留"))
        assertTrue(text.contains("退出登录"))
        // 未成年人 + 生效日期 + 运营主体待补（用户 2026-10-12 拍板：本轮不声明）
        assertTrue(text.contains("未成年人"))
        assertTrue(text.contains("生效日期"))
        assertTrue(text.contains("尚未声明"))
    }

    @Test
    fun `用户协议 写明注销不删本地与云同步不等于备份`() {
        val text = legalText("terms_of_service")
        assertTrue(text.contains("都不会删除本地账目"))
        assertTrue(text.contains("不是备份的替代品"))
        assertTrue(text.contains("注册"))
        assertTrue(text.contains("同意"))
        assertTrue(text.contains("生效日期"))
        assertTrue(text.contains("尚未声明"))
    }

    @Test
    fun `两份文本是纯文本 无 Markdown 记号且长度像样`() {
        for (name in listOf("privacy_policy", "terms_of_service")) {
            val text = legalText(name)
            assertTrue("$name 太短，像是被误删了", text.length > 800)
            assertFalse("$name 含 Markdown 标题记号，界面会显示裸 ##", text.contains("##"))
            assertFalse("$name 含 Markdown 加粗记号，界面会显示裸 **", text.contains("**"))
            assertFalse("$name 含 Markdown 链接语法", text.contains("]("))
        }
    }
}
