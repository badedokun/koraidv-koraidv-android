package com.koraidv.sdk

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KoraExceptionTest {

    @Test
    fun `NotConfigured has correct errorCode`() {
        val ex = KoraException.NotConfigured()
        assertThat(ex.errorCode).isEqualTo("NOT_CONFIGURED")
        assertThat(ex.message).contains("not configured")
    }

    @Test
    fun `Unauthorized has correct errorCode`() {
        val ex = KoraException.Unauthorized()
        assertThat(ex.errorCode).isEqualTo("UNAUTHORIZED")
    }

    @Test
    fun `Forbidden has correct errorCode`() {
        val ex = KoraException.Forbidden()
        assertThat(ex.errorCode).isEqualTo("FORBIDDEN")
    }

    @Test
    fun `NotFound has correct errorCode`() {
        val ex = KoraException.NotFound()
        assertThat(ex.errorCode).isEqualTo("NOT_FOUND")
    }

    @Test
    fun `RateLimited has recoverySuggestion`() {
        val ex = KoraException.RateLimited()
        assertThat(ex.errorCode).isEqualTo("RATE_LIMITED")
        assertThat(ex.recoverySuggestion).isNotNull()
    }

    @Test
    fun `ServerError includes status code`() {
        val ex = KoraException.ServerError(503)
        assertThat(ex.statusCode).isEqualTo(503)
        assertThat(ex.errorCode).isEqualTo("SERVER_ERROR")
        assertThat(ex.message).contains("503")
    }

    @Test
    fun `HttpError includes status code`() {
        val ex = KoraException.HttpError(418)
        assertThat(ex.statusCode).isEqualTo(418)
        assertThat(ex.message).contains("418")
    }

    @Test
    fun `NetworkError includes cause message`() {
        val ex = KoraException.NetworkError("connection reset")
        assertThat(ex.message).contains("connection reset")
        assertThat(ex.recoverySuggestion).isNotNull()
    }

    @Test
    fun `Timeout has recoverySuggestion`() {
        val ex = KoraException.Timeout()
        assertThat(ex.errorCode).isEqualTo("TIMEOUT")
        assertThat(ex.recoverySuggestion).isNotNull()
    }

    @Test
    fun `NoInternet has recoverySuggestion`() {
        val ex = KoraException.NoInternet()
        assertThat(ex.errorCode).isEqualTo("NO_INTERNET")
        assertThat(ex.recoverySuggestion).contains("Wi-Fi")
    }

    @Test
    fun `ValidationError includes field errors`() {
        val errors = listOf(
            FieldError("email", "is required"),
            FieldError("name", "too short")
        )
        val ex = KoraException.ValidationError(errors)
        assertThat(ex.errorCode).isEqualTo("VALIDATION_ERROR")
        assertThat(ex.errors).hasSize(2)
        assertThat(ex.message).contains("email")
        assertThat(ex.message).contains("name")
    }

    @Test
    fun `CameraAccessDenied has recoverySuggestion`() {
        val ex = KoraException.CameraAccessDenied()
        assertThat(ex.errorCode).isEqualTo("CAMERA_ACCESS_DENIED")
        assertThat(ex.recoverySuggestion).contains("Settings")
    }

    @Test
    fun `CaptureFailed includes reason`() {
        val ex = KoraException.CaptureFailed("lens error")
        assertThat(ex.message).contains("lens error")
    }

    @Test
    fun `QualityValidationFailed includes issues`() {
        val ex = KoraException.QualityValidationFailed(listOf("blur", "dark"))
        assertThat(ex.message).contains("blur")
        assertThat(ex.message).contains("dark")
    }

    @Test
    fun `Unknown includes message`() {
        val ex = KoraException.Unknown("something broke")
        assertThat(ex.errorCode).isEqualTo("UNKNOWN")
        assertThat(ex.message).isEqualTo("something broke")
    }

    @Test
    fun `UserCancelled has correct errorCode`() {
        val ex = KoraException.UserCancelled()
        assertThat(ex.errorCode).isEqualTo("USER_CANCELLED")
    }

    @Test
    fun `SessionExpired has correct errorCode`() {
        val ex = KoraException.SessionExpired()
        assertThat(ex.errorCode).isEqualTo("SESSION_EXPIRED")
    }

    @Test
    fun `ChallengeNotCompleted includes challenge type`() {
        val ex = KoraException.ChallengeNotCompleted("blink")
        assertThat(ex.message).contains("blink")
    }

    @Test
    fun `all exceptions extend Exception`() {
        val ex: Exception = KoraException.NotConfigured()
        assertThat(ex).isInstanceOf(Exception::class.java)
    }

    @Test
    fun `FieldError holds field and message`() {
        val fe = FieldError(field = "age", message = "must be >= 18")
        assertThat(fe.field).isEqualTo("age")
        assertThat(fe.message).isEqualTo("must be >= 18")
    }

    // =====================================================================
    // Remaining exception types coverage
    // =====================================================================

    @Test
    fun `InvalidApiKey has correct errorCode`() {
        val ex = KoraException.InvalidApiKey()
        assertThat(ex.errorCode).isEqualTo("INVALID_API_KEY")
    }

    @Test
    fun `InvalidTenantId has correct errorCode`() {
        val ex = KoraException.InvalidTenantId()
        assertThat(ex.errorCode).isEqualTo("INVALID_TENANT_ID")
    }

    @Test
    fun `CameraNotAvailable has correct errorCode`() {
        val ex = KoraException.CameraNotAvailable()
        assertThat(ex.errorCode).isEqualTo("CAMERA_NOT_AVAILABLE")
    }

    @Test
    fun `DocumentNotDetected has recoverySuggestion`() {
        val ex = KoraException.DocumentNotDetected()
        assertThat(ex.errorCode).isEqualTo("DOCUMENT_NOT_DETECTED")
        assertThat(ex.recoverySuggestion).isNotNull()
    }

    @Test
    fun `DocumentTypeNotSupported has correct errorCode`() {
        val ex = KoraException.DocumentTypeNotSupported()
        assertThat(ex.errorCode).isEqualTo("DOCUMENT_TYPE_NOT_SUPPORTED")
    }

    @Test
    fun `MrzReadFailed has correct errorCode`() {
        val ex = KoraException.MrzReadFailed()
        assertThat(ex.errorCode).isEqualTo("MRZ_READ_FAILED")
    }

    @Test
    fun `FaceNotDetected has recoverySuggestion`() {
        val ex = KoraException.FaceNotDetected()
        assertThat(ex.errorCode).isEqualTo("FACE_NOT_DETECTED")
        assertThat(ex.recoverySuggestion).isNotNull()
    }

    @Test
    fun `MultipleFacesDetected has correct errorCode`() {
        val ex = KoraException.MultipleFacesDetected()
        assertThat(ex.errorCode).isEqualTo("MULTIPLE_FACES_DETECTED")
    }

    @Test
    fun `FaceMatchFailed has correct errorCode`() {
        val ex = KoraException.FaceMatchFailed()
        assertThat(ex.errorCode).isEqualTo("FACE_MATCH_FAILED")
    }

    @Test
    fun `LivenessCheckFailed has correct errorCode`() {
        val ex = KoraException.LivenessCheckFailed()
        assertThat(ex.errorCode).isEqualTo("LIVENESS_CHECK_FAILED")
    }

    @Test
    fun `VerificationExpired has correct errorCode`() {
        val ex = KoraException.VerificationExpired()
        assertThat(ex.errorCode).isEqualTo("VERIFICATION_EXPIRED")
    }

    @Test
    fun `VerificationAlreadyCompleted has correct errorCode`() {
        val ex = KoraException.VerificationAlreadyCompleted()
        assertThat(ex.errorCode).isEqualTo("VERIFICATION_ALREADY_COMPLETED")
    }

    @Test
    fun `InvalidVerificationState includes state in message`() {
        val ex = KoraException.InvalidVerificationState("pending")
        assertThat(ex.message).contains("pending")
        assertThat(ex.errorCode).isEqualTo("INVALID_VERIFICATION_STATE")
    }

    @Test
    fun `ValidationError with empty errors has empty message`() {
        val ex = KoraException.ValidationError(emptyList())
        assertThat(ex.errors).isEmpty()
        assertThat(ex.message).isEmpty()
    }
}
