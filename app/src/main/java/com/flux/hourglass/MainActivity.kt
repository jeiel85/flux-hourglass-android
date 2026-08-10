package com.flux.hourglass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flux.hourglass.ui.theme.HourglassTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.flux.hourglass.ui.theme.PureBlack
import com.flux.hourglass.ui.theme.PureWhite
import com.flux.hourglass.ui.theme.SandWhite
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

val LocalSoundEnabled = androidx.compose.runtime.staticCompositionLocalOf { false }
val LocalOnSoundToggle = androidx.compose.runtime.staticCompositionLocalOf<() -> Unit> { {} }
val LocalSandboxSettings = androidx.compose.runtime.staticCompositionLocalOf { SandboxSettings() }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Enable complete full-screen Immersive Mode (hide system status bar and navigation bar)
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())

        setContent {
            HourglassTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PureBlack)
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(PureBlack)
                    ) {
                        HourglassApp()
                    }
                }
            }
        }
    }
}

@Composable
fun HourglassApp(
    viewModel: TimerViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.timerState.collectAsState()
    val remainingMillis by viewModel.remainingMillis.collectAsState()

    val seeded = remember {
        runBlocking {
            try {
                TimerPreferences.observe(context).first()
            } catch (e: Exception) {
                LastDuration(0, 1, 0, DisplayMode.SAND)
            }
        }
    }

    var hoursVal by remember { mutableStateOf(seeded.hours) }
    var minutesVal by remember { mutableStateOf(seeded.minutes) }
    var secondsVal by remember { mutableStateOf(seeded.seconds) }
    var modeVal by remember { mutableStateOf(seeded.mode) }
    var showCalibration by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val sandboxSettings by viewModel.sandboxSettings.collectAsState()
    var showSandboxSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        TimerPreferences.observeSandbox(context).collect { settings ->
            viewModel.updateSandboxSettings(settings)
        }
    }

    val audioPlayer = remember { ProceduralAudioPlayer() }
    var soundEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(state, soundEnabled, modeVal) {
        if (state is TimerState.Running && soundEnabled && modeVal != DisplayMode.LED) {
            audioPlayer.start(modeVal)
        } else {
            audioPlayer.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.stop()
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalSoundEnabled provides soundEnabled,
        LocalOnSoundToggle provides { soundEnabled = !soundEnabled },
        LocalSandboxSettings provides sandboxSettings
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
        when (val current = state) {
            is TimerState.Setup -> {
                SetupScreen(
                    hours = hoursVal,
                    minutes = minutesVal,
                    seconds = secondsVal,
                    mode = modeVal,
                    onHoursChange = { hoursVal = it },
                    onMinutesChange = { minutesVal = it },
                    onSecondsChange = { secondsVal = it },
                    onModeChange = { modeVal = it },
                    onPresetSelected = { h, m, s ->
                        hoursVal = h
                        minutesVal = m
                        secondsVal = s
                    },
                    onStart = {
                        if (hoursVal > 0 || minutesVal > 0 || secondsVal > 0) {
                            scope.launch {
                                TimerPreferences.save(context, hoursVal, minutesVal, secondsVal, modeVal)
                            }
                        }
                        viewModel.startTimer(hoursVal, minutesVal, secondsVal)
                    },
                    onCalibrate = { showCalibration = true },
                    onShowSandbox = { showSandboxSettings = true }
                )
            }
            is TimerState.Running -> {
                RunningScreen(
                    remainingMillis = remainingMillis,
                    totalMillis = current.totalMillis,
                    mode = modeVal,
                    onPause = { viewModel.pauseTimer() },
                    onReset = { viewModel.resetTimer() }
                )
            }
            is TimerState.Paused -> {
                PausedScreen(
                    remainingMillis = current.remainingMillis,
                    totalMillis = current.totalMillis,
                    onResume = { viewModel.resumeTimer() },
                    onReset = { viewModel.resetTimer() }
                )
            }
            is TimerState.Finished -> {
                FinishedScreen(
                    onReset = { viewModel.resetTimer() }
                )
            }
        }

        if (showCalibration) {
            CalibrationScreen(
                onDismiss = { showCalibration = false },
                onCalibrated = { showCalibration = false }
            )
        }

        if (showSandboxSettings) {
            SandboxSettingsDialog(
                settings = sandboxSettings,
                onSettingsChange = { settings ->
                    scope.launch {
                        TimerPreferences.saveSandbox(context, settings)
                    }
                    viewModel.updateSandboxSettings(settings)
                },
                onDismiss = { showSandboxSettings = false }
            )
        }
    }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
fun SetupScreen(
    hours: Int,
    minutes: Int,
    seconds: Int,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit,
    onSecondsChange: (Int) -> Unit,
    mode: DisplayMode = DisplayMode.SAND,
    onModeChange: (DisplayMode) -> Unit = {},
    onPresetSelected: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onStart: () -> Unit,
    onCalibrate: () -> Unit = {},
    onShowSandbox: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current

    // The setup content is laid out with SpaceBetween so it breathes on tall
    // screens. But if the content is ever taller than the viewport (small or
    // split screen, large system font, tall insets, an object row that grows),
    // SpaceBetween alone would push the picker and START button off the bottom
    // edge with no way to reach them. Wrapping in a min-height scroll container
    // keeps the spread-out look when it fits AND guarantees every control stays
    // reachable when it doesn't — the controls can never silently disappear.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = viewportHeight)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
        // App Title, CALI button, and SETT button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onShowSandbox()
                            }
                        )
                    }
                    .testTag("sandbox_button")
                    .padding(top = 24.dp, start = 8.dp)
            ) {
                Text(
                    text = "S E T T",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Light,
                    color = PureWhite.copy(alpha = 0.55f),
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                )
            }

            Text(
                text = "H O U R G L A S S",
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                color = PureWhite.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                letterSpacing = 3.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 24.dp)
            )

            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onCalibrate()
                            }
                        )
                    }
                    .testTag("cali_button")
                    .padding(top = 24.dp, end = 8.dp)
            ) {
                Text(
                    text = "C A L I",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Light,
                    color = PureWhite.copy(alpha = 0.55f),
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                )
            }
        }

        // Mode selector — a single horizontal filmstrip you swipe through.
        // One row instead of three frees the vertical space the picker and
        // START button need, and scales as more objects are added.
        val modeListState = rememberLazyListState()
        LaunchedEffect(mode) {
            val index = ModeOptions.indexOfFirst { it.mode == mode }
            if (index >= 0) {
                // Glide the chosen object into the centre. If it isn't yet
                // measured (off-screen on first frame), jump close first.
                fun centerDelta(): Float? {
                    val info = modeListState.layoutInfo
                    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                        ?: return null
                    val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                    return item.offset + item.size / 2f - viewportCenter
                }
                val delta = centerDelta()
                if (delta != null) {
                    modeListState.animateScrollBy(delta)
                } else {
                    modeListState.scrollToItem(index)
                    centerDelta()?.let { modeListState.animateScrollBy(it) }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mode_column")
        ) {
            LazyRow(
                state = modeListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mode_scroller"),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 48.dp)
            ) {
                items(ModeOptions, key = { it.tag }) { option ->
                    ModeTab(
                        label = option.label,
                        selected = mode == option.mode,
                        onClick = { onModeChange(option.mode) },
                        tag = option.tag
                    )
                }
            }
            // Edge fades hint that the strip continues past both ends.
            // matchParentSize overlays the row without forcing the wrapping
            // Box to grow (a fillMaxHeight child here would stretch to the
            // whole Column and shove the picker off-screen). No pointer
            // modifiers, so the items underneath stay tappable.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            0.0f to PureBlack,
                            0.07f to Color.Transparent,
                            0.93f to Color.Transparent,
                            1.0f to PureBlack
                        )
                    )
            )
        }

        // Quick Presets — minimal text labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("preset_row"),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PresetLabels.forEach { preset ->
                PresetChip(
                    label = preset.label,
                    onClick = {
                        onPresetSelected(preset.h, preset.m, preset.s)
                    }
                )
            }
        }

        // Triple Picker Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimePickerColumn(
                label = "H R",
                value = hours,
                onValueChange = onHoursChange,
                max = 99
            )
            Text(
                text = ":",
                fontSize = 40.sp,
                fontWeight = FontWeight.Light,
                color = PureWhite.copy(alpha = 0.45f),
                modifier = Modifier.padding(bottom = 24.dp)
            )
            TimePickerColumn(
                label = "M I N",
                value = minutes,
                onValueChange = onMinutesChange,
                max = 59
            )
            Text(
                text = ":",
                fontSize = 40.sp,
                fontWeight = FontWeight.Light,
                color = PureWhite.copy(alpha = 0.45f),
                modifier = Modifier.padding(bottom = 24.dp)
            )
            TimePickerColumn(
                label = "S E C",
                value = seconds,
                onValueChange = onSecondsChange,
                max = 59
            )
        }

        // Start Button using pure minimalist outline and thin letters
        Box(
            modifier = Modifier
                .padding(bottom = 48.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStart()
                        }
                    )
                }
                .testTag("start_button")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "S T A R T",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Light,
                    color = PureWhite,
                    letterSpacing = 4.sp,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp)
                )
                // A thin, gorgeous line beneath instead of a heavy box
                Spacer(
                    modifier = Modifier
                        .width(56.dp)
                        .height(1.dp)
                        .background(PureWhite.copy(alpha = 0.85f))
                )
            }
        }
        }
    }
}

@Composable
private fun ModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val haptic = LocalHapticFeedback.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .pointerInput(label) {
                detectTapGestures(
                    onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                )
            }
            .testTag(tag)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Light,
            color = if (selected) PureWhite else PureWhite.copy(alpha = 0.45f),
            letterSpacing = 3.sp,
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
        )
        Spacer(
            modifier = Modifier
                .width(20.dp)
                .height(1.dp)
                .background(
                    if (selected) PureWhite.copy(alpha = 0.85f)
                    else PureWhite.copy(alpha = 0.0f)
                )
        )
    }
}

private data class ModeOption(val mode: DisplayMode, val label: String, val tag: String)

// Display order of the object filmstrip. New objects append here.
private val ModeOptions = listOf(
    ModeOption(DisplayMode.SAND, "S A N D", "mode_sand"),
    ModeOption(DisplayMode.LED, "L E D", "mode_led"),
    ModeOption(DisplayMode.WATER, "W A T E R", "mode_water"),
    ModeOption(DisplayMode.FIRE, "F I R E", "mode_fire"),
)

private data class TimePreset(val label: String, val h: Int, val m: Int, val s: Int)

private val PresetLabels = listOf(
    TimePreset("1m", 0, 1, 0),
    TimePreset("3m", 0, 3, 0),
    TimePreset("5m", 0, 5, 0),
    TimePreset("10m", 0, 10, 0),
    TimePreset("25m", 0, 25, 0),
    TimePreset("1h", 1, 0, 0)
)

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .pointerInput(label) {
                detectTapGestures(
                    onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                )
            }
            .testTag("preset_$label")
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Light,
            color = PureWhite.copy(alpha = 0.78f),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        )
    }
}

@Composable
fun TimePickerColumn(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    max: Int
) {
    val haptic = LocalHapticFeedback.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (dragAmount.y < -10f) {
                            onValueChange((value + 1).coerceIn(0, max))
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } else if (dragAmount.y > 10f) {
                            onValueChange((value - 1).coerceIn(0, max))
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                )
            }
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Light,
            color = PureWhite.copy(alpha = 0.6f),
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        IconButton(
            onClick = {
                onValueChange((value + 1).coerceIn(0, max))
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            colors = IconButtonDefaults.iconButtonColors(contentColor = PureWhite.copy(alpha = 0.6f)),
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Increment $label",
                modifier = Modifier.size(20.dp)
            )
        }

        Text(
            text = String.format("%02d", value),
            fontSize = 54.sp,
            fontWeight = FontWeight.Light,
            color = PureWhite,
            textAlign = TextAlign.Center
        )

        IconButton(
            onClick = {
                onValueChange((value - 1).coerceIn(0, max))
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            colors = IconButtonDefaults.iconButtonColors(contentColor = PureWhite.copy(alpha = 0.6f)),
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Decrement $label",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun RunningScreen(
    remainingMillis: Long,
    totalMillis: Long,
    mode: DisplayMode = DisplayMode.SAND,
    onPause: () -> Unit = {},
    onReset: () -> Unit
) {
    when (mode) {
        DisplayMode.SAND -> SandRunningScreen(
            remainingMillis = remainingMillis,
            totalMillis = totalMillis,
            onPause = onPause,
            onReset = onReset
        )
        DisplayMode.LED -> LedRunningScreen(
            remainingMillis = remainingMillis,
            totalMillis = totalMillis,
            onPause = onPause,
            onReset = onReset
        )
        DisplayMode.WATER -> WaterRunningScreen(
            remainingMillis = remainingMillis,
            totalMillis = totalMillis,
            onPause = onPause,
            onReset = onReset
        )
        DisplayMode.FIRE -> FireRunningScreen(
            remainingMillis = remainingMillis,
            totalMillis = totalMillis,
            onPause = onPause,
            onReset = onReset
        )
    }
}



@Composable
private fun RunningOverlay(
    remainingMillis: Long,
    isScreenPressed: Boolean,
    onPause: () -> Unit,
    onReset: () -> Unit,
    soundAvailable: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val soundEnabled = LocalSoundEnabled.current
    val onSoundToggle = LocalOnSoundToggle.current

    val minutesLeft = (remainingMillis / 60000) % 60
    val hoursLeft = (remainingMillis / 3600000)
    val secondsLeft = (remainingMillis / 1000) % 60
    val timeString = String.format("%02d : %02d : %02d", hoursLeft, minutesLeft, secondsLeft)

    AnimatedVisibility(
        visible = isScreenPressed,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(250)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PureBlack.copy(alpha = 0.88f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "R E M A I N I N G",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light,
                    color = PureWhite.copy(alpha = 0.75f),
                    letterSpacing = 4.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = timeString,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Light,
                    color = PureWhite,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        ControlPill(
            label = "P A U S E",
            tag = "pause_button",
            onTap = onPause
        )
        if (soundAvailable) {
            ControlPill(
                label = if (soundEnabled) "S O U N D" else "M U T E",
                tag = "sound_button",
                onTap = onSoundToggle
            )
        }
        ControlPill(
            label = "R E S E T",
            tag = "reset_button",
            onTap = onReset
        )
    }
}

/**
 * Pill-shaped tap target for the running screen's PAUSE / RESET controls.
 * The semi-transparent black backdrop gives the labels enough contrast to
 * stay readable when the sand pile or a fully lit LED grid would otherwise
 * blend the white text into a white background.
 */
@Composable
private fun ControlPill(
    label: String,
    tag: String,
    onTap: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTap()
                    }
                )
            }
            .background(
                color = PureBlack.copy(alpha = 0.55f),
                shape = RoundedCornerShape(percent = 50)
            )
            .padding(horizontal = 16.dp, vertical = 9.dp)
            .testTag(tag)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Light,
            color = PureWhite.copy(alpha = 0.95f),
            letterSpacing = 3.sp,
        )
    }
}

@Composable
fun SandRunningScreen(
    remainingMillis: Long,
    totalMillis: Long,
    onPause: () -> Unit = {},
    onReset: () -> Unit
) {
    KeepScreenOn()

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sandboxSettings = LocalSandboxSettings.current
    val drawnLines = remember { mutableStateListOf<DrawnLineSegment>() }

    // Raw device-frame gravity (m/s²), low-pass filtered.
    //   tiltX > 0 → gravity pulls toward the device's right edge
    //   tiltY > 0 → gravity pulls toward the device's bottom edge (portrait normal)
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(9.81f) }
    var tiltZ by remember { mutableFloatStateOf(0f) }

    // Calibration offsets
    var calX by remember { mutableFloatStateOf(0f) }
    var calY by remember { mutableFloatStateOf(0f) }
    var calZ by remember { mutableFloatStateOf(9.81f) }

    var isScreenPressed by remember { mutableStateOf(false) }

    // Load calibration
    LaunchedEffect(Unit) {
        TimerPreferences.observeCalibration(context).collect { c ->
            calX = c.x
            calY = c.y
            calZ = c.z
        }
    }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val alpha = 0.2f
                    // Raw sensor values
                    val rawX = -event.values[0]
                    val rawY = event.values[1]
                    val rawZ = event.values[2]
                    // Apply calibration offset
                    val adjX = rawX - calX
                    val adjY = rawY - calY
                    val adjZ = rawZ - calZ
                    tiltX = tiltX + alpha * (adjX - tiltX)
                    tiltY = tiltY + alpha * (adjY - tiltY)
                    tiltZ = tiltZ + alpha * (adjZ - tiltZ)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (accelerometer != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    val progressFraction = remember(remainingMillis, totalMillis) {
        (remainingMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
    }

    val physics = remember { ParticleSystem() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .pointerInput(Unit) {
                val touchSlop = viewConfiguration.touchSlop
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        isScreenPressed = true
                        var prevPos = down.position
                        var totalDragDistance = 0f
                        var isDragging = false
                        val pointerId = down.id
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            if (change == null || !change.pressed) {
                                isScreenPressed = false
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                break
                            } else {
                                val currPos = change.position
                                val dist = (currPos - prevPos).getDistance()
                                if (dist > 0.1f) {
                                    totalDragDistance += dist
                                    if (totalDragDistance > touchSlop) {
                                        if (isScreenPressed) {
                                            isScreenPressed = false
                                        }
                                        isDragging = true
                                    }
                                    if (isDragging && dist > 2f) {
                                        drawnLines.add(DrawnLineSegment(start = prevPos, end = currPos))
                                        prevPos = currPos
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        val currentProgressFraction by rememberUpdatedState(progressFraction)
        val currentGravityX by rememberUpdatedState(tiltX)
        val currentGravityY by rememberUpdatedState(tiltY)
        var tick by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            physics.reset()
            var frameTime = 0L
            while (true) {
                withFrameNanos { frameTimeNanos ->
                    if (frameTime == 0L) {
                        frameTime = frameTimeNanos
                    }
                    val dt = ((frameTimeNanos - frameTime) / 1_000_000_000f).coerceIn(0f, 0.03f)
                    frameTime = frameTimeNanos

                    // Update line alphas
                    for (i in drawnLines.indices) {
                        val line = drawnLines[i]
                        val nextAlpha = line.alpha - dt * 0.16f
                        drawnLines[i] = line.copy(alpha = nextAlpha)
                    }
                    drawnLines.removeAll { it.alpha <= 0f }

                    physics.update(
                        remainingFraction = currentProgressFraction,
                        gravityX = currentGravityX,
                        gravityY = currentGravityY,
                        dt = dt,
                        totalMillis = totalMillis,
                        sandboxSettings = sandboxSettings,
                        lines = drawnLines
                    )
                    tick++
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                // Read tick state to trigger draw invalidation on every physics update
                val drawTick = tick

                val width = size.width
                val height = size.height

                physics.initDimensions(width, height)

                val particleRadius = 0.85.dp.toPx() * sandboxSettings.particleSize
                val grainRadius = 0.6.dp.toPx() * sandboxSettings.particleSize

                // Draw active drawn obstacles
                for (line in drawnLines) {
                    drawLine(
                        color = PureWhite.copy(alpha = line.alpha * 0.65f),
                        start = line.start,
                        end = line.end,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // 1. Active falling grains
                for (i in 0 until physics.maxParticles) {
                    if (physics.pActive[i]) {
                        drawCircle(
                            color = PureWhite.copy(alpha = physics.pAlpha[i]),
                            radius = particleRadius,
                            center = Offset(physics.px[i], physics.py[i])
                        )
                    }
                }

                // 2. Accumulated pile — drawn as fine columns with a soft grain
                val colWidth = width / physics.numCols
                val noise = Random(physics.noiseSeed)
                for (i in 0 until physics.numCols) {
                    val h = physics.heights[i]
                    if (h > 0.5f) {
                        val x = i * colWidth
                        drawRect(
                            color = SandWhite,
                            topLeft = Offset(x, height - h),
                            size = Size(colWidth + 0.5f, h)
                        )

                        // Surface micro-grain: a single jittered highlight per column
                        val surfaceX = x + noise.nextFloat() * colWidth
                        val surfaceY = height - h - noise.nextFloat() * 4f
                        if (surfaceY in 0f..height) {
                            drawCircle(
                                color = PureWhite.copy(alpha = 0.85f),
                                radius = grainRadius,
                                center = Offset(surfaceX, surfaceY)
                            )
                        }
                    }
                }
            }
        }

        RunningOverlay(
            remainingMillis = remainingMillis,
            isScreenPressed = isScreenPressed,
            onPause = onPause,
            onReset = onReset
        )
    }
}

@Composable
fun LedRunningScreen(
    remainingMillis: Long,
    totalMillis: Long,
    onPause: () -> Unit = {},
    onReset: () -> Unit
) {
    KeepScreenOn()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(9.81f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val alpha = 0.2f
                    // Raw sensor values; only the horizontal tilt direction is needed.
                    val rawX = -event.values[0]
                    val rawY = event.values[1]
                    tiltX = tiltX + alpha * (rawX - tiltX)
                    tiltY = tiltY + alpha * (rawY - tiltY)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (accelerometer != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sensorManager?.unregisterListener(listener) }
    }

    var isScreenPressed by remember { mutableStateOf(false) }

    val elapsedFraction = remember(remainingMillis, totalMillis) {
        if (totalMillis <= 0L) 0f
        else (1f - remainingMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
    }

    var tick by remember { mutableIntStateOf(0) }
    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var frameTime = 0L
        while (true) {
            withFrameNanos { frameTimeNanos ->
                if (frameTime == 0L) {
                    frameTime = frameTimeNanos
                }
                val dt = ((frameTimeNanos - frameTime) / 1_000_000_000f).coerceIn(0f, 0.03f)
                frameTime = frameTimeNanos
                time += dt
                tick++
            }
        }
    }

    val currentTiltX by rememberUpdatedState(tiltX)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isScreenPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        tryAwaitRelease()
                        isScreenPressed = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Trigger recomposition every frame.
                val drawTick = tick

                val width = size.width
                val height = size.height

                val cols = 16
                val rows = 32
                val total = cols * rows

                val cellW = width / cols
                val cellH = height / rows
                val cell = min(cellW, cellH)
                val dotSize = cell * 0.62f
                val corner = CornerRadius(dotSize * 0.2f)

                val offsetX = (width - cellW * cols) / 2f
                val offsetY = (height - cellH * rows) / 2f

                val baseLevel = elapsedFraction * rows
                val tiltFactor = (currentTiltX / 9.81f).coerceIn(-1f, 1f)
                val centerCol = (cols - 1) / 2f

                for (idx in 0 until total) {
                    val rowFromBottom = idx / cols
                    val rowFromTop = rows - 1 - rowFromBottom
                    val col = idx % cols
                    val cx = offsetX + col * cellW + (cellW - dotSize) / 2f
                    val cy = offsetY + rowFromTop * cellH + (cellH - dotSize) / 2f

                    val tiltOffset = tiltFactor * 6f * (col - centerCol) / centerCol
                    val level = baseLevel + tiltOffset
                    val fill = (level - rowFromBottom).coerceIn(0f, 1f)
                    var alpha = 0.06f + fill * 0.89f

                    // Subtle pulse on the liquid boundary cells.
                    if (fill > 0f && fill < 1f) {
                        alpha *= (0.85f + 0.15f * sin(time * 3f))
                    }
                    alpha = alpha.coerceIn(0.02f, 0.95f)

                    drawRoundRect(
                        color = PureWhite.copy(alpha = alpha),
                        topLeft = Offset(cx, cy),
                        size = Size(dotSize, dotSize),
                        cornerRadius = corner
                    )
                }
            }
        }

        RunningOverlay(
            remainingMillis = remainingMillis,
            isScreenPressed = isScreenPressed,
            onPause = onPause,
            onReset = onReset,
            soundAvailable = false
        )
    }
}

private data class WaterBubble(
    var x: Float, // normalized 0..1 horizontal
    var y: Float, // normalized 0..1 vertical inside bottom pool
    val radius: Float,
    val speed: Float,
    val wobbleSpeed: Float,
    val wobbleScale: Float,
    var wobblePhase: Float,
    val alpha: Float
)

private data class WaterSplashParticle(
    var x: Float, // normalized 0..1 horizontal
    var y: Float, // normalized 0..1 vertical
    var vx: Float,
    var vy: Float,
    var alpha: Float
)

@Composable
fun WaterRunningScreen(
    remainingMillis: Long,
    totalMillis: Long,
    onPause: () -> Unit = {},
    onReset: () -> Unit
) {
    KeepScreenOn()

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sandboxSettings = LocalSandboxSettings.current
    val drawnLines = remember { mutableStateListOf<DrawnLineSegment>() }

    // Raw device-frame gravity (m/s²), low-pass filtered.
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(9.81f) }
    var tiltZ by remember { mutableFloatStateOf(0f) }

    // Calibration offsets
    var calX by remember { mutableFloatStateOf(0f) }
    var calY by remember { mutableFloatStateOf(0f) }
    var calZ by remember { mutableFloatStateOf(9.81f) }

    var isScreenPressed by remember { mutableStateOf(false) }

    // Load calibration
    LaunchedEffect(Unit) {
        TimerPreferences.observeCalibration(context).collect { c ->
            calX = c.x
            calY = c.y
            calZ = c.z
        }
    }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val alpha = 0.2f
                    // Raw sensor values
                    val rawX = -event.values[0]
                    val rawY = event.values[1]
                    val rawZ = event.values[2]
                    // Apply calibration offset
                    val adjX = rawX - calX
                    val adjY = rawY - calY
                    val adjZ = rawZ - calZ
                    tiltX = tiltX + alpha * (adjX - tiltX)
                    tiltY = tiltY + alpha * (adjY - tiltY)
                    tiltZ = tiltZ + alpha * (adjZ - tiltZ)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (accelerometer != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    val progressFraction = remember(remainingMillis, totalMillis) {
        (remainingMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
    }

    // Phase animation for waves
    var wavePhase by remember { mutableFloatStateOf(0f) }

    // Spring-damper system to simulate water sloshing
    var sloshAngle by remember { mutableFloatStateOf(0f) }
    var sloshVelocity by remember { mutableFloatStateOf(0f) }

    // Gaseous ambient bubbles
    val maxBubbles = 35
    val bubbles = remember { mutableStateListOf<WaterBubble>() }

    // Splash particles
    val maxSplashes = 50
    val splashes = remember { mutableStateListOf<WaterSplashParticle>() }

    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        var frameTime = 0L
        while (true) {
            withFrameNanos { frameTimeNanos ->
                if (frameTime == 0L) {
                    frameTime = frameTimeNanos
                }
                val dt = ((frameTimeNanos - frameTime) / 1_000_000_000f).coerceIn(0f, 0.03f)
                frameTime = frameTimeNanos

                // Update line alphas
                for (i in drawnLines.indices) {
                    val line = drawnLines[i]
                    val nextAlpha = line.alpha - dt * 0.16f
                    drawnLines[i] = line.copy(alpha = nextAlpha)
                }
                drawnLines.removeAll { it.alpha <= 0f }

                // 1. Update wave phase
                wavePhase = (wavePhase + 3f * dt) % (2f * Math.PI.toFloat())

                // 2. Spring-damper for sloshing
                val targetSlosh = (-tiltX * sandboxSettings.gravityScale / 9.81f).coerceIn(-0.4f, 0.4f)
                val springK = 35f
                val damping = 6f
                val force = (targetSlosh - sloshAngle) * springK
                sloshVelocity += (force - sloshVelocity * damping) * dt
                sloshAngle += sloshVelocity * dt

                // 3. Spawning and updating bubbles
                if (bubbles.size < maxBubbles && Random.nextFloat() < 0.08f) {
                    bubbles.add(
                        WaterBubble(
                            x = Random.nextFloat(), // normalized
                            y = 1.0f,               // bottom of pool
                            radius = 2.dp.value + Random.nextFloat() * 4.dp.value,
                            speed = 40f + Random.nextFloat() * 60f,
                            wobbleSpeed = 4f + Random.nextFloat() * 8f,
                            wobbleScale = 3f + Random.nextFloat() * 6f,
                            wobblePhase = Random.nextFloat() * 2f * Math.PI.toFloat(),
                            alpha = 0.3f + Random.nextFloat() * 0.4f
                        )
                    )
                }

                // 4. Spawning splash particles where the stream hits
                // Only spawn if timer is running (progress > 0)
                if (progressFraction > 0f && Random.nextFloat() < 0.25f) {
                    val count = Random.nextInt(1, 3)
                    val bottomLevelFrac = (1f - progressFraction) * 0.45f
                    val surfaceYFrac = 1f - bottomLevelFrac
                    for (k in 0 until count) {
                        if (splashes.size < maxSplashes) {
                            splashes.add(
                                WaterSplashParticle(
                                    x = 0.5f + (Random.nextFloat() - 0.5f) * 0.04f, // centered
                                    y = surfaceYFrac, // nominal splash hit height
                                    vx = (Random.nextFloat() - 0.5f) * 120f,
                                    vy = -60f - Random.nextFloat() * 120f,
                                    alpha = 0.8f + Random.nextFloat() * 0.2f
                                )
                            )
                        }
                    }
                }

                tick++
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .pointerInput(Unit) {
                val touchSlop = viewConfiguration.touchSlop
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        isScreenPressed = true
                        var prevPos = down.position
                        var totalDragDistance = 0f
                        var isDragging = false
                        val pointerId = down.id
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            if (change == null || !change.pressed) {
                                isScreenPressed = false
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                break
                            } else {
                                val currPos = change.position
                                val dist = (currPos - prevPos).getDistance()
                                if (dist > 0.1f) {
                                    totalDragDistance += dist
                                    if (totalDragDistance > touchSlop) {
                                        if (isScreenPressed) {
                                            isScreenPressed = false
                                        }
                                        isDragging = true
                                    }
                                    if (isDragging && dist > 2f) {
                                        drawnLines.add(DrawnLineSegment(start = prevPos, end = currPos))
                                        prevPos = currPos
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val drawTick = tick // trigger recomposition

                val w = size.width
                val h = size.height

                val pxScale = density

                // Draw active drawn obstacles
                for (line in drawnLines) {
                    drawLine(
                        color = PureWhite.copy(alpha = line.alpha * 0.65f),
                        start = line.start,
                        end = line.end,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Let's define the bottom water level
                // Bottom pool level increases as progress decreases (fills)
                val bottomLevelFrac = (1f - progressFraction) * 0.45f
                val bottomBaseHeight = h * bottomLevelFrac

                // Render 1: Falling water stream in the center (spans from top ceiling h=0 to water surface, with parabolic gravity lean)
                if (progressFraction > 0f) {
                    val streamTopY = 0f
                    val streamBottomY = (h - bottomBaseHeight).coerceAtMost(h)
                    
                    val streamPath = Path().apply {
                        val centerX = w / 2f
                        val segments = 40
                        val dy = (streamBottomY - streamTopY) / segments
                        
                        moveTo(centerX, streamTopY)
                        for (i in 1..segments) {
                            val currY = streamTopY + i * dy
                            val progress = (currY - streamTopY) / (streamBottomY - streamTopY).coerceAtLeast(1f)
                            // Parabolic displacement due to gravity: x_offset = gravity * progress^2
                            val gravityOffset = (tiltX * sandboxSettings.gravityScale * 36f * pxScale) * (progress * progress)
                            val xOffset = sin(currY * 0.06f - wavePhase * 4f) * 3.dp.toPx() + gravityOffset
                            lineTo(centerX + xOffset, currY)
                        }
                    }
                    
                    drawPath(
                        path = streamPath,
                        color = Color(0x3300D2FF),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 6.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                    drawPath(
                        path = streamPath,
                        color = PureWhite.copy(alpha = 0.9f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                }

                // Render 2: Bottom pool (fills the entire screen width!)
                if (bottomBaseHeight > 0f) {
                    val bottomY = h
                    val targetY = h - bottomBaseHeight

                    val wavePathFront = Path().apply {
                        moveTo(0f, bottomY)
                        val segments = 60
                        val dx = w / segments
                        for (i in 0..segments) {
                            val currX = i * dx
                            val t = i.toFloat() / segments
                            val slosh = (t - 0.5f) * w * sloshAngle
                            val wave1 = sin(t * 2 * Math.PI.toFloat() * 1.5f + wavePhase) * 10.dp.toPx()
                            val wave2 = cos(t * 2 * Math.PI.toFloat() * 2.8f - wavePhase * 1.3f) * 5.dp.toPx()
                            val currY = (targetY + slosh + wave1 + wave2).coerceIn(0f, bottomY)
                            lineTo(currX, currY)
                        }
                        lineTo(w, bottomY)
                        close()
                    }

                    val wavePathBack = Path().apply {
                        moveTo(0f, bottomY)
                        val segments = 60
                        val dx = w / segments
                        for (i in 0..segments) {
                            val currX = i * dx
                            val t = i.toFloat() / segments
                            val slosh = (t - 0.5f) * w * sloshAngle
                            val wave1 = sin(t * 2 * Math.PI.toFloat() * 1.8f - wavePhase + 1.2f) * 9.dp.toPx()
                            val wave2 = cos(t * 2 * Math.PI.toFloat() * 2.2f + wavePhase * 0.8f) * 4.dp.toPx()
                            val currY = (targetY - 3.dp.toPx() + slosh + wave1 + wave2).coerceIn(0f, bottomY)
                            lineTo(currX, currY)
                        }
                        lineTo(w, bottomY)
                        close()
                    }

                    drawPath(path = wavePathBack, color = Color(0x44005C97))
                    drawPath(path = wavePathFront, color = Color(0x6600C9FF))

                    // Draw & Update ambient bubbles inside bottom pool
                    val iterator = bubbles.iterator()
                    while (iterator.hasNext()) {
                        val bubble = iterator.next()
                        
                        bubble.y -= bubble.speed * (1f / 60f) * pxScale
                        bubble.wobblePhase += bubble.wobbleSpeed * (1f / 60f)
                        
                        val bubbleX = bubble.x * w + sin(bubble.wobblePhase) * bubble.wobbleScale * pxScale
                        val bubbleY = bottomY - (1f - bubble.y) * bottomBaseHeight
                        
                        val tNormal = (bubbleX / w).coerceIn(0f, 1f)
                        val slosh = (tNormal - 0.5f) * w * sloshAngle
                        val surfaceYAtX = targetY + slosh
                        
                        if (bubbleY < surfaceYAtX || bubbleY > bottomY) {
                            iterator.remove()
                        } else {
                            drawCircle(
                                color = PureWhite.copy(alpha = bubble.alpha),
                                radius = bubble.radius,
                                center = Offset(bubbleX, bubbleY),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                            )
                            drawCircle(
                                color = PureWhite.copy(alpha = bubble.alpha * 0.5f),
                                radius = bubble.radius * 0.4f,
                                center = Offset(bubbleX - bubble.radius * 0.3f, bubbleY - bubble.radius * 0.3f)
                            )
                        }
                    }
                }

                // Render 3: Splash particles
                if (progressFraction > 0f && bottomBaseHeight > 0f) {
                    val bottomY = h
                    val targetY = h - bottomBaseHeight
                    val sloshAtCenter = (0.5f - 0.5f) * w * sloshAngle
                    val hitY = targetY + sloshAtCenter

                    // The hit X position is w/2f + gravityOffset
                    val hitX = w / 2f + (tiltX * sandboxSettings.gravityScale * 36f * pxScale)

                    val iterator = splashes.iterator()
                    while (iterator.hasNext()) {
                        val splash = iterator.next()
                        
                        splash.vy += 240f * sandboxSettings.gravityScale * (1f / 60f) * pxScale
                        splash.x += splash.vx * (1f / 60f) / w
                        splash.y = (splash.y * h + splash.vy * (1f / 60f) * pxScale) / h
                        splash.alpha -= 1.2f * (1f / 60f)

                        var posX = hitX + (splash.x - 0.5f) * w
                        var posY = splash.y * h

                        if (drawnLines.isNotEmpty()) {
                            val updated = resolveLineCollisions(
                                px = posX,
                                py = posY,
                                pvx = splash.vx,
                                pvy = splash.vy,
                                radius = 2.5f * sandboxSettings.particleSize,
                                lines = drawnLines
                            )
                            posX = updated[0]
                            posY = updated[1]
                            splash.vx = updated[2]
                            splash.vy = updated[3]
                            splash.x = 0.5f + (posX - hitX) / w
                        }
                        splash.y = posY / h

                        if (posY >= bottomY || splash.alpha <= 0f) {
                            iterator.remove()
                        } else {
                            drawCircle(
                                color = Color(0xFF88F2FF).copy(alpha = splash.alpha),
                                radius = 1.5.dp.toPx() * sandboxSettings.particleSize,
                                center = Offset(posX, posY)
                            )
                        }
                    }
                }
            }
        }

        RunningOverlay(
            remainingMillis = remainingMillis,
            isScreenPressed = isScreenPressed,
            onPause = onPause,
            onReset = onReset
        )
    }
}

@Composable
fun PausedScreen(
    remainingMillis: Long,
    totalMillis: Long,
    onResume: () -> Unit,
    onReset: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val hoursLeft = (remainingMillis / 3600000)
    val minutesLeft = (remainingMillis / 60000) % 60
    val secondsLeft = (remainingMillis / 1000) % 60
    val timeString = String.format("%02d : %02d : %02d", hoursLeft, minutesLeft, secondsLeft)

    val totalSecs = totalMillis / 1000
    val remainingSecs = remainingMillis / 1000
    val percentLeft = if (totalSecs > 0) {
        ((remainingSecs.toFloat() / totalSecs.toFloat()) * 100).toInt()
    } else {
        0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "P A U S E D",
            fontSize = 12.sp,
            fontWeight = FontWeight.Light,
            color = PureWhite.copy(alpha = 0.75f),
            letterSpacing = 4.sp,
            modifier = Modifier.padding(top = 32.dp)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = timeString,
                fontSize = 50.sp,
                fontWeight = FontWeight.Light,
                color = PureWhite,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "$percentLeft %  R E M A I N I N G",
                fontSize = 11.sp,
                fontWeight = FontWeight.Light,
                color = PureWhite.copy(alpha = 0.7f),
                letterSpacing = 3.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onReset()
                            }
                        )
                    }
                    .testTag("paused_reset_button")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "R E S E T",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Light,
                        color = PureWhite.copy(alpha = 0.85f),
                        letterSpacing = 3.sp,
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp)
                    )
                    Spacer(
                        modifier = Modifier
                            .width(36.dp)
                            .height(1.dp)
                            .background(PureWhite.copy(alpha = 0.55f))
                    )
                }
            }

            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onResume()
                            }
                        )
                    }
                    .testTag("resume_button")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "R E S U M E",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Light,
                        color = PureWhite,
                        letterSpacing = 4.sp,
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp)
                    )
                    Spacer(
                        modifier = Modifier
                            .width(52.dp)
                            .height(1.dp)
                            .background(PureWhite.copy(alpha = 0.85f))
                    )
                }
            }
        }
    }
}

@Composable
fun FinishedScreen(
    onReset: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 100, 150, 100, 150, 400)
                    val amplitudes = intArrayOf(0, 180, 0, 180, 0, 255)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(600)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 220)
            kotlinx.coroutines.delay(320)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 220)
            kotlinx.coroutines.delay(320)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 420)
            kotlinx.coroutines.delay(600)
            tone.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    var pulseState by remember { mutableStateOf(false) }
    val pulseAlpha by animateFloatAsState(
        targetValue = if (pulseState) 0.6f else 0.1f,
        animationSpec = tween(durationMillis = 1500),
        label = "Pulse animation"
    )

    LaunchedEffect(Unit) {
        while (true) {
            pulseState = !pulseState
            kotlinx.coroutines.delay(1500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite.copy(alpha = pulseAlpha))
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "E N D",
            fontSize = 72.sp,
            fontWeight = FontWeight.Light,
            color = PureWhite,
            letterSpacing = 8.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Text(
            text = "T I M E  I S  F U L F I L L E D",
            fontSize = 12.sp,
            fontWeight = FontWeight.Light,
            color = PureWhite.copy(alpha = 0.8f),
            letterSpacing = 4.sp,
            modifier = Modifier.padding(bottom = 64.dp)
        )

        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onReset()
                        }
                    )
                }
                .testTag("setup_again_button")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "A G A I N",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light,
                    color = PureWhite,
                    letterSpacing = 3.sp,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp)
                )
                Spacer(
                    modifier = Modifier
                        .width(40.dp)
                        .height(1.dp)
                        .background(PureWhite.copy(alpha = 0.8f))
                )
            }
        }
    }
}

@Composable
fun CalibrationScreen(
    onDismiss: () -> Unit,
    onCalibrated: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Live sensor readings
    var rawX by remember { mutableFloatStateOf(0f) }
    var rawY by remember { mutableFloatStateOf(0f) }
    var rawZ by remember { mutableFloatStateOf(9.81f) }

    // Calibration state
    var calibrationX by remember { mutableFloatStateOf(0f) }
    var calibrationY by remember { mutableFloatStateOf(0f) }
    var calibrationZ by remember { mutableFloatStateOf(9.81f) }
    var isCalibrating by remember { mutableStateOf(false) }
    var calibrationProgress by remember { mutableIntStateOf(0) }
    var calibrationMessage by remember { mutableStateOf("") }

    // Load existing calibration
    LaunchedEffect(Unit) {
        TimerPreferences.observeCalibration(context).collect { cal ->
            calibrationX = cal.x
            calibrationY = cal.y
            calibrationZ = cal.z
        }
    }

    // Sensor listener for live preview
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val alpha = 0.1f
                    rawX = rawX + alpha * (-event.values[0] - rawX)
                    rawY = rawY + alpha * (event.values[1] - rawY)
                    rawZ = rawZ + alpha * (event.values[2] - rawZ)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (accelerometer != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sensorManager?.unregisterListener(listener) }
    }

    // Calculate calibrated values for display
    val calX = rawX - calibrationX
    val calY = rawY - calibrationY
    val calZ = rawZ - calibrationZ
    val magnitude = sqrt(calX * calX + calY * calY + calZ * calZ)

    // Calculate tilt angles for visualization
    val tiltAngleX = atan2(calX, sqrt(calY * calY + calZ * calZ)) * 180 / Math.PI.toFloat()
    val tiltAngleY = atan2(calY, sqrt(calX * calX + calZ * calZ)) * 180 / Math.PI.toFloat()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Title
            Text(
                text = "C A L I B R A T E",
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                color = PureWhite.copy(alpha = 0.75f),
                letterSpacing = 3.sp,
                modifier = Modifier.padding(top = 16.dp)
            )

            // Live sensor values
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "R A W",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Light,
                    color = PureWhite.copy(alpha = 0.45f),
                    letterSpacing = 2.sp
                )
                Text(
                    text = String.format("X: %+.3f\nY: %+.3f\nZ: %+.3f", rawX, rawY, rawZ),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    color = PureWhite.copy(alpha = 0.85f),
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "C A L I B R A T E D",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Light,
                    color = PureWhite.copy(alpha = 0.45f),
                    letterSpacing = 2.sp
                )
                Text(
                    text = String.format("X: %+.3f\nY: %+.3f\nZ: %+.3f", calX, calY, calZ),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    color = PureWhite.copy(alpha = 0.85f),
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Magnitude indicator
                Text(
                    text = "|G| = ${String.format("%.3f", magnitude)} m/s²",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Light,
                    color = if (abs(magnitude - 9.81f) < 0.2f) PureWhite.copy(alpha = 0.9f) else PureWhite.copy(alpha = 0.6f),
                    letterSpacing = 1.sp
                )
                Text(
                    text = String.format("Tilt: X %.1f°  Y %.1f°", tiltAngleX, tiltAngleY),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Light,
                    color = PureWhite.copy(alpha = 0.6f),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Calibration button / progress
            if (!isCalibrating) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isCalibrating = true
                                    calibrationProgress = 0
                                    calibrationMessage = "H O L D  S T I L L . . ."
                                }
                            )
                        }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "S E T  C U R R E N T  A S  L E V E L",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Light,
                            color = PureWhite,
                            letterSpacing = 2.sp,
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp)
                        )
                        Spacer(
                            modifier = Modifier
                                .width(120.dp)
                                .height(1.dp)
                                .background(PureWhite.copy(alpha = 0.85f))
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = calibrationMessage,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Light,
                        color = PureWhite.copy(alpha = 0.9f),
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Progress bar
                    Box(
                        modifier = Modifier
                            .width(200.dp)
                            .height(2.dp)
                            .background(PureWhite.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .width((200f * calibrationProgress / 60).dp)
                                .height(2.dp)
                                .background(PureWhite.copy(alpha = 0.9f))
                        )
                    }

                    Text(
                        text = "$calibrationProgress / 60",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Light,
                        color = PureWhite.copy(alpha = 0.6f),
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Dismiss button
            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDismiss()
                            }
                        )
                    }
                    .padding(top = 8.dp, bottom = 24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "D O N E",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Light,
                        color = PureWhite.copy(alpha = 0.7f),
                        letterSpacing = 3.sp,
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp)
                    )
                    Spacer(
                        modifier = Modifier
                            .width(40.dp)
                            .height(1.dp)
                            .background(PureWhite.copy(alpha = 0.4f))
                    )
                }
            }
        }
    }

    // Calibration logic
    LaunchedEffect(isCalibrating, calibrationProgress) {
        if (!isCalibrating) return@LaunchedEffect

        val samplesX = mutableListOf<Float>()
        val samplesY = mutableListOf<Float>()
        val samplesZ = mutableListOf<Float>()

        while (calibrationProgress < 60 && isCalibrating) {
            kotlinx.coroutines.delay(50)
            samplesX.add(rawX)
            samplesY.add(rawY)
            samplesZ.add(rawZ)
            calibrationProgress++
        }

        if (calibrationProgress >= 60 && isCalibrating) {
            val avgX = samplesX.average().toFloat()
            val avgY = samplesY.average().toFloat()
            val avgZ = samplesZ.average().toFloat()
            val mag = sqrt(avgX * avgX + avgY * avgY + avgZ * avgZ)

            // Scale Z to 9.81 (gravity magnitude)
            val scale = if (mag > 0.1f) 9.81f / mag else 1f

            val newCal = CalibrationData(
                x = avgX,
                y = avgY,
                z = avgZ * scale
            )

            scope.launch {
                TimerPreferences.saveCalibration(context, newCal)
            }

            calibrationX = newCal.x
            calibrationY = newCal.y
            calibrationZ = newCal.z
            calibrationMessage = "D O N E !"
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

            kotlinx.coroutines.delay(800)
            isCalibrating = false
            onCalibrated()
        }
    }
}

// ============================================================
// FIRE (불멍) — blazes bright at start, dies down over time
// ============================================================
@Composable
fun FireRunningScreen(
    remainingMillis: Long, totalMillis: Long,
    onPause: () -> Unit = {}, onReset: () -> Unit
) {
    KeepScreenOn()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sandboxSettings = LocalSandboxSettings.current

    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(9.81f) }
    var calX by remember { mutableFloatStateOf(0f) }
    var calY by remember { mutableFloatStateOf(0f) }
    var calZ by remember { mutableFloatStateOf(9.81f) }

    LaunchedEffect(Unit) {
        TimerPreferences.observeCalibration(context).collect { c ->
            calX = c.x; calY = c.y; calZ = c.z
        }
    }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val acc = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent?) {
                if (e == null) return
                val alpha = 0.2f
                val ax = (-e.values[0] - calX); val ay = (e.values[1] - calY)
                tiltX = tiltX + alpha * (ax - tiltX)
                tiltY = tiltY + alpha * (ay - tiltY)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (acc != null) sm?.registerListener(listener, acc, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(listener) }
    }

    val progress = remember(remainingMillis, totalMillis) {
        1f - (remainingMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
    }
    val currentProgress by rememberUpdatedState(progress)
    var isPressed by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    data class FireParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        val maxLife: Float,
        val size: Float,
        val isSpark: Boolean
    )
    val particles = remember { mutableStateListOf<FireParticle>() }

    LaunchedEffect(Unit) {
        var ft = 0L
        while (true) {
            withFrameNanos { n ->
                if (ft == 0L) ft = n
                val dt = ((n - ft) / 1_000_000_000f).coerceIn(0f, 0.03f)
                ft = n

                val prog = currentProgress
                val intensity = (1f - prog * 0.95f).coerceAtLeast(0.05f)
                val gScale = sandboxSettings.gravityScale.coerceIn(0.3f, 2.5f)

                // 1. Spawning Flames (only if progress < 80%)
                val flameIntensity = if (prog < 0.8f) (1f - (prog / 0.8f)).coerceIn(0f, 1f) else 0f
                val spawnRate = 22f * flameIntensity
                val count = (spawnRate * dt * 60f).toInt().coerceAtLeast(if (flameIntensity > 0.1f) 1 else 0)

                for (i in 0 until count) {
                    val spread = 0.05f + (1f - flameIntensity) * 0.05f
                    val speed = -(0.25f + flameIntensity * 0.5f + Random.nextFloat() * 0.25f) * gScale
                    val size = 1f + Random.nextFloat() * 2.5f * flameIntensity
                    particles.add(FireParticle(
                        x = 0.5f + (Random.nextFloat() - 0.5f) * spread,
                        y = 0.90f + Random.nextFloat() * 0.02f,
                        vx = (Random.nextFloat() - 0.5f) * 0.08f * (1f + flameIntensity),
                        vy = speed,
                        life = 0f,
                        maxLife = (0.8f + Random.nextFloat() * 1.5f) * (0.4f + flameIntensity * 0.6f),
                        size = size,
                        isSpark = false
                    ))
                }

                // 2. Spawning Sparks (crackling embers)
                val sparkSpawnRate = if (prog < 0.8f) 1.5f else 4f * (1f - (prog - 0.8f) / 0.2f).coerceIn(0f, 1f)
                val sparkCount = (sparkSpawnRate * dt * 60f).toInt().coerceAtLeast(
                    if (Random.nextFloat() < (sparkSpawnRate * dt * 60f % 1f)) 1 else 0
                )

                for (i in 0 until sparkCount) {
                    val spread = 0.35f
                    val speed = -(0.4f + Random.nextFloat() * 0.5f) * gScale
                    val size = 0.6f + Random.nextFloat() * 1.2f
                    particles.add(FireParticle(
                        x = 0.5f + (Random.nextFloat() - 0.5f) * spread,
                        y = 0.90f + Random.nextFloat() * 0.02f,
                        vx = (Random.nextFloat() - 0.5f) * 0.12f,
                        vy = speed,
                        life = 0f,
                        maxLife = 0.5f + Random.nextFloat() * 0.8f,
                        size = size,
                        isSpark = true
                    ))
                }

                // 3. Update Particles
                val tiltBias = tiltX * 0.005f * gScale
                val iter = particles.iterator()
                while (iter.hasNext()) {
                    val p = iter.next()
                    p.x += p.vx * dt + tiltBias * dt
                    p.y += p.vy * dt
                    if (p.isSpark) {
                        p.vx += (Random.nextFloat() - 0.5f) * 0.3f * dt * gScale
                        p.vy += 0.08f * dt * gScale
                    } else {
                        p.vy -= 0.2f * dt * (1f - prog * 0.8f) * gScale
                        p.vx += (Random.nextFloat() - 0.5f) * 0.15f * dt * gScale
                    }
                    p.life += dt
                    if (p.life >= p.maxLife || p.y < -0.05f || p.x < -0.05f || p.x > 1.05f) {
                        iter.remove()
                    }
                }

                val maxParticles = if (prog < 0.8f) {
                    (350 * intensity).toInt().coerceAtLeast(30)
                } else {
                    20
                }
                while (particles.size > maxParticles) {
                    particles.removeAt(0)
                }
                tick++
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(PureBlack)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    isPressed = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    tryAwaitRelease(); isPressed = false; haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                })
            }
    ) {
        Box(modifier = Modifier.fillMaxSize().align(Alignment.Center)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                val prog = progress
                val intensity = (1f - prog * 0.95f).coerceAtLeast(0.05f)

                // 1. Warm ambient glow base - shrinks as fire dies
                if (prog < 0.98f) {
                    val glowRadius = w * 0.3f * intensity
                    val glowAlpha = (intensity * 0.15f).coerceIn(0f, 0.15f)
                    val warmGlowColor = Color(0xFFFF5722).copy(alpha = glowAlpha)
                    drawCircle(
                        color = warmGlowColor,
                        radius = glowRadius,
                        center = Offset(w / 2f, h * 0.91f)
                    )
                    drawCircle(
                        color = Color(0xFFFFB300).copy(alpha = glowAlpha * 0.6f),
                        radius = glowRadius * 0.5f,
                        center = Offset(w / 2f, h * 0.91f)
                    )
                }

                // 2. Draw Campfire Logs
                val logY = h * 0.91f
                val logWidth = 14.dp.toPx()
                val logLength = w * 0.35f

                // Log 1: Left-down to Right-up
                drawLine(
                    color = Color(0xFF211510),
                    start = Offset(w / 2f - logLength / 2f, logY + 8.dp.toPx()),
                    end = Offset(w / 2f + logLength / 2f, logY - 8.dp.toPx()),
                    strokeWidth = logWidth,
                    cap = StrokeCap.Round
                )
                // Log 2: Left-up to Right-down
                drawLine(
                    color = Color(0xFF211510),
                    start = Offset(w / 2f - logLength / 2f, logY - 8.dp.toPx()),
                    end = Offset(w / 2f + logLength / 2f, logY + 8.dp.toPx()),
                    strokeWidth = logWidth,
                    cap = StrokeCap.Round
                )

                // 3. Draw Glowing Charcoal Core inside logs
                if (prog < 0.99f) {
                    val pulse1 = sin(tick * 0.05f) * 0.15f + 0.85f
                    val pulse2 = cos(tick * 0.07f) * 0.15f + 0.85f

                    val emberGlowAlpha = if (prog < 0.8f) {
                        (0.3f + prog * 0.5f)
                    } else {
                        (0.8f * (1f - (prog - 0.8f) / 0.2f)).coerceIn(0f, 1f)
                    }

                    val glowColor1 = Color(0xFFE65100).copy(alpha = emberGlowAlpha * pulse1)
                    val glowColor2 = Color(0xFFFF3D00).copy(alpha = emberGlowAlpha * pulse2)
                    val coreColor = Color(0xFFFFD54F).copy(alpha = emberGlowAlpha * ((pulse1 + pulse2) / 2f))

                    drawLine(
                        color = glowColor1,
                        start = Offset(w / 2f - logLength * 0.35f, logY + 5.dp.toPx()),
                        end = Offset(w / 2f + logLength * 0.35f, logY - 5.dp.toPx()),
                        strokeWidth = logWidth * 0.35f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = glowColor2,
                        start = Offset(w / 2f - logLength * 0.35f, logY - 5.dp.toPx()),
                        end = Offset(w / 2f + logLength * 0.35f, logY + 5.dp.toPx()),
                        strokeWidth = logWidth * 0.35f,
                        cap = StrokeCap.Round
                    )

                    drawCircle(
                        color = coreColor,
                        radius = 10.dp.toPx() * (1f - prog * 0.3f),
                        center = Offset(w / 2f, logY)
                    )
                }

                // 4. Draw Particles (flames and sparks)
                for (p in particles) {
                    val lifeRatio = p.life / p.maxLife
                    val radius = p.size.dp.toPx()

                    if (p.isSpark) {
                        val sparkAlpha = (1f - lifeRatio).coerceIn(0f, 1f)
                        val color = Color(0xFFFFF9C4).copy(alpha = sparkAlpha)
                        drawCircle(
                            color = color,
                            radius = radius,
                            center = Offset(p.x * w, p.y * h)
                        )
                    } else {
                        val alpha = (1f - lifeRatio).coerceIn(0f, 0.9f) * (if (prog < 0.8f) 1f else 0f)
                        val sizeMultiplier = 1f + lifeRatio * 1.5f
                        val color = when {
                            lifeRatio < 0.15f -> Color(0xFFFFFFFF).copy(alpha = alpha)
                            lifeRatio < 0.35f -> Color(0xFFFFEB3B).copy(alpha = alpha)
                            lifeRatio < 0.60f -> Color(0xFFFF9800).copy(alpha = alpha * 0.9f)
                            lifeRatio < 0.80f -> Color(0xFFE65100).copy(alpha = alpha * 0.7f)
                            else -> Color(0xFF3E2723).copy(alpha = alpha * 0.3f)
                        }
                        drawCircle(
                            color = color,
                            radius = radius * sizeMultiplier,
                            center = Offset(p.x * w, p.y * h)
                        )
                    }
                }
            }
        }
        RunningOverlay(remainingMillis, isPressed, onPause, onReset)
    }
}

// Particle system simulating sand falling under gravity and piling up.
// Particles are spawned near the top center; gravityX bends their path so
// tilting the phone makes the stream lean to one side. The pile uses a
// per-column height array and slumps under a slope threshold biased by
// gravityX so the heap immediately reacts when the device is tilted.
class ParticleSystem(val maxParticles: Int = 4800) {
    val px = FloatArray(maxParticles)
    val py = FloatArray(maxParticles)
    val pvx = FloatArray(maxParticles)
    val pvy = FloatArray(maxParticles)
    val pActive = BooleanArray(maxParticles)
    val pAlpha = FloatArray(maxParticles)

    val numCols = 180
    val heights = FloatArray(numCols)

    // Re-seeded on dimension change so the same surface grain pattern
    // doesn't lock in for an entire session.
    var noiseSeed: Int = 55
        private set

    private var w = 0f
    private var h = 0f
    private var spawnAccumulator = 0f

    fun initDimensions(width: Float, height: Float) {
        if (w == width && h == height) return
        val oldH = h
        w = width
        h = height
        noiseSeed = (width.toInt() * 31 + height.toInt())
        // When the device rotates (portrait ↔ landscape) the logical
        // canvas swaps its short and long edges, so the pile height
        // measured in pixels needs to be rescaled to preserve the fill
        // RATIO (heights[i] / h). Without this, a pile that was at 60%
        // would suddenly look near-empty or overflow after a rotation.
        if (oldH > 1f && height > 1f && abs(oldH - height) > 1f) {
            val scale = height / oldH
            for (i in 0 until numCols) {
                heights[i] = (heights[i] * scale).coerceIn(0f, h)
            }
        }
        // Active particle positions are stored in the old frame's pixels,
        // so re-spawn them rather than letting them teleport.
        for (i in 0 until maxParticles) pActive[i] = false
    }

    fun reset() {
        for (i in 0 until maxParticles) {
            pActive[i] = false
        }
        for (i in 0 until numCols) {
            heights[i] = 0f
        }
        spawnAccumulator = 0f
    }

    fun update(
        remainingFraction: Float,
        gravityX: Float,
        gravityY: Float,
        dt: Float,
        totalMillis: Long,
        sandboxSettings: SandboxSettings = SandboxSettings(),
        lines: List<DrawnLineSegment> = emptyList()
    ) {
        if (w == 0f || h == 0f) return

        val safeGravityX = if (gravityX.isNaN()) 0f else gravityX
        val safeGravityY = if (gravityY.isNaN()) 9.81f else gravityY

        val gMult = 200f
        val activeGravityY = safeGravityY * gMult * sandboxSettings.gravityScale
        val activeGravityX = safeGravityX * gMult * sandboxSettings.gravityScale

        // Sized so the column-averaged pile reaches the full screen height
        // exactly when remainingFraction → 0. baseRate ≈ 640 grains/sec.
        val rawIncrement = (h * numCols * 1000f) / (640f * totalMillis)
        val sandIncrement = rawIncrement.coerceIn(0.02f, 60.0f)

        // 1. Spawn falling grains near top-center, with mild jitter and a
        // slight bias toward the gravity direction so the stream visibly leans.
        if (remainingFraction > 0f) {
            val baseRate = 640f
            val spawnRate = baseRate * (1.5f - remainingFraction) * sandboxSettings.particleCount
            spawnAccumulator += spawnRate * dt

            val toSpawn = spawnAccumulator.toInt()
            if (toSpawn > 0) {
                spawnAccumulator -= toSpawn
                var spawned = 0
                val centerX = w / 2f
                val jitter = (w * 0.012f).coerceAtLeast(2f)
                val tiltSpawnBias = safeGravityX * 4f
                for (i in 0 until maxParticles) {
                    if (!pActive[i]) {
                        pActive[i] = true
                        px[i] = centerX + tiltSpawnBias + (Random.nextFloat() - 0.5f) * 2f * jitter
                        py[i] = -2f - Random.nextFloat() * 6f
                        pvx[i] = (Random.nextFloat() - 0.5f) * 20f
                        pvy[i] = 320f + Random.nextFloat() * 60f
                        pAlpha[i] = 0.78f + Random.nextFloat() * 0.22f
                        spawned++
                        if (spawned >= toSpawn) break
                    }
                }
            }
        }

        // 2. Integrate motion, then deposit on the pile surface.
        for (i in 0 until maxParticles) {
            if (!pActive[i]) continue

            pvx[i] += activeGravityX * dt
            pvy[i] += activeGravityY * dt
            // Light air damping
            pvx[i] *= 0.995f
            pvy[i] *= 0.992f

            px[i] += pvx[i] * dt
            py[i] += pvy[i] * dt

            // Collision with drawn barriers
            if (lines.isNotEmpty()) {
                val pRadius = 2.5f * sandboxSettings.particleSize
                val updated = resolveLineCollisions(
                    px = px[i],
                    py = py[i],
                    pvx = pvx[i],
                    pvy = pvy[i],
                    radius = pRadius,
                    lines = lines
                )
                px[i] = updated[0]
                py[i] = updated[1]
                pvx[i] = updated[2]
                pvy[i] = updated[3]
            }

            // Particles that fly off the side just despawn
            if (px[i] < -8f || px[i] > w + 8f) {
                pActive[i] = false
                continue
            }

            val col = ((px[i] / w) * numCols).toInt().coerceIn(0, numCols - 1)
            val pileSurfaceY = h - heights[col]

            if (py[i] >= pileSurfaceY || py[i] >= h - 1f) {
                // Slide along the slope a few steps toward the lower neighbour.
                var currentCol = col
                for (step in 0..9) {
                    val leftCol = currentCol - 1
                    val rightCol = currentCol + 1
                    val leftHeight = if (leftCol >= 0) heights[leftCol] else Float.MAX_VALUE
                    val rightHeight = if (rightCol < numCols) heights[rightCol] else Float.MAX_VALUE
                    val currentHeight = heights[currentCol]

                    val tiltBias = safeGravityX * 2.2f
                    val scoreLeft = (currentHeight - leftHeight) + tiltBias
                    val scoreRight = (currentHeight - rightHeight) - tiltBias

                    if (scoreLeft > scoreRight && scoreLeft > 0.6f && leftCol >= 0) {
                        currentCol = leftCol
                    } else if (scoreRight > scoreLeft && scoreRight > 0.6f && rightCol < numCols) {
                        currentCol = rightCol
                    } else {
                        break
                    }
                }

                pActive[i] = false
                heights[currentCol] = (heights[currentCol] + sandIncrement).coerceIn(0f, h)
            }
        }

        // 3. Slumping pass — heap immediately reacts to tilt.
        val biasX = safeGravityX * 0.55f
        val passes = if (Math.abs(safeGravityX) > 2.0f) 6 else 4
        for (pass in 0 until passes) {
            for (idx in 1 until numCols - 1) {
                // Flow to the left
                val leftThreshold = (1.0f + biasX).coerceAtLeast(0.05f)
                val leftDiff = heights[idx] - heights[idx - 1]
                if (leftDiff > leftThreshold) {
                    val slide = (leftDiff - leftThreshold) * 0.45f
                    if (slide > 0f) {
                        val actual = min(slide, heights[idx])
                        heights[idx] = (heights[idx] - actual).coerceAtLeast(0f)
                        heights[idx - 1] = (heights[idx - 1] + actual).coerceIn(0f, h)
                    }
                }

                // Flow to the right
                val rightThreshold = (1.0f - biasX).coerceAtLeast(0.05f)
                val rightDiff = heights[idx] - heights[idx + 1]
                if (rightDiff > rightThreshold) {
                    val slide = (rightDiff - rightThreshold) * 0.45f
                    if (slide > 0f) {
                        val actual = min(slide, heights[idx])
                        heights[idx] = (heights[idx] - actual).coerceAtLeast(0f)
                        heights[idx + 1] = (heights[idx + 1] + actual).coerceIn(0f, h)
                    }
                }
            }

            // Aggressive lateral pour when strongly tilted
            if (biasX < -0.6f) {
                for (idx in 1 until numCols) {
                    val diff = heights[idx] - heights[idx - 1]
                    if (diff > 0.05f) {
                        val slide = diff * 0.25f
                        heights[idx] = (heights[idx] - slide).coerceAtLeast(0f)
                        heights[idx - 1] = (heights[idx - 1] + slide).coerceIn(0f, h)
                    }
                }
            } else if (biasX > 0.6f) {
                for (idx in numCols - 2 downTo 0) {
                    val diff = heights[idx] - heights[idx + 1]
                    if (diff > 0.05f) {
                        val slide = diff * 0.25f
                        heights[idx] = (heights[idx] - slide).coerceAtLeast(0f)
                        heights[idx + 1] = (heights[idx + 1] + slide).coerceIn(0f, h)
                    }
                }
            }
        }

        // 4. Baseline catch-up — guarantees the pile keeps growing to fill
        // the entire screen by the time the timer ends, even if particle
        // deposition lags behind. The lift is distributed evenly so the
        // surface stays smooth rather than spiking.
        // Why: per-particle deposition alone can plateau when columns hit
        // the slope threshold faster than they can flatten, leaving the
        // pile capped well below the target. This term sanitises NaN
        // values as a side effect.
        var sum = 0f
        for (i in 0 until numCols) {
            val hVal = heights[i]
            if (hVal.isNaN()) {
                heights[i] = 0f
            } else if (hVal > 0f) {
                sum += hVal
            }
        }

        val targetAvg = (1f - remainingFraction) * h
        val currentAvg = sum / numCols
        val gap = targetAvg - currentAvg
        if (gap > 0f && remainingFraction < 0.999f) {
            // Catch-up speed: ~3% of the deficit per frame, capped so
            // the lift never creates a visible jolt.
            val lift = (gap * 0.03f).coerceAtMost(h * 0.004f)
            for (i in 0 until numCols) {
                heights[i] = (heights[i] + lift).coerceIn(0f, h)
            }
        }
    }
}

// ============================================================
// SANDBOX SUPPORT & COLLISION SOLVER
// ============================================================

data class DrawnLineSegment(
    val start: Offset,
    val end: Offset,
    var alpha: Float = 1.0f
)

fun resolveLineCollisions(
    px: Float, py: Float,
    pvx: Float, pvy: Float,
    radius: Float,
    lines: List<DrawnLineSegment>,
    elasticity: Float = 0.35f
): FloatArray {
    var npx = px
    var npy = py
    var npvx = pvx
    var npvy = pvy

    for (line in lines) {
        val x1 = line.start.x
        val y1 = line.start.y
        val x2 = line.end.x
        val y2 = line.end.y

        val abX = x2 - x1
        val abY = y2 - y1
        val apX = npx - x1
        val apY = npy - y1
        val abLenSq = abX * abX + abY * abY
        if (abLenSq > 0.0001f) {
            val t = (apX * abX + apY * abY) / abLenSq
            val clampedT = t.coerceIn(0f, 1f)
            val closestX = x1 + clampedT * abX
            val closestY = y1 + clampedT * abY
            val dx = npx - closestX
            val dy = npy - closestY
            val distSq = dx * dx + dy * dy
            if (distSq < radius * radius && distSq > 0.0001f) {
                val dist = sqrt(distSq)
                val nx = dx / dist
                val ny = dy / dist

                npx = closestX + nx * radius
                npy = closestY + ny * radius

                val dot = npvx * nx + npvy * ny
                if (dot < 0f) {
                    npvx = (npvx - 2f * dot * nx) * elasticity
                    npvy = (npvy - 2f * dot * ny) * elasticity
                }
            }
        }
    }
    return floatArrayOf(npx, npy, npvx, npvy)
}

@Composable
fun SandboxSettingsDialog(
    settings: SandboxSettings,
    onSettingsChange: (SandboxSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack.copy(alpha = 0.85f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = PureBlack,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {})
                }
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "S A N D B O X   P H Y S I C S",
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                color = PureWhite.copy(alpha = 0.85f),
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            SandboxSliderItem(
                label = "G R A V I T Y   S E N S I T I V I T Y",
                value = settings.gravityScale,
                valueRange = 0.3f..2.5f,
                onValueChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSettingsChange(settings.copy(gravityScale = it))
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SandboxSliderItem(
                label = "P A R T I C L E   S I Z E",
                value = settings.particleSize,
                valueRange = 0.6f..2.2f,
                onValueChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSettingsChange(settings.copy(particleSize = it))
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SandboxSliderItem(
                label = "P A R T I C L E   D E N S I T Y",
                value = settings.particleCount,
                valueRange = 0.5f..2.0f,
                onValueChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSettingsChange(settings.copy(particleCount = it))
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "D O N E",
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                color = PureWhite,
                letterSpacing = 3.sp,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismiss()
                        })
                    }
                    .padding(vertical = 8.dp, horizontal = 24.dp)
            )
        }
    }
}

@Composable
fun SandboxSliderItem(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Light,
                color = PureWhite.copy(alpha = 0.55f),
                letterSpacing = 1.sp
            )
            Text(
                text = String.format("%.1fx", value),
                fontSize = 10.sp,
                fontWeight = FontWeight.Light,
                color = PureWhite
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = PureWhite,
                activeTrackColor = PureWhite.copy(alpha = 0.8f),
                inactiveTrackColor = PureWhite.copy(alpha = 0.2f)
            )
        )
    }
}

