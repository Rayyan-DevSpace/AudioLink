package com.rayyanshehzad.audiolink.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rayyanshehzad.audiolink.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "audiolink_prefs")

/**
 * Single source of truth for everything the UI and the foreground service need to
 * agree on: selected output/input device ids, split-routing on/off, app-scope
 * selection, and the theme preference. Backed by DataStore so it survives process
 * death and is readable from both the Activity and the Service.
 */
class RoutingRepository(private val context: Context) {

    private object Keys {
        val OUTPUT_DEVICE_ID = stringPreferencesKey("output_device_id")
        val OUTPUT_DEVICE_NAME = stringPreferencesKey("output_device_name")
        val INPUT_DEVICE_ID = stringPreferencesKey("input_device_id")
        val INPUT_DEVICE_NAME = stringPreferencesKey("input_device_name")
        val SPLIT_ROUTING_ON = booleanPreferencesKey("split_routing_on")
        val ALL_APPS = booleanPreferencesKey("all_apps")
        val SELECTED_APPS = stringSetPreferencesKey("selected_apps")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val outputDeviceName: Flow<String?> = context.dataStore.data.map { it[Keys.OUTPUT_DEVICE_NAME] }
    val inputDeviceName: Flow<String?> = context.dataStore.data.map { it[Keys.INPUT_DEVICE_NAME] }
    val splitRoutingOn: Flow<Boolean> = context.dataStore.data.map { it[Keys.SPLIT_ROUTING_ON] ?: false }
    val allApps: Flow<Boolean> = context.dataStore.data.map { it[Keys.ALL_APPS] ?: true }
    val selectedApps: Flow<Set<String>> = context.dataStore.data.map { it[Keys.SELECTED_APPS] ?: emptySet() }
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        ThemeMode.valueOf(it[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
    }

    suspend fun setOutputDevice(id: String, name: String) {
        context.dataStore.edit {
            it[Keys.OUTPUT_DEVICE_ID] = id
            it[Keys.OUTPUT_DEVICE_NAME] = name
        }
    }

    suspend fun setInputDevice(id: String, name: String) {
        context.dataStore.edit {
            it[Keys.INPUT_DEVICE_ID] = id
            it[Keys.INPUT_DEVICE_NAME] = name
        }
    }

    suspend fun setSplitRoutingOn(on: Boolean) {
        context.dataStore.edit { it[Keys.SPLIT_ROUTING_ON] = on }
    }

    suspend fun setAllApps(all: Boolean) {
        context.dataStore.edit { it[Keys.ALL_APPS] = all }
    }

    suspend fun setSelectedApps(packages: Set<String>) {
        context.dataStore.edit { it[Keys.SELECTED_APPS] = packages }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }
}
