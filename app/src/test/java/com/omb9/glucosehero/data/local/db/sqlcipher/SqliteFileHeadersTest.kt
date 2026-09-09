package com.omb9.glucosehero.data.local.db.sqlcipher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class SqliteFileHeadersTest {

    @Test
    fun isPlaintextSqlite_acceptsClassicHeader() {
        val dir = createTempDirectory("sqlite-header").toFile()
        val file = File(dir, "plain.db")
        val header = "SQLite format 3".toByteArray(Charsets.US_ASCII) + byteArrayOf(0) + ByteArray(16)
        file.writeBytes(header)
        assertTrue(SqliteFileHeaders.isPlaintextSqlite(file))
        dir.deleteRecursively()
    }

    @Test
    fun isPlaintextSqlite_rejectsEncryptedLookingBytes() {
        val dir = createTempDirectory("sqlite-header").toFile()
        val file = File(dir, "enc.db")
        file.writeBytes(ByteArray(64) { 0xA5.toByte() })
        assertFalse(SqliteFileHeaders.isPlaintextSqlite(file))
        dir.deleteRecursively()
    }

    @Test
    fun isPlaintextSqlite_rejectsMissingAndShortFiles() {
        val dir = createTempDirectory("sqlite-header").toFile()
        val missing = File(dir, "nope.db")
        val shortFile = File(dir, "short.db")
        shortFile.writeBytes(byteArrayOf(1, 2, 3))
        assertFalse(SqliteFileHeaders.isPlaintextSqlite(missing))
        assertFalse(SqliteFileHeaders.isPlaintextSqlite(shortFile))
        dir.deleteRecursively()
    }

    @Test
    fun rawKeySpec_isXPrefixed64Hex() {
        val key = ByteArray(32) { it.toByte() }
        val spec = SqlCipherKeys.rawKeySpecString(key)
        assertTrue(spec.startsWith("x'"))
        assertTrue(spec.endsWith("'"))
        assertEquals(64, spec.length - 3)
        assertEquals(spec.toByteArray(Charsets.UTF_8).toList(), SqlCipherKeys.rawKeySpec(key).toList())
    }
}
