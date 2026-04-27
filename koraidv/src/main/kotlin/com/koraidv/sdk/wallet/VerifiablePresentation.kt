// VerifiablePresentation.kt
// KoraIDV Wallet — VP creation with selective disclosure

package com.koraidv.sdk.wallet

import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Factory for building W3C Verifiable Presentations.
 */
object WalletPresentationBuilder {

    /**
     * Create a Verifiable Presentation from a credential with selective disclosure.
     *
     * @param credential The full Verifiable Credential.
     * @param profile The disclosure profile to apply.
     * @param holder Optional holder DID.
     * @param audience The intended verifier domain or DID.
     * @param nonce A challenge nonce from the verifier for replay protection.
     * @return A [WalletPresentation] ready for transmission.
     */
    fun create(
        credential: WalletCredential,
        profile: DisclosureProfile,
        holder: String? = null,
        audience: String? = null,
        nonce: String? = null,
    ): WalletPresentation {
        val disclosed = SelectiveDisclosureEngine.apply(profile, credential)
        val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

        return WalletPresentation(
            context = listOf("https://www.w3.org/ns/credentials/v2"),
            type = listOf("VerifiablePresentation"),
            holder = holder,
            verifiableCredential = listOf(disclosed),
            created = now,
            audience = audience,
            challenge = nonce,
        )
    }

    /**
     * Serialize a presentation to a JSON string.
     */
    fun encode(presentation: WalletPresentation): String {
        return presentation.toJson().toString(2)
    }

    /**
     * Deserialize a presentation from a JSON string.
     */
    fun decode(json: String): WalletPresentation {
        return WalletPresentation.fromJson(org.json.JSONObject(json))
    }
}
