package com.koraidv.sdk

import com.google.common.truth.Truth.assertThat
import com.koraidv.sdk.api.ChallengeType
import com.koraidv.sdk.api.DocumentSide
import org.junit.Test

class VerificationModelsTest {

    // =====================================================================
    // VerificationStatus
    // =====================================================================

    @Test
    fun `VerificationStatus fromValue maps pending`() {
        assertThat(VerificationStatus.fromValue("pending")).isEqualTo(VerificationStatus.PENDING)
    }

    @Test
    fun `VerificationStatus fromValue maps approved`() {
        assertThat(VerificationStatus.fromValue("approved")).isEqualTo(VerificationStatus.APPROVED)
    }

    @Test
    fun `VerificationStatus fromValue maps rejected`() {
        assertThat(VerificationStatus.fromValue("rejected")).isEqualTo(VerificationStatus.REJECTED)
    }

    @Test
    fun `VerificationStatus fromValue maps document_required`() {
        assertThat(VerificationStatus.fromValue("document_required"))
            .isEqualTo(VerificationStatus.DOCUMENT_REQUIRED)
    }

    @Test
    fun `VerificationStatus fromValue maps selfie_required`() {
        assertThat(VerificationStatus.fromValue("selfie_required"))
            .isEqualTo(VerificationStatus.SELFIE_REQUIRED)
    }

    @Test
    fun `VerificationStatus fromValue maps liveness_required`() {
        assertThat(VerificationStatus.fromValue("liveness_required"))
            .isEqualTo(VerificationStatus.LIVENESS_REQUIRED)
    }

    @Test
    fun `VerificationStatus fromValue maps processing`() {
        assertThat(VerificationStatus.fromValue("processing"))
            .isEqualTo(VerificationStatus.PROCESSING)
    }

    @Test
    fun `VerificationStatus fromValue maps review_required`() {
        assertThat(VerificationStatus.fromValue("review_required"))
            .isEqualTo(VerificationStatus.REVIEW_REQUIRED)
    }

    @Test
    fun `VerificationStatus fromValue maps expired`() {
        assertThat(VerificationStatus.fromValue("expired"))
            .isEqualTo(VerificationStatus.EXPIRED)
    }

    @Test
    fun `VerificationStatus fromValue defaults unknown to PENDING`() {
        assertThat(VerificationStatus.fromValue("nonexistent")).isEqualTo(VerificationStatus.PENDING)
    }

    @Test
    fun `VerificationStatus value property matches string`() {
        assertThat(VerificationStatus.APPROVED.value).isEqualTo("approved")
    }

    // =====================================================================
    // ChallengeType
    // =====================================================================

    @Test
    fun `ChallengeType fromValue maps blink`() {
        assertThat(ChallengeType.fromValue("blink")).isEqualTo(ChallengeType.BLINK)
    }

    @Test
    fun `ChallengeType fromValue maps smile`() {
        assertThat(ChallengeType.fromValue("smile")).isEqualTo(ChallengeType.SMILE)
    }

    @Test
    fun `ChallengeType fromValue maps turn_left`() {
        assertThat(ChallengeType.fromValue("turn_left")).isEqualTo(ChallengeType.TURN_LEFT)
    }

    @Test
    fun `ChallengeType fromValue maps turn_right`() {
        assertThat(ChallengeType.fromValue("turn_right")).isEqualTo(ChallengeType.TURN_RIGHT)
    }

    @Test
    fun `ChallengeType fromValue maps nod_up`() {
        assertThat(ChallengeType.fromValue("nod_up")).isEqualTo(ChallengeType.NOD_UP)
    }

    @Test
    fun `ChallengeType fromValue maps nod_down`() {
        assertThat(ChallengeType.fromValue("nod_down")).isEqualTo(ChallengeType.NOD_DOWN)
    }

    @Test
    fun `ChallengeType fromValue defaults unknown to BLINK`() {
        assertThat(ChallengeType.fromValue("unknown_challenge")).isEqualTo(ChallengeType.BLINK)
    }

    @Test
    fun `ChallengeType value property matches string`() {
        assertThat(ChallengeType.SMILE.value).isEqualTo("smile")
    }

    // =====================================================================
    // DocumentSide
    // =====================================================================

    @Test
    fun `DocumentSide FRONT has value front`() {
        assertThat(DocumentSide.FRONT.value).isEqualTo("front")
    }

    @Test
    fun `DocumentSide BACK has value back`() {
        assertThat(DocumentSide.BACK.value).isEqualTo("back")
    }

    // =====================================================================
    // VerificationTier
    // =====================================================================

    @Test
    fun `VerificationTier BASIC has value basic`() {
        assertThat(VerificationTier.BASIC.value).isEqualTo("basic")
    }

    @Test
    fun `VerificationTier STANDARD has value standard`() {
        assertThat(VerificationTier.STANDARD.value).isEqualTo("standard")
    }

    @Test
    fun `VerificationTier ENHANCED has value enhanced`() {
        assertThat(VerificationTier.ENHANCED.value).isEqualTo("enhanced")
    }

    // =====================================================================
    // VerificationScores
    // =====================================================================

    @Test
    fun `VerificationScores stores all fields`() {
        val scores = VerificationScores(
            documentQuality = 95.0,
            documentAuth = 90.0,
            faceMatch = 88.5,
            liveness = 92.0,
            nameMatch = 100.0,
            dataConsistency = 85.0,
            overall = 91.0
        )
        assertThat(scores.documentQuality).isEqualTo(95.0)
        assertThat(scores.overall).isEqualTo(91.0)
        assertThat(scores.faceMatch).isEqualTo(88.5)
    }

    // =====================================================================
    // VerificationResult sealed class
    // =====================================================================

    @Test
    fun `VerificationResult Success holds verification`() {
        val verification = createTestVerification()
        val result = VerificationResult.Success(verification)
        assertThat(result.verification.id).isEqualTo("ver-123")
    }

    @Test
    fun `VerificationResult Failure holds error`() {
        val error = KoraException.Unauthorized()
        val result = VerificationResult.Failure(error)
        assertThat(result.error).isInstanceOf(KoraException.Unauthorized::class.java)
    }

    @Test
    fun `VerificationResult Cancelled is singleton`() {
        assertThat(VerificationResult.Cancelled).isEqualTo(VerificationResult.Cancelled)
    }

    // =====================================================================
    // VerificationRequest
    // =====================================================================

    @Test
    fun `VerificationRequest defaults tier to STANDARD`() {
        val request = VerificationRequest(externalId = "user-1")
        assertThat(request.tier).isEqualTo(VerificationTier.STANDARD)
    }

    @Test
    fun `VerificationRequest defaults documentTypes to null`() {
        val request = VerificationRequest(externalId = "user-1")
        assertThat(request.documentTypes).isNull()
    }

    // =====================================================================
    // Data class helpers
    // =====================================================================

    @Test
    fun `DocumentVerification fields are accessible`() {
        val doc = DocumentVerification(
            documentType = "passport",
            documentNumber = "ABC123",
            firstName = "John",
            lastName = "Doe",
            dateOfBirth = "1990-01-01",
            expirationDate = "2030-01-01",
            issuingCountry = "US",
            mrzValid = true,
            authenticityScore = 0.95,
            extractedFields = mapOf("field1" to "value1")
        )
        assertThat(doc.documentType).isEqualTo("passport")
        assertThat(doc.mrzValid).isTrue()
        assertThat(doc.extractedFields).containsEntry("field1", "value1")
    }

    @Test
    fun `FaceVerification fields are accessible`() {
        val face = FaceVerification(matchScore = 0.95, matchResult = "match", confidence = 0.9)
        assertThat(face.matchScore).isEqualTo(0.95)
        assertThat(face.matchResult).isEqualTo("match")
    }

    @Test
    fun `LivenessVerification fields are accessible`() {
        val liveness = LivenessVerification(
            livenessScore = 0.98,
            isLive = true,
            challengeResults = listOf(ChallengeResult("blink", true, 0.9))
        )
        assertThat(liveness.isLive).isTrue()
        assertThat(liveness.challengeResults).hasSize(1)
    }

    @Test
    fun `RiskSignal fields are accessible`() {
        val signal = RiskSignal(code = "DEVICE_SPOOFING", severity = "high", message = "Suspicious device")
        assertThat(signal.code).isEqualTo("DEVICE_SPOOFING")
        assertThat(signal.severity).isEqualTo("high")
    }

    private fun createTestVerification(): Verification {
        return Verification(
            id = "ver-123",
            externalId = "ext-1",
            tenantId = "t-1",
            tier = "standard",
            status = VerificationStatus.APPROVED,
            createdAt = java.util.Date(),
            updatedAt = java.util.Date()
        )
    }
}
