package com.omb9.glucosehero.data.local.db.sqlcipher

import android.content.Context
import com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase
import com.omb9.glucosehero.data.security.KeystoreManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates a 256-bit SQLCipher raw key, wraps it with [KeystoreManager], and
 * persists the wrapped blob under [Context.getNoBackupFilesDir].
 *
 * Uninstall deletes Android Keystore keys. The wrapped file is then
 * undecryptable, and the encrypted Room file cannot be recovered. Auto Backup
 * also cannot restore this database on a new device, because Keystore keys
 * are not part of the backup.
 *
 * A missing wrapped file is never repaired by minting a new key if an
 * encrypted database already exists (that would lock the user out of PHI).
 */
@Singleton
class SqlCipherPassphraseStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keystoreManager: KeystoreManager,
) {
    private val lock = Any()

    fun getOrCreatePassphrase(): ByteArray = synchronized(lock) {
        val file = wrappedFile()
        if (file.exists()) {
            return keystoreManager.unwrapSqlCipherPassphrase(file.readBytes())
        }
        val dbFile = context.getDatabasePath(GlucoseHeroDatabase.NAME)
        if (dbFile.exists() && !SqliteFileHeaders.isPlaintextSqlite(dbFile)) {
            throw SqlCipherMigrationException(
                "Encrypted database exists but the wrapped SQLCipher key is missing. " +
                    "Uninstall removes Keystore keys; this database cannot be recovered.",
            )
        }
        val raw = ByteArray(SqlCipherKeys.SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        persistWrapped(file, keystoreManager.wrapSqlCipherPassphrase(raw))
        return raw
    }

    fun wrappedFile(): File =
        File(context.noBackupFilesDir, WRAPPED_FILE_NAME)

    private fun persistWrapped(file: File, wrapped: ByteArray) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(wrapped)
        if (!tmp.renameTo(file)) {
            file.writeBytes(wrapped)
            tmp.delete()
        }
        check(file.exists() && file.length() > 0L) {
            "Failed to persist the wrapped SQLCipher key."
        }
    }

    private companion object {
        const val WRAPPED_FILE_NAME = "sqlcipher_passphrase.wrapped"
    }
}
