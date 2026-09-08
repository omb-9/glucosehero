package com.omb9.glucosehero.di

import com.omb9.glucosehero.data.vision.MealBarcodeScanner
import com.omb9.glucosehero.data.vision.PlayServicesMealBarcodeScanner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VisionModule {

    @Binds
    @Singleton
    abstract fun bindMealBarcodeScanner(
        impl: PlayServicesMealBarcodeScanner,
    ): MealBarcodeScanner
}
