package com.minibook.data.local.security

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.minibook.data.local.db.MiniBookDatabase
import java.util.Arrays
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * SQLCipher 数据库「解密门」（ARCHITECTURE §7.1/§7.3、TASK-004）。
 *
 * - [open] 用解出的 SQLCipher passphrase 构建 Room + `SQLCipherOpenHelperFactory`
 *   （即 [SupportOpenHelperFactory]），并**在 SQLCipher 打开成功后**触发 Room migration
 *   （DATABASE §7.2：任何版本只在 SQLCipher 打开成功后执行 schema 迁移）。
 * - passphrase 用后立即 `Arrays.fill` 清零（ARCHITECTURE §7.2 / 代码审查点）。
 * - **未解锁（未 open）时 [database] 为 null，不提供任何可用连接**
 *   （TASK-004 验收：未解锁时 DatabaseGate 不提供可用连接）。
 *
 * 线程安全：open/close 以 [lock] 互斥；[database]/[isOpen] 经 [AtomicReference] 读取。
 *
 * @param context 应用上下文（构建 RoomDatabase）
 * @param keyManager 用于内部静默取 passphrase（锁关闭路径，DEC-002）；构造注入避免
 *                   在 DI 中直接 @Provides passphrase（防止循环依赖）。
 */
class DatabaseGate(
    private val context: Context,
    private val keyManager: KeyManager,
) : DatabaseProvider {
    private val lock = Any()
    private val databaseRef = AtomicReference<MiniBookDatabase?>(null)
    private val openStateRef = MutableStateFlow<MiniBookDatabase?>(null)

    /** 是否已解锁且数据库连接可用。 */
    val isOpen: Boolean
        get() = databaseRef.get()?.isOpen == true

    /** 当前打开的 [MiniBookDatabase]；未解锁（未 open）时为 null，调用方不得强解包。 */
    override val database: MiniBookDatabase?
        get() = databaseRef.get()

    /**
     * 「已打开数据库」流（F-16i）：随 [open]/[close] 变化——解锁发射实例、重锁回 null。
     * 供返回 Flow 的数据源方法做门控（见 [com.minibook.data.local.datasource.gatedFlow]）。
     */
    override val openState: StateFlow<MiniBookDatabase?> = openStateRef.asStateFlow()

    /**
     * 用给定 passphrase 打开 SQLCipher 数据库。
     *
     * @param passphraseBytes SQLCipher passphrase（32 字节 master key）。**本方法返回前会
     *                        将其清零**，调用方不得在返回后继续引用其内容。
     * @return true=打开成功且 [isOpen]；false=打开失败（已记录日志，DB 保持关闭）。
     */
    fun open(passphraseBytes: ByteArray): Boolean {
        synchronized(lock) {
            val current = databaseRef.get()
            if (current != null && current.isOpen) {
                wipe(passphraseBytes)
                return true
            }
            return try {
                // SupportOpenHelperFactory 以引用持有 byte[]（SQLCipher 4.6.1 反编译证实）。
                // 传一份拷贝给 SQLCipher，避免本方法清零后影响其连接生命周期内的
                // 连接重开（Room 默认关闭 AutoCloser，但仍保持工厂密钥可用）。
                // 调用方的 passphraseBytes 在 finally 中按 ARCHITECTURE §7.2 清零。
                val factory = SupportOpenHelperFactory(passphraseBytes.copyOf())
                val db = Room.databaseBuilder(context, MiniBookDatabase::class.java, DB_NAME)
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration(false) // DATABASE §7.3 显式禁止破坏迁移
                    .addMigrations(
                        MiniBookDatabase.MIGRATION_1_2, // F-12：version 1→2 加列 + 唯一索引（DEC-018）
                        MiniBookDatabase.MIGRATION_2_3, // F-12：version 2→3 新增 user_category_rules 表（分类自学习）
                        MiniBookDatabase.MIGRATION_3_4, // F-15：version 3→4 新增 sync_state / sync_conflicts（DEC-033）
                        MiniBookDatabase.MIGRATION_4_5, // DEC-056：version 4→5 transactions 加 debtId（账单转债务）
                    )
                    .build()
                // 触发真实打开：SQLCipher 先以 key 解密，随后 Room 在同一上下文执行
                // migration（密码错误会在 schema 校验时抛 SQLiteException）。
                db.openHelper.writableDatabase
                databaseRef.set(db)
                openStateRef.value = db
                db.isOpen
            } catch (e: Exception) {
                databaseRef.set(null)
                openStateRef.value = null
                Log.e(TAG, "DatabaseGate.open 失败：${e.message}", e)
                false
            } finally {
                wipe(passphraseBytes)
            }
        }
    }

    /**
     * 内部便捷：用 [keyManager] 静默解出 passphrase 并打开（锁「关闭」路径，DEC-002
     * 静默开库直达；锁「开启」路径由调用方先经生物识别解出 passphrase 再调 [open]）。
     *
     * @param useAuth true=走 auth 解包（要求已完成认证）；false=plain key 静默解包。
     * @return 同 [open]
     */
    fun openWithKeyManager(useAuth: Boolean): Boolean {
        val passphrase = keyManager.getMasterKey(useAuth)
        return open(passphrase)
    }

    /** 关闭数据库并释放引用；关闭后 [database] 为 null（重锁路径，ARCHITECTURE §7.5）。 */
    fun close() {
        synchronized(lock) {
            databaseRef.getAndSet(null)?.close()
            openStateRef.value = null
        }
    }

    private fun wipe(bytes: ByteArray) {
        Arrays.fill(bytes, 0)
    }

    companion object {
        /**
         * 显式加载 SQLCipher native 库。
         *
         * SQLCipher 4.6.1（`net.zetetic:sqlcipher-android`）不会在首次使用
         * [SupportOpenHelperFactory] 时自动加载 `libsqlcipher.so`；若不在此加载，
         * Java 层 `SQLiteConnection.nativeOpen` 将找不到 native 实现并抛
         * `UnsatisfiedLinkError`，导致 `open` 静默失败（锁关闭路径「自动解锁失败」）。
         * companion object 的 init 块仅在本类首次加载时执行一次（比每次实例化的
         * init 块更省）；`System.loadLibrary` 重复调用本身幂等、安全。
         */
        init {
            System.loadLibrary("sqlcipher")
        }

        private const val TAG = "DatabaseGate"

        /** SQLCipher 加密库文件名（DATABASE §2）。 */
        const val DB_NAME = "minibook-encrypted.db"
    }
}
