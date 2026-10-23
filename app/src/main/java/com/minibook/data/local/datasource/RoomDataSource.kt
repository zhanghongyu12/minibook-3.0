package com.minibook.data.local.datasource

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.minibook.data.datasource.LedgerDataSource
import com.minibook.data.local.db.dao.LedgerDao
import com.minibook.data.local.security.DatabaseGate
import com.minibook.data.mapper.LedgerMapper
import com.minibook.domain.model.Ledger
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room 数据源聚合标记（TASK-008），对齐 API §2。
 *
 * 语义：标识「Room 本地实现」这一聚合入口。**本标记不继承 7 个 `*DataSource` 接口**——
 * `TransactionDataSource.getById(id): Transaction?` 与 `LedgerDataSource.getById(id): Ledger?`
 * 同签名（`getById(String)`）异返回类型，JVM 方法签名不含返回类型，单类无法同时实现；
 * 故 [RoomDataSourceImpl] 直接实现 6 接口（Transaction/Debt/Category/Budget/SyncChange/UserCategoryRule），
 * `LedgerDataSource` 由独立 [LedgerDataSourceAdapter] 实现（同一 DatabaseGate，见 RoomDataSourceImpl）。
 */
interface RoomDataSource {
    // 聚合标记，无额外方法。
}

/**
 * 账本数据源实现（TASK-008）。
 *
 * 因 `getById` 签名冲突（见 [RoomDataSource]）无法并入 [RoomDataSourceImpl] 直接实现，
 * 故独立成类，由 `DataSourceModule` 的 `@Binds` 绑定 `LedgerDataSource → 本类`；
 * 本类构造注入 [DatabaseGate]，每方法取当前已打开库的 DAO，锁周期安全（ARCHITECTURE §7.5）。
 *
 * `update` 走原始 SQL（`LedgerDao` 无 update 方法，DATABASE §6.4 仅
 * insert/observeAll/getDefault/getById/count），更新后经 DAO getById 回读返回（API §6）。
 */
@Singleton
class LedgerDataSourceAdapter @Inject constructor(
    private val gate: DatabaseGate,
) : LedgerDataSource {

    override suspend fun insert(ledger: Ledger): String {
        val now = Instant.now().toString()
        val id = UUID.randomUUID().toString()
        ledgerDao().insert(LedgerMapper.toEntity(ledger.copy(id = id, createdAt = now, updatedAt = now)))
        return id
    }

    override suspend fun update(ledger: Ledger): Ledger {
        gate.database?.openHelper?.writableDatabase?.update(
            "ledgers",
            SQLiteDatabase.CONFLICT_NONE,
            ContentValues().apply {
                put("name", ledger.name)
                put("icon", ledger.icon)
                put("color", ledger.color)
                put("isDefault", if (ledger.isDefault) 1 else 0)
                put("position", ledger.position)
                put("updatedAt", Instant.now().toString())
            },
            "id = ?",
            arrayOf(ledger.id),
        ) ?: error("DatabaseGate 未解锁/未打开，无法访问数据库")
        return requireNotNull(ledgerDao().getById(ledger.id)) {
            "更新账本后回读失败: id=${ledger.id}"
        }.let(LedgerMapper::toDomain)
    }

    override suspend fun getById(id: String): Ledger? =
        ledgerDao().getById(id)?.let(LedgerMapper::toDomain)

    /** 按 id 删除账本行（删账本时最后一步，TASK-012；LedgerDao 无 delete → 原始 SQL，同 update 逃生舱）。 */
    override suspend fun deleteById(id: String) {
        gate.database?.openHelper?.writableDatabase?.delete(
            "ledgers",
            "id = ?",
            arrayOf(id),
        ) ?: error("DatabaseGate 未解锁/未打开，无法访问数据库")
    }

    override suspend fun getDefault(): Ledger? =
        ledgerDao().getDefault()?.let(LedgerMapper::toDomain)

    override fun observeAll(): Flow<List<Ledger>> =
        gate.gatedFlow { db ->
            db.ledgerDao().observeAll().map { list -> list.map(LedgerMapper::toDomain) }
        }

    private fun ledgerDao(): LedgerDao = gate.database?.ledgerDao()
        ?: error("DatabaseGate 未解锁/未打开，无法提供 LedgerDao")
}
