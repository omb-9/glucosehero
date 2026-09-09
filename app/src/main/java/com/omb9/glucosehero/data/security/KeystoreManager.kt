package com.omb9.glucosehero.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps the native Android KeyStore.
 *
 * Three non-exportable AES-256 keys live in hardware-backed storage:
 * - [KEY_ALIAS] wraps BYOK API keys as Base64(IV || ciphertext) strings
 *   before they touch DataStore.
 * - [BACKUP_KEY_ALIAS] wraps per-backup data-encryption keys used by the
 *   zero-knowledge cloud/local encrypted backup path. The wrapping key never
 *   leaves the device; only the wrapped DEK travels with the ciphertext blob.
 * - [SQLCIPHER_KEY_ALIAS] wraps the 256-bit SQLCipher raw key. The raw key
 *   itself cannot live in Keystore (Keystore keys are non-exportable, and
 *   SQLCipher needs the bytes). Uninstall deletes these Keystore keys, after
 *   which the wrapped SQLCipher key file cannot be decrypted.
 *
 * Keys and plaintext medical data are never logged.
 */
@Singleton
class KeystoreManager @Inject constructor() {

    private val keyStore: KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun encrypt(plainText: String): String {
        val encoded = encryptBytes(plainText.toByteArray(Charsets.UTF_8), KEY_ALIAS)
        return Base64.encodeToString(encoded, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        return decryptBytes(bytes, KEY_ALIAS).toString(Charsets.UTF_8)
    }

    /**
     * Encrypts a 256-bit data-encryption key with the backup wrapping key.
     * Wire format: IV[12] || ciphertext+tag. The DEK itself never leaves RAM
     * unwrapped except inside [EncryptedBackupCipher] during a single operation.
     */
    fun wrapDataKey(rawKey: ByteArray): ByteArray {
        require(rawKey.size == DATA_KEY_BYTES) { "Backup data key must be 256 bits." }
        return encryptBytes(rawKey, BACKUP_KEY_ALIAS)
    }

    fun unwrapDataKey(wrappedKey: ByteArray): ByteArray {
        val dek = decryptBytes(wrappedKey, BACKUP_KEY_ALIAS)
        require(dek.size == DATA_KEY_BYTES) { "Unwrapped backup data key was the wrong size." }
        return dek
    }

    /**
     * Wraps a 256-bit SQLCipher raw key with a dedicated Keystore AES-GCM key.
     * Distinct from [wrapDataKey] so a backup-key rotation cannot lock the
     * on-device database.
     */
    fun wrapSqlCipherPassphrase(rawKey: ByteArray): ByteArray {
        require(rawKey.size == DATA_KEY_BYTES) { "SQLCipher passphrase must be 256 bits." }
        return encryptBytes(rawKey, SQLCIPHER_KEY_ALIAS)
    }

    fun unwrapSqlCipherPassphrase(wrappedKey: ByteArray): ByteArray {
        val key = decryptBytes(wrappedKey, SQLCIPHER_KEY_ALIAS)
        require(key.size == DATA_KEY_BYTES) { "Unwrapped SQLCipher passphrase was the wrong size." }
        return key
    }

    private fun encryptBytes(plain: ByteArray, alias: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(alias))
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain)
        return iv + cipherText
    }

    private fun decryptBytes(blob: ByteArray, alias: String): ByteArray {
        require(blob.size > IV_SIZE) { "Corrupt ciphertext blob" }
        val iv = blob.copyOfRange(0, IV_SIZE)
        val cipherText = blob.copyOfRange(IV_SIZE, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(alias), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)
            ?.secretKey
            ?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "glucosehero_api_key"
        const val BACKUP_KEY_ALIAS = "glucosehero_backup_wrap"
        const val SQLCIPHER_KEY_ALIAS = "glucosehero_sqlcipher_wrap"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        const val DATA_KEY_BYTES = 32
    }
}
