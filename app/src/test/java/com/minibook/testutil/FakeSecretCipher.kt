package com.minibook.testutil

import com.minibook.data.local.security.SecretCipher
import java.util.Base64

/**
 * JVM 单测用的假包裹实现（真实现走 Android Keystore，JVM 上不可用）。
 *
 * 不是密码学实现，只保证「加密后的落盘串不等于明文 + 可逆」——足以断言
 * 「DataStore 里存的不是明文令牌」这一口径。
 */
class FakeSecretCipher : SecretCipher {

    override fun encrypt(plaintext: String): String =
        PREFIX + Base64.getEncoder().encodeToString(plaintext.toByteArray(Charsets.UTF_8))

    override fun decrypt(blob: String): String {
        require(blob.startsWith(PREFIX)) { "不是本实现的密文" }
        return String(Base64.getDecoder().decode(blob.removePrefix(PREFIX)), Charsets.UTF_8)
    }

    private companion object {
        const val PREFIX = "enc:"
    }
}
