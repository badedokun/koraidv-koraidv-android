package com.koraidv.sdk.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Parcelable
import com.koraidv.sdk.getParcelableExtraCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.koraidv.sdk.KoraException
import com.koraidv.sdk.KoraIDV
import com.koraidv.sdk.Verification
import com.koraidv.sdk.VerificationRequest
import com.koraidv.sdk.nfc.NfcPassportActivity
import com.koraidv.sdk.ui.compose.VerificationFlow
import com.koraidv.sdk.ui.theme.KoraIDVTheme

/**
 * Main activity for verification flow
 */
class VerificationActivity : ComponentActivity() {

    private val viewModel: VerificationViewModel by viewModels()

    /** Tracks whether the NFC activity has been launched for the current NfcReading state */
    private var nfcActivityLaunched = false

    private val nfcActivityLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        nfcActivityLaunched = false
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val nfcData = NfcPassportActivity.parseResult(result.resultCode, result.data)
                if (nfcData != null) {
                    viewModel.submitNfcData(nfcData)
                } else {
                    viewModel.skipNfc()
                }
            }
            NfcPassportActivity.RESULT_SKIPPED -> {
                viewModel.skipNfc()
            }
            else -> {
                // Cancelled or error - skip NFC
                viewModel.skipNfc()
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initializeVerification()
        } else {
            finishWithError(KoraException.CameraAccessDenied())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Pre-warm CameraProvider while user reads consent
        ProcessCameraProvider.getInstance(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            initializeVerification()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun initializeVerification() {
        val verificationId = intent.getStringExtra(EXTRA_VERIFICATION_ID)
        val request = intent.getParcelableExtraCompat<VerificationRequest>(EXTRA_REQUEST)

        if (verificationId != null) {
            viewModel.initializeForResume(verificationId)
        } else if (request != null) {
            viewModel.initialize(request)
            // Pre-warm camera during consent
            viewModel.preWarmCamera(this)
        } else {
            finishWithError(KoraException.Unknown("Missing verification request"))
            return
        }

        setContent {
            // Gracefully handle process death: KoraIDV singleton is in-memory
            // and will be null after the system kills and restores the process.
            val config = try { KoraIDV.getConfiguration() } catch (_: KoraException) {
                finishWithError(KoraException.NotConfigured())
                return@setContent
            }
            KoraIDVTheme(theme = config.theme) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val state by viewModel.state.collectAsState()

                    // Launch NfcPassportActivity when state transitions to NfcReading
                    if (state is VerificationState.NfcReading && !nfcActivityLaunched) {
                        val nfcState = state as VerificationState.NfcReading
                        LaunchedEffect(nfcState) {
                            nfcActivityLaunched = true
                            if (NfcPassportActivity.isNfcAvailable(this@VerificationActivity)) {
                                val nfcIntent = NfcPassportActivity.createIntent(
                                    context = this@VerificationActivity,
                                    documentNumber = nfcState.documentNumber,
                                    dateOfBirth = nfcState.dateOfBirth,
                                    dateOfExpiry = nfcState.dateOfExpiry
                                )
                                nfcActivityLauncher.launch(nfcIntent)
                            } else {
                                // No NFC hardware, skip
                                viewModel.skipNfc()
                            }
                        }
                    }

                    VerificationFlow(
                        state = state,
                        onConsentAccepted = { viewModel.acceptConsent() },
                        onConsentDeclined = { finishCancelled() },
                        onCountrySelected = { viewModel.selectCountry(it) },
                        onDocumentTypeSelected = { viewModel.selectDocumentType(it) },
                        onDocumentCaptured = { viewModel.submitDocument(it) },
                        onNfcDataReceived = { viewModel.submitNfcData(it) },
                        onNfcSkipped = { viewModel.skipNfc() },
                        onSelfieCaptured = { viewModel.submitSelfie(it) },
                        onLivenessComplete = { viewModel.completeLiveness(it) },
                        onComplete = { finishWithSuccess(it) },
                        onError = { viewModel.handleError(it) },
                        onCancel = { finishCancelled() },
                        onRetry = { viewModel.retry() },
                        sessionManager = viewModel.getSessionManager(),
                        verificationId = viewModel.getCurrentVerification()?.id
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
        const val EXTRA_VERIFICATION_ID = "verification_id"
        const val EXTRA_VERIFICATION = "verification"
        const val EXTRA_ERROR = "error"

        fun createIntent(context: Context, request: VerificationRequest): Intent {
            return Intent(context, VerificationActivity::class.java).apply {
                putExtra(EXTRA_REQUEST, request)
            }
        }

        fun createResumeIntent(context: Context, verificationId: String): Intent {
            return Intent(context, VerificationActivity::class.java).apply {
                putExtra(EXTRA_VERIFICATION_ID, verificationId)
            }
        }
    }
}
