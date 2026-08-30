package com.omb9.glucosehero.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.omb9.glucosehero.data.local.entity.ChatMessageEntity
import com.omb9.glucosehero.data.local.entity.EntryEntity
import com.omb9.glucosehero.data.local.entity.PendingAiQueryEntity

@Database(
    entities = [
        EntryEntity::class,
        ChatMessageEntity::class,
        PendingAiQueryEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class GlucoseHeroDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun pendingAiQueryDao(): PendingAiQueryDao
}
