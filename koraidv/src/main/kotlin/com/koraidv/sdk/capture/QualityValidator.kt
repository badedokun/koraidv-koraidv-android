package com.koraidv.sdk.capture

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.pow

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
    val minBlurScore: Double = 30.0,  // Relaxed for mobile — 100 was too strict
    val minBrightness: Double = 0.15,
    val maxBrightness: Double = 0.90,
    val maxGlarePercentage: Double = 0.10,
    val minFaceSizePercentage: Double = 0.10,
    val minFaceConfidence: Double = 0.6
)

/**
 * Combined metrics from a single-pass pixel scan.
 * Avoids 3 separate getPixels allocations for blur, brightness, and glare.
 */
private data class PixelMetrics(
    val brightness: Double,
    val glarePercentage: Double,
    val blurScore: Double
)

/**
 * Max dimension for quality analysis. Full-resolution analysis on a 1920x1080 image
 * allocates ~24MB for Laplacian alone. Downsampling to 480px max keeps it under 2MB.
 */
private const val MAX_ANALYSIS_DIMENSION = 480

/**
 * Quality validator for captured images.
 * Downsamples before processing to prevent OOM on low-end devices.
 */
class QualityValidator(
    private val thresholds: QualityThresholds = QualityThresholds()
) {

    /**
     * Validate document image quality
     */
    fun validateDocumentImage(bitmap: Bitmap): QualityValidationResult {
        val issues = mutableListOf<QualityIssue>()

        // Downsample for analysis to reduce memory from ~24MB to ~2MB
        val analysisbitmap = downsample(bitmap)
        val metrics = analyzePixels(analysisbitmap)
        if (analysisbitmap !== bitmap) {
            analysisbitmap.recycle()
        }

        if (metrics.blurScore < thresholds.minBlurScore) {
            issues.add(
                QualityIssue(
                    type = QualityIssueType.BLUR,
                    message = "Image is too blurry. Hold the device steady.",
                    severity = QualityIssueSeverity.ERROR
                )
            )
        }

        if (metrics.brightness < thresholds.minBrightness) {
            issues.add(
                QualityIssue(
                    type = QualityIssueType.TOO_DARK,
                    message = "Image is too dark. Move to a brighter area.",
                    severity = QualityIssueSeverity.ERROR
                )
            )
        } else if (metrics.brightness > thresholds.maxBrightness) {
            issues.add(
                QualityIssue(
                    type = QualityIssueType.TOO_BRIGHT,
                    message = "Image is too bright. Reduce lighting.",
                    severity = QualityIssueSeverity.WARNING
                )
            )
        }

        if (metrics.glarePercentage > thresholds.maxGlarePercentage) {
            issues.add(
                QualityIssue(
                    type = QualityIssueType.GLARE,
                    message = "Glare detected. Adjust angle to reduce reflections.",
                    severity = QualityIssueSeverity.WARNING
                )
            )
        }

        val qualityMetrics = QualityMetrics(
            blurScore = metrics.blurScore,
            brightness = metrics.brightness,
            glarePercentage = metrics.glarePercentage
        )

        val hasErrors = issues.any { it.severity == QualityIssueSeverity.ERROR }

        return QualityValidationResult(
            isValid = !hasErrors,
            issues = issues,
            metrics = qualityMetrics
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

        val analysisBitmap = downsample(bitmap)
        val metrics = analyzePixels(analysisBitmap)
        if (analysisBitmap !== bitmap) {
            analysisBitmap.recycle()
        }

        if (metrics.blurScore < thresholds.minBlurScore) {
            issues.add(
                QualityIssue(
                    type = QualityIssueType.BLUR,
                    message = "Image is too blurry. Hold the device steady.",
                    severity = QualityIssueSeverity.ERROR
                )
            )
        }

        if (metrics.brightness < thresholds.minBrightness) {
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

            // Check face size (use original bitmap dimensions, not downsampled)
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

            // Check face centering (use original bitmap dimensions)
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

        val qualityMetrics = QualityMetrics(
            blurScore = metrics.blurScore,
            brightness = metrics.brightness,
            glarePercentage = metrics.glarePercentage,
            faceSize = faceSize,
            faceConfidence = faceConfidence
        )

        val hasErrors = issues.any { it.severity == QualityIssueSeverity.ERROR }

        return QualityValidationResult(
            isValid = !hasErrors,
            issues = issues,
            metrics = qualityMetrics
        )
    }

    /**
     * Downsample a bitmap so its largest dimension is at most [MAX_ANALYSIS_DIMENSION].
     * Returns the original bitmap if already small enough.
     */
    private fun downsample(bitmap: Bitmap): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        if (maxDim <= MAX_ANALYSIS_DIMENSION) return bitmap

        val scale = MAX_ANALYSIS_DIMENSION.toFloat() / maxDim
        val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Single-pass pixel analysis computing blur, brightness, and glare simultaneously.
     * Reads pixels once into a single IntArray instead of 3 separate allocations.
     */
    private fun analyzePixels(bitmap: Bitmap): PixelMetrics {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height
        val pixels = IntArray(totalPixels)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // === Single pass: convert to grayscale + compute brightness + count glare ===
        val grayscale = FloatArray(totalPixels)
        var totalBrightness = 0.0
        var glarePixels = 0
        val glareThreshold = 250

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            // Grayscale (for blur detection)
            val gray = 0.299f * r + 0.587f * g + 0.114f * b
            grayscale[i] = gray

            // Brightness (perceived luminance, 0-1)
            totalBrightness += gray / 255.0

            // Glare (overexposed pixels)
            if (r > glareThreshold && g > glareThreshold && b > glareThreshold) {
                glarePixels++
            }
        }

        val brightness = totalBrightness / totalPixels
        val glarePercentage = glarePixels.toDouble() / totalPixels

        // === Laplacian variance for blur detection ===
        val kernel = intArrayOf(0, 1, 0, 1, -4, 1, 0, 1, 0)
        var laplacianSum = 0.0
        var laplacianSumSq = 0.0
        var laplacianCount = 0

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
                laplacianSum += sum
                laplacianSumSq += sum.toDouble().pow(2.0)
                laplacianCount++
            }
        }

        val blurScore = if (laplacianCount > 0) {
            val mean = laplacianSum / laplacianCount
            laplacianSumSq / laplacianCount - mean * mean
        } else {
            0.0
        }

        return PixelMetrics(
            brightness = brightness,
            glarePercentage = glarePercentage,
            blurScore = blurScore
        )
    }
}

/**
 * Face detection info for quality validation
 */
data class FaceDetectionInfo(
    val boundingBox: RectF,
    val confidence: Float
)
