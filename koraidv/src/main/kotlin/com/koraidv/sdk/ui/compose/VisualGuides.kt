package com.koraidv.sdk.ui.compose

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.koraidv.sdk.api.ChallengeType

/**
 * REQ-003 · Visual onboarding guides, implemented as native Compose Canvas
 * drawings so animations run on the platform renderer (no WebView / no
 * SMIL). Each guide is a 1:1 port of the reviewed SVG concept at
 * koraidv/docs/visual-guides/.
 *
 * All guides draw on a 320×240 logical canvas. The wrapper [VisualGuide]
 * handles aspect-ratio sizing so callers can drop it into any width.
 *
 * Gated by [com.koraidv.sdk.Configuration.showVisualGuides]. When the
 * flag is off, callers should fall back to the existing minimal-icon UI.
 */

/** The guide shown at a given step of the flow. */
enum class VisualGuideKind {
    DOC_FRONT,
    DOC_BACK,
    SELFIE,
    NFC_SCAN,
    LIVENESS_TURN_RIGHT,
    LIVENESS_TURN_LEFT,
    LIVENESS_LOOK_UP,
    LIVENESS_LOOK_DOWN,
    LIVENESS_SMILE,
    LIVENESS_BLINK,
}

/** Map the SDK's [ChallengeType] to the matching liveness guide. */
fun challengeToGuide(challenge: ChallengeType): VisualGuideKind = when (challenge) {
    ChallengeType.TURN_LEFT -> VisualGuideKind.LIVENESS_TURN_LEFT
    ChallengeType.TURN_RIGHT -> VisualGuideKind.LIVENESS_TURN_RIGHT
    ChallengeType.NOD_UP -> VisualGuideKind.LIVENESS_LOOK_UP
    ChallengeType.NOD_DOWN -> VisualGuideKind.LIVENESS_LOOK_DOWN
    ChallengeType.SMILE -> VisualGuideKind.LIVENESS_SMILE
    ChallengeType.BLINK -> VisualGuideKind.LIVENESS_BLINK
}

/**
 * Entry point. Renders the concept art appropriate to [kind] at a 4:3
 * aspect ratio. Caller controls the width.
 */
@Composable
fun VisualGuide(kind: VisualGuideKind, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().aspectRatio(320f / 240f)) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(320f / 240f)) {
            val sx = size.width / LOGICAL_WIDTH
            val sy = size.height / LOGICAL_HEIGHT
            scale(sx, sy, pivot = Offset.Zero) {
                when (kind) {
                    VisualGuideKind.DOC_FRONT -> drawDocFront()
                    VisualGuideKind.DOC_BACK -> drawDocBack()
                    VisualGuideKind.SELFIE -> drawSelfie()
                    VisualGuideKind.NFC_SCAN -> drawNfcScan(nfcWavePhase = 0f)
                    else -> Unit
                }
            }
        }
        // Animated variants need a remembered infinite transition — the
        // Canvas-plus-transition pattern is noisier than a single draw,
        // so they each compose their own overlay.
        when (kind) {
            VisualGuideKind.NFC_SCAN -> AnimatedOverlay { phase -> drawNfcScan(phase) }
            VisualGuideKind.LIVENESS_TURN_RIGHT -> AnimatedOverlay { phase -> drawHeadTurn(phase, right = true) }
            VisualGuideKind.LIVENESS_TURN_LEFT -> AnimatedOverlay { phase -> drawHeadTurn(phase, right = false) }
            VisualGuideKind.LIVENESS_LOOK_UP -> AnimatedOverlay { phase -> drawLookVertical(phase, up = true) }
            VisualGuideKind.LIVENESS_LOOK_DOWN -> AnimatedOverlay { phase -> drawLookVertical(phase, up = false) }
            VisualGuideKind.LIVENESS_SMILE -> AnimatedOverlay { phase -> drawSmile(phase) }
            VisualGuideKind.LIVENESS_BLINK -> AnimatedOverlay(durationMs = 1600) { phase -> drawBlink(phase) }
            else -> Unit
        }
    }
}

// ─── Logical canvas + palette (shared with SVG concepts) ───────────────────

private const val LOGICAL_WIDTH = 320f
private const val LOGICAL_HEIGHT = 240f

// Brand colours — matches the concept SVGs exactly.
private val BrandNavy = Color(0xFF1A237E)
private val BrandNavySoft = Color(0x99_1A237E.toInt())
private val BrandNavyGhost = Color(0x4D_1A237E.toInt())
private val CardBgA = Color(0xFFE3F2FD)
private val CardBgB = Color(0xFFBBDEFB)
private val CardBackA = Color(0xFFF5F5F5)
private val CardBackB = Color(0xFFCFD8DC)
private val CardBackInk = Color(0xFF263238)
private val GoldAmber = Color(0xFFFFB300)
private val SkinHi = Color(0xFFF4C7A1)
private val SkinLo = Color(0xFFC9A07F)
private val Hair = Color(0xFF3E2723)
private val EyeInk = Color(0xFF263238)
private val Lip = Color(0xFF8D5B3F)
private val Neck = Color(0xFFCFD8DC)
private val PassportA = Color(0xFF1B5E20)
private val PassportB = Color(0xFF2E7D32)
private val PhoneA = Color(0xFF455A64)
private val PhoneB = Color(0xFF263238)
private val ScreenBg = Color(0xFF0D47A1)
private val NfcCyan = Color(0xFF00E5FF)
private val NoGlassesRed = Color(0xFFC62828)

// ─── Helper: animated overlay with a normalised 0..1 phase ─────────────────

/**
 * Renders [draw] on top of the base layer with an infinitely-looping
 * phase in 0..1. Keeps the animated-only guides from paying for a
 * transition when the static ones are selected.
 */
@Composable
private fun AnimatedOverlay(
    durationMs: Int = 2400,
    draw: DrawScope.(phase: Float) -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "visual_guide")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = EaseInOutSine),
            repeatMode = RepeatMode.Restart,
        ),
        label = "visual_guide_phase",
    )
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(320f / 240f)) {
        val sx = size.width / LOGICAL_WIDTH
        val sy = size.height / LOGICAL_HEIGHT
        scale(sx, sy, pivot = Offset.Zero) {
            draw(phase)
        }
    }
}

// ─── Shared pose elements ──────────────────────────────────────────────────

/**
 * Draws a head with eyes/eyebrows/nose/mouth at the current origin
 * (expected to be the face centre). [eyeDx] shifts pupils horizontally,
 * [eyeDy] vertically; [mouthCurve] bends the mouth stroke 0..1.
 * [eyeLidClose] collapses the eye vertically 0..1 (used by blink).
 * [cheekGlow] fades the blush circles 0..1 (used by smile).
 */
private fun DrawScope.drawHeadBase(
    eyeDx: Float = 0f,
    eyeDy: Float = 0f,
    mouthCurve: Float = 0f,
    eyeLidClose: Float = 0f,
    cheekGlow: Float = 0f,
    showTeeth: Float = 0f,
) {
    // Shoulder/neck hint
    val shoulderPath = Path().apply {
        moveTo(-40f, 60f)
        quadraticBezierTo(-40f, 78f, -68f, 90f)
        lineTo(68f, 90f)
        quadraticBezierTo(40f, 78f, 40f, 60f)
        close()
    }
    drawPath(shoulderPath, color = Neck.copy(alpha = 0.5f))

    // Head oval — radial gradient for subtle 3D
    val headBrush = Brush.radialGradient(
        colors = listOf(SkinHi, SkinLo),
        center = Offset(0f, -6f - 18f),
        radius = 58f,
    )
    drawOval(
        brush = headBrush,
        topLeft = Offset(-42f, -6f - 54f),
        size = Size(84f, 108f),
    )
    // Hair cap
    val hairPath = Path().apply {
        moveTo(-40f, -30f)
        quadraticBezierTo(-40f, -70f, 0f, -70f)
        quadraticBezierTo(40f, -70f, 40f, -30f)
        quadraticBezierTo(30f, -48f, 0f, -48f)
        quadraticBezierTo(-30f, -48f, -40f, -30f)
        close()
    }
    drawPath(hairPath, color = Hair.copy(alpha = 0.8f))

    // Eyebrows
    drawLine(Hair, Offset(-20f, -14f), Offset(-8f, -15f), strokeWidth = 2f)
    drawLine(Hair, Offset(8f, -15f), Offset(20f, -14f), strokeWidth = 2f)

    // Eyes — draw as vertically-scaled ovals (ry shrinks with eyeLidClose)
    val eyeRy = 2.5f * (1f - eyeLidClose)
    if (eyeRy > 0.25f) {
        drawOval(
            color = EyeInk,
            topLeft = Offset(-14f + eyeDx - 3.5f, -4f + eyeDy - eyeRy),
            size = Size(7f, eyeRy * 2f),
        )
        drawOval(
            color = EyeInk,
            topLeft = Offset(14f + eyeDx - 3.5f, -4f + eyeDy - eyeRy),
            size = Size(7f, eyeRy * 2f),
        )
    }
    if (eyeLidClose > 0.7f) {
        // Closed-eyelid lines appear at the tail of a blink
        val alpha = ((eyeLidClose - 0.7f) / 0.3f).coerceIn(0f, 1f)
        drawLine(EyeInk.copy(alpha = alpha), Offset(-18f, -4f), Offset(-10f, -4f), strokeWidth = 1.5f)
        drawLine(EyeInk.copy(alpha = alpha), Offset(10f, -4f), Offset(18f, -4f), strokeWidth = 1.5f)
    }

    // Nose
    val nosePath = Path().apply {
        moveTo(0f, -3f)
        quadraticBezierTo(-3f, 7f, 0f, 11f)
    }
    drawPath(nosePath, color = Lip.copy(alpha = 0.55f), style = Stroke(width = 1.5f))

    // Cheeks (smile glow)
    if (cheekGlow > 0.02f) {
        drawCircle(
            color = Color(0xFFE8A87C).copy(alpha = cheekGlow * 0.45f),
            radius = 5f,
            center = Offset(-28f, 10f),
        )
        drawCircle(
            color = Color(0xFFE8A87C).copy(alpha = cheekGlow * 0.45f),
            radius = 5f,
            center = Offset(28f, 10f),
        )
    }

    // Mouth — base curve opens into a smile as mouthCurve grows
    val half = 10f + 8f * mouthCurve
    val curveY = 20f - 2f * mouthCurve
    val dipY = 5f + 9f * mouthCurve
    val mouthPath = Path().apply {
        moveTo(-half, curveY)
        quadraticBezierTo(0f, curveY + dipY, half, curveY)
    }
    drawPath(mouthPath, color = Lip, style = Stroke(width = 2f))

    // Teeth hint at peak smile
    if (showTeeth > 0.05f) {
        drawRoundRect(
            color = Color.White.copy(alpha = showTeeth * 0.65f),
            topLeft = Offset(-10f, 18f),
            size = Size(20f, 4f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f),
        )
    }
}

/** Dashed oval + tick marks that frame the face in selfie + liveness. */
private fun DrawScope.drawFaceFrame() {
    drawOval(
        color = BrandNavy.copy(alpha = 0.5f),
        topLeft = Offset(160f - 72f, 115f - 88f),
        size = Size(144f, 176f),
        style = Stroke(
            width = 2.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
        ),
    )
}

// ─── 1 · Document Front ────────────────────────────────────────────────────

private fun DrawScope.drawDocFront() {
    // Dashed capture frame
    drawRoundRect(
        color = BrandNavy.copy(alpha = 0.6f),
        topLeft = Offset(40f, 60f),
        size = Size(240f, 150f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f),
        style = Stroke(
            width = 3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
        ),
    )
    // Corner brackets
    listOf(
        listOf(Offset(50f, 75f), Offset(50f, 60f), Offset(65f, 60f)),
        listOf(Offset(270f, 60f), Offset(285f, 60f), Offset(285f, 75f)),
        listOf(Offset(50f, 195f), Offset(50f, 210f), Offset(65f, 210f)),
        listOf(Offset(285f, 195f), Offset(285f, 210f), Offset(270f, 210f)),
    ).forEach { pts ->
        for (i in 0 until pts.size - 1) {
            drawLine(BrandNavy, pts[i], pts[i + 1], strokeWidth = 4f)
        }
    }
    // Card (slight tilt)
    translate(left = 70f, top = 85f) {
        rotate(degrees = -2f, pivot = Offset(100f, 60f)) {
            val cardBrush = Brush.linearGradient(listOf(CardBgA, CardBgB))
            drawRoundRect(
                brush = cardBrush,
                size = Size(200f, 120f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f),
            )
            drawRoundRect(
                color = BrandNavy,
                size = Size(200f, 120f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f),
                style = Stroke(1.5f),
            )
            // Photo box
            drawRoundRect(
                color = Color(0xFF90CAF9),
                topLeft = Offset(12f, 14f),
                size = Size(50f, 60f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
            )
            // Face silhouette in photo
            drawCircle(BrandNavy.copy(alpha = 0.4f), radius = 10f, center = Offset(37f, 36f))
            val bustPath = Path().apply {
                moveTo(22f, 70f)
                quadraticBezierTo(37f, 52f, 52f, 70f)
                lineTo(52f, 74f)
                lineTo(22f, 74f)
                close()
            }
            drawPath(bustPath, color = BrandNavy.copy(alpha = 0.4f))
            // Text lines
            drawRoundRect(BrandNavy.copy(alpha = 0.5f), Offset(74f, 20f), Size(110f, 6f), androidx.compose.ui.geometry.CornerRadius(3f))
            drawRoundRect(BrandNavy.copy(alpha = 0.35f), Offset(74f, 32f), Size(90f, 5f), androidx.compose.ui.geometry.CornerRadius(2.5f))
            drawRoundRect(BrandNavy.copy(alpha = 0.35f), Offset(74f, 42f), Size(100f, 5f), androidx.compose.ui.geometry.CornerRadius(2.5f))
            drawRoundRect(BrandNavy.copy(alpha = 0.35f), Offset(74f, 56f), Size(80f, 5f), androidx.compose.ui.geometry.CornerRadius(2.5f))
            // Chip
            drawRoundRect(GoldAmber.copy(alpha = 0.85f), Offset(14f, 88f), Size(20f, 16f), androidx.compose.ui.geometry.CornerRadius(2f))
            // MRZ
            drawRect(BrandNavy.copy(alpha = 0.35f), Offset(12f, 108f), Size(176f, 3f))
        }
    }
    // Sun
    drawSun(center = Offset(25f, 25f))
}

// ─── 2 · Document Back ─────────────────────────────────────────────────────

private fun DrawScope.drawDocBack() {
    // Flip arc
    val arcCenter = Offset(160f, 120f)
    drawArc(
        color = BrandNavy.copy(alpha = 0.35f),
        startAngle = 160f,
        sweepAngle = 220f,
        useCenter = false,
        topLeft = Offset(arcCenter.x - 95f, arcCenter.y - 55f),
        size = Size(190f, 110f),
        style = Stroke(
            width = 4f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        ),
    )

    // Capture frame
    drawRoundRect(
        color = BrandNavy.copy(alpha = 0.6f),
        topLeft = Offset(40f, 60f),
        size = Size(240f, 150f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f),
        style = Stroke(
            width = 3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
        ),
    )

    // Card back (light tilt +2°)
    translate(left = 70f, top = 85f) {
        rotate(degrees = 2f, pivot = Offset(100f, 60f)) {
            val bg = Brush.linearGradient(listOf(CardBackA, CardBackB))
            drawRoundRect(
                brush = bg,
                size = Size(200f, 120f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f),
            )
            drawRoundRect(
                color = Color(0xFF455A64),
                size = Size(200f, 120f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f),
                style = Stroke(1.5f),
            )
            // Magnetic stripe
            drawRect(CardBackInk, Offset(10f, 14f), Size(180f, 24f))
            // Barcode lines
            val barWidths = listOf(2f, 1f, 3f, 1f, 2f, 2f, 1f, 3f, 1f, 2f, 2f, 1f, 3f, 1f, 2f, 2f, 1f, 3f, 1f, 2f, 1f, 3f, 1f, 2f, 2f)
            var x = 12f
            for (w in barWidths) {
                drawRect(CardBackInk, Offset(x, 52f), Size(w, 36f))
                x += w + 3f
            }
            // MRZ lines
            drawRect(Color(0xFF455A64).copy(alpha = 0.6f), Offset(10f, 96f), Size(180f, 4f))
            drawRect(Color(0xFF455A64).copy(alpha = 0.6f), Offset(10f, 104f), Size(170f, 4f))
        }
    }
}

// ─── 3 · Selfie ────────────────────────────────────────────────────────────

private fun DrawScope.drawSelfie() {
    drawFaceFrame()
    translate(left = 160f, top = 115f) {
        drawHeadBase()
    }
    drawSun(center = Offset(28f, 30f))
    drawNoGlassesGlyph(center = Offset(292f, 30f))
}

// ─── 4 · NFC Scan ──────────────────────────────────────────────────────────

private fun DrawScope.drawNfcScan(nfcWavePhase: Float) {
    // Passport booklet
    translate(left = 50f, top = 110f) {
        val bg = Brush.linearGradient(
            colors = listOf(PassportA, PassportB),
            start = Offset(0f, 0f),
            end = Offset(0f, 100f),
        )
        drawRoundRect(
            brush = bg,
            size = Size(140f, 100f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f),
        )
        drawRoundRect(
            color = GoldAmber.copy(alpha = 0.8f),
            topLeft = Offset(4f, 4f),
            size = Size(132f, 92f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
            style = Stroke(1.5f),
        )
        // Crest
        drawCircle(
            color = GoldAmber,
            radius = 16f,
            center = Offset(70f, 40f),
            style = Stroke(1.5f),
        )
        val crest = Path().apply {
            moveTo(62f, 35f)
            lineTo(70f, 27f)
            lineTo(78f, 35f)
            lineTo(70f, 52f)
            close()
        }
        drawPath(crest, color = GoldAmber)
    }

    // Phone
    translate(left = 140f, top = 38f) {
        drawRoundRect(
            brush = Brush.linearGradient(listOf(PhoneA, PhoneB)),
            size = Size(76f, 140f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f),
        )
        drawRoundRect(
            color = ScreenBg,
            topLeft = Offset(5f, 8f),
            size = Size(66f, 124f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f),
        )
        drawCircle(
            color = Color(0xFF90CAF9),
            radius = 2f,
            center = Offset(38f, 6f),
        )
    }

    // NFC waves (three nested arcs, staggered phase)
    val waveOrigin = Offset(178f, 38f)
    for (i in 0 until 3) {
        val staggered = ((nfcWavePhase + i * 0.22f) % 1f)
        val alpha = waveAlpha(staggered)
        val r = 14f + i * 10f
        drawArc(
            color = NfcCyan.copy(alpha = alpha),
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(waveOrigin.x - r, waveOrigin.y - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(2.5f),
        )
    }

    // Directional nudge arrow
    drawLine(BrandNavy.copy(alpha = 0.75f), Offset(220f, 115f), Offset(244f, 115f), strokeWidth = 3f)
    val arrow = Path().apply {
        moveTo(248f, 115f)
        lineTo(240f, 110f)
        lineTo(240f, 120f)
        close()
    }
    drawPath(arrow, color = BrandNavy.copy(alpha = 0.75f))
}

private fun waveAlpha(t: Float): Float {
    // Fade in-out bell curve so the ping feels like a real ping
    return (kotlin.math.sin(t * kotlin.math.PI).toFloat()).coerceAtLeast(0f)
}

// ─── 5/6 · Head turn right / left ──────────────────────────────────────────

private fun DrawScope.drawHeadTurn(phase: Float, right: Boolean) {
    drawFaceFrame()
    val s = triangle(phase) // 0..1..0
    translate(left = 160f, top = 115f) {
        // Narrow the face horizontally to simulate Y-axis rotation
        val scaleX = 1f - 0.45f * s
        // Pupils drift in the direction of travel
        val eyeDx = 6f * s * if (right) 1f else -1f
        scale(scaleX, 1f, pivot = Offset.Zero) {
            drawHeadBase(eyeDx = eyeDx)
        }
    }
    drawHorizontalArrow(right = right)
}

// ─── 7/8 · Look up / look down ─────────────────────────────────────────────

private fun DrawScope.drawLookVertical(phase: Float, up: Boolean) {
    drawFaceFrame()
    val s = triangle(phase)
    val dy = 10f * s * if (up) -1f else 1f // translate IN the arrow direction
    val scaleY = 1f - 0.12f * s             // gentle foreshortening
    val eyeDy = 4f * s * if (up) -1f else 1f
    translate(left = 160f, top = 115f + dy) {
        scale(1f, scaleY, pivot = Offset.Zero) {
            drawHeadBase(eyeDy = eyeDy)
        }
    }
    drawVerticalArrow(up = up)
}

// ─── 9 · Smile ─────────────────────────────────────────────────────────────

private fun DrawScope.drawSmile(phase: Float) {
    drawFaceFrame()
    val s = triangle(phase)
    translate(left = 160f, top = 115f) {
        drawHeadBase(mouthCurve = s, cheekGlow = s, showTeeth = s)
    }
    // Smile reaction chip (top-right) — grows subtly at peak
    translate(left = 275f, top = 40f) {
        drawCircle(
            color = BrandNavy,
            radius = 16f,
            center = Offset.Zero,
            style = Stroke(2.5f),
        )
        drawCircle(color = BrandNavy, radius = 1.5f, center = Offset(-5f, -3f))
        drawCircle(color = BrandNavy, radius = 1.5f, center = Offset(5f, -3f))
        val sp = Path().apply {
            moveTo(-6f, 4f)
            quadraticBezierTo(0f, 10f, 6f, 4f)
        }
        drawPath(sp, color = BrandNavy, style = Stroke(2f))
    }
}

// ─── 10 · Blink ────────────────────────────────────────────────────────────

private fun DrawScope.drawBlink(phase: Float) {
    drawFaceFrame()
    val s = triangle(phase)
    translate(left = 160f, top = 115f) {
        drawHeadBase(eyeLidClose = s)
    }
}

// ─── Reusable glyphs ───────────────────────────────────────────────────────

private fun DrawScope.drawSun(center: Offset) {
    drawCircle(GoldAmber, radius = 7f, center = center)
    val rays = listOf(
        Offset(0f, -13f) to Offset(0f, -10f),
        Offset(0f, 10f) to Offset(0f, 13f),
        Offset(-13f, 0f) to Offset(-10f, 0f),
        Offset(10f, 0f) to Offset(13f, 0f),
        Offset(-9f, -9f) to Offset(-7f, -7f),
        Offset(7f, 7f) to Offset(9f, 9f),
        Offset(9f, -9f) to Offset(7f, -7f),
        Offset(-7f, 7f) to Offset(-9f, 9f),
    )
    for ((a, b) in rays) {
        drawLine(GoldAmber, center + a, center + b, strokeWidth = 2f)
    }
}

private fun DrawScope.drawNoGlassesGlyph(center: Offset) {
    val grey = Color(0xFF546E7A)
    drawCircle(grey, radius = 6f, center = Offset(center.x - 8f, center.y), style = Stroke(2f))
    drawCircle(grey, radius = 6f, center = Offset(center.x + 8f, center.y), style = Stroke(2f))
    drawLine(grey, Offset(center.x - 2f, center.y), Offset(center.x + 2f, center.y), strokeWidth = 2f)
    drawLine(NoGlassesRed, Offset(center.x - 16f, center.y - 10f), Offset(center.x + 16f, center.y + 10f), strokeWidth = 2.5f)
}

private fun DrawScope.drawHorizontalArrow(right: Boolean) {
    val y = 115f
    val tipX = if (right) 281f else 39f
    val tailX = if (right) 251f else 69f
    drawLine(BrandNavy, Offset(tailX, y), Offset(tipX - if (right) 6f else -6f, y), strokeWidth = 4f)
    val head = Path().apply {
        moveTo(tipX, y)
        lineTo(if (right) tipX - 10f else tipX + 10f, y - 7f)
        lineTo(if (right) tipX - 10f else tipX + 10f, y + 7f)
        close()
    }
    drawPath(head, color = BrandNavy)
}

private fun DrawScope.drawVerticalArrow(up: Boolean) {
    val x = 160f
    val tipY = if (up) 14f else 226f
    val tailY = if (up) 44f else 196f
    drawLine(BrandNavy, Offset(x, tailY), Offset(x, tipY + if (up) 6f else -6f), strokeWidth = 4f)
    val head = Path().apply {
        moveTo(x, tipY)
        lineTo(x - 7f, if (up) tipY + 10f else tipY - 10f)
        lineTo(x + 7f, if (up) tipY + 10f else tipY - 10f)
        close()
    }
    drawPath(head, color = BrandNavy)
}

/** 0 → 1 → 0 triangular ease — matches the SVG SMIL keyTimes 0, 0.5, 1 shape. */
private fun triangle(phase: Float): Float {
    val t = (phase * 2f).coerceIn(0f, 2f)
    return if (t <= 1f) t else 2f - t
}
