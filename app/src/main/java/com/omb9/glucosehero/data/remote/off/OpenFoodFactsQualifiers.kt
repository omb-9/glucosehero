package com.omb9.glucosehero.data.remote.off

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
@Deprecated("Use @Named(\"openfoodfacts\") instead")
annotation class OffHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
@Deprecated("Use @Named(\"openfoodfacts\") instead")
annotation class OffRetrofit
