package com.minibook.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 数据库迁移 4→5 instrumented 测试（账单转债务 / DEC-056）。
 *
 * 覆盖点：
 * 1. **旧数据保留**：v4 的 `transactions` 行迁移后金额/分类/来源等字段毫发无损（证明没走破坏性重建）；
 * 2. **新列默认空**：存量行的 `debtId` 为 NULL ⇒ 一律视为「未转债务」，统计口径无需回填；
 * 3. **新列可写**：迁移后把某行 `debtId` 置值可写入并读回（关联能力真的可用）；
 * 4. `runMigrationsAndValidate(..., 5, ...)` 对照导出的 `5.json` 校验收件 schema
 *    （列名/类型/NULL 约束逐字一致），迁移 DDL 与 `TransactionEntity` 漂移在这里大声失败。
 *
 * **运行前提（尚未执行，如实挂账）**：instrumented 测试需要真机或模拟器
 * （`./gradlew :app:connectedDebugAndroidTest`）。真机 `A1B2C3D4` 装的是用户真实数据正式版，
 * 装 debug 测试 APK 会因签名不同要求卸载 ⇒ 不得在真机上跑；需先获批启动模拟器。
 * 在此之前，「迁移在真机上不丢数据」由本文件 + `5.json` 与 `4.json` 的静态 diff（仅多 1 个可空列）
 * 共同保证。
 */
class MigrationV4To5Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MiniBookDatabase::class.java,
    )

    @Test
    fun migrationV4To5AddsNullableDebtIdAndKeepsLegacyRows() {
        // 1) 造 v4 库：一条账本 + 一条已同步的旧交易（v4 的 transactions 无 debtId 列）。
        helper.createDatabase(DB_NAME, 4).use { db ->
            db.execSQL(
                "INSERT INTO ledgers (id, name, icon, color, isDefault, position, createdAt, updatedAt) " +
                    "VALUES ('ledger-1','默认账本','wallet','#FF5722',1,0," +
                    "'2026-10-10T00:00:00Z','2026-10-10T00:00:00Z')",
            )
            db.execSQL(
                "INSERT INTO transactions " +
                    "(id, ledgerId, type, amount, categoryId, description, date, createdAt, updatedAt, " +
                    "syncStatus, externalId, counterparty, paymentMethod, source) " +
                    "VALUES ('tx-v4-1','ledger-1','expense',12800,'cat-1','v4 旧数据','2026-10-20'," +
                    "'2026-10-20T00:00:00Z','2026-10-20T00:00:00Z','synced','ext-1','张三','wechat','wechat_import')",
            )
        }

        // 2) 迁移到 v5 并对照 5.json 校验（列名/类型/NULL 约束逐字一致）。
        val db = helper.runMigrationsAndValidate(DB_NAME, 5, true, MiniBookDatabase.MIGRATION_4_5)

        // 3) 存量行完好 + 新列默认为 NULL（= 未转债务）。
        db.query(
            "SELECT amount, categoryId, source, counterparty, externalId, debtId FROM transactions WHERE id='tx-v4-1'",
        ).use { c ->
            assertTrue("v4 交易行必须保留", c.moveToFirst())
            assertEquals(12800L, c.getLong(c.getColumnIndexOrThrow("amount")))
            assertEquals("cat-1", c.getString(c.getColumnIndexOrThrow("categoryId")))
            assertEquals("wechat_import", c.getString(c.getColumnIndexOrThrow("source")))
            assertEquals("张三", c.getString(c.getColumnIndexOrThrow("counterparty")))
            assertEquals("ext-1", c.getString(c.getColumnIndexOrThrow("externalId")))
            assertTrue(
                "存量行 debtId 必须为 NULL（未转债务，无需回填）",
                c.isNull(c.getColumnIndexOrThrow("debtId")),
            )
        }

        // 4) 新列可写可读：迁移后能建立「账单 → 债务」关联。
        db.execSQL(
            "UPDATE transactions SET debtId='debt-v5-1', updatedAt='2026-10-21T00:00:00Z' WHERE id='tx-v4-1'",
        )
        db.query("SELECT debtId FROM transactions WHERE id='tx-v4-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("debt-v5-1", c.getString(c.getColumnIndexOrThrow("debtId")))
        }

        db.close()
    }

    private companion object {
        const val DB_NAME = "migration-4-5-test"
    }
}
