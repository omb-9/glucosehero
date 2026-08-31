package com.omb9.glucosehero.di

import com.omb9.glucosehero.data.repository.ChatRepositoryImpl
import com.omb9.glucosehero.data.repository.EntryRepositoryImpl
import com.omb9.glucosehero.data.repository.SettingsRepositoryImpl
import com.omb9.glucosehero.data.repository.SupplyRepositoryImpl
import com.omb9.glucosehero.domain.repository.ChatRepository
import com.omb9.glucosehero.domain.repository.EntryRepository
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.domain.repository.SupplyRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindEntryRepository(impl: EntryRepositoryImpl): EntryRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindSupplyRepository(impl: SupplyRepositoryImpl): SupplyRepository
}
