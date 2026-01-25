package com.koraidv.sdk

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Verification model.
 */
@Parcelize
data class Verification(
    /** Unique verification ID */
    val id: String,
    /** External ID provided by the client */
    val externalId: String,
    /** Tenant ID */
    val tenantId: String,
    /** Verification tier */
    val tier: String,
    /** Current status */
    val status: VerificationStatus,
    /** Document verification result */
    val documentVerification: DocumentVerification? = null,
    /** Face verification result */
    val faceVerification: FaceVerification? = null,
    /** Liveness verification result */
    val livenessVerification: LivenessVerification? = null,
    /** Risk signals */
    val riskSignals: List<RiskSignal>? = null,
    /** Overall risk score (0-100) */
    val riskScore: Int? = null,
    /** Creation timestamp */
    val createdAt: Date,
    /** Last update timestamp */
    val updatedAt: Date,
    /** Completion timestamp */
    val completedAt: Date? = null
) : Parcelable

/**
 * Verification status.
 */
enum class VerificationStatus(val value: String) {
    PENDING("pending"),
    DOCUMENT_REQUIRED("document_required"),
    SELFIE_REQUIRED("selfie_required"),
    LIVENESS_REQUIRED("liveness_required"),
    PROCESSING("processing"),
    APPROVED("approved"),
    REJECTED("rejected"),
    REVIEW_REQUIRED("review_required"),
    EXPIRED("expired");

    companion object {
        fun fromValue(value: String): VerificationStatus {
            return entries.find { it.value == value } ?: PENDING
        }
    }
}

/**
 * Document verification result.
 */
@Parcelize
data class DocumentVerification(
    val documentType: String,
    val documentNumber: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val dateOfBirth: String? = null,
    val expirationDate: String? = null,
    val issuingCountry: String? = null,
    val mrzValid: Boolean? = null,
    val authenticityScore: Double? = null,
    val extractedFields: Map<String, String>? = null
) : Parcelable

/**
 * Face verification result.
 */
@Parcelize
data class FaceVerification(
    val matchScore: Double,
    val matchResult: String,
    val confidence: Double
) : Parcelable

/**
 * Liveness verification result.
 */
@Parcelize
data class LivenessVerification(
    val livenessScore: Double,
    val isLive: Boolean,
    val challengeResults: List<ChallengeResult>? = null
) : Parcelable

/**
 * Individual challenge result.
 */
@Parcelize
data class ChallengeResult(
    val type: String,
    val passed: Boolean,
    val confidence: Double
) : Parcelable

/**
 * Risk signal.
 */
@Parcelize
data class RiskSignal(
    val code: String,
    val severity: String,
    val message: String
) : Parcelable
