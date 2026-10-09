# MiniBook ProGuard 规则（TASK-001 最小占位，release 未开启 minify）

# SQLCipher（TASK-004 细化）
# -keep class net.zetetic.database.** { *; }

# Room（TASK-003 细化）
# -keep class * extends androidx.room.RoomDatabase { *; }
# -keep class * implements androidx.room.RoomOpenHelper.Delegate { *; }

# JavaMail（F-13 邮件账单导入，ImapMailClient；release 当前未开 minify，规则先备好）
# Android 上不存在 AWT/Beans/SASL，JavaMail 引用其类名会产生 benign warning，抑制之。
-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn javax.security.sasl.**
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }