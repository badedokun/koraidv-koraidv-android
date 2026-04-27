package com.koraidv.sdk.ui.compose

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.koraidv.sdk.*
import com.koraidv.sdk.api.CountryInfo
import com.koraidv.sdk.api.DocumentTypeInfo
import com.koraidv.sdk.api.SessionManager
import com.koraidv.sdk.liveness.LivenessResult
import com.koraidv.sdk.nfc.NfcPassportData
import com.koraidv.sdk.ui.VerificationState

/**
 * Main verification flow composable
 */
@Composable
internal fun VerificationFlow(
    state: VerificationState,
    onConsentAccepted: () -> Unit,
    onConsentDeclined: () -> Unit,
    onCountrySelected: (CountryInfo) -> Unit,
    onDocumentTypeSelected: (DocumentTypeInfo) -> Unit,
    onDocumentCaptured: (ByteArray) -> Unit,
    onNfcDataReceived: (NfcPassportData) -> Unit,
    onNfcSkipped: () -> Unit,
    onSelfieCaptured: (ByteArray) -> Unit,
    onLivenessComplete: (LivenessResult) -> Unit,
    onComplete: (Verification) -> Unit,
    onError: (KoraException) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    sessionManager: SessionManager? = null,
    verificationId: String? = null,
    // REQ-005 — result-page presentation.
    resultPageMode: ResultPageMode = ResultPageMode.DETAILED,
    customMessages: ResultPageMessages? = null,
    // REQ-003 — rich visual onboarding guides (demo-gated until production
    // sign-off). When false, the existing minimal-icon UI is used.
    showVisualGuides: Boolean = false
) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn() + slideInHorizontally() togetherWith fadeOut() + slideOutHorizontally()
        },
        label = "verification_flow"
    ) { currentState ->
        when (currentState) {
            is VerificationState.Loading -> {
                LoadingScreen()
            }
            is VerificationState.Consent -> {
                ConsentScreen(
                    onAccept = onConsentAccepted,
                    onDecline = onConsentDeclined
                )
            }
            is VerificationState.CountrySelection -> {
                CountrySelectionScreen(
                    countries = currentState.countries,
                    onSelect = onCountrySelected,
                    onCancel = onCancel
                )
            }
            is VerificationState.DocumentSelection -> {
                DocumentSelectionScreen(
                    documentTypes = currentState.documentTypes,
                    selectedCountry = currentState.selectedCountry,
                    onSelect = onDocumentTypeSelected,
                    onCancel = onCancel
                )
            }
            is VerificationState.DocumentCapture -> {
                DocumentCaptureScreen(
                    documentTypeCode = currentState.documentTypeCode,
                    documentDisplayName = currentState.documentDisplayName,
                    requiresBack = currentState.requiresBack,
                    side = currentState.side,
                    onCaptured = onDocumentCaptured,
                    onCancel = onCancel,
                    showVisualGuides = showVisualGuides
                )
            }
            is VerificationState.NfcReading -> {
                NfcReadingPlaceholder(
                    documentNumber = currentState.documentNumber,
                    dateOfBirth = currentState.dateOfBirth,
                    dateOfExpiry = currentState.dateOfExpiry,
                    onNfcDataReceived = onNfcDataReceived,
                    onSkip = onNfcSkipped,
                    showVisualGuides = showVisualGuides
                )
            }
            is VerificationState.SelfieCapture -> {
                SelfieCaptureScreen(
                    onCaptured = onSelfieCaptured,
                    onCancel = onCancel,
                    showVisualGuides = showVisualGuides
                )
            }
            is VerificationState.LivenessCheck -> {
                LivenessScreen(
                    sessionManager = sessionManager,
                    verificationId = verificationId,
                    onComplete = onLivenessComplete,
                    onCancel = onCancel,
                    showVisualGuides = showVisualGuides
                )
            }
            is VerificationState.Processing -> {
                ProcessingScreen(step = currentState.step)
            }
            is VerificationState.Complete -> {
                val verification = currentState.verification
                val simplified = resultPageMode == ResultPageMode.SIMPLIFIED
                when (verification.status) {
                    VerificationStatus.APPROVED ->
                        if (simplified) {
                            SimplifiedSuccessScreen(
                                messages = customMessages,
                                onDone = { onComplete(verification) }
                            )
                        } else {
                            SuccessScreen(
                                verification = verification,
                                onDone = { onComplete(verification) }
                            )
                        }
                    VerificationStatus.REJECTED ->
                        if (simplified) {
                            SimplifiedFailedScreen(
                                messages = customMessages,
                                onRetry = onRetry
                            )
                        } else {
                            RejectedScreen(
                                verification = verification,
                                onRetry = onRetry
                            )
                        }
                    else ->
                        if (simplified) {
                            SimplifiedSuccessScreen(
                                messages = customMessages,
                                onDone = { onComplete(verification) }
                            )
                        } else {
                            SuccessScreen(
                                verification = verification,
                                onDone = { onComplete(verification) }
                            )
                        }
                }
            }
            is VerificationState.ExpiredDocument -> {
                if (resultPageMode == ResultPageMode.SIMPLIFIED) {
                    SimplifiedFailedScreen(
                        messages = customMessages?.copy(
                            failedTitle = customMessages.failedTitle ?: "Document Expired",
                            failedMessage = customMessages.failedMessage
                                ?: "The document you submitted has expired. Please use a valid document."
                        ) ?: ResultPageMessages(
                            failedTitle = "Document Expired",
                            failedMessage = "The document you submitted has expired. Please use a valid document."
                        ),
                        onRetry = onRetry
                    )
                } else {
                    ExpiredDocumentScreen(
                        verification = currentState.verification,
                        onRetry = onRetry
                    )
                }
            }
            is VerificationState.ManualReview -> {
                if (resultPageMode == ResultPageMode.SIMPLIFIED) {
                    SimplifiedReviewScreen(
                        verification = currentState.verification,
                        messages = customMessages,
                        onDone = { onComplete(currentState.verification) }
                    )
                } else {
                    ManualReviewScreen(
                        verification = currentState.verification,
                        onDone = { onComplete(currentState.verification) }
                    )
                }
            }
            is VerificationState.Error -> {
                ErrorScreen(
                    error = currentState.error,
                    canRetry = currentState.canRetry,
                    onRetry = onRetry,
                    onCancel = onCancel
                )
            }
        }
    }
}
