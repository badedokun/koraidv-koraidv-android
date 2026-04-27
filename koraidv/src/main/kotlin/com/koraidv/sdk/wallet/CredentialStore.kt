// CredentialStore.kt
// KoraIDV Wallet — AndroidKeyStore-backed encrypted credential storage

package com.koraidv.sdk.wallet

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secure credential storage using AndroidKeyStore for key management
 * and AES-GCM encryption for credential data at rest.
 */
internal class CredentialStore(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "kora_wallet"
        private const val KEY_ALIAS = "kora_wallet_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val GCM_TAG_LENGTH = 128
        private const val INDEX_KEY = "__credential_ids__"
    }

    // MARK: - Key Management

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    // MARK: - Encrypt / Decrypt

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        // Prepend IV (12 bytes) to ciphertext
        return iv + ciphertext
    }

    private fun decrypt(data: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, 12)
        val ciphertext = data.copyOfRange(12, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    // MARK: - CRUD Operations

    fun save(id: String, credential: StoredWalletCredential) {
        val json = credential.toJson().toString()
        val encrypted = encrypt(json.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit().putString(id, encoded).apply()
        addToIndex(id)
    }

    fun load(id: String): StoredWalletCredential? {
        val encoded = prefs.getString(id, null) ?: return null
        return try {
            val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
            val json = String(decrypt(encrypted), Charsets.UTF_8)
            StoredWalletCredential.fromJson(org.json.JSONObject(json))
        } catch (_: Exception) {
            null
        }
    }

    fun delete(id: String) {
        prefs.edit().remove(id).apply()
        removeFromIndex(id)
    }

    fun listIds(): List<String> {
        val raw = prefs.getString(INDEX_KEY, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split(",").filter { it.isNotEmpty() }
    }

    // MARK: - Index Management

    private fun addToIndex(id: String) {
        val ids = listIds().toMutableSet()
        ids.add(id)
        prefs.edit().putString(INDEX_KEY, ids.joinToString(",")).apply()
    }

    private fun removeFromIndex(id: String) {
        val ids = listIds().toMutableSet()
        ids.remove(id)
        prefs.edit().putString(INDEX_KEY, ids.joinToString(",")).apply()
    }
}
