package com.koraidv.sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.koraidv.sdk.api.ApiClient
import com.koraidv.sdk.ui.VerificationActivity

/**
 * Main entry point for the Kora IDV SDK.
 *
 * Usage:
 * ```kotlin
 * // Configure the SDK
 * KoraIDV.configure(
 *     Configuration(
 *         apiKey = "ck_live_xxx",
 *         tenantId = "tenant-uuid"
 *     )
 * )
 *
 * // Start verification using Activity Result API
 * val launcher = registerForActivityResult(KoraIDV.VerificationContract()) { result ->
 *     when (result) {
 *         is VerificationResult.Success -> handleSuccess(result.verification)
 *         is VerificationResult.Failure -> handleError(result.error)
 *         is VerificationResult.Cancelled -> handleCancelled()
 *     }
 * }
 * launcher.launch(VerificationRequest(externalId = "user-123"))
 * ```
 */
object KoraIDV {

    private var configuration: Configuration? = null
    internal var apiClient: ApiClient? = null
        private set

    /**
     * SDK version
     */
    const val VERSION = "1.0.0"

    /**
     * Configure the SDK with the provided configuration.
     *
     * @param configuration SDK configuration with API key and tenant ID
     */
    fun configure(configuration: Configuration) {
        this.configuration = configuration
        this.apiClient = ApiClient(configuration)
    }

    /**
     * Check if the SDK is configured.
     */
    val isConfigured: Boolean
        get() = configuration != null

    /**
     * Get the current configuration.
     */
    internal fun getConfiguration(): Configuration {
        return configuration ?: throw KoraException.NotConfigured()
    }

    /**
     * Reset the SDK configuration.
     */
    fun reset() {
        configuration = null
        apiClient = null
    }

    /**
     * Activity Result Contract for starting verification flow.
     *
     * Usage:
     * ```kotlin
     * val launcher = registerForActivityResult(KoraIDV.VerificationContract()) { result ->
     *     // Handle result
     * }
     * launcher.launch(VerificationRequest(externalId = "user-123"))
     * ```
     */
    class VerificationContract : ActivityResultContract<VerificationRequest, VerificationResult>() {

        override fun createIntent(context: Context, input: VerificationRequest): Intent {
            if (!isConfigured) {
                throw KoraException.NotConfigured()
            }

            return VerificationActivity.createIntent(
                context = context,
                request = input
            )
        }

        override fun parseResult(resultCode: Int, intent: Intent?): VerificationResult {
            return when (resultCode) {
                Activity.RESULT_OK -> {
                    intent?.getParcelableExtra<Verification>(VerificationActivity.EXTRA_VERIFICATION)
                        ?.let { VerificationResult.Success(it) }
                        ?: VerificationResult.Failure(KoraException.Unknown("Missing verification data"))
                }
                Activity.RESULT_CANCELED -> {
                    val error = intent?.getParcelableExtra<KoraException>(VerificationActivity.EXTRA_ERROR)
                    if (error != null) {
                        VerificationResult.Failure(error)
                    } else {
                        VerificationResult.Cancelled
                    }
                }
                else -> VerificationResult.Cancelled
            }
        }
    }
}

/**
 * Verification request parameters.
 *
 * @property externalId Your unique identifier for this user/verification
 * @property tier Verification tier level (default: standard)
 * @property documentTypes Allowed document types (default: all configured types)
 */
data class VerificationRequest(
    val externalId: String,
    val tier: VerificationTier = VerificationTier.STANDARD,
    val documentTypes: List<DocumentType>? = null
) : java.io.Serializable

/**
 * Result of a verification flow.
 */
sealed class VerificationResult {
    /**
     * Verification completed successfully.
     */
    data class Success(val verification: Verification) : VerificationResult()

    /**
     * Verification failed with an error.
     */
    data class Failure(val error: KoraException) : VerificationResult()

    /**
     * User cancelled the verification.
     */
    data object Cancelled : VerificationResult()
}

/**
 * Verification tier levels.
 */
enum class VerificationTier(val value: String) {
    BASIC("basic"),
    STANDARD("standard"),
    ENHANCED("enhanced")
}
