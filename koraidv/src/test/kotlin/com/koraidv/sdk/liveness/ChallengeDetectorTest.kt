package com.koraidv.sdk.liveness

import com.google.common.truth.Truth.assertThat
import com.google.mlkit.vision.face.Face
import com.koraidv.sdk.api.ChallengeType
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

class ChallengeDetectorTest {

    private lateinit var detector: ChallengeDetector

    @Before
    fun setUp() {
        detector = ChallengeDetector()
    }

    // =====================================================================
    // Smile detection
    // =====================================================================

    @Test
    fun `smile detected when probability above threshold`() {
        val face = mockFace(smilingProbability = 0.8f)
        detector.startDetecting(ChallengeType.SMILE)

        // Need 3 consecutive detections
        val r1 = detector.process(face, ChallengeType.SMILE)
        val r2 = detector.process(face, ChallengeType.SMILE)
        val r3 = detector.process(face, ChallengeType.SMILE)

        assertThat(r3.completed).isTrue()
        assertThat(r3.progress).isEqualTo(1.0f)
    }

    @Test
    fun `smile not detected when probability below threshold`() {
        val face = mockFace(smilingProbability = 0.2f) // Below 0.5 threshold
        detector.startDetecting(ChallengeType.SMILE)

        val result = detector.process(face, ChallengeType.SMILE)
        assertThat(result.completed).isFalse()
    }

    @Test
    fun `smile detection returns null probability gracefully`() {
        val face = mockFace(smilingProbability = null)
        detector.startDetecting(ChallengeType.SMILE)

        val result = detector.process(face, ChallengeType.SMILE)
        assertThat(result.completed).isFalse()
    }

    // =====================================================================
    // Blink detection
    // =====================================================================

    @Test
    fun `blink detected through full cycle`() {
        detector.startDetecting(ChallengeType.BLINK)

        // Open → Closing → Closed → Opening → Open (blink detected)
        val openFace = mockFace(leftEyeOpen = 0.9f, rightEyeOpen = 0.9f)
        val closedFace = mockFace(leftEyeOpen = 0.1f, rightEyeOpen = 0.1f)

        // Start open
        detector.process(openFace, ChallengeType.BLINK)
        // Eyes close
        detector.process(closedFace, ChallengeType.BLINK)
        detector.process(closedFace, ChallengeType.BLINK)
        // Eyes open again
        detector.process(openFace, ChallengeType.BLINK)
        // Repeat for consecutive detections
        val r = detector.process(openFace, ChallengeType.BLINK)

        // After blink cycle, blinkDetected stays true
        assertThat(r.progress).isGreaterThan(0f)
    }

    @Test
    fun `blink not detected with eyes always open`() {
        val face = mockFace(leftEyeOpen = 0.9f, rightEyeOpen = 0.9f)
        detector.startDetecting(ChallengeType.BLINK)

        repeat(5) { detector.process(face, ChallengeType.BLINK) }

        val result = detector.process(face, ChallengeType.BLINK)
        assertThat(result.completed).isFalse()
    }

    @Test
    fun `blink returns false for null eye probabilities`() {
        val face = mockFace(leftEyeOpen = null, rightEyeOpen = null)
        detector.startDetecting(ChallengeType.BLINK)

        val result = detector.process(face, ChallengeType.BLINK)
        assertThat(result.completed).isFalse()
    }

    // =====================================================================
    // Turn detection
    // =====================================================================

    @Test
    fun `turn left detected when yaw decreases enough`() {
        detector.startDetecting(ChallengeType.TURN_LEFT)

        // Build baseline (6 frames needed: skip 3, average frames 3-5)
        val straightFace = mockFace(headEulerAngleY = 0f)
        repeat(6) { detector.process(straightFace, ChallengeType.TURN_LEFT) }

        // Face turns left (negative delta for user's left on front camera)
        val turnedFace = mockFace(headEulerAngleY = -20f)
        val result = detector.process(turnedFace, ChallengeType.TURN_LEFT)

        assertThat(result.completed).isTrue()
    }

    @Test
    fun `turn right detected when yaw increases enough`() {
        detector.startDetecting(ChallengeType.TURN_RIGHT)

        val straightFace = mockFace(headEulerAngleY = 0f)
        repeat(6) { detector.process(straightFace, ChallengeType.TURN_RIGHT) }

        val turnedFace = mockFace(headEulerAngleY = 20f)
        val result = detector.process(turnedFace, ChallengeType.TURN_RIGHT)

        assertThat(result.completed).isTrue()
    }

    @Test
    fun `turn not detected for small head movement`() {
        detector.startDetecting(ChallengeType.TURN_LEFT)

        val straightFace = mockFace(headEulerAngleY = 0f)
        repeat(6) { detector.process(straightFace, ChallengeType.TURN_LEFT) }

        val slightTurn = mockFace(headEulerAngleY = -10f) // Below 15 degree threshold
        repeat(5) { detector.process(slightTurn, ChallengeType.TURN_LEFT) }

        val result = detector.process(slightTurn, ChallengeType.TURN_LEFT)
        assertThat(result.completed).isFalse()
    }

    // =====================================================================
    // Nod detection
    // =====================================================================

    @Test
    fun `nod up detected when pitch increases enough`() {
        detector.startDetecting(ChallengeType.NOD_UP)

        val straightFace = mockFace(headEulerAngleX = 0f)
        repeat(6) { detector.process(straightFace, ChallengeType.NOD_UP) }

        val nodUpFace = mockFace(headEulerAngleX = 10f)
        val result = detector.process(nodUpFace, ChallengeType.NOD_UP)

        assertThat(result.completed).isTrue()
    }

    @Test
    fun `nod down detected when pitch decreases enough`() {
        detector.startDetecting(ChallengeType.NOD_DOWN)

        val straightFace = mockFace(headEulerAngleX = 0f)
        repeat(6) { detector.process(straightFace, ChallengeType.NOD_DOWN) }

        val nodDownFace = mockFace(headEulerAngleX = -10f)
        val result = detector.process(nodDownFace, ChallengeType.NOD_DOWN)

        assertThat(result.completed).isTrue()
    }

    // =====================================================================
    // Reset
    // =====================================================================

    @Test
    fun `reset clears all state`() {
        val smilingFace = mockFace(smilingProbability = 0.8f)
        detector.startDetecting(ChallengeType.SMILE)
        val before = detector.process(smilingFace, ChallengeType.SMILE)
        assertThat(before.completed).isTrue()

        detector.reset()

        // After reset, a non-smiling face should not complete
        val neutralFace = mockFace(smilingProbability = 0.1f)
        val result = detector.process(neutralFace, ChallengeType.SMILE)
        assertThat(result.completed).isFalse()
    }

    @Test
    fun `startDetecting resets state`() {
        val face = mockFace(smilingProbability = 0.8f)
        detector.startDetecting(ChallengeType.SMILE)
        detector.process(face, ChallengeType.SMILE)
        detector.process(face, ChallengeType.SMILE)

        detector.startDetecting(ChallengeType.BLINK) // New challenge resets

        val blinkFace = mockFace(leftEyeOpen = 0.9f, rightEyeOpen = 0.9f)
        val result = detector.process(blinkFace, ChallengeType.BLINK)
        assertThat(result.completed).isFalse()
    }

    // =====================================================================
    // Confidence
    // =====================================================================

    @Test
    fun `confidence is 0_9 when face has tracking ID`() {
        val face = mockFace(smilingProbability = 0.8f, trackingId = 42)
        val result = detector.process(face, ChallengeType.SMILE)
        assertThat(result.confidence).isEqualTo(0.9f)
    }

    @Test
    fun `confidence is 0_7 when face has no tracking ID`() {
        val face = mockFace(smilingProbability = 0.8f, trackingId = null)
        val result = detector.process(face, ChallengeType.SMILE)
        assertThat(result.confidence).isEqualTo(0.7f)
    }

    // =====================================================================
    // Progress
    // =====================================================================

    @Test
    fun `progress is clamped between 0 and 1`() {
        val face = mockFace(smilingProbability = 0.8f)
        detector.startDetecting(ChallengeType.SMILE)

        val result = detector.process(face, ChallengeType.SMILE)
        assertThat(result.progress).isAtLeast(0f)
        assertThat(result.progress).isAtMost(1f)
    }

    // =====================================================================
    // Detection history management
    // =====================================================================

    @Test
    fun `detection history is bounded`() {
        val face = mockFace(smilingProbability = 0.8f)
        detector.startDetecting(ChallengeType.SMILE)

        // Process many frames
        repeat(20) {
            detector.process(face, ChallengeType.SMILE)
        }

        // Should still work without OOM
        val result = detector.process(face, ChallengeType.SMILE)
        assertThat(result.completed).isTrue()
    }

    // =====================================================================
    // Wrong direction detection
    // =====================================================================

    @Test
    fun `turn left not detected when turning right`() {
        detector.startDetecting(ChallengeType.TURN_LEFT)
        val straightFace = mockFace(headEulerAngleY = 0f)
        repeat(6) { detector.process(straightFace, ChallengeType.TURN_LEFT) }

        val wrongDirection = mockFace(headEulerAngleY = 20f)
        repeat(3) { detector.process(wrongDirection, ChallengeType.TURN_LEFT) }
        val result = detector.process(wrongDirection, ChallengeType.TURN_LEFT)
        assertThat(result.completed).isFalse()
    }

    @Test
    fun `turn right not detected when turning left`() {
        detector.startDetecting(ChallengeType.TURN_RIGHT)
        val straightFace = mockFace(headEulerAngleY = 0f)
        repeat(6) { detector.process(straightFace, ChallengeType.TURN_RIGHT) }

        val wrongDirection = mockFace(headEulerAngleY = -20f)
        repeat(3) { detector.process(wrongDirection, ChallengeType.TURN_RIGHT) }
        val result = detector.process(wrongDirection, ChallengeType.TURN_RIGHT)
        assertThat(result.completed).isFalse()
    }

    @Test
    fun `nod up not detected when nodding down`() {
        detector.startDetecting(ChallengeType.NOD_UP)
        val straightFace = mockFace(headEulerAngleX = 0f)
        repeat(6) { detector.process(straightFace, ChallengeType.NOD_UP) }

        val nodDown = mockFace(headEulerAngleX = -10f)
        repeat(3) { detector.process(nodDown, ChallengeType.NOD_UP) }
        val result = detector.process(nodDown, ChallengeType.NOD_UP)
        assertThat(result.completed).isFalse()
    }

    @Test
    fun `nod down not detected when nodding up`() {
        detector.startDetecting(ChallengeType.NOD_DOWN)
        val straightFace = mockFace(headEulerAngleX = 0f)
        repeat(6) { detector.process(straightFace, ChallengeType.NOD_DOWN) }

        val nodUp = mockFace(headEulerAngleX = 10f)
        repeat(3) { detector.process(nodUp, ChallengeType.NOD_DOWN) }
        val result = detector.process(nodUp, ChallengeType.NOD_DOWN)
        assertThat(result.completed).isFalse()
    }

    // =====================================================================
    // Pre-captured baseline
    // =====================================================================

    @Test
    fun `turn left detected immediately with pre-captured baseline`() {
        detector.startDetecting(ChallengeType.TURN_LEFT, baselineYaw = 0f)

        // No baseline frames needed — should detect on first frame
        val turnedFace = mockFace(headEulerAngleY = -20f)
        val result = detector.process(turnedFace, ChallengeType.TURN_LEFT)

        assertThat(result.completed).isTrue()
    }

    @Test
    fun `turn right detected immediately with pre-captured baseline`() {
        detector.startDetecting(ChallengeType.TURN_RIGHT, baselineYaw = 0f)

        val turnedFace = mockFace(headEulerAngleY = 20f)
        val result = detector.process(turnedFace, ChallengeType.TURN_RIGHT)

        assertThat(result.completed).isTrue()
    }

    // =====================================================================
    // Cumulative delta (jitter resilience)
    // =====================================================================

    @Test
    fun `turn detection survives momentary jitter after reaching threshold`() {
        detector.startDetecting(ChallengeType.TURN_LEFT, baselineYaw = 0f)

        // User turns far enough
        val turnedFace = mockFace(headEulerAngleY = -20f)
        detector.process(turnedFace, ChallengeType.TURN_LEFT)

        // ML Kit jitters back closer to center
        val jitteredFace = mockFace(headEulerAngleY = -8f)
        val result = detector.process(jitteredFace, ChallengeType.TURN_LEFT)

        // maxTurnDelta was 20, so should still be detected
        assertThat(result.completed).isTrue()
    }

    // =====================================================================
    // Threshold edge cases
    // =====================================================================

    @Test
    fun `smile at exact threshold is not detected`() {
        val face = mockFace(smilingProbability = 0.35f) // At threshold (uses >, not >=)
        detector.startDetecting(ChallengeType.SMILE)

        repeat(5) { detector.process(face, ChallengeType.SMILE) }
        val result = detector.process(face, ChallengeType.SMILE)
        assertThat(result.completed).isFalse()
    }

    @Test
    fun `blink with one eye null returns false`() {
        val face = mockFace(leftEyeOpen = 0.1f, rightEyeOpen = null)
        detector.startDetecting(ChallengeType.BLINK)

        val result = detector.process(face, ChallengeType.BLINK)
        assertThat(result.completed).isFalse()
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private fun mockFace(
        smilingProbability: Float? = null,
        leftEyeOpen: Float? = null,
        rightEyeOpen: Float? = null,
        headEulerAngleY: Float = 0f,
        headEulerAngleX: Float = 0f,
        trackingId: Int? = 1
    ): Face {
        return mockk<Face> {
            every { this@mockk.smilingProbability } returns smilingProbability
            every { leftEyeOpenProbability } returns leftEyeOpen
            every { rightEyeOpenProbability } returns rightEyeOpen
            every { this@mockk.headEulerAngleY } returns headEulerAngleY
            every { this@mockk.headEulerAngleX } returns headEulerAngleX
            every { this@mockk.trackingId } returns trackingId
        }
    }
}
