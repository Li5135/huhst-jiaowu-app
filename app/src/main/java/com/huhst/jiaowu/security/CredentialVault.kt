package com.huhst.jiaowu.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 账号密码的本机加密存储。
 *
 * 密钥由 Android Keystore 生成并保管，**不落盘、不出安全区**；
 * 落盘的只有密文，离开本机无法还原。
 * 应用已关闭云备份与换机迁移（见 AndroidManifest / backup_rules.xml），
 * 凭证不会被带出设备。
 */
class CredentialVault {

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "com.huhst.jiaowu.credential"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
        const val SEPARATOR = ':'
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun secretKey(): SecretKey {
        val ks = keyStore()
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    /** 加密。返回 base64(iv):base64(cipher) 形式，失败返回 null。 */
    fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val body = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        "$iv$SEPARATOR$body"
    }.getOrNull()

    /** 解密。密钥丢失或数据损坏时返回 null（调用方应视为未保存凭证）。 */
    fun decrypt(encoded: String): String? = runCatching {
        val idx = encoded.indexOf(SEPARATOR)
        if (idx <= 0) return null
        val iv = Base64.decode(encoded.substring(0, idx), Base64.NO_WRAP)
        val body = Base64.decode(encoded.substring(idx + 1), Base64.NO_WRAP)

        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(body), Charsets.UTF_8)
    }.getOrNull()

    /** 清除密钥，历史密文随之永久不可解。 */
    fun clear() {
        runCatching {
            val ks = keyStore()
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
        }
    }
}
