package com.koraidv.sdk.ui.compose

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.koraidv.sdk.api.DocumentSide
import com.koraidv.sdk.api.SessionManager
import com.koraidv.sdk.capture.CameraManager
import com.koraidv.sdk.capture.CameraPosition
import com.koraidv.sdk.capture.DocumentScanner
import com.koraidv.sdk.capture.FaceDetectionInfo
import com.koraidv.sdk.capture.FaceScanner
import com.koraidv.sdk.capture.QualityValidator
import com.koraidv.sdk.liveness.*
import java.io.ByteArrayOutputStream

/**
 * Document capture screen — dark theme with viewfinder, scan line, corner brackets
 */
@Composable
fun DocumentCaptureScreen(
    documentTypeCode: String,
    documentDisplayName: String,
    requiresBack: Boolean,
    side: DocumentSide,
    onCaptured: (ByteArray) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraManager = remember { CameraManager(context) }
    val documentScanner = remember { DocumentScanner() }
    val qualityValidator = remember { QualityValidator() }

    var cameraReady by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var documentDetected by remember { mutableStateOf(false) }
    var documentReady by remember { mutableStateOf(false) }
    var qualityGuidance by remember { mutableStateOf<String?>(null) }
    var capturedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var autoCapturePending by remember { mutableStateOf(false) }

    LaunchedEffect(autoCapturePending) {
        if (autoCapturePending && !isCapturing && cameraReady) {
            isCapturing = true
            cameraManager.capturePhotoOnMain { bytes ->
                if (bytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        val validation = qualityValidator.validateDocumentImage(bitmap)
                        if (validation.isValid) {
                            val stream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                            capturedImageBytes = stream.toByteArray()
                            capturedBitmap = bitmap
                        } else {
                            documentScanner.resetStability()
                        }
                    }
                }
                isCapturing = false
                autoCapturePending = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.release()
            documentScanner.resetStability()
        }
    }

    // ── Review mode ──────────────────────────────────────────────────────────
    val reviewBytes = capturedImageBytes
    val reviewBitmap = capturedBitmap
    if (reviewBytes != null && reviewBitmap != null) {
        DocumentReviewScreen(
            bitmap = reviewBitmap,
            title = "Review photo",
            subtitle = "${if (side == DocumentSide.FRONT) "Front" else "Back"} of $documentDisplayName",
            onRetake = {
                capturedImageBytes = null
                capturedBitmap = null
                documentScanner.resetStability()
            },
            onAccept = { onCaptured(reviewBytes) },
            onCancel = onCancel
        )
        return
    }

    // ── Camera capture mode ──────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KoraColors.DarkBg)
    ) {
        // Progress bar (step 3/5, dark)
        StepProgressBar(total = 5, current = 3, isDark = true)

        // Dark header
        DarkScreenHeader(
            title = if (side == DocumentSide.FRONT) "Front of ID" else "Back of ID",
            subtitle = documentDisplayName,
            onClose = onCancel
        )

        // Viewfinder area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 342.dp)
                    .aspectRatio(1.586f)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                // Camera preview
                AndroidView(
                    factory = { ctx ->
                        val mainHandler = Handler(Looper.getMainLooper())
                        PreviewView(ctx).also { previewView ->
                            cameraManager.initialize(
                                lifecycleOwner = lifecycleOwner,
                                previewView = previewView,
                                position = CameraPosition.BACK,
                                onInitialized = { success ->
                                    cameraReady = success
                                    if (success) {
                                        cameraManager.setFrameAnalysisListener { imageProxy ->
                                            @Suppress("OPT_IN_USAGE")
                                            val detection = documentScanner.detectDocument(imageProxy)
                                            mainHandler.post {
                                                documentDetected = detection != null
                                                qualityGuidance = detection?.qualityGuidance
                                                val ready = detection != null &&
                                                        detection.isStable &&
                                                        detection.qualityGuidance == null
                                                documentReady = ready
                                                if (ready && !isCapturing && capturedImageBytes == null) {
                                                    autoCapturePending = true
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Scan line animation
                val infiniteTransition = rememberInfiniteTransition(label = "scan")
                val scanPosition by infiniteTransition.animateFloat(
                    initialValue = 0.15f,
                    targetValue = 0.80f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scan_line"
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Scan line
                    val lineY = h * scanPosition
                    drawLine(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(Color.Transparent, KoraColors.Teal, Color.Transparent),
                            startX = w * 0.1f,
                            endX = w * 0.9f
                        ),
                        start = Offset(w * 0.1f, lineY),
                        end = Offset(w * 0.9f, lineY),
                        strokeWidth = 2.dp.toPx()
                    )

                    // Corner brackets (28dp, 3dp solid teal)
                    val cornerSize = 28.dp.toPx()
                    val strokeW = 3.dp.toPx()
                    val teal = KoraColors.Teal
                    val cornerR = 8.dp.toPx()

                    // Top-left
                    drawLine(teal, Offset(cornerR, 0f), Offset(cornerSize, 0f), strokeW)
                    drawLine(teal, Offset(0f, cornerR), Offset(0f, cornerSize), strokeW)
                    drawArc(teal, 180f, 90f, false, Offset.Zero, Size(cornerR * 2, cornerR * 2), style = Stroke(strokeW))

                    // Top-right
                    drawLine(teal, Offset(w - cornerSize, 0f), Offset(w - cornerR, 0f), strokeW)
                    drawLine(teal, Offset(w, cornerR), Offset(w, cornerSize), strokeW)
                    drawArc(teal, 270f, 90f, false, Offset(w - cornerR * 2, 0f), Size(cornerR * 2, cornerR * 2), style = Stroke(strokeW))

                    // Bottom-left
                    drawLine(teal, Offset(cornerR, h), Offset(cornerSize, h), strokeW)
                    drawLine(teal, Offset(0f, h - cornerSize), Offset(0f, h - cornerR), strokeW)
                    drawArc(teal, 90f, 90f, false, Offset(0f, h - cornerR * 2), Size(cornerR * 2, cornerR * 2), style = Stroke(strokeW))

                    // Bottom-right
                    drawLine(teal, Offset(w - cornerSize, h), Offset(w - cornerR, h), strokeW)
                    drawLine(teal, Offset(w, h - cornerSize), Offset(w, h - cornerR), strokeW)
                    drawArc(teal, 0f, 90f, false, Offset(w - cornerR * 2, h - cornerR * 2), Size(cornerR * 2, cornerR * 2), style = Stroke(strokeW))
                }
            }
        }

        // Guidance area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val pillVariant = if (documentReady) GuidancePillVariant.Ready else GuidancePillVariant.Scanning
            val pillText = when {
                documentReady -> "Ready to capture"
                qualityGuidance != null -> qualityGuidance!!
                else -> "Scanning document..."
            }
            GuidancePill(text = pillText, variant = pillVariant)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (side == DocumentSide.FRONT)
                    "Hold your ID flat in good lighting.\nAuto-capture when ready."
                else
                    "Ensure the barcode is clearly visible.\nAuto-capture when ready.",
                fontSize = 13.sp,
                color = KoraColors.WhiteAlpha40,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step pills (Front/Back)
            if (requiresBack) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (side == DocumentSide.FRONT) {
                        StepPill("Front", StepPillState.Active)
                        StepPill("Back", StepPillState.Inactive)
                    } else {
                        StepPill("Front", StepPillState.Done)
                        StepPill("Back", StepPillState.Active)
                    }
                }
            }
        }
    }
}

/**
 * Document review screen — dark bg, review card with badge and quality checks
 */
@Composable
private fun DocumentReviewScreen(
    bitmap: Bitmap,
    title: String,
    subtitle: String,
    onRetake: () -> Unit,
    onAccept: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KoraColors.DarkBg)
    ) {
        StepProgressBar(total = 5, current = 3, isDark = true)

        DarkScreenHeader(
            title = title,
            subtitle = subtitle,
            onClose = onCancel
        )

        // Review image area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 342.dp)
                        .aspectRatio(1.586f)
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Captured document",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Good quality badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        ReviewBadge(text = "Good quality")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Quality checks
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ReviewQualityCheck("Sharp")
                    ReviewQualityCheck("Well-lit")
                    ReviewQualityCheck("No glare")
                }
            }
        }

        // Bottom buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            Text(
                text = "Is the document clearly readable?",
                fontSize = 14.sp,
                color = KoraColors.WhiteAlpha50,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KoraButton(
                    text = "Retake",
                    onClick = onRetake,
                    variant = KoraButtonVariant.DarkOutline,
                    modifier = Modifier.weight(1f)
                )
                KoraButton(
                    text = "Looks good",
                    onClick = onAccept,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Selfie capture screen — dark bg, oval viewfinder with animated ring
 */
@Composable
fun SelfieCaptureScreen(
    onCaptured: (ByteArray) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraManager = remember { CameraManager(context) }
    val faceScanner = remember { FaceScanner() }
    val qualityValidator = remember { QualityValidator() }

    var cameraReady by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var faceDetected by remember { mutableStateOf(false) }
    var faceReady by remember { mutableStateOf(false) }
    var guidanceMessage by remember { mutableStateOf<String?>("Position your face in the oval") }
    var capturedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(faceReady) {
        if (faceReady && !isCapturing && cameraReady && capturedImageBytes == null) {
            isCapturing = true
            cameraManager.capturePhotoOnMain { bytes ->
                if (bytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        val matrix = Matrix()
                        matrix.postScale(-1f, 1f)
                        val mirrored = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

                        val faceInfo = FaceDetectionInfo(
                            boundingBox = android.graphics.RectF(0f, 0f, mirrored.width.toFloat(), mirrored.height.toFloat()),
                            confidence = 0.95f
                        )
                        val validation = qualityValidator.validateSelfieImage(mirrored, faceInfo)
                        if (validation.isValid) {
                            val stream = ByteArrayOutputStream()
                            mirrored.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                            capturedImageBytes = stream.toByteArray()
                            capturedBitmap = mirrored
                        } else {
                            faceScanner.resetStability()
                            faceReady = false
                        }
                    }
                }
                isCapturing = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.release()
            faceScanner.close()
        }
    }

    // ── Review mode ──────────────────────────────────────────────────────────
    val reviewBytes = capturedImageBytes
    val reviewBitmap = capturedBitmap
    if (reviewBytes != null && reviewBitmap != null) {
        SelfieReviewScreen(
            bitmap = reviewBitmap,
            onRetake = {
                capturedImageBytes = null
                capturedBitmap = null
                faceReady = false
                faceScanner.resetStability()
            },
            onAccept = { onCaptured(reviewBytes) },
            onCancel = onCancel
        )
        return
    }

    // ── Camera capture mode ──────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KoraColors.DarkBg)
    ) {
        StepProgressBar(total = 5, current = 4, isDark = true)

        DarkScreenHeader(
            title = "Take a selfie",
            onClose = onCancel
        )

        // Bold title area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Face the camera",
                fontSize = 24.sp,
                fontWeight = FontWeight.W700,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Keep a neutral expression",
                fontSize = 14.sp,
                color = KoraColors.WhiteAlpha50
            )
        }

        // Oval viewfinder with animated ring
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 140.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Camera preview in oval
                Box(
                    modifier = Modifier
                        .width(240.dp)
                        .height(300.dp)
                        .clip(OvalViewfinderShape)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val mainHandler = Handler(Looper.getMainLooper())
                            PreviewView(ctx).also { previewView ->
                                cameraManager.initialize(
                                    lifecycleOwner = lifecycleOwner,
                                    previewView = previewView,
                                    position = CameraPosition.FRONT,
                                    onInitialized = { success ->
                                        cameraReady = success
                                        if (success) {
                                            cameraManager.setFrameAnalysisListener { imageProxy ->
                                                @Suppress("OPT_IN_USAGE")
                                                val result = faceScanner.detectFace(imageProxy)
                                                mainHandler.post {
                                                    if (result != null) {
                                                        faceDetected = result.faceDetected
                                                        guidanceMessage = result.guidanceMessage
                                                        val ready = result.faceDetected &&
                                                                result.isCentered &&
                                                                result.isSizedCorrectly &&
                                                                result.isStable
                                                        faceReady = ready
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Animated rotating ring
                val infiniteTransition = rememberInfiniteTransition(label = "selfie_ring")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(3000, easing = LinearEasing)
                    ),
                    label = "ring_rotation"
                )

                Canvas(
                    modifier = Modifier
                        .width(246.dp)
                        .height(306.dp)
                ) {
                    rotate(rotation) {
                        val strokeWidth = 3.dp.toPx()
                        drawOval(
                            color = KoraColors.Teal,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                            size = Size(size.width - strokeWidth, size.height - strokeWidth)
                        )
                    }
                }
            }
        }

        // Guidance
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GuidancePill(
                text = if (faceReady) "Ready to capture" else "Detecting face...",
                variant = if (faceReady) GuidancePillVariant.Ready else GuidancePillVariant.Scanning
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Position your face within the oval.\nKeep a neutral expression. Auto-capture when ready.",
                fontSize = 13.sp,
                color = KoraColors.WhiteAlpha40,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * Selfie review screen — dark bg, oval with captured image
 */
@Composable
private fun SelfieReviewScreen(
    bitmap: Bitmap,
    onRetake: () -> Unit,
    onAccept: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KoraColors.DarkBg)
    ) {
        StepProgressBar(total = 5, current = 4, isDark = true)

        DarkScreenHeader(title = "Review selfie", onClose = onCancel)

        // Bold title
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Does this look like you?",
                fontSize = 24.sp,
                fontWeight = FontWeight.W700,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Check clarity and lighting",
                fontSize = 14.sp,
                color = KoraColors.WhiteAlpha50
            )
        }

        // Oval with captured image
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 140.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(240.dp)
                    .height(300.dp)
                    .clip(OvalViewfinderShape)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Captured selfie",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    ReviewBadge(text = "Face detected")
                }
            }
        }

        // Bottom area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
            ) {
                ReviewQualityCheck("Clear")
                ReviewQualityCheck("Centered")
                ReviewQualityCheck("Well-lit")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KoraButton(
                    text = "Retake",
                    onClick = onRetake,
                    variant = KoraButtonVariant.DarkOutline,
                    modifier = Modifier.weight(1f)
                )
                KoraButton(
                    text = "Use this",
                    onClick = onAccept,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Liveness check screen — dark bg, oval with countdown and progress ring
 */
@Composable
internal fun LivenessScreen(
    sessionManager: SessionManager?,
    verificationId: String?,
    onComplete: (LivenessResult) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraManager = remember { CameraManager(context) }
    val livenessManager = remember { LivenessManager() }

    var cameraReady by remember { mutableStateOf(false) }
    var sessionLoaded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val livenessState by livenessManager.state.collectAsState()

    LaunchedEffect(sessionManager, verificationId) {
        if (sessionManager != null && verificationId != null) {
            val result = sessionManager.createLivenessSession(verificationId)
            result.fold(
                onSuccess = { session ->
                    livenessManager.start(session)
                    sessionLoaded = true
                },
                onFailure = { error ->
                    errorMessage = error.message ?: "Failed to start liveness session"
                }
            )
        } else {
            val fallbackSession = com.koraidv.sdk.api.LivenessSession(
                sessionId = "local-session",
                challenges = listOf(
                    com.koraidv.sdk.api.LivenessChallenge("1", com.koraidv.sdk.api.ChallengeType.BLINK, "Blink your eyes", 0),
                    com.koraidv.sdk.api.LivenessChallenge("2", com.koraidv.sdk.api.ChallengeType.SMILE, "Smile naturally", 1),
                    com.koraidv.sdk.api.LivenessChallenge("3", com.koraidv.sdk.api.ChallengeType.TURN_LEFT, "Turn your head to the left", 2)
                ),
                expiresAt = java.util.Date(System.currentTimeMillis() + 300_000)
            )
            livenessManager.start(fallbackSession)
            sessionLoaded = true
        }
    }

    LaunchedEffect(livenessState) {
        when (val state = livenessState) {
            is LivenessState.Complete -> {
                if (sessionManager != null && verificationId != null) {
                    for (challengeResult in state.result.challenges) {
                        challengeResult.imageData?.let { imageData ->
                            sessionManager.submitLivenessChallenge(
                                verificationId,
                                challengeResult.challenge,
                                imageData
                            )
                        }
                    }
                }
                onComplete(state.result)
            }
            else -> {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.release()
            livenessManager.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KoraColors.DarkBg)
    ) {
        StepProgressBar(total = 5, current = 5, isDark = true)

        DarkScreenHeader(title = "Liveness check", onClose = onCancel)

        if (errorMessage != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = KoraColors.ErrorRed
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorMessage!!,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
            return@Column
        }

        // Challenge title
        val challengeTitle = when (val state = livenessState) {
            is LivenessState.InProgress -> state.challenge.instruction
            is LivenessState.ChallengeComplete -> if (state.passed) "Great!" else "Try again"
            else -> "Get ready..."
        }
        val challengeSubtitle = when (val state = livenessState) {
            is LivenessState.InProgress -> when (state.challenge.type) {
                com.koraidv.sdk.api.ChallengeType.BLINK -> "Blink naturally when ready"
                com.koraidv.sdk.api.ChallengeType.SMILE -> "Show a natural smile"
                com.koraidv.sdk.api.ChallengeType.TURN_LEFT -> "Slowly turn left"
                com.koraidv.sdk.api.ChallengeType.TURN_RIGHT -> "Slowly turn right"
                else -> "Follow the instruction"
            }
            else -> ""
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = challengeTitle,
                fontSize = 24.sp,
                fontWeight = FontWeight.W700,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
            if (challengeSubtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = challengeSubtitle,
                    fontSize = 14.sp,
                    color = KoraColors.WhiteAlpha50
                )
            }
        }

        // Oval viewfinder with progress ring and countdown
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 140.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Camera preview
                Box(
                    modifier = Modifier
                        .width(240.dp)
                        .height(300.dp)
                        .clip(OvalViewfinderShape)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).also { previewView ->
                                cameraManager.initialize(
                                    lifecycleOwner = lifecycleOwner,
                                    previewView = previewView,
                                    position = CameraPosition.FRONT,
                                    onInitialized = { success ->
                                        cameraReady = success
                                        if (success) {
                                            cameraManager.setFrameAnalysisListener { imageProxy ->
                                                @Suppress("OPT_IN_USAGE")
                                                livenessManager.processFrame(imageProxy)
                                            }
                                        }
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Progress ring via Canvas
                val progress = when (val state = livenessState) {
                    is LivenessState.InProgress -> state.progress
                    is LivenessState.ChallengeComplete -> 1f
                    else -> 0f
                }

                Canvas(
                    modifier = Modifier
                        .width(252.dp)
                        .height(312.dp)
                ) {
                    val strokeWidth = 4.dp.toPx()
                    // Background track
                    drawOval(
                        color = KoraColors.WhiteAlpha15,
                        style = Stroke(width = strokeWidth),
                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth)
                    )
                    // Progress fill
                    if (progress > 0f) {
                        drawArc(
                            color = KoraColors.Teal,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                            size = Size(size.width - strokeWidth, size.height - strokeWidth)
                        )
                    }
                }

                // Countdown badge
                val countdown = when (val state = livenessState) {
                    is LivenessState.InProgress -> {
                        val remaining = ((1f - state.progress) * 3).toInt() + 1
                        remaining.coerceIn(1, 3)
                    }
                    else -> null
                }
                if (countdown != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-12).dp)
                    ) {
                        CountdownBadge(count = countdown)
                    }
                }
            }
        }

        // Challenge progress dots and info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val currentIndex = when (val state = livenessState) {
                is LivenessState.InProgress -> state.challenge.order
                is LivenessState.ChallengeComplete -> state.challenge.order + 1
                is LivenessState.Complete -> 3
                else -> 0
            }

            ChallengeDots(total = 3, currentIndex = currentIndex)

            if (sessionLoaded) {
                val challengeNum = (currentIndex + 1).coerceAtMost(3)
                Text(
                    text = "Challenge $challengeNum of 3",
                    fontSize = 13.sp,
                    color = KoraColors.WhiteAlpha50
                )
            }
        }
    }
}

/**
 * Convert ImageProxy to JPEG byte array
 */
private fun imageProxyToJpegBytes(image: ImageProxy, mirror: Boolean): ByteArray {
    val buffer = image.planes[0].buffer
    val data = ByteArray(buffer.remaining())
    buffer.get(data)

    val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
    val rotation = image.imageInfo.rotationDegrees.toFloat()

    val matrix = Matrix()
    if (rotation != 0f) {
        matrix.postRotate(rotation)
    }
    if (mirror) {
        matrix.postScale(-1f, 1f)
    }

    val rotated = if (rotation != 0f || mirror) {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }

    val stream = ByteArrayOutputStream()
    rotated.compress(Bitmap.CompressFormat.JPEG, 90, stream)
    return stream.toByteArray()
}
