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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()

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
                }
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
    onStart: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

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
        // App Title
        Text(
            text = "H O U R G L A S S",
            fontSize = 12.sp,
            fontWeight = FontWeight.Light,
            color = PureWhite.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            letterSpacing = 3.sp,
            modifier = Modifier.padding(top = 24.dp)
        )

        // Mode toggle (SAND / LED)
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .testTag("mode_row"),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeTab(
                label = "S A N D",
                selected = mode == DisplayMode.SAND,
                onClick = { onModeChange(DisplayMode.SAND) },
                tag = "mode_sand"
            )
            ModeTab(
                label = "L E D",
                selected = mode == DisplayMode.LED,
                onClick = { onModeChange(DisplayMode.LED) },
                tag = "mode_led"
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
    }
}

@Composable
private fun RunningOverlay(
    remainingMillis: Long,
    isScreenPressed: Boolean,
    onPause: () -> Unit,
    onReset: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

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
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPause()
                        }
                    )
                }
                .testTag("pause_button")
        ) {
            Text(
                text = "P A U S E",
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                color = PureWhite.copy(alpha = 0.65f),
                letterSpacing = 3.sp,
                modifier = Modifier.padding(12.dp)
            )
        }

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
                .testTag("reset_button")
        ) {
            Text(
                text = "R E S E T",
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                color = PureWhite.copy(alpha = 0.65f),
                letterSpacing = 3.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
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

    // Tilt (Gravity) variables — gravityX positive when phone tilts right,
    // gravityY positive when bottom of phone points down (normal).
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(9.81f) }

    var isScreenPressed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    // Sensor X is positive when right side of phone goes up;
                    // we want positive gravityX to mean "gravity pulls toward
                    // the right of the screen", which happens when right side
                    // goes down → negate the raw value.
                    val alpha = 0.2f
                    tiltX = tiltX + alpha * (-event.values[0] - tiltX)
                    tiltY = tiltY + alpha * (event.values[1] - tiltY)
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

    Box(
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
        val currentProgressFraction by rememberUpdatedState(progressFraction)
        val currentTiltX by rememberUpdatedState(tiltX)
        val currentTiltY by rememberUpdatedState(tiltY)
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

                    physics.update(
                        remainingFraction = currentProgressFraction,
                        gravityX = currentTiltX,
                        gravityY = currentTiltY,
                        dt = dt,
                        totalMillis = totalMillis
                    )
                    tick++
                }
            }
        }

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            // Read tick state to trigger draw invalidation on every physics update
            val drawTick = tick

            val width = size.width
            val height = size.height

            physics.initDimensions(width, height)

            val particleRadius = 1.4.dp.toPx()
            val grainRadius = 1.0.dp.toPx()

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
    val haptic = LocalHapticFeedback.current

    var isScreenPressed by remember { mutableStateOf(false) }

    val elapsedFraction = remember(remainingMillis, totalMillis) {
        if (totalMillis <= 0L) 0f
        else (1f - remainingMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
    }

    Box(
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
        Canvas(modifier = Modifier.fillMaxSize()) {
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

            val filledExact = elapsedFraction * total

            for (idx in 0 until total) {
                // Fill bottom-to-top, left-to-right within each row.
                val rowFromTop = rows - 1 - (idx / cols)
                val col = idx % cols
                val cx = offsetX + col * cellW + (cellW - dotSize) / 2f
                val cy = offsetY + rowFromTop * cellH + (cellH - dotSize) / 2f

                val alpha = when {
                    idx + 1 <= filledExact -> 0.95f
                    idx.toFloat() < filledExact -> {
                        val frac = (filledExact - idx).coerceIn(0f, 1f)
                        0.06f + frac * 0.89f
                    }
                    else -> 0.06f
                }

                drawRoundRect(
                    color = PureWhite.copy(alpha = alpha),
                    topLeft = Offset(cx, cy),
                    size = Size(dotSize, dotSize),
                    cornerRadius = corner
                )
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

// Particle system simulating sand falling under gravity and piling up.
// Particles are spawned near the top center; gravityX bends their path so
// tilting the phone makes the stream lean to one side. The pile uses a
// per-column height array and slumps under a slope threshold biased by
// gravityX so the heap immediately reacts when the device is tilted.
class ParticleSystem(val maxParticles: Int = 2400) {
    val px = FloatArray(maxParticles)
    val py = FloatArray(maxParticles)
    val pvx = FloatArray(maxParticles)
    val pvy = FloatArray(maxParticles)
    val pActive = BooleanArray(maxParticles)
    val pAlpha = FloatArray(maxParticles)

    val numCols = 90
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
        w = width
        h = height
        noiseSeed = (width.toInt() * 31 + height.toInt())
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

    fun update(remainingFraction: Float, gravityX: Float, gravityY: Float, dt: Float, totalMillis: Long) {
        if (w == 0f || h == 0f) return

        val safeGravityX = if (gravityX.isNaN()) 0f else gravityX
        val safeGravityY = if (gravityY.isNaN()) 9.81f else gravityY

        val gMult = 200f
        val activeGravityY = safeGravityY * gMult
        val activeGravityX = safeGravityX * gMult

        // Sized so the column-averaged pile reaches the full screen height
        // exactly when remainingFraction → 0. baseRate ≈ 320 grains/sec.
        val rawIncrement = (h * numCols * 1000f) / (320f * totalMillis)
        val sandIncrement = rawIncrement.coerceIn(0.02f, 60.0f)

        // 1. Spawn falling grains near top-center, with mild jitter and a
        // slight bias toward the gravity direction so the stream visibly leans.
        if (remainingFraction > 0f) {
            val baseRate = 320f
            val spawnRate = baseRate * (1.5f - remainingFraction)
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
