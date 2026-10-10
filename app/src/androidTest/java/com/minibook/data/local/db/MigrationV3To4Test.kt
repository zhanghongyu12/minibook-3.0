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
 * 数据库迁移 3→4 instrumented 测试（F-15 / DEC-033，`docs/14_cloud_sync.md §5.3`）。
 *
 * 覆盖点：
 * 1. **旧数据保留**：v3 的 `transactions` / `sync_changes` 行迁移后仍在（证明没走破坏性重建）；
 * 2. **新表可用**：`sync_state` 复合主键 `(entityType, entityId)` 生效（同键重复插入抛
 *    [SQLiteConstraintException]）、`sync_conflicts` 可写入并读回；
 * 3. `runMigrationsAndValidate(..., 4, ...)` 对照导出的 `4.json` 校验收件 schema
 *    （列/类型/主键/索引名逐字一致），迁移与实体漂移在此大声失败。
 *
 * **运行前提（尚未执行，如实挂账）**：instrumented 测试需要真机或模拟器
 * （`./gradlew :app:connectedDebugAndroidTest`）。真机 `A1B2C3D4` 上装着**用户真实数据的正式版**，
 * 安装 debug 测试 APK 会因签名不同而要求卸载 ⇒ **不得**在真机上跑；需先获批启动模拟器。
 * 在此之前，「迁移在真机上不丢数据」只能由本文件 + `4.json` 的静态比对（既有 7 表 CREATE/索引逐字未变）来保证。
 */
class MigrationV3To4Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MiniBookDatabase::class.java,
    )

    @Test
    fun migrationV3To4KeepsLegacyRowsAndCreatesSyncTables() {
        // 1) 造 v3 库：一条账本、一条交易、一条 sync_changes（既有表数据必须活着穿过迁移）。
        helper.createDatabase(DB_NAME, 3).use { db ->
            db.execSQL(
                "INSERT INTO ledgers (id, name, icon, color, isDefault, position, createdAt, updatedAt) " +
                    "VALUES ('ledger-1','默认账本','wallet','#FF5722',1,0," +
                    "'2026-10-10T00:00:00Z','2026-10-10T00:00:00Z')",
            )
            db.execSQL(
                "INSERT INTO transactions " +
                    "(id, ledgerId, type, amount, categoryId, description, date, createdAt, updatedAt, syncStatus) " +
                    "VALUES ('tx-v3-1','ledger-1','expense',1234,'cat-1','v3 旧数据'," +
                    "'2026-10-10','2026-10-10T00:00:00Z','2026-10-10T00:00:00Z','local')",
            )
            db.execSQL(
                "INSERT INTO sync_changes (id, ledgerId, entityType, entityId, action, timestamp, data) " +
                    "VALUES ('chg-1','ledger-1','transaction','tx-v3-1','delete'," +
                    "'2026-10-10T00:00:01Z',NULL)",
            )
        }

        // 2) 迁移到 v4 并对照 4.json 校验（列/主键/索引名逐字一致）。
        val db = helper.runMigrationsAndValidate(DB_NAME, 4, true, MiniBookDatabase.MIGRATION_3_4)

        // 3) 旧数据仍在。
        db.query("SELECT amount, description FROM transactions WHERE id='tx-v3-1'").use { c ->
            assertTrue("v3 交易行必须保留", c.moveToFirst())
            assertEquals(1234L, c.getLong(c.getColumnIndexOrThrow("amount")))
            assertEquals("v3 旧数据", c.getString(c.getColumnIndexOrThrow("description")))
        }
        db.query("SELECT action FROM sync_changes WHERE id='chg-1'").use { c ->
            assertTrue("v3 sync_changes 行必须保留（本轮起不再写入，但历史行不清）", c.moveToFirst())
            assertEquals("delete", c.getString(c.getColumnIndexOrThrow("action")))
        }

        // 4) 新表可用：sync_state 写入 + 读回。
        db.execSQL(
            "INSERT INTO sync_state (entityType, entityId, ledgerId, baseUpdatedAt, syncedAt, serverSeq) " +
                "VALUES ('transaction','tx-v3-1','ledger-1','2026-10-10T00:00:00Z','2026-10-10T00:01:00Z',7)",
        )
        db.query("SELECT serverSeq FROM sync_state WHERE entityType='transaction' AND entityId='tx-v3-1'")
            .use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(7L, c.getLong(c.getColumnIndexOrThrow("serverSeq")))
            }

        // 5) 复合主键生效：同 (entityType, entityId) 再插一行被拦截。
        assertThrows("sync_state 复合主键应拦截重复键", SQLiteConstraintException::class.java) {
            db.execSQL(
                "INSERT INTO sync_state (entityType, entityId, ledgerId, baseUpdatedAt, syncedAt, serverSeq) " +
                    "VALUES ('transaction','tx-v3-1','ledger-1','2026-10-10T09:00:00Z','2026-10-10T09:00:00Z',9)",
            )
        }

        // 6) sync_conflicts 可写入并读回；resolvedAt/resolution 允许为 NULL（二期人工处理预留）。
        db.execSQL(
            "INSERT INTO sync_conflicts " +
                "(id, entityType, entityId, ledgerId, localJson, remoteJson, detectedAt) " +
                "VALUES ('c-1','transaction','tx-v3-1','ledger-1','{\"amount\":1234}','{\"amount\":9999}'," +
                "'2026-10-10T00:02:00Z')",
        )
        db.query("SELECT localJson, remoteJson, resolvedAt, resolution FROM sync_conflicts WHERE id='c-1'")
            .use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("""{"amount":1234}""", c.getString(c.getColumnIndexOrThrow("localJson")))
                assertTrue("未处理的冲突 resolvedAt 应为 NULL", c.isNull(c.getColumnIndexOrThrow("resolvedAt")))
                assertTrue("未处理的冲突 resolution 应为 NULL", c.isNull(c.getColumnIndexOrThrow("resolution")))
            }

        db.close()
    }

    private companion object {
        const val DB_NAME = "migration-3-4-test"
    }
}
