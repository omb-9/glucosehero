package com.omb9.glucosehero.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.omb9.glucosehero.domain.repository.EntryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WearSyncEntryPoint {
    fun entryRepository(): EntryRepository
    fun wearSyncSettingsStore(): WearSyncSettingsStore
}

/**
 * Phone-side MessageClient listener. Watch quick-entry tiles/UI post JSON to
 * `/glucosehero/entry/quick`; a refresh ping hits `/glucosehero/glucose/request`.
 */
class WearMessageListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WearSyncEntryPoint::class.java,
        )
        when (messageEvent.path) {
            WearSyncProtocol.PATH_GLUCOSE_REQUEST -> WearSyncWorker.enqueue(this)
            WearSyncProtocol.PATH_ENTRY_QUICK -> runBlocking {
                runCatching {
                    if (!entryPoint.wearSyncSettingsStore().syncEnabled.first()) return@runCatching
                    val json = messageEvent.data.toString(Charsets.UTF_8)
                    val payload = WearSyncProtocol.json.decodeFromString(
                        WearQuickEntryPayload.serializer(),
                        json,
                    )
                    entryPoint.entryRepository().add(payload.toLogEvent())
                    WearSyncWorker.enqueue(this@WearMessageListenerService)
                }
            }
        }
    }
}
