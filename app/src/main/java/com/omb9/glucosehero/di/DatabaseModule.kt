package com.omb9.glucosehero.di

import android.content.Context
import androidx.room.Room
import com.omb9.glucosehero.data.local.db.ChatMessageDao
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase
import com.omb9.glucosehero.data.local.db.PendingAiQueryDao
import com.omb9.glucosehero.data.local.db.migration.Migration1To2
import com.omb9.glucosehero.data.local.db.migration.Migration2To3
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GlucoseHeroDatabase =
        Room.databaseBuilder(
            context,
            GlucoseHeroDatabase::class.java,
            "glucosehero.db",
        )
            .addMigrations(Migration1To2, Migration2To3)
            .build()

    @Provides
    fun provideEntryDao(db: GlucoseHeroDatabase): EntryDao = db.entryDao()

    @Provides
    fun provideChatMessageDao(db: GlucoseHeroDatabase): ChatMessageDao = db.chatMessageDao()

    @Provides
    fun providePendingAiQueryDao(db: GlucoseHeroDatabase): PendingAiQueryDao =
        db.pendingAiQueryDao()
}
