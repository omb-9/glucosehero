package com.omb9.glucosehero.di

import com.omb9.glucosehero.data.backup.DataKeyWrapper
import com.omb9.glucosehero.data.backup.KeystoreDataKeyWrapper
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupCryptoModule {

    @Binds
    @Singleton
    abstract fun bindDataKeyWrapper(impl: KeystoreDataKeyWrapper): DataKeyWrapper
}
