package com.masterproef.shared

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import org.jtransforms.fft.DoubleFFT_1D
import java.util.LinkedList
import kotlin.math.sqrt

private const val FREQUENCY = 20000
private const val FREQUENCY_TOLERANCE = 50
private const val SAMPLE_RATE = 44100

private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val BYTES_PER_SAMPLE = 2

// Determines the size of the shortest possible sample that can be taken
// while still being able to detect the 20.000 Hz frequency
private val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

class UltrasonicDetector {

    // Because this app isn't made for general usage, it is assumed that all permissions always get accepted
    @SuppressLint("MissingPermission")
    var audioRecord = AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)

    // Maintains the last 7 values values created by the analyzeAudio function
    private val history = LinkedList<Double>()

    private var countDown = 30
    private var detectedCounter = 0

    /* Activates microphone and start listening for the pattern
     * Returns true if the pattern is detected
     * Returns false if the countdown runs out and the pattern wasn't detected
     * */
    fun analyzeAudio(): Boolean {
        audioRecord.startRecording()

        // Runs a loop till the countdown runs out or stops earlier if function checkForPattern detects pattern 5 times
        while (countDown >= 0 && detectedCounter < 3) {
            val startTimestamp = System.currentTimeMillis()

            // Inserts recorded sample into ByteArray
            val buffer = ByteArray(bufferSize)
            audioRecord.read(buffer, 0, bufferSize)

            // Transforms sample from ByteArray to DoubleArray where 2 bytes are converted to 1 double
            val length = buffer.size / BYTES_PER_SAMPLE
            val samples = DoubleArray(length)

            for (i in 0 until length) {
                // The LSB are concatenated with the MSB using a shift left and or-operator to form the double
                val sample = (buffer[i * BYTES_PER_SAMPLE].toInt() and 0xFF) or (buffer[i * BYTES_PER_SAMPLE + 1].toInt() shl 8)
                samples[i] = sample.toDouble()
            }

            /* Perform in place discrete fourier transformation to convert from time-space to frequency-space
             * Input is an array of samples (real values), it is transformed in place to an array of magnitudes of specific frequencies
             * The amount of frequencies is half of the amount of samples but the magnitudes are saved as complex values so the size of the array is the same
             * */
            val fft = DoubleFFT_1D(samples.size.toLong())
            fft.realForward(samples)

            /* Iterate over the array of frequency magnitudes, divided by 2 because real and imaginary values are saved separately
             * The magnitude of every frequency within the range 19.050 - 20.050 is saved to an array
             * This extended frequency range ensures that speakers that don't create the right frequency exactly also work
             * */
            val lowerFrequency = FREQUENCY - FREQUENCY_TOLERANCE
            val upperFrequency = FREQUENCY + FREQUENCY_TOLERANCE

            val magnitudes = mutableListOf<Double>()

            for (i in 0 until samples.size / 2) {
                // Samplerate divided by the amount of samples returns the frequency resolution after a DFT, the bin index can be used together with this to determine the frequency of the bin
                val frequency = i * SAMPLE_RATE.toDouble() / samples.size

                if ((frequency >= lowerFrequency) && (frequency <= upperFrequency)) {
                    val real = samples[2 * i]
                    val img = samples[2 * i + 1]
                    val total = sqrt(real * real + img * img)

                    magnitudes.add(total)
                }
            }

            // The largest value in the magnitudes array is saved to the history linked list
            addValue(magnitudes.max())

            Log.i("New Value", magnitudes.max().toString())

            countDown -= 1

            // Makes sure that every loop takes 450 milliseconds
            val interval = System.currentTimeMillis() - startTimestamp
            if (interval < 450) { Thread.sleep(450 - interval) }
        }

        audioRecord.stop()
        audioRecord.release()

        return detectedCounter >= 3
    }

    // Adds the last value to the history linkedlist and deletes the older values to maintain a constant size of 7
    private fun addValue(value: Double) {
        history.add(value)

        if (history.size >= 7) {
            if (checkForPattern()) { detectedCounter += 1 }
            history.removeFirst()
        }
    }

    // Checks the history linkedlist if the pattern of peaks is present in it
    // The function isPeak is used together with the mean value in the linkedlist to determine if a value is a peak
    private fun checkForPattern(): Boolean {
        val mean = history.toList().average()
        val pattern = intArrayOf(3, 1, 3)

        var countDown = pattern[0]
        var arrayPos = 0
        var enabled = true

        Log.i("History Evaluation", history.toList().toString())

        for (el in history) {
            if (countDown <= 0) {
                arrayPos += 1

                if (arrayPos >= pattern.size) { break }

                countDown = pattern[arrayPos]
                enabled = !enabled
            }

            countDown -= 1

            if (enabled != isPeak(mean, el)) {
                Log.i("Evaluation Result", "False")
                return false
            }
        }

        Log.i("Evaluation Result", "True")
        return true
    }

    /* Determines if the sample was taken during the on-part of the pattern
     * If the pattern is actually playing then the 20.000 Hz frequency will be significantly louder during the on-part than the mean loudness of 20.000 Hz over the whole history linkedlist
     * Random detections when practically no 20.000 Hz is present in the current sound are prevented by the threshold of 10.000
     * */
    private fun isPeak(mean: Double, value: Double): Boolean {
        var threshold = mean / 2
        if ( threshold < 5000.0) { threshold = 5000.0 }
        return value >= threshold
    }
}