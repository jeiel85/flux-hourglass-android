package com.flux.hourglass

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.timerDataStore: DataStore<Preferences> by preferencesDataStore(name = "timer_prefs")

enum class DisplayMode { 
    SAND, LED, WATER, 
    NEBULA, MOSS, INK, CRYSTAL, WAX, FLIP, FIRE,
    MAGNETIC, AURORA, RAIN, BLACKHOLE, ELECTRIC;
    companion object {
        fun parse(s: String?): DisplayMode = when (s) {
            "LED" -> LED
            "WATER" -> WATER
            "NEBULA" -> NEBULA
            "MOSS" -> MOSS
            "INK" -> INK
            "CRYSTAL" -> CRYSTAL
            "WAX" -> WAX
            "FLIP" -> FLIP
            "FIRE" -> FIRE
            "MAGNETIC" -> MAGNETIC
            "AURORA" -> AURORA
            "RAIN" -> RAIN
            "BLACKHOLE" -> BLACKHOLE
            "ELECTRIC" -> ELECTRIC
            else -> SAND
        }
    }
}

object TimerPreferenceKeys {
    val LAST_HOURS = intPreferencesKey("last_hours")
    val LAST_MINUTES = intPreferencesKey("last_minutes")
    val LAST_SECONDS = intPreferencesKey("last_seconds")
    val LAST_MODE = stringPreferencesKey("last_mode")
    val CALIBRATION_X = intPreferencesKey("calibration_x")
    val CALIBRATION_Y = intPreferencesKey("calibration_y")
    val CALIBRATION_Z = intPreferencesKey("calibration_z")
    val SANDBOX_GRAVITY_SCALE = floatPreferencesKey("sandbox_gravity_scale")
    val SANDBOX_PARTICLE_SIZE = floatPreferencesKey("sandbox_particle_size")
    val SANDBOX_PARTICLE_COUNT = floatPreferencesKey("sandbox_particle_count")
}

data class SandboxSettings(
    val gravityScale: Float = 1.0f,
    val particleSize: Float = 1.0f,
    val particleCount: Float = 1.0f,
)

data class LastDuration(
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val mode: DisplayMode = DisplayMode.SAND,
)

data class CalibrationData(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 9.81f
)

object TimerPreferences {
    private val DEFAULT = LastDuration(hours = 0, minutes = 1, seconds = 0, mode = DisplayMode.SAND)
    private val DEFAULT_CALIBRATION = CalibrationData()

    fun observe(context: Context): Flow<LastDuration> =
        context.timerDataStore.data.map { prefs ->
            LastDuration(
                hours = (prefs[TimerPreferenceKeys.LAST_HOURS] ?: DEFAULT.hours).coerceIn(0, 99),
                minutes = (prefs[TimerPreferenceKeys.LAST_MINUTES] ?: DEFAULT.minutes).coerceIn(0, 59),
                seconds = (prefs[TimerPreferenceKeys.LAST_SECONDS] ?: DEFAULT.seconds).coerceIn(0, 59),
                mode = DisplayMode.parse(prefs[TimerPreferenceKeys.LAST_MODE]),
            )
        }

    fun observeCalibration(context: Context): Flow<CalibrationData> =
        context.timerDataStore.data.map { prefs ->
            CalibrationData(
                x = (prefs[TimerPreferenceKeys.CALIBRATION_X] ?: 0).toFloat() / 1000f,
                y = (prefs[TimerPreferenceKeys.CALIBRATION_Y] ?: 0).toFloat() / 1000f,
                z = (prefs[TimerPreferenceKeys.CALIBRATION_Z] ?: 9810).toFloat() / 1000f
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

    suspend fun saveCalibration(context: Context, calibration: CalibrationData) {
        context.timerDataStore.edit { prefs ->
            prefs[TimerPreferenceKeys.CALIBRATION_X] = (calibration.x * 1000).toInt()
            prefs[TimerPreferenceKeys.CALIBRATION_Y] = (calibration.y * 1000).toInt()
            prefs[TimerPreferenceKeys.CALIBRATION_Z] = (calibration.z * 1000).toInt()
        }
    }

    suspend fun resetCalibration(context: Context) {
        context.timerDataStore.edit { prefs ->
            prefs[TimerPreferenceKeys.CALIBRATION_X] = 0
            prefs[TimerPreferenceKeys.CALIBRATION_Y] = 0
            prefs[TimerPreferenceKeys.CALIBRATION_Z] = 9810
        }
    }

    fun observeSandbox(context: Context): Flow<SandboxSettings> =
        context.timerDataStore.data.map { prefs ->
            SandboxSettings(
                gravityScale = prefs[TimerPreferenceKeys.SANDBOX_GRAVITY_SCALE] ?: 1.0f,
                particleSize = prefs[TimerPreferenceKeys.SANDBOX_PARTICLE_SIZE] ?: 1.0f,
                particleCount = prefs[TimerPreferenceKeys.SANDBOX_PARTICLE_COUNT] ?: 1.0f,
            )
        }

    suspend fun saveSandbox(context: Context, settings: SandboxSettings) {
        context.timerDataStore.edit { prefs ->
            prefs[TimerPreferenceKeys.SANDBOX_GRAVITY_SCALE] = settings.gravityScale
            prefs[TimerPreferenceKeys.SANDBOX_PARTICLE_SIZE] = settings.particleSize
            prefs[TimerPreferenceKeys.SANDBOX_PARTICLE_COUNT] = settings.particleCount
        }
    }
}
