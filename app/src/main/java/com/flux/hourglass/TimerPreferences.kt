package com.flux.hourglass

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.timerDataStore: DataStore<Preferences> by preferencesDataStore(name = "timer_prefs")

enum class DisplayMode { SAND, LED, WATER;
    companion object {
        fun parse(s: String?): DisplayMode = when (s) {
            "LED" -> LED
            "WATER" -> WATER
            else -> SAND
        }
    }
}

object TimerPreferenceKeys {
    val LAST_HOURS = intPreferencesKey("last_hours")
    val LAST_MINUTES = intPreferencesKey("last_minutes")
    val LAST_SECONDS = intPreferencesKey("last_seconds")
    val LAST_MODE = stringPreferencesKey("last_mode")
}

data class LastDuration(
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val mode: DisplayMode = DisplayMode.SAND,
)

object TimerPreferences {
    private val DEFAULT = LastDuration(hours = 0, minutes = 1, seconds = 0, mode = DisplayMode.SAND)

    fun observe(context: Context): Flow<LastDuration> =
        context.timerDataStore.data.map { prefs ->
            LastDuration(
                hours = (prefs[TimerPreferenceKeys.LAST_HOURS] ?: DEFAULT.hours).coerceIn(0, 99),
                minutes = (prefs[TimerPreferenceKeys.LAST_MINUTES] ?: DEFAULT.minutes).coerceIn(0, 59),
                seconds = (prefs[TimerPreferenceKeys.LAST_SECONDS] ?: DEFAULT.seconds).coerceIn(0, 59),
                mode = DisplayMode.parse(prefs[TimerPreferenceKeys.LAST_MODE]),
            )
        }

    suspend fun save(context: Context, hours: Int, minutes: Int, seconds: Int, mode: DisplayMode) {
        context.timerDataStore.edit { prefs ->
            prefs[TimerPreferenceKeys.LAST_HOURS] = hours.coerceIn(0, 99)
            prefs[TimerPreferenceKeys.LAST_MINUTES] = minutes.coerceIn(0, 59)
            prefs[TimerPreferenceKeys.LAST_SECONDS] = seconds.coerceIn(0, 59)
            prefs[TimerPreferenceKeys.LAST_MODE] = mode.name
        }
    }
}
