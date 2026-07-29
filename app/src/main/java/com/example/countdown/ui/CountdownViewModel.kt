package com.example.countdown.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.countdown.data.DurationPreferences
import com.example.countdown.data.DurationSetting
import com.example.countdown.data.MemoryDurationPreferences
import com.example.countdown.data.SharedDurationPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class TimerPhase {
    Setup,
    Running,
    Paused,
    Finished,
}

data class CountdownUiState(
    val hours: Int = 0,
    val minutes: Int = 1,
    val seconds: Int = 0,
    val remainingMillis: Long = 60_000L,
    val totalMillis: Long = 60_000L,
    val phase: TimerPhase = TimerPhase.Setup,
)

class CountdownViewModel(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val durationPreferences: DurationPreferences = MemoryDurationPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(initialState(durationPreferences.load()))
    val uiState: StateFlow<CountdownUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var endAtMillis: Long = 0L
    private var mediaPlayer: MediaPlayer? = null
    private var activeVibrator: Vibrator? = null
    private var toneJob: Job? = null
    private var toneGenerator: ToneGenerator? = null

    fun setHours(value: Int) {
        if (_uiState.value.phase != TimerPhase.Setup) return
        _uiState.update { it.copy(hours = value.coerceIn(0, 99)) }
        syncSetupDuration()
    }

    fun setMinutes(value: Int) {
        if (_uiState.value.phase != TimerPhase.Setup) return
        _uiState.update { it.copy(minutes = value.coerceIn(0, 59)) }
        syncSetupDuration()
    }

    fun setSeconds(value: Int) {
        if (_uiState.value.phase != TimerPhase.Setup) return
        _uiState.update { it.copy(seconds = value.coerceIn(0, 59)) }
        syncSetupDuration()
    }

    fun applyPreset(hours: Int, minutes: Int, seconds: Int) {
        if (_uiState.value.phase != TimerPhase.Setup) return
        _uiState.update {
            it.copy(hours = hours, minutes = minutes, seconds = seconds)
        }
        syncSetupDuration()
    }

    fun start() {
        val state = _uiState.value
        val total = durationMillis(state.hours, state.minutes, state.seconds)
        if (total <= 0L) return
        endAtMillis = nowMillis() + total
        _uiState.update { it.copy(totalMillis = total, remainingMillis = total, phase = TimerPhase.Running) }
        startTicker()
    }

    fun pause() {
        if (_uiState.value.phase != TimerPhase.Running) return
        tickerJob?.cancel()
        val remaining = (endAtMillis - nowMillis()).coerceAtLeast(0L)
        _uiState.update { it.copy(remainingMillis = remaining, phase = TimerPhase.Paused) }
    }

    fun resume() {
        if (_uiState.value.phase != TimerPhase.Paused) return
        val remaining = _uiState.value.remainingMillis
        if (remaining <= 0L) { finish(); return }
        endAtMillis = nowMillis() + remaining
        _uiState.update { it.copy(phase = TimerPhase.Running) }
        startTicker()
    }

    fun cancel() {
        stopAlert()
        tickerJob?.cancel()
        tickerJob = null
        _uiState.update {
            it.copy(phase = TimerPhase.Setup, remainingMillis = durationMillis(it.hours, it.minutes, it.seconds), totalMillis = durationMillis(it.hours, it.minutes, it.seconds))
        }
    }

    fun resetToSetup() { cancel() }

    /** 结束页「重启」：按设置页保留的时分秒重新开始。 */
    fun restart() {
        if (_uiState.value.phase != TimerPhase.Finished) return
        stopAlert()
        start()
    }

    fun notifyFinished(context: Context) {
        val appContext = context.applicationContext
        stopAlert()
        vibrate(appContext)
        playAlarm(appContext)
    }

    /** 仅停止响铃/震动，仍停留在结束页。按音量键或电源键时调用。 */
    fun silenceAlert() {
        if (_uiState.value.phase != TimerPhase.Finished) return
        stopAlert()
    }

    /** @return true 表示当前正在响铃/震动并已处理静音 */
    fun silenceAlertIfActive(): Boolean {
        if (_uiState.value.phase != TimerPhase.Finished) return false
        if (!isAlertActive()) return false
        stopAlert()
        return true
    }

    private fun isAlertActive(): Boolean =
        mediaPlayer != null || toneJob != null || activeVibrator != null

    private fun syncSetupDuration() {
        _uiState.update {
            val total = durationMillis(it.hours, it.minutes, it.seconds)
            it.copy(totalMillis = total, remainingMillis = total)
        }
        val state = _uiState.value
        durationPreferences.save(state.hours, state.minutes, state.seconds)
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                val remaining = (endAtMillis - nowMillis()).coerceAtLeast(0L)
                _uiState.update { it.copy(remainingMillis = remaining) }
                if (remaining <= 0L) { finish(); break }
                delay(50L)
            }
        }
    }

    private fun finish() {
        tickerJob?.cancel()
        tickerJob = null
        _uiState.update { it.copy(remainingMillis = 0L, phase = TimerPhase.Finished) }
    }

    private fun vibrate(context: Context) {
        runCatching {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            // repeatIndex = 0：从波形开头循环，直到 stopAlert()
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 300, 500, 300, 500), 0))
            activeVibrator = vibrator
        }
    }

    private fun playAlarm(context: Context) {
        val played = runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return@runCatching false
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
            true
        }.getOrDefault(false)

        if (!played) {
            runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_ALARM, 90)
                toneGenerator = tone
                toneJob = viewModelScope.launch {
                    while (isActive) {
                        tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1200)
                        delay(1600L)
                    }
                }
            }
        }
    }

    private fun stopAlert() {
        toneJob?.cancel()
        toneJob = null
        runCatching { toneGenerator?.release() }
        toneGenerator = null
        runCatching {
            mediaPlayer?.let { player ->
                if (player.isPlaying) player.stop()
                player.release()
            }
        }
        mediaPlayer = null
        runCatching { activeVibrator?.cancel() }
        activeVibrator = null
    }

    override fun onCleared() {
        stopAlert()
        tickerJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun durationMillis(hours: Int, minutes: Int, seconds: Int): Long = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L
        fun formatHms(millis: Long): Triple<Int, Int, Int> {
            val totalSeconds = (millis / 1000L).coerceAtLeast(0L).toInt()
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return Triple(hours, minutes, seconds)
        }
        fun formatDisplay(millis: Long): String {
            val (h, m, s) = formatHms(millis)
            return "%02d:%02d:%02d".format(h, m, s)
        }

        private fun initialState(setting: DurationSetting): CountdownUiState {
            val total = durationMillis(setting.hours, setting.minutes, setting.seconds)
            return CountdownUiState(
                hours = setting.hours,
                minutes = setting.minutes,
                seconds = setting.seconds,
                remainingMillis = total,
                totalMillis = total,
                phase = TimerPhase.Setup,
            )
        }
    }
}

class CountdownViewModelFactory(
    private val context: Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CountdownViewModel::class.java))
        return CountdownViewModel(
            durationPreferences = SharedDurationPreferences(context),
        ) as T
    }
}
