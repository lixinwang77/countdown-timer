package com.example.countdown.data

import android.content.Context
import com.example.countdown.ui.TimerPhase

data class TimerSession(
    val phase: TimerPhase,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val totalMillis: Long,
    val endAtMillis: Long,
    val alertActive: Boolean,
)

class TimerSessionStore(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveRunning(
        hours: Int,
        minutes: Int,
        seconds: Int,
        totalMillis: Long,
        endAtMillis: Long,
    ) {
        prefs.edit()
            .putString(KEY_PHASE, TimerPhase.Running.name)
            .putInt(KEY_HOURS, hours)
            .putInt(KEY_MINUTES, minutes)
            .putInt(KEY_SECONDS, seconds)
            .putLong(KEY_TOTAL, totalMillis)
            .putLong(KEY_END_AT, endAtMillis)
            .putBoolean(KEY_ALERT_ACTIVE, false)
            .apply()
    }

    fun savePaused(
        hours: Int,
        minutes: Int,
        seconds: Int,
        totalMillis: Long,
        remainingMillis: Long,
    ) {
        prefs.edit()
            .putString(KEY_PHASE, TimerPhase.Paused.name)
            .putInt(KEY_HOURS, hours)
            .putInt(KEY_MINUTES, minutes)
            .putInt(KEY_SECONDS, seconds)
            .putLong(KEY_TOTAL, totalMillis)
            .putLong(KEY_END_AT, 0L)
            .putLong(KEY_REMAINING, remainingMillis)
            .putBoolean(KEY_ALERT_ACTIVE, false)
            .apply()
    }

    fun markFinished(alertActive: Boolean) {
        prefs.edit()
            .putString(KEY_PHASE, TimerPhase.Finished.name)
            .putLong(KEY_REMAINING, 0L)
            .putBoolean(KEY_ALERT_ACTIVE, alertActive)
            .apply()
    }

    fun setAlertActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_ALERT_ACTIVE, active).apply()
    }

    fun clearToSetup(hours: Int, minutes: Int, seconds: Int) {
        val total = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L
        prefs.edit()
            .putString(KEY_PHASE, TimerPhase.Setup.name)
            .putInt(KEY_HOURS, hours)
            .putInt(KEY_MINUTES, minutes)
            .putInt(KEY_SECONDS, seconds)
            .putLong(KEY_TOTAL, total)
            .putLong(KEY_END_AT, 0L)
            .putLong(KEY_REMAINING, total)
            .putBoolean(KEY_ALERT_ACTIVE, false)
            .apply()
    }

    fun load(): TimerSession? {
        if (!prefs.contains(KEY_PHASE)) return null
        val phase = runCatching {
            TimerPhase.valueOf(prefs.getString(KEY_PHASE, TimerPhase.Setup.name)!!)
        }.getOrDefault(TimerPhase.Setup)
        return TimerSession(
            phase = phase,
            hours = prefs.getInt(KEY_HOURS, 0),
            minutes = prefs.getInt(KEY_MINUTES, 1),
            seconds = prefs.getInt(KEY_SECONDS, 0),
            totalMillis = prefs.getLong(KEY_TOTAL, 60_000L),
            endAtMillis = prefs.getLong(KEY_END_AT, 0L),
            alertActive = prefs.getBoolean(KEY_ALERT_ACTIVE, false),
        )
    }

    fun remainingWhenPaused(): Long = prefs.getLong(KEY_REMAINING, 0L)

    companion object {
        private const val PREFS_NAME = "countdown_session"
        private const val KEY_PHASE = "phase"
        private const val KEY_HOURS = "hours"
        private const val KEY_MINUTES = "minutes"
        private const val KEY_SECONDS = "seconds"
        private const val KEY_TOTAL = "total"
        private const val KEY_END_AT = "end_at"
        private const val KEY_REMAINING = "remaining"
        private const val KEY_ALERT_ACTIVE = "alert_active"
    }
}
