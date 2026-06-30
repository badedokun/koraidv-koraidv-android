package com.koraidv.sdk.capture

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Enforces the KoraIDV eyeglasses policy at capture time — Android port of the
 * iOS `EyeVisibilityChecker`. The subject's eyes MUST be clearly visible in the
 * selfie; rejects sunglasses (dark, tinted, AND mirrored/reflective) and heavy
 * glare. Clear prescription glasses pass.
 *
 * Heuristic (no ML model): measure the eye region against the rest of the face
 * on four signals, because no single one catches every lens —
 *   • DARK     → eye region much darker than the face.
 *   • MIRRORED → eye region BRIGHTER than the face, and/or many specular
 *                (blown-out) pixels from reflections of the scene.
 *   • TINTED   → eye region noticeably more colour-saturated than the face.
 *   • FLAT     → eye region with almost no contrast (no eye structure).
 *
 * Thresholds CALIBRATED from on-device iOS readings (mirrored sunglasses vs.
 * bare eyes, same subject/room):
 *   sunglasses: lumaR 0.70  bright 0.06  satR 0.82
 *   bare eyes : lumaR 0.88+ bright 0.00  satR 1.13+
 * Two independent signals separate them with margin: specular fraction and
 * saturation ratio (a real eye is ≥ face-skin colour; a neutral reflective/dark
 * lens is much less). Every capture sets [lastDebug] for QA calibration.
 */
object EyeVisibilityChecker {

    enum class Outcome {
        CLEAR, SUNGLASSES, REFLECTIVE, TINTED, OBSCURED, NO_FACE;
        // FAIL CLOSED: anything that isn't a confirmed-clear eye blocks the
        // capture — including NO_FACE. The old fail-open on NO_FACE let
        // sunglasses through whenever face detection blipped on a frame (cold
        // first capture / rapid retakes) — the "hammer it enough and it slips
        // through" pattern (BanffPay 2026-06-30).
        val rejects: Boolean get() = this != CLEAR
    }

    // TIGHTENED to fail closed (BanffPay 2026-06-30) — see iOS EyeVisibilityChecker.
    private const val DARK_RATIO_REJECT = 0.78    // eyes < 78% of face brightness → lens (was 0.62)
    private const val BRIGHT_RATIO_REJECT = 1.08  // eyes > 108% of face brightness → bright reflective
    private const val SPECULAR_FRAC_REJECT = 0.025 // ≥2.5% blown-out pixels → reflections (was 0.035)
    private const val SAT_LOW_REJECT = 0.95       // eye colour < 95% of face → neutral lens (was 0.85)
    private const val SAT_HIGH_REJECT = 1.6       // eye colour >> face → colour tint
    private const val SAT_HIGH_ABS = 0.30         // …and absolutely colourful
    private const val MIN_EYE_CONTRAST = 0.10     // eye-region std below this → flat tint

    @Volatile
    var lastDebug: String = ""
        private set

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .build()
        )
    }

    suspend fun check(bitmap: Bitmap): Outcome {
        // Retry detection (transient failures / cold first call). A real selfie
        // always has a face — the capture auto-fired on a stable one — so if we
        // still can't find it the frame is unusable and we FAIL CLOSED (→ retake)
        // rather than pass it unchecked.
        repeat(3) {
            val faces = try { detectOnce(bitmap) } catch (_: Exception) { emptyList() }
            if (faces.isNotEmpty()) return evaluate(bitmap, faces)
        }
        lastDebug = "eye: no face after retries → reject (fail-closed)"
        val dbg = try { com.koraidv.sdk.KoraIDV.getConfiguration().debugLogging } catch (_: Exception) { false }
        if (dbg) Log.d("KoraIDV", lastDebug)
        return Outcome.NO_FACE
    }

    private suspend fun detectOnce(bitmap: Bitmap): List<Face> = suspendCoroutine { cont ->
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { faces -> cont.resume(faces) }
            .addOnFailureListener { cont.resume(emptyList()) }
    }

    private fun evaluate(bitmap: Bitmap, faces: List<Face>): Outcome {
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            ?: return Outcome.NO_FACE

        val eyePts = eyePoints(face)
        if (eyePts.isEmpty()) {
            lastDebug = "eye: no eye contour → sunglasses"
            return Outcome.SUNGLASSES
        }

        val eye = stats(bitmap, eyeRect(eyePts, expand = 0.35f))
        val faceRef = stats(bitmap, face.boundingBox)
        if (eye.count == 0 || faceRef.lumaMean <= 0.02) return Outcome.CLEAR

        val ratio = eye.lumaMean / faceRef.lumaMean
        val satRatio = eye.satMean / max(faceRef.satMean, 0.01)

        val outcome = when {
            ratio < DARK_RATIO_REJECT -> Outcome.SUNGLASSES
            ratio > BRIGHT_RATIO_REJECT || eye.brightFraction > SPECULAR_FRAC_REJECT -> Outcome.REFLECTIVE
            satRatio < SAT_LOW_REJECT -> Outcome.REFLECTIVE
            satRatio > SAT_HIGH_REJECT && eye.satMean > SAT_HIGH_ABS -> Outcome.TINTED
            eye.lumaStd < MIN_EYE_CONTRAST -> Outcome.OBSCURED
            else -> Outcome.CLEAR
        }

        lastDebug = "eye lumaR=%.2f std=%.2f bright=%.2f eyeSat=%.2f faceSat=%.2f satR=%.2f → %s"
            .format(ratio, eye.lumaStd, eye.brightFraction, eye.satMean, faceRef.satMean, satRatio, outcome)
        val debug = try { com.koraidv.sdk.KoraIDV.getConfiguration().debugLogging } catch (_: Exception) { false }
        if (debug) Log.d("KoraIDV", lastDebug)
        return outcome
    }

    /** Eye region = both eyes' contours (or landmarks as fallback). */
    private fun eyePoints(face: Face): List<PointF> {
        val contour = (face.getContour(FaceContour.LEFT_EYE)?.points ?: emptyList()) +
                (face.getContour(FaceContour.RIGHT_EYE)?.points ?: emptyList())
        if (contour.isNotEmpty()) return contour
        return listOfNotNull(
            face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)?.position,
            face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)?.position
        )
    }

    private fun eyeRect(pts: List<PointF>, expand: Float): Rect {
        var minX = pts[0].x; var maxX = pts[0].x; var minY = pts[0].y; var maxY = pts[0].y
        for (p in pts) {
            minX = min(minX, p.x); maxX = max(maxX, p.x)
            minY = min(minY, p.y); maxY = max(maxY, p.y)
        }
        val ew = (maxX - minX) * expand; val eh = (maxY - minY) * expand
        return Rect((minX - ew).toInt(), (minY - eh).toInt(), (maxX + ew).toInt(), (maxY + eh).toInt())
    }

    private data class RegionStats(
        val lumaMean: Double, val lumaStd: Double,
        val brightFraction: Double, val satMean: Double, val count: Int
    )

    private fun stats(bitmap: Bitmap, rect: Rect): RegionStats {
        val x0 = rect.left.coerceIn(0, bitmap.width - 1)
        val y0 = rect.top.coerceIn(0, bitmap.height - 1)
        val x1 = rect.right.coerceIn(x0 + 1, bitmap.width)
        val y1 = rect.bottom.coerceIn(y0 + 1, bitmap.height)
        val w = x1 - x0; val h = y1 - y0
        if (w <= 0 || h <= 0) return RegionStats(0.0, 0.0, 0.0, 0.0, 0)

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, x0, y0, w, h)

        var sum = 0.0; var sumSq = 0.0; var satSum = 0.0; var n = 0; var bright = 0
        var yy = 0
        while (yy < h) {
            val row = yy * w
            var xx = 0
            while (xx < w) {
                val p = pixels[row + xx]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val luma = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
                val sat = if (mx > 0) (mx - mn).toDouble() / mx else 0.0
                sum += luma; sumSq += luma * luma; satSum += sat; n++
                if (luma > 0.85) bright++
                xx += 2  // subsample for speed
            }
            yy += 2
        }
        if (n == 0) return RegionStats(0.0, 0.0, 0.0, 0.0, 0)
        val mean = sum / n
        val variance = max(0.0, sumSq / n - mean * mean)
        return RegionStats(mean, sqrt(variance), bright.toDouble() / n, satSum / n, n)
    }
}
