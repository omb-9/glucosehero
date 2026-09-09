package com.omb9.glucosehero.data.local.db.sqlcipher

import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget

/**
 * Loads libsqlcipher once per process and silences SQLCipher's Java logger.
 *
 * SQLCipher must never print SQL (ATTACH KEY bind values, PRAGMA key, or
 * error text that echoes a statement). PHI and the raw key are never logged.
 */
object SqlCipherNative {
    @Volatile
    private var loaded = false
    private val lock = Any()

    fun load() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            System.loadLibrary("sqlcipher")
            Logger.setTarget(NoopTarget())
            loaded = true
        }
    }
}
