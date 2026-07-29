package com.example.countdown.data

import android.content.Context

data class DurationSetting(
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
) {
    companion object {
        val DEFAULT = DurationSetting(hours = 0, minutes = 1, seconds = 0)
    }
}

interface DurationPreferences {
    fun load(): DurationSetting
    fun save(hours: Int, minutes: Int, seconds: Int)
}

/** 测试与未注入持久化时使用：不读写磁盘，始终返回默认时长。 */
object MemoryDurationPreferences : DurationPreferences {
    override fun load(): DurationSetting = DurationSetting.DEFAULT
    override fun save(hours: Int, minutes: Int, seconds: Int) = Unit
}

class SharedDurationPreferences(
    context: Context,
) : DurationPreferences {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): DurationSetting {
        val hours = prefs.getInt(KEY_HOURS, DurationSetting.DEFAULT.hours).coerceIn(0, 99)
        val minutes = prefs.getInt(KEY_MINUTES, DurationSetting.DEFAULT.minutes).coerceIn(0, 59)
        val seconds = prefs.getInt(KEY_SECONDS, DurationSetting.DEFAULT.seconds).coerceIn(0, 59)
        if (hours == 0 && minutes == 0 && seconds == 0) return DurationSetting.DEFAULT
        return DurationSetting(hours, minutes, seconds)
    }

    override fun save(hours: Int, minutes: Int, seconds: Int) {
        prefs.edit()
            .putInt(KEY_HOURS, hours.coerceIn(0, 99))
            .putInt(KEY_MINUTES, minutes.coerceIn(0, 59))
            .putInt(KEY_SECONDS, seconds.coerceIn(0, 59))
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "countdown_prefs"
        private const val KEY_HOURS = "hours"
        private const val KEY_MINUTES = "minutes"
        private const val KEY_SECONDS = "seconds"
    }
}
