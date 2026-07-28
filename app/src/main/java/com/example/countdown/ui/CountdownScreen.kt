package com.example.countdown.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.countdown.theme.Black
import com.example.countdown.theme.CountdownTheme
import com.example.countdown.theme.InactiveGray
import com.example.countdown.theme.LabelGray
import com.example.countdown.theme.PresetGray
import com.example.countdown.theme.StartBlue
import com.example.countdown.theme.White

private data class Preset(val hours: Int, val minutes: Int, val seconds: Int) {
    val label: String
        get() = "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private val presets = listOf(
    Preset(0, 10, 0),
    Preset(0, 15, 0),
    Preset(0, 30, 0),
)

@Composable
fun CountdownScreen(
    viewModel: CountdownViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(uiState.phase) {
        val window = activity?.window
        if (uiState.phase == TimerPhase.Running || uiState.phase == TimerPhase.Paused) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(uiState.phase) {
        if (uiState.phase == TimerPhase.Finished) {
            viewModel.notifyFinished(context)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(White)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        TopBar(
            modifier = Modifier.align(Alignment.TopEnd),
            onReset = { viewModel.resetToSetup() },
        )

        AnimatedContent(
            targetState = uiState.phase,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "phase",
            modifier = Modifier.fillMaxSize(),
        ) { phase ->
            when (phase) {
                TimerPhase.Setup -> SetupContent(
                    hours = uiState.hours,
                    minutes = uiState.minutes,
                    seconds = uiState.seconds,
                    onHoursChange = viewModel::setHours,
                    onMinutesChange = viewModel::setMinutes,
                    onSecondsChange = viewModel::setSeconds,
                    onPreset = viewModel::applyPreset,
                    onStart = viewModel::start,
                )

                TimerPhase.Running, TimerPhase.Paused -> RunningContent(
                    remainingMillis = uiState.remainingMillis,
                    totalMillis = uiState.totalMillis,
                    isPaused = phase == TimerPhase.Paused,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onCancel = viewModel::cancel,
                )

                TimerPhase.Finished -> FinishedContent(
                    onReset = viewModel::resetToSetup,
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    modifier: Modifier = Modifier,
    onReset: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.padding(top = 4.dp, end = 4.dp)) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.more_options),
                tint = Black,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reset)) },
                onClick = {
                    expanded = false
                    onReset()
                },
            )
        }
    }
}

@Composable
private fun SetupContent(
    hours: Int,
    minutes: Int,
    seconds: Int,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit,
    onSecondsChange: (Int) -> Unit,
    onPreset: (Int, Int, Int) -> Unit,
    onStart: () -> Unit,
) {
    val canStart = hours > 0 || minutes > 0 || seconds > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(0.22f))

        TimePickerSection(
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            onHoursChange = onHoursChange,
            onMinutesChange = onMinutesChange,
            onSecondsChange = onSecondsChange,
        )

        Spacer(modifier = Modifier.height(48.dp))

        PresetRow(onPreset = onPreset)

        Spacer(modifier = Modifier.weight(0.45f))

        PrimaryPillButton(
            text = stringResource(R.string.start),
            enabled = canStart,
            onClick = onStart,
            modifier = Modifier.padding(bottom = 36.dp),
        )
    }
}

@Composable
private fun TimePickerSection(
    hours: Int,
    minutes: Int,
    seconds: Int,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit,
    onSecondsChange: (Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PickerLabel(stringResource(R.string.hours), Modifier.width(72.dp))
            Spacer(modifier = Modifier.width(36.dp))
            PickerLabel(stringResource(R.string.minutes), Modifier.width(72.dp))
            Spacer(modifier = Modifier.width(36.dp))
            PickerLabel(stringResource(R.string.seconds), Modifier.width(72.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            WheelPicker(
                value = hours,
                range = 0..99,
                onValueChange = onHoursChange,
                modifier = Modifier.width(72.dp),
            )
            ColonSeparator()
            WheelPicker(
                value = minutes,
                range = 0..59,
                onValueChange = onMinutesChange,
                modifier = Modifier.width(72.dp),
            )
            ColonSeparator()
            WheelPicker(
                value = seconds,
                range = 0..59,
                onValueChange = onSecondsChange,
                modifier = Modifier.width(72.dp),
            )
        }
    }
}

@Composable
private fun PickerLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = LabelGray,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
private fun ColonSeparator() {
    Box(
        modifier = Modifier
            .width(28.dp)
            .height(156.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = ":",
            color = Black,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PresetRow(onPreset: (Int, Int, Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        presets.forEach { preset ->
            PresetButton(
                label = preset.label,
                onClick = { onPreset(preset.hours, preset.minutes, preset.seconds) },
            )
        }
    }
}

@Composable
private fun PresetButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(CircleShape)
            .background(PresetGray)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Black,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RunningContent(
    remainingMillis: Long,
    totalMillis: Long,
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    val progress = if (totalMillis > 0L) {
        remainingMillis.toFloat() / totalMillis.toFloat()
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(0.28f))

        Text(
            text = CountdownViewModel.formatDisplay(remainingMillis),
            color = Black,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(28.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(PresetGray),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(StartBlue),
            )
        }

        Spacer(modifier = Modifier.weight(0.42f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 36.dp),
        ) {
            SecondaryPillButton(
                text = stringResource(R.string.cancel),
                onClick = onCancel,
            )
            PrimaryPillButton(
                text = stringResource(if (isPaused) R.string.resume else R.string.pause),
                onClick = if (isPaused) onResume else onPause,
            )
        }
    }
}

@Composable
private fun FinishedContent(onReset: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.finished),
            color = Black,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "00:00:00",
            color = InactiveGray,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )

        Spacer(modifier = Modifier.height(64.dp))

        PrimaryPillButton(
            text = stringResource(R.string.reset),
            onClick = onReset,
        )
    }
}

@Composable
private fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .width(220.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) StartBlue else StartBlue.copy(alpha = 0.35f))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = White),
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SecondaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(120.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(PresetGray)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SetupPreview() {
    CountdownTheme {
        Box(Modifier.fillMaxSize().background(White)) {
            SetupContent(
                hours = 0,
                minutes = 1,
                seconds = 0,
                onHoursChange = {},
                onMinutesChange = {},
                onSecondsChange = {},
                onPreset = { _, _, _ -> },
                onStart = {},
            )
        }
    }
}
