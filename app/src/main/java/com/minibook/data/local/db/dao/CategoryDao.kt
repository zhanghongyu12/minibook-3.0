package com.minibook.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.minibook.data.local.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 分类 DAO，严格对齐 DATABASE §6.3。
 *
 * - [upsertList]（REPLACE）供种子写入 / 导入覆盖使用（SEED 幂等，§8.3）。
 * - [deleteCustom] 携带 `isBuiltIn=0` 守卫，防止误删内置分类（DEC-007：
 *   内置禁删，删除保护下沉到 SQL 层兜底，业务层另做校验）。
 * - [countRef] 为删除前关联检查（命中 `(ledgerId, categoryId)` 索引）。
 */
@Dao
interface CategoryDao {

    /** 种子 / 导入批量写入：主键+账本冲突即整行替换（REPLACE 语义）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertList(categories: List<CategoryEntity>)

    @Insert
    suspend fun insert(category: CategoryEntity)

    @Query(
        """
        UPDATE categories
        SET name=:name, icon=:icon, color=:color, `order`=:order, updatedAt=:updatedAt
        WHERE id=:id AND ledgerId=:ledgerId
        """,
    )
    suspend fun update(
        id: String,
        ledgerId: String,
        name: String,
        icon: String,
        color: String,
        order: Int,
        updatedAt: String,
    )

    /** 防误删内置：`isBuiltIn=0` 守卫，内置分类（1）天然删不掉（DEC-007 / §6.3）。 */
    @Query("DELETE FROM categories WHERE id=:id AND ledgerId=:ledgerId AND isBuiltIn=0")
    suspend fun deleteCustom(id: String, ledgerId: String)

    /** 按类型取分类，order 升序、同序按名（「其他」order=99 恒末尾）。 */
    @Query("SELECT * FROM categories WHERE ledgerId=:ledgerId AND type=:type ORDER BY `order` ASC, name ASC")
    fun observeByType(ledgerId: String, type: String): Flow<List<CategoryEntity>>

    /** 无语义一用：种子上幂等守卫（`countByLedger==0` 才 clone，§8.3）。 */
    @Query("SELECT COUNT(*) FROM categories WHERE ledgerId=:ledgerId")
    suspend fun countByLedger(ledgerId: String): Long

    /** 删除前关联检查：该分类被引用的交易数（>0 需走迁移流程，A-1）。 */
    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId=:categoryId")
    suspend fun countRef(categoryId: String): Int
}