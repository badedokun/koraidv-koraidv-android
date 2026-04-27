package com.koraidv.sdk.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koraidv.sdk.*
import com.koraidv.sdk.api.*
import com.koraidv.sdk.capture.BarcodeScanner
import com.koraidv.sdk.liveness.LivenessResult
import com.koraidv.sdk.nfc.NfcPassportActivity
import com.koraidv.sdk.nfc.NfcPassportData
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
    /**
     * NFC chip reading step (Enhanced tier, documents with MRZ).
     * @property documentNumber MRZ document number for BAC key
     * @property dateOfBirth MRZ date of birth (YYMMDD) for BAC key
     * @property dateOfExpiry MRZ date of expiry (YYMMDD) for BAC key
     */
    data class NfcReading(
        val documentNumber: String,
        val dateOfBirth: String,
        val dateOfExpiry: String
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
    ANALYZING("Analyzing document"),
    CHECKING_QUALITY("Matching identity"),
    FINALIZING("Finalizing")
}

/**
 * Score breakdown metric for result screens
 */
data class ScoreBreakdown(
    val liveness: Int,
    val screening: Int,
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
    private val debugLogging: Boolean
        get() = try { KoraIDV.getConfiguration().debugLogging } catch (_: Exception) { false }

    private var selectedDocumentTypeCode: String? = null
    private var selectedDocumentDisplayName: String? = null
    private var selectedDocumentRequiresBack: Boolean = false
    private var selectedDocumentHasMrz: Boolean = false
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

            val result = manager.createVerification(
                externalId = req.externalId,
                tier = req.tier,
                expectedFirstName = req.expectedFirstName,
                expectedLastName = req.expectedLastName
            )

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

        if (debugLogging) Log.d("KoraIDV", "loadCountries: calling getDocumentTypes(country=null)")
        val result = manager.getDocumentTypes(country = null)
        result.fold(
            onSuccess = { docTypesResult ->
                if (debugLogging) Log.d("KoraIDV", "loadCountries: success - ${docTypesResult.countries.size} countries, ${docTypesResult.documentTypes.size} doc types")
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
                if (debugLogging) Log.e("KoraIDV", "loadCountries: FAILED - ${error.message}", error)
                _state.value = VerificationState.Error(
                    error as? KoraException ?: KoraException.NetworkError(
                        "Could not load supported countries. Please check your connection and try again."
                    )
                )
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
                    if (debugLogging) Log.e("KoraIDV", "selectCountry: FAILED - ${error.message}", error)
                    _state.value = VerificationState.Error(
                        error as? KoraException ?: KoraException.NetworkError(
                            "Could not load document types. Please check your connection and try again."
                        )
                    )
                }
            )
        }
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
         * Returns 5 metrics: Liveness, Screening, Name Match, Document Quality, Selfie Match.
         * Uses backend `scores` object as primary source (0-100 scale).
         * Overall uses the backend's weighted score directly.
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

            val screening = if (scores != null) {
                scores.screening.toInt().coerceIn(0, 100)
            } else {
                0
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

            // Use the backend's weighted overall score directly
            val overall = if (scores != null) {
                scores.overall.toInt().coerceIn(0, 100)
            } else {
                (liveness + selfieMatch + documentQuality + nameMatch + screening) / 5
            }

            return ScoreBreakdown(
                liveness = liveness,
                screening = screening,
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
        selectedDocumentHasMrz = docType.hasMrz
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
                    frontUploadResult = manager.uploadDocument(
                        verificationId = verification.id,
                        imageData = imageData,
                        side = DocumentSide.FRONT,
                        documentTypeCode = docTypeCode
                    )
                }
            } else {
                // Back side, or front of no-back documents: upload without Processing screen.
                // Processing screen only appears after liveness is completed.

                // Await front upload BEFORE sending back — the server needs the
                // front document record to exist so it can inherit document_type.
                if (side == DocumentSide.BACK) {
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
                }

                // On-device barcode decode (Phase 2 of the multi-channel
                // decode roadmap). For back-side captures we run ML Kit's
                // PDF417 scanner on the bitmap before upload — when it
                // succeeds the server skips its own image-decoding cascade
                // entirely, saving ~1-3 s of round-trip latency. Cost on
                // documents without a barcode (passports, foreign DLs) is
                // bounded: ML Kit returns null in ~50 ms.
                // docs/architecture/idv-decode-roadmap.md (Phase 2).
                val decodedPayload = if (side == DocumentSide.BACK) {
                    decodeBarcodeOrNull(imageData)
                } else {
                    null
                }

                val result = manager.uploadDocument(
                    verificationId = verification.id,
                    imageData = imageData,
                    side = side,
                    documentTypeCode = docTypeCode,
                    decodedBarcodePayload = decodedPayload
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

                // Check if NFC reading should be offered (Enhanced tier + MRZ document)
                val nextState = determinePostDocumentState()
                _state.value = nextState
            }
        }
    }

    /**
     * Determine the next state after document upload completes.
     *
     * Routes to NFC reading when:
     * - Verification tier is ENHANCED
     * - The selected document type has MRZ (passport, EU ID)
     *
     * The NFC activity itself checks device NFC capability.
     */
    private fun determinePostDocumentState(): VerificationState {
        val req = request
        val docTypeCode = selectedDocumentTypeCode

        // Check if Enhanced tier
        val isEnhanced = req?.tier == VerificationTier.ENHANCED

        // Check if document type has MRZ
        val hasMrz = selectedDocumentHasMrz

        if (isEnhanced && hasMrz) {
            // Get MRZ data from the document verification result (if available from OCR)
            val docVerification = currentVerification?.documentVerification
            val docNumber = docVerification?.documentNumber
            val dob = docVerification?.dateOfBirth
            val expiry = docVerification?.expirationDate

            if (docNumber != null && dob != null && expiry != null) {
                // Convert dates to YYMMDD format if they are in YYYY-MM-DD
                val dobYymmdd = convertToYymmdd(dob)
                val expiryYymmdd = convertToYymmdd(expiry)

                return VerificationState.NfcReading(
                    documentNumber = docNumber,
                    dateOfBirth = dobYymmdd,
                    dateOfExpiry = expiryYymmdd
                )
            }
        }

        return VerificationState.SelfieCapture
    }

    /**
     * Convert a date string to YYMMDD format.
     * Handles both YYMMDD (passthrough) and YYYY-MM-DD formats.
     */
    private fun convertToYymmdd(date: String): String {
        // Already in YYMMDD format
        if (date.length == 6 && date.all { it.isDigit() }) return date

        // YYYY-MM-DD format
        val parts = date.split("-")
        if (parts.size == 3) {
            val year = parts[0].takeLast(2)
            val month = parts[1].padStart(2, '0')
            val day = parts[2].padStart(2, '0')
            return "$year$month$day"
        }

        return date
    }

    /**
     * Handle NFC data received from [NfcPassportActivity].
     * Uploads the chip data to the server, then proceeds to selfie capture.
     */
    fun submitNfcData(nfcData: NfcPassportData) {
        viewModelScope.launch {
            val verification = currentVerification ?: return@launch
            val manager = sessionManager ?: return@launch

            _state.value = VerificationState.Processing(ProcessingStep.ANALYZING)

            val result = manager.uploadNfcData(
                verificationId = verification.id,
                nfcData = nfcData
            )

            result.fold(
                onSuccess = {
                    if (debugLogging) Log.d("KoraIDV", "NFC data uploaded successfully")
                    _state.value = VerificationState.SelfieCapture
                },
                onFailure = { error ->
                    if (debugLogging) Log.w("KoraIDV", "NFC upload failed: ${error.message}")
                    // NFC is optional, proceed to selfie even if upload fails
                    _state.value = VerificationState.SelfieCapture
                }
            )
        }
    }

    /**
     * Skip NFC reading and proceed directly to selfie capture.
     */
    fun skipNfc() {
        _state.value = VerificationState.SelfieCapture
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

    private var pendingLivenessResult: LivenessResult? = null

    fun completeLiveness(result: LivenessResult) {
        viewModelScope.launch {
            if (result.passed) {
                pendingLivenessResult = result
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

        val result = manager.completeVerification(verification.id, pendingLivenessResult)

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

    /**
     * Decode a PDF417 barcode from the captured back-side JPEG bytes using
     * ML Kit. Returns the raw AAMVA payload on success; returns null on any
     * error (decoding failure, no barcode present, malformed bitmap). All
     * failures are silent — the server cascade picks up the same image and
     * retries with its own decoders. We never block upload on this.
     */
    private suspend fun decodeBarcodeOrNull(imageData: ByteArray): String? {
        val bitmap = try {
            BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
        } catch (e: Exception) {
            Log.w("KoraIDV.ViewModel", "bitmap decode failed: ${e.message}")
            null
        } ?: return null

        val scanner = BarcodeScanner()
        return try {
            scanner.decodePdf417(bitmap)
        } finally {
            scanner.close()
        }
    }
}
