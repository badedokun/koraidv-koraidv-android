package com.koraidv.sdk.api

import android.util.Base64
import android.util.Log
import com.koraidv.sdk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            val base64Image = Base64.encodeToString(imageData, Base64.NO_WRAP)

            val response = if (side == DocumentSide.FRONT) {
                apiClient.apiService.uploadDocument(
                    verificationId,
                    UploadDocumentRequest(
                        documentType = documentType.code,
                        imageBase64 = base64Image
                    )
                )
            } else {
                apiClient.apiService.uploadDocumentBack(
                    verificationId,
                    UploadDocumentBackRequest(imageBase64 = base64Image)
                )
            }

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Result.success(
                    DocumentUploadResult(
                        success = body.documentId != null,
                        documentId = body.documentId,
                        qualityScore = body.qualityScore,
                        warnings = body.warnings
                    )
                )
            } else {
                Result.failure(mapHttpError(response.code()))
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    suspend fun uploadDocumentByCode(
        verificationId: String,
        imageData: ByteArray,
        side: DocumentSide,
        documentTypeCode: String
    ): Result<DocumentUploadResult> = withContext(Dispatchers.IO) {
        try {
            val base64Image = Base64.encodeToString(imageData, Base64.NO_WRAP)

            val response = if (side == DocumentSide.FRONT) {
                apiClient.apiService.uploadDocument(
                    verificationId,
                    UploadDocumentRequest(
                        documentType = documentTypeCode,
                        imageBase64 = base64Image
                    )
                )
            } else {
                apiClient.apiService.uploadDocumentBack(
                    verificationId,
                    UploadDocumentBackRequest(imageBase64 = base64Image)
                )
            }

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Result.success(
                    DocumentUploadResult(
                        success = body.documentId != null,
                        documentId = body.documentId,
                        qualityScore = body.qualityScore,
                        warnings = body.warnings
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
            val base64Image = Base64.encodeToString(imageData, Base64.NO_WRAP)

            val response = apiClient.apiService.uploadSelfie(
                verificationId,
                UploadSelfieRequest(imageBase64 = base64Image)
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Result.success(
                    SelfieUploadResult(
                        success = body.faceDetected,
                        faceDetected = body.faceDetected,
                        qualityScore = body.qualityScore,
                        qualityIssues = body.qualityIssues
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
                        sessionId = body.id,
                        challenges = body.challenges.mapIndexed { index, challenge ->
                            LivenessChallenge(
                                id = "${body.id}_${index}",
                                type = ChallengeType.fromValue(challenge.type),
                                instruction = getInstructionForType(challenge.type),
                                order = index
                            )
                        },
                        expiresAt = Date(System.currentTimeMillis() + 300_000) // 5 min default
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
            val base64Image = Base64.encodeToString(imageData, Base64.NO_WRAP)

            val response = apiClient.apiService.submitLivenessChallenge(
                verificationId,
                SubmitLivenessChallengeRequest(
                    challengeType = challenge.type.value,
                    imageBase64 = base64Image
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Result.success(
                    LivenessChallengeResult(
                        success = body.completed ?: false,
                        challengePassed = body.completed ?: false,
                        confidence = body.score ?: 0.0,
                        remainingChallenges = body.remainingChallenges ?: 0
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

    suspend fun getDocumentTypes(
        country: String? = null
    ): Result<DocumentTypesResult> = withContext(Dispatchers.IO) {
        try {
            Log.d("KoraIDV", "getDocumentTypes: calling API with country=$country")
            val response = apiClient.apiService.getDocumentTypes(country)
            Log.d("KoraIDV", "getDocumentTypes: response code=${response.code()}, isSuccessful=${response.isSuccessful}")

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Log.d("KoraIDV", "getDocumentTypes: body has ${body.documentTypes?.size ?: 0} docTypes, ${body.countries?.size ?: 0} countries")
                Result.success(
                    DocumentTypesResult(
                        documentTypes = body.documentTypes?.map { dto ->
                            DocumentTypeInfo(
                                code = dto.code,
                                displayName = dto.displayName,
                                description = dto.description,
                                country = dto.country,
                                countryName = dto.countryName,
                                requiresBack = dto.requiresBack,
                                category = dto.category
                            )
                        } ?: emptyList(),
                        countries = body.countries?.map { dto ->
                            CountryInfo(
                                code = dto.code,
                                name = dto.name,
                                flagEmoji = dto.flagEmoji
                            )
                        } ?: emptyList()
                    )
                )
            } else {
                Log.e("KoraIDV", "getDocumentTypes: HTTP error ${response.code()} - ${response.errorBody()?.string()}")
                Result.failure(mapHttpError(response.code()))
            }
        } catch (e: Exception) {
            Log.e("KoraIDV", "getDocumentTypes: exception", e)
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

    private fun getInstructionForType(type: String): String {
        return when (type) {
            "blink" -> "Blink your eyes"
            "smile" -> "Smile naturally"
            "turn_left" -> "Turn your head to the left"
            "turn_right" -> "Turn your head to the right"
            "nod_up" -> "Look up"
            "nod_down" -> "Look down"
            else -> "Follow the instruction"
        }
    }

    private fun mapVerification(response: VerificationResponse): Verification {
        return Verification(
            id = response.id,
            externalId = response.externalId ?: "",
            tenantId = response.tenantId ?: "",
            tier = response.tier,
            status = VerificationStatus.fromValue(response.status),
            documentVerification = response.documentVerification?.let {
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
            faceVerification = response.faceVerification?.let {
                FaceVerification(it.match_score, it.match_result, it.confidence)
            },
            livenessVerification = response.livenessVerification?.let {
                LivenessVerification(
                    livenessScore = it.liveness_score,
                    isLive = it.is_live,
                    challengeResults = it.challenge_results?.map { cr ->
                        ChallengeResult(cr.type, cr.passed, cr.confidence)
                    }
                )
            },
            riskSignals = response.riskSignals?.map { RiskSignal(it.code, it.severity, it.message) },
            riskScore = response.riskScore,
            createdAt = parseDate(response.createdAt),
            updatedAt = parseDate(response.updatedAt),
            completedAt = response.completedAt?.let { parseDate(it) }
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
    val warnings: List<String>?
)

data class SelfieUploadResult(
    val success: Boolean,
    val faceDetected: Boolean,
    val qualityScore: Double?,
    val qualityIssues: List<String>?
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

// Document types result models
data class DocumentTypesResult(
    val documentTypes: List<DocumentTypeInfo>,
    val countries: List<CountryInfo>
)

data class DocumentTypeInfo(
    val code: String,
    val displayName: String,
    val description: String?,
    val country: String?,
    val countryName: String?,
    val requiresBack: Boolean,
    val category: String?
)

data class CountryInfo(
    val code: String,
    val name: String,
    val flagEmoji: String?
)
