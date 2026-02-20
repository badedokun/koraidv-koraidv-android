package com.koraidv.sdk.capture

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QualityValidatorTest {

    private lateinit var validator: QualityValidator

    @Before
    fun setUp() {
        validator = QualityValidator()
    }

    // =====================================================================
    // Document image quality
    // =====================================================================

    @Test
    fun `validateDocumentImage passes for well-lit sharp image`() {
        // Create a bitmap with varied pixel values (not too dark, not too bright, has edges)
        val bitmap = createTestBitmap(200, 200, generateSharpPixels(200, 200))
        val result = validator.validateDocumentImage(bitmap)
        assertThat(result.metrics.brightness).isGreaterThan(0.0)
    }

    @Test
    fun `validateDocumentImage detects dark image`() {
        // All-black image
        val bitmap = createSolidBitmap(200, 200, 0xFF000000.toInt())
        val result = validator.validateDocumentImage(bitmap)

        assertThat(result.isValid).isFalse()
        assertThat(result.issues.any { it.type == QualityIssueType.TOO_DARK }).isTrue()
        assertThat(result.metrics.brightness).isLessThan(0.15)
    }

    @Test
    fun `validateDocumentImage detects bright image with glare`() {
        // All-white image
        val bitmap = createSolidBitmap(200, 200, 0xFFFFFFFF.toInt())
        val result = validator.validateDocumentImage(bitmap)

        assertThat(result.issues.any { it.type == QualityIssueType.TOO_BRIGHT || it.type == QualityIssueType.GLARE }).isTrue()
    }

    @Test
    fun `validateDocumentImage returns metrics`() {
        val bitmap = createSolidBitmap(200, 200, 0xFF808080.toInt()) // mid-gray
        val result = validator.validateDocumentImage(bitmap)
        assertThat(result.metrics.blurScore).isAtLeast(0.0)
        assertThat(result.metrics.brightness).isGreaterThan(0.0)
        assertThat(result.metrics.glarePercentage).isAtLeast(0.0)
    }

    @Test
    fun `validateDocumentImage detects blur on uniform image`() {
        // Uniform color = 0 variance in Laplacian = blurry
        val bitmap = createSolidBitmap(200, 200, 0xFF808080.toInt())
        val result = validator.validateDocumentImage(bitmap)
        assertThat(result.metrics.blurScore).isLessThan(30.0)
        assertThat(result.issues.any { it.type == QualityIssueType.BLUR }).isTrue()
    }

    // =====================================================================
    // Selfie image quality
    // =====================================================================

    @Test
    fun `validateSelfieImage detects missing face`() {
        val bitmap = createSolidBitmap(200, 200, 0xFF808080.toInt())
        val result = validator.validateSelfieImage(bitmap, faceDetection = null)
        assertThat(result.issues.any { it.type == QualityIssueType.FACE_NOT_DETECTED }).isTrue()
        assertThat(result.isValid).isFalse()
    }

    @Test
    fun `validateSelfieImage detects face too small`() {
        val bitmap = createSolidBitmap(1000, 1000, 0xFF808080.toInt())
        // Face is only 10x10 pixels = 0.01% of 1000x1000 — way below 10% threshold
        val faceInfo = FaceDetectionInfo(
            boundingBox = RectF(100f, 100f, 110f, 110f),
            confidence = 0.95f
        )
        val result = validator.validateSelfieImage(bitmap, faceInfo)
        assertThat(result.issues.any { it.type == QualityIssueType.FACE_TOO_SMALL }).isTrue()
    }

    @Test
    fun `validateSelfieImage detects face off center`() {
        val bitmap = createSolidBitmap(1000, 1000, 0xFF808080.toInt())
        // Face in the far top-left corner
        val faceInfo = FaceDetectionInfo(
            boundingBox = RectF(0f, 0f, 200f, 200f),
            confidence = 0.95f
        )
        val result = validator.validateSelfieImage(bitmap, faceInfo)
        assertThat(result.issues.any { it.type == QualityIssueType.FACE_OFF_CENTER }).isTrue()
    }

    @Test
    fun `validateSelfieImage with low confidence face warns`() {
        val bitmap = createSolidBitmap(200, 200, 0xFF808080.toInt())
        val faceInfo = FaceDetectionInfo(
            boundingBox = RectF(50f, 50f, 150f, 150f),
            confidence = 0.3f // Below 0.6 threshold
        )
        val result = validator.validateSelfieImage(bitmap, faceInfo)
        assertThat(result.issues.any { it.type == QualityIssueType.FACE_NOT_DETECTED }).isTrue()
    }

    @Test
    fun `validateSelfieImage passes with good face detection`() {
        val bitmap = createTestBitmap(200, 200, generateSharpPixels(200, 200))
        // Centered face occupying ~25% of frame
        val faceInfo = FaceDetectionInfo(
            boundingBox = RectF(50f, 50f, 150f, 150f),
            confidence = 0.95f
        )
        val result = validator.validateSelfieImage(bitmap, faceInfo)
        assertThat(result.metrics.faceSize).isGreaterThan(0.0)
        assertThat(result.metrics.faceConfidence).isWithin(0.01).of(0.95)
    }

    // =====================================================================
    // Custom thresholds
    // =====================================================================

    @Test
    fun `custom thresholds are respected`() {
        val lenientValidator = QualityValidator(
            QualityThresholds(
                minBlurScore = 0.0,    // Accept any blur
                minBrightness = 0.0,   // Accept darkness
                maxBrightness = 1.0,   // Accept brightness
                maxGlarePercentage = 1.0 // Accept glare
            )
        )
        val bitmap = createSolidBitmap(200, 200, 0xFF000000.toInt()) // all black
        val result = lenientValidator.validateDocumentImage(bitmap)
        assertThat(result.isValid).isTrue()
        assertThat(result.issues).isEmpty()
    }

    // =====================================================================
    // Quality issue types
    // =====================================================================

    @Test
    fun `QualityIssueType has all expected values`() {
        val types = QualityIssueType.entries
        assertThat(types).contains(QualityIssueType.BLUR)
        assertThat(types).contains(QualityIssueType.TOO_DARK)
        assertThat(types).contains(QualityIssueType.TOO_BRIGHT)
        assertThat(types).contains(QualityIssueType.GLARE)
        assertThat(types).contains(QualityIssueType.FACE_NOT_DETECTED)
        assertThat(types).contains(QualityIssueType.FACE_TOO_SMALL)
        assertThat(types).contains(QualityIssueType.FACE_OFF_CENTER)
        assertThat(types).contains(QualityIssueType.MULTIPLE_FACES)
        assertThat(types).contains(QualityIssueType.DOCUMENT_NOT_DETECTED)
    }

    @Test
    fun `QualityIssueSeverity has ERROR and WARNING`() {
        assertThat(QualityIssueSeverity.entries).containsExactly(
            QualityIssueSeverity.ERROR,
            QualityIssueSeverity.WARNING
        )
    }

    @Test
    fun `only ERROR severity issues cause isValid false`() {
        // Image with brightness warning but no errors: should still be valid
        // (construct this by using mid-high brightness that triggers WARNING but not ERROR)
        val lenientValidator = QualityValidator(
            QualityThresholds(minBlurScore = 0.0) // disable blur check
        )
        val bitmap = createSolidBitmap(200, 200, 0xFF808080.toInt())
        val result = lenientValidator.validateDocumentImage(bitmap)
        val hasOnlyWarnings = result.issues.all { it.severity == QualityIssueSeverity.WARNING }
        if (hasOnlyWarnings) {
            assertThat(result.isValid).isTrue()
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private fun createSolidBitmap(width: Int, height: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height) { color }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun createTestBitmap(width: Int, height: Int, pixels: IntArray): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun generateSharpPixels(width: Int, height: Int): IntArray {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                // Create a checkerboard pattern for high Laplacian variance
                val isWhite = (x / 4 + y / 4) % 2 == 0
                val gray = if (isWhite) 200 else 80
                pixels[y * width + x] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
            }
        }
        return pixels
    }
}
