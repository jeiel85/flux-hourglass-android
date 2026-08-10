package com.flux.hourglass

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.Random
import kotlin.math.sin

class ProceduralAudioPlayer {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var synthThread: Thread? = null
    private val lock = Any()
    private var currentMode: DisplayMode = DisplayMode.SAND

    fun start(mode: DisplayMode) {
        synchronized(lock) {
            if (isPlaying) {
                if (currentMode == mode) return
                stop()
            }
            currentMode = mode
            isPlaying = true

            val minBufferSize = AudioTrack.getMinBufferSize(
                22050,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                22050,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize.coerceAtLeast(4096),
                AudioTrack.MODE_STREAM
            )
            audioTrack?.play()

            synthThread = Thread {
                val buffer = ShortArray(2048)
                val random = Random()
                var lastOut = 0f
                var phase = 0f

                while (isPlaying) {
                    val modeToPlay = currentMode
                    for (i in buffer.indices) {
                        val whiteNoise = random.nextFloat() * 2f - 1f

                        when (modeToPlay) {
                            DisplayMode.SAND -> {
                                // Sand: soft low-pass noise like gentle falling grains,
                                // plus rare subtle grain ticks.
                                lastOut = lastOut + 0.09f * (whiteNoise - lastOut)
                                var sample = lastOut * 6000f
                                if (random.nextFloat() < 0.0002f) {
                                    sample += (random.nextFloat() * 8000f - 4000f)
                                }
                                buffer[i] = sample.toInt().coerceIn(-32768, 32767).toShort()
                            }
                            DisplayMode.FIRE -> {
                                // Fire: soft low rumble + crackle pops
                                lastOut = lastOut + 0.08f * (whiteNoise - lastOut)
                                var sample = lastOut * 8000f

                                // Crackling pops
                                if (random.nextFloat() < 0.0005f) {
                                    sample += (random.nextFloat() * 26000f - 13000f)
                                }
                                buffer[i] = sample.toInt().coerceIn(-32768, 32767).toShort()
                            }
                            DisplayMode.WATER -> {
                                // Water: slow rolling swell (modulating volume/pitch of low-pass noise)
                                phase += 0.0003f
                                if (phase > 2f * Math.PI.toFloat()) {
                                    phase -= 2f * Math.PI.toFloat()
                                }
                                val swell = (sin(phase) * 0.4f + 0.6f)
                                lastOut = lastOut + 0.05f * (whiteNoise - lastOut)
                                buffer[i] = (lastOut * 12000f * swell).toInt().coerceIn(-32768, 32767).toShort()
                            }
                            else -> {
                                // LED is silent (start() is not called for LED).
                                buffer[i] = 0
                            }
                        }
                    }
                    audioTrack?.write(buffer, 0, buffer.size)
                }
            }
            synthThread?.start()
        }
    }

    fun stop() {
        synchronized(lock) {
            if (!isPlaying) return
            isPlaying = false
            try {
                synthThread?.interrupt()
                synthThread?.join(500)
            } catch (e: Exception) {
                // ignore
            }
            synthThread = null

            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (e: Exception) {
                // ignore
            }
            audioTrack = null
        }
    }
}
