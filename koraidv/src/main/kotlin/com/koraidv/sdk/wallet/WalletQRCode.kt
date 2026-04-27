// WalletQRCode.kt
// KoraIDV Wallet — QR code generation for credential presentations

package com.koraidv.sdk.wallet

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Base64

/**
 * Generates QR codes and deep links for Verifiable Presentations.
 *
 * QR code bitmap generation uses a minimal manual approach to avoid
 * requiring external libraries like ZXing. For production use with
 * high error correction, consider integrating a full QR library.
 */
object WalletQRCode {

    private const val MAX_INLINE_SIZE = 2048

    /**
     * Generate a deep link URI for the given presentation.
     *
     * If the JSON payload fits within 2 KB, it is base64url-encoded inline.
     * Otherwise a reference link with credential ID and profile name is produced.
     */
    fun deepLink(
        presentation: WalletPresentation,
        profile: DisclosureProfile = DisclosureProfile.Full,
    ): Uri? {
        val json = presentation.toJson().toString()
        val data = json.toByteArray(Charsets.UTF_8)

        return if (data.size <= MAX_INLINE_SIZE) {
            val encoded = Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            Uri.parse("korastratum://present?data=$encoded")
        } else {
            val credId = presentation.verifiableCredential.firstOrNull()?.id ?: "unknown"
            val profileName = profile.name
            Uri.parse("korastratum://present?ref=$credId&profile=$profileName")
        }
    }

    /**
     * Generate a QR code bitmap for a presentation.
     *
     * This creates a simple 1-bit bitmap encoding the deep link string.
     * Each module is rendered as a [moduleSize]x[moduleSize] block of pixels.
     *
     * Note: This is a placeholder that produces a data URI bitmap.
     * For production, integrate a QR encoder (ZXing, etc.).
     */
    fun generate(
        presentation: WalletPresentation,
        profile: DisclosureProfile = DisclosureProfile.Full,
        size: Int = 300,
    ): Bitmap? {
        val uri = deepLink(presentation, profile) ?: return null
        return generateQRBitmap(uri.toString(), size)
    }

    /**
     * Generate a simple placeholder bitmap with the encoded data.
     * In production, replace with a proper QR encoder.
     */
    private fun generateQRBitmap(content: String, size: Int): Bitmap {
        // Placeholder: create a bitmap with the content hash pattern.
        // Real implementation should use a QR code encoding library.
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        // Draw a simple border pattern to indicate this is a QR placeholder
        for (i in 0 until size) {
            bitmap.setPixel(i, 0, Color.BLACK)
            bitmap.setPixel(i, size - 1, Color.BLACK)
            bitmap.setPixel(0, i, Color.BLACK)
            bitmap.setPixel(size - 1, i, Color.BLACK)
        }

        return bitmap
    }
}
