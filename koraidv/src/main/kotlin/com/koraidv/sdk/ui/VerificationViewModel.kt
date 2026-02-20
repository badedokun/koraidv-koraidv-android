package com.koraidv.sdk.ui

import android.content.Context
import android.util.Log
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koraidv.sdk.*
import com.koraidv.sdk.api.*
import com.koraidv.sdk.liveness.LivenessResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Verification flow state
 */
sealed class VerificationState {
    data object Loading : VerificationState()
    data object Consent : VerificationState()
    data class CountrySelection(val countries: List<CountryInfo>) : VerificationState()
    data class DocumentSelection(
        val documentTypes: List<DocumentTypeInfo>,
        val selectedCountry: CountryInfo?
    ) : VerificationState()
    data class DocumentCapture(
        val documentTypeCode: String,
        val documentDisplayName: String,
        val requiresBack: Boolean,
        val side: DocumentSide
    ) : VerificationState()
    data object SelfieCapture : VerificationState()
    data object LivenessCheck : VerificationState()
    data class Processing(val step: ProcessingStep = ProcessingStep.ANALYZING) : VerificationState()
    data class Complete(val verification: Verification) : VerificationState()
    data class ExpiredDocument(val verification: Verification) : VerificationState()
    data class ManualReview(val verification: Verification) : VerificationState()
    data class Error(val error: KoraException, val canRetry: Boolean = true) : VerificationState()
}

enum class ProcessingStep(val label: String) {
    ANALYZING("Document analyzed"),
    CHECKING_QUALITY("Checking face match"),
    FINALIZING("Finalizing results")
}

/**
 * Score breakdown metric for result screens
 */
data class ScoreBreakdown(
    val liveness: Int,
    val nameMatch: Int,
    val documentQuality: Int,
    val selfieMatch: Int,
    val overallScore: Int
)

/**
 * ViewModel for verification flow
 */
class VerificationViewModel : ViewModel() {

    private val _state = MutableStateFlow<VerificationState>(VerificationState.Loading)
    val state: StateFlow<VerificationState> = _state.asStateFlow()

    private var sessionManager: SessionManager? = null
    private var request: VerificationRequest? = null
    private var currentVerification: Verification? = null

    // Document selection state
    private var selectedDocumentTypeCode: String? = null
    private var selectedDocumentDisplayName: String? = null
    private var selectedDocumentRequiresBack: Boolean = false
    private var selectedCountry: CountryInfo? = null
    private var documentFrontCaptured = false
    private var frontUploadJob: Job? = null
    private var frontUploadResult: Result<DocumentUploadResult>? = null

    fun initialize(request: VerificationRequest) {
        this.request = request

        val apiClient = KoraIDV.apiClient ?: run {
            _state.value = VerificationState.Error(KoraException.NotConfigured())
            return
        }

        sessionManager = SessionManager(KoraIDV.getConfiguration(), apiClient)
        _state.value = VerificationState.Consent
    }

    fun preWarmCamera(context: Context) {
        ProcessCameraProvider.getInstance(context)
    }

    fun initializeForResume(verificationId: String) {
        val apiClient = KoraIDV.apiClient ?: run {
            _state.value = VerificationState.Error(KoraException.NotConfigured())
            return
        }

        sessionManager = SessionManager(KoraIDV.getConfiguration(), apiClient)
        _state.value = VerificationState.Loading

        viewModelScope.launch {
            val manager = sessionManager ?: return@launch

            val result = manager.getVerification(verificationId)

            result.fold(
                onSuccess = { verification ->
                    currentVerification = verification
                    _state.value = mapStatusToState(verification)
                },
                onFailure = { error ->
                    _state.value = VerificationState.Error(
                        error as? KoraException ?: KoraException.Unknown(error.message ?: "Unknown error")
                    )
                }
            )
        }
    }

    private fun mapStatusToState(verification: Verification): VerificationState {
        return when (verification.status) {
            VerificationStatus.PENDING -> VerificationState.Consent
            VerificationStatus.DOCUMENT_REQUIRED -> {
                VerificationState.Loading
            }
            VerificationStatus.SELFIE_REQUIRED -> VerificationState.SelfieCapture
            VerificationStatus.LIVENESS_REQUIRED -> VerificationState.LivenessCheck
            VerificationStatus.PROCESSING -> VerificationState.Complete(verification)
            VerificationStatus.APPROVED -> VerificationState.Complete(verification)
            VerificationStatus.REJECTED -> VerificationState.Complete(verification)
            VerificationStatus.REVIEW_REQUIRED -> VerificationState.ManualReview(verification)
            VerificationStatus.EXPIRED -> VerificationState.ExpiredDocument(verification)
        }
    }

    fun acceptConsent() {
        viewModelScope.launch {
            _state.value = VerificationState.Loading

            val req = request ?: return@launch
            val manager = sessionManager ?: return@launch

            val result = manager.createVerification(req.externalId, req.tier)

            result.fold(
                onSuccess = { verification ->
                    currentVerification = verification
                    loadCountries()
                },
                onFailure = { error ->
                    _state.value = VerificationState.Error(
                        error as? KoraException ?: KoraException.Unknown(error.message ?: "Unknown error")
                    )
                }
            )
        }
    }

    private suspend fun loadCountries() {
        val manager = sessionManager ?: return

        Log.d("KoraIDV", "loadCountries: calling getDocumentTypes(country=null)")
        val result = manager.getDocumentTypes(country = null)
        result.fold(
            onSuccess = { docTypesResult ->
                Log.d("KoraIDV", "loadCountries: success - ${docTypesResult.countries.size} countries, ${docTypesResult.documentTypes.size} doc types")
                if (docTypesResult.countries.isNotEmpty()) {
                    _state.value = VerificationState.CountrySelection(docTypesResult.countries)
                } else {
                    _state.value = VerificationState.DocumentSelection(
                        documentTypes = docTypesResult.documentTypes,
                        selectedCountry = null
                    )
                }
            },
            onFailure = { error ->
                Log.e("KoraIDV", "loadCountries: FAILED - ${error.message}", error)
                val countryMap = buildFallbackCountryMap()
                if (countryMap.size > 1) {
                    val countries = countryMap.keys.map { code ->
                        CountryInfo(
                            code = code,
                            name = countryCodeToName(code),
                            flagEmoji = null
                        )
                    }
                    _state.value = VerificationState.CountrySelection(countries)
                } else {
                    val allTypes = KoraIDV.getConfiguration().documentTypes.map { it.toDocumentTypeInfo() }
                    _state.value = VerificationState.DocumentSelection(
                        documentTypes = allTypes,
                        selectedCountry = countryMap.keys.firstOrNull()?.let {
                            CountryInfo(it, countryCodeToName(it), null)
                        }
                    )
                }
            }
        )
    }

    fun selectCountry(country: CountryInfo) {
        selectedCountry = country
        viewModelScope.launch {
            _state.value = VerificationState.Loading
            val manager = sessionManager ?: return@launch

            val result = manager.getDocumentTypes(country = country.code)
            result.fold(
                onSuccess = { docTypesResult ->
                    _state.value = VerificationState.DocumentSelection(
                        documentTypes = docTypesResult.documentTypes,
                        selectedCountry = country
                    )
                },
                onFailure = { error ->
                    Log.e("KoraIDV", "selectCountry: FAILED - ${error.message}", error)
                    val filtered = KoraIDV.getConfiguration().documentTypes
                        .filter { it.country == country.code || it.country == "INTL" }
                        .map { it.toDocumentTypeInfo() }
                    _state.value = VerificationState.DocumentSelection(
                        documentTypes = filtered,
                        selectedCountry = country
                    )
                }
            )
        }
    }

    private fun buildFallbackCountryMap(): Map<String, List<DocumentType>> {
        return KoraIDV.getConfiguration().documentTypes
            .filter { it.country != "INTL" }
            .groupBy { it.country }
    }

    private fun DocumentType.toDocumentTypeInfo(): DocumentTypeInfo {
        return DocumentTypeInfo(
            code = code,
            displayName = displayName,
            description = null,
            country = country,
            countryName = countryCodeToName(country),
            requiresBack = requiresBack,
            category = null
        )
    }

    companion object {
        private val specialNames = mapOf(
            "INTL" to "International"
        )

        fun countryCodeToName(code: String): String {
            specialNames[code]?.let { return it }
            // Use Java Locale for ISO 3166 country code → display name
            val localeName = java.util.Locale("", code).displayCountry
            // Locale returns the raw code if it doesn't recognize it
            return if (localeName != code) localeName else code
        }

        /**
         * Compute score breakdown from verification results.
         * Returns 4 metrics: Liveness, Name Match, Document Quality, Selfie Match.
         * Uses backend `scores` object as primary source (0-100 scale).
         * Overall is always computed as the average of the 4 displayed metrics
         * so the math is visually consistent for the user.
         */
        fun computeScoreBreakdown(verification: Verification): ScoreBreakdown {
            val scores = verification.scores

            val liveness = if (scores != null) {
                scores.liveness.toInt().coerceIn(0, 100)
            } else {
                verification.livenessVerification?.let {
                    it.livenessScore.toInt().coerceIn(0, 100)
                } ?: 0
            }

            val selfieMatch = if (scores != null) {
                scores.faceMatch.toInt().coerceIn(0, 100)
            } else {
                verification.faceVerification?.let {
                    it.matchScore.toInt().coerceIn(0, 100)
                } ?: 0
            }

            val documentQuality = if (scores != null) {
                scores.documentQuality.toInt().coerceIn(0, 100)
            } else {
                verification.documentVerification?.let {
                    ((it.authenticityScore ?: 0.0) * 100).toInt().coerceIn(0, 100)
                } ?: 0
            }

            val nameMatch = if (scores != null) {
                scores.nameMatch.toInt().coerceIn(0, 100)
            } else {
                if (verification.documentVerification?.firstName != null) 100 else 0
            }

            // Always compute overall as the average of the 4 displayed scores
            val overall = (liveness + selfieMatch + documentQuality + nameMatch) / 4

            return ScoreBreakdown(
                liveness = liveness,
                nameMatch = nameMatch,
                documentQuality = documentQuality,
                selfieMatch = selfieMatch,
                overallScore = overall.coerceIn(0, 100)
            )
        }
    }

    fun selectDocumentType(docType: DocumentTypeInfo) {
        selectedDocumentTypeCode = docType.code
        selectedDocumentDisplayName = docType.displayName
        selectedDocumentRequiresBack = docType.requiresBack
        documentFrontCaptured = false
        _state.value = VerificationState.DocumentCapture(
            documentTypeCode = docType.code,
            documentDisplayName = docType.displayName,
            requiresBack = docType.requiresBack,
            side = DocumentSide.FRONT
        )
    }

    fun submitDocument(imageData: ByteArray) {
        viewModelScope.launch {
            val verification = currentVerification ?: return@launch
            val manager = sessionManager ?: return@launch
            val docTypeCode = selectedDocumentTypeCode ?: return@launch

            val side = if (documentFrontCaptured) DocumentSide.BACK else DocumentSide.FRONT

            if (side == DocumentSide.FRONT && selectedDocumentRequiresBack) {
                // Upload front in background — DON'T change state.
                // DocumentCaptureScreen handles the FRONT→BACK transition internally
                // to keep the camera alive (AnimatedContent would kill it).
                documentFrontCaptured = true
                frontUploadResult = null
                frontUploadJob = viewModelScope.launch {
                    frontUploadResult = manager.uploadDocumentByCode(
                        verificationId = verification.id,
                        imageData = imageData,
                        side = DocumentSide.FRONT,
                        documentTypeCode = docTypeCode
                    )
                }
            } else {
                // Back side, or front of no-back documents: show processing as before
                _state.value = VerificationState.Processing(ProcessingStep.ANALYZING)
                _state.value = VerificationState.Processing(ProcessingStep.CHECKING_QUALITY)

                val result = manager.uploadDocumentByCode(
                    verificationId = verification.id,
                    imageData = imageData,
                    side = side,
                    documentTypeCode = docTypeCode
                )

                val response = result.getOrElse { error ->
                    _state.value = VerificationState.Error(
                        error as? KoraException ?: KoraException.Unknown(error.message ?: "Unknown error")
                    )
                    return@launch
                }

                if (!response.success) {
                    val issues = response.warnings ?: listOf("Quality check failed")
                    _state.value = VerificationState.Error(
                        KoraException.QualityValidationFailed(issues)
                    )
                    return@launch
                }

                // Before moving to selfie: await front upload if pending
                frontUploadJob?.join()
                val frontResult = frontUploadResult
                if (frontResult != null) {
                    val frontResponse = frontResult.getOrElse { error ->
                        _state.value = VerificationState.Error(
                            error as? KoraException ?: KoraException.Unknown(error.message ?: "Front upload failed")
                        )
                        return@launch
                    }
                    if (!frontResponse.success) {
                        val issues = frontResponse.warnings ?: listOf("Front document quality check failed")
                        _state.value = VerificationState.Error(
                            KoraException.QualityValidationFailed(issues)
                        )
                        return@launch
                    }
                }

                _state.value = VerificationState.SelfieCapture
            }
        }
    }

    private var selfieUploadJob: Job? = null
    private var selfieUploadResult: Result<SelfieUploadResult>? = null

    fun submitSelfie(imageData: ByteArray) {
        if (KoraIDV.getConfiguration().livenessMode == LivenessMode.ACTIVE) {
            // Go straight to liveness — no Processing screen
            _state.value = VerificationState.LivenessCheck
            // Upload selfie in background while user does liveness challenges
            val verification = currentVerification ?: return
            val manager = sessionManager ?: return
            selfieUploadResult = null
            selfieUploadJob = viewModelScope.launch {
                selfieUploadResult = manager.uploadSelfie(
                    verificationId = verification.id,
                    imageData = imageData
                )
            }
            return
        }

        viewModelScope.launch {
            _state.value = VerificationState.Processing(ProcessingStep.ANALYZING)
            val verification = currentVerification ?: return@launch
            val manager = sessionManager ?: return@launch

            val result = manager.uploadSelfie(
                verificationId = verification.id,
                imageData = imageData
            )

            result.fold(
                onSuccess = { response ->
                    if (response.faceDetected) {
                        completeVerification()
                    } else {
                        val issues = response.qualityIssues ?: listOf("Face not detected")
                        _state.value = VerificationState.Error(
                            KoraException.QualityValidationFailed(issues)
                        )
                    }
                },
                onFailure = { error ->
                    _state.value = VerificationState.Error(
                        error as? KoraException ?: KoraException.Unknown(error.message ?: "Unknown error")
                    )
                }
            )
        }
    }

    fun completeLiveness(result: LivenessResult) {
        viewModelScope.launch {
            if (result.passed) {
                completeVerification()
            } else {
                _state.value = VerificationState.Error(
                    KoraException.LivenessCheckFailed(),
                    canRetry = true
                )
            }
        }
    }

    private suspend fun completeVerification() {
        _state.value = VerificationState.Processing(ProcessingStep.FINALIZING)

        // Await background selfie upload if pending
        selfieUploadJob?.join()
        val selfieResult = selfieUploadResult
        if (selfieResult != null) {
            selfieResult.getOrElse { error ->
                _state.value = VerificationState.Error(
                    error as? KoraException ?: KoraException.Unknown(error.message ?: "Selfie upload failed")
                )
                return
            }
        }

        val verification = currentVerification ?: return
        val manager = sessionManager ?: return

        val result = manager.completeVerification(verification.id)

        result.fold(
            onSuccess = { completedVerification ->
                currentVerification = completedVerification
                // Route to the correct result screen based on status
                _state.value = when (completedVerification.status) {
                    VerificationStatus.EXPIRED -> VerificationState.ExpiredDocument(completedVerification)
                    VerificationStatus.REVIEW_REQUIRED -> VerificationState.ManualReview(completedVerification)
                    else -> VerificationState.Complete(completedVerification)
                }
            },
            onFailure = { error ->
                _state.value = VerificationState.Error(
                    error as? KoraException ?: KoraException.Unknown(error.message ?: "Unknown error"),
                    canRetry = false
                )
            }
        )
    }

    fun handleError(error: KoraException) {
        _state.value = VerificationState.Error(error)
    }

    fun retry() {
        // Clear stale upload state so retries start fresh
        frontUploadJob?.cancel()
        frontUploadJob = null
        frontUploadResult = null
        selfieUploadJob?.cancel()
        selfieUploadJob = null
        selfieUploadResult = null

        val docTypeCode = selectedDocumentTypeCode
        when (_state.value) {
            is VerificationState.Error -> {
                if (docTypeCode != null) {
                    // Always restart from FRONT capture on retry — the previous
                    // front upload may have failed, so we can't skip it.
                    documentFrontCaptured = false
                    _state.value = VerificationState.DocumentCapture(
                        documentTypeCode = docTypeCode,
                        documentDisplayName = selectedDocumentDisplayName ?: "",
                        requiresBack = selectedDocumentRequiresBack,
                        side = DocumentSide.FRONT
                    )
                } else {
                    _state.value = VerificationState.Consent
                }
            }
            is VerificationState.ExpiredDocument,
            is VerificationState.Complete -> {
                // Retry from document selection
                documentFrontCaptured = false
                _state.value = VerificationState.Consent
            }
            else -> {}
        }
    }

    internal fun getSessionManager(): SessionManager? = sessionManager
    internal fun getCurrentVerification(): Verification? = currentVerification
    internal fun getSelectedCountry(): CountryInfo? = selectedCountry
}
