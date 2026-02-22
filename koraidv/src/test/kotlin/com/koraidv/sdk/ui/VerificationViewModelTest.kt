package com.koraidv.sdk.ui

import com.google.common.truth.Truth.assertThat
import com.koraidv.sdk.*
import com.koraidv.sdk.api.DocumentSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class VerificationViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        KoraIDV.reset()
    }

    // =====================================================================
    // computeScoreBreakdown — with scores object
    // =====================================================================

    @Test
    fun `computeScoreBreakdown with perfect scores returns all 100`() {
        val verification = testVerification(
            scores = VerificationScores(100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0)
        )
        val breakdown = VerificationViewModel.computeScoreBreakdown(verification)
        assertThat(breakdown.liveness).isEqualTo(100)
        assertThat(breakdown.selfieMatch).isEqualTo(100)
        assertThat(breakdown.documentQuality).isEqualTo(100)
        assertThat(breakdown.nameMatch).isEqualTo(100)
        assertThat(breakdown.overallScore).isEqualTo(100)
    }

    @Test
    fun `computeScoreBreakdown with zero scores returns all 0`() {
        val verification = testVerification(
            scores = VerificationScores(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        )
        val breakdown = VerificationViewModel.computeScoreBreakdown(verification)
        assertThat(breakdown.liveness).isEqualTo(0)
        assertThat(breakdown.selfieMatch).isEqualTo(0)
        assertThat(breakdown.documentQuality).isEqualTo(0)
        assertThat(breakdown.nameMatch).isEqualTo(0)
        assertThat(breakdown.overallScore).isEqualTo(0)
    }

    @Test
    fun `computeScoreBreakdown with mixed scores computes correct average`() {
        // VerificationScores(documentQuality, documentAuth, faceMatch, liveness, nameMatch, dataConsistency, overall)
        val verification = testVerification(
            scores = VerificationScores(80.0, 70.0, 90.0, 95.0, 85.0, 75.0, 70.0, 88.0)
        )
        val breakdown = VerificationViewModel.computeScoreBreakdown(verification)
        assertThat(breakdown.liveness).isEqualTo(95)
        assertThat(breakdown.selfieMatch).isEqualTo(90)
        assertThat(breakdown.documentQuality).isEqualTo(80)
        assertThat(breakdown.nameMatch).isEqualTo(85)
        // overall uses the backend's weighted score directly (88.0)
        assertThat(breakdown.overallScore).isEqualTo(88)
    }

    @Test
    fun `computeScoreBreakdown coerces negative values to 0`() {
        val verification = testVerification(
            scores = VerificationScores(-10.0, 0.0, -5.0, -100.0, -20.0, 0.0, 0.0, 0.0)
        )
        val breakdown = VerificationViewModel.computeScoreBreakdown(verification)
        assertThat(breakdown.liveness).isEqualTo(0)
        assertThat(breakdown.selfieMatch).isEqualTo(0)
        assertThat(breakdown.documentQuality).isEqualTo(0)
        assertThat(breakdown.nameMatch).isEqualTo(0)
        assertThat(breakdown.overallScore).isEqualTo(0)
    }

    @Test
    fun `computeScoreBreakdown coerces values over 100 to 100`() {
        val verification = testVerification(
            scores = VerificationScores(150.0, 0.0, 200.0, 300.0, 999.0, 0.0, 0.0, 150.0)
        )
        val breakdown = VerificationViewModel.computeScoreBreakdown(verification)
        assertThat(breakdown.liveness).isEqualTo(100)
        assertThat(breakdown.selfieMatch).isEqualTo(100)
        assertThat(breakdown.documentQuality).isEqualTo(100)
        assertThat(breakdown.nameMatch).isEqualTo(100)
        assertThat(breakdown.overallScore).isEqualTo(100)
    }

    @Test
    fun `computeScoreBreakdown truncates decimal values`() {
        val verification = testVerification(
            scores = VerificationScores(87.9, 0.0, 92.1, 95.5, 88.7, 0.0, 0.0, 0.0)
        )
        val breakdown = VerificationViewModel.computeScoreBreakdown(verification)
        assertThat(breakdown.liveness).isEqualTo(95)
        assertThat(breakdown.selfieMatch).isEqualTo(92)
        assertThat(breakdown.documentQuality).isEqualTo(87)
        assertThat(breakdown.nameMatch).isEqualTo(88)
    }

    @Test
    fun `computeScoreBreakdown overall uses backend weighted score`() {
        val verification = testVerification(
            scores = VerificationScores(60.0, 0.0, 80.0, 40.0, 100.0, 0.0, 0.0, 99.0)
        )
        val breakdown = VerificationViewModel.computeScoreBreakdown(verification)
        // overall uses the backend's weighted score directly (99.0)
        assertThat(breakdown.overallScore).isEqualTo(99)
    }

    @Test
    fun `computeScoreBreakdown overall uses backend overall field`() {
        val verification = testVerification(
            scores = VerificationScores(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 99.0)
        )
        val breakdown = VerificationViewModel.computeScoreBreakdown(verification)
        // overall uses the backend's weighted score directly (99.0)
        assertThat(breakdown.overallScore).isEqualTo(99)
    }

    // =====================================================================
    // computeScoreBreakdown — fallback paths (no scores object)
    // =====================================================================

    @Test
    fun `computeScoreBreakdown uses livenessVerification fallback`() {
        val verification = testVerification(
            livenessVerification = LivenessVerification(livenessScore = 92.0, isLive = true)
        )
        val breakdown = VerificationViewModel.computeScoreBreakdown(verification)
        assertThat(breakdown.liveness).isEqualTo(92)
    }

    @Test
    fun `computeScoreBreakdown uses faceVerification fallback`() {
        val verification = testVerification(
            faceVerification = FaceVerification(matchScore = 88.5, matchResult = "match", confidence = 0.9)
        )
        val breakdown = VerificationViewModel.computeScoreBreakdown(verification)
        assertThat(breakdown.selfieMatch).isEqualTo(88)
    }

    @Test
    fun `computeScoreBreakdown uses documentVerification authenticityScore fallback`() {
        val verification = testVerification(
            documentVerification = DocumentVerification(documentType = "passport", authenticityScore = 0.85)
        )
        val breakdown = VerificationViewModel.computeScoreBreakdown(verification)
        assertThat(breakdown.documentQuality).isEqualTo(85)
    }

    @Test
    fun `computeScoreBreakdown nameMatch 100 when firstName present`() {
        val verification = testVerification(
            documentVerification = DocumentVerification(documentType = "passport", firstName = "John")
        )
        val breakdown = VerificationViewModel.computeScoreBreakdown(verification)
        assertThat(breakdown.nameMatch).isEqualTo(100)
    }

    @Test
    fun `computeScoreBreakdown nameMatch 0 when no firstName`() {
        val verification = testVerification(
            documentVerification = DocumentVerification(documentType = "passport", firstName = null)
        )
        val breakdown = VerificationViewModel.computeScoreBreakdown(verification)
        assertThat(breakdown.nameMatch).isEqualTo(0)
    }

    @Test
    fun `computeScoreBreakdown with no data returns all zeros`() {
        val verification = testVerification()
        val breakdown = VerificationViewModel.computeScoreBreakdown(verification)
        assertThat(breakdown.liveness).isEqualTo(0)
        assertThat(breakdown.selfieMatch).isEqualTo(0)
        assertThat(breakdown.documentQuality).isEqualTo(0)
        assertThat(breakdown.nameMatch).isEqualTo(0)
        assertThat(breakdown.overallScore).isEqualTo(0)
    }

    @Test
    fun `computeScoreBreakdown fallback document null authenticityScore returns 0`() {
        val verification = testVerification(
            documentVerification = DocumentVerification(documentType = "passport", authenticityScore = null)
        )
        val breakdown = VerificationViewModel.computeScoreBreakdown(verification)
        assertThat(breakdown.documentQuality).isEqualTo(0)
    }

    @Test
    fun `computeScoreBreakdown fallback liveness coerces over 100`() {
        val verification = testVerification(
            livenessVerification = LivenessVerification(livenessScore = 150.0, isLive = true)
        )
        val breakdown = VerificationViewModel.computeScoreBreakdown(verification)
        assertThat(breakdown.liveness).isEqualTo(100)
    }

    @Test
    fun `computeScoreBreakdown fallback faceVerification coerces negative`() {
        val verification = testVerification(
            faceVerification = FaceVerification(matchScore = -10.0, matchResult = "no_match", confidence = 0.0)
        )
        val breakdown = VerificationViewModel.computeScoreBreakdown(verification)
        assertThat(breakdown.selfieMatch).isEqualTo(0)
    }

    // =====================================================================
    // countryCodeToName
    // =====================================================================

    @Test
    fun `countryCodeToName returns US name`() {
        assertThat(VerificationViewModel.countryCodeToName("US")).isEqualTo("United States")
    }

    @Test
    fun `countryCodeToName returns GB name`() {
        assertThat(VerificationViewModel.countryCodeToName("GB")).isEqualTo("United Kingdom")
    }

    @Test
    fun `countryCodeToName returns NG name`() {
        assertThat(VerificationViewModel.countryCodeToName("NG")).isEqualTo("Nigeria")
    }

    @Test
    fun `countryCodeToName returns GH name`() {
        assertThat(VerificationViewModel.countryCodeToName("GH")).isEqualTo("Ghana")
    }

    @Test
    fun `countryCodeToName returns INTL name`() {
        assertThat(VerificationViewModel.countryCodeToName("INTL")).isEqualTo("International")
    }

    @Test
    fun `countryCodeToName returns code itself for unknown`() {
        assertThat(VerificationViewModel.countryCodeToName("XX")).isEqualTo("XX")
    }

    @Test
    fun `countryCodeToName maps all known countries`() {
        val expected = mapOf(
            "US" to "United States", "GB" to "United Kingdom", "DE" to "Germany",
            "FR" to "France", "ES" to "Spain", "IT" to "Italy",
            "GH" to "Ghana", "NG" to "Nigeria", "KE" to "Kenya",
            "ZA" to "South Africa", "INTL" to "International"
        )
        expected.forEach { (code, name) ->
            assertThat(VerificationViewModel.countryCodeToName(code)).isEqualTo(name)
        }
    }

    @Test
    fun `countryCodeToName resolves unlisted ISO country via Locale`() {
        // JP was not in the old hardcoded map — now resolved via java.util.Locale
        assertThat(VerificationViewModel.countryCodeToName("JP")).isEqualTo("Japan")
    }

    // =====================================================================
    // ProcessingStep enum
    // =====================================================================

    @Test
    fun `ProcessingStep ANALYZING has correct label`() {
        assertThat(ProcessingStep.ANALYZING.label).isEqualTo("Analyzing document")
    }

    @Test
    fun `ProcessingStep CHECKING_QUALITY has correct label`() {
        assertThat(ProcessingStep.CHECKING_QUALITY.label).isEqualTo("Matching identity")
    }

    @Test
    fun `ProcessingStep FINALIZING has correct label`() {
        assertThat(ProcessingStep.FINALIZING.label).isEqualTo("Finalizing")
    }

    @Test
    fun `ProcessingStep has exactly 3 values`() {
        assertThat(ProcessingStep.entries).hasSize(3)
    }

    // =====================================================================
    // ScoreBreakdown data class
    // =====================================================================

    @Test
    fun `ScoreBreakdown stores all fields`() {
        val breakdown = ScoreBreakdown(
            liveness = 95, screening = 90, nameMatch = 100, documentQuality = 88,
            selfieMatch = 92, overallScore = 93
        )
        assertThat(breakdown.liveness).isEqualTo(95)
        assertThat(breakdown.nameMatch).isEqualTo(100)
        assertThat(breakdown.documentQuality).isEqualTo(88)
        assertThat(breakdown.selfieMatch).isEqualTo(92)
        assertThat(breakdown.overallScore).isEqualTo(93)
    }

    @Test
    fun `ScoreBreakdown equality works`() {
        val a = ScoreBreakdown(95, 90, 100, 88, 92, 93)
        val b = ScoreBreakdown(95, 90, 100, 88, 92, 93)
        assertThat(a).isEqualTo(b)
    }

    // =====================================================================
    // VerificationState sealed class
    // =====================================================================

    @Test
    fun `VerificationState Loading is singleton`() {
        assertThat(VerificationState.Loading).isEqualTo(VerificationState.Loading)
    }

    @Test
    fun `VerificationState Consent is singleton`() {
        assertThat(VerificationState.Consent).isEqualTo(VerificationState.Consent)
    }

    @Test
    fun `VerificationState SelfieCapture is singleton`() {
        assertThat(VerificationState.SelfieCapture).isEqualTo(VerificationState.SelfieCapture)
    }

    @Test
    fun `VerificationState LivenessCheck is singleton`() {
        assertThat(VerificationState.LivenessCheck).isEqualTo(VerificationState.LivenessCheck)
    }

    @Test
    fun `VerificationState DocumentCapture holds document info`() {
        val state = VerificationState.DocumentCapture(
            documentTypeCode = "international_passport",
            documentDisplayName = "Passport",
            requiresBack = false,
            side = DocumentSide.FRONT
        )
        assertThat(state.documentTypeCode).isEqualTo("international_passport")
        assertThat(state.documentDisplayName).isEqualTo("Passport")
        assertThat(state.side).isEqualTo(DocumentSide.FRONT)
        assertThat(state.requiresBack).isFalse()
    }

    @Test
    fun `VerificationState Processing defaults to ANALYZING`() {
        val state = VerificationState.Processing()
        assertThat(state.step).isEqualTo(ProcessingStep.ANALYZING)
    }

    @Test
    fun `VerificationState Error holds exception and canRetry`() {
        val error = KoraException.Timeout()
        val state = VerificationState.Error(error, canRetry = true)
        assertThat(state.error).isInstanceOf(KoraException.Timeout::class.java)
        assertThat(state.canRetry).isTrue()
    }

    @Test
    fun `VerificationState Error defaults canRetry to true`() {
        val state = VerificationState.Error(KoraException.Unknown("test"))
        assertThat(state.canRetry).isTrue()
    }

    @Test
    fun `VerificationState Complete holds verification`() {
        val v = testVerification()
        val state = VerificationState.Complete(v)
        assertThat(state.verification.id).isEqualTo("ver-test")
    }

    @Test
    fun `VerificationState ExpiredDocument holds verification`() {
        val v = testVerification(status = VerificationStatus.EXPIRED)
        val state = VerificationState.ExpiredDocument(v)
        assertThat(state.verification.status).isEqualTo(VerificationStatus.EXPIRED)
    }

    @Test
    fun `VerificationState ManualReview holds verification`() {
        val v = testVerification(status = VerificationStatus.REVIEW_REQUIRED)
        val state = VerificationState.ManualReview(v)
        assertThat(state.verification.status).isEqualTo(VerificationStatus.REVIEW_REQUIRED)
    }

    // =====================================================================
    // ViewModel initialization and state
    // =====================================================================

    @Test
    fun `initial state is Loading`() {
        val vm = VerificationViewModel()
        assertThat(vm.state.value).isEqualTo(VerificationState.Loading)
    }

    @Test
    fun `initialize without KoraIDV configured sets Error NotConfigured`() {
        KoraIDV.reset()
        val vm = VerificationViewModel()
        vm.initialize(VerificationRequest(externalId = "user-1"))
        assertThat(vm.state.value).isInstanceOf(VerificationState.Error::class.java)
        val errorState = vm.state.value as VerificationState.Error
        assertThat(errorState.error).isInstanceOf(KoraException.NotConfigured::class.java)
    }

    @Test
    fun `initialize with KoraIDV configured sets Consent`() {
        KoraIDV.configure(Configuration(apiKey = "ck_sandbox_test", tenantId = "t-1"))
        val vm = VerificationViewModel()
        vm.initialize(VerificationRequest(externalId = "user-1"))
        assertThat(vm.state.value).isEqualTo(VerificationState.Consent)
    }

    @Test
    fun `initializeForResume without KoraIDV configured sets Error`() {
        KoraIDV.reset()
        val vm = VerificationViewModel()
        vm.initializeForResume("ver-123")
        assertThat(vm.state.value).isInstanceOf(VerificationState.Error::class.java)
    }

    @Test
    fun `handleError sets Error state`() {
        val vm = VerificationViewModel()
        vm.handleError(KoraException.Timeout())
        assertThat(vm.state.value).isInstanceOf(VerificationState.Error::class.java)
        val errorState = vm.state.value as VerificationState.Error
        assertThat(errorState.error).isInstanceOf(KoraException.Timeout::class.java)
    }

    @Test
    fun `handleError with different exception types`() {
        val vm = VerificationViewModel()
        vm.handleError(KoraException.NoInternet())
        val errorState = vm.state.value as VerificationState.Error
        assertThat(errorState.error).isInstanceOf(KoraException.NoInternet::class.java)
    }

    @Test
    fun `retry from Error without doc type goes to Consent`() {
        val vm = VerificationViewModel()
        vm.handleError(KoraException.Timeout())
        vm.retry()
        assertThat(vm.state.value).isEqualTo(VerificationState.Consent)
    }

    @Test
    fun `retry from non-error state is no-op`() {
        val vm = VerificationViewModel()
        // State is Loading (initial)
        vm.retry()
        assertThat(vm.state.value).isEqualTo(VerificationState.Loading)
    }

    @Test
    fun `getSessionManager returns null before initialize`() {
        val vm = VerificationViewModel()
        assertThat(vm.getSessionManager()).isNull()
    }

    @Test
    fun `getCurrentVerification returns null before flow`() {
        val vm = VerificationViewModel()
        assertThat(vm.getCurrentVerification()).isNull()
    }

    @Test
    fun `getSelectedCountry returns null initially`() {
        val vm = VerificationViewModel()
        assertThat(vm.getSelectedCountry()).isNull()
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private fun testVerification(
        status: VerificationStatus = VerificationStatus.APPROVED,
        scores: VerificationScores? = null,
        livenessVerification: LivenessVerification? = null,
        faceVerification: FaceVerification? = null,
        documentVerification: DocumentVerification? = null
    ): Verification {
        return Verification(
            id = "ver-test",
            externalId = "ext-1",
            tenantId = "t-1",
            tier = "standard",
            status = status,
            scores = scores,
            livenessVerification = livenessVerification,
            faceVerification = faceVerification,
            documentVerification = documentVerification,
            createdAt = Date(),
            updatedAt = Date()
        )
    }
}
