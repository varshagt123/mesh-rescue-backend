package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rescuelink_session")

class SessionPreferences(private val context: Context) {

    private object Keys {
        val LOGGED_IN_USER_ID = stringPreferencesKey("logged_in_user_id")
        val IS_OFFLINE_MODE = booleanPreferencesKey("is_offline_mode")
        val DEVICE_ID = stringPreferencesKey("persistent_device_id")
    }

    val loggedInUserId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LOGGED_IN_USER_ID]
    }

    val isOfflineMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.IS_OFFLINE_MODE] ?: false
    }

    suspend fun getOrCreateDeviceId(): String {
        var existing: String? = null
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.DEVICE_ID]
            if (!current.isNullOrBlank()) {
                existing = current
            } else {
                val generated = "RL-" + java.util.UUID.randomUUID().toString()
                    .replace("-", "")
                    .take(4)
                    .uppercase()
                prefs[Keys.DEVICE_ID] = generated
                existing = generated
            }
        }
        return existing ?: "RL-0001"
    }

    suspend fun setLoggedInUserId(uniqueUserId: String?) {
        context.dataStore.edit { prefs ->
            if (uniqueUserId == null) {
                prefs.remove(Keys.LOGGED_IN_USER_ID)
            } else {
                prefs[Keys.LOGGED_IN_USER_ID] = uniqueUserId
            }
        }
    }

    suspend fun setOfflineMode(offline: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_OFFLINE_MODE] = offline
        }
    }
}
