package com.minibook.domain.usecase.category

import com.minibook.domain.usecase.category.CategoryRuleNormalizer.MatchStrength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分类规则归一化与匹配强度（TASK-084，用户需求①；算法细节见
 * `docs/19_category_rule_normalization.md`）。
 *
 * 覆盖用户 2026-10-13 给的真实样例（北京轨道交通 / 罗森门店系列），以及
 * 「旧行为（原始串包含）不变」「归一化相等」「核心词包含」「公共前缀只给 WEAK」四条强度。
 */
class CategoryRuleNormalizerTest {

    // ---------- 归一化 ----------

    @Test
    fun `归一化 剥企业门店后缀与括号 全角转半角`() {
        // 括号里的城市/门店信息与后缀词都被剥掉（规则词只留「商户名」这一段）。
        assertEquals("罗森朝阳", CategoryRuleNormalizer.normalize("罗森(北京)有限公司朝阳门店"))
        assertEquals("罗森房山店", CategoryRuleNormalizer.normalize("罗森（北京）有限公司房山店"))
        assertEquals("北京轨道交通", CategoryRuleNormalizer.normalize("北京轨道交通有限公司"))
        assertEquals("rosenbeijing", CategoryRuleNormalizer.normalize("Ｒｏｓｅｎ　ＢｅｉＪｉｎｇ"))
    }

    @Test
    fun `归一化 空串与空白给空串 不配对括号直接丢掉`() {
        assertEquals("", CategoryRuleNormalizer.normalize(null))
        assertEquals("", CategoryRuleNormalizer.normalize("   "))
        assertEquals("星巴克", CategoryRuleNormalizer.normalize("星巴克(上海）有限公司"))
    }

    @Test
    fun `归一化 剥空退回空串 长词优先不咬坏`() {
        assertEquals("", CategoryRuleNormalizer.normalize("有限公司"))
        // 「天猫超市」不含任何后缀词：不能被剥坏。
        assertEquals("天猫超市", CategoryRuleNormalizer.normalize("天猫超市"))
        // 裸「店」不在后缀表里：如家酒店不能变成「如家酒」。
        assertEquals("如家酒店", CategoryRuleNormalizer.normalize("如家酒店"))
    }

    // ---------- 匹配强度 ----------

    @Test
    fun `强度 原始串包含保持旧行为`() {
        // 旧规则（未归一化存储的历史数据）照旧命中。
        assertEquals(MatchStrength.STRONG, CategoryRuleNormalizer.matchStrength("面馆", "北京面馆"))
        assertEquals(MatchStrength.STRONG, CategoryRuleNormalizer.matchStrength("面馆", "罗森面馆(朝阳店)"))
    }

    @Test
    fun `强度 归一化后相等 跨写法跨门店`() {
        // 用户真实样例：原词「北京轨道交通」、新词「北京轨道交通有限公司」。
        assertEquals(
            MatchStrength.STRONG,
            CategoryRuleNormalizer.matchStrength("北京轨道交通", "北京轨道交通有限公司"),
        )
        assertEquals(
            MatchStrength.STRONG,
            CategoryRuleNormalizer.matchStrength("北京轨道交通有限公司", "北京轨道交通"),
        )
        // 同一商户的两种括号写法。
        assertEquals(
            MatchStrength.STRONG,
            CategoryRuleNormalizer.matchStrength("星巴克(上海)有限公司", "星巴克（上海）有限公司"),
        )
    }

    @Test
    fun `强度 归一化核心词被完整包含 算强匹配`() {
        // 原始串并不包含规则词（中间隔着括号），归一化后核心词「罗森朝阳」被完整包含 → STRONG。
        assertEquals(
            MatchStrength.STRONG,
            CategoryRuleNormalizer.matchStrength("罗森朝阳", "罗森(北京)朝阳房山店"),
        )
        assertEquals(
            MatchStrength.STRONG,
            CategoryRuleNormalizer.matchStrength("北京轨道交通", "北京轨道交通集团有限公司"),
        )
    }

    @Test
    fun `强度 公共前缀只给弱匹配`() {
        // 用户真实样例：规则词（朝阳门店）与新门店（房山店）公共前缀「罗森北京」。
        assertEquals(
            MatchStrength.WEAK,
            CategoryRuleNormalizer.matchStrength("罗森(北京)有限公司朝阳门店", "罗森(北京)有限公司房山店"),
        )
        // 已知局限（KDoc 记过）：中国移动/中国联通共享前缀「中国」，只能给 WEAK。
        assertEquals(MatchStrength.WEAK, CategoryRuleNormalizer.matchStrength("中国移动", "中国联通"))
    }

    @Test
    fun `强度 无公共前缀与空串给不匹配`() {
        assertEquals(MatchStrength.NONE, CategoryRuleNormalizer.matchStrength("汉堡", "麦当劳"))
        assertEquals(MatchStrength.NONE, CategoryRuleNormalizer.matchStrength("", "麦当劳"))
        assertEquals(MatchStrength.NONE, CategoryRuleNormalizer.matchStrength("汉堡", ""))
    }

    // ---------- 辅助 ----------

    @Test
    fun `coreLength 归一化后长度 空则退回原始长度`() {
        assertEquals(4, CategoryRuleNormalizer.coreLength("罗森(北京)有限公司朝阳门店"))
        assertEquals(6, CategoryRuleNormalizer.coreLength("北京轨道交通"))
        assertEquals(2, CategoryRuleNormalizer.coreLength("面馆"))
    }

    @Test
    fun `commonPrefixLength 按字符计`() {
        assertEquals(4, CategoryRuleNormalizer.commonPrefixLength("罗森北京朝阳", "罗森北京房山"))
        assertEquals(0, CategoryRuleNormalizer.commonPrefixLength("汉堡", "麦当劳"))
        assertTrue(CategoryRuleNormalizer.commonPrefixLength("a", "ab") == 1)
    }
}
