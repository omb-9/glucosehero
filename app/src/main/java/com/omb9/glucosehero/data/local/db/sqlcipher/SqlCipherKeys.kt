package com.omb9.glucosehero.data.local.db.sqlcipher

/**
 * Formats the 256-bit SQLCipher raw key.
 *
 * SQLCipher treats a passphrase whose UTF-8 bytes are exactly `x'<64 hex>'`
 * as a raw AES-256 key (no PBKDF2). Room's [net.zetetic.database.sqlcipher.SupportOpenHelperFactory]
 * and [PlaintextToSqlCipherMigrator] must use this same encoding.
 */
object SqlCipherKeys {
    const val SIZE_BYTES = 32

    fun toHex(bytes: ByteArray): String {
        val digits = "0123456789abcdef"
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xff
            out[i * 2] = digits[v ushr 4]
            out[i * 2 + 1] = digits[v and 0x0f]
        }
        return String(out)
    }

    fun rawKeySpec(passphrase: ByteArray): ByteArray {
        require(passphrase.size == SIZE_BYTES) { "SQLCipher raw key must be 256 bits." }
        return rawKeySpecString(passphrase).toByteArray(Charsets.UTF_8)
    }

    fun rawKeySpecString(passphrase: ByteArray): String {
        require(passphrase.size == SIZE_BYTES) { "SQLCipher raw key must be 256 bits." }
        return "x'${toHex(passphrase)}'"
    }
}
