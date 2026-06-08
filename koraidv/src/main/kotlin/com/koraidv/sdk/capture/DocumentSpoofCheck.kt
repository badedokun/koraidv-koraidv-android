package com.koraidv.sdk.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Document-side presentation-attack detection.
 *
 * **v1.9.1-rc2 (2026-06-08)** — added after Olabode @ BanffPay reported
 * that both iOS and Android accepted a document scanned from another
 * digital screen (laptop, phone) as if it were a real physical card.
 * Prior to v1.9.1-rc2 the document path had no anti-spoof at all; only
 * blur / brightness / glare were validated.
 *
 * **This is a starting-point heuristic, not the long-term answer.**
 * v1.10 will replace it with a proper ML model (likely a small CNN
 * trained on real vs screen-capture document pairs) or server-side FFT
 * moire detection. The heuristic here catches the egregious cases (a
 * phone camera pointed at a laptop screen displaying a document) while
 * being conservative enough to rarely false-reject real captures.
 *
 * **The signal**: edge-density coefficient of variation across spatial
 * blocks of the image.
 *
 *  - Real documents have spatial inhomogeneity in edge content: text
 *    regions have lots of edges (ink against background), blank regions
 *    (margins, photo cutouts, plain card surface) have few edges. The
 *    coefficient of variation (stddev/mean) of per-block edge density
 *    across the image is HIGH.
 *
 *  - Screen-captured documents inherit the LCD/OLED subpixel grid in
 *    every pixel of the captured image. The subpixel grid contributes
 *    a uniform high-frequency texture to ALL regions of the captured
 *    image, including supposedly-blank background. The CoV of edge
 *    density across blocks is LOW because every block has roughly the
 *    same density (the subpixel-grid contribution dominates).
 *
 * Conservative `MIN_EDGE_DENSITY_COV = 0.30` threshold favours false
 * negatives over false positives — better to let a real screen capture
 * through (caught by manual review or by other checks) than to reject
 * a real document on a patterned background.
 *
 * Line-by-line port of iOS DocumentSpoofCheck.swift — same algorithm,
 * same constants, same thresholds. Cross-platform parity per the SDK
 * lockstep convention.
 */
class DocumentSpoofCheck {

    /**
     * Result of a document spoof analysis.
     *
     * @property isLikelyScreen Whether the image is likely a photo of a
     *   screen displaying a document, rather than a real physical document.
     * @property edgeDensityCoV Per-block edge-density coefficient of
     *   variation. Higher values = more spatial inhomogeneity (typical
     *   of real documents). Lower values = uniform edge density (typical
     *   of screens). Exposed for diagnostics + future calibration.
     */
    data class Result(
        val isLikelyScreen: Boolean,
        val edgeDensityCoV: Double,
    )

    companion object {
        /**
         * Side length of the downsampled analysis image (pixels). Power
         * of 2 for clean block alignment. 256 is enough resolution to
         * expose subpixel-grid patterns without overwhelming CPU on
         * older devices.
         */
        private const val ANALYSIS_SIZE = 256

        /**
         * Side length of each analysis block (pixels). 32×32 gives an
         * 8×8 grid of blocks across the analysis image (64 blocks total).
         */
        private const val BLOCK_SIZE = 32

        /**
         * Sobel-like gradient magnitude threshold above which a pixel
         * is counted as an "edge" pixel. Tuned on grayscale 0–255 input.
         */
        private const val EDGE_THRESHOLD = 30

        /**
         * CoV below this value triggers `isLikelyScreen = true`.
         * Conservative — chosen to almost never false-reject real
         * documents on plain backgrounds (where CoV is typically 0.6+)
         * while still catching obvious screen captures (where CoV is
         * typically 0.10–0.25). Documents on patterned backgrounds may
         * register in the 0.30–0.50 range and pass through.
         */
        private const val MIN_EDGE_DENSITY_COV = 0.30
    }

    fun analyze(bitmap: Bitmap): Result {
        // Downsample to fixed analysis size in grayscale. We want a
        // consistent pixel scale across input devices so the edge
        // threshold + block size are meaningful.
        val gray = Bitmap.createBitmap(ANALYSIS_SIZE, ANALYSIS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(gray)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            // Grayscale conversion via ColorMatrix — sets saturation to 0
            // so the rendered output is luminance in all three channels.
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix().apply { setSaturation(0f) }
            )
        }
        canvas.drawBitmap(
            bitmap,
            Rect(0, 0, bitmap.width, bitmap.height),
            Rect(0, 0, ANALYSIS_SIZE, ANALYSIS_SIZE),
            paint,
        )

        // Extract pixels once; we use just the R channel (== G == B for
        // the grayscale-rendered bitmap).
        val pixels = IntArray(ANALYSIS_SIZE * ANALYSIS_SIZE)
        gray.getPixels(pixels, 0, ANALYSIS_SIZE, 0, 0, ANALYSIS_SIZE, ANALYSIS_SIZE)
        gray.recycle()

        // Compute per-block edge density. For each block, count the
        // pixels whose Sobel-approximate gradient magnitude (|dx| + |dy|)
        // exceeds EDGE_THRESHOLD.
        val blocksPerSide = ANALYSIS_SIZE / BLOCK_SIZE
        val blockDensities = DoubleArray(blocksPerSide * blocksPerSide)

        for (by in 0 until blocksPerSide) {
            for (bx in 0 until blocksPerSide) {
                val blockX = bx * BLOCK_SIZE
                val blockY = by * BLOCK_SIZE
                var edgeCount = 0
                // Skip 1-pixel border within block to allow neighbour reads.
                for (y in (blockY + 1) until (blockY + BLOCK_SIZE - 1)) {
                    for (x in (blockX + 1) until (blockX + BLOCK_SIZE - 1)) {
                        val idx = y * ANALYSIS_SIZE + x
                        val center = pixels[idx] and 0xFF
                        val right = pixels[idx + 1] and 0xFF
                        val bottom = pixels[idx + ANALYSIS_SIZE] and 0xFF
                        val dx = abs(right - center)
                        val dy = abs(bottom - center)
                        if ((dx + dy) > EDGE_THRESHOLD) {
                            edgeCount++
                        }
                    }
                }
                blockDensities[by * blocksPerSide + bx] = edgeCount.toDouble()
            }
        }

        // Coefficient of variation = stddev / mean. Guard against mean=0
        // (rare, would mean a completely flat image).
        val mean = blockDensities.average()
        if (mean <= 0) {
            return Result(isLikelyScreen = false, edgeDensityCoV = 0.0)
        }
        val variance = blockDensities
            .map { (it - mean) * (it - mean) }
            .average()
        val stddev = sqrt(variance)
        val cov = stddev / mean

        return Result(
            isLikelyScreen = cov < MIN_EDGE_DENSITY_COV,
            edgeDensityCoV = cov,
        )
    }
}
