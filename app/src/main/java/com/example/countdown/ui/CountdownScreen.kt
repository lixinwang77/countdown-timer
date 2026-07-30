package com.example.countdown.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.countdown.R
import com.example.countdown.theme.*

private data class Preset(val hours: Int, val minutes: Int, val seconds: Int) { val label: String get() = "%02d:%02d:%02d".format(hours, minutes, seconds) }
private val presets = listOf(Preset(0, 10, 0), Preset(0, 15, 0), Preset(0, 30, 0))
@Composable fun CountdownScreen(viewModel: CountdownViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle(); val context = LocalContext.current; val activity = context as? Activity
    DisposableEffect(uiState.phase) { val window = activity?.window; if (uiState.phase == TimerPhase.Running || uiState.phase == TimerPhase.Paused) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    LaunchedEffect(uiState.phase) { if (uiState.phase == TimerPhase.Finished) viewModel.ensureAlertPlaying() }
    Box(Modifier.fillMaxSize().background(White).statusBarsPadding().navigationBarsPadding()) {
        TopBar(Modifier.align(Alignment.TopEnd)) { viewModel.resetToSetup() }
        when (uiState.phase) {
            TimerPhase.Setup -> SetupContent(
                uiState.hours, uiState.minutes, uiState.seconds,
                viewModel::setHours, viewModel::setMinutes, viewModel::setSeconds,
                viewModel::applyPreset, viewModel::start,
            )
            TimerPhase.Running, TimerPhase.Paused, TimerPhase.Finished -> ActiveTimerContent(
                remainingMillis = uiState.remainingMillis,
                totalMillis = uiState.totalMillis,
                phase = uiState.phase,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onCancel = viewModel::cancel,
                onStop = viewModel::resetToSetup,
                onRestart = viewModel::restart,
            )
        }
    }
}
@Composable private fun TopBar(modifier: Modifier = Modifier, onReset: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier.padding(top = 4.dp, end = 4.dp)) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options), tint = Black)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reset)) },
                onClick = { expanded = false; onReset() },
            )
        }
    }
}
@Composable private fun SetupContent(hours: Int, minutes: Int, seconds: Int, onHoursChange: (Int) -> Unit, onMinutesChange: (Int) -> Unit, onSecondsChange: (Int) -> Unit, onPreset: (Int, Int, Int) -> Unit, onStart: () -> Unit) { Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Spacer(Modifier.weight(0.22f)); TimePickerSection(hours, minutes, seconds, onHoursChange, onMinutesChange, onSecondsChange); Spacer(Modifier.height(48.dp)); PresetRow(onPreset); Spacer(Modifier.weight(0.45f)); PrimaryPillButton(stringResource(R.string.start), onStart, Modifier.padding(bottom = 36.dp), hours > 0 || minutes > 0 || seconds > 0) } }
@Composable private fun TimePickerSection(hours: Int, minutes: Int, seconds: Int, onHoursChange: (Int) -> Unit, onMinutesChange: (Int) -> Unit, onSecondsChange: (Int) -> Unit) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Row(Modifier.fillMaxWidth(), Arrangement.Center, Alignment.CenterVertically) { PickerLabel(stringResource(R.string.hours), Modifier.width(72.dp)); Spacer(Modifier.width(36.dp)); PickerLabel(stringResource(R.string.minutes), Modifier.width(72.dp)); Spacer(Modifier.width(36.dp)); PickerLabel(stringResource(R.string.seconds), Modifier.width(72.dp)) }; Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) { WheelPicker(hours, 0..99, onHoursChange, Modifier.width(72.dp)); ColonSeparator(); WheelPicker(minutes, 0..59, onMinutesChange, Modifier.width(72.dp)); ColonSeparator(); WheelPicker(seconds, 0..59, onSecondsChange, Modifier.width(72.dp)) } } }
@Composable private fun PickerLabel(text: String, modifier: Modifier = Modifier) { Text(text, color = LabelGray, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = modifier) }
@Composable private fun ColonSeparator() { Box(Modifier.width(28.dp).height(156.dp), contentAlignment = Alignment.Center) { Text(":", color = Black, fontSize = 32.sp, fontWeight = FontWeight.Bold) } }
@Composable private fun PresetRow(onPreset: (Int, Int, Int) -> Unit) { Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) { presets.forEach { preset -> PresetButton(preset.label) { onPreset(preset.hours, preset.minutes, preset.seconds) } } } }
@Composable private fun PresetButton(label: String, onClick: () -> Unit) { Box(Modifier.size(84.dp).clip(CircleShape).background(PresetGray).clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(bounded = true), role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) { Text(label, color = Black, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center) } }
@Composable
private fun ActiveTimerContent(
    remainingMillis: Long,
    totalMillis: Long,
    phase: TimerPhase,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
) {
    val isFinished = phase == TimerPhase.Finished
    val isPaused = phase == TimerPhase.Paused
    val progress = when {
        isFinished || totalMillis <= 0L -> 0f
        else -> (remainingMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
    }
    val displayTime = if (isFinished) "00:00:00" else CountdownViewModel.formatDisplay(remainingMillis)
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.28f))
        if (isFinished) {
            Text(
                stringResource(R.string.finished),
                color = Black,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            displayTime,
            color = if (isFinished) InactiveGray else Black,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier
                .fillMaxWidth(0.72f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(PresetGray),
        ) {
            Box(Modifier.fillMaxWidth(progress).height(4.dp).background(StartBlue))
        }
        Spacer(Modifier.weight(0.42f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 36.dp),
        ) {
            if (isFinished) {
                SecondaryPillButton(stringResource(R.string.stop), onStop)
                PrimaryPillButton(stringResource(R.string.restart), onRestart)
            } else {
                SecondaryPillButton(stringResource(R.string.cancel), onCancel)
                PrimaryPillButton(
                    stringResource(if (isPaused) R.string.resume else R.string.pause),
                    if (isPaused) onResume else onPause,
                )
            }
        }
    }
}
@Composable private fun PrimaryPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) { Box(modifier.width(220.dp).height(52.dp).clip(RoundedCornerShape(50)).background(if (enabled) StartBlue else StartBlue.copy(alpha = 0.35f)).clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = ripple(bounded = true, color = White), role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) { Text(text, color = White, fontSize = 18.sp, fontWeight = FontWeight.Medium) } }
@Composable private fun SecondaryPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) { Box(modifier.width(120.dp).height(52.dp).clip(RoundedCornerShape(50)).background(PresetGray).clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(bounded = true), role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) { Text(text, color = Black, fontSize = 16.sp, fontWeight = FontWeight.Medium) } }
@Preview(showBackground = true, showSystemUi = true) @Composable private fun SetupPreview() { CountdownTheme { Box(Modifier.fillMaxSize().background(White)) { SetupContent(0, 1, 0, {}, {}, {}, { _, _, _ -> }, {}) } } }
