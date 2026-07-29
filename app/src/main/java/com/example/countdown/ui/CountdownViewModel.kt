package com.example.countdown.ui

import android.media.AudioAttributes
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(CountdownUiState())
    val uiState: StateFlow<CountdownUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var endAtMillis: Long = 0L

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
        start()
    }

    fun notifyFinished(context: Context) { vibrate(context); playAlarm(context) }

    private fun syncSetupDuration() {
        _uiState.update {
            val total = durationMillis(it.hours, it.minutes, it.seconds)
            it.copy(totalMillis = total, remainingMillis = total)
        }
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
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 400), -1))
        }
    }

    private fun playAlarm(context: Context) {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            ringtone?.play()
            viewModelScope.launch { delay(2500L); runCatching { ringtone?.stop() } }
        }.onFailure {
            runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_ALARM, 80)
                tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500)
                viewModelScope.launch { delay(1600L); tone.release() }
            }
        }
    }

    override fun onCleared() { tickerJob?.cancel(); super.onCleared() }

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
    }
}
