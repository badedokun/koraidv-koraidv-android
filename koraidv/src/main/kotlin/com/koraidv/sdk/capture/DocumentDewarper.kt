package com.koraidv.sdk.capture

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

/**
 * REQ-003 FR-003.4 — detect a document quadrilateral in the captured photo and
 * warp it to a frame-filling, ID-1 aspect (1.586:1) bitmap so the review frame
 * is always edge-to-edge regardless of how the user held the camera.
 *
 * Falls through (returns null) when no convincing quad is found; the caller
 * is expected to keep the original capture in that case.
 */
object DocumentDewarper {

    private const val TAG = "KoraIDV"
    private const val PROCESSING_MAX_DIM = 1000
    private const val OUTPUT_WIDTH = 1600
    private const val ID1_ASPECT = 1.586f
    private const val MIN_AREA_FRACTION = 0.20
    private const val POLY_EPSILON_FRACTION = 0.02

    @Volatile
    private var initialized = false

    private fun ensureInit(): Boolean {
        if (initialized) return true
        return try {
            initialized = OpenCVLoader.initLocal()
            if (!initialized) {
                Log.w(TAG, "DocumentDewarper: OpenCV.initLocal() returned false")
            }
            initialized
        } catch (t: Throwable) {
            Log.w(TAG, "DocumentDewarper: OpenCV init failed", t)
            false
        }
    }

    fun dewarp(bitmap: Bitmap): Bitmap? {
        if (!ensureInit()) return null

        val src = Mat()
        val gray = Mat()
        val edges = Mat()
        val resized = Mat()
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()
        try {
            Utils.bitmapToMat(bitmap, src)

            val origW = src.cols()
            val origH = src.rows()
            val scale = max(origW, origH).toDouble() / PROCESSING_MAX_DIM
            if (scale > 1.0) {
                Imgproc.resize(src, resized, Size(origW / scale, origH / scale))
            } else {
                src.copyTo(resized)
            }

            Imgproc.cvtColor(resized, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(gray, edges, 50.0, 150.0)
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.dilate(edges, edges, kernel)

            Imgproc.findContours(
                edges, contours, hierarchy,
                Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE
            )

            val resW = resized.cols()
            val resH = resized.rows()
            val totalArea = resW.toDouble() * resH.toDouble()
            val minArea = totalArea * MIN_AREA_FRACTION

            val sorted = contours.sortedByDescending { Imgproc.contourArea(it) }
            var quad: MatOfPoint2f? = null
            for (c in sorted.take(8)) {
                val area = Imgproc.contourArea(c)
                if (area < minArea) break
                val curve = MatOfPoint2f(*c.toArray())
                val peri = Imgproc.arcLength(curve, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(curve, approx, POLY_EPSILON_FRACTION * peri, true)
                if (approx.total() == 4L &&
                    Imgproc.isContourConvex(MatOfPoint(*approx.toArray()))) {
                    quad = approx
                    curve.release()
                    break
                }
                curve.release()
                approx.release()
            }
            contours.forEach { it.release() }
            if (quad == null) {
                Log.d(TAG, "DocumentDewarper: no quadrilateral found")
                return null
            }

            val orderedScaled = orderCorners(quad.toArray()).map { p ->
                Point(p.x * scale, p.y * scale)
            }
            quad.release()

            // ID-1 long edge fixed to OUTPUT_WIDTH; height derived from aspect
            val outW = OUTPUT_WIDTH.toDouble()
            val outH = (OUTPUT_WIDTH / ID1_ASPECT).toDouble()

            val srcPts = MatOfPoint2f(
                orderedScaled[0], orderedScaled[1],
                orderedScaled[2], orderedScaled[3]
            )
            val dstPts = MatOfPoint2f(
                Point(0.0, 0.0),
                Point(outW - 1, 0.0),
                Point(outW - 1, outH - 1),
                Point(0.0, outH - 1)
            )
            val transform = Imgproc.getPerspectiveTransform(srcPts, dstPts)
            val warped = Mat()
            Imgproc.warpPerspective(
                src, warped, transform,
                Size(outW, outH),
                Imgproc.INTER_CUBIC
            )
            srcPts.release()
            dstPts.release()
            transform.release()

            val out = Bitmap.createBitmap(warped.cols(), warped.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(warped, out)
            warped.release()
            return out
        } catch (t: Throwable) {
            Log.w(TAG, "DocumentDewarper: dewarp failed", t)
            return null
        } finally {
            src.release()
            gray.release()
            edges.release()
            resized.release()
            hierarchy.release()
        }
    }

    // TL/TR/BR/BL via x+y and y-x extremes — robust to point ordering from approxPolyDP
    private fun orderCorners(pts: Array<Point>): Array<Point> {
        var tl = pts[0]; var br = pts[0]; var tr = pts[0]; var bl = pts[0]
        var minSum = Double.MAX_VALUE; var maxSum = -Double.MAX_VALUE
        var minDiff = Double.MAX_VALUE; var maxDiff = -Double.MAX_VALUE
        for (p in pts) {
            val s = p.x + p.y
            val d = p.y - p.x
            if (s < minSum) { minSum = s; tl = p }
            if (s > maxSum) { maxSum = s; br = p }
            if (d < minDiff) { minDiff = d; tr = p }
            if (d > maxDiff) { maxDiff = d; bl = p }
        }
        return arrayOf(tl, tr, br, bl)
    }
}
