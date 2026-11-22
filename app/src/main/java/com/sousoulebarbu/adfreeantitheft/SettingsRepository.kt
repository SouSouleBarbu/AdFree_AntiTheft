package com.sousoulebarbu.adfreeantitheft

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "anti_theft_settings")

data class AppSettings(
    val placementDelaySec: Int = DEFAULT_PLACEMENT_DELAY_SEC,
    val sensitivityLimit: Float = DEFAULT_SENSITIVITY_LIMIT,
    val consecutiveExceededCount: Int = DEFAULT_CONSECUTIVE_EXCEEDED_COUNT,
    val checkIntervalMs: Long = DEFAULT_CHECK_INTERVAL_MS,
    val cableUnplugAlarmEnabled: Boolean = DEFAULT_CABLE_UNPLUG_ALARM_ENABLED,
    val onboardingCompleted: Boolean = DEFAULT_ONBOARDING_COMPLETED,
    val ignoreAlarmBlockedAlways: Boolean = DEFAULT_IGNORE_ALARM_BLOCKED_ALWAYS,
) {
    companion object {
        const val DEFAULT_PLACEMENT_DELAY_SEC = 3
        const val DEFAULT_SENSITIVITY_LIMIT = 1.5f
        const val DEFAULT_CONSECUTIVE_EXCEEDED_COUNT = 5
        const val DEFAULT_CHECK_INTERVAL_MS = 50L
        const val DEFAULT_CABLE_UNPLUG_ALARM_ENABLED = true
        const val DEFAULT_ONBOARDING_COMPLETED = false
        const val DEFAULT_IGNORE_ALARM_BLOCKED_ALWAYS = false
    }
}

class SettingsRepository(private val context: Context) {

    private companion object {
        val KEY_PLACEMENT_DELAY_SEC = intPreferencesKey("placement_delay_sec")
        val KEY_SENSITIVITY_LIMIT = floatPreferencesKey("sensitivity_limit")
        val KEY_CONSECUTIVE_EXCEEDED_COUNT = intPreferencesKey("consecutive_exceeded_count")
        val KEY_CHECK_INTERVAL_MS = longPreferencesKey("check_interval_ms")
        val KEY_CABLE_UNPLUG_ALARM_ENABLED = booleanPreferencesKey("cable_unplug_alarm_enabled")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_IGNORE_ALARM_BLOCKED_ALWAYS = booleanPreferencesKey("ignore_alarm_blocked_always")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AppSettings(
                placementDelaySec = preferences[KEY_PLACEMENT_DELAY_SEC]
                    ?: AppSettings.DEFAULT_PLACEMENT_DELAY_SEC,
                sensitivityLimit = preferences[KEY_SENSITIVITY_LIMIT]
                    ?: AppSettings.DEFAULT_SENSITIVITY_LIMIT,
                consecutiveExceededCount = preferences[KEY_CONSECUTIVE_EXCEEDED_COUNT]
                    ?: AppSettings.DEFAULT_CONSECUTIVE_EXCEEDED_COUNT,
                checkIntervalMs = preferences[KEY_CHECK_INTERVAL_MS]
                    ?: AppSettings.DEFAULT_CHECK_INTERVAL_MS,
                cableUnplugAlarmEnabled = preferences[KEY_CABLE_UNPLUG_ALARM_ENABLED]
                    ?: AppSettings.DEFAULT_CABLE_UNPLUG_ALARM_ENABLED,
                onboardingCompleted = preferences[KEY_ONBOARDING_COMPLETED]
                    ?: AppSettings.DEFAULT_ONBOARDING_COMPLETED,
                ignoreAlarmBlockedAlways = preferences[KEY_IGNORE_ALARM_BLOCKED_ALWAYS]
                    ?: AppSettings.DEFAULT_IGNORE_ALARM_BLOCKED_ALWAYS
            )
        }

    suspend fun updatePlacementDelaySec(value: Int) {
        val clampedValue = value.coerceIn(1, 30)
        context.dataStore.edit { preferences ->
            preferences[KEY_PLACEMENT_DELAY_SEC] = clampedValue
        }
    }

    suspend fun updateSensitivityLimit(value: Float) {
        val clampedValue = value.coerceIn(1.1f, 2.0f)
        context.dataStore.edit { preferences ->
            preferences[KEY_SENSITIVITY_LIMIT] = clampedValue
        }
    }

    suspend fun updateConsecutiveExceededCount(value: Int) {
        val clampedValue = value.coerceIn(1, 10)
        context.dataStore.edit { preferences ->
            preferences[KEY_CONSECUTIVE_EXCEEDED_COUNT] = clampedValue
        }
    }

    suspend fun updateCheckIntervalMs(value: Long) {
        val clampedValue = value.coerceIn(20L, 200L)
        context.dataStore.edit { preferences ->
            preferences[KEY_CHECK_INTERVAL_MS] = clampedValue
        }
    }

    suspend fun updateCableUnplugAlarmEnabled(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CABLE_UNPLUG_ALARM_ENABLED] = value
        }
    }

    suspend fun updateOnboardingCompleted(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = value
        }
    }

    suspend fun updateIgnoreAlarmBlockedAlways(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_IGNORE_ALARM_BLOCKED_ALWAYS] = value
        }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun getSettingsSnapshot(): AppSettings {
        return settingsFlow.first()
    }
}
