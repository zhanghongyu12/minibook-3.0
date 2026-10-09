package com.minibook.data.local.mail

import android.content.Context
import android.util.Log
import com.minibook.data.local.security.MailCredentialCipher
import com.minibook.domain.model.MailAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 邮箱账单账户存储（F-13 多账号增强，单账号时代落盘格式自动迁移）。
 *
 * 落盘位置：`context.noBackupFilesDir`（不参与系统备份，与 master key blob 同口径）：
 * - `minibook_mail_accounts.json`：账号列表 `[{"id":"<uuid>","email":"xxx@example.com"}]`
 *   （编解码/校验见 [MailAccountCodec]）；
 * - `minibook_mail_auth_<id>.bin`：**每个账号各自**的授权码密文（Base64(12B IV || GCM
 *   ciphertext)，经 [MailCredentialCipher] 用 Keystore 别名 `minibook_mail_credential_key`
 *   加密，与数据库 master key 体系完全隔离）。
 *
 * 旧配置自动迁移（构造时一次性执行，**崩溃安全**：先复制不删旧、写盘后回读校验，
 * 全部通过才删除旧文件；详见 [loadOrMigrate]）。任何一步失败（含 `Error`，历史上
 * `MailAccountCodec` 的 `<clinit>` 曾在真机抛 `Error` 导致半迁移丢凭据）都会回滚
 * 本次新产物、保留旧文件原样，accounts 视为空，绝不让构造抛异常导致 App 起不来。
 *
 * 状态流：[accounts] / [isConfigured] 初值在**构造时同步读盘**得出（仅小文件的简单同步
 * 读，KB 量级以下，可接受；不做 runBlocking 协程 IO）。之后 upsert/delete/clearAll 内
 * 更新并保持与磁盘一致。
 *
 * 安全纪律：**授权码/邮箱地址/密文绝不出现在任何日志或异常信息中**——失败日志只记
 * 异常类名（`Log.w` + `t.javaClass.name`），不含 message（message 可能夹带用户数据）。
 * [getAuthCode] 解密失败（密文损坏/密钥失效）时防御性返回 null（视为不可用，由调用方
 * 引导重配）。校验失败的 [IllegalArgumentException] message 仅含格式描述与长度数字。
 *
 * `@Singleton` + `@Inject` 构造注入，无需 Hilt Module。
 */
@Singleton
class MailAccountStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    companion object {
        /** 账号列表 JSON 文件名（`noBackupFilesDir/minibook_mail_accounts.json`）。 */
        const val ACCOUNTS_FILE_NAME = "minibook_mail_accounts.json"

        /** QQ 邮箱 IMAP 授权码固定长度：16 位。 */
        const val AUTH_CODE_LENGTH = 16

        /** 单账号时代邮箱地址明文文件名（仅迁移读取，迁移成功后删除）。 */
        private const val LEGACY_EMAIL_FILE_NAME = "minibook_mail_account.txt"

        /** 单账号时代授权码密文文件名（仅迁移重命名）。 */
        private const val LEGACY_AUTH_FILE_NAME = "minibook_mail_auth.bin"

        /** 按账号 id 生成各自的授权码密文文件名。 */
        private fun authFileName(id: String): String = "minibook_mail_auth_$id.bin"

        /** 日志 TAG（日志内容纪律：只记异常类名等非凭据信息）。 */
        private const val TAG = "MailAccountStore"
    }

    private val cipher = MailCredentialCipher()

    private val dir: File = context.noBackupFilesDir
    private val accountsFile: File = File(dir, ACCOUNTS_FILE_NAME)

    /** 串行化 upsert/delete/getAuthCode/clearAll 的磁盘操作，避免并发写坏状态。 */
    private val mutex = Mutex()

    private val _accounts = MutableStateFlow<List<MailAccount>>(emptyList())

    /** 当前已配置的账号列表；空列表=未配置。 */
    val accounts: StateFlow<List<MailAccount>> = _accounts.asStateFlow()

    private val _isConfigured = MutableStateFlow(false)

    /** 是否已配置至少一个账号。 */
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    init {
        // 构造时同步读盘初始化（含旧格式迁移；任何失败都折叠为空列表并记日志，构造不抛）。
        val loaded = runCatching { loadOrMigrate() }.getOrElse { t ->
            // loadOrMigrate 内部已尽力兜底；这里防御性捕获逃逸的 Throwable（含 Error）。
            Log.w(TAG, "mail account init failed: " + t.javaClass.name)
            emptyList()
        }
        _accounts.value = loaded
        _isConfigured.value = loaded.isNotEmpty()
    }

    /**
     * 读账号列表；无新格式文件时执行单账号旧格式迁移。
     *
     * 读盘路径：json 已存在则直接解码返回（[MailAccountCodec.decode] 自身容错不抛）；
     * 读盘 IO 异常或意外 `Error` 记异常类名后返回空列表，不再静默。
     *
     * 迁移顺序（**先复制、后校验、最后才删旧**，任一步失败可完整回退重试）：
     * 1. 读旧邮箱文件 `minibook_mail_account.txt`（trim，空则放弃迁移）；
     * 2. 旧密文 `minibook_mail_auth.bin` **copyTo 复制**（非 rename，旧文件原地不动）为
     *    `minibook_mail_auth_<新uuid>.bin`——同一 Keystore 密钥，密文直接沿用，不重新加解密；
     * 3. 写 `minibook_mail_accounts.json`；
     * 4. **回读校验**：用 [MailAccountCodec.decode] 解回该账号（验证通过才继续）；
     * 5. 校验通过才删除两个旧文件。
     *
     * 失败处理：`catch (t: Throwable)`（**必须含 `Error`**——历史上 `MailAccountCodec`
     * 的 `<clinit>` 曾在真机抛 `ExceptionInInitializerError` 导致半迁移丢凭据）→ 尽力
     * 删除本次复制出的新密文与半成品 json（保证幂等可重试），旧文件保留不动，返回空
     * 列表，并记异常类名日志（不含任何凭据/邮箱/密文内容）。
     *
     * 幂等：失败重跑可再迁一次（旧文件仍在）；已存在合法 json 则直接解码返回，不再迁移。
     * 遗留的孤儿密文文件（有 `minibook_mail_auth_<uuid>.bin` 而 json 未引用）**不删除**——
     * 没有邮箱地址无法恢复，删了反而破坏数据，留着无害。
     *
     * @return 账号列表；不可用/迁移失败时为空列表（绝不抛异常）
     */
    private fun loadOrMigrate(): List<MailAccount> {
        if (accountsFile.exists()) {
            return try {
                MailAccountCodec.decode(accountsFile.readText())
            } catch (t: Throwable) {
                Log.w(TAG, "mail account load failed: " + t.javaClass.name)
                emptyList()
            }
        }
        val legacyEmailFile = File(dir, LEGACY_EMAIL_FILE_NAME)
        val legacyAuthFile = File(dir, LEGACY_AUTH_FILE_NAME)
        if (!legacyEmailFile.exists() || !legacyAuthFile.exists()) return emptyList()

        val id = UUID.randomUUID().toString()
        val newAuthFile = File(dir, authFileName(id))
        return try {
            val email = legacyEmailFile.readText().trim()
            if (email.isEmpty()) return emptyList() // 旧邮箱文件为空：放弃迁移（非失败，不告警）
            val account = MailAccount(id, email)

            // 步骤 2：复制密文（非 rename——旧文件原地保留，失败可无损重试）。
            legacyAuthFile.copyTo(newAuthFile, overwrite = true)

            // 步骤 3+4：写 json 后回读校验，确认能解出这条账号（防半迁移假成功）。
            accountsFile.writeText(MailAccountCodec.encode(listOf(account)))
            if (MailAccountCodec.decode(accountsFile.readText()) != listOf(account)) {
                throw IllegalStateException("mail account migration verification failed")
            }

            // 步骤 5：全部通过才删除旧文件（此后迁移完成）。
            legacyEmailFile.delete()
            legacyAuthFile.delete()
            listOf(account)
        } catch (t: Throwable) {
            // 回滚：删掉本次复制出的新密文与半成品 json，旧文件保持原样（下次构造可重试）。
            runCatching { newAuthFile.delete() }
            runCatching { accountsFile.delete() }
            // 只记异常类名：message 可能含路径等环境信息，凭据纪律下不落日志。
            Log.w(TAG, "mail account migration failed: " + t.javaClass.name)
            emptyList()
        }
    }

    /**
     * 新增或更新一个账号（校验 + Keystore 加密落盘）。
     *
     * 校验规则（沿用单账号口径）：邮箱须匹配基本格式（含 `@` 且域名含 `.`）；授权码
     * trim 后必须恰为 [AUTH_CODE_LENGTH] 位。非法抛 [IllegalArgumentException]（中文
     * 原因，不含凭据内容）。
     *
     * @param id null=新增（生成 UUID）；非 null=更新该账号的邮箱地址与授权码
     * @param email 邮箱地址
     * @param authCode IMAP 授权码（明文仅经内存传入 [MailCredentialCipher.encrypt]，不落盘）
     * @return 新增/更新后的账号
     * @throws IllegalArgumentException 输入非法或要修改的账号不存在时（中文原因）
     */
    suspend fun upsert(id: String?, email: String, authCode: String): MailAccount {
        val trimmedEmail = email.trim()
        val trimmedCode = authCode.trim()
        require(MailAccountCodec.validateEmail(trimmedEmail)) {
            "邮箱地址格式不正确，需形如 name@example.com"
        }
        require(MailAccountCodec.validateAuthCode(trimmedCode)) {
            "IMAP 授权码必须为 $AUTH_CODE_LENGTH 位（当前输入 ${trimmedCode.length} 位）"
        }
        val encrypted = cipher.encrypt(trimmedCode)
        return mutex.withLock {
            val current = _accounts.value
            val result: MailAccount
            withContext(Dispatchers.IO) {
                if (id == null) {
                    val newId = UUID.randomUUID().toString()
                    val authFile = File(dir, authFileName(newId))
                    try {
                        authFile.writeText(encrypted)
                        persist(current + MailAccount(newId, trimmedEmail))
                    } catch (t: Throwable) {
                        // 写 json 失败时清掉孤儿密文；catch Throwable（含 Error）确保回滚必达。
                        runCatching { authFile.delete() }
                        throw t
                    }
                    result = MailAccount(newId, trimmedEmail)
                } else {
                    val existing = current.firstOrNull { it.id == id }
                        ?: throw IllegalArgumentException("要修改的邮箱账号不存在，请返回列表后重试")
                    File(dir, authFileName(id)).writeText(encrypted)
                    result = existing.copy(email = trimmedEmail)
                    persist(current.map { if (it.id == id) result else it })
                }
            }
            _accounts.value = if (id == null) current + result else current.map { if (it.id == id) result else it }
            _isConfigured.value = _accounts.value.isNotEmpty()
            result
        }
    }

    /**
     * 解密读出指定账号的授权码（供 IMAP 拉取模块用）。
     *
     * @return 授权码明文；账号不存在/密文文件缺失/解密失败（密文损坏或密钥失效）均返回
     *         null（视为不可用，由上层引导重新配置；不抛出、不写日志）。
     */
    suspend fun getAuthCode(id: String): String? = mutex.withLock {
        val authFile = withContext(Dispatchers.IO) {
            val account = _accounts.value.firstOrNull { it.id == id }
            if (account != null) File(dir, authFileName(id)).takeIf { it.exists() } else null
        } ?: return@withLock null
        val blob = withContext(Dispatchers.IO) {
            runCatching { authFile.readText().trim().takeIf { it.isNotEmpty() } }.getOrNull()
        } ?: return@withLock null
        runCatching { cipher.decrypt(blob) }.getOrNull()
    }

    /**
     * 删除一个账号：删该账号的密文文件 + 从 json 移除；账号不存在时静默无操作。
     */
    suspend fun delete(id: String) {
        mutex.withLock {
            val current = _accounts.value
            if (current.none { it.id == id }) return@withLock
            withContext(Dispatchers.IO) {
                File(dir, authFileName(id)).delete()
                persist(current.filterNot { it.id == id })
            }
            _accounts.value = current.filterNot { it.id == id }
            _isConfigured.value = _accounts.value.isNotEmpty()
        }
    }

    /** 抹除全部账号（含各自密文文件与账号列表 json），恢复未配置态。 */
    suspend fun clearAll() {
        mutex.withLock {
            val current = _accounts.value
            withContext(Dispatchers.IO) {
                current.forEach { account -> File(dir, authFileName(account.id)).delete() }
                accountsFile.delete()
            }
            _accounts.value = emptyList()
            _isConfigured.value = false
        }
    }

    /** 账号列表 json 覆盖写（调用方须已持锁）。 */
    private fun persist(accounts: List<MailAccount>) {
        accountsFile.writeText(MailAccountCodec.encode(accounts))
    }
}
