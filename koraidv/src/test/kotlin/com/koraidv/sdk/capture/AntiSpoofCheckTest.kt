package com.koraidv.sdk.capture

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AntiSpoofCheckTest {

    private lateinit var checker: AntiSpoofCheck

    @Before
    fun setUp() {
        checker = AntiSpoofCheck()
    }

    // -- Solid color bitmap: no texture, no frequency variation, no color variation --

    @Test
    fun `solid color bitmap is flagged as spoof`() {
        val bitmap = createSolidBitmap(Color.RED, 64, 64)
        val result = checker.analyze(bitmap)
        assertFalse("Solid color should be flagged as spoof", result.isLikelyReal)
        bitmap.recycle()
    }

    @Test
    fun `solid color bitmap has low texture score`() {
        val pixels = IntArray(64 * 64) { Color.BLUE }
        val score = checker.analyzeTexture(pixels, 64)
        assertTrue("Texture score should be very low for solid color", score < 0.1)
    }

    @Test
    fun `solid color bitmap has zero color score`() {
        val pixels = IntArray(64 * 64) { Color.GREEN }
        val score = checker.analyzeColorDistribution(pixels, 64)
        assertEquals("Color score should be 0 for uniform color", 0.0, score, 0.001)
    }

    // -- Checkerboard bitmap: high texture contrast --

    @Test
    fun `checkerboard bitmap has high texture score`() {
        val pixels = createCheckerboardPixels(64)
        val score = checker.analyzeTexture(pixels, 64)
        assertTrue("Checkerboard should have high texture score, got $score", score > 0.5)
    }

    @Test
    fun `checkerboard bitmap has high frequency score`() {
        val pixels = createCheckerboardPixels(64)
        val score = checker.analyzeFrequency(pixels, 64)
        assertTrue("Checkerboard should have measurable frequency score, got $score", score > 0.0)
    }

    // -- Score range validation --

    @Test
    fun `texture score is always in 0 to 1 range`() {
        val testCases = listOf(
            IntArray(64 * 64) { Color.BLACK },
            IntArray(64 * 64) { Color.WHITE },
            createCheckerboardPixels(64),
            createGradientPixels(64)
        )
        for (pixels in testCases) {
            val score = checker.analyzeTexture(pixels, 64)
            assertTrue("Texture score $score should be >= 0", score >= 0.0)
            assertTrue("Texture score $score should be <= 1", score <= 1.0)
        }
    }

    @Test
    fun `frequency score is always in 0 to 1 range`() {
        val testCases = listOf(
            IntArray(64 * 64) { Color.BLACK },
            IntArray(64 * 64) { Color.WHITE },
            createCheckerboardPixels(64),
            createGradientPixels(64)
        )
        for (pixels in testCases) {
            val score = checker.analyzeFrequency(pixels, 64)
            assertTrue("Frequency score $score should be >= 0", score >= 0.0)
            assertTrue("Frequency score $score should be <= 1", score <= 1.0)
        }
    }

    @Test
    fun `color score is always in 0 to 1 range`() {
        val testCases = listOf(
            IntArray(64 * 64) { Color.BLACK },
            IntArray(64 * 64) { Color.WHITE },
            createCheckerboardPixels(64),
            createGradientPixels(64)
        )
        for (pixels in testCases) {
            val score = checker.analyzeColorDistribution(pixels, 64)
            assertTrue("Color score $score should be >= 0", score >= 0.0)
            assertTrue("Color score $score should be <= 1", score <= 1.0)
        }
    }

    // -- Weighted formula verification --

    @Test
    fun `overall score matches weighted formula`() {
        val bitmap = createCheckerboardBitmap(64)
        val result = checker.analyze(bitmap)

        val expected = result.textureScore * 0.45 +
                result.frequencyScore * 0.30 +
                result.colorScore * 0.25

        assertEquals(
            "Overall score should match weighted formula",
            expected, result.overallScore, 0.0001
        )
        bitmap.recycle()
    }

    @Test
    fun `solid bitmap overall score matches weighted formula`() {
        val bitmap = createSolidBitmap(Color.GRAY, 64, 64)
        val result = checker.analyze(bitmap)

        val expected = result.textureScore * 0.45 +
                result.frequencyScore * 0.30 +
                result.colorScore * 0.25

        assertEquals(
            "Overall score should match weighted formula",
            expected, result.overallScore, 0.0001
        )
        bitmap.recycle()
    }

    // -- Helper functions --

    private fun createSolidBitmap(color: Int, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height) { color }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun createCheckerboardBitmap(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = createCheckerboardPixels(size)
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    private fun createCheckerboardPixels(size: Int): IntArray {
        return IntArray(size * size) { i ->
            val x = i % size
            val y = i / size
            if ((x + y) % 2 == 0) Color.WHITE else Color.BLACK
        }
    }

    private fun createGradientPixels(size: Int): IntArray {
        return IntArray(size * size) { i ->
            val x = i % size
            val y = i / size
            val r = (x * 255) / (size - 1)
            val g = (y * 255) / (size - 1)
            val b = ((x + y) * 255) / (2 * (size - 1))
            Color.rgb(r, g, b)
        }
    }
}
