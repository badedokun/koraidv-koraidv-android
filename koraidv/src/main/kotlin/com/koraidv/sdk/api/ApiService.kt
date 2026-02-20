package com.koraidv.sdk.api

import com.google.gson.annotations.SerializedName
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

    // Document endpoints (JSON with base64 image)
    @POST("verifications/{id}/document")
    suspend fun uploadDocument(
        @Path("id") verificationId: String,
        @Body request: UploadDocumentRequest
    ): Response<DocumentUploadResponse>

    @POST("verifications/{id}/document/back")
    suspend fun uploadDocumentBack(
        @Path("id") verificationId: String,
        @Body request: UploadDocumentBackRequest
    ): Response<DocumentUploadResponse>

    // Selfie endpoint (JSON with base64 image)
    @POST("verifications/{id}/selfie")
    suspend fun uploadSelfie(
        @Path("id") verificationId: String,
        @Body request: UploadSelfieRequest
    ): Response<SelfieUploadResponse>

    // Liveness endpoints
    @POST("verifications/{id}/liveness/session")
    suspend fun createLivenessSession(
        @Path("id") verificationId: String
    ): Response<LivenessSessionResponse>

    @POST("verifications/{id}/liveness/challenge")
    suspend fun submitLivenessChallenge(
        @Path("id") verificationId: String,
        @Body request: SubmitLivenessChallengeRequest
    ): Response<LivenessChallengeResponse>

    // Complete verification
    @POST("verifications/{id}/complete")
    suspend fun completeVerification(
        @Path("id") verificationId: String
    ): Response<VerificationResponse>

    // Document types
    @GET("document-types")
    suspend fun getDocumentTypes(
        @Query("country") country: String? = null
    ): Response<DocumentTypesResponse>
}

// ===== Request models =====

data class CreateVerificationRequest(
    @SerializedName("externalId") val externalId: String,
    val tier: String
)

data class UploadDocumentRequest(
    @SerializedName("documentType") val documentType: String,
    @SerializedName("imageBase64") val imageBase64: String
)

data class UploadDocumentBackRequest(
    @SerializedName("imageBase64") val imageBase64: String
)

data class UploadSelfieRequest(
    @SerializedName("imageBase64") val imageBase64: String
)

data class SubmitLivenessChallengeRequest(
    @SerializedName("challengeType") val challengeType: String,
    @SerializedName("imageBase64") val imageBase64: String
)

// ===== Response models =====

data class VerificationResponse(
    val id: String,
    @SerializedName("externalId") val externalId: String?,
    @SerializedName("tenantId") val tenantId: String?,
    val tier: String,
    val status: String,
    @SerializedName("documentVerification") val documentVerification: DocumentVerificationResponse?,
    @SerializedName("faceVerification") val faceVerification: FaceVerificationResponse?,
    @SerializedName("livenessVerification") val livenessVerification: LivenessVerificationResponse?,
    @SerializedName("scores") val scores: VerificationScoresResponse?,
    @SerializedName("riskSignals") val riskSignals: List<RiskSignalResponse>?,
    @SerializedName("riskScore") val riskScore: Int?,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String,
    @SerializedName("completedAt") val completedAt: String?
)

data class VerificationScoresResponse(
    @SerializedName("documentQuality") val documentQuality: Double?,
    @SerializedName("documentAuth") val documentAuth: Double?,
    @SerializedName("faceMatch") val faceMatch: Double?,
    @SerializedName("liveness") val liveness: Double?,
    @SerializedName("nameMatch") val nameMatch: Double?,
    @SerializedName("dataConsistency") val dataConsistency: Double?,
    @SerializedName("overall") val overall: Double?
)

data class DocumentVerificationResponse(
    @SerializedName("documentType") val documentType: String,
    @SerializedName("documentNumber") val documentNumber: String?,
    @SerializedName("firstName") val firstName: String?,
    @SerializedName("lastName") val lastName: String?,
    @SerializedName("dateOfBirth") val dateOfBirth: String?,
    @SerializedName("expirationDate") val expirationDate: String?,
    @SerializedName("issuingCountry") val issuingCountry: String?,
    @SerializedName("mrzValid") val mrzValid: Boolean?,
    @SerializedName("authenticityScore") val authenticityScore: Double?,
    @SerializedName("extractedFields") val extractedFields: Map<String, String>?
)

data class FaceVerificationResponse(
    @SerializedName("matchScore") val matchScore: Double,
    @SerializedName("matchResult") val matchResult: String,
    val confidence: Double
)

data class LivenessVerificationResponse(
    @SerializedName("livenessScore") val livenessScore: Double,
    @SerializedName("isLive") val isLive: Boolean,
    @SerializedName("challengeResults") val challengeResults: List<ChallengeResultResponse>?
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

// Document upload response - matches server's ProcessDocumentResult
data class DocumentUploadResponse(
    @SerializedName("documentId") val documentId: String?,
    @SerializedName("documentType") val documentType: String?,
    @SerializedName("qualityScore") val qualityScore: Double?,
    @SerializedName("ocrResult") val ocrResult: OcrResultResponse?,
    @SerializedName("extractedData") val extractedData: Map<String, Any>?,
    @SerializedName("warnings") val warnings: List<String>?
)

data class OcrResultResponse(
    @SerializedName("fullText") val fullText: String?,
    @SerializedName("fields") val fields: Map<String, String>?,
    @SerializedName("mrzData") val mrzData: Map<String, String>?
)

// Selfie upload response - matches server's ProcessSelfieResult
data class SelfieUploadResponse(
    @SerializedName("faceDetected") val faceDetected: Boolean,
    @SerializedName("faceCount") val faceCount: Int?,
    @SerializedName("qualityScore") val qualityScore: Double?,
    @SerializedName("qualityIssues") val qualityIssues: List<String>?,
    @SerializedName("faceMatch") val faceMatch: FaceMatchResponse?,
    @SerializedName("warnings") val warnings: List<String>?
)

data class FaceMatchResponse(
    @SerializedName("matched") val matched: Boolean,
    @SerializedName("score") val score: Double?,
    @SerializedName("confidence") val confidence: String?
)

// Liveness session response - matches server's LivenessSession
data class LivenessSessionResponse(
    val id: String,
    @SerializedName("verificationId") val verificationId: String?,
    val challenges: List<LivenessChallengeDto>,
    @SerializedName("overallScore") val overallScore: Double?,
    val passed: Boolean?,
    @SerializedName("createdAt") val createdAt: String?
)

// Liveness challenge DTO - matches server's LivenessChallenge
data class LivenessChallengeDto(
    val type: String,
    val completed: Boolean,
    val score: Double?,
    @SerializedName("attemptedAt") val attemptedAt: String?
)

// Liveness challenge response - matches server's SubmitLivenessChallenge result
data class LivenessChallengeResponse(
    val type: String?,
    val completed: Boolean?,
    val score: Double?,
    @SerializedName("overallScore") val overallScore: Double?,
    val passed: Boolean?,
    @SerializedName("remainingChallenges") val remainingChallenges: Int?
)

// Document types response
data class DocumentTypesResponse(
    val success: Boolean?,
    @SerializedName("documentTypes") val documentTypes: List<DocumentTypeInfoResponse>?,
    val countries: List<CountryResponse>?,
    val total: Int?
)

data class DocumentTypeInfoResponse(
    val id: String?,
    @SerializedName("type") val code: String,
    @SerializedName("name") val displayName: String,
    val description: String?,
    val country: String?,
    @SerializedName("countryName") val countryName: String?,
    @SerializedName("requiresBack") val requiresBack: Boolean,
    val category: String?,
    val active: Boolean?
)

data class CountryResponse(
    val code: String,
    val name: String,
    @SerializedName("flagEmoji") val flagEmoji: String?
)
