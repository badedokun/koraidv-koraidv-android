// KoraWallet.kt
// KoraIDV Wallet — Main wallet class for Android

package com.koraidv.sdk.wallet

import android.content.Context
import android.net.Uri
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Main entry point for the Kora Wallet SDK module on Android.
 *
 * Provides credential storage (AndroidKeyStore-backed), selective disclosure,
 * Verifiable Presentation creation, and QR/deep-link sharing.
 *
 * @param context Android application context for secure storage access.
 */
class KoraWallet(context: Context) {

    private val store: CredentialStore = CredentialStore(context.applicationContext)

    // MARK: - Credential Management

    /**
     * Store a Verifiable Credential in the wallet.
     *
     * @param credential The W3C Verifiable Credential to store.
     * @return The storage ID (same as the credential's `id`).
     */
    fun store(credential: WalletCredential): String {
        val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val stored = StoredWalletCredential(
            id = credential.id,
            credential = credential,
            storedAt = now,
            issuerDID = credential.issuer,
            subjectName = credential.credentialSubject.fullName,
            expiresAt = credential.expirationDate,
        )
        store.save(credential.id, stored)
        return credential.id
    }

    /**
     * Retrieve all stored credentials.
     */
    fun getCredentials(): List<StoredWalletCredential> {
        return store.listIds().mapNotNull { store.load(it) }
    }

    /**
     * Retrieve a single credential by ID.
     */
    fun getCredential(id: String): StoredWalletCredential? {
        return store.load(id)
    }

    /**
     * Delete a credential from the wallet.
     */
    fun deleteCredential(id: String) {
        store.delete(id)
    }

    /**
     * Number of credentials currently stored.
     */
    val credentialCount: Int
        get() = store.listIds().size

    // MARK: - Presentation

    /**
     * Create a Verifiable Presentation with selective disclosure.
     *
     * @param credentialId ID of the stored credential to present.
     * @param profile Disclosure profile controlling which claims are revealed.
     * @param audience The intended verifier (domain or DID).
     * @param nonce Challenge nonce from the verifier for replay protection.
     * @return A [WalletPresentation] containing the disclosed credential.
     * @throws WalletException.CredentialNotFound if the credential does not exist.
     * @throws WalletException.CredentialExpired if the credential has expired.
     */
    fun createPresentation(
        credentialId: String,
        profile: DisclosureProfile,
        audience: String? = null,
        nonce: String? = null,
    ): WalletPresentation {
        val stored = store.load(credentialId)
            ?: throw WalletException.CredentialNotFound()

        if (isExpired(credentialId)) {
            throw WalletException.CredentialExpired()
        }

        return WalletPresentationBuilder.create(
            credential = stored.credential,
            profile = profile,
            audience = audience,
            nonce = nonce,
        )
    }

    /**
     * Generate a deep link URI for sharing a presentation.
     */
    fun generateDeepLink(
        presentation: WalletPresentation,
        profile: DisclosureProfile = DisclosureProfile.Full,
    ): Uri? {
        return WalletQRCode.deepLink(presentation, profile)
    }

    // MARK: - Expiry

    /**
     * Check whether a stored credential has expired.
     */
    fun isExpired(credentialId: String): Boolean {
        val stored = store.load(credentialId) ?: return true
        return try {
            val expires = Instant.parse(stored.expiresAt)
            Instant.now().isAfter(expires)
        } catch (_: Exception) {
            false
        }
    }
}
