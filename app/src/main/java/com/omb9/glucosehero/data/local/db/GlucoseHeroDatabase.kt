package com.omb9.glucosehero.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.omb9.glucosehero.data.local.db.migration.Migration1To2
import com.omb9.glucosehero.data.local.db.migration.Migration2To3
import com.omb9.glucosehero.data.local.db.migration.Migration3To4
import com.omb9.glucosehero.data.local.db.migration.Migration4To5
import com.omb9.glucosehero.data.local.db.migration.Migration5To6
import com.omb9.glucosehero.data.local.db.migration.Migration6To7
import com.omb9.glucosehero.data.local.db.migration.Migration7To8
import com.omb9.glucosehero.data.local.db.migration.Migration8To9
import com.omb9.glucosehero.data.local.db.migration.Migration9To10
import com.omb9.glucosehero.data.local.db.migration.Migration10To11
import com.omb9.glucosehero.data.local.db.migration.Migration11To12
import com.omb9.glucosehero.data.local.db.migration.Migration12To13
import com.omb9.glucosehero.data.local.db.migration.MigrationPendingAiTtl
import com.omb9.glucosehero.data.local.entity.ChatMessageEntity
import com.omb9.glucosehero.data.local.entity.EntryEntity
import com.omb9.glucosehero.data.local.entity.FoodEntity
import com.omb9.glucosehero.data.local.entity.GlucoseReadingView
import com.omb9.glucosehero.data.local.entity.GlucoseSampleEntity
import com.omb9.glucosehero.data.local.entity.InsightCardEntity
import com.omb9.glucosehero.data.local.entity.PendingAiQueryEntity
import com.omb9.glucosehero.data.local.entity.SupplyEntity
import com.omb9.glucosehero.data.local.entity.TagAnalyticEntity

/**
 * Local Room database. Schema JSON is exported to `app/schemas` for
 * MigrationTestHelper.
 *
 * New installs create this version's schema directly from the entities
 * below. Room does not run [ALL_MIGRATIONS] when no database file exists.
 * Production never calls `fallbackToDestructiveMigration`. At rest the file
 * is SQLCipher-encrypted (see `SqlCipherRoomFactory`). Uninstall still loses
 * the Android Keystore wrapping key, so the encrypted file cannot be opened
 * after reinstall.
 */
@Database(
    entities = [
        EntryEntity::class,
        FoodEntity::class,
        ChatMessageEntity::class,
        PendingAiQueryEntity::class,
        SupplyEntity::class,
        InsightCardEntity::class,
        GlucoseSampleEntity::class,
        TagAnalyticEntity::class,
    ],
    views = [GlucoseReadingView::class],
    version = 14, // FEATURE: pending-query-ttl
    exportSchema = true,
)
abstract class GlucoseHeroDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun foodDao(): FoodDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun pendingAiQueryDao(): PendingAiQueryDao
    abstract fun supplyDao(): SupplyDao
    abstract fun insightDao(): InsightDao
    abstract fun glucoseSampleDao(): GlucoseSampleDao
    abstract fun tagAnalyticDao(): TagAnalyticDao

    companion object {
        const val NAME = "glucosehero.db"

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            Migration1To2,
            Migration2To3,
            Migration3To4,
            Migration4To5,
            Migration5To6,
            Migration6To7,
            Migration7To8,
            Migration8To9,
            Migration9To10,
            Migration10To11,
            Migration11To12,
            Migration12To13,
            MigrationPendingAiTtl, // FEATURE: pending-query-ttl
        )
    }
}
