package com.flux.hourglass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import com.flux.hourglass.ui.theme.PureBlack
import com.flux.hourglass.ui.theme.PureWhite
import com.flux.hourglass.ui.theme.SandWhite
import kotlin.math.atan2
import kotlin.math.cos
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
    val state by viewModel.timerState.collectAsState()
    val remainingMillis by viewModel.remainingMillis.collectAsState()

    var hoursVal by remember { mutableStateOf(0) }
    var minutesVal by remember { mutableStateOf(1) }
    var secondsVal by remember { mutableStateOf(0) }

    when (val current = state) {
        is TimerState.Setup -> {
            SetupScreen(
                hours = hoursVal,
                minutes = minutesVal,
                seconds = secondsVal,
                onHoursChange = { hoursVal = it },
                onMinutesChange = { minutesVal = it },
                onSecondsChange = { secondsVal = it },
                onPresetSelected = { h, m, s ->
                    hoursVal = h
                    minutesVal = m
                    secondsVal = s
                },
                onStart = {
                    viewModel.startTimer(hoursVal, minutesVal, secondsVal)
                }
            )
        }
        is TimerState.Running -> {
            RunningScreen(
                remainingMillis = remainingMillis,
                totalMillis = current.totalMillis,
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
            fontWeight = FontWeight.Thin,
            color = PureWhite.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp)
        )

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
                color = PureWhite.copy(alpha = 0.2f),
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
                color = PureWhite.copy(alpha = 0.2f),
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Thin,
                    color = PureWhite,
                    letterSpacing = 4.sp,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp)
                )
                // A thin, gorgeous line beneath instead of a heavy box
                Spacer(
                    modifier = Modifier
                        .width(48.dp)
                        .height(0.5.dp)
                        .background(PureWhite.copy(alpha = 0.5f))
                )
            }
        }
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
            fontSize = 12.sp,
            fontWeight = FontWeight.Thin,
            color = PureWhite.copy(alpha = 0.55f),
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
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraLight,
            color = PureWhite.copy(alpha = 0.3f),
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        IconButton(
            onClick = {
                onValueChange((value + 1).coerceIn(0, max))
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            colors = IconButtonDefaults.iconButtonColors(contentColor = PureWhite.copy(alpha = 0.3f)),
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Increment $label",
                modifier = Modifier.size(18.dp)
            )
        }

        Text(
            text = String.format("%02d", value),
            fontSize = 54.sp,
            fontWeight = FontWeight.Thin,
            color = PureWhite,
            textAlign = TextAlign.Center
        )

        IconButton(
            onClick = {
                onValueChange((value - 1).coerceIn(0, max))
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            colors = IconButtonDefaults.iconButtonColors(contentColor = PureWhite.copy(alpha = 0.3f)),
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Decrement $label",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun RunningScreen(
    remainingMillis: Long,
    totalMillis: Long,
    onPause: () -> Unit = {},
    onReset: () -> Unit
) {
    KeepScreenOn()

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Tilt (Gravity) variables
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(9.81f) }

    // Touch reveal state
    var isScreenPressed by remember { mutableStateOf(false) }

    // Register Accelerometer sensor for physical gravity tilt
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    // Smooth sensor readings using Low-Pass Filter
                    val alpha = 0.15f
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

    // Progress Fraction (from 1.0f down to 0.0f)
    val progressFraction = remember(remainingMillis, totalMillis) {
        (remainingMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
    }

    // Keep the high-performance physics arrays inside a remembered class
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
        // High-fps Physics Simulation Loop & Rendering Canvas
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

                    // Run particle simulation
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

            // Initialize dimensions inside particle system if first frame
            physics.initDimensions(width, height)

            // 1. Draw ACTIVE FALLING pixels
            for (i in 0 until physics.maxParticles) {
                if (physics.pActive[i]) {
                    drawCircle(
                        color = PureWhite.copy(alpha = physics.pAlpha[i]),
                        radius = 3.0.dp.toPx(),
                        center = Offset(physics.px[i], physics.py[i])
                    )
                }
            }

            // 2. Draw LOWER SAND accumulated pile perfectly starting from the very bottom
            val colWidth = width / physics.numCols
            val rNoise = Random(55)
            for (i in 0 until physics.numCols) {
                val h = physics.heights[i]
                if (h > 0) {
                    val x = i * colWidth
                    drawRect(
                        color = SandWhite,
                        topLeft = Offset(x, height - h),
                        size = Size(colWidth - 0.5f, h)
                    )

                    // Overlay grainy surface details
                    for (k in 0..2) {
                        val surfaceX = x + rNoise.nextFloat() * colWidth
                        val surfaceY = height - h - rNoise.nextFloat() * 12f
                        if (surfaceY < height) {
                            drawCircle(
                                color = PureWhite.copy(alpha = 0.95f),
                                radius = 2.0.dp.toPx(),
                                center = Offset(surfaceX, surfaceY)
                            )
                        }
                    }
                }
            }
        }

        // TOUCH REVEAL OVERLAY (Fade in/out exact HH:MM:SS)
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
                    .background(PureBlack.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "R E M A I N I N G",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraLight,
                        color = PureWhite.copy(alpha = 0.4f),
                        letterSpacing = 4.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        text = timeString,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Thin,
                        color = PureWhite,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Bottom controls: PAUSE on the left, RESET on the right, both extremely faint
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
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
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Thin,
                    color = PureWhite.copy(alpha = 0.25f),
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
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Thin,
                    color = PureWhite.copy(alpha = 0.25f),
                    letterSpacing = 3.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
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
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraLight,
            color = PureWhite.copy(alpha = 0.4f),
            letterSpacing = 4.sp,
            modifier = Modifier.padding(top = 32.dp)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = timeString,
                fontSize = 48.sp,
                fontWeight = FontWeight.Thin,
                color = PureWhite,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "$percentLeft %  R E M A I N I N G",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraLight,
                color = PureWhite.copy(alpha = 0.35f),
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraLight,
                        color = PureWhite.copy(alpha = 0.5f),
                        letterSpacing = 3.sp,
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp)
                    )
                    Spacer(
                        modifier = Modifier
                            .width(32.dp)
                            .height(0.5.dp)
                            .background(PureWhite.copy(alpha = 0.3f))
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
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Thin,
                        color = PureWhite,
                        letterSpacing = 4.sp,
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp)
                    )
                    Spacer(
                        modifier = Modifier
                            .width(48.dp)
                            .height(0.5.dp)
                            .background(PureWhite.copy(alpha = 0.5f))
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

    // Repeated pulses/vibration on completion
    LaunchedEffect(Unit) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        // Trigger gorgeous pulsing vibration sequence safely wrapped in try-catch
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
    }

    var pulseState by remember { mutableStateOf(false) }
    val pulseAlpha by animateFloatAsState(
        targetValue = if (pulseState) 0.6f else 0.1f,
        animationSpec = tween(durationMillis = 1500),
        label = "Pulse animation"
    )

    LaunchedEffect(Unit) {
        // Endless subtle breathing animation for completed screen
        while (true) {
            pulseState = !pulseState
            kotlinx.coroutines.delay(1500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite.copy(alpha = pulseAlpha)) // Subtle white glow background pulse on end
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "E N D",
            fontSize = 72.sp,
            fontWeight = FontWeight.Thin,
            color = PureWhite,
            letterSpacing = 8.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Text(
            text = "T I M E  I S  F U L F I L L E D",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraLight,
            color = PureWhite.copy(alpha = 0.5f),
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraLight,
                    color = PureWhite,
                    letterSpacing = 3.sp,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp)
                )
                Spacer(
                    modifier = Modifier
                        .width(36.dp)
                        .height(0.5.dp)
                        .background(PureWhite.copy(alpha = 0.5f))
                )
            }
        }
    }
}

// Particle system simulating falling sand and piling up heights
class ParticleSystem(val maxParticles: Int = 1800) {
    val px = FloatArray(maxParticles)
    val py = FloatArray(maxParticles)
    val pvx = FloatArray(maxParticles)
    val pvy = FloatArray(maxParticles)
    val pActive = BooleanArray(maxParticles)
    val pAlpha = FloatArray(maxParticles)

    val numCols = 50
    val heights = FloatArray(numCols)

    private var w = 0f
    private var h = 0f
    private var spawnAccumulator = 0f

    fun initDimensions(width: Float, height: Float) {
        if (w == width && h == height) return
        w = width
        h = height
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

        // Defensive checks against any weird NaN values from accelerometer
        val safeGravityX = if (gravityX.isNaN()) 0f else gravityX
        val safeGravityY = if (gravityY.isNaN()) 9.81f else gravityY

        // Base physics gravity configurations
        val gMult = 180f
        val activeGravityY = safeGravityY * gMult

        // Calculate sand height increment dynamically so that the sand pile builds up
        // to exactly 50% of the screen height at the end of the selected duration naturally.
        val rawIncrement = (0.38f * h * numCols * 1000f) / (120f * totalMillis)
        val sandIncrement = rawIncrement.coerceIn(0.01f, 25.0f)

        // 1. Spawning of falling particles
        if (remainingFraction > 0f) {
            val baseRate = 120f // particles per second limit for elegant stream
            val spawnRate = baseRate * (1.5f - remainingFraction)
            spawnAccumulator += spawnRate * dt

            val toSpawn = spawnAccumulator.toInt()
            if (toSpawn > 0) {
                spawnAccumulator -= toSpawn
                var spawned = 0
                for (i in 0 until maxParticles) {
                    if (!pActive[i]) {
                        pActive[i] = true
                        // Ensure perfect vertical alignment by spawning exactly at the horizontal center
                        px[i] = w / 2f
                        py[i] = 0f
                        pvx[i] = 0f
                        pvy[i] = 350f + Random.nextFloat() * 50f // drop straight down nicely
                        pAlpha[i] = 0.8f + Random.nextFloat() * 0.2f
                        spawned++
                        if (spawned >= toSpawn) break
                    }
                }
            }
        }

        // 2. Fall, collision and accumulate physics updates
        for (i in 0 until maxParticles) {
            if (pActive[i]) {
                // Avoid drifting: always keep horizontal coordinate centered to maintain a perfect vertical line stream
                pvx[i] = 0f
                pvy[i] += activeGravityY * dt

                // Apply air damping
                pvy[i] *= 0.98f

                px[i] = w / 2f
                py[i] += pvy[i] * dt

                // Bottom Sand Pile collision checks
                val col = ((px[i] / w) * numCols).toInt().coerceIn(0, numCols - 1)
                val pileSurfaceY = h - heights[col]

                // Added redundant py[i] >= h - 1f check to absolutely guarantee particle deactivation under all conditions
                if (py[i] >= pileSurfaceY || py[i] >= h - 1f) {
                    // Particle has hit the pile surface! Slide down the slopes step-by-step
                    // to naturally model a realistic angle of repose instead of stacking instantly at the center
                    var currentCol = col
                    for (step in 0..7) {
                        val leftCol = currentCol - 1
                        val rightCol = currentCol + 1
                        val leftHeight = if (leftCol >= 0) heights[leftCol] else Float.MAX_VALUE
                        val rightHeight = if (rightCol < numCols) heights[rightCol] else Float.MAX_VALUE
                        val currentHeight = heights[currentCol]

                        // Gravitational tilt shift bias
                        val tiltBias = safeGravityX * 1.5f
                        val scoreLeft = (currentHeight - leftHeight) + tiltBias
                        val scoreRight = (currentHeight - rightHeight) - tiltBias

                        if (scoreLeft > scoreRight && scoreLeft > 0.8f && leftCol >= 0) {
                            currentCol = leftCol
                        } else if (scoreRight > scoreLeft && scoreRight > 0.8f && rightCol < numCols) {
                            currentCol = rightCol
                        } else {
                            break
                        }
                    }

                    // Deactivate particle and accumulate sand at the resting column (handling safety height limit)
                    pActive[i] = false
                    heights[currentCol] = (heights[currentCol] + sandIncrement).coerceIn(0f, h * 0.95f)
                }
            }
        }

        // 3. Fluid Sand Pile Slumping/Pouring under Gravity & Slope
        // Since gravityX represents physical lateral tilt (approx. -9.8 to 9.8 m/s^2), we scale it to calculate bias
        val biasX = safeGravityX * 0.45f
        
        for (pass in 0..2) { // Multiple passes per frame for faster, organic fluid slumping
            for (idx in 1 until numCols - 1) {
                // Flow to the left (avoiding any negative threshold which allows uphill flowing)
                val leftThreshold = (1.0f + biasX).coerceAtLeast(0.1f)
                val leftDiff = heights[idx] - heights[idx - 1]
                if (leftDiff > leftThreshold) {
                    val slide = (leftDiff - leftThreshold) * 0.35f
                    if (slide > 0f) {
                        val actual = Math.min(slide, heights[idx])
                        heights[idx] = (heights[idx] - actual).coerceAtLeast(0f)
                        heights[idx - 1] = (heights[idx - 1] + actual).coerceAtMost(h * 0.95f)
                    }
                }

                // Flow to the right (avoiding any negative threshold which allows uphill flowing)
                val rightThreshold = (1.0f - biasX).coerceAtLeast(0.1f)
                val rightDiff = heights[idx] - heights[idx + 1]
                if (rightDiff > rightThreshold) {
                    val slide = (rightDiff - rightThreshold) * 0.35f
                    if (slide > 0f) {
                        val actual = Math.min(slide, heights[idx])
                        heights[idx] = (heights[idx] - actual).coerceAtLeast(0f)
                        heights[idx + 1] = (heights[idx + 1] + actual).coerceAtMost(h * 0.95f)
                    }
                }
            }

            // Extreme slope slumping near edge boundaries when device is heavily tilted
            if (biasX < -1.0f) {
                for (idx in 1 until numCols) {
                    val diff = heights[idx] - heights[idx - 1]
                    if (diff > 0.1f) {
                        val slide = diff * 0.2f
                        heights[idx] = (heights[idx] - slide).coerceAtLeast(0f)
                        heights[idx - 1] = (heights[idx - 1] + slide).coerceAtMost(h * 0.95f)
                    }
                }
            } else if (biasX > 1.0f) {
                for (idx in numCols - 2 downTo 0) {
                    val diff = heights[idx] - heights[idx + 1]
                    if (diff > 0.1f) {
                        val slide = diff * 0.2f
                        heights[idx] = (heights[idx] - slide).coerceAtLeast(0f)
                        heights[idx + 1] = (heights[idx + 1] + slide).coerceAtMost(h * 0.95f)
                    }
                }
            }
        }

        // Maintain overall volume proportional to the timer countdown progress smoothly
        val targetVolume = (1f - remainingFraction) * (h * 0.55f)
        var sum = 0f
        for (i in 0 until numCols) {
            val hVal = heights[i]
            if (!hVal.isNaN() && hVal > 0f) {
                sum += hVal
            } else if (hVal.isNaN()) {
                heights[i] = 0f // Correct any NaN
            }
        }
        
        // Only apply scaling correction after initial starts to avoid early crushing,
        // and carefully coerce the scale factor to prevent Infinity/NaN explosions
        if (remainingFraction < 0.995f && sum > 2.0f) {
            val targetSum = targetVolume * numCols * 0.45f
            val scaleFactor = (targetSum / sum).coerceIn(0.5f, 2.0f)
            if (Math.abs(scaleFactor - 1.0f) > 0.05f) {
                val lerpFactor = 0.008f
                for (i in 0 until numCols) {
                    heights[i] = (heights[i] * (1f - lerpFactor) + (heights[i] * scaleFactor) * lerpFactor).coerceIn(0f, h * 0.95f)
                    if (heights[i].isNaN()) {
                        heights[i] = 0f // fallback sanitation
                    }
                }
            }
        }
    }
}
