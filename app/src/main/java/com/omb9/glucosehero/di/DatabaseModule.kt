package com.omb9.glucosehero.di

import android.content.Context
import androidx.room.Room
import com.omb9.glucosehero.data.local.db.ChatMessageDao
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase
import com.omb9.glucosehero.data.local.db.GlucoseSampleDao
import com.omb9.glucosehero.data.local.db.InsightDao
import com.omb9.glucosehero.data.local.db.PendingAiQueryDao
import com.omb9.glucosehero.data.local.db.SupplyDao
import com.omb9.glucosehero.data.local.db.migration.Migration1To2
import com.omb9.glucosehero.data.local.db.migration.Migration2To3
import com.omb9.glucosehero.data.local.db.migration.Migration3To4
import com.omb9.glucosehero.data.local.db.migration.Migration4To5
import com.omb9.glucosehero.data.local.db.migration.Migration5To6
import com.omb9.glucosehero.data.local.db.migration.Migration6To7
import com.omb9.glucosehero.data.local.db.migration.Migration7To8
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
            .addMigrations(Migration1To2, Migration2To3, Migration3To4, Migration4To5, Migration5To6, Migration6To7, Migration7To8)
            .build()

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
}
