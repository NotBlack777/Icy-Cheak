package com.icy.icycheak.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "icycheak_local")

/**
 * Tiny DataStore-backed JSON string store used for the app's local-only
 * collections (battery history, benchmark runs, saved scripts, saved exports).
 *
 * Everything here stays on-device — nothing is ever uploaded or synced.
 */
object LocalStore {
    private lateinit var appContext: Context
    private val store by lazy { appContext.dataStore }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun putJson(key: String, json: String) {
        store.edit { it[stringPreferencesKey(key)] = json }
    }

    suspend fun getJson(key: String): String? =
        store.data.first()[stringPreferencesKey(key)]

    fun jsonFlow(key: String): Flow<String?> =
        store.data.map { it[stringPreferencesKey(key)] }
}
