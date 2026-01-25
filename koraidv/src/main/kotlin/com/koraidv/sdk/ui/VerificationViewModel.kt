package com.koraidv.sdk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koraidv.sdk.*
import com.koraidv.sdk.api.DocumentSide
import com.koraidv.sdk.api.SessionManager
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
    data class DocumentSelection(val allowedTypes: List<DocumentType>) : VerificationState()
    data class DocumentCapture(val documentType: DocumentType, val side: DocumentSide) : VerificationState()
    data object SelfieCapture : VerificationState()
    data object LivenessCheck : VerificationState()
    data object Processing : VerificationState()
    data class Complete(val verification: Verification) : VerificationState()
    data class Error(val error: KoraException, val canRetry: Boolean = true) : VerificationState()
}

/**
 * ViewModel for verification flow
 */
class VerificationViewModel : ViewModel() {

    private val _state = MutableStateFlow<VerificationState>(VerificationState.Loading)
    val state: StateFlow<VerificationState> = _state.asStateFlow()

    private var sessionManager: SessionManager? = null
    private var request: VerificationRequest? = null
    private var currentVerification: Verification? = null
    private var selectedDocumentType: DocumentType? = null
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

    fun acceptConsent() {
        viewModelScope.launch {
            _state.value = VerificationState.Loading

            val req = request ?: return@launch
            val manager = sessionManager ?: return@launch

            val result = manager.createVerification(req.externalId, req.tier)

            result.fold(
                onSuccess = { verification ->
                    currentVerification = verification
                    val allowedTypes = req.documentTypes ?: KoraIDV.getConfiguration().documentTypes
                    _state.value = VerificationState.DocumentSelection(allowedTypes)
                },
                onFailure = { error ->
                    _state.value = VerificationState.Error(
                        error as? KoraException ?: KoraException.Unknown(error.message ?: "Unknown error")
                    )
                }
            )
        }
    }

    fun selectDocumentType(type: DocumentType) {
        selectedDocumentType = type
        documentFrontCaptured = false
        _state.value = VerificationState.DocumentCapture(type, DocumentSide.FRONT)
    }

    fun submitDocument(imageData: ByteArray) {
        viewModelScope.launch {
            _state.value = VerificationState.Loading

            val verification = currentVerification ?: return@launch
            val manager = sessionManager ?: return@launch
            val docType = selectedDocumentType ?: return@launch

            val side = if (documentFrontCaptured) DocumentSide.BACK else DocumentSide.FRONT

            val result = manager.uploadDocument(
                verificationId = verification.id,
                imageData = imageData,
                side = side,
                documentType = docType
            )

            result.fold(
                onSuccess = { response ->
                    if (response.success) {
                        if (side == DocumentSide.FRONT) {
                            documentFrontCaptured = true
                            if (docType.requiresBack) {
                                _state.value = VerificationState.DocumentCapture(docType, DocumentSide.BACK)
                            } else {
                                _state.value = VerificationState.SelfieCapture
                            }
                        } else {
                            _state.value = VerificationState.SelfieCapture
                        }
                    } else {
                        val issues = response.qualityIssues?.map { it.message } ?: listOf("Quality check failed")
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
            _state.value = VerificationState.Loading

            val verification = currentVerification ?: return@launch
            val manager = sessionManager ?: return@launch

            val result = manager.uploadSelfie(
                verificationId = verification.id,
                imageData = imageData
            )

            result.fold(
                onSuccess = { response ->
                    if (response.success) {
                        if (KoraIDV.getConfiguration().livenessMode == LivenessMode.ACTIVE) {
                            _state.value = VerificationState.LivenessCheck
                        } else {
                            completeVerification()
                        }
                    } else {
                        val issues = response.qualityIssues?.map { it.message } ?: listOf("Quality check failed")
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
        _state.value = VerificationState.Processing

        val verification = currentVerification ?: return
        val manager = sessionManager ?: return

        val result = manager.completeVerification(verification.id)

        result.fold(
            onSuccess = { completedVerification ->
                currentVerification = completedVerification
                _state.value = VerificationState.Complete(completedVerification)
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
        val docType = selectedDocumentType
        when (val currentState = _state.value) {
            is VerificationState.Error -> {
                // Retry from appropriate step
                if (docType != null) {
                    if (!documentFrontCaptured) {
                        _state.value = VerificationState.DocumentCapture(docType, DocumentSide.FRONT)
                    } else if (docType.requiresBack) {
                        _state.value = VerificationState.DocumentCapture(docType, DocumentSide.BACK)
                    } else {
                        _state.value = VerificationState.SelfieCapture
                    }
                } else {
                    _state.value = VerificationState.Consent
                }
            }
            else -> {}
        }
    }
}
