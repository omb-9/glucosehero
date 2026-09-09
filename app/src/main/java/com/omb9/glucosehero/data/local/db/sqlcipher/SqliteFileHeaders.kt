package com.omb9.glucosehero.data.local.db.sqlcipher

import java.io.File

/**
 * Detects a classic unencrypted SQLite file by its 16-byte header magic.
 * SQLCipher-encrypted files do not start with this prefix.
 */
object SqliteFileHeaders {
    private val MAGIC: ByteArray =
        "SQLite format 3".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)

    fun isPlaintextSqlite(file: File): Boolean {
        if (!file.isFile || file.length() < MAGIC.size.toLong()) return false
        val header = ByteArray(MAGIC.size)
        file.inputStream().use { input ->
            var filled = 0
            while (filled < header.size) {
                val read = input.read(header, filled, header.size - filled)
                if (read <= 0) return false
                filled += read
            }
        }
        return header.contentEquals(MAGIC)
    }
}
