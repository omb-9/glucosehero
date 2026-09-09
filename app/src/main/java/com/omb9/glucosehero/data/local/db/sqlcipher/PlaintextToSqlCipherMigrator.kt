package com.omb9.glucosehero.data.local.db.sqlcipher

import android.content.Context
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

/**
 * One-time copy-then-encrypt upgrade for an existing plaintext Room file.
 *
 * Steps:
 * 1. If `databases/<name>` is missing, empty, or already encrypted (no
 *    `SQLite format 3` header), do nothing.
 * 2. Open the plaintext file with SQLCipher and an empty key, checkpoint WAL,
 *    and switch journal_mode to DELETE so sidecars are flushed.
 * 3. ATTACH a temp file keyed with the same raw AES-256 spec Room will use,
 *    then `SELECT sqlcipher_export('encrypted')`. Copy `user_version` so Room
 *    can still run schema migrations after the swap.
 * 4. Verify the temp file opens with the raw key and no longer has a plaintext
 *    header. Only then park the plaintext as `*.pre-encrypt.bak` and rename
 *    the temp file into place.
 * 5. Verify the live file. Delete the plaintext backup. On any failure, restore
 *    the plaintext file and delete the temp copy (fail closed).
 *
 * The passphrase and raw-key spec are never written to logs.
 */
object PlaintextToSqlCipherMigrator {

    fun migrateIfNeeded(context: Context, databaseName: String, passphrase: ByteArray) {
        SqlCipherNative.load()
        val dbFile = context.getDatabasePath(databaseName)
        if (!SqliteFileHeaders.isPlaintextSqlite(dbFile)) return

        val tmp = File(dbFile.parentFile, "$databaseName.encrypting")
        val bak = File(dbFile.parentFile, "$databaseName.pre-encrypt.bak")
        deleteFileAndSidecars(tmp)

        try {
            exportPlaintextToEncrypted(dbFile, tmp, passphrase)
            verifyEncrypted(tmp, passphrase)
            deleteFileAndSidecars(bak)
            if (!renameDatabase(dbFile, bak)) {
                deleteFileAndSidecars(tmp)
                throw SqlCipherMigrationException(
                    "Could not park the plaintext database; original file left in place",
                )
            }
            if (!renameDatabase(tmp, dbFile)) {
                renameDatabase(bak, dbFile)
                deleteFileAndSidecars(tmp)
                throw SqlCipherMigrationException(
                    "Could not install the encrypted database; original file restored",
                )
            }
            try {
                verifyEncrypted(dbFile, passphrase)
            } catch (t: Throwable) {
                deleteFileAndSidecars(dbFile)
                renameDatabase(bak, dbFile)
                throw SqlCipherMigrationException(
                    "Encrypted database failed verification; original file restored",
                    t,
                )
            }
            deleteFileAndSidecars(bak)
        } catch (t: SqlCipherMigrationException) {
            throw t
        } catch (t: Throwable) {
            deleteFileAndSidecars(tmp)
            throw SqlCipherMigrationException(
                "Plaintext to SQLCipher copy failed; original file left in place",
                t,
            )
        }
    }

    private fun exportPlaintextToEncrypted(src: File, dest: File, passphrase: ByteArray) {
        deleteFileAndSidecars(dest)
        val source = SQLiteDatabase.openOrCreateDatabase(src, "", null, null, null)
        try {
            source.rawExecSQL("PRAGMA wal_checkpoint(FULL)")
            source.rawExecSQL("PRAGMA journal_mode = DELETE")
            val version = source.version
            source.rawExecSQL(
                "ATTACH DATABASE ? AS encrypted KEY ?",
                dest.absolutePath,
                SqlCipherKeys.rawKeySpecString(passphrase),
            )
            try {
                source.rawExecSQL("SELECT sqlcipher_export('encrypted')")
                source.rawExecSQL("PRAGMA encrypted.user_version = $version")
            } finally {
                source.rawExecSQL("DETACH DATABASE encrypted")
            }
        } finally {
            source.close()
        }
    }

    private fun verifyEncrypted(file: File, passphrase: ByteArray) {
        check(file.isFile && file.length() > 0L) { "Encrypted database file is missing." }
        check(!SqliteFileHeaders.isPlaintextSqlite(file)) {
            "Copy produced a file that still has a plaintext SQLite header."
        }
        val db = SQLiteDatabase.openDatabase(
            file.absolutePath,
            SqlCipherKeys.rawKeySpec(passphrase),
            null,
            SQLiteDatabase.OPEN_READONLY,
            null,
            null,
        )
        try {
            db.rawQuery("SELECT count(*) FROM sqlite_master", emptyArray<String>()).use { cursor ->
                check(cursor.moveToFirst()) { "Encrypted database has no sqlite_master." }
            }
        } finally {
            db.close()
        }
    }

    private fun renameDatabase(from: File, to: File): Boolean {
        deleteFileAndSidecars(to)
        return from.renameTo(to)
    }

    private fun deleteFileAndSidecars(file: File) {
        file.delete()
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
        File(file.path + "-journal").delete()
    }
}
