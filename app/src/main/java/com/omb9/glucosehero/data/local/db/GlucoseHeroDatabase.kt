package com.omb9.glucosehero.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.omb9.glucosehero.data.local.entity.ChatMessageEntity
import com.omb9.glucosehero.data.local.entity.EntryEntity
import com.omb9.glucosehero.data.local.entity.FoodEntity
import com.omb9.glucosehero.data.local.entity.GlucoseReadingView
import com.omb9.glucosehero.data.local.entity.GlucoseSampleEntity
import com.omb9.glucosehero.data.local.entity.InsightCardEntity
import com.omb9.glucosehero.data.local.entity.PendingAiQueryEntity
import com.omb9.glucosehero.data.local.entity.SupplyEntity

@Database(
    entities = [
        EntryEntity::class,
        FoodEntity::class,
        ChatMessageEntity::class,
        PendingAiQueryEntity::class,
        SupplyEntity::class,
        InsightCardEntity::class,
        GlucoseSampleEntity::class,
    ],
    views = [GlucoseReadingView::class],
    version = 9,
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
}
