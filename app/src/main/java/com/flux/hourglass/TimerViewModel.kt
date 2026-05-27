package com.flux.hourglass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class TimerState {
    object Setup : TimerState()
    data class Running(val remainingMillis: Long, val totalMillis: Long) : TimerState()
    data class Paused(val remainingMillis: Long, val totalMillis: Long) : TimerState()
    object Finished : TimerState()
}

class TimerViewModel : ViewModel() {
    private val _timerState = MutableStateFlow<TimerState>(TimerState.Setup)
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    private val _remainingMillis = MutableStateFlow(0L)
    val remainingMillis: StateFlow<Long> = _remainingMillis.asStateFlow()

    private var timerJob: Job? = null
    var totalDuration: Long = 0L
        private set
    private var endTime: Long = 0L

    fun startTimer(hours: Int, minutes: Int, seconds: Int) {
        val totalSecs = (hours * 3600L) + (minutes * 60L) + seconds
        if (totalSecs <= 0) return

        totalDuration = totalSecs * 1000L
        _remainingMillis.value = totalDuration
        _timerState.value = TimerState.Running(totalDuration, totalDuration)

        endTime = System.currentTimeMillis() + totalDuration
        startTicking()
    }

    fun pauseTimer() {
        val current = _timerState.value as? TimerState.Running ?: return
        timerJob?.cancel()
        timerJob = null
        _timerState.value = TimerState.Paused(current.remainingMillis, current.totalMillis)
    }

    fun resumeTimer() {
        val current = _timerState.value as? TimerState.Paused ?: return
        endTime = System.currentTimeMillis() + current.remainingMillis
        _remainingMillis.value = current.remainingMillis
        _timerState.value = TimerState.Running(current.remainingMillis, current.totalMillis)
        startTicking()
    }

    fun resetTimer() {
        timerJob?.cancel()
        timerJob = null
        totalDuration = 0L
        _remainingMillis.value = 0L
        _timerState.value = TimerState.Setup
    }

    private fun startTicking() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val left = endTime - now
                if (left <= 0) {
                    _remainingMillis.value = 0L
                    _timerState.value = TimerState.Finished
                    break
                }
                _remainingMillis.value = left
                _timerState.value = TimerState.Running(left, totalDuration)
                delay(16)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
