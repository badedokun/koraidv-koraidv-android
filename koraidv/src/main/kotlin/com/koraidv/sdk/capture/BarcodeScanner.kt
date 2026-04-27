package com.koraidv.sdk.capture

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On-device barcode decoder for the back side of identity documents.
 *
 * The KoraIDV pipeline cascades through three decoders in priority order:
 *  1. **This class (ML Kit)** — runs on the captured back-side bitmap before
 *     upload. When it succeeds, the decoded AAMVA payload travels to the
 *     server in [com.koraidv.sdk.api.UploadDocumentBackRequest.decodedBarcodePayload]
 *     and the server skips image decoding entirely.
 *  2. **zxing-cpp** (server-side) — primary decoder when the client failed.
 *  3. **pdf417decoder** (server-side) — fallback for captures zxing-cpp
 *     can't read.
 *
 * Why decode on-device when the server can do it? Three reasons:
 *  - **Latency**: phone decode finishes in ~50-200 ms vs. ~1-3 s server
 *    round-trip + cascade (image upload + ml-service work).
 *  - **Cost**: zero ML-service compute on the happy path.
 *  - **Reliability**: ML Kit's PDF417 implementation is the same one
 *    Google Wallet uses to scan boarding passes. Empirically more robust
 *    on real phone captures than either zxing-cpp or pdf417decoder.
 *
 * When this fails, we silently send the image to the server — no user
 * impact, just a slower path.
 *
 * Restricted to PDF417 today (US/CA driver's licenses). The same
 * `BarcodeScanning` client also handles QR (Nigeria voter's card),
 * DataMatrix (some EU permits), and Aztec (e-tickets) — extending later
 * is one line in [BarcodeScannerOptions.Builder.setBarcodeFormats].
 *
 * See `docs/architecture/idv-decode-roadmap.md` Phase 2.
 */
class BarcodeScanner {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            // Scope down: trying every format slows decode and risks false
            // positives on busy backgrounds. Add formats here as we onboard
            // new document types per Phase 4 of the roadmap.
            .setBarcodeFormats(Barcode.FORMAT_PDF417)
            .build()
    )

    /**
     * Attempt to decode a PDF417 barcode from the supplied bitmap. Returns
     * the raw AAMVA payload as a single string (newline-separated records,
     * exactly the form the server's AAMVA parser expects) or `null` when
     * no barcode was found or decoding failed.
     */
    suspend fun decodePdf417(bitmap: Bitmap): String? = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val payload = barcodes.firstOrNull()?.rawValue
                if (payload.isNullOrBlank()) {
                    Log.d(TAG, "no PDF417 detected in bitmap (${bitmap.width}x${bitmap.height})")
                    cont.resume(null)
                } else {
                    Log.d(TAG, "PDF417 decoded on-device, ${payload.length} bytes")
                    cont.resume(payload)
                }
            }
            .addOnFailureListener { e ->
                // ML Kit can throw transient errors on malformed inputs; we
                // don't escalate — the server cascade will pick it up.
                Log.w(TAG, "ML Kit barcode scan failed: ${e.message}")
                cont.resume(null)
            }
            .addOnCanceledListener { cont.resume(null) }
    }

    /** Release ML Kit resources. */
    fun close() {
        scanner.close()
    }

    private companion object {
        const val TAG = "KoraIDV.BarcodeScanner"
    }
}
