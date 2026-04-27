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

    private val requiredConsecutiveDetections = 1

    // Thresholds — tuned for responsive detection on mobile front cameras
    private val blinkThreshold = 0.3f
    private val smileThreshold = 0.35f
    private val turnThreshold = 10f // degrees — visible head turn, relaxed for front-camera jitter
    private val nodThreshold = 5f // degrees

    // State tracking
    private var blinkState = BlinkState.OPEN
    private var blinkDetected = false
    private var initialYaw: Float? = null
    private var turnDetected = false
    private var maxTurnDelta = 0f
    private var turnBaselineFrames = 0
    private var turnBaselineSum = 0f
    private var turnBaselineCount = 0
    private var initialPitch: Float? = null
    private var nodDetected = false
    private var nodBaselineFrames = 0
    private var nodBaselineSum = 0f
    private var nodBaselineCount = 0
    private var smileDetected = false

    private enum class BlinkState {
        OPEN, CLOSING, CLOSED, OPENING
    }

    /**
     * Start detecting a specific challenge type.
     *
     * @param baselineYaw   optional pre-captured yaw angle (degrees) — supplied
     *                      by [LivenessManager] right at the end of the countdown
     *                      so subsequent yaw deltas reflect movement *from the
     *                      moment "Go" appears*, not from the user's pose at
     *                      the previous challenge's end.
     * @param baselinePitch optional pre-captured pitch angle (degrees) — same
     *                      treatment for NOD_UP / NOD_DOWN. Without this, the
     *                      detector accumulated frames 3–5 to derive a baseline,
     *                      which biased the "look up" check when the user had
     *                      already started moving during the countdown.
     */
    fun startDetecting(
        challengeType: ChallengeType,
        baselineYaw: Float? = null,
        baselinePitch: Float? = null,
    ) {
        currentChallengeType = challengeType
        reset()
        if (baselineYaw != null) {
            initialYaw = baselineYaw
        }
        if (baselinePitch != null) {
            initialPitch = baselinePitch
        }
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
        maxTurnDelta = 0f
        turnBaselineFrames = 0
        turnBaselineSum = 0f
        turnBaselineCount = 0
        initialPitch = null
        nodDetected = false
        nodBaselineFrames = 0
        nodBaselineSum = 0f
        nodBaselineCount = 0
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
     * Detect head turn.
     *
     * When a pre-countdown baseline is available (initialYaw already set via
     * startDetecting), we skip the frame-accumulation phase and immediately
     * compare against the known-good baseline.  This avoids the baseline-drift
     * problem where the user starts turning during the countdown.
     *
     * We track the maximum directional delta seen so far (maxTurnDelta) so that
     * a momentary jitter in ML Kit's yaw estimate doesn't cause a false negative
     * after the user has already turned far enough.
     */
    private fun detectTurn(face: Face, isLeft: Boolean): Boolean {
        val yaw = face.headEulerAngleY

        // Fallback baseline: if no pre-countdown baseline was provided, accumulate
        // from frames 3-5 (original behaviour).
        if (initialYaw == null) {
            turnBaselineFrames++
            if (turnBaselineFrames < 3) return false // skip first 3 frames
            turnBaselineSum += yaw
            turnBaselineCount++
            if (turnBaselineFrames < 6) return false // accumulate frames 3-5
            initialYaw = turnBaselineSum / turnBaselineCount
            return false
        }

        val delta = yaw - (initialYaw ?: 0f)

        // ML Kit headEulerAngleY: positive = face rotated left from CAMERA's perspective,
        // which is the USER's RIGHT on a front camera. So for the user to turn LEFT,
        // the delta is negative; for the user to turn RIGHT, the delta is positive.
        val directionalDelta = if (isLeft) -delta else delta
        if (directionalDelta > maxTurnDelta) {
            maxTurnDelta = directionalDelta
        }

        turnDetected = maxTurnDelta > turnThreshold
        return turnDetected
    }

    /**
     * Detect head nod
     */
    private fun detectNod(face: Face, isUp: Boolean): Boolean {
        val pitch = face.headEulerAngleX

        // Skip the first few frames to let the user settle, same as turn detection
        if (initialPitch == null) {
            nodBaselineFrames++
            if (nodBaselineFrames < 3) return false
            nodBaselineSum += pitch
            nodBaselineCount++
            if (nodBaselineFrames < 6) return false
            initialPitch = nodBaselineSum / nodBaselineCount
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
