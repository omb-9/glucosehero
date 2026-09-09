package com.omb9.glucosehero.data.local.db.sqlcipher

import java.io.IOException

/**
 * Thrown when the plaintext-to-SQLCipher copy cannot complete. The original
 * database file is left in place (fail closed). Messages never include the
 * passphrase or raw-key spec.
 */
class SqlCipherMigrationException(message: String, cause: Throwable? = null) :
    IOException(message, cause)
