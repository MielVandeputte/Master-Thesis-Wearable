package com.masterproef.model

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import org.jtransforms.fft.DoubleFFT_1D
import java.util.LinkedList
import kotlin.math.sqrt

private const val FREQUENCY = 20000
private const val FREQUENCY_TOLERANCE = 100
private const val SAMPLE_RATE = 44100

private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val BYTES_PER_SAMPLE = 2

object UltrasonicDetector {

    private var countDown = -1

    fun startAnalyzingAudio(): Boolean {
        countDown = 30
        return analyzeAudio()
    }

    fun stopAnalyzingAudio() {
        countDown = -1
    }

    private var bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    private val history = LinkedList<Double>()
    private var detectedCounter = 0

    @SuppressLint("MissingPermission")
    private var audioRecord = AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)

    private fun analyzeAudio(): Boolean {
        audioRecord.startRecording()

        val thread = Thread {
            detectedCounter = 0

            while (countDown >= 0 && detectedCounter < 5) {
                //Log.i("Miel loop", "Loop started")
                //Log.i("Miel countdown", countDown.toString())

                val start = System.currentTimeMillis()

                //Log.i("Miel start", start.toString())

                val buffer = ByteArray(bufferSize)
                audioRecord.read(buffer, 0, bufferSize)

                val audioLength = buffer.size / BYTES_PER_SAMPLE
                val audioSamples = DoubleArray(audioLength)

                for (i in 0 until audioLength) {
                    val sample = (buffer[i * BYTES_PER_SAMPLE].toInt() and 0xFF) or (buffer[i * BYTES_PER_SAMPLE + 1].toInt() shl 8)
                    audioSamples[i] = sample.toDouble()
                }

                //Log.i("Miel audiosamples", audioSamples.toList().toString())

                val lowerFrequency = FREQUENCY - FREQUENCY_TOLERANCE
                val upperFrequency = FREQUENCY + FREQUENCY_TOLERANCE

                val fft = DoubleFFT_1D(audioSamples.size.toLong())
                val spectrum = audioSamples.copyOf()

                //Log.i("Miel spectrum 2", spectrum.toList().toString())

                fft.realForward(spectrum)

                for (i in 0 until audioSamples.size / 2) {
                    val frequency = i * SAMPLE_RATE.toDouble() / audioSamples.size
                    if (frequency < lowerFrequency || frequency > upperFrequency) {
                        spectrum[2 * i] = 0.0
                        spectrum[2 * i + 1] = 0.0
                    }
                }

                fft.realInverse(spectrum, true)
                val magnitudes = DoubleArray(audioSamples.size / 2)

                //Log.i("Miel magnitudes", magnitudes.toList().toString())

                for (i in 0 until audioSamples.size / 2) {
                    val real = spectrum[2 * i]
                    val img = spectrum[2 * i + 1]
                    magnitudes[i] = sqrt(real * real + img * img)
                }

                val maxMagnitude = magnitudes.maxOrNull() ?: 0.0
                addValue(maxMagnitude)
                countDown -= 1

                val interval = System.currentTimeMillis() - start

                //Log.i("Miel maxmagnitude", System.currentTimeMillis().toString())
                //Log.i("Miel end", System.currentTimeMillis().toString())
                //Log.i("Miel interval", interval.toString())


                if (interval < 450) { Thread.sleep(450 - interval) }
            }
        }

        thread.start()
        thread.join()

        audioRecord.stop()

        //Log.i("Miel detectedcounter", detectedCounter.toString())

        return detectedCounter >= 5
    }

    private fun addValue(value: Double) {
        history.add(value)

        if (history.size >= 7) {
            //Log.i("Miel history", history.toString())

            if (checkForPattern()) {
                detectedCounter += 1
            }
            history.removeFirst()
        }
    }

    private fun checkForPattern(): Boolean {
        val mean = history.toList().average()
        val pattern = intArrayOf(3, 1, 3)

        var countDown = pattern[0]
        var arrayPos = 0
        var enabled = true

        var errors = 0

        for (el in history) {
            if (countDown <= 0) {
                arrayPos += 1

                if (arrayPos >= pattern.size) { break }

                countDown = pattern[arrayPos]
                enabled = !enabled
            }

            countDown -= 1

            if (enabled != isPeak(mean, el)) {
                if (errors > 0) { return false } else { errors += 1 }
            } else { errors = 0 }
        }

        return true
    }

    private fun isPeak(mean: Double, value: Double): Boolean {
        var threshold = mean / 2
        if ( mean < 100.0) { threshold = 100.0 }

        return value >= threshold
    }
}