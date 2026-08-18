package com.minibook.data.local.db

import androidx.room.TypeConverter

/**
 * Room TypeConverter 集合，对齐 DATABASE §9.6。
 *
 * 转码口径：
 * - Boolean ↔ Int：`INTEGER(0/1)`（category.isBuiltIn / ledger.isDefault）。
 *   Room 原生即按 INTEGER 0/1 存 Boolean，此处显式声明作为统一转码约定（§9.6 要求）。
 * - 枚举字段（type/kind/direction/entityType/action 等）在 Entity 层一律以 `String` 存储
 *   （DATABASE §4 实体定义即 String 列），枚举↔String 转码归属 domain 层（TASK-006
 *   Enums + TASK-008 mapper），避免在数据层重复定义枚举类型。
 * - 日期/时间（date `yyyy-MM-dd`、month `yyyy-MM`、createdAt 等 ISO 8601 完整时间戳）
 *   均以 `String` 存储，字典序即时间序，无需 LocalDate 转换（§9.5）。
 */
class Converter {

    @TypeConverter
    fun intToBoolean(value: Int): Boolean = value == 1

    @TypeConverter
    fun booleanToInt(value: Boolean): Int = if (value) 1 else 0
}