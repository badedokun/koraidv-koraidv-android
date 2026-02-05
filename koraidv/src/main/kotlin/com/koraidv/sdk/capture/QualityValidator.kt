package com.koraidv.sdk.capture

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Quality validation result
 */
data class QualityValidationResult(
    val isValid: Boolean,
    val issues: List<QualityIssue>,
    val metrics: QualityMetrics
)

/**
 * Quality issue
 */
data class QualityIssue(
    val type: QualityIssueType,
    val message: String,
    val severity: QualityIssueSeverity
)

/**
 * Quality issue types
 */
enum class QualityIssueType {
    BLUR,
    TOO_DARK,
    TOO_BRIGHT,
    GLARE,
    FACE_NOT_DETECTED,
    FACE_TOO_SMALL,
    FACE_OFF_CENTER,
    MULTIPLE_FACES,
    DOCUMENT_NOT_DETECTED
}

/**
 * Quality issue severity
 */
enum class QualityIssueSeverity {
    ERROR,
    WARNING
}

/**
 * Quality metrics
 */
data class QualityMetrics(
    val blurScore: Double,
    val brightness: Double,
    val glarePercentage: Double,
    val faceSize: Double? = null,
    val faceConfidence: Double? = null
)

/**
 * Quality thresholds
 */
data class QualityThresholds(
    val minBlurScore: Double = 100.0,
    val minBrightness: Double = 0.3,
    val maxBrightness: Double = 0.85,
    val maxGlarePercentage: Double = 0.05,
    val minFaceSizePercentage: Double = 0.2,
    val minFaceConfidence: Double = 0.7
)

/**
 * Quality validator for captured images
 */
class QualityValidator(
    private val thresholds: QualityThresholds = QualityThresholds()
) {

    /**
     * Validate document image quality
     */
    fun validateDocumentImage(bitmap: Bitmap): QualityValidationResult {
        val issues = mutableListOf<QualityIssue>()

        // Calculate blur score
        val blurScore = calculateBlurScore(bitmap)
        if (blurScore < thresholds.minBlurScore) {
            issues.add(
                QualityIssue(
                    type = QualityIssueType.BLUR,
                    message = "Image is too blurry. Hold the device steady.",
                    severity = QualityIssueSeverity.ERROR
                )
            )
        }

        // Calculate brightness
        val brightness = calculateBrightness(bitmap)
        if (brightness < thresholds.minBrightness) {
            issues.add(
                QualityIssue(
                    type = QualityIssueType.TOO_DARK,
                    message = "Image is too dark. Move to a brighter area.",
                    severity = QualityIssueSeverity.ERROR
                )
            )
        } else if (brightness > thresholds.maxBrightness) {
            issues.add(
                QualityIssue(
                    type = QualityIssueType.TOO_BRIGHT,
                    message = "Image is too bright. Reduce lighting.",
                    severity = QualityIssueSeverity.WARNING
                )
            )
        }

        // Calculate glare
        val glarePercentage = calculateGlarePercentage(bitmap)
        if (glarePercentage > thresholds.maxGlarePercentage) {
            issues.add(
                QualityIssue(
                    type = QualityIssueType.GLARE,
                    message = "Glare detected. Adjust angle to reduce reflections.",
                    severity = QualityIssueSeverity.WARNING
                )
            )
        }

        val metrics = QualityMetrics(
            blurScore = blurScore,
            brightness = brightness,
            glarePercentage = glarePercentage
        )

        val hasErrors = issues.any { it.severity == QualityIssueSeverity.ERROR }

        return QualityValidationResult(
            isValid = !hasErrors,
            issues = issues,
            metrics = metrics
        )
    }

    /**
     * Validate selfie image quality with face detection info
     */
    fun validateSelfieImage(
        bitmap: Bitmap,
        faceDetection: FaceDetectionInfo? = null
    ): QualityValidationResult {
        val issues = mutableListOf<QualityIssue>()

        // Basic quality checks
        val blurScore = calculateBlurScore(bitmap)
        if (blurScore < thresholds.minBlurScore) {
            issues.add(
                QualityIssue(
                    type = QualityIssueType.BLUR,
                    message = "Image is too blurry. Hold the device steady.",
                    severity = QualityIssueSeverity.ERROR
                )
            )
        }

        val brightness = calculateBrightness(bitmap)
        if (brightness < thresholds.minBrightness) {
            issues.add(
                QualityIssue(
                    type = QualityIssueType.TOO_DARK,
                    message = "Image is too dark. Move to a brighter area.",
                    severity = QualityIssueSeverity.ERROR
                )
            )
        }

        // Face detection checks
        var faceSize: Double? = null
        var faceConfidence: Double? = null

        if (faceDetection == null) {
            issues.add(
                QualityIssue(
                    type = QualityIssueType.FACE_NOT_DETECTED,
                    message = "Face not detected. Position your face in the frame.",
                    severity = QualityIssueSeverity.ERROR
                )
            )
        } else {
            faceConfidence = faceDetection.confidence.toDouble()

            if (faceDetection.confidence < thresholds.minFaceConfidence) {
                issues.add(
                    QualityIssue(
                        type = QualityIssueType.FACE_NOT_DETECTED,
                        message = "Face not clearly visible. Ensure good lighting.",
                        severity = QualityIssueSeverity.WARNING
                    )
                )
            }

            // Check face size
            val imageArea = bitmap.width * bitmap.height.toFloat()
            val faceArea = faceDetection.boundingBox.width() * faceDetection.boundingBox.height()
            faceSize = (faceArea / imageArea).toDouble()

            if (faceSize < thresholds.minFaceSizePercentage) {
                issues.add(
                    QualityIssue(
                        type = QualityIssueType.FACE_TOO_SMALL,
                        message = "Face is too small. Move closer to the camera.",
                        severity = QualityIssueSeverity.ERROR
                    )
                )
            }

            // Check face centering
            val faceCenterX = faceDetection.boundingBox.centerX() / bitmap.width
            val faceCenterY = faceDetection.boundingBox.centerY() / bitmap.height

            if (abs(faceCenterX - 0.5f) > 0.2f || abs(faceCenterY - 0.5f) > 0.2f) {
                issues.add(
                    QualityIssue(
                        type = QualityIssueType.FACE_OFF_CENTER,
                        message = "Center your face in the frame.",
                        severity = QualityIssueSeverity.WARNING
                    )
                )
            }
        }

        val metrics = QualityMetrics(
            blurScore = blurScore,
            brightness = brightness,
            glarePercentage = calculateGlarePercentage(bitmap),
            faceSize = faceSize,
            faceConfidence = faceConfidence
        )

        val hasErrors = issues.any { it.severity == QualityIssueSeverity.ERROR }

        return QualityValidationResult(
            isValid = !hasErrors,
            issues = issues,
            metrics = metrics
        )
    }

    /**
     * Calculate blur score using Laplacian variance
     */
    private fun calculateBlurScore(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Convert to grayscale
        val grayscale = FloatArray(width * height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            grayscale[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        // Apply Laplacian kernel
        val laplacian = FloatArray(width * height)
        val kernel = intArrayOf(0, 1, 0, 1, -4, 1, 0, 1, 0)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var sum = 0f
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val idx = (y + ky) * width + (x + kx)
                        val kidx = (ky + 1) * 3 + (kx + 1)
                        sum += grayscale[idx] * kernel[kidx]
                    }
                }
                laplacian[y * width + x] = sum
            }
        }

        // Calculate variance
        var mean = 0.0
        for (value in laplacian) {
            mean += value
        }
        mean /= laplacian.size

        var variance = 0.0
        for (value in laplacian) {
            variance += (value - mean).pow(2.0)
        }
        variance /= laplacian.size

        return variance
    }

    /**
     * Calculate average brightness (0-1)
     */
    private fun calculateBrightness(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var totalBrightness = 0.0

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            // Perceived brightness formula
            val brightness = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            totalBrightness += brightness
        }

        return totalBrightness / pixels.size
    }

    /**
     * Calculate percentage of overexposed pixels (glare)
     */
    private fun calculateGlarePercentage(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var glarePixels = 0
        val glareThreshold = 250

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            if (r > glareThreshold && g > glareThreshold && b > glareThreshold) {
                glarePixels++
            }
        }

        return glarePixels.toDouble() / pixels.size
    }
}

/**
 * Face detection info for quality validation
 */
data class FaceDetectionInfo(
    val boundingBox: RectF,
    val confidence: Float
)
