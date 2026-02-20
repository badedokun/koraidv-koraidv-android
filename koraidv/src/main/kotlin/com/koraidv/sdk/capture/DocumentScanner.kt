package com.koraidv.sdk.capture

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Document detection result
 */
data class DocumentDetectionResult(
    val corners: List<PointF>,
    val boundingBox: RectF,
    val confidence: Float,
    val isStable: Boolean,
    val qualityGuidance: String?
)

/**
 * Document scanner using ML Kit Text Recognition for document presence detection.
 * Takes ownership of ImageProxy and closes it after ML Kit processing completes.
 */
class DocumentScanner {

    private var lastCorners: List<PointF>? = null
    private var stabilityCounter = 0
    private var noDetectionCounter = 0
    private val noDetectionThreshold = 3  // 3 consecutive missed frames before reset
    private val stabilityThreshold = 1    // single stable frame suffices (user gets review screen)
    private val stabilityTolerance = 0.10f // 10% tolerance for natural hand movement

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @Volatile
    private var lastDetectionResult: DocumentDetectionResult? = null
    @Volatile
    private var isAnalyzing = false

    /**
     * Detect document in image proxy (for frame analysis).
     * IMPORTANT: This method takes ownership of imageProxy and will close it.
     * The caller must NOT close imageProxy after calling this method.
     * Returns the last known detection result (may be null initially).
     */
    @androidx.camera.core.ExperimentalGetImage
    fun detectDocument(imageProxy: ImageProxy): DocumentDetectionResult? {
        // If already analyzing, skip this frame and close immediately
        if (isAnalyzing) {
            imageProxy.close()
            return lastDetectionResult
        }

        val width = imageProxy.width.toFloat()
        val height = imageProxy.height.toFloat()

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return lastDetectionResult
        }

        isAnalyzing = true

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        textRecognizer.process(inputImage)
            .addOnSuccessListener { text ->
                val blockCount = text.textBlocks.size

                if (blockCount >= 1) {
                    noDetectionCounter = 0
                    // Build bounding box from detected text blocks
                    var minX = width
                    var minY = height
                    var maxX = 0f
                    var maxY = 0f

                    for (block in text.textBlocks) {
                        block.boundingBox?.let { rect ->
                            minX = minOf(minX, rect.left.toFloat())
                            minY = minOf(minY, rect.top.toFloat())
                            maxX = maxOf(maxX, rect.right.toFloat())
                            maxY = maxOf(maxY, rect.bottom.toFloat())
                        }
                    }

                    // For single text block, use center-frame heuristic
                    // assuming ID card aspect ratio 1.586:1 occupying 70% of frame width
                    if (blockCount == 1) {
                        val cardWidth = width * 0.70f
                        val cardHeight = cardWidth / 1.586f
                        val centerX = width / 2f
                        val centerY = height / 2f
                        minX = centerX - cardWidth / 2f
                        maxX = centerX + cardWidth / 2f
                        minY = centerY - cardHeight / 2f
                        maxY = centerY + cardHeight / 2f
                    } else {
                        val padX = (maxX - minX) * 0.1f
                        val padY = (maxY - minY) * 0.1f
                        minX = (minX - padX).coerceAtLeast(0f)
                        minY = (minY - padY).coerceAtLeast(0f)
                        maxX = (maxX + padX).coerceAtMost(width)
                        maxY = (maxY + padY).coerceAtMost(height)
                    }

                    val corners = listOf(
                        PointF(minX, minY),
                        PointF(maxX, minY),
                        PointF(maxX, maxY),
                        PointF(minX, maxY)
                    )

                    val boundingBox = RectF(minX, minY, maxX, maxY)
                    val isStable = checkStability(corners)

                    val textArea = (maxX - minX) * (maxY - minY)
                    val imageArea = width * height
                    val coverage = textArea / imageArea

                    // Lower confidence for single-block fallback
                    val confidence = if (blockCount == 1) {
                        0.55f
                    } else {
                        (0.5f + coverage.coerceAtMost(0.5f)).coerceAtMost(0.99f)
                    }

                    // Compute quality guidance from coverage
                    val qualityGuidance = when {
                        coverage < 0.05f -> "Move closer to the document"
                        coverage > 0.85f -> "Move further from the document"
                        else -> null // All conditions good
                    }

                    lastDetectionResult = DocumentDetectionResult(
                        corners = corners,
                        boundingBox = boundingBox,
                        confidence = confidence,
                        isStable = isStable,
                        qualityGuidance = qualityGuidance
                    )
                } else {
                    noDetectionCounter++
                    if (noDetectionCounter >= noDetectionThreshold) {
                        lastCorners = null
                        stabilityCounter = 0
                        lastDetectionResult = null
                    }
                }
            }
            .addOnFailureListener { e ->
                val debug = try { com.koraidv.sdk.KoraIDV.getConfiguration().debugLogging } catch (_: Exception) { false }
                if (debug) {
                    Log.w("KoraIDV", "DocumentScanner: ML Kit text recognition failed", e)
                }
            }
            .addOnCompleteListener {
                // Always close imageProxy and mark analysis as done
                imageProxy.close()
                isAnalyzing = false
            }

        return lastDetectionResult
    }

    private fun checkStability(corners: List<PointF>): Boolean {
        val last = lastCorners

        if (last == null || last.size != corners.size) {
            lastCorners = corners
            stabilityCounter = 1
            return false
        }

        var isStable = true
        for (i in corners.indices) {
            val dx = Math.abs(corners[i].x - last[i].x) / corners[i].x.coerceAtLeast(1f)
            val dy = Math.abs(corners[i].y - last[i].y) / corners[i].y.coerceAtLeast(1f)

            if (dx > stabilityTolerance || dy > stabilityTolerance) {
                isStable = false
                break
            }
        }

        if (isStable) {
            stabilityCounter++
        } else {
            stabilityCounter = (stabilityCounter - 1).coerceAtLeast(0)
        }

        lastCorners = corners

        return stabilityCounter >= stabilityThreshold
    }

    fun resetStability() {
        lastCorners = null
        stabilityCounter = 0
        noDetectionCounter = 0
        lastDetectionResult = null
    }

    fun close() {
        textRecognizer.close()
    }

    fun extractDocument(bitmap: Bitmap, corners: List<PointF>): Bitmap {
        if (corners.size != 4) return bitmap

        val topLeft = corners[0]
        val topRight = corners[1]
        val bottomRight = corners[2]
        val bottomLeft = corners[3]

        val left = maxOf(0, minOf(topLeft.x.toInt(), bottomLeft.x.toInt()))
        val top = maxOf(0, minOf(topLeft.y.toInt(), topRight.y.toInt()))
        val right = minOf(bitmap.width, maxOf(topRight.x.toInt(), bottomRight.x.toInt()))
        val bottom = minOf(bitmap.height, maxOf(bottomLeft.y.toInt(), bottomRight.y.toInt()))

        val cropWidth = right - left
        val cropHeight = bottom - top

        if (cropWidth <= 0 || cropHeight <= 0) return bitmap

        return Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
    }
}
