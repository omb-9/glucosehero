package com.omb9.glucosehero.data.local.db.sqlcipher

import android.content.Context
import androidx.room.Room
import com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Builds the production [GlucoseHeroDatabase] on top of SQLCipher.
 *
 * Step-by-step open path:
 * 1. Load `libsqlcipher` ([SqlCipherNative.load]) and disable SQLCipher's
 *    Java logger so statements cannot leak a key.
 * 2. Ask [SqlCipherPassphraseStore] for a 256-bit raw key. On first launch
 *    the store generates one, wraps it with [com.omb9.glucosehero.data.security.KeystoreManager],
 *    and writes the wrapped blob to no-backup storage. Later launches unwrap
 *    that blob. Uninstall deletes Keystore keys; the wrapped blob is then
 *    useless and the encrypted file cannot be recovered.
 * 3. If `glucosehero.db` still has a plaintext SQLite header, run
 *    [PlaintextToSqlCipherMigrator] (copy-then-encrypt, fail closed). New
 *    installs skip this because there is no file yet.
 * 4. Give Room [SupportOpenHelperFactory] the SQLCipher raw-key spec
 *    (`x'<64 hex>'`), which is AES-256 with no PBKDF2.
 * 5. Register [GlucoseHeroDatabase.ALL_MIGRATIONS]. Room runs those only when
 *    an existing file's `user_version` is older than the entity version.
 *    A first install creates the current schema directly. Production does
 *    not call `fallbackToDestructiveMigration` and does not use
 *    `createFromAsset` / `createFromFile`.
 *
 * The raw key and its hex spec are never logged. The 32-byte copy held here
 * is zeroed after the factory has its own spec bytes.
 */
object SqlCipherRoomFactory {

    fun open(
        context: Context,
        passphraseStore: SqlCipherPassphraseStore,
        databaseName: String = GlucoseHeroDatabase.NAME,
    ): GlucoseHeroDatabase {
        SqlCipherNative.load()
        val raw = passphraseStore.getOrCreatePassphrase()
        try {
            PlaintextToSqlCipherMigrator.migrateIfNeeded(
                context,
                databaseName,
                raw,
            )
            val factory = SupportOpenHelperFactory(SqlCipherKeys.rawKeySpec(raw))
            return Room.databaseBuilder(
                context,
                GlucoseHeroDatabase::class.java,
                databaseName,
            )
                .openHelperFactory(factory)
                .addMigrations(*GlucoseHeroDatabase.ALL_MIGRATIONS)
                .build()
        } finally {
            raw.fill(0)
        }
    }
}
