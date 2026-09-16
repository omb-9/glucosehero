package com.omb9.glucosehero.di

import javax.inject.Qualifier

/** Process-lifetime [kotlinx.coroutines.CoroutineScope] that outlives any ViewModel. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
