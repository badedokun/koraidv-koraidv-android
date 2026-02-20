package com.koraidv.sdk.api

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.koraidv.sdk.Configuration
import com.koraidv.sdk.Environment
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiIntegrationTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var apiService: ApiService
    private lateinit var sessionManager: SessionManager

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()

        val baseUrl = mockServer.url("/api/v1/").toString()

        apiService = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)

        val config = Configuration(
            apiKey = "ck_sandbox_test",
            tenantId = "test-tenant",
            baseUrl = baseUrl
        )
        val apiClient = ApiClient(config)
        sessionManager = SessionManager(config, apiClient)
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    // =====================================================================
    // Create Verification
    // =====================================================================

    @Test
    fun `createVerification sends correct request`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(verificationResponseJson())
                .setHeader("Content-Type", "application/json")
        )

        val response = apiService.createVerification(
            CreateVerificationRequest(externalId = "user-123", tier = "standard")
        )

        assertThat(response.isSuccessful).isTrue()
        assertThat(response.body()?.id).isEqualTo("ver-abc-123")
        assertThat(response.body()?.status).isEqualTo("document_required")

        val request = mockServer.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/v1/verifications")
        assertThat(request.body.readUtf8()).contains("user-123")
    }

    // =====================================================================
    // Get Verification
    // =====================================================================

    @Test
    fun `getVerification sends correct request`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(verificationResponseJson())
                .setHeader("Content-Type", "application/json")
        )

        val response = apiService.getVerification("ver-abc-123")

        assertThat(response.isSuccessful).isTrue()

        val request = mockServer.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/api/v1/verifications/ver-abc-123")
    }

    // =====================================================================
    // Upload Document
    // =====================================================================

    @Test
    fun `uploadDocument sends base64 image`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"documentId": "doc-1", "qualityScore": 0.95, "warnings": []}""")
                .setHeader("Content-Type", "application/json")
        )

        val response = apiService.uploadDocument(
            "ver-123",
            UploadDocumentRequest(documentType = "international_passport", imageBase64 = "base64data")
        )

        assertThat(response.isSuccessful).isTrue()
        assertThat(response.body()?.documentId).isEqualTo("doc-1")

        val request = mockServer.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/v1/verifications/ver-123/document")
        val body = request.body.readUtf8()
        assertThat(body).contains("base64data")
        assertThat(body).contains("international_passport")
    }

    // =====================================================================
    // Upload Document Back
    // =====================================================================

    @Test
    fun `uploadDocumentBack sends to correct endpoint`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"documentId": "doc-1", "qualityScore": 0.9, "warnings": []}""")
                .setHeader("Content-Type", "application/json")
        )

        val response = apiService.uploadDocumentBack(
            "ver-123",
            UploadDocumentBackRequest(imageBase64 = "back_image_data")
        )

        assertThat(response.isSuccessful).isTrue()
        val request = mockServer.takeRequest()
        assertThat(request.path).isEqualTo("/api/v1/verifications/ver-123/document/back")
    }

    // =====================================================================
    // Upload Selfie
    // =====================================================================

    @Test
    fun `uploadSelfie sends to correct endpoint`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"faceDetected": true, "qualityScore": 0.88}""")
                .setHeader("Content-Type", "application/json")
        )

        val response = apiService.uploadSelfie(
            "ver-123",
            UploadSelfieRequest(imageBase64 = "selfie_data")
        )

        assertThat(response.isSuccessful).isTrue()
        assertThat(response.body()?.faceDetected).isTrue()

        val request = mockServer.takeRequest()
        assertThat(request.path).isEqualTo("/api/v1/verifications/ver-123/selfie")
    }

    // =====================================================================
    // Liveness Session
    // =====================================================================

    @Test
    fun `createLivenessSession sends to correct endpoint`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id": "liveness-1", "challenges": [{"type": "blink", "completed": false}], "passed": false}""")
                .setHeader("Content-Type", "application/json")
        )

        val response = apiService.createLivenessSession("ver-123")

        assertThat(response.isSuccessful).isTrue()
        assertThat(response.body()?.id).isEqualTo("liveness-1")
        assertThat(response.body()?.challenges).hasSize(1)

        val request = mockServer.takeRequest()
        assertThat(request.path).isEqualTo("/api/v1/verifications/ver-123/liveness/session")
    }

    // =====================================================================
    // Submit Liveness Challenge
    // =====================================================================

    @Test
    fun `submitLivenessChallenge sends correct payload`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"type": "blink", "completed": true, "score": 0.95, "remainingChallenges": 2}""")
                .setHeader("Content-Type", "application/json")
        )

        val response = apiService.submitLivenessChallenge(
            "ver-123",
            SubmitLivenessChallengeRequest(challengeType = "blink", imageBase64 = "frame_data")
        )

        assertThat(response.isSuccessful).isTrue()
        assertThat(response.body()?.completed).isTrue()

        val request = mockServer.takeRequest()
        assertThat(request.path).isEqualTo("/api/v1/verifications/ver-123/liveness/challenge")
        assertThat(request.body.readUtf8()).contains("blink")
    }

    // =====================================================================
    // Complete Verification
    // =====================================================================

    @Test
    fun `completeVerification sends to correct endpoint`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(verificationResponseJson(status = "approved"))
                .setHeader("Content-Type", "application/json")
        )

        val response = apiService.completeVerification("ver-123")

        assertThat(response.isSuccessful).isTrue()
        assertThat(response.body()?.status).isEqualTo("approved")

        val request = mockServer.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/v1/verifications/ver-123/complete")
    }

    // =====================================================================
    // Document Types
    // =====================================================================

    @Test
    fun `getDocumentTypes sends correct request`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success": true, "documentTypes": [{"type": "passport", "name": "Passport", "requiresBack": false}], "countries": [{"code": "US", "name": "United States"}]}""")
                .setHeader("Content-Type", "application/json")
        )

        val response = apiService.getDocumentTypes("US")

        assertThat(response.isSuccessful).isTrue()
        assertThat(response.body()?.documentTypes).hasSize(1)
        assertThat(response.body()?.countries).hasSize(1)

        val request = mockServer.takeRequest()
        assertThat(request.path).isEqualTo("/api/v1/document-types?country=US")
    }

    @Test
    fun `getDocumentTypes without country omits query param`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success": true, "documentTypes": [], "countries": []}""")
                .setHeader("Content-Type", "application/json")
        )

        apiService.getDocumentTypes(null)

        val request = mockServer.takeRequest()
        assertThat(request.path).isEqualTo("/api/v1/document-types")
    }

    // =====================================================================
    // Error responses
    // =====================================================================

    @Test
    fun `401 response is not successful`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error": "Unauthorized"}""")
        )

        val response = apiService.getVerification("ver-123")
        assertThat(response.isSuccessful).isFalse()
        assertThat(response.code()).isEqualTo(401)
    }

    @Test
    fun `404 response is not successful`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error": "Not Found"}""")
        )

        val response = apiService.getVerification("nonexistent")
        assertThat(response.isSuccessful).isFalse()
        assertThat(response.code()).isEqualTo(404)
    }

    @Test
    fun `500 response is not successful`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error": "Internal Server Error"}""")
        )

        val response = apiService.createVerification(
            CreateVerificationRequest("user-1", "standard")
        )
        assertThat(response.isSuccessful).isFalse()
        assertThat(response.code()).isEqualTo(500)
    }

    // =====================================================================
    // Response parsing
    // =====================================================================

    @Test
    fun `verification response with all fields parses correctly`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fullVerificationResponseJson())
                .setHeader("Content-Type", "application/json")
        )

        val response = apiService.getVerification("ver-full")
        assertThat(response.isSuccessful).isTrue()

        val body = response.body()!!
        assertThat(body.documentVerification?.documentType).isEqualTo("passport")
        assertThat(body.faceVerification?.matchScore).isEqualTo(0.95)
        assertThat(body.livenessVerification?.isLive).isTrue()
        assertThat(body.scores?.overall).isEqualTo(91.0)
        assertThat(body.riskSignals).hasSize(1)
        assertThat(body.riskScore).isEqualTo(15)
    }

    // =====================================================================
    // Additional error responses
    // =====================================================================

    @Test
    fun `422 response is not successful`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setBody("""{"errors": [{"field": "document_type", "message": "unsupported"}]}""")
        )

        val response = apiService.createVerification(
            CreateVerificationRequest("user-1", "standard")
        )
        assertThat(response.isSuccessful).isFalse()
        assertThat(response.code()).isEqualTo(422)
    }

    @Test
    fun `429 response is not successful`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"error": "Rate limit exceeded"}""")
        )

        val response = apiService.getVerification("ver-123")
        assertThat(response.isSuccessful).isFalse()
        assertThat(response.code()).isEqualTo(429)
    }

    // =====================================================================
    // Additional response parsing
    // =====================================================================

    @Test
    fun `verification with null optional fields parses`() = runTest {
        val json = """{
            "id": "ver-null",
            "tier": "basic",
            "status": "pending",
            "createdAt": "2024-06-15T10:30:45.000Z",
            "updatedAt": "2024-06-15T10:30:45.000Z"
        }"""
        mockServer.enqueue(
            MockResponse().setResponseCode(200).setBody(json)
                .setHeader("Content-Type", "application/json")
        )
        val response = apiService.getVerification("ver-null")
        assertThat(response.isSuccessful).isTrue()
        assertThat(response.body()?.documentVerification).isNull()
        assertThat(response.body()?.scores).isNull()
        assertThat(response.body()?.riskSignals).isNull()
    }

    @Test
    fun `selfie response with quality issues parses`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"faceDetected": false, "qualityScore": 0.3, "qualityIssues": ["too_dark", "blurry"]}""")
                .setHeader("Content-Type", "application/json")
        )
        val response = apiService.uploadSelfie("ver-123", UploadSelfieRequest("data"))
        assertThat(response.isSuccessful).isTrue()
        assertThat(response.body()?.faceDetected).isFalse()
        assertThat(response.body()?.qualityIssues).containsExactly("too_dark", "blurry")
    }

    @Test
    fun `liveness challenge response with remaining challenges parses`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"type": "smile", "completed": false, "score": 0.3, "remainingChallenges": 2}""")
                .setHeader("Content-Type", "application/json")
        )
        val response = apiService.submitLivenessChallenge(
            "ver-123",
            SubmitLivenessChallengeRequest("smile", "frame")
        )
        assertThat(response.isSuccessful).isTrue()
        assertThat(response.body()?.completed).isFalse()
        assertThat(response.body()?.remainingChallenges).isEqualTo(2)
    }

    @Test
    fun `document upload with warnings parses`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"documentId": "doc-1", "qualityScore": 0.7, "warnings": ["slight_blur", "low_contrast"]}""")
                .setHeader("Content-Type", "application/json")
        )
        val response = apiService.uploadDocument(
            "ver-123",
            UploadDocumentRequest("passport", "base64data")
        )
        assertThat(response.isSuccessful).isTrue()
        assertThat(response.body()?.warnings).containsExactly("slight_blur", "low_contrast")
    }

    // =====================================================================
    // JSON helpers
    // =====================================================================

    private fun verificationResponseJson(status: String = "document_required"): String {
        return """{
            "id": "ver-abc-123",
            "externalId": "user-123",
            "tenantId": "tenant-1",
            "tier": "standard",
            "status": "$status",
            "createdAt": "2024-06-15T10:30:45.000Z",
            "updatedAt": "2024-06-15T10:30:45.000Z"
        }"""
    }

    private fun fullVerificationResponseJson(): String {
        return """{
            "id": "ver-full",
            "externalId": "user-456",
            "tenantId": "tenant-1",
            "tier": "enhanced",
            "status": "approved",
            "documentVerification": {
                "documentType": "passport",
                "documentNumber": "AB1234567",
                "firstName": "John",
                "lastName": "Doe",
                "dateOfBirth": "1990-01-15",
                "expirationDate": "2030-01-15",
                "issuingCountry": "US",
                "mrzValid": true,
                "authenticityScore": 0.95,
                "extractedFields": {"nationality": "US"}
            },
            "faceVerification": {
                "matchScore": 0.95,
                "matchResult": "match",
                "confidence": 0.92
            },
            "livenessVerification": {
                "livenessScore": 0.98,
                "isLive": true,
                "challengeResults": [
                    {"type": "blink", "passed": true, "confidence": 0.95},
                    {"type": "smile", "passed": true, "confidence": 0.88}
                ]
            },
            "scores": {
                "documentQuality": 95.0,
                "documentAuth": 90.0,
                "faceMatch": 88.0,
                "liveness": 92.0,
                "nameMatch": 100.0,
                "dataConsistency": 85.0,
                "overall": 91.0
            },
            "riskSignals": [
                {"code": "LOW_RISK", "severity": "low", "message": "Standard risk profile"}
            ],
            "riskScore": 15,
            "createdAt": "2024-06-15T10:30:45.000Z",
            "updatedAt": "2024-06-15T12:00:00.000Z",
            "completedAt": "2024-06-15T12:00:00.000Z"
        }"""
    }
}
