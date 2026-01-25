package com.koraidv.sdk.ui.compose

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.koraidv.sdk.*
import com.koraidv.sdk.api.DocumentSide
import com.koraidv.sdk.liveness.LivenessResult
import com.koraidv.sdk.ui.VerificationState

/**
 * Main verification flow composable
 */
@Composable
fun VerificationFlow(
    state: VerificationState,
    onConsentAccepted: () -> Unit,
    onConsentDeclined: () -> Unit,
    onDocumentTypeSelected: (DocumentType) -> Unit,
    onDocumentCaptured: (ByteArray) -> Unit,
    onSelfieCaptured: (ByteArray) -> Unit,
    onLivenessComplete: (LivenessResult) -> Unit,
    onComplete: (Verification) -> Unit,
    onError: (KoraException) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
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
            is VerificationState.DocumentSelection -> {
                DocumentSelectionScreen(
                    allowedTypes = currentState.allowedTypes,
                    onSelect = onDocumentTypeSelected,
                    onCancel = onCancel
                )
            }
            is VerificationState.DocumentCapture -> {
                DocumentCaptureScreen(
                    documentType = currentState.documentType,
                    side = currentState.side,
                    onCaptured = onDocumentCaptured,
                    onCancel = onCancel
                )
            }
            is VerificationState.SelfieCapture -> {
                SelfieCaptureScreen(
                    onCaptured = onSelfieCaptured,
                    onCancel = onCancel
                )
            }
            is VerificationState.LivenessCheck -> {
                LivenessScreen(
                    onComplete = onLivenessComplete,
                    onCancel = onCancel
                )
            }
            is VerificationState.Processing -> {
                ProcessingScreen()
            }
            is VerificationState.Complete -> {
                ResultScreen(
                    verification = currentState.verification,
                    onDone = { onComplete(currentState.verification) }
                )
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
