package com.koraidv.sdk.capture

import android.graphics.PointF
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * Face scan result for selfie capture guidance.
 */
data class FaceScanResult(
    val faceDetected: Boolean,
    val boundingBox: RectF?,
    val isCentered: Boolean,
    val isSizedCorrectly: Boolean,
    val isStable: Boolean,
    val guidanceMessage: String?
)

/**
 * Face scanner using ML Kit Face Detection for real-time selfie capture guidance.
 * Takes ownership of ImageProxy and closes it after ML Kit processing completes.
 */
class FaceScanner {

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    @Volatile
    private var lastResult: FaceScanResult? = null
    @Volatile
    private var isAnalyzing = false

    private var lastCenter: PointF? = null
    private var stabilityCounter = 0
    private val stabilityThreshold = 10 // ~0.33s at 30fps
    private val stabilityTolerance = 0.03f

    // Face sizing: 8-55% of frame area
    private val minFaceFraction = 0.08f
    private val maxFaceFraction = 0.55f
    // Face centering: within 20% of center
    private val centerTolerance = 0.20f

    /**
     * Detect face in image proxy for real-time guidance.
     * Takes ownership of imageProxy and closes it.
     */
    @androidx.camera.core.ExperimentalGetImage
    fun detectFace(imageProxy: ImageProxy): FaceScanResult? {
        if (isAnalyzing) {
            imageProxy.close()
            return lastResult
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return lastResult
        }

        val width = imageProxy.width.toFloat()
        val height = imageProxy.height.toFloat()

        isAnalyzing = true

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    resetStability()
                    lastResult = FaceScanResult(
                        faceDetected = false,
                        boundingBox = null,
                        isCentered = false,
                        isSizedCorrectly = false,
                        isStable = false,
                        guidanceMessage = "Position your face in the oval"
                    )
                    return@addOnSuccessListener
                }

                if (faces.size > 1) {
                    resetStability()
                    lastResult = FaceScanResult(
                        faceDetected = true,
                        boundingBox = null,
                        isCentered = false,
                        isSizedCorrectly = false,
                        isStable = false,
                        guidanceMessage = "Only one face should be visible"
                    )
                    return@addOnSuccessListener
                }

                val face = faces[0]
                val bounds = face.boundingBox
                val boundingBox = RectF(
                    bounds.left.toFloat(),
                    bounds.top.toFloat(),
                    bounds.right.toFloat(),
                    bounds.bottom.toFloat()
                )

                val imageArea = width * height
                val faceArea = boundingBox.width() * boundingBox.height()
                val faceFraction = faceArea / imageArea

                val faceCenterX = boundingBox.centerX() / width
                val faceCenterY = boundingBox.centerY() / height

                val isCentered = kotlin.math.abs(faceCenterX - 0.5f) <= centerTolerance &&
                        kotlin.math.abs(faceCenterY - 0.5f) <= centerTolerance
                val isSizedCorrectly = faceFraction in minFaceFraction..maxFaceFraction
                val isStable = checkStability(PointF(boundingBox.centerX(), boundingBox.centerY()))

                val guidanceMessage = when {
                    faceFraction < minFaceFraction -> "Move closer"
                    faceFraction > maxFaceFraction -> "Move further away"
                    !isCentered -> "Center your face"
                    !isStable -> "Hold steady..."
                    else -> null // All conditions met
                }

                lastResult = FaceScanResult(
                    faceDetected = true,
                    boundingBox = boundingBox,
                    isCentered = isCentered,
                    isSizedCorrectly = isSizedCorrectly,
                    isStable = isStable,
                    guidanceMessage = guidanceMessage
                )
            }
            .addOnFailureListener {
                // ML Kit processing failed
            }
            .addOnCompleteListener {
                imageProxy.close()
                isAnalyzing = false
            }

        return lastResult
    }

    private fun checkStability(center: PointF): Boolean {
        val last = lastCenter
        if (last == null) {
            lastCenter = center
            stabilityCounter = 1
            return false
        }

        val dx = kotlin.math.abs(center.x - last.x) / center.x.coerceAtLeast(1f)
        val dy = kotlin.math.abs(center.y - last.y) / center.y.coerceAtLeast(1f)

        if (dx <= stabilityTolerance && dy <= stabilityTolerance) {
            stabilityCounter++
        } else {
            stabilityCounter = 1
        }

        lastCenter = center
        return stabilityCounter >= stabilityThreshold
    }

    fun resetStability() {
        lastCenter = null
        stabilityCounter = 0
        lastResult = null
    }

    fun close() {
        faceDetector.close()
    }
}
