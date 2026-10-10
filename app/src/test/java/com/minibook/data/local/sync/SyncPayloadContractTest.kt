package com.minibook.data.local.sync

import com.minibook.domain.usecase.sync.SyncEntityType
import java.io.File
import kotlinx.serialization.KSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * **跨语言契约守卫**：Android 实体序列化出的 JSON 键集，必须与服务端
 * `server/app/entity_spec.py` 的字段白名单**逐字一致**（F-15 / DEC-033）。
 *
 * 为什么值得写：payload 就是「Room 行原样 JSON」，两侧任何一边改名/加列（例如给
 * `transactions` 加一列却忘了加白名单）都会在**真机同步时**才炸，而且报错是服务端
 * `validation_error: 未知字段`，排查成本高。这个测试把漂移挡在 JVM 单测里。
 *
 * 找不到 `server/` 时用 [Assume] 跳过（例如只 checkout 了 `app/` 子目录），
 * 跳过会如实记成 skipped，**不**冒充通过。
 */
class SyncPayloadContractTest {

    @Test
    fun `本地实体序列化键集与服务端白名单逐字一致`() {
        val specFile = candidatePaths().firstOrNull { it.isFile }
        Assume.assumeTrue(
            "未找到 server/app/entity_spec.py，跳过跨语言契约校验",
            specFile != null,
        )

        val specs = parseEntitySpecs(specFile!!)

        // 解析自检：解析不出来要大声失败，不能"零个类型"也算过。
        assertEquals("服务端白名单应解析出 6 类实体", 6, specs.size)
        assertEquals(
            "实体类型名集合必须与 SyncEntityType 一致",
            SyncEntityType.entries.map { it.wire }.toSet(),
            specs.keys,
        )
        specs.forEach { (type, fields) ->
            assertTrue("$type 的字段数异常（疑似解析失败）", fields.size >= 6)
        }

        for (type in SyncEntityType.entries) {
            val localKeys = elementNames(serializerOf(type))
            val serverKeys = specs.getValue(type.wire)
            assertEquals(
                "$type 的字段集必须与服务端白名单逐字一致（少字段会被服务端拒、多字段会被判未知字段）",
                serverKeys,
                localKeys,
            )
        }
    }

    private fun elementNames(serializer: KSerializer<*>): Set<String> {
        val descriptor = serializer.descriptor
        return (0 until descriptor.elementsCount)
            .map { descriptor.getElementName(it) }
            .toSet()
    }

    /** 逐行解析 `ENTITY_SPECS`（结构固定：4 空格缩进的 `"type": {` + 8 空格缩进的 `"field": _f(`）。 */
    private fun parseEntitySpecs(file: File): Map<String, Set<String>> {
        val typePattern = Regex("^ {4}\"([A-Za-z0-9_]+)\": \\{$")
        val fieldPattern = Regex("^ {8}\"([A-Za-z0-9_]+)\": _f\\(")
        val result = linkedMapOf<String, MutableSet<String>>()
        var inSpecs = false
        var current: MutableSet<String>? = null

        file.readLines(Charsets.UTF_8).forEach { line ->
            if (!inSpecs) {
                if (line.startsWith("ENTITY_SPECS")) inSpecs = true
                return@forEach
            }
            if (line == "}") {
                inSpecs = false
                return@forEach
            }
            val typeMatch = typePattern.find(line)
            if (typeMatch != null) {
                current = mutableSetOf<String>().also { result[typeMatch.groupValues[1]] = it }
                return@forEach
            }
            fieldPattern.find(line)?.let { current?.add(it.groupValues[1]) }
        }
        return result
    }

    private fun candidatePaths(): List<File> = listOf(
        File("../server/app/entity_spec.py"), // Gradle 单测工作目录 = app/
        File("server/app/entity_spec.py"),
        File("../../server/app/entity_spec.py"),
    )
}
