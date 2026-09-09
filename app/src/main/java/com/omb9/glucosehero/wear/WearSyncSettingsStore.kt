package com.omb9.glucosehero.wear

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.wearSyncDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "wear_sync",
)

@Singleton
class WearSyncSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyEnabled = booleanPreferencesKey("wear_sync_enabled")

    val syncEnabled: Flow<Boolean> = context.wearSyncDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs -> prefs[keyEnabled] ?: true }

    suspend fun setSyncEnabled(enabled: Boolean) {
        context.wearSyncDataStore.edit { prefs ->
            prefs[keyEnabled] = enabled
        }
    }
}
