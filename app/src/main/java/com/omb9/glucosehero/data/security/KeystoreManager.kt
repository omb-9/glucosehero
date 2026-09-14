package com.omb9.glucosehero.data.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps the native Android KeyStore.
 *
 * Three non-exportable AES-256 keys live in Android Keystore:
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
 * Keystore does not guarantee TEE/StrongBox. Query [isInsideSecureHardware]
 * (or the log line emitted on first use of each alias) for the device's
 * real answer. Keys and plaintext medical data are never logged.
 */
@Singleton
class KeystoreManager @Inject constructor() {

    private val keyStore: KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private val hardwareStatusLogged = mutableSetOf<String>()

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
     * unwrapped except inside EncryptedBackupCipher during a single operation.
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

    /**
     * Whether Android reports [alias] as inside TEE/StrongBox.
     *
     * False on emulators and on devices whose OEM only offers a software
     * Keystore. Non-exportable still holds either way: the key cannot be
     * extracted, but a software key is only as strong as process isolation.
     */
    fun isInsideSecureHardware(alias: String): Boolean =
        queryInsideSecureHardware(getOrCreateKey(alias))

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

    /**
     * Serialized so two callers cannot both miss the alias and [KeyGenerator.generateKey]
     * twice. The second generate would replace the first key and permanently
     * orphan anything already encrypted under it (encrypted backups via
     * [BACKUP_KEY_ALIAS] are the life-safety case: [SqlCipherPassphraseStore]
     * already holds its own lock).
     *
     * Call-site main-thread analysis:
     * - [com.omb9.glucosehero.data.repository.SettingsRepositoryImpl] encrypt /
     *   decrypt now hop to [kotlinx.coroutines.Dispatchers.IO] so
     *   viewModelScope callers (Main.immediate) do not hold this lock on main.
     * - [com.omb9.glucosehero.ui.settings.NightscoutSettingsViewModel.saveCredential]
     *   encrypts on IO.
     * - [com.omb9.glucosehero.data.backup.EncryptedCloudBackupManager] encrypt /
     *   decrypt of WebDAV/Drive secrets hop to IO.
     * - [com.omb9.glucosehero.data.export.BackupManager.exportEncryptedTo] and
     *   wrapDataKey already run on IO.
     * - [com.omb9.glucosehero.data.remote.DynamicApiInterceptor] decrypts on
     *   OkHttp dispatcher threads, never main.
     * - [com.omb9.glucosehero.data.cgm.NightscoutCgmSource.fetch] decrypts on IO.
     * - [com.omb9.glucosehero.data.local.db.sqlcipher.SqlCipherPassphraseStore]
     *   is not a coroutine; Hilt may first open Room on the main thread during
     *   Application/Activity injection. That path is a blocking provider, not a
     *   coroutine holding Main, so this method stays non-suspending.
     */
    @Synchronized
    private fun getOrCreateKey(alias: String): SecretKey {
        val existing = (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
        val key = existing ?: generateKey(alias)
        logHardwareBackedStatusOnce(alias, key)
        return key
    }

    private fun generateKey(alias: String): SecretKey {
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
        val key = generator.generateKey()
        // Refresh the in-memory KeyStore so the next getEntry sees the new alias.
        keyStore.load(null)
        return key
    }

    private fun logHardwareBackedStatusOnce(alias: String, key: SecretKey) {
        if (!hardwareStatusLogged.add(alias)) return
        val inside = queryInsideSecureHardware(key)
        Log.i(TAG, "Keystore alias=$alias insideSecureHardware=$inside")
    }

    private fun queryInsideSecureHardware(key: SecretKey): Boolean {
        return try {
            val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
            val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                info.securityLevel >= KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT
            } else {
                @Suppress("DEPRECATION")
                info.isInsideSecureHardware
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "KeystoreManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "glucosehero_api_key"
        const val BACKUP_KEY_ALIAS = "glucosehero_backup_wrap"
        const val SQLCIPHER_KEY_ALIAS = "glucosehero_sqlcipher_wrap"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
        private const val DATA_KEY_BYTES = 32
    }
}
