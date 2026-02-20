package com.koraidv.sdk.capture

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

data class AntiSpoofResult(
    val isLikelyReal: Boolean,
    val textureScore: Double,
    val frequencyScore: Double,
    val colorScore: Double,
    val overallScore: Double
)

class AntiSpoofCheck {

    private val spoofThreshold = 0.35

    fun analyze(bitmap: Bitmap): AntiSpoofResult {
        // Downsample to 64x64
        val scaled = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
        val pixels = IntArray(64 * 64)
        scaled.getPixels(pixels, 0, 64, 0, 0, 64, 64)
        if (scaled !== bitmap) scaled.recycle()

        val texture = analyzeTexture(pixels, 64)
        val frequency = analyzeFrequency(pixels, 64)
        val color = analyzeColorDistribution(pixels, 64)

        val overall = texture * 0.45 + frequency * 0.30 + color * 0.25

        return AntiSpoofResult(
            isLikelyReal = overall >= spoofThreshold,
            textureScore = texture,
            frequencyScore = frequency,
            colorScore = color,
            overallScore = overall
        )
    }

    /**
     * Laplacian variance on grayscale.
     * Real skin has micro-texture producing higher variance;
     * printed/screen images are smoother with lower variance.
     */
    fun analyzeTexture(pixels: IntArray, size: Int): Double {
        // Convert to grayscale
        val gray = IntArray(pixels.size) { i ->
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            // Standard luminance conversion
            (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }

        // Compute Laplacian variance
        var laplacianSum = 0.0
        var laplacianSumSq = 0.0
        var count = 0.0

        for (y in 1 until size - 1) {
            for (x in 1 until size - 1) {
                val idx = y * size + x
                val center = gray[idx].toDouble()
                val top = gray[idx - size].toDouble()
                val bottom = gray[idx + size].toDouble()
                val left = gray[idx - 1].toDouble()
                val right = gray[idx + 1].toDouble()

                val laplacian = -4 * center + top + bottom + left + right
                laplacianSum += laplacian
                laplacianSumSq += laplacian * laplacian
                count += 1.0
            }
        }

        if (count <= 0) return 0.0
        val mean = laplacianSum / count
        val variance = (laplacianSumSq / count) - (mean * mean)

        // Normalize: typical real face variance ~50-500, spoof ~0-30
        return min(variance / 200.0, 1.0)
    }

    /**
     * Frequency analysis via adjacent-pixel differences.
     * Real faces have more balanced high/low frequency energy;
     * screens/prints tend to have less high-frequency content.
     */
    fun analyzeFrequency(pixels: IntArray, size: Int): Double {
        // Convert to grayscale float [0, 1]
        val floatData = FloatArray(pixels.size) { i ->
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        }

        var lowEnergy = 0f
        var highEnergy = 0f

        for (y in 0 until size) {
            val rowStart = y * size
            for (x in 0 until size - 1) {
                val diff = abs(floatData[rowStart + x + 1] - floatData[rowStart + x])
                if (x < size / 2) {
                    lowEnergy += diff
                } else {
                    highEnergy += diff
                }
            }
        }

        val totalEnergy = lowEnergy + highEnergy
        if (totalEnergy <= 0f) return 0.0

        val highFreqRatio = (highEnergy / totalEnergy).toDouble()

        // Normalize: expect ~0.3-0.5 for real, ~0.1-0.25 for screen
        return min(highFreqRatio * 2.5, 1.0)
    }

    /**
     * Color distribution analysis using YCbCr chrominance std dev.
     * Real skin has wider chroma variance than printed photos.
     */
    fun analyzeColorDistribution(pixels: IntArray, size: Int): Double {
        val pixelCount = size * size
        val cbValues = DoubleArray(pixelCount)
        val crValues = DoubleArray(pixelCount)

        for (i in 0 until pixelCount) {
            val pixel = pixels[i]
            val r = Color.red(pixel).toDouble()
            val g = Color.green(pixel).toDouble()
            val b = Color.blue(pixel).toDouble()

            // Simplified YCbCr conversion
            cbValues[i] = 128 + (-0.169 * r - 0.331 * g + 0.500 * b)
            crValues[i] = 128 + (0.500 * r - 0.419 * g - 0.081 * b)
        }

        val cbStdDev = standardDeviation(cbValues)
        val crStdDev = standardDeviation(crValues)

        // Real skin has wider chroma variance (std dev ~10-30)
        // Printed photos have narrower (~3-10)
        val combinedStdDev = (cbStdDev + crStdDev) / 2.0

        // Normalize to 0-1
        return min(combinedStdDev / 20.0, 1.0)
    }

    private fun standardDeviation(values: DoubleArray): Double {
        if (values.isEmpty()) return 0.0
        val count = values.size.toDouble()
        val mean = values.sum() / count
        val variance = values.sumOf { (it - mean) * (it - mean) } / count
        return sqrt(variance)
    }
}
