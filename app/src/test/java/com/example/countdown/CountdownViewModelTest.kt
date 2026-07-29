package com.example.countdown.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CountdownViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }
    @Test fun formatDisplay_padsDigits() {
        assertEquals("00:01:00", CountdownViewModel.formatDisplay(60_000L))
        assertEquals("01:01:01", CountdownViewModel.formatDisplay(3_661_000L))
    }
    @Test fun applyPreset_updatesPickerValues() = runTest(dispatcher) {
        val viewModel = CountdownViewModel()
        viewModel.applyPreset(0, 15, 0)
        val state = viewModel.uiState.value
        assertEquals(0, state.hours); assertEquals(15, state.minutes); assertEquals(0, state.seconds); assertEquals(900_000L, state.totalMillis)
    }
    @Test fun start_thenPause_keepsRemainingTime() = runTest(dispatcher) {
        var now = 0L
        val viewModel = CountdownViewModel(nowMillis = { now })
        viewModel.applyPreset(0, 0, 5); viewModel.start(); now = 1_200L; advanceTimeBy(50L); runCurrent(); viewModel.pause()
        assertEquals(TimerPhase.Paused, viewModel.uiState.value.phase); assertEquals(3_800L, viewModel.uiState.value.remainingMillis)
    }
    @Test fun restart_fromFinished_startsOriginalDuration() = runTest(dispatcher) {
        var now = 0L
        val viewModel = CountdownViewModel(nowMillis = { now })
        viewModel.applyPreset(0, 0, 3); viewModel.start(); now = 3_100L; advanceTimeBy(50L); runCurrent()
        assertEquals(TimerPhase.Finished, viewModel.uiState.value.phase)
        viewModel.restart(); runCurrent()
        val state = viewModel.uiState.value
        assertEquals(TimerPhase.Running, state.phase); assertEquals(3_000L, state.totalMillis); assertEquals(3_000L, state.remainingMillis)
        viewModel.cancel()
    }
    @Test fun resetToSetup_fromFinished_returnsToSetup() = runTest(dispatcher) {
        var now = 0L
        val viewModel = CountdownViewModel(nowMillis = { now })
        viewModel.applyPreset(0, 0, 2); viewModel.start(); now = 2_100L; advanceTimeBy(50L); runCurrent()
        assertEquals(TimerPhase.Finished, viewModel.uiState.value.phase)
        viewModel.resetToSetup()
        val state = viewModel.uiState.value
        assertEquals(TimerPhase.Setup, state.phase); assertEquals(0, state.hours); assertEquals(0, state.minutes); assertEquals(2, state.seconds)
    }
    @Test fun durationPreferences_restoredOnNewViewModel() = runTest(dispatcher) {
        val prefs = object : com.example.countdown.data.DurationPreferences {
            private var saved = com.example.countdown.data.DurationSetting.DEFAULT
            override fun load() = saved
            override fun save(hours: Int, minutes: Int, seconds: Int) {
                saved = com.example.countdown.data.DurationSetting(hours, minutes, seconds)
            }
        }
        val first = CountdownViewModel(durationPreferences = prefs)
        first.setMinutes(2)
        assertEquals(2, first.uiState.value.minutes)
        val second = CountdownViewModel(durationPreferences = prefs)
        assertEquals(0, second.uiState.value.hours)
        assertEquals(2, second.uiState.value.minutes)
        assertEquals(0, second.uiState.value.seconds)
        assertEquals(120_000L, second.uiState.value.totalMillis)
    }
}
