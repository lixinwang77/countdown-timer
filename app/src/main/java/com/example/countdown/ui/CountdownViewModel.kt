package com.example.countdown.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.countdown.alert.AndroidTimerAlertController
import com.example.countdown.alert.NoOpTimerAlertController
import com.example.countdown.alert.TimerAlertController
import com.example.countdown.alert.TimerFinishBus
import com.example.countdown.data.DurationPreferences
import com.example.countdown.data.DurationSetting
import com.example.countdown.data.MemoryDurationPreferences
import com.example.countdown.data.SharedDurationPreferences
import com.example.countdown.data.TimerSessionStore
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
    private val alertController: TimerAlertController = NoOpTimerAlertController,
    private val sessionStore: TimerSessionStore? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(initialState(durationPreferences.load()))
    val uiState: StateFlow<CountdownUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var endAtMillis: Long = 0L
    private var alertActive: Boolean = false

    init {
        restoreSessionIfNeeded()
        viewModelScope.launch {
            TimerFinishBus.finished.collect {
                onExternalFinished()
            }
        }
        viewModelScope.launch {
            TimerFinishBus.silenced.collect {
                alertActive = false
                sessionStore?.setAlertActive(false)
            }
        }
    }

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
        alertController.stopAlert()
        alertActive = false
        endAtMillis = nowMillis() + total
        _uiState.update { it.copy(totalMillis = total, remainingMillis = total, phase = TimerPhase.Running) }
        sessionStore?.saveRunning(state.hours, state.minutes, state.seconds, total, endAtMillis)
        alertController.scheduleFinish(endAtMillis)
        startTicker()
    }

    fun pause() {
        if (_uiState.value.phase != TimerPhase.Running) return
        tickerJob?.cancel()
        alertController.cancelSchedule()
        val remaining = (endAtMillis - nowMillis()).coerceAtLeast(0L)
        val state = _uiState.value
        _uiState.update { it.copy(remainingMillis = remaining, phase = TimerPhase.Paused) }
        sessionStore?.savePaused(state.hours, state.minutes, state.seconds, state.totalMillis, remaining)
    }

    fun resume() {
        if (_uiState.value.phase != TimerPhase.Paused) return
        val remaining = _uiState.value.remainingMillis
        if (remaining <= 0L) {
            finish(startAlert = true)
            return
        }
        endAtMillis = nowMillis() + remaining
        val state = _uiState.value
        _uiState.update { it.copy(phase = TimerPhase.Running) }
        sessionStore?.saveRunning(state.hours, state.minutes, state.seconds, state.totalMillis, endAtMillis)
        alertController.scheduleFinish(endAtMillis)
        startTicker()
    }

    fun cancel() {
        stopAlertInternal()
        tickerJob?.cancel()
        tickerJob = null
        alertController.cancelSchedule()
        _uiState.update {
            val total = durationMillis(it.hours, it.minutes, it.seconds)
            it.copy(phase = TimerPhase.Setup, remainingMillis = total, totalMillis = total)
        }
        val state = _uiState.value
        sessionStore?.clearToSetup(state.hours, state.minutes, state.seconds)
    }

    fun resetToSetup() {
        cancel()
    }

    /** 结束页「重启」：按设置页保留的时分秒重新开始。 */
    fun restart() {
        if (_uiState.value.phase != TimerPhase.Finished) return
        stopAlertInternal()
        start()
    }

    /** 确保结束提示在响（前台恢复时若仍应响铃则拉起服务）。 */
    fun ensureAlertPlaying() {
        if (_uiState.value.phase != TimerPhase.Finished) return
        if (!alertActive) return
        alertController.startAlert()
    }

    /** 仅停止响铃/震动，仍停留在结束页。按音量键或电源键时调用。 */
    fun silenceAlert() {
        if (_uiState.value.phase != TimerPhase.Finished) return
        stopAlertInternal()
    }

    /** @return true 表示当前正在响铃/震动并已处理静音 */
    fun silenceAlertIfActive(): Boolean {
        if (_uiState.value.phase != TimerPhase.Finished) return false
        if (!alertActive) return false
        stopAlertInternal()
        return true
    }

    private fun restoreSessionIfNeeded() {
        val session = sessionStore?.load() ?: return
        when (session.phase) {
            TimerPhase.Running -> {
                endAtMillis = session.endAtMillis
                val remaining = (endAtMillis - nowMillis()).coerceAtLeast(0L)
                if (remaining <= 0L) {
                    _uiState.value = CountdownUiState(
                        hours = session.hours,
                        minutes = session.minutes,
                        seconds = session.seconds,
                        remainingMillis = 0L,
                        totalMillis = session.totalMillis,
                        phase = TimerPhase.Finished,
                    )
                    finish(startAlert = true)
                } else {
                    _uiState.value = CountdownUiState(
                        hours = session.hours,
                        minutes = session.minutes,
                        seconds = session.seconds,
                        remainingMillis = remaining,
                        totalMillis = session.totalMillis,
                        phase = TimerPhase.Running,
                    )
                    alertController.scheduleFinish(endAtMillis)
                    startTicker()
                }
            }
            TimerPhase.Paused -> {
                val remaining = sessionStore.remainingWhenPaused().coerceAtLeast(0L)
                _uiState.value = CountdownUiState(
                    hours = session.hours,
                    minutes = session.minutes,
                    seconds = session.seconds,
                    remainingMillis = remaining,
                    totalMillis = session.totalMillis,
                    phase = TimerPhase.Paused,
                )
            }
            TimerPhase.Finished -> {
                _uiState.value = CountdownUiState(
                    hours = session.hours,
                    minutes = session.minutes,
                    seconds = session.seconds,
                    remainingMillis = 0L,
                    totalMillis = session.totalMillis,
                    phase = TimerPhase.Finished,
                )
                alertActive = session.alertActive
                if (alertActive) {
                    alertController.startAlert()
                }
            }
            TimerPhase.Setup -> Unit
        }
    }

    private fun onExternalFinished() {
        if (_uiState.value.phase == TimerPhase.Finished) {
            alertActive = true
            return
        }
        finish(startAlert = false) // receiver already started alert
        alertActive = true
        sessionStore?.markFinished(alertActive = true)
    }

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
                if (remaining <= 0L) {
                    finish(startAlert = true)
                    break
                }
                delay(50L)
            }
        }
    }

    private fun finish(startAlert: Boolean) {
        tickerJob?.cancel()
        tickerJob = null
        alertController.cancelSchedule()
        _uiState.update { it.copy(remainingMillis = 0L, phase = TimerPhase.Finished) }
        sessionStore?.markFinished(alertActive = startAlert || alertActive)
        if (startAlert) {
            alertActive = true
            alertController.startAlert()
        }
    }

    private fun stopAlertInternal() {
        alertActive = false
        alertController.stopAlert()
        sessionStore?.setAlertActive(false)
    }

    override fun onCleared() {
        tickerJob?.cancel()
        // 不在此处 stopAlert：进程退到后台时 ViewModel 可能被清掉，但闹钟服务应继续响
        super.onCleared()
    }

    companion object {
        fun durationMillis(hours: Int, minutes: Int, seconds: Int): Long =
            ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L

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
        val appContext = context.applicationContext
        return CountdownViewModel(
            durationPreferences = SharedDurationPreferences(appContext),
            alertController = AndroidTimerAlertController(appContext),
            sessionStore = TimerSessionStore(appContext),
        ) as T
    }
}
