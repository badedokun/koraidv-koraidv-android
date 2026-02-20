package com.koraidv.sdk.api

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
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
    @Volatile
    var currentVerification: Verification? = null
        private set

    @Volatile
    private var sessionStartTime: Date? = null

    // ThreadLocal ensures each coroutine/thread gets its own SimpleDateFormat instance,
    // avoiding the thread-safety issues with SimpleDateFormat's internal mutable state.
    private val dateFormatLocal = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    // Fallback format for servers that return dates without milliseconds
    private val dateFormatFallbackLocal = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private val gson = Gson()

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
                Result.failure(mapHttpError(response.code(), response.errorBody()?.string()))
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
                Result.failure(mapHttpError(response.code(), response.errorBody()?.string()))
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
                Result.failure(mapHttpError(response.code(), response.errorBody()?.string()))
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
                Result.failure(mapHttpError(response.code(), response.errorBody()?.string()))
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
                Result.failure(mapHttpError(response.code(), response.errorBody()?.string()))
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
                Result.failure(mapHttpError(response.code(), response.errorBody()?.string()))
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
                Result.failure(mapHttpError(response.code(), response.errorBody()?.string()))
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
                Result.failure(mapHttpError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    suspend fun getDocumentTypes(
        country: String? = null
    ): Result<DocumentTypesResult> = withContext(Dispatchers.IO) {
        try {
            if (configuration.debugLogging) Log.d("KoraIDV", "getDocumentTypes: calling API with country=$country")
            val response = apiClient.apiService.getDocumentTypes(country)
            if (configuration.debugLogging) Log.d("KoraIDV", "getDocumentTypes: response code=${response.code()}, isSuccessful=${response.isSuccessful}")

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (configuration.debugLogging) Log.d("KoraIDV", "getDocumentTypes: body has ${body.documentTypes?.size ?: 0} docTypes, ${body.countries?.size ?: 0} countries")
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
                val errorBodyString = response.errorBody()?.string()
                if (configuration.debugLogging) Log.e("KoraIDV", "getDocumentTypes: HTTP error ${response.code()} - $errorBodyString")
                Result.failure(mapHttpError(response.code(), errorBodyString))
            }
        } catch (e: Exception) {
            if (configuration.debugLogging) Log.e("KoraIDV", "getDocumentTypes: exception", e)
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
                    documentType = it.documentType,
                    documentNumber = it.documentNumber,
                    firstName = it.firstName,
                    lastName = it.lastName,
                    dateOfBirth = it.dateOfBirth,
                    expirationDate = it.expirationDate,
                    issuingCountry = it.issuingCountry,
                    mrzValid = it.mrzValid,
                    authenticityScore = it.authenticityScore,
                    extractedFields = it.extractedFields
                )
            },
            faceVerification = response.faceVerification?.let {
                FaceVerification(it.matchScore, it.matchResult, it.confidence)
            },
            livenessVerification = response.livenessVerification?.let {
                LivenessVerification(
                    livenessScore = it.livenessScore,
                    isLive = it.isLive,
                    challengeResults = it.challengeResults?.map { cr ->
                        ChallengeResult(cr.type, cr.passed, cr.confidence)
                    }
                )
            },
            scores = response.scores?.let {
                VerificationScores(
                    documentQuality = it.documentQuality ?: 0.0,
                    documentAuth = it.documentAuth ?: 0.0,
                    faceMatch = it.faceMatch ?: 0.0,
                    liveness = it.liveness ?: 0.0,
                    nameMatch = it.nameMatch ?: 0.0,
                    dataConsistency = it.dataConsistency ?: 0.0,
                    overall = it.overall ?: 0.0
                )
            },
            riskSignals = response.riskSignals?.map { RiskSignal(it.code, it.severity, it.message) },
            riskScore = response.riskScore,
            createdAt = parseDate(response.createdAt),
            updatedAt = parseDate(response.updatedAt),
            completedAt = response.completedAt?.let { parseDate(it) }
        )
    }

    internal fun parseDate(dateString: String): Date {
        return try {
            dateFormatLocal.get()!!.parse(dateString) ?: Date()
        } catch (e: Exception) {
            // Try fallback format without milliseconds
            try {
                dateFormatFallbackLocal.get()!!.parse(dateString) ?: Date()
            } catch (e2: Exception) {
                Date()
            }
        }
    }

    internal fun mapHttpError(statusCode: Int, errorBody: String? = null): KoraException {
        return when (statusCode) {
            401 -> KoraException.Unauthorized()
            403 -> KoraException.Forbidden()
            404 -> KoraException.NotFound()
            422 -> {
                // Parse field-level validation errors from the response body
                val fieldErrors = parseValidationErrors(errorBody)
                KoraException.ValidationError(fieldErrors)
            }
            429 -> KoraException.RateLimited()
            in 500..599 -> KoraException.ServerError(statusCode)
            else -> KoraException.HttpError(statusCode)
        }
    }

    /**
     * Parse validation errors from a 422 response body.
     * Supports common formats: { "errors": [{ "field": "...", "message": "..." }] }
     * and { "error": "...", "details": { "field": "message" } }
     */
    internal fun parseValidationErrors(errorBody: String?): List<FieldError> {
        if (errorBody.isNullOrBlank()) return emptyList()
        return try {
            val response = gson.fromJson(errorBody, ValidationErrorBody::class.java)
            when {
                response?.errors != null -> response.errors.map {
                    FieldError(field = it.field ?: "unknown", message = it.message ?: "Validation failed")
                }
                response?.details != null -> response.details.map { (field, message) ->
                    FieldError(field = field, message = message)
                }
                response?.error != null -> listOf(FieldError(field = "general", message = response.error))
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.w("KoraIDV", "Failed to parse validation error body", e)
            emptyList()
        }
    }

    internal fun mapException(e: Exception): KoraException {
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

// Internal model for parsing 422 validation error response bodies
internal data class ValidationErrorBody(
    val error: String? = null,
    val errors: List<ValidationFieldError>? = null,
    val details: Map<String, String>? = null
)

internal data class ValidationFieldError(
    val field: String? = null,
    val message: String? = null
)
