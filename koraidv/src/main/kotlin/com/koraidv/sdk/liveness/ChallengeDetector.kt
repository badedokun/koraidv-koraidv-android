package com.koraidv.sdk.liveness

import com.google.mlkit.vision.face.Face
import com.koraidv.sdk.api.ChallengeType
import kotlin.math.abs

/**
 * Challenge detection result
 */
data class ChallengeDetectionResult(
    val progress: Float,
    val completed: Boolean,
    val confidence: Float
)

/**
 * Challenge detector for liveness verification
 */
class ChallengeDetector {

    private var currentChallengeType: ChallengeType? = null
    private var frameCount = 0
    private var detectionHistory = mutableListOf<Boolean>()

    private val requiredConsecutiveDetections = 5

    // Thresholds
    private val blinkThreshold = 0.3f
    private val smileThreshold = 0.6f
    private val turnThreshold = 20f // degrees
    private val nodThreshold = 10f // degrees

    // State tracking
    private var blinkState = BlinkState.OPEN
    private var blinkDetected = false
    private var initialYaw: Float? = null
    private var turnDetected = false
    private var initialPitch: Float? = null
    private var nodDetected = false
    private var smileDetected = false

    private enum class BlinkState {
        OPEN, CLOSING, CLOSED, OPENING
    }

    /**
     * Start detecting a specific challenge type
     */
    fun startDetecting(challengeType: ChallengeType) {
        currentChallengeType = challengeType
        reset()
    }

    /**
     * Process a face detection result
     */
    fun process(face: Face, challengeType: ChallengeType): ChallengeDetectionResult {
        frameCount++

        val detected = when (challengeType) {
            ChallengeType.BLINK -> detectBlink(face)
            ChallengeType.SMILE -> detectSmile(face)
            ChallengeType.TURN_LEFT -> detectTurn(face, isLeft = true)
            ChallengeType.TURN_RIGHT -> detectTurn(face, isLeft = false)
            ChallengeType.NOD_UP -> detectNod(face, isUp = true)
            ChallengeType.NOD_DOWN -> detectNod(face, isUp = false)
        }

        detectionHistory.add(detected)

        // Keep only recent history
        if (detectionHistory.size > requiredConsecutiveDetections * 2) {
            detectionHistory.removeAt(0)
        }

        // Check consecutive detections
        val recentDetections = detectionHistory.takeLast(requiredConsecutiveDetections)
        val consecutiveCount = recentDetections.count { it }
        val progress = consecutiveCount.toFloat() / requiredConsecutiveDetections
        val completed = consecutiveCount >= requiredConsecutiveDetections

        return ChallengeDetectionResult(
            progress = progress.coerceIn(0f, 1f),
            completed = completed,
            confidence = face.trackingId?.let { 0.9f } ?: 0.7f
        )
    }

    /**
     * Reset detector state
     */
    fun reset() {
        frameCount = 0
        detectionHistory.clear()
        blinkState = BlinkState.OPEN
        blinkDetected = false
        initialYaw = null
        turnDetected = false
        initialPitch = null
        nodDetected = false
        smileDetected = false
    }

    /**
     * Detect blink
     */
    private fun detectBlink(face: Face): Boolean {
        val leftEyeOpen = face.leftEyeOpenProbability ?: return false
        val rightEyeOpen = face.rightEyeOpenProbability ?: return false
        val avgEyeOpen = (leftEyeOpen + rightEyeOpen) / 2

        when (blinkState) {
            BlinkState.OPEN -> {
                if (avgEyeOpen < blinkThreshold) {
                    blinkState = BlinkState.CLOSING
                }
            }
            BlinkState.CLOSING -> {
                if (avgEyeOpen < blinkThreshold) {
                    blinkState = BlinkState.CLOSED
                } else {
                    blinkState = BlinkState.OPEN
                }
            }
            BlinkState.CLOSED -> {
                if (avgEyeOpen > blinkThreshold) {
                    blinkState = BlinkState.OPENING
                }
            }
            BlinkState.OPENING -> {
                if (avgEyeOpen > 0.5f) {
                    blinkState = BlinkState.OPEN
                    blinkDetected = true
                }
            }
        }

        return blinkDetected
    }

    /**
     * Detect smile
     */
    private fun detectSmile(face: Face): Boolean {
        val smileProbability = face.smilingProbability ?: return false
        smileDetected = smileProbability > smileThreshold
        return smileDetected
    }

    /**
     * Detect head turn
     */
    private fun detectTurn(face: Face, isLeft: Boolean): Boolean {
        val yaw = face.headEulerAngleY

        if (initialYaw == null) {
            initialYaw = yaw
            return false
        }

        val delta = yaw - (initialYaw ?: 0f)

        turnDetected = if (isLeft) {
            delta > turnThreshold
        } else {
            delta < -turnThreshold
        }

        return turnDetected
    }

    /**
     * Detect head nod
     */
    private fun detectNod(face: Face, isUp: Boolean): Boolean {
        val pitch = face.headEulerAngleX

        if (initialPitch == null) {
            initialPitch = pitch
            return false
        }

        val delta = pitch - (initialPitch ?: 0f)

        nodDetected = if (isUp) {
            delta > nodThreshold
        } else {
            delta < -nodThreshold
        }

        return nodDetected
    }
}
