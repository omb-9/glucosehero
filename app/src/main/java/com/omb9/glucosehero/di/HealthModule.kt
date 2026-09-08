package com.omb9.glucosehero.di

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import com.omb9.glucosehero.data.health.HealthConnectAvailability
import com.omb9.glucosehero.data.health.HealthConnectStatus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Health Connect dependencies.
 *
 * Provides a nullable [HealthConnectClient]. [HealthConnectClient.getOrCreate] throws
 * on unsupported devices, so an eager singleton `@Provides` would crash the Hilt graph
 * on hardware without Health Connect. Client creation is therefore gated on availability
 * and wrapped with [runCatching].
 */
@Module
@InstallIn(SingletonComponent::class)
object HealthModule {

    @Provides
    @Singleton
    fun provideHealthConnectClient(
        @ApplicationContext context: Context,
        availability: HealthConnectAvailability,
    ): HealthConnectClient? =
        if (availability.status() == HealthConnectStatus.AVAILABLE) {
            runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
        } else {
            null
        }
}
