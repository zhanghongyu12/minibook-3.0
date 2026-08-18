# MiniBook ProGuard 规则（TASK-001 最小占位，release 未开启 minify）

# SQLCipher（TASK-004 细化）
# -keep class net.zetetic.database.** { *; }

# Room（TASK-003 细化）
# -keep class * extends androidx.room.RoomDatabase { *; }
# -keep class * implements androidx.room.RoomOpenHelper.Delegate { *; }