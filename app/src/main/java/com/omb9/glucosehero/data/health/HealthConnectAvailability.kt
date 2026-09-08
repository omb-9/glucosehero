package com.omb9.glucosehero.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class HealthConnectStatus {
    AVAILABLE,
    UPDATE_REQUIRED,
    UNAVAILABLE;

    val isAvailable: Boolean get() = this == AVAILABLE
}

/**
 * Health Connect is a platform component on Android 14 and above but a
 * separately installable APK below that, so availability must be checked at
 * runtime and never assumed.
 */
@Singleton
class HealthConnectAvailability @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Queries the Health Connect SDK availability status.
     * Maps HealthConnectClient.SDK_AVAILABLE -> AVAILABLE,
     * SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> UPDATE_REQUIRED,
     * SDK_UNAVAILABLE / others -> UNAVAILABLE.
     */
    fun status(): HealthConnectStatus = fromSdkStatus(HealthConnectClient.getSdkStatus(context))

    val isAvailable: Boolean get() = status().isAvailable

    companion object {
        fun fromSdkStatus(sdkStatus: Int): HealthConnectStatus = when (sdkStatus) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectStatus.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectStatus.UPDATE_REQUIRED
            HealthConnectClient.SDK_UNAVAILABLE -> HealthConnectStatus.UNAVAILABLE
            else -> HealthConnectStatus.UNAVAILABLE
        }

        fun check(context: Context): HealthConnectStatus =
            fromSdkStatus(HealthConnectClient.getSdkStatus(context))
    }
}
