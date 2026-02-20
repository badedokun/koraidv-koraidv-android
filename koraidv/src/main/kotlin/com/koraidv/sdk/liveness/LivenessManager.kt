package com.koraidv.sdk.liveness

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.koraidv.sdk.api.ChallengeType
import com.koraidv.sdk.api.LivenessChallenge
import com.koraidv.sdk.api.LivenessSession
import com.koraidv.sdk.capture.AntiSpoofCheck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Liveness result
 */
data class LivenessResult(
    val passed: Boolean,
    val challenges: List<ChallengeResultItem>,
    val sessionId: String
)

/**
 * Challenge result item
 */
data class ChallengeResultItem(
    val challenge: LivenessChallenge,
    val passed: Boolean,
    val confidence: Double,
    val imageData: ByteArray? = null
)

/**
 * Liveness state
 */
sealed class LivenessState {
    data object Idle : LivenessState()
    data class Countdown(val challenge: LivenessChallenge, val count: Int) : LivenessState()
    data class InProgress(val challenge: LivenessChallenge, val progress: Float) : LivenessState()
    data class ChallengeComplete(val challenge: LivenessChallenge, val passed: Boolean) : LivenessState()
    data class Complete(val result: LivenessResult) : LivenessState()
    data class Error(val message: String) : LivenessState()
}

/**
 * Liveness manager for challenge-response verification
 */
class LivenessManager {

    private val _state = MutableStateFlow<LivenessState>(LivenessState.Idle)
    val state: StateFlow<LivenessState> = _state.asStateFlow()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isTransitioning = false

    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.15f)
        .enableTracking()
        .build()

    private var faceDetector: FaceDetector = FaceDetection.getClient(faceDetectorOptions)
    private val challengeDetector = ChallengeDetector()
    private val antiSpoofCheck = AntiSpoofCheck()

    private var session: LivenessSession? = null
    private var currentChallengeIndex = 0
    private var challengeResults = mutableListOf<ChallengeResultItem>()
    private var isProcessing = false
    private var frameCount = 0
    private val maxFramesPerChallenge = 150

    val currentChallenge: LivenessChallenge?
        get() {
            val session = session ?: return null
            return if (currentChallengeIndex < session.challenges.size) {
                session.challenges[currentChallengeIndex]
            } else null
        }

    /**
     * Start liveness session
     */
    fun start(session: LivenessSession) {
        this.session = session
        this.currentChallengeIndex = 0
        this.challengeResults.clear()
        this.isProcessing = false
        this.frameCount = 0

        // Re-create the face detector in case stop() closed the previous one
        faceDetector = FaceDetection.getClient(faceDetectorOptions)
        challengeDetector.reset()
        startNextChallenge()
    }

    /**
     * Stop liveness session
     */
    fun stop() {
        session = null
        mainHandler.removeCallbacksAndMessages(null)
        isTransitioning = false
        challengeDetector.reset()
        faceDetector.close()
        _state.value = LivenessState.Idle
    }

    /**
     * Process a camera frame
     */
    @androidx.camera.core.ExperimentalGetImage
    fun processFrame(imageProxy: ImageProxy) {
        if (isProcessing || isTransitioning) {
            imageProxy.close()
            return
        }

        val challenge = currentChallenge ?: run {
            imageProxy.close()
            return
        }

        // Enforce per-challenge frame budget
        if (frameCount >= maxFramesPerChallenge) {
            imageProxy.close()
            recordChallengeResult(
                challenge = challenge,
                passed = false,
                confidence = 0.0,
                imageData = null
            )
            return
        }
        frameCount++

        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        isProcessing = true

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces.first()
                    val detectionResult = challengeDetector.process(face, challenge.type)

                    _state.value = LivenessState.InProgress(challenge, detectionResult.progress)

                    if (detectionResult.completed) {
                        // Capture the current frame as JPEG for backend submission
                        val capturedBytes = try {
                            val bitmap = imageProxy.toBitmap()

                            // Run anti-spoof check on captured frame
                            val spoofResult = antiSpoofCheck.analyze(bitmap)
                            if (!spoofResult.isLikelyReal) {
                                bitmap.recycle()
                                recordChallengeResult(
                                    challenge = challenge,
                                    passed = false,
                                    confidence = 0.0,
                                    imageData = null
                                )
                                isProcessing = false
                                return@addOnSuccessListener
                            }

                            val stream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                            bitmap.recycle()
                            stream.toByteArray()
                        } catch (e: Exception) {
                            null
                        }

                        recordChallengeResult(
                            challenge = challenge,
                            passed = true,
                            confidence = detectionResult.confidence.toDouble(),
                            imageData = capturedBytes
                        )
                    }
                }
                isProcessing = false
            }
            .addOnFailureListener { e ->
                val debug = try { com.koraidv.sdk.KoraIDV.getConfiguration().debugLogging } catch (_: Exception) { false }
                if (debug) {
                    android.util.Log.w("KoraIDV", "LivenessManager: ML Kit face detection failed", e)
                }
                isProcessing = false
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun startNextChallenge() {
        val challenge = currentChallenge ?: run {
            completeSession()
            return
        }

        challengeDetector.reset()
        frameCount = 0

        // Start countdown 3 → 2 → 1 → begin detection
        isTransitioning = true
        _state.value = LivenessState.Countdown(challenge, 3)

        mainHandler.postDelayed({
            _state.value = LivenessState.Countdown(challenge, 2)
        }, 1000)

        mainHandler.postDelayed({
            _state.value = LivenessState.Countdown(challenge, 1)
        }, 2000)

        mainHandler.postDelayed({
            isTransitioning = false
            challengeDetector.startDetecting(challenge.type)
            _state.value = LivenessState.InProgress(challenge, 0f)
        }, 3000)
    }

    private fun recordChallengeResult(
        challenge: LivenessChallenge,
        passed: Boolean,
        confidence: Double,
        imageData: ByteArray?
    ) {
        val result = ChallengeResultItem(
            challenge = challenge,
            passed = passed,
            confidence = confidence,
            imageData = imageData
        )
        challengeResults.add(result)

        // Show ChallengeComplete state
        isTransitioning = true
        _state.value = LivenessState.ChallengeComplete(challenge, passed)

        if (passed) {
            // Auto-advance to next challenge after 2.5s
            mainHandler.postDelayed({
                isTransitioning = false
                currentChallengeIndex++
                startNextChallenge()
            }, 2500)
        }
        // If failed, do NOT auto-advance — wait for user to tap "Try Again"
        // (handled by retryCurrentChallenge())
    }

    /**
     * Retry the current challenge after a failure.
     * Called from the UI when the user taps "Try Again" on a failed challenge.
     */
    fun retryCurrentChallenge() {
        // Remove the failed result so it can be re-attempted
        if (challengeResults.isNotEmpty() && !challengeResults.last().passed) {
            challengeResults.removeAt(challengeResults.size - 1)
        }
        isTransitioning = false
        challengeDetector.reset()
        frameCount = 0
        startNextChallenge()
    }

    private fun completeSession() {
        val session = session ?: return

        val allPassed = challengeResults.all { it.passed }

        val result = LivenessResult(
            passed = allPassed,
            challenges = challengeResults.toList(),
            sessionId = session.sessionId
        )

        _state.value = LivenessState.Complete(result)
    }
}
