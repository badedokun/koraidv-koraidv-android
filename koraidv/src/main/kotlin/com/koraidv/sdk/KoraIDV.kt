package com.koraidv.sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import com.koraidv.sdk.api.ApiClient
import com.koraidv.sdk.ui.VerificationActivity
import kotlinx.parcelize.Parcelize

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

    /**
     * Holds configuration and API client together so they are always
     * set and read atomically via a single volatile reference.
     */
    private data class SdkState(
        val configuration: Configuration,
        val apiClient: ApiClient
    )

    @Volatile
    private var sdkState: SdkState? = null

    /**
     * SDK version
     */
    const val VERSION = "1.0.5"

    /**
     * Configure the SDK with the provided configuration.
     * Thread-safe — a single volatile write ensures both configuration
     * and API client are visible atomically to all threads.
     *
     * @param configuration SDK configuration with API key and tenant ID
     */
    fun configure(configuration: Configuration) {
        sdkState = SdkState(configuration, ApiClient(configuration))
    }

    /**
     * Check if the SDK is configured.
     */
    val isConfigured: Boolean
        get() = sdkState != null

    /**
     * Get the current configuration.
     */
    internal fun getConfiguration(): Configuration {
        return sdkState?.configuration ?: throw KoraException.NotConfigured()
    }

    /**
     * Get the current API client.
     */
    internal val apiClient: ApiClient?
        get() = sdkState?.apiClient

    /**
     * Reset the SDK configuration.
     * Thread-safe — a single volatile write.
     */
    fun reset() {
        sdkState = null
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
            return parseVerificationResult(resultCode, intent)
        }
    }

    /**
     * Activity Result Contract for resuming an existing verification flow.
     *
     * Usage:
     * ```kotlin
     * val launcher = registerForActivityResult(KoraIDV.ResumeVerificationContract()) { result ->
     *     // Handle result
     * }
     * launcher.launch("ver-uuid")
     * ```
     */
    class ResumeVerificationContract : ActivityResultContract<String, VerificationResult>() {

        override fun createIntent(context: Context, input: String): Intent {
            if (!isConfigured) {
                throw KoraException.NotConfigured()
            }

            return VerificationActivity.createResumeIntent(
                context = context,
                verificationId = input
            )
        }

        override fun parseResult(resultCode: Int, intent: Intent?): VerificationResult {
            return parseVerificationResult(resultCode, intent)
        }
    }

    private fun parseVerificationResult(resultCode: Int, intent: Intent?): VerificationResult {
        return when (resultCode) {
            Activity.RESULT_OK -> {
                val verification = intent?.getParcelableExtraCompat<Verification>(
                    VerificationActivity.EXTRA_VERIFICATION
                )
                verification?.let { VerificationResult.Success(it) }
                    ?: VerificationResult.Failure(KoraException.Unknown("Missing verification data"))
            }
            Activity.RESULT_CANCELED -> {
                val error = intent?.getParcelableExtraCompat<KoraException>(
                    VerificationActivity.EXTRA_ERROR
                )
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

/**
 * Backward-compatible helper for [Intent.getParcelableExtra] that uses
 * the type-safe overload on API 33+ and the deprecated variant on older APIs.
 */
@Suppress("DEPRECATION")
internal inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? {
    return if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(key, T::class.java)
    } else {
        getParcelableExtra(key) as? T
    }
}

/**
 * Verification request parameters.
 *
 * @property externalId Your unique identifier for this user/verification
 * @property tier Verification tier level (default: standard)
 * @property documentTypes Allowed document types (default: all configured types)
 * @property expectedFirstName Expected first name from registration (for name matching)
 * @property expectedLastName Expected last name from registration (for name matching)
 */
@Parcelize
data class VerificationRequest(
    val externalId: String,
    val tier: VerificationTier = VerificationTier.STANDARD,
    val documentTypes: List<DocumentType>? = null,
    val expectedFirstName: String? = null,
    val expectedLastName: String? = null
) : Parcelable

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
