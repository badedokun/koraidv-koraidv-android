package com.koraidv.sdk

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Kora SDK Exception types.
 */
sealed class KoraException(
    override val message: String,
    val errorCode: String,
    val recoverySuggestion: String? = null
) : Exception(message), Parcelable {

    // Configuration Errors
    @Parcelize
    class NotConfigured : KoraException(
        message = "SDK not configured. Call KoraIDV.configure() first.",
        errorCode = "NOT_CONFIGURED"
    )

    @Parcelize
    class InvalidApiKey : KoraException(
        message = "Invalid API key provided.",
        errorCode = "INVALID_API_KEY"
    )

    @Parcelize
    class InvalidTenantId : KoraException(
        message = "Invalid tenant ID provided.",
        errorCode = "INVALID_TENANT_ID"
    )

    // Network Errors
    @Parcelize
    data class NetworkError(val errorCause: String) : KoraException(
        message = "Network error: $errorCause",
        errorCode = "NETWORK_ERROR",
        recoverySuggestion = "Check your internet connection and try again."
    )

    @Parcelize
    class Timeout : KoraException(
        message = "Request timed out. Please try again.",
        errorCode = "TIMEOUT",
        recoverySuggestion = "Please wait a moment and try again."
    )

    @Parcelize
    class NoInternet : KoraException(
        message = "No internet connection. Please check your network.",
        errorCode = "NO_INTERNET",
        recoverySuggestion = "Check your Wi-Fi or cellular connection and try again."
    )

    // HTTP Errors
    @Parcelize
    class Unauthorized : KoraException(
        message = "Authentication failed. Please check your API key.",
        errorCode = "UNAUTHORIZED"
    )

    @Parcelize
    class Forbidden : KoraException(
        message = "Access denied.",
        errorCode = "FORBIDDEN"
    )

    @Parcelize
    class NotFound : KoraException(
        message = "Resource not found.",
        errorCode = "NOT_FOUND"
    )

    @Parcelize
    data class ValidationError(val errors: List<FieldError>) : KoraException(
        message = errors.joinToString(", ") { "${it.field}: ${it.message}" },
        errorCode = "VALIDATION_ERROR"
    )

    @Parcelize
    class RateLimited : KoraException(
        message = "Rate limit exceeded. Please try again later.",
        errorCode = "RATE_LIMITED",
        recoverySuggestion = "Please wait a moment and try again."
    )

    @Parcelize
    data class ServerError(val statusCode: Int) : KoraException(
        message = "Server error ($statusCode). Please try again later.",
        errorCode = "SERVER_ERROR",
        recoverySuggestion = "Please wait a moment and try again."
    )

    @Parcelize
    data class HttpError(val statusCode: Int) : KoraException(
        message = "HTTP error ($statusCode).",
        errorCode = "HTTP_ERROR"
    )

    // Capture Errors
    @Parcelize
    class CameraAccessDenied : KoraException(
        message = "Camera access denied. Please enable camera access in Settings.",
        errorCode = "CAMERA_ACCESS_DENIED",
        recoverySuggestion = "Go to Settings > Apps and enable camera access for this app."
    )

    @Parcelize
    class CameraNotAvailable : KoraException(
        message = "Camera not available on this device.",
        errorCode = "CAMERA_NOT_AVAILABLE"
    )

    @Parcelize
    data class CaptureFailed(val reason: String) : KoraException(
        message = "Capture failed: $reason",
        errorCode = "CAPTURE_FAILED"
    )

    @Parcelize
    data class QualityValidationFailed(val issues: List<String>) : KoraException(
        message = "Quality check failed: ${issues.joinToString(", ")}",
        errorCode = "QUALITY_VALIDATION_FAILED",
        recoverySuggestion = "Hold the device steady and ensure good lighting."
    )

    // Document Errors
    @Parcelize
    class DocumentNotDetected : KoraException(
        message = "Document not detected. Please position the document within the frame.",
        errorCode = "DOCUMENT_NOT_DETECTED",
        recoverySuggestion = "Place the document on a flat, well-lit surface and center it in the frame."
    )

    @Parcelize
    class DocumentTypeNotSupported : KoraException(
        message = "This document type is not supported.",
        errorCode = "DOCUMENT_TYPE_NOT_SUPPORTED"
    )

    @Parcelize
    class MrzReadFailed : KoraException(
        message = "Could not read document MRZ. Please try again.",
        errorCode = "MRZ_READ_FAILED"
    )

    // Face Errors
    @Parcelize
    class FaceNotDetected : KoraException(
        message = "Face not detected. Please position your face within the frame.",
        errorCode = "FACE_NOT_DETECTED",
        recoverySuggestion = "Ensure good lighting and center your face in the oval guide."
    )

    @Parcelize
    class MultipleFacesDetected : KoraException(
        message = "Multiple faces detected. Please ensure only one face is visible.",
        errorCode = "MULTIPLE_FACES_DETECTED"
    )

    @Parcelize
    class FaceMatchFailed : KoraException(
        message = "Face match failed. Please try again.",
        errorCode = "FACE_MATCH_FAILED"
    )

    // Liveness Errors
    @Parcelize
    class LivenessCheckFailed : KoraException(
        message = "Liveness check failed. Please try again.",
        errorCode = "LIVENESS_CHECK_FAILED"
    )

    @Parcelize
    data class ChallengeNotCompleted(val challengeType: String) : KoraException(
        message = "Challenge '$challengeType' was not completed successfully.",
        errorCode = "CHALLENGE_NOT_COMPLETED"
    )

    @Parcelize
    class SessionExpired : KoraException(
        message = "Session expired. Please start a new verification.",
        errorCode = "SESSION_EXPIRED"
    )

    // Verification Errors
    @Parcelize
    class VerificationExpired : KoraException(
        message = "Verification expired. Please start a new verification.",
        errorCode = "VERIFICATION_EXPIRED"
    )

    @Parcelize
    class VerificationAlreadyCompleted : KoraException(
        message = "This verification has already been completed.",
        errorCode = "VERIFICATION_ALREADY_COMPLETED"
    )

    @Parcelize
    data class InvalidVerificationState(val state: String) : KoraException(
        message = "Invalid verification state: $state",
        errorCode = "INVALID_VERIFICATION_STATE"
    )

    // Generic Errors
    @Parcelize
    data class Unknown(override val message: String) : KoraException(
        message = message,
        errorCode = "UNKNOWN"
    )

    @Parcelize
    class UserCancelled : KoraException(
        message = "Verification cancelled by user.",
        errorCode = "USER_CANCELLED"
    )
}

/**
 * Field validation error.
 */
@Parcelize
data class FieldError(
    val field: String,
    val message: String
) : Parcelable
