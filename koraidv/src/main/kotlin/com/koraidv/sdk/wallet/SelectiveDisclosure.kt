// SelectiveDisclosure.kt
// KoraIDV Wallet — Selective disclosure profiles for Verifiable Presentations

package com.koraidv.sdk.wallet

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Disclosure profile controlling which claims are revealed in a Verifiable Presentation.
 */
sealed class DisclosureProfile(val name: String) {
    object Full : DisclosureProfile("full")
    object Onboarding : DisclosureProfile("onboarding")
    object AgeOnly : DisclosureProfile("ageOnly")
    object NationalityOnly : DisclosureProfile("nationalityOnly")
    object VerificationOnly : DisclosureProfile("verificationOnly")
    data class Custom(val claims: Set<DisclosureClaim>) : DisclosureProfile("custom")
}

/**
 * Individual claims that can be selectively disclosed.
 */
enum class DisclosureClaim {
    FULL_NAME,
    DATE_OF_BIRTH,
    NATIONALITY,
    VERIFICATION_LEVEL,
    DOCUMENT_TYPE,
    DOCUMENT_COUNTRY,
    BIOMETRIC_MATCH,
    LIVENESS_CHECK,
    GOVERNMENT_DB_VERIFIED,
    VERIFIED_AT,
    CONFIDENCE_SCORE,
}

/**
 * Engine for applying selective disclosure to credentials.
 */
object SelectiveDisclosureEngine {

    /**
     * Apply a disclosure profile to a credential, returning a new credential
     * containing only the disclosed claims in its subject.
     */
    fun apply(profile: DisclosureProfile, credential: WalletCredential): WalletCredential {
        val claims: Set<DisclosureClaim> = when (profile) {
            is DisclosureProfile.Full -> DisclosureClaim.entries.toSet()
            is DisclosureProfile.Onboarding -> setOf(
                DisclosureClaim.FULL_NAME,
                DisclosureClaim.DATE_OF_BIRTH,
                DisclosureClaim.NATIONALITY,
                DisclosureClaim.VERIFICATION_LEVEL,
                DisclosureClaim.DOCUMENT_TYPE,
                DisclosureClaim.DOCUMENT_COUNTRY,
            )
            is DisclosureProfile.AgeOnly -> setOf(DisclosureClaim.DATE_OF_BIRTH)
            is DisclosureProfile.NationalityOnly -> setOf(DisclosureClaim.NATIONALITY)
            is DisclosureProfile.VerificationOnly -> setOf(
                DisclosureClaim.VERIFICATION_LEVEL,
                DisclosureClaim.VERIFIED_AT,
                DisclosureClaim.CONFIDENCE_SCORE,
            )
            is DisclosureProfile.Custom -> profile.claims
        }

        val subject = credential.credentialSubject
        val disclosed = WalletCredentialSubject(
            id = subject.id,
            fullName = if (claims.contains(DisclosureClaim.FULL_NAME)) subject.fullName else "",
            dateOfBirth = if (claims.contains(DisclosureClaim.DATE_OF_BIRTH)) subject.dateOfBirth else null,
            nationality = if (claims.contains(DisclosureClaim.NATIONALITY)) subject.nationality else null,
            verificationLevel = if (claims.contains(DisclosureClaim.VERIFICATION_LEVEL)) subject.verificationLevel else "",
            documentType = if (claims.contains(DisclosureClaim.DOCUMENT_TYPE)) subject.documentType else "",
            documentCountry = if (claims.contains(DisclosureClaim.DOCUMENT_COUNTRY)) subject.documentCountry else "",
            biometricMatch = claims.contains(DisclosureClaim.BIOMETRIC_MATCH) && subject.biometricMatch,
            livenessCheck = claims.contains(DisclosureClaim.LIVENESS_CHECK) && subject.livenessCheck,
            governmentDbVerified = claims.contains(DisclosureClaim.GOVERNMENT_DB_VERIFIED) && subject.governmentDbVerified,
            verifiedAt = if (claims.contains(DisclosureClaim.VERIFIED_AT)) subject.verifiedAt else "",
            confidenceScore = if (claims.contains(DisclosureClaim.CONFIDENCE_SCORE)) subject.confidenceScore else 0.0,
        )

        return credential.copy(credentialSubject = disclosed)
    }

    /**
     * For ageOnly profile, compute whether the subject is over 18.
     */
    fun computeAgeOver18(dateOfBirth: String?): Boolean {
        if (dateOfBirth.isNullOrEmpty()) return false
        return try {
            val dob = LocalDate.parse(dateOfBirth, DateTimeFormatter.ISO_DATE)
            Period.between(dob, LocalDate.now()).years >= 18
        } catch (_: DateTimeParseException) {
            false
        }
    }
}
