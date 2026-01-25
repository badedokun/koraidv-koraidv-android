package com.koraidv.sdk.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API service interface.
 */
internal interface ApiService {

    // Verification endpoints
    @POST("verifications")
    suspend fun createVerification(
        @Body request: CreateVerificationRequest
    ): Response<VerificationResponse>

    @GET("verifications/{id}")
    suspend fun getVerification(
        @Path("id") id: String
    ): Response<VerificationResponse>

    // Document endpoints
    @Multipart
    @POST("verifications/{id}/document")
    suspend fun uploadDocument(
        @Path("id") verificationId: String,
        @Part image: MultipartBody.Part,
        @Part("document_type") documentType: RequestBody,
        @Part("side") side: RequestBody
    ): Response<DocumentUploadResponse>

    @Multipart
    @POST("verifications/{id}/document/back")
    suspend fun uploadDocumentBack(
        @Path("id") verificationId: String,
        @Part image: MultipartBody.Part,
        @Part("document_type") documentType: RequestBody
    ): Response<DocumentUploadResponse>

    // Selfie endpoint
    @Multipart
    @POST("verifications/{id}/selfie")
    suspend fun uploadSelfie(
        @Path("id") verificationId: String,
        @Part image: MultipartBody.Part
    ): Response<SelfieUploadResponse>

    // Liveness endpoints
    @POST("verifications/{id}/liveness/session")
    suspend fun createLivenessSession(
        @Path("id") verificationId: String
    ): Response<LivenessSessionResponse>

    @Multipart
    @POST("verifications/{id}/liveness/challenge")
    suspend fun submitLivenessChallenge(
        @Path("id") verificationId: String,
        @Part image: MultipartBody.Part,
        @Part("challenge_type") challengeType: RequestBody,
        @Part("challenge_id") challengeId: RequestBody
    ): Response<LivenessChallengeResponse>

    // Complete verification
    @POST("verifications/{id}/complete")
    suspend fun completeVerification(
        @Path("id") verificationId: String
    ): Response<VerificationResponse>
}

// Request/Response models
data class CreateVerificationRequest(
    val external_id: String,
    val tier: String
)

data class VerificationResponse(
    val id: String,
    val external_id: String,
    val tenant_id: String,
    val tier: String,
    val status: String,
    val document_verification: DocumentVerificationResponse?,
    val face_verification: FaceVerificationResponse?,
    val liveness_verification: LivenessVerificationResponse?,
    val risk_signals: List<RiskSignalResponse>?,
    val risk_score: Int?,
    val created_at: String,
    val updated_at: String,
    val completed_at: String?
)

data class DocumentVerificationResponse(
    val document_type: String,
    val document_number: String?,
    val first_name: String?,
    val last_name: String?,
    val date_of_birth: String?,
    val expiration_date: String?,
    val issuing_country: String?,
    val mrz_valid: Boolean?,
    val authenticity_score: Double?,
    val extracted_fields: Map<String, String>?
)

data class FaceVerificationResponse(
    val match_score: Double,
    val match_result: String,
    val confidence: Double
)

data class LivenessVerificationResponse(
    val liveness_score: Double,
    val is_live: Boolean,
    val challenge_results: List<ChallengeResultResponse>?
)

data class ChallengeResultResponse(
    val type: String,
    val passed: Boolean,
    val confidence: Double
)

data class RiskSignalResponse(
    val code: String,
    val severity: String,
    val message: String
)

data class DocumentUploadResponse(
    val success: Boolean,
    val document_id: String?,
    val quality_score: Double?,
    val quality_issues: List<QualityIssueResponse>?,
    val extracted_data: DocumentVerificationResponse?
)

data class QualityIssueResponse(
    val type: String,
    val message: String,
    val severity: String
)

data class SelfieUploadResponse(
    val success: Boolean,
    val selfie_id: String?,
    val face_detected: Boolean,
    val quality_score: Double?,
    val quality_issues: List<QualityIssueResponse>?
)

data class LivenessSessionResponse(
    val session_id: String,
    val challenges: List<LivenessChallengeDto>,
    val expires_at: String
)

data class LivenessChallengeDto(
    val id: String,
    val type: String,
    val instruction: String,
    val order: Int
)

data class LivenessChallengeResponse(
    val success: Boolean,
    val challenge_passed: Boolean,
    val confidence: Double,
    val remaining_challenges: Int
)
