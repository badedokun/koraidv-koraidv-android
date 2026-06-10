package com.koraidv.sdk.liveness

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.Executors
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
    val imageData: ByteArray? = null,
    /** **v1.9.1-rc3** — flat key=value;key=value diagnostic string. */
    val diagnostics: String? = null
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

    /**
     * Background executor for MLKit Task callbacks + JPEG encoding.
     *
     * v1.6.3 perf fix: MLKit's `addOnSuccessListener(listener)` (no
     * executor parameter) defaults to the main thread per Google docs
     * (`docs.gms.tasks.Task`). The previous implementation did
     * `bitmap.compress(JPEG, 95, stream)` inside that callback — on
     * mid-tier Android hardware (Snapdragon 6/7-gen, MediaTek G-series,
     * older Galaxy A-series), encoding a 1080p frame at quality 95
     * costs 200–500ms of UI-thread time, manifesting as the
     * "intermittent freeze" BanffPay QA reported in 2026-05-26 testing.
     * Pixel 9 Pro XL (Tensor G4) was fast enough that the freeze was
     * sub-perceptual, which is why our internal device testing missed
     * it. Routing both the listener AND the JPEG encode onto a single
     * background thread eliminates the freeze on slow devices without
     * changing behaviour on fast ones.
     */
    private val workerExecutor: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "KoraIDV-Liveness-Worker").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }

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
    private var lastDetectedYaw: Float? = null
    private var lastDetectedPitch: Float? = null
    private val maxFramesPerChallenge = 300

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
     * Process a camera frame.
     *
     * REQ-003 FR-003.5 — even while transitioning (countdown / success hold)
     * we still run face detection so [lastDetectedYaw] / [lastDetectedPitch]
     * track the user's pose in real time. That way [startNextChallenge] can
     * snapshot a fresh baseline at the *end* of the countdown rather than
     * inheriting the pose from the previous challenge's last frame.
     * Challenge scoring is still gated on `!isTransitioning`.
     */
    @androidx.camera.core.ExperimentalGetImage
    fun processFrame(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }

        if (isTransitioning) {
            // Pose-only path: update the baseline pose without scoring or
            // advancing state. Lightweight enough to run on every frame.
            updatePoseOnly(imageProxy)
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
            // v1.6.3: explicit background executor — without this, the
            // listener runs on the main thread (MLKit default) and the
            // JPEG encode below freezes the UI on mid-tier devices.
            .addOnSuccessListener(workerExecutor) { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces.first()
                    lastDetectedYaw = face.headEulerAngleY
                    lastDetectedPitch = face.headEulerAngleX
                    val detectionResult = challengeDetector.process(face, challenge.type)

                    _state.value = LivenessState.InProgress(challenge, detectionResult.progress)

                    if (detectionResult.completed) {
                        // Capture the current frame as JPEG for backend submission
                        val capturedBytes = try {
                            val bitmap = imageProxy.toBitmap()

                            // Anti-spoof check is a soft signal — log but do NOT
                            // reject the challenge.  The signal-processing heuristics
                            // (LBP/FFT) produce frequent false positives on real
                            // mobile-camera images.  The backend already treats anti-
                            // spoof as a soft score, not a hard gate.
                            val debug = try { com.koraidv.sdk.KoraIDV.getConfiguration().debugLogging } catch (_: Exception) { false }
                            if (debug) {
                                val spoofResult = antiSpoofCheck.analyze(bitmap)
                                android.util.Log.d("KoraIDV", "LivenessManager: anti-spoof score=${spoofResult.overallScore} isLikelyReal=${spoofResult.isLikelyReal}")
                            }

                            val stream = ByteArrayOutputStream()
                            // v1.6.3: liveness frames are used for face-match
                            // scoring, not human review — q=85 is more than
                            // enough and cuts encode time ~35% vs the previous
                            // q=95. Visual fidelity for the user is unchanged
                            // (they never see the encoded JPEG).
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                            bitmap.recycle()
                            stream.toByteArray()
                        } catch (e: Exception) {
                            null
                        }

                        // **v1.9.1-rc3** — server-side diagnostic. Pack the
                        // last yaw/pitch + baseline + delta + raw face euler
                        // into a flat string and forward to the backend with
                        // this challenge submission. Lets the K team read the
                        // empirical ML Kit sign convention on consumer devices
                        // without depending on adb logcat access. Removed when
                        // the wrong-direction-accept investigation is closed.
                        val diagnostics = buildString {
                            append("rc=v1.9.1-rc4")
                            append(";chType=").append(challenge.type)
                            append(";rawYaw=").append(face.headEulerAngleY)
                            append(";rawPitch=").append(face.headEulerAngleX)
                            append(";rawRoll=").append(face.headEulerAngleZ)
                            append(";baseYaw=").append(challengeDetector.initialYaw)
                            append(";basePitch=").append(challengeDetector.initialPitch)
                            append(";yawDelta=").append(challengeDetector.lastYawDelta)
                            append(";pitchDelta=").append(challengeDetector.lastPitchDelta)
                            append(";frames=").append(frameCount)
                        }

                        recordChallengeResult(
                            challenge = challenge,
                            passed = true,
                            confidence = detectionResult.confidence.toDouble(),
                            imageData = capturedBytes,
                            diagnostics = diagnostics
                        )
                    }
                }
                isProcessing = false
            }
            .addOnFailureListener(workerExecutor) { e ->
                val debug = try { com.koraidv.sdk.KoraIDV.getConfiguration().debugLogging } catch (_: Exception) { false }
                if (debug) {
                    android.util.Log.w("KoraIDV", "LivenessManager: ML Kit face detection failed", e)
                }
                isProcessing = false
            }
            .addOnCompleteListener(workerExecutor) {
                imageProxy.close()
            }
    }

    /**
     * Run face detection on the frame just to refresh [lastDetectedYaw] and
     * [lastDetectedPitch]. Used during transitions (countdown / success hold)
     * so the next challenge can baseline against a fresh pose.
     */
    @androidx.camera.core.ExperimentalGetImage
    private fun updatePoseOnly(imageProxy: ImageProxy) {
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
            // v1.6.3: same off-main routing as the main processFrame
            // path above — even pose-only updates were hitting the UI
            // thread every frame at 30fps.
            .addOnSuccessListener(workerExecutor) { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces.first()
                    lastDetectedYaw = face.headEulerAngleY
                    lastDetectedPitch = face.headEulerAngleX
                }
            }
            .addOnCompleteListener(workerExecutor) {
                isProcessing = false
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

        // REQ-003 FR-003.5 · Pacing. Previous 500ms-per-beat countdown (1500ms
        // total) was too fast to read the instruction and prepare. Industry
        // norm for liveness countdowns is 1s per beat — matches a classic
        // "3, 2, 1" cadence and gives users time to absorb what's next.
        isTransitioning = true
        _state.value = LivenessState.Countdown(challenge, 3)

        mainHandler.postDelayed({
            _state.value = LivenessState.Countdown(challenge, 2)
        }, 1000)

        mainHandler.postDelayed({
            _state.value = LivenessState.Countdown(challenge, 1)
        }, 2000)

        mainHandler.postDelayed({
            // Snapshot the baseline pose at the *end* of the countdown, after
            // the user has had 3s to recover to neutral. updatePoseOnly()
            // refreshes lastDetectedYaw/Pitch on every frame during the
            // countdown, so these are current — not inherited from the last
            // frame of the previous challenge.
            val baselineYaw = lastDetectedYaw
            val baselinePitch = lastDetectedPitch
            isTransitioning = false
            challengeDetector.startDetecting(
                challenge.type,
                baselineYaw = baselineYaw,
                baselinePitch = baselinePitch,
            )
            _state.value = LivenessState.InProgress(challenge, 0f)
        }, 3000)
    }

    private fun recordChallengeResult(
        challenge: LivenessChallenge,
        passed: Boolean,
        confidence: Double,
        imageData: ByteArray?,
        diagnostics: String? = null
    ) {
        val result = ChallengeResultItem(
            challenge = challenge,
            passed = passed,
            confidence = confidence,
            imageData = imageData,
            diagnostics = diagnostics
        )
        challengeResults.add(result)

        // Show ChallengeComplete state
        isTransitioning = true
        _state.value = LivenessState.ChallengeComplete(challenge, passed)

        if (passed) {
            // REQ-003 FR-003.5 · Pacing. 600ms wasn't long enough to notice the
            // ✓ before the next countdown started; users felt rushed and
            // occasionally missed that the challenge had succeeded. 1.4s gives
            // a visible success celebration without feeling sluggish.
            mainHandler.postDelayed({
                isTransitioning = false
                currentChallengeIndex++
                startNextChallenge()
            }, 1400)
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
