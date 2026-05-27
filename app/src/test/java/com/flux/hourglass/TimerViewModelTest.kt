package com.flux.hourglass

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setupIsTheInitialState() {
        val vm = TimerViewModel()
        assertEquals(TimerState.Setup, vm.timerState.value)
        assertEquals(0L, vm.remainingMillis.value)
        assertEquals(0L, vm.totalDuration)
    }

    @Test
    fun startTimerWithZeroDurationStaysInSetup() = runTest(dispatcher) {
        val vm = TimerViewModel()
        vm.startTimer(hours = 0, minutes = 0, seconds = 0)
        assertEquals(TimerState.Setup, vm.timerState.value)
    }

    @Test
    fun startTimerEntersRunningWithMatchingDuration() = runTest(dispatcher) {
        val vm = TimerViewModel()
        vm.startTimer(hours = 0, minutes = 1, seconds = 30)

        val state = vm.timerState.value
        assertTrue("expected Running, got $state", state is TimerState.Running)
        assertEquals(90_000L, vm.totalDuration)
        assertEquals(90_000L, (state as TimerState.Running).totalMillis)
    }

    @Test
    fun resetReturnsToSetupAndClearsDuration() = runTest(dispatcher) {
        val vm = TimerViewModel()
        vm.startTimer(hours = 0, minutes = 0, seconds = 5)
        vm.resetTimer()
        assertEquals(TimerState.Setup, vm.timerState.value)
        assertEquals(0L, vm.totalDuration)
        assertEquals(0L, vm.remainingMillis.value)
    }

    @Test
    fun particleSystemRejectsNaNGravityWithoutCorruptingHeights() {
        val system = ParticleSystem(maxParticles = 32)
        system.initDimensions(width = 400f, height = 800f)
        system.update(
            remainingFraction = 0.5f,
            gravityX = Float.NaN,
            gravityY = Float.NaN,
            dt = 0.016f,
            totalMillis = 60_000L,
        )
        for (h in system.heights) {
            assertTrue("height became NaN", !h.isNaN())
            assertTrue("height became negative ($h)", h >= 0f)
        }
    }

    @Test
    fun particleSystemResetClearsActiveParticlesAndHeights() {
        val system = ParticleSystem(maxParticles = 16)
        system.initDimensions(width = 200f, height = 400f)
        repeat(10) {
            system.update(
                remainingFraction = 0.9f,
                gravityX = 0f,
                gravityY = 9.81f,
                dt = 0.016f,
                totalMillis = 10_000L,
            )
        }
        system.reset()
        for (active in system.pActive) {
            assertTrue("particle remained active after reset", !active)
        }
        for (h in system.heights) {
            assertEquals(0f, h, 0.0001f)
        }
    }
}
