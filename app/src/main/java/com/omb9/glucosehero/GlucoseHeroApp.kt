package com.omb9.glucosehero

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.omb9.glucosehero.work.InsightNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GlucoseHeroApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var insightNotifier: InsightNotifier

    override fun onCreate() {
        super.onCreate()
        insightNotifier.createChannel()
    }

    /** WorkManager (manifest initializer removed) builds workers through Hilt. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
