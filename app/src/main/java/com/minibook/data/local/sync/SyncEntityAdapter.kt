package com.minibook.data.local.sync

import com.minibook.data.local.db.dao.SyncDao
import com.minibook.data.local.db.entity.BudgetEntity
import com.minibook.data.local.db.entity.CategoryEntity
import com.minibook.data.local.db.entity.DebtEntity
import com.minibook.data.local.db.entity.LedgerEntity
import com.minibook.data.local.db.entity.TransactionEntity
import com.minibook.data.local.db.entity.UserCategoryRuleEntity
import com.minibook.domain.usecase.sync.SyncEntityType
import com.minibook.domain.usecase.sync.SyncLocalRow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * 「本地业务行 ↔ 云端 payload」适配器（F-15 / DEC-033，`docs/14_cloud_sync.md §5.2`）。
 *
 * 每类实体一个实现，只做三件事：**全量投影**、**按 payload 落地（REPLACE）**、**按 id 删除**。
 * 之所以能这么薄，是因为 6 张实体都标了 `@Serializable`，Room 行**原样**就是 payload
 * （camelCase 键名与服务端白名单逐字一致）——少一层手写 mapper，就少一类静默错位。
 *
 * 全量投影（而非按 `updatedAt >= 游标` 取增量）是**有意为之**：上行判据改成「与 `sync_state`
 * 基线不一致」，既避免了时钟回拨漏推，也让「墓碑差分」拿到**完整**本地集合
 * （若只取增量，未加载的行会被误判成「本地已删」，进而误发墓碑删掉云端数据）。
 * 个人账本量级下，全量投影 + 编码开销可忽略（见 §12.2 差分说明）。
 */
interface SyncEntityAdapter {

    val type: SyncEntityType

    /** 本地该类型**全部**行（含 payload JSON）。 */
    suspend fun allRows(): List<SyncLocalRow>

    /** 把云端 payload 落库（REPLACE 语义，幂等）。 */
    suspend fun applyPayload(entityId: String, ledgerId: String, payloadJson: String)

    /** 按 id 删除本地行（云端墓碑下发 / 冲突云端胜）。 */
    suspend fun deleteById(entityId: String)
}

/** 6 类适配器的注册表；[SyncUseCase][com.minibook.domain.usecase.sync.SyncUseCase] 经它访问全部实体。 */
class SyncRegistry(
    private val dao: SyncDao,
    private val json: Json,
) {
    val adapters: List<SyncEntityAdapter> = listOf(
        LedgerAdapter(dao, json),
        CategoryAdapter(dao, json),
        TransactionAdapter(dao, json),
        DebtAdapter(dao, json),
        BudgetAdapter(dao, json),
        UserCategoryRuleAdapter(dao, json),
    )

    fun forType(type: SyncEntityType): SyncEntityAdapter =
        adapters.firstOrNull { it.type == type }
            ?: error("未注册的实体类型：$type")

    /** 全部 6 类本地行（一次同步只取一次，保证同一批次内判定一致）。 */
    suspend fun allLocalRows(): List<SyncLocalRow> = adapters.flatMap { it.allRows() }
}

/** 只在同步期间使用的「空游标」——`updatedAt >= ''` 恒真，即取全表。 */
private const val ALL_ROWS = ""

/** 账本自身即作用域：账本没有 `ledgerId` 列，信封/基线的 `ledgerId` 用**账本自己的 id**。 */
private class LedgerAdapter(private val dao: SyncDao, private val json: Json) : SyncEntityAdapter {
    override val type = SyncEntityType.LEDGER

    override suspend fun allRows(): List<SyncLocalRow> =
        dao.ledgersSince(ALL_ROWS).map {
            SyncLocalRow(
                type = type,
                id = it.id,
                ledgerId = it.id,
                updatedAt = it.updatedAt,
                payloadJson = json.encodeToString(LedgerEntity.serializer(), it),
            )
        }

    override suspend fun applyPayload(entityId: String, ledgerId: String, payloadJson: String) {
        val entity = json.decodeFromString(LedgerEntity.serializer(), payloadJson)
        require(entity.id == entityId) { "ledger payload id 与 entity_id 不一致：${entity.id} vs $entityId" }
        dao.upsertLedger(entity)
    }

    override suspend fun deleteById(entityId: String) = dao.deleteLedgerById(entityId)
}

private class CategoryAdapter(private val dao: SyncDao, private val json: Json) : SyncEntityAdapter {
    override val type = SyncEntityType.CATEGORY

    override suspend fun allRows(): List<SyncLocalRow> =
        dao.categoriesSince(ALL_ROWS).map {
            SyncLocalRow(
                type = type,
                id = it.id,
                ledgerId = it.ledgerId,
                updatedAt = it.updatedAt,
                payloadJson = json.encodeToString(CategoryEntity.serializer(), it),
            )
        }

    override suspend fun applyPayload(entityId: String, ledgerId: String, payloadJson: String) {
        val entity = json.decodeFromString(CategoryEntity.serializer(), payloadJson)
        require(entity.id == entityId) { "category payload id 不一致：${entity.id} vs $entityId" }
        require(entity.ledgerId == ledgerId) { "category payload ledgerId 不一致：${entity.ledgerId} vs $ledgerId" }
        dao.upsertCategory(entity)
    }

    override suspend fun deleteById(entityId: String) = dao.deleteCategoryById(entityId)
}

private class TransactionAdapter(private val dao: SyncDao, private val json: Json) : SyncEntityAdapter {
    override val type = SyncEntityType.TRANSACTION

    override suspend fun allRows(): List<SyncLocalRow> =
        dao.transactionsSince(ALL_ROWS).map {
            SyncLocalRow(
                type = type,
                id = it.id,
                ledgerId = it.ledgerId,
                updatedAt = it.updatedAt,
                payloadJson = json.encodeToString(TransactionEntity.serializer(), it),
            )
        }

    override suspend fun applyPayload(entityId: String, ledgerId: String, payloadJson: String) {
        val entity = json.decodeFromString(TransactionEntity.serializer(), payloadJson)
        require(entity.id == entityId) { "transaction payload id 不一致：${entity.id} vs $entityId" }
        require(entity.ledgerId == ledgerId) { "transaction payload ledgerId 不一致：${entity.ledgerId} vs $ledgerId" }
        dao.upsertTransaction(entity)
    }

    override suspend fun deleteById(entityId: String) = dao.deleteTransactionById(entityId)
}

private class DebtAdapter(private val dao: SyncDao, private val json: Json) : SyncEntityAdapter {
    override val type = SyncEntityType.DEBT

    override suspend fun allRows(): List<SyncLocalRow> =
        dao.debtsSince(ALL_ROWS).map {
            SyncLocalRow(
                type = type,
                id = it.id,
                ledgerId = it.ledgerId,
                updatedAt = it.updatedAt,
                payloadJson = json.encodeToString(DebtEntity.serializer(), it),
            )
        }

    override suspend fun applyPayload(entityId: String, ledgerId: String, payloadJson: String) {
        val entity = json.decodeFromString(DebtEntity.serializer(), payloadJson)
        require(entity.id == entityId) { "debt payload id 不一致：${entity.id} vs $entityId" }
        require(entity.ledgerId == ledgerId) { "debt payload ledgerId 不一致：${entity.ledgerId} vs $ledgerId" }
        dao.upsertDebt(entity)
    }

    override suspend fun deleteById(entityId: String) = dao.deleteDebtById(entityId)
}

private class BudgetAdapter(private val dao: SyncDao, private val json: Json) : SyncEntityAdapter {
    override val type = SyncEntityType.BUDGET

    override suspend fun allRows(): List<SyncLocalRow> =
        dao.budgetsSince(ALL_ROWS).map {
            SyncLocalRow(
                type = type,
                id = it.id,
                ledgerId = it.ledgerId,
                updatedAt = it.updatedAt,
                payloadJson = json.encodeToString(BudgetEntity.serializer(), it),
            )
        }

    override suspend fun applyPayload(entityId: String, ledgerId: String, payloadJson: String) {
        val entity = json.decodeFromString(BudgetEntity.serializer(), payloadJson)
        require(entity.id == entityId) { "budget payload id 不一致：${entity.id} vs $entityId" }
        require(entity.ledgerId == ledgerId) { "budget payload ledgerId 不一致：${entity.ledgerId} vs $ledgerId" }
        dao.upsertBudget(entity)
    }

    override suspend fun deleteById(entityId: String) = dao.deleteBudgetById(entityId)
}

private class UserCategoryRuleAdapter(private val dao: SyncDao, private val json: Json) : SyncEntityAdapter {
    override val type = SyncEntityType.USER_CATEGORY_RULE

    override suspend fun allRows(): List<SyncLocalRow> =
        dao.userCategoryRulesSince(ALL_ROWS).map {
            SyncLocalRow(
                type = type,
                id = it.id,
                ledgerId = it.ledgerId,
                updatedAt = it.updatedAt,
                payloadJson = json.encodeToString(UserCategoryRuleEntity.serializer(), it),
            )
        }

    override suspend fun applyPayload(entityId: String, ledgerId: String, payloadJson: String) {
        val entity = json.decodeFromString(UserCategoryRuleEntity.serializer(), payloadJson)
        require(entity.id == entityId) { "rule payload id 不一致：${entity.id} vs $entityId" }
        require(entity.ledgerId == ledgerId) { "rule payload ledgerId 不一致：${entity.ledgerId} vs $ledgerId" }
        dao.upsertUserCategoryRule(entity)
    }

    override suspend fun deleteById(entityId: String) = dao.deleteUserCategoryRuleById(entityId)
}

/** 便于测试断言「6 类都注册了、且序列化器键集与实体一致」。 */
internal fun serializerOf(type: SyncEntityType): KSerializer<*> = when (type) {
    SyncEntityType.LEDGER -> LedgerEntity.serializer()
    SyncEntityType.CATEGORY -> CategoryEntity.serializer()
    SyncEntityType.TRANSACTION -> TransactionEntity.serializer()
    SyncEntityType.DEBT -> DebtEntity.serializer()
    SyncEntityType.BUDGET -> BudgetEntity.serializer()
    SyncEntityType.USER_CATEGORY_RULE -> UserCategoryRuleEntity.serializer()
}
