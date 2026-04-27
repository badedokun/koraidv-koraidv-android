// WalletModels.kt
// KoraIDV Wallet — W3C Verifiable Credential types for Android
//
// Types are prefixed with "Wallet" to avoid conflicts with existing KoraIDV types.

package com.koraidv.sdk.wallet

import org.json.JSONArray
import org.json.JSONObject

// MARK: - Verifiable Credential

data class WalletCredential(
    val context: List<String> = listOf("https://www.w3.org/ns/credentials/v2"),
    val id: String,
    val type: List<String> = listOf("VerifiableCredential", "KoraIdentityCredential"),
    val issuer: String,
    val issuanceDate: String,
    val expirationDate: String,
    val credentialSubject: WalletCredentialSubject,
    val credentialStatus: WalletCredentialStatus? = null,
    val proof: WalletDataIntegrityProof? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("@context", JSONArray(context))
        put("id", id)
        put("type", JSONArray(type))
        put("issuer", issuer)
        put("issuanceDate", issuanceDate)
        put("expirationDate", expirationDate)
        put("credentialSubject", credentialSubject.toJson())
        credentialStatus?.let { put("credentialStatus", it.toJson()) }
        proof?.let { put("proof", it.toJson()) }
    }

    companion object {
        fun fromJson(json: JSONObject): WalletCredential {
            val ctxArray = json.getJSONArray("@context")
            val context = (0 until ctxArray.length()).map { ctxArray.getString(it) }
            val typeArray = json.getJSONArray("type")
            val types = (0 until typeArray.length()).map { typeArray.getString(it) }

            return WalletCredential(
                context = context,
                id = json.getString("id"),
                type = types,
                issuer = json.getString("issuer"),
                issuanceDate = json.getString("issuanceDate"),
                expirationDate = json.getString("expirationDate"),
                credentialSubject = WalletCredentialSubject.fromJson(json.getJSONObject("credentialSubject")),
                credentialStatus = json.optJSONObject("credentialStatus")?.let { WalletCredentialStatus.fromJson(it) },
                proof = json.optJSONObject("proof")?.let { WalletDataIntegrityProof.fromJson(it) },
            )
        }
    }
}

// MARK: - Credential Subject

data class WalletCredentialSubject(
    val id: String,
    val fullName: String,
    val dateOfBirth: String? = null,
    val nationality: String? = null,
    val verificationLevel: String,
    val documentType: String,
    val documentCountry: String,
    val biometricMatch: Boolean,
    val livenessCheck: Boolean,
    val governmentDbVerified: Boolean,
    val verifiedAt: String,
    val confidenceScore: Double,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("fullName", fullName)
        dateOfBirth?.let { put("dateOfBirth", it) }
        nationality?.let { put("nationality", it) }
        put("verificationLevel", verificationLevel)
        put("documentType", documentType)
        put("documentCountry", documentCountry)
        put("biometricMatch", biometricMatch)
        put("livenessCheck", livenessCheck)
        put("governmentDbVerified", governmentDbVerified)
        put("verifiedAt", verifiedAt)
        put("confidenceScore", confidenceScore)
    }

    companion object {
        fun fromJson(json: JSONObject): WalletCredentialSubject = WalletCredentialSubject(
            id = json.getString("id"),
            fullName = json.getString("fullName"),
            dateOfBirth = json.optString("dateOfBirth").takeIf { it.isNotEmpty() },
            nationality = json.optString("nationality").takeIf { it.isNotEmpty() },
            verificationLevel = json.getString("verificationLevel"),
            documentType = json.getString("documentType"),
            documentCountry = json.getString("documentCountry"),
            biometricMatch = json.getBoolean("biometricMatch"),
            livenessCheck = json.getBoolean("livenessCheck"),
            governmentDbVerified = json.getBoolean("governmentDbVerified"),
            verifiedAt = json.getString("verifiedAt"),
            confidenceScore = json.getDouble("confidenceScore"),
        )
    }
}

// MARK: - Credential Status (StatusList2021)

data class WalletCredentialStatus(
    val id: String,
    val type: String = "StatusList2021Entry",
    val statusPurpose: String = "revocation",
    val statusListIndex: String,
    val statusListCredential: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("statusPurpose", statusPurpose)
        put("statusListIndex", statusListIndex)
        put("statusListCredential", statusListCredential)
    }

    companion object {
        fun fromJson(json: JSONObject): WalletCredentialStatus = WalletCredentialStatus(
            id = json.getString("id"),
            type = json.optString("type", "StatusList2021Entry"),
            statusPurpose = json.optString("statusPurpose", "revocation"),
            statusListIndex = json.getString("statusListIndex"),
            statusListCredential = json.getString("statusListCredential"),
        )
    }
}

// MARK: - Data Integrity Proof

data class WalletDataIntegrityProof(
    val type: String = "DataIntegrityProof",
    val cryptosuite: String = "eddsa-rdfc-2022",
    val created: String,
    val verificationMethod: String,
    val proofPurpose: String = "assertionMethod",
    val proofValue: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        put("cryptosuite", cryptosuite)
        put("created", created)
        put("verificationMethod", verificationMethod)
        put("proofPurpose", proofPurpose)
        put("proofValue", proofValue)
    }

    companion object {
        fun fromJson(json: JSONObject): WalletDataIntegrityProof = WalletDataIntegrityProof(
            type = json.optString("type", "DataIntegrityProof"),
            cryptosuite = json.optString("cryptosuite", "eddsa-rdfc-2022"),
            created = json.getString("created"),
            verificationMethod = json.getString("verificationMethod"),
            proofPurpose = json.optString("proofPurpose", "assertionMethod"),
            proofValue = json.getString("proofValue"),
        )
    }
}

// MARK: - Stored Credential (wrapper with metadata)

data class StoredWalletCredential(
    val id: String,
    val credential: WalletCredential,
    val storedAt: String,
    val issuerDID: String,
    val subjectName: String,
    val expiresAt: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("credential", credential.toJson())
        put("storedAt", storedAt)
        put("issuerDID", issuerDID)
        put("subjectName", subjectName)
        put("expiresAt", expiresAt)
    }

    companion object {
        fun fromJson(json: JSONObject): StoredWalletCredential = StoredWalletCredential(
            id = json.getString("id"),
            credential = WalletCredential.fromJson(json.getJSONObject("credential")),
            storedAt = json.getString("storedAt"),
            issuerDID = json.getString("issuerDID"),
            subjectName = json.getString("subjectName"),
            expiresAt = json.getString("expiresAt"),
        )
    }
}

// MARK: - Verifiable Presentation

data class WalletPresentation(
    val context: List<String> = listOf("https://www.w3.org/ns/credentials/v2"),
    val type: List<String> = listOf("VerifiablePresentation"),
    val holder: String? = null,
    val verifiableCredential: List<WalletCredential>,
    val created: String,
    val audience: String? = null,
    val challenge: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("@context", JSONArray(context))
        put("type", JSONArray(type))
        holder?.let { put("holder", it) }
        put("verifiableCredential", JSONArray(verifiableCredential.map { it.toJson() }))
        put("created", created)
        audience?.let { put("audience", it) }
        challenge?.let { put("challenge", it) }
    }

    companion object {
        fun fromJson(json: JSONObject): WalletPresentation {
            val ctxArray = json.getJSONArray("@context")
            val context = (0 until ctxArray.length()).map { ctxArray.getString(it) }
            val typeArray = json.getJSONArray("type")
            val types = (0 until typeArray.length()).map { typeArray.getString(it) }
            val vcArray = json.getJSONArray("verifiableCredential")
            val vcs = (0 until vcArray.length()).map { WalletCredential.fromJson(vcArray.getJSONObject(it)) }

            return WalletPresentation(
                context = context,
                type = types,
                holder = json.optString("holder").takeIf { it.isNotEmpty() },
                verifiableCredential = vcs,
                created = json.getString("created"),
                audience = json.optString("audience").takeIf { it.isNotEmpty() },
                challenge = json.optString("challenge").takeIf { it.isNotEmpty() },
            )
        }
    }
}

// MARK: - Errors

sealed class WalletException(message: String) : Exception(message) {
    class StorageFailed : WalletException("Failed to store credential securely.")
    class CredentialNotFound : WalletException("Credential not found.")
    class CredentialExpired : WalletException("Credential has expired.")
    class EncodingFailed : WalletException("Failed to encode credential data.")
    class DecodingFailed : WalletException("Failed to decode credential data.")
}
