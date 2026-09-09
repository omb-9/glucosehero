package com.omb9.glucosehero.di

import android.content.Context
import com.omb9.glucosehero.data.local.db.ChatMessageDao
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase
import com.omb9.glucosehero.data.local.db.GlucoseSampleDao
import com.omb9.glucosehero.data.local.db.InsightDao
import com.omb9.glucosehero.data.local.db.PendingAiQueryDao
import com.omb9.glucosehero.data.local.db.SupplyDao
import com.omb9.glucosehero.data.local.db.TagAnalyticDao
import com.omb9.glucosehero.data.local.db.sqlcipher.SqlCipherPassphraseStore
import com.omb9.glucosehero.data.local.db.sqlcipher.SqlCipherRoomFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the SQLCipher-backed Room database.
 *
 * Step-by-step (implemented in [SqlCipherRoomFactory.open]):
 * 1. Load libsqlcipher.
 * 2. Unwrap or create a 256-bit raw key via [SqlCipherPassphraseStore]
 *    (Keystore wrap, file in no-backup storage).
 * 3. Copy-then-encrypt a leftover plaintext `glucosehero.db` if its header
 *    is still `SQLite format 3`. Fail closed: the plaintext file is kept
 *    unless the encrypted copy verifies.
 * 4. Open Room with `SupportOpenHelperFactory` and
 *    [GlucoseHeroDatabase.ALL_MIGRATIONS] (including pending-query TTL).
 * 5. Do not call `fallbackToDestructiveMigration`. New installs skip every
 *    legacy Migration object and create the current schema from the
 *    entities. Uninstall still loses Keystore keys.
 *
 * Passphrases are never logged.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        passphraseStore: SqlCipherPassphraseStore,
    ): GlucoseHeroDatabase = SqlCipherRoomFactory.open(context, passphraseStore)

    @Provides
    fun provideEntryDao(db: GlucoseHeroDatabase): EntryDao = db.entryDao()

    @Provides
    fun provideChatMessageDao(db: GlucoseHeroDatabase): ChatMessageDao = db.chatMessageDao()

    @Provides
    fun providePendingAiQueryDao(db: GlucoseHeroDatabase): PendingAiQueryDao =
        db.pendingAiQueryDao()

    @Provides
    fun provideSupplyDao(db: GlucoseHeroDatabase): SupplyDao = db.supplyDao()

    @Provides
    fun provideInsightDao(db: GlucoseHeroDatabase): InsightDao = db.insightDao()

    @Provides
    fun provideGlucoseSampleDao(db: GlucoseHeroDatabase): GlucoseSampleDao = db.glucoseSampleDao()

    @Provides
    fun provideTagAnalyticDao(db: GlucoseHeroDatabase): TagAnalyticDao = db.tagAnalyticDao()
}
