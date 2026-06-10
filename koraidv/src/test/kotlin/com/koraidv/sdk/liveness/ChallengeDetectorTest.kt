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

        // v1.9.1: needs 5 consecutive detections.
        repeat(4) { detector.process(face, ChallengeType.SMILE) }
        val r = detector.process(face, ChallengeType.SMILE)

        assertThat(r.completed).isTrue()
        assertThat(r.progress).isEqualTo(1.0f)
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
    fun `turn left detected when yaw increases enough`() {
        detector.startDetecting(ChallengeType.TURN_LEFT)

        // Build baseline (3 frames needed: skip 1, average frames 1-2)
        val straightFace = mockFace(headEulerAngleY = 0f)
        repeat(3) { detector.process(straightFace, ChallengeType.TURN_LEFT) }

        // v1.9.1-rc5: ML Kit headEulerAngleY on Pixel 9 Pro XL / Android 16
        // produces POSITIVE values when the user turns LEFT (opposite of
        // what the pre-rc5 code comment assumed — see SR diagnostic
        // verification f4b15520, 2026-06-10). turn_left now requires
        // positive yaw delta.
        val turnedFace = mockFace(headEulerAngleY = 20f)
        repeat(4) { detector.process(turnedFace, ChallengeType.TURN_LEFT) }
        val result = detector.process(turnedFace, ChallengeType.TURN_LEFT)

        assertThat(result.completed).isTrue()
    }

    @Test
    fun `turn right detected when yaw decreases enough`() {
        detector.startDetecting(ChallengeType.TURN_RIGHT)

        val straightFace = mockFace(headEulerAngleY = 0f)
        repeat(3) { detector.process(straightFace, ChallengeType.TURN_RIGHT) }

        // v1.9.1-rc5: user turning RIGHT produces NEGATIVE ML Kit yaw delta
        // on this device/OS combo (post-empirical-correction).
        val turnedFace = mockFace(headEulerAngleY = -20f)
        repeat(4) { detector.process(turnedFace, ChallengeType.TURN_RIGHT) }
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
    fun `nod up detected when pitch decreases enough`() {
        detector.startDetecting(ChallengeType.NOD_UP)

        val straightFace = mockFace(headEulerAngleX = 0f)
        repeat(3) { detector.process(straightFace, ChallengeType.NOD_UP) }

        // v1.9.1-rc5: ML Kit headEulerAngleX on this device/OS produces
        // NEGATIVE values when the user nods UP (positive = nods DOWN).
        val nodUpFace = mockFace(headEulerAngleX = -10f)
        repeat(4) { detector.process(nodUpFace, ChallengeType.NOD_UP) }
        val result = detector.process(nodUpFace, ChallengeType.NOD_UP)

        assertThat(result.completed).isTrue()
    }

    @Test
    fun `nod down detected when pitch increases enough`() {
        detector.startDetecting(ChallengeType.NOD_DOWN)

        val straightFace = mockFace(headEulerAngleX = 0f)
        repeat(3) { detector.process(straightFace, ChallengeType.NOD_DOWN) }

        // v1.9.1-rc5: user nodding DOWN produces POSITIVE ML Kit pitch.
        val nodDownFace = mockFace(headEulerAngleX = 10f)
        repeat(4) { detector.process(nodDownFace, ChallengeType.NOD_DOWN) }
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
        repeat(4) { detector.process(smilingFace, ChallengeType.SMILE) }
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

        // v1.9.1-rc5: user turning RIGHT produces NEGATIVE yaw.
        val wrongDirection = mockFace(headEulerAngleY = -20f)
        repeat(3) { detector.process(wrongDirection, ChallengeType.TURN_LEFT) }
        val result = detector.process(wrongDirection, ChallengeType.TURN_LEFT)
        assertThat(result.completed).isFalse()
    }

    @Test
    fun `turn right not detected when turning left`() {
        detector.startDetecting(ChallengeType.TURN_RIGHT)
        val straightFace = mockFace(headEulerAngleY = 0f)
        repeat(6) { detector.process(straightFace, ChallengeType.TURN_RIGHT) }

        // v1.9.1-rc5: user turning LEFT produces POSITIVE yaw.
        val wrongDirection = mockFace(headEulerAngleY = 20f)
        repeat(3) { detector.process(wrongDirection, ChallengeType.TURN_RIGHT) }
        val result = detector.process(wrongDirection, ChallengeType.TURN_RIGHT)
        assertThat(result.completed).isFalse()
    }

    @Test
    fun `nod up not detected when nodding down`() {
        detector.startDetecting(ChallengeType.NOD_UP)
        val straightFace = mockFace(headEulerAngleX = 0f)
        repeat(6) { detector.process(straightFace, ChallengeType.NOD_UP) }

        // v1.9.1-rc5: user nodding DOWN produces POSITIVE pitch.
        val nodDown = mockFace(headEulerAngleX = 10f)
        repeat(3) { detector.process(nodDown, ChallengeType.NOD_UP) }
        val result = detector.process(nodDown, ChallengeType.NOD_UP)
        assertThat(result.completed).isFalse()
    }

    @Test
    fun `nod down not detected when nodding up`() {
        detector.startDetecting(ChallengeType.NOD_DOWN)
        val straightFace = mockFace(headEulerAngleX = 0f)
        repeat(6) { detector.process(straightFace, ChallengeType.NOD_DOWN) }

        // v1.9.1-rc5: user nodding UP produces NEGATIVE pitch.
        val nodUp = mockFace(headEulerAngleX = -10f)
        repeat(3) { detector.process(nodUp, ChallengeType.NOD_DOWN) }
        val result = detector.process(nodUp, ChallengeType.NOD_DOWN)
        assertThat(result.completed).isFalse()
    }

    // =====================================================================
    // Pre-captured baseline
    // =====================================================================

    @Test
    fun `turn left detected with pre-captured baseline after 5 sustained frames`() {
        detector.startDetecting(ChallengeType.TURN_LEFT, baselineYaw = 0f)

        // v1.9.1-rc5: positive yaw delta = user turned LEFT on this device.
        val turnedFace = mockFace(headEulerAngleY = 20f)
        repeat(4) { detector.process(turnedFace, ChallengeType.TURN_LEFT) }
        val result = detector.process(turnedFace, ChallengeType.TURN_LEFT)

        assertThat(result.completed).isTrue()
    }

    @Test
    fun `turn right detected with pre-captured baseline after 5 sustained frames`() {
        detector.startDetecting(ChallengeType.TURN_RIGHT, baselineYaw = 0f)

        // v1.9.1-rc5: negative yaw delta = user turned RIGHT.
        val turnedFace = mockFace(headEulerAngleY = -20f)
        repeat(4) { detector.process(turnedFace, ChallengeType.TURN_RIGHT) }
        val result = detector.process(turnedFace, ChallengeType.TURN_RIGHT)

        assertThat(result.completed).isTrue()
    }

    // =====================================================================
    // Jitter behavior (v1.9.1 — single-frame jitter does NOT lock detection)
    // =====================================================================

    @Test
    fun `single frame past threshold does NOT complete challenge`() {
        // The pre-v1.9.1 detector accepted a challenge after ONE frame past
        // threshold (requiredConsecutiveDetections = 1) AND made it sticky
        // via maxTurnDelta. Combined, this let any momentary motion satisfy
        // any direction. v1.9.1 raises the gate to 5 consecutive frames and
        // drops the sticky max.
        detector.startDetecting(ChallengeType.TURN_LEFT, baselineYaw = 0f)

        val turnedFace = mockFace(headEulerAngleY = -20f)
        val result = detector.process(turnedFace, ChallengeType.TURN_LEFT)

        // Just one frame — must not complete.
        assertThat(result.completed).isFalse()
    }

    @Test
    fun `momentary spike then return to neutral does NOT complete challenge`() {
        // A jitter spike that briefly exceeds threshold and then returns is
        // exactly the false-positive pattern v1.9.1 closes. Under the old
        // sticky-max behavior this would have stayed "completed."
        detector.startDetecting(ChallengeType.TURN_LEFT, baselineYaw = 0f)

        val spike = mockFace(headEulerAngleY = -20f)
        val neutral = mockFace(headEulerAngleY = 0f)

        detector.process(spike, ChallengeType.TURN_LEFT)
        // Followed by 4 neutral frames — last-5 window has only 1 detection.
        repeat(4) { detector.process(neutral, ChallengeType.TURN_LEFT) }
        val result = detector.process(neutral, ChallengeType.TURN_LEFT)

        assertThat(result.completed).isFalse()
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
    // v1.9.1 — security regression: Olabode @ BanffPay 2026-06-08
    //
    // Reproduction: SDK asks "turn right" but a user who turns LEFT (or who
    // simply returns to neutral from a left-offset baseline) is accepted.
    // Root cause was `requiredConsecutiveDetections = 1` + sticky `maxTurnDelta`
    // letting a single transient frame past threshold permanently complete the
    // challenge. These tests pin the fix.
    // =====================================================================

    @Test
    fun `turn right NOT completed when user turns left from neutral baseline`() {
        // v1.9.1-rc5: user turning LEFT produces POSITIVE ML Kit yaw on this
        // device/OS (opposite of the pre-rc5 code-comment assumption).
        // turn_right requires NEGATIVE yaw delta. Sustained positive delta
        // must NOT pass turn_right.
        detector.startDetecting(ChallengeType.TURN_RIGHT, baselineYaw = 0f)

        val turnedLeft = mockFace(headEulerAngleY = 20f)
        repeat(10) { detector.process(turnedLeft, ChallengeType.TURN_RIGHT) }
        val result = detector.process(turnedLeft, ChallengeType.TURN_RIGHT)

        assertThat(result.completed).isFalse()
    }

    @Test
    fun `turn left NOT completed when user turns right from neutral baseline`() {
        detector.startDetecting(ChallengeType.TURN_LEFT, baselineYaw = 0f)

        // v1.9.1-rc5: user turning RIGHT produces NEGATIVE yaw.
        val turnedRight = mockFace(headEulerAngleY = -20f)
        repeat(10) { detector.process(turnedRight, ChallengeType.TURN_LEFT) }
        val result = detector.process(turnedRight, ChallengeType.TURN_LEFT)

        assertThat(result.completed).isFalse()
    }

    @Test
    fun `nod up NOT completed when user nods down from neutral baseline`() {
        detector.startDetecting(ChallengeType.NOD_UP, baselinePitch = 0f)

        // v1.9.1-rc5: user nodding DOWN produces POSITIVE pitch.
        val noddedDown = mockFace(headEulerAngleX = 15f)
        repeat(10) { detector.process(noddedDown, ChallengeType.NOD_UP) }
        val result = detector.process(noddedDown, ChallengeType.NOD_UP)

        assertThat(result.completed).isFalse()
    }

    @Test
    fun `nod down NOT completed when user nods up from neutral baseline`() {
        detector.startDetecting(ChallengeType.NOD_DOWN, baselinePitch = 0f)

        // v1.9.1-rc5: user nodding UP produces NEGATIVE pitch.
        val noddedUp = mockFace(headEulerAngleX = -15f)
        repeat(10) { detector.process(noddedUp, ChallengeType.NOD_DOWN) }
        val result = detector.process(noddedUp, ChallengeType.NOD_DOWN)

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
