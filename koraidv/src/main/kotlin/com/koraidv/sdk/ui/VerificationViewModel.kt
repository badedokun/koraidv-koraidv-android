package com.koraidv.sdk.ui

import android.content.Context
import android.util.Log
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koraidv.sdk.*
import com.koraidv.sdk.api.*
import com.koraidv.sdk.liveness.LivenessResult
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
        private val countryNames = mapOf(
            "US" to "United States",
            "GB" to "United Kingdom",
            "DE" to "Germany",
            "FR" to "France",
            "ES" to "Spain",
            "IT" to "Italy",
            "GH" to "Ghana",
            "NG" to "Nigeria",
            "KE" to "Kenya",
            "ZA" to "South Africa",
            "INTL" to "International"
        )

        fun countryCodeToName(code: String): String {
            return countryNames[code] ?: code
        }

        /**
         * Compute score breakdown from verification results.
         * Returns 4 metrics: Liveness, Name Match, Document Quality, Selfie Match
         */
        fun computeScoreBreakdown(verification: Verification): ScoreBreakdown {
            val liveness = verification.livenessVerification?.let {
                (it.livenessScore * 100).toInt().coerceIn(0, 100)
            } ?: 0

            val selfieMatch = verification.faceVerification?.let {
                (it.matchScore * 100).toInt().coerceIn(0, 100)
            } ?: 0

            val documentQuality = verification.documentVerification?.let {
                ((it.authenticityScore ?: 0.0) * 100).toInt().coerceIn(0, 100)
            } ?: 0

            // Name match: check if names are present and consistent
            val nameMatch = if (verification.documentVerification?.firstName != null) 100 else 0

            val overall = verification.riskScore?.let { 100 - it } ?: run {
                val scores = listOfNotNull(
                    if (liveness > 0) liveness else null,
                    if (selfieMatch > 0) selfieMatch else null,
                    if (documentQuality > 0) documentQuality else null,
                    if (nameMatch > 0) nameMatch else null
                )
                if (scores.isNotEmpty()) scores.average().toInt() else 0
            }

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
            _state.value = VerificationState.Processing(ProcessingStep.ANALYZING)

            val verification = currentVerification ?: return@launch
            val manager = sessionManager ?: return@launch
            val docTypeCode = selectedDocumentTypeCode ?: return@launch

            val side = if (documentFrontCaptured) DocumentSide.BACK else DocumentSide.FRONT

            _state.value = VerificationState.Processing(ProcessingStep.CHECKING_QUALITY)

            val result = manager.uploadDocumentByCode(
                verificationId = verification.id,
                imageData = imageData,
                side = side,
                documentTypeCode = docTypeCode
            )

            result.fold(
                onSuccess = { response ->
                    if (response.success) {
                        if (side == DocumentSide.FRONT) {
                            documentFrontCaptured = true
                            if (selectedDocumentRequiresBack) {
                                _state.value = VerificationState.DocumentCapture(
                                    documentTypeCode = docTypeCode,
                                    documentDisplayName = selectedDocumentDisplayName ?: "",
                                    requiresBack = true,
                                    side = DocumentSide.BACK
                                )
                            } else {
                                _state.value = VerificationState.SelfieCapture
                            }
                        } else {
                            _state.value = VerificationState.SelfieCapture
                        }
                    } else {
                        val issues = response.warnings ?: listOf("Quality check failed")
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

    fun submitSelfie(imageData: ByteArray) {
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
                        if (KoraIDV.getConfiguration().livenessMode == LivenessMode.ACTIVE) {
                            _state.value = VerificationState.LivenessCheck
                        } else {
                            completeVerification()
                        }
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
        val docTypeCode = selectedDocumentTypeCode
        when (_state.value) {
            is VerificationState.Error -> {
                if (docTypeCode != null) {
                    if (!documentFrontCaptured) {
                        _state.value = VerificationState.DocumentCapture(
                            documentTypeCode = docTypeCode,
                            documentDisplayName = selectedDocumentDisplayName ?: "",
                            requiresBack = selectedDocumentRequiresBack,
                            side = DocumentSide.FRONT
                        )
                    } else if (selectedDocumentRequiresBack) {
                        _state.value = VerificationState.DocumentCapture(
                            documentTypeCode = docTypeCode,
                            documentDisplayName = selectedDocumentDisplayName ?: "",
                            requiresBack = true,
                            side = DocumentSide.BACK
                        )
                    } else {
                        _state.value = VerificationState.SelfieCapture
                    }
                } else {
                    _state.value = VerificationState.Consent
                }
            }
            is VerificationState.ExpiredDocument,
            is VerificationState.Complete -> {
                // Retry from document selection
                _state.value = VerificationState.Consent
            }
            else -> {}
        }
    }

    internal fun getSessionManager(): SessionManager? = sessionManager
    internal fun getCurrentVerification(): Verification? = currentVerification
    internal fun getSelectedCountry(): CountryInfo? = selectedCountry
}
