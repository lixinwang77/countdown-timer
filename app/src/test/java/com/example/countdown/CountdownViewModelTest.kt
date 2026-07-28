package com.example.countdown.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CountdownViewModelTest {

    @Test
    fun formatDisplay_padsDigits() {
        assertEquals("00:01:00", CountdownViewModel.formatDisplay(60_000L))
        assertEquals("01:01:01", CountdownViewModel.formatDisplay(3_661_000L))
    }

    @Test
    fun applyPreset_updatesPickerValues() = runTest {
        val viewModel = CountdownViewModel()
        viewModel.applyPreset(0, 15, 0)
        val state = viewModel.uiState.value
        assertEquals(0, state.hours)
        assertEquals(15, state.minutes)
        assertEquals(0, state.seconds)
        assertEquals(900_000L, state.totalMillis)
    }

    @Test
    fun start_thenPause_keepsRemainingTime() = runTest {
        val viewModel = CountdownViewModel()
        viewModel.applyPreset(0, 0, 5)
        viewModel.start()
        advanceTimeBy(1_200L)
        runCurrent()
        viewModel.pause()

        val remaining = viewModel.uiState.value.remainingMillis
        assertEquals(TimerPhase.Paused, viewModel.uiState.value.phase)
        assertTrue(remaining in 3_500L..4_000L)
    }
}
