package com.koraidv.sdk.api

import com.koraidv.sdk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages verification sessions and state.
 */
internal class SessionManager(
    private val configuration: Configuration,
    private val apiClient: ApiClient
) {
    /** Current active verification */
    var currentVerification: Verification? = null
        private set

    private var sessionStartTime: Date? = null

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // Verification lifecycle

    suspend fun createVerification(
        externalId: String,
        tier: VerificationTier
    ): Result<Verification> = withContext(Dispatchers.IO) {
        try {
            sessionStartTime = Date()
            val request = CreateVerificationRequest(externalId, tier.value)
            val response = apiClient.apiService.createVerification(request)

            if (response.isSuccessful && response.body() != null) {
                val verification = mapVerification(response.body()!!)
                currentVerification = verification
                Result.success(verification)
            } else {
                Result.failure(mapHttpError(response.code()))
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    suspend fun getVerification(id: String): Result<Verification> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.apiService.getVerification(id)

            if (response.isSuccessful && response.body() != null) {
                val verification = mapVerification(response.body()!!)
                currentVerification = verification
                sessionStartTime = Date()
                Result.success(verification)
            } else {
                Result.failure(mapHttpError(response.code()))
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    suspend fun uploadDocument(
        verificationId: String,
        imageData: ByteArray,
        side: DocumentSide,
        documentType: DocumentType
    ): Result<DocumentUploadResult> = withContext(Dispatchers.IO) {
        try {
            val imagePart = MultipartBody.Part.createFormData(
                "image",
                "document.jpg",
                imageData.toRequestBody("image/jpeg".toMediaTypeOrNull())
            )
            val documentTypePart = documentType.code.toRequestBody("text/plain".toMediaTypeOrNull())
            val sidePart = side.value.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = if (side == DocumentSide.FRONT) {
                apiClient.apiService.uploadDocument(verificationId, imagePart, documentTypePart, sidePart)
            } else {
                apiClient.apiService.uploadDocumentBack(verificationId, imagePart, documentTypePart)
            }

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Result.success(
                    DocumentUploadResult(
                        success = body.success,
                        documentId = body.document_id,
                        qualityScore = body.quality_score,
                        qualityIssues = body.quality_issues?.map { QualityIssue(it.type, it.message, it.severity) }
                    )
                )
            } else {
                Result.failure(mapHttpError(response.code()))
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    suspend fun uploadSelfie(
        verificationId: String,
        imageData: ByteArray
    ): Result<SelfieUploadResult> = withContext(Dispatchers.IO) {
        try {
            val imagePart = MultipartBody.Part.createFormData(
                "image",
                "selfie.jpg",
                imageData.toRequestBody("image/jpeg".toMediaTypeOrNull())
            )

            val response = apiClient.apiService.uploadSelfie(verificationId, imagePart)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Result.success(
                    SelfieUploadResult(
                        success = body.success,
                        selfieId = body.selfie_id,
                        faceDetected = body.face_detected,
                        qualityScore = body.quality_score,
                        qualityIssues = body.quality_issues?.map { QualityIssue(it.type, it.message, it.severity) }
                    )
                )
            } else {
                Result.failure(mapHttpError(response.code()))
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    suspend fun createLivenessSession(
        verificationId: String
    ): Result<LivenessSession> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.apiService.createLivenessSession(verificationId)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Result.success(
                    LivenessSession(
                        sessionId = body.session_id,
                        challenges = body.challenges.map { challenge ->
                            LivenessChallenge(
                                id = challenge.id,
                                type = ChallengeType.fromValue(challenge.type),
                                instruction = challenge.instruction,
                                order = challenge.order
                            )
                        },
                        expiresAt = parseDate(body.expires_at)
                    )
                )
            } else {
                Result.failure(mapHttpError(response.code()))
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    suspend fun submitLivenessChallenge(
        verificationId: String,
        challenge: LivenessChallenge,
        imageData: ByteArray
    ): Result<LivenessChallengeResult> = withContext(Dispatchers.IO) {
        try {
            val imagePart = MultipartBody.Part.createFormData(
                "image",
                "challenge.jpg",
                imageData.toRequestBody("image/jpeg".toMediaTypeOrNull())
            )
            val challengeTypePart = challenge.type.value.toRequestBody("text/plain".toMediaTypeOrNull())
            val challengeIdPart = challenge.id.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = apiClient.apiService.submitLivenessChallenge(
                verificationId, imagePart, challengeTypePart, challengeIdPart
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Result.success(
                    LivenessChallengeResult(
                        success = body.success,
                        challengePassed = body.challenge_passed,
                        confidence = body.confidence,
                        remainingChallenges = body.remaining_challenges
                    )
                )
            } else {
                Result.failure(mapHttpError(response.code()))
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    suspend fun completeVerification(
        verificationId: String
    ): Result<Verification> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.apiService.completeVerification(verificationId)

            if (response.isSuccessful && response.body() != null) {
                val verification = mapVerification(response.body()!!)
                currentVerification = verification
                Result.success(verification)
            } else {
                Result.failure(mapHttpError(response.code()))
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    // Session management

    val isSessionTimedOut: Boolean
        get() {
            val startTime = sessionStartTime ?: return false
            return Date().time - startTime.time > configuration.timeout * 1000
        }

    fun resetSession() {
        currentVerification = null
        sessionStartTime = null
    }

    fun refreshSession() {
        sessionStartTime = Date()
    }

    // Mapping helpers

    private fun mapVerification(response: VerificationResponse): Verification {
        return Verification(
            id = response.id,
            externalId = response.external_id,
            tenantId = response.tenant_id,
            tier = response.tier,
            status = VerificationStatus.fromValue(response.status),
            documentVerification = response.document_verification?.let {
                DocumentVerification(
                    documentType = it.document_type,
                    documentNumber = it.document_number,
                    firstName = it.first_name,
                    lastName = it.last_name,
                    dateOfBirth = it.date_of_birth,
                    expirationDate = it.expiration_date,
                    issuingCountry = it.issuing_country,
                    mrzValid = it.mrz_valid,
                    authenticityScore = it.authenticity_score,
                    extractedFields = it.extracted_fields
                )
            },
            faceVerification = response.face_verification?.let {
                FaceVerification(it.match_score, it.match_result, it.confidence)
            },
            livenessVerification = response.liveness_verification?.let {
                LivenessVerification(
                    livenessScore = it.liveness_score,
                    isLive = it.is_live,
                    challengeResults = it.challenge_results?.map { cr ->
                        ChallengeResult(cr.type, cr.passed, cr.confidence)
                    }
                )
            },
            riskSignals = response.risk_signals?.map { RiskSignal(it.code, it.severity, it.message) },
            riskScore = response.risk_score,
            createdAt = parseDate(response.created_at),
            updatedAt = parseDate(response.updated_at),
            completedAt = response.completed_at?.let { parseDate(it) }
        )
    }

    private fun parseDate(dateString: String): Date {
        return try {
            dateFormat.parse(dateString) ?: Date()
        } catch (e: Exception) {
            Date()
        }
    }

    private fun mapHttpError(statusCode: Int): KoraException {
        return when (statusCode) {
            401 -> KoraException.Unauthorized()
            403 -> KoraException.Forbidden()
            404 -> KoraException.NotFound()
            422 -> KoraException.ValidationError(emptyList())
            429 -> KoraException.RateLimited()
            in 500..599 -> KoraException.ServerError(statusCode)
            else -> KoraException.HttpError(statusCode)
        }
    }

    private fun mapException(e: Exception): KoraException {
        return when (e) {
            is java.net.UnknownHostException -> KoraException.NoInternet()
            is java.net.SocketTimeoutException -> KoraException.Timeout()
            is java.io.IOException -> KoraException.NetworkError(e.message ?: "Unknown error")
            else -> KoraException.Unknown(e.message ?: "Unknown error")
        }
    }
}

// Result models
data class DocumentUploadResult(
    val success: Boolean,
    val documentId: String?,
    val qualityScore: Double?,
    val qualityIssues: List<QualityIssue>?
)

data class SelfieUploadResult(
    val success: Boolean,
    val selfieId: String?,
    val faceDetected: Boolean,
    val qualityScore: Double?,
    val qualityIssues: List<QualityIssue>?
)

data class QualityIssue(
    val type: String,
    val message: String,
    val severity: String
)

data class LivenessSession(
    val sessionId: String,
    val challenges: List<LivenessChallenge>,
    val expiresAt: Date
)

data class LivenessChallenge(
    val id: String,
    val type: ChallengeType,
    val instruction: String,
    val order: Int
)

enum class ChallengeType(val value: String) {
    BLINK("blink"),
    SMILE("smile"),
    TURN_LEFT("turn_left"),
    TURN_RIGHT("turn_right"),
    NOD_UP("nod_up"),
    NOD_DOWN("nod_down");

    companion object {
        fun fromValue(value: String): ChallengeType {
            return entries.find { it.value == value } ?: BLINK
        }
    }
}

data class LivenessChallengeResult(
    val success: Boolean,
    val challengePassed: Boolean,
    val confidence: Double,
    val remainingChallenges: Int
)

enum class DocumentSide(val value: String) {
    FRONT("front"),
    BACK("back")
}
