package com.koraidv.sdk.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.koraidv.sdk.KoraException
import com.koraidv.sdk.KoraIDV
import com.koraidv.sdk.Verification
import com.koraidv.sdk.VerificationRequest
import com.koraidv.sdk.ui.compose.VerificationFlow
import com.koraidv.sdk.ui.theme.KoraIDVTheme

/**
 * Main activity for verification flow
 */
class VerificationActivity : ComponentActivity() {

    private val viewModel: VerificationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val request = intent.getSerializableExtra(EXTRA_REQUEST) as? VerificationRequest
        if (request == null) {
            finishWithError(KoraException.Unknown("Missing verification request"))
            return
        }

        viewModel.initialize(request)

        setContent {
            KoraIDVTheme(theme = KoraIDV.getConfiguration().theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val state by viewModel.state.collectAsState()

                    VerificationFlow(
                        state = state,
                        onConsentAccepted = { viewModel.acceptConsent() },
                        onConsentDeclined = { finishCancelled() },
                        onDocumentTypeSelected = { viewModel.selectDocumentType(it) },
                        onDocumentCaptured = { viewModel.submitDocument(it) },
                        onSelfieCaptured = { viewModel.submitSelfie(it) },
                        onLivenessComplete = { viewModel.completeLiveness(it) },
                        onComplete = { finishWithSuccess(it) },
                        onError = { viewModel.handleError(it) },
                        onCancel = { finishCancelled() },
                        onRetry = { viewModel.retry() }
                    )
                }
            }
        }
    }

    private fun finishWithSuccess(verification: Verification) {
        val resultIntent = Intent().apply {
            putExtra(EXTRA_VERIFICATION, verification)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun finishWithError(error: KoraException) {
        val resultIntent = Intent().apply {
            putExtra(EXTRA_ERROR, error as Parcelable)
        }
        setResult(Activity.RESULT_CANCELED, resultIntent)
        finish()
    }

    private fun finishCancelled() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    companion object {
        const val EXTRA_REQUEST = "verification_request"
        const val EXTRA_VERIFICATION = "verification"
        const val EXTRA_ERROR = "error"

        fun createIntent(context: Context, request: VerificationRequest): Intent {
            return Intent(context, VerificationActivity::class.java).apply {
                putExtra(EXTRA_REQUEST, request)
            }
        }
    }
}
