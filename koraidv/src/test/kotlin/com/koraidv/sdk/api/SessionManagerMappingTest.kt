package com.koraidv.sdk.api

import com.google.common.truth.Truth.assertThat
import com.koraidv.sdk.*
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.*

class SessionManagerMappingTest {

    private lateinit var sessionManager: SessionManager

    @Before
    fun setUp() {
        val config = Configuration(apiKey = "ck_sandbox_test", tenantId = "test-tenant")
        val apiClient = mockk<ApiClient>(relaxed = true)
        sessionManager = SessionManager(config, apiClient)
    }

    // =====================================================================
    // mapHttpError
    // =====================================================================

    @Test
    fun `mapHttpError 401 returns Unauthorized`() {
        val error = sessionManager.mapHttpError(401)
        assertThat(error).isInstanceOf(KoraException.Unauthorized::class.java)
    }

    @Test
    fun `mapHttpError 403 returns Forbidden`() {
        val error = sessionManager.mapHttpError(403)
        assertThat(error).isInstanceOf(KoraException.Forbidden::class.java)
    }

    @Test
    fun `mapHttpError 404 returns NotFound`() {
        val error = sessionManager.mapHttpError(404)
        assertThat(error).isInstanceOf(KoraException.NotFound::class.java)
    }

    @Test
    fun `mapHttpError 429 returns RateLimited`() {
        val error = sessionManager.mapHttpError(429)
        assertThat(error).isInstanceOf(KoraException.RateLimited::class.java)
    }

    @Test
    fun `mapHttpError 500 returns ServerError`() {
        val error = sessionManager.mapHttpError(500)
        assertThat(error).isInstanceOf(KoraException.ServerError::class.java)
        assertThat((error as KoraException.ServerError).statusCode).isEqualTo(500)
    }

    @Test
    fun `mapHttpError 502 returns ServerError`() {
        val error = sessionManager.mapHttpError(502)
        assertThat(error).isInstanceOf(KoraException.ServerError::class.java)
    }

    @Test
    fun `mapHttpError 503 returns ServerError`() {
        val error = sessionManager.mapHttpError(503)
        assertThat(error).isInstanceOf(KoraException.ServerError::class.java)
    }

    @Test
    fun `mapHttpError 418 returns HttpError`() {
        val error = sessionManager.mapHttpError(418)
        assertThat(error).isInstanceOf(KoraException.HttpError::class.java)
        assertThat((error as KoraException.HttpError).statusCode).isEqualTo(418)
    }

    @Test
    fun `mapHttpError 422 returns ValidationError`() {
        val error = sessionManager.mapHttpError(422)
        assertThat(error).isInstanceOf(KoraException.ValidationError::class.java)
    }

    @Test
    fun `mapHttpError 422 with error array body parses field errors`() {
        val body = """{"errors": [{"field": "email", "message": "is required"}, {"field": "name", "message": "too short"}]}"""
        val error = sessionManager.mapHttpError(422, body)
        assertThat(error).isInstanceOf(KoraException.ValidationError::class.java)
        val validationError = error as KoraException.ValidationError
        assertThat(validationError.errors).hasSize(2)
        assertThat(validationError.errors[0].field).isEqualTo("email")
        assertThat(validationError.errors[1].message).isEqualTo("too short")
    }

    @Test
    fun `mapHttpError 422 with details map body parses field errors`() {
        val body = """{"details": {"email": "invalid format", "age": "must be >= 18"}}"""
        val error = sessionManager.mapHttpError(422, body)
        assertThat(error).isInstanceOf(KoraException.ValidationError::class.java)
        val validationError = error as KoraException.ValidationError
        assertThat(validationError.errors).hasSize(2)
    }

    @Test
    fun `mapHttpError 422 with simple error body`() {
        val body = """{"error": "Invalid document type"}"""
        val error = sessionManager.mapHttpError(422, body)
        assertThat(error).isInstanceOf(KoraException.ValidationError::class.java)
        val validationError = error as KoraException.ValidationError
        assertThat(validationError.errors).hasSize(1)
        assertThat(validationError.errors[0].message).isEqualTo("Invalid document type")
    }

    @Test
    fun `mapHttpError 422 with null body returns empty errors`() {
        val error = sessionManager.mapHttpError(422, null)
        assertThat(error).isInstanceOf(KoraException.ValidationError::class.java)
        assertThat((error as KoraException.ValidationError).errors).isEmpty()
    }

    @Test
    fun `mapHttpError 422 with malformed body returns empty errors`() {
        val error = sessionManager.mapHttpError(422, "not json at all")
        assertThat(error).isInstanceOf(KoraException.ValidationError::class.java)
        assertThat((error as KoraException.ValidationError).errors).isEmpty()
    }

    // =====================================================================
    // parseValidationErrors
    // =====================================================================

    @Test
    fun `parseValidationErrors with blank body returns empty`() {
        assertThat(sessionManager.parseValidationErrors("")).isEmpty()
        assertThat(sessionManager.parseValidationErrors(null)).isEmpty()
        assertThat(sessionManager.parseValidationErrors("   ")).isEmpty()
    }

    @Test
    fun `parseValidationErrors with empty JSON object returns empty`() {
        assertThat(sessionManager.parseValidationErrors("{}")).isEmpty()
    }

    // =====================================================================
    // mapException
    // =====================================================================

    @Test
    fun `mapException maps UnknownHostException to NoInternet`() {
        val error = sessionManager.mapException(UnknownHostException("host not found"))
        assertThat(error).isInstanceOf(KoraException.NoInternet::class.java)
    }

    @Test
    fun `mapException maps SocketTimeoutException to Timeout`() {
        val error = sessionManager.mapException(SocketTimeoutException("timed out"))
        assertThat(error).isInstanceOf(KoraException.Timeout::class.java)
    }

    @Test
    fun `mapException maps IOException to NetworkError`() {
        val error = sessionManager.mapException(java.io.IOException("connection reset"))
        assertThat(error).isInstanceOf(KoraException.NetworkError::class.java)
        assertThat(error.message).contains("connection reset")
    }

    @Test
    fun `mapException maps generic Exception to Unknown`() {
        val error = sessionManager.mapException(IllegalStateException("bad state"))
        assertThat(error).isInstanceOf(KoraException.Unknown::class.java)
        assertThat(error.message).contains("bad state")
    }

    // =====================================================================
    // parseDate
    // =====================================================================

    @Test
    fun `parseDate parses ISO format with milliseconds`() {
        val date = sessionManager.parseDate("2024-06-15T10:30:45.123Z")
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = date }
        assertThat(cal.get(Calendar.YEAR)).isEqualTo(2024)
        assertThat(cal.get(Calendar.MONTH)).isEqualTo(Calendar.JUNE)
        assertThat(cal.get(Calendar.DAY_OF_MONTH)).isEqualTo(15)
    }

    @Test
    fun `parseDate parses ISO format without milliseconds`() {
        val date = sessionManager.parseDate("2024-06-15T10:30:45Z")
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = date }
        assertThat(cal.get(Calendar.YEAR)).isEqualTo(2024)
    }

    @Test
    fun `parseDate returns current date for unparseable string`() {
        val before = Date()
        val date = sessionManager.parseDate("not-a-date")
        val after = Date()
        assertThat(date.time).isAtLeast(before.time)
        assertThat(date.time).isAtMost(after.time)
    }

    // =====================================================================
    // Session management
    // =====================================================================

    @Test
    fun `currentVerification is initially null`() {
        assertThat(sessionManager.currentVerification).isNull()
    }

    @Test
    fun `resetSession clears currentVerification`() {
        sessionManager.resetSession()
        assertThat(sessionManager.currentVerification).isNull()
    }

    @Test
    fun `isSessionTimedOut returns false before any session`() {
        assertThat(sessionManager.isSessionTimedOut).isFalse()
    }

    // =====================================================================
    // Additional mapHttpError coverage
    // =====================================================================

    @Test
    fun `mapHttpError 400 returns HttpError`() {
        val error = sessionManager.mapHttpError(400)
        assertThat(error).isInstanceOf(KoraException.HttpError::class.java)
        assertThat((error as KoraException.HttpError).statusCode).isEqualTo(400)
    }

    @Test
    fun `mapHttpError 599 returns ServerError`() {
        val error = sessionManager.mapHttpError(599)
        assertThat(error).isInstanceOf(KoraException.ServerError::class.java)
    }

    // =====================================================================
    // Additional parseValidationErrors coverage
    // =====================================================================

    @Test
    fun `parseValidationErrors with null field uses unknown`() {
        val body = """{"errors": [{"message": "something wrong"}]}"""
        val errors = sessionManager.parseValidationErrors(body)
        assertThat(errors).hasSize(1)
        assertThat(errors[0].field).isEqualTo("unknown")
    }

    @Test
    fun `parseValidationErrors with null message uses default`() {
        val body = """{"errors": [{"field": "email"}]}"""
        val errors = sessionManager.parseValidationErrors(body)
        assertThat(errors).hasSize(1)
        assertThat(errors[0].message).isEqualTo("Validation failed")
    }

    @Test
    fun `parseValidationErrors with empty errors array returns empty`() {
        val body = """{"errors": []}"""
        val errors = sessionManager.parseValidationErrors(body)
        assertThat(errors).isEmpty()
    }

    // =====================================================================
    // Additional parseDate coverage
    // =====================================================================

    @Test
    fun `parseDate preserves hour and minute for millis format`() {
        val date = sessionManager.parseDate("2024-12-25T14:30:00.000Z")
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = date }
        assertThat(cal.get(Calendar.HOUR_OF_DAY)).isEqualTo(14)
        assertThat(cal.get(Calendar.MINUTE)).isEqualTo(30)
    }

    @Test
    fun `parseDate preserves hour and minute for no-millis format`() {
        val date = sessionManager.parseDate("2024-12-25T14:30:00Z")
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = date }
        assertThat(cal.get(Calendar.HOUR_OF_DAY)).isEqualTo(14)
        assertThat(cal.get(Calendar.MINUTE)).isEqualTo(30)
    }

    // =====================================================================
    // Additional session management coverage
    // =====================================================================

    @Test
    fun `refreshSession does not crash and prevents timeout`() {
        sessionManager.refreshSession()
        assertThat(sessionManager.isSessionTimedOut).isFalse()
    }
}
