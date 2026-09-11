package com.omb9.glucosehero.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    /**
     * UTC clock for instants. Callers that need a local zone must use
     * [java.time.ZoneId.systemDefault] at evaluation time so a travel-day
     * is not stuck with a zone captured at process start.
     *
     * FEATURE: dosing-profiles
     */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}
