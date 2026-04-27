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
    // Preview-image dimensions of the most recent successful detection.
    // Used by getLastBoundsFractional() to express the bounds in 0..1
    // space so callers can crop the higher-resolution capture bitmap.
    @Volatile
    private var lastImageWidth = 0f
    @Volatile
    private var lastImageHeight = 0f
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

                    // Use the actual text bounding box across all blocks +
                    // 10 % padding. This applies equally to single-block
                    // and multi-block detections.
                    //
                    // The previous single-block branch overwrote these
                    // bounds with a synthesised 70%-wide centred box —
                    // which made coverage read a constant ~41 % regardless
                    // of how small the card actually was in the frame.
                    // That defeated the coverage-based "Move closer" guard
                    // and let auto-capture fire on far-away cards.
                    val padX = (maxX - minX) * 0.1f
                    val padY = (maxY - minY) * 0.1f
                    minX = (minX - padX).coerceAtLeast(0f)
                    minY = (minY - padY).coerceAtLeast(0f)
                    maxX = (maxX + padX).coerceAtMost(width)
                    maxY = (maxY + padY).coerceAtMost(height)

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

                    // REQ-003 FR-003.4 · Coverage thresholds dictate when
                    // auto-capture is allowed. Below 35 % the document is
                    // too small to fill the review frame (user needs to
                    // move closer); above 85 % the document is likely
                    // clipped. Previously the lower bound was 5 %, which
                    // let auto-capture fire on a tiny card and produced
                    // the "lots of desk visible in review" screenshots.
                    val qualityGuidance = when {
                        coverage < 0.35f -> "Move closer to the document"
                        coverage > 0.85f -> "Move further from the document"
                        else -> null // All conditions good
                    }

                    lastImageWidth = width
                    lastImageHeight = height
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

    /**
     * Returns the last detection bounding box in 0..1 coordinates, relative
     * to the analysis-image dimensions, or null if there is no detection yet.
     *
     * REQ-003 FR-003.4 — the capture bitmap and the analysis stream share an
     * aspect ratio (both derive from the same CameraX use case), so these
     * fractions map correctly onto the higher-resolution capture by scaling
     * with the capture dimensions. The caller is responsible for clamping
     * the result into the bitmap bounds.
     */
    fun getLastBoundsFractional(): RectF? {
        val w = lastImageWidth
        val h = lastImageHeight
        val box = lastDetectionResult?.boundingBox
        if (w <= 0f || h <= 0f || box == null) return null
        return RectF(
            (box.left / w).coerceIn(0f, 1f),
            (box.top / h).coerceIn(0f, 1f),
            (box.right / w).coerceIn(0f, 1f),
            (box.bottom / h).coerceIn(0f, 1f),
        )
    }

    /**
     * Produces a tight-cropped bitmap around the most recent detection,
     * with a small padding. Falls back to a centered ID-1-aspect crop
     * when no detection is available — the viewfinder already funnels
     * the user toward a centered document.
     *
     * REQ-003 FR-003.4 · "Captured document images must scale to fill the
     * review frame regardless of original capture size. No empty space
     * around the document in the review screen."
     */
    fun cropToDocument(bitmap: Bitmap, @Suppress("UNUSED_PARAMETER") paddingFraction: Float = 0.05f): Bitmap {
        // Why not use getLastBoundsFractional()? The ML Kit analysis stream
        // and the capture stream can arrive in different orientations on
        // CameraX (analysis = sensor orientation, capture = device-display
        // orientation), so fractions computed on one don't translate
        // cleanly onto the other without a rotation-aware transform. The
        // symptom was a crop strip that cut the card in half horizontally.
        //
        // Pragmatic path: the viewfinder already enforces a centred,
        // horizontally-oriented document at ID-1 (1.586:1). A centred
        // crop at that ratio, sized to the shorter dimension, reliably
        // frames the card regardless of the sensor's orientation.
        val bmpW = bitmap.width
        val bmpH = bitmap.height
        val landscape = bmpW >= bmpH

        // Pick the dominant orientation for the crop — horizontal ID card.
        val targetAspect = 1.586f
        val targetW: Int
        val targetH: Int
        if (landscape) {
            // Source already horizontal. Use ~95% of height → width = h * aspect.
            targetH = (bmpH * 0.95f).toInt()
            targetW = (targetH * targetAspect).toInt().coerceAtMost(bmpW)
        } else {
            // Source portrait. The card lives horizontally inside, centred.
            // Use ~95% of width → height = w / aspect.
            targetW = (bmpW * 0.95f).toInt()
            targetH = (targetW / targetAspect).toInt().coerceAtMost(bmpH)
        }
        val left = ((bmpW - targetW) / 2).coerceAtLeast(0)
        val top = ((bmpH - targetH) / 2).coerceAtLeast(0)
        val width = targetW.coerceAtMost(bmpW - left).coerceAtLeast(1)
        val height = targetH.coerceAtMost(bmpH - top).coerceAtLeast(1)
        return try {
            Bitmap.createBitmap(bitmap, left, top, width, height)
        } catch (_: IllegalArgumentException) {
            bitmap
        }
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
