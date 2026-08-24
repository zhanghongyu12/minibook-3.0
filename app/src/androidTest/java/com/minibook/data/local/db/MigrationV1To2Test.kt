package com.minibook.data.local.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 数据库迁移 1→2 instrumented 测试（F-12，DEC-018，TASK-040「迁移往返」验收，ARCHITECTURE §16.10）。
 *
 * 用 Room [MigrationTestHelper] 构造 version 1 旧库（transactions 旧 10 列 + 一条手动记录）→
 * 经 [MiniBookDatabase.MIGRATION_1_2] 升级到 version 2 → 断言：
 * 1. 旧数据仍在（不丢数据，无破坏重建）；
 * 2. 新列语义：`source='manual'`、`externalId/counterparty/paymentMethod` 为 NULL（手动记录）；
 * 3. 唯一索引 `UNIQUE(ledgerId, externalId)` 生效——同 (ledgerId, externalId) 重复插入抛
 *    [SQLiteConstraintException]；
 * 4. `runMigrationsAndValidate(validateDroppedTables=true)` 会对照导出的 `2.json` 校验收件 schema
 *    （列/默认值/索引名逐字一致），迁移偏差在此大声失败。
 *
 * 依赖：`app/schemas/`（exportSchema=true 导出的 1.json/2.json）经 build.gradle.kts
 * `androidTest.assets.srcDir("schemas")` 打包进测试 APK assets（Tester 补迁移测试基建）。
 * 运行：真机/模拟器 `./gradlew :app:connectedDebugAndroidTest`（需设备授权）。
 */
class MigrationV1To2Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MiniBookDatabase::class.java,
    )

    @Test
    /** 迁移 1→2：保留旧数据、新列默认（source=manual、三列 NULL）、唯一索引(ledgerId, externalId)生效。 */
    fun migrationV1To2KeepsDataAndAppliesDefaultsAndUniqueIndex() {
        // 1) 造 v1 库：旧 10 列 + 一条手动记录。
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL(
                "INSERT INTO transactions " +
                    "(id, ledgerId, type, amount, categoryId, description, date, createdAt, updatedAt, syncStatus) " +
                    "VALUES (" +
                    "'tx-legacy-1','ledger-1','expense',1000,'cat-1',NULL," +
                    "'2026-08-18','2026-08-18T00:00:00Z','2026-08-18T00:00:00Z','local'" +
                    ")",
            )
        }

        // 2) 迁移到 v2 并对照 2.json 校验 schema（列/默认值/唯一索引名逐字一致）。
        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true, MiniBookDatabase.MIGRATION_1_2)

        // 3) 旧数据仍在 + 新列语义（source=manual、三列 NULL）。
        db.query(
            "SELECT source, externalId, counterparty, paymentMethod FROM transactions WHERE id='tx-legacy-1'",
        ).use { c ->
            assertTrue("旧交易记录应保留", c.moveToFirst())
            assertEquals("manual", c.getString(c.getColumnIndexOrThrow("source")))
            assertTrue("手动记录 externalId 应为 NULL", c.isNull(c.getColumnIndexOrThrow("externalId")))
            assertTrue("手动记录 counterparty 应为 NULL", c.isNull(c.getColumnIndexOrThrow("counterparty")))
            assertTrue("手动记录 paymentMethod 应为 NULL", c.isNull(c.getColumnIndexOrThrow("paymentMethod")))
        }

        // 4) 唯一索引生效：同 (ledgerId, externalId) 重复插入报 SQLiteConstraintException。
        db.execSQL(v2InsertWithExternalId("tx-w1", "wechat:x"))
        assertThrows("唯一索引应拦截重复 externalId", SQLiteConstraintException::class.java) {
            db.execSQL(v2InsertWithExternalId("tx-w2", "wechat:x"))
        }

        // 5) NULL externalId 互异（手动记录不参与去重）：两条 NULL externalId 均插入成功。
        db.execSQL(v2InsertWithoutExternalId("tx-null-1"))
        db.execSQL(v2InsertWithoutExternalId("tx-null-2"))
        db.query("SELECT COUNT(*) AS c FROM transactions WHERE externalId IS NULL").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(3, c.getInt(c.getColumnIndexOrThrow("c"))) // tx-legacy-1 + tx-null-1 + tx-null-2
        }

        db.close()
    }

    /** v2 全 14 列插入（含 externalId），其余可空三列置 NULL、source 用默认 'manual'。 */
    private fun v2InsertWithExternalId(id: String, externalId: String): String =
        "INSERT INTO transactions " +
            "(id, ledgerId, type, amount, categoryId, description, date, createdAt, updatedAt, syncStatus, source, externalId) " +
            "VALUES ('$id','ledger-1','expense',100,'cat-1',NULL,'2026-08-18','2026-08-18T00:00:00Z','2026-08-18T00:00:00Z','local','manual','$externalId')"

    /** v2 插入（externalId 缺省 NULL），验证 NULL 互异不冲突。 */
    private fun v2InsertWithoutExternalId(id: String): String =
        "INSERT INTO transactions " +
            "(id, ledgerId, type, amount, categoryId, description, date, createdAt, updatedAt, syncStatus) " +
            "VALUES ('$id','ledger-1','expense',100,'cat-1',NULL,'2026-08-18','2026-08-18T00:00:00Z','2026-08-18T00:00:00Z','local')"

    private companion object {
        const val DB_NAME = "migration-1-2-test"
    }
}