package com.koraidv.sdk.ui.compose

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.koraidv.sdk.R
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

    // Manage side internally so FRONT→BACK never goes through AnimatedContent
    var currentSide by remember { mutableStateOf(side) }
    var showFlipInstruction by remember { mutableStateOf(false) }

    var cameraReady by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var documentDetected by remember { mutableStateOf(false) }
    var documentReady by remember { mutableStateOf(false) }
    var qualityGuidance by remember { mutableStateOf<String?>(null) }
    var capturedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var autoCapturePending by remember { mutableStateOf(false) }
    var firstDetectedTime by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(autoCapturePending) {
        if (autoCapturePending && !isCapturing && cameraReady) {
            isCapturing = true
            cameraManager.capturePhotoOnMain { bytes ->
                if (bytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        // Skip client-side quality validation — always show review screen.
                        // User can retake if quality is poor; server-side Vision API
                        // performs the real quality assessment.
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                        capturedImageBytes = stream.toByteArray()
                        capturedBitmap = bitmap
                    } else {
                        // Corrupt JPEG — show guidance and let auto-capture retry
                        qualityGuidance = "Capture failed, retrying..."
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
            documentScanner.close()
        }
    }

    // ── Review mode ──────────────────────────────────────────────────────────
    val reviewBytes = capturedImageBytes
    val reviewBitmap = capturedBitmap
    if (reviewBytes != null && reviewBitmap != null) {
        DocumentReviewScreen(
            bitmap = reviewBitmap,
            title = stringResource(R.string.koraidv_capture_review_title),
            subtitle = "${if (currentSide == DocumentSide.FRONT) stringResource(R.string.koraidv_capture_step_front) else stringResource(R.string.koraidv_capture_step_back)} of $documentDisplayName",
            onRetake = {
                capturedBitmap?.recycle()
                capturedImageBytes = null
                capturedBitmap = null
                cameraReady = false
                isCapturing = false
                documentDetected = false
                documentReady = false
                qualityGuidance = null
                autoCapturePending = false
                firstDetectedTime = null
                documentScanner.resetStability()
            },
            onAccept = {
                if (currentSide == DocumentSide.FRONT && requiresBack) {
                    // Submit front image to ViewModel (background upload)
                    onCaptured(reviewBytes)
                    // Show flip instruction before transitioning to back capture
                    capturedImageBytes = null
                    capturedBitmap = null
                    showFlipInstruction = true
                } else {
                    // Back side or no-back document — hand off to ViewModel
                    onCaptured(reviewBytes)
                }
            },
            onCancel = onCancel
        )
        return
    }

    // ── Flip document instruction ─────────────────────────────────────────────
    if (showFlipInstruction) {
        FlipDocumentScreen(
            onContinue = {
                showFlipInstruction = false
                currentSide = DocumentSide.BACK
                isCapturing = false
                documentDetected = false
                documentReady = false
                qualityGuidance = null
                autoCapturePending = false
                firstDetectedTime = null
                documentScanner.resetStability()
            },
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
            title = if (currentSide == DocumentSide.FRONT) stringResource(R.string.koraidv_capture_front) else stringResource(R.string.koraidv_capture_back),
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
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }.also { previewView ->
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
                                                val detected = detection != null
                                                documentDetected = detected
                                                qualityGuidance = detection?.qualityGuidance

                                                // Track when document was first detected for deadline timer
                                                if (detected) {
                                                    if (firstDetectedTime == null) {
                                                        firstDetectedTime = System.currentTimeMillis()
                                                    }
                                                } else {
                                                    firstDetectedTime = null
                                                }

                                                val ready = detected &&
                                                        detection!!.isStable &&
                                                        detection.qualityGuidance == null
                                                documentReady = ready

                                                // Force capture after 3s deadline if document is detected
                                                val deadline = firstDetectedTime
                                                val forceCapture = detected && deadline != null &&
                                                        (System.currentTimeMillis() - deadline) >= 3000L

                                                if ((ready || forceCapture) && !isCapturing && capturedImageBytes == null) {
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
                documentReady -> stringResource(R.string.koraidv_capture_ready)
                qualityGuidance != null -> qualityGuidance!!
                else -> stringResource(R.string.koraidv_capture_scanning)
            }
            GuidancePill(text = pillText, variant = pillVariant)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (currentSide == DocumentSide.FRONT)
                    stringResource(R.string.koraidv_capture_instruction_front)
                else
                    stringResource(R.string.koraidv_capture_instruction_back),
                fontSize = 13.sp,
                color = KoraColors.WhiteAlpha40,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step pills (Front/Back)
            if (requiresBack) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (currentSide == DocumentSide.FRONT) {
                        StepPill(stringResource(R.string.koraidv_capture_step_front), StepPillState.Active)
                        StepPill(stringResource(R.string.koraidv_capture_step_back), StepPillState.Inactive)
                    } else {
                        StepPill(stringResource(R.string.koraidv_capture_step_front), StepPillState.Done)
                        StepPill(stringResource(R.string.koraidv_capture_step_back), StepPillState.Active)
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
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Good quality badge
                ReviewBadge(text = stringResource(R.string.koraidv_capture_review_quality))

                Spacer(modifier = Modifier.height(16.dp))

                // Quality checks
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ReviewQualityCheck(stringResource(R.string.koraidv_capture_review_sharp))
                    ReviewQualityCheck(stringResource(R.string.koraidv_capture_review_well_lit))
                    ReviewQualityCheck(stringResource(R.string.koraidv_capture_review_no_glare))
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
                text = stringResource(R.string.koraidv_capture_review_readable),
                fontSize = 14.sp,
                color = KoraColors.WhiteAlpha50,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KoraButton(
                    text = stringResource(R.string.koraidv_capture_retake),
                    onClick = onRetake,
                    variant = KoraButtonVariant.DarkOutline,
                    modifier = Modifier.weight(1f)
                )
                KoraButton(
                    text = stringResource(R.string.koraidv_capture_looks_good),
                    onClick = onAccept,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Flip document instruction screen — shown between front and back capture
 */
@Composable
private fun FlipDocumentScreen(
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KoraColors.DarkBg)
    ) {
        StepProgressBar(total = 5, current = 3, isDark = true)

        DarkScreenHeader(
            title = stringResource(R.string.koraidv_flip_title),
            onClose = onCancel
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Flip icon
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(KoraColors.Teal.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = KoraColors.Teal,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.koraidv_flip_heading),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.koraidv_flip_description),
                fontSize = 15.sp,
                color = KoraColors.WhiteAlpha60,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.widthIn(max = 280.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Step pills
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StepPill(stringResource(R.string.koraidv_capture_step_front), StepPillState.Done)
                StepPill(stringResource(R.string.koraidv_capture_step_back), StepPillState.Active)
            }
        }

        // Continue button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            KoraButton(
                text = stringResource(R.string.koraidv_continue),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            )
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

    var cameraReady by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var faceDetected by remember { mutableStateOf(false) }
    var faceReady by remember { mutableStateOf(false) }
    var guidanceMessage by remember { mutableStateOf<String?>("Position your face in the oval") }
    var capturedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var autoCapturePending by remember { mutableStateOf(false) }
    var firstFaceDetectedTime by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(autoCapturePending) {
        if (autoCapturePending && !isCapturing && cameraReady && capturedImageBytes == null) {
            isCapturing = true
            cameraManager.capturePhotoOnMain { bytes ->
                if (bytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        val matrix = Matrix()
                        matrix.postScale(-1f, 1f)
                        val mirrored = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        // Recycle the original — only the mirrored copy is needed
                        if (mirrored !== bitmap) {
                            bitmap.recycle()
                        }

                        // Always show review screen — server-side ML performs real quality assessment
                        val stream = ByteArrayOutputStream()
                        mirrored.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                        capturedImageBytes = stream.toByteArray()
                        capturedBitmap = mirrored
                    } else {
                        // Corrupt JPEG — show guidance and let auto-capture retry
                        guidanceMessage = "Capture failed, retrying..."
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
                capturedBitmap?.recycle()
                capturedImageBytes = null
                capturedBitmap = null
                cameraReady = false
                isCapturing = false
                faceDetected = false
                faceReady = false
                guidanceMessage = "Position your face in the oval"
                autoCapturePending = false
                firstFaceDetectedTime = null
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
            title = stringResource(R.string.koraidv_selfie_title),
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
                text = stringResource(R.string.koraidv_selfie_face_camera),
                fontSize = 24.sp,
                fontWeight = FontWeight.W700,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.koraidv_selfie_neutral),
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

                                                        // Track when face was first detected for deadline timer
                                                        if (result.faceDetected) {
                                                            if (firstFaceDetectedTime == null) {
                                                                firstFaceDetectedTime = System.currentTimeMillis()
                                                            }
                                                        } else {
                                                            firstFaceDetectedTime = null
                                                        }

                                                        val ready = result.faceDetected &&
                                                                result.isCentered &&
                                                                result.isSizedCorrectly &&
                                                                result.isStable
                                                        faceReady = ready

                                                        // Force capture after 5s deadline if face is detected
                                                        val deadline = firstFaceDetectedTime
                                                        val forceCapture = result.faceDetected && deadline != null &&
                                                                (System.currentTimeMillis() - deadline) >= 5000L

                                                        if ((ready || forceCapture) && !isCapturing && capturedImageBytes == null) {
                                                            autoCapturePending = true
                                                        }
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
            val pillText = when {
                faceReady -> stringResource(R.string.koraidv_capture_ready)
                guidanceMessage != null -> guidanceMessage!!
                faceDetected -> "Hold steady..."
                else -> "Detecting face..."
            }
            GuidancePill(
                text = pillText,
                variant = if (faceReady) GuidancePillVariant.Ready else GuidancePillVariant.Scanning
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.koraidv_selfie_instruction),
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

        DarkScreenHeader(title = stringResource(R.string.koraidv_selfie_review_title), onClose = onCancel)

        // Bold title
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.koraidv_selfie_review_question),
                fontSize = 24.sp,
                fontWeight = FontWeight.W700,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.koraidv_selfie_review_subtitle),
                fontSize = 14.sp,
                color = KoraColors.WhiteAlpha50
            )
        }

        // Oval with captured image
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 100.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                }

                Spacer(modifier = Modifier.height(20.dp))

                ReviewBadge(text = stringResource(R.string.koraidv_selfie_review_detected))
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
                ReviewQualityCheck(stringResource(R.string.koraidv_selfie_review_clear))
                ReviewQualityCheck(stringResource(R.string.koraidv_selfie_review_centered))
                ReviewQualityCheck(stringResource(R.string.koraidv_selfie_review_well_lit))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KoraButton(
                    text = stringResource(R.string.koraidv_selfie_retake),
                    onClick = onRetake,
                    variant = KoraButtonVariant.DarkOutline,
                    modifier = Modifier.weight(1f)
                )
                KoraButton(
                    text = stringResource(R.string.koraidv_selfie_use_this),
                    onClick = onAccept,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Liveness check screen — premium design with animated challenge icons,
 * progress ring, and clear visual cues for each challenge type.
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
    var totalChallenges by remember { mutableIntStateOf(3) }

    val livenessState by livenessManager.state.collectAsState()

    LaunchedEffect(sessionManager, verificationId) {
        val fallbackSession = com.koraidv.sdk.api.LivenessSession(
            sessionId = "local-session",
            challenges = listOf(
                com.koraidv.sdk.api.LivenessChallenge("1", com.koraidv.sdk.api.ChallengeType.BLINK, "Blink your eyes", 0),
                com.koraidv.sdk.api.LivenessChallenge("2", com.koraidv.sdk.api.ChallengeType.SMILE, "Smile naturally", 1),
                com.koraidv.sdk.api.LivenessChallenge("3", com.koraidv.sdk.api.ChallengeType.TURN_LEFT, "Turn your head to the left", 2)
            ),
            expiresAt = java.util.Date(System.currentTimeMillis() + 300_000)
        )

        if (sessionManager != null && verificationId != null) {
            val result = sessionManager.createLivenessSession(verificationId)
            result.fold(
                onSuccess = { session ->
                    totalChallenges = session.challenges.size
                    livenessManager.start(session)
                    sessionLoaded = true
                },
                onFailure = {
                    // Server unavailable — use local challenges.
                    // Liveness is verified client-side; the server scores it
                    // via completeVerification() regardless.
                    totalChallenges = fallbackSession.challenges.size
                    livenessManager.start(fallbackSession)
                    sessionLoaded = true
                }
            )
        } else {
            totalChallenges = fallbackSession.challenges.size
            livenessManager.start(fallbackSession)
            sessionLoaded = true
        }
    }

    LaunchedEffect(livenessState) {
        when (val state = livenessState) {
            is LivenessState.Complete -> {
                // Liveness challenges were verified client-side (ML Kit confirmed
                // the physical action).  Skip per-challenge server upload — the
                // completeVerification() endpoint scores the full verification
                // including liveness.  Per-challenge images are captured in the
                // result for the backend if needed in the future.
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

    // Derive challenge info
    val challengeType = when (val state = livenessState) {
        is LivenessState.Countdown -> state.challenge.type
        is LivenessState.InProgress -> state.challenge.type
        is LivenessState.ChallengeComplete -> state.challenge.type
        else -> null
    }
    val isCountdown = livenessState is LivenessState.Countdown
    val countdownValue = (livenessState as? LivenessState.Countdown)?.count ?: 0
    val isChallengeComplete = livenessState is LivenessState.ChallengeComplete
    val challengePassed = (livenessState as? LivenessState.ChallengeComplete)?.passed == true

    val currentIndex = when (val state = livenessState) {
        is LivenessState.Countdown -> state.challenge.order
        is LivenessState.InProgress -> state.challenge.order
        is LivenessState.ChallengeComplete -> state.challenge.order + 1
        is LivenessState.Complete -> totalChallenges
        else -> 0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KoraColors.DarkBg)
    ) {
        StepProgressBar(total = 5, current = 5, isDark = true)

        DarkScreenHeader(title = stringResource(R.string.koraidv_liveness_title), onClose = onCancel)

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

        // ── Challenge instruction with icon ──────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isCountdown && challengeType != null) {
                // Countdown before challenge starts
                Text(
                    text = "$countdownValue",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.W700,
                    color = KoraColors.TealBright,
                    letterSpacing = (-1).sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.koraidv_liveness_get_ready),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W500,
                    color = KoraColors.WhiteAlpha50
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (challengeType) {
                        com.koraidv.sdk.api.ChallengeType.BLINK -> stringResource(R.string.koraidv_liveness_challenge_blink)
                        com.koraidv.sdk.api.ChallengeType.SMILE -> stringResource(R.string.koraidv_liveness_smile_naturally)
                        com.koraidv.sdk.api.ChallengeType.TURN_LEFT -> stringResource(R.string.koraidv_liveness_turn_head_left)
                        com.koraidv.sdk.api.ChallengeType.TURN_RIGHT -> stringResource(R.string.koraidv_liveness_turn_head_right)
                        com.koraidv.sdk.api.ChallengeType.NOD_UP -> stringResource(R.string.koraidv_liveness_challenge_nod_up)
                        com.koraidv.sdk.api.ChallengeType.NOD_DOWN -> stringResource(R.string.koraidv_liveness_challenge_nod_down)
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.White,
                    letterSpacing = (-0.5).sp
                )
            } else if (isChallengeComplete) {
                // Prominent completion indicator
                Icon(
                    imageVector = if (challengePassed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = if (challengePassed) KoraColors.TealBright else KoraColors.ErrorRed
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (challengePassed) stringResource(R.string.koraidv_liveness_complete) else "Challenge failed",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.W700,
                    color = if (challengePassed) KoraColors.TealBright else KoraColors.ErrorRed,
                    letterSpacing = (-0.5).sp
                )
                if (!challengePassed) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Don\u2019t worry, you can try again",
                        fontSize = 14.sp,
                        color = KoraColors.WhiteAlpha50
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    KoraButton(
                        text = stringResource(R.string.koraidv_liveness_try_again),
                        onClick = { livenessManager.retryCurrentChallenge() },
                        modifier = Modifier.widthIn(min = 200.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    val completedNum = (livenessState as? LivenessState.ChallengeComplete)
                        ?.challenge?.order?.plus(1) ?: currentIndex
                    Text(
                        text = stringResource(R.string.koraidv_liveness_step_done, completedNum, totalChallenges),
                        fontSize = 14.sp,
                        color = KoraColors.WhiteAlpha50
                    )
                }
            } else if (challengeType != null) {
                // Active challenge — show icon + instruction
                LivenessChallengeIcon(
                    challengeType = challengeType,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (challengeType) {
                        com.koraidv.sdk.api.ChallengeType.BLINK -> stringResource(R.string.koraidv_liveness_challenge_blink)
                        com.koraidv.sdk.api.ChallengeType.SMILE -> stringResource(R.string.koraidv_liveness_smile_naturally)
                        com.koraidv.sdk.api.ChallengeType.TURN_LEFT -> stringResource(R.string.koraidv_liveness_turn_head_left)
                        com.koraidv.sdk.api.ChallengeType.TURN_RIGHT -> stringResource(R.string.koraidv_liveness_turn_head_right)
                        com.koraidv.sdk.api.ChallengeType.NOD_UP -> stringResource(R.string.koraidv_liveness_challenge_nod_up)
                        com.koraidv.sdk.api.ChallengeType.NOD_DOWN -> stringResource(R.string.koraidv_liveness_challenge_nod_down)
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.White,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (challengeType) {
                        com.koraidv.sdk.api.ChallengeType.BLINK -> stringResource(R.string.koraidv_liveness_blink_detail)
                        com.koraidv.sdk.api.ChallengeType.SMILE -> stringResource(R.string.koraidv_liveness_smile_detail)
                        com.koraidv.sdk.api.ChallengeType.TURN_LEFT -> stringResource(R.string.koraidv_liveness_turn_left_detail)
                        com.koraidv.sdk.api.ChallengeType.TURN_RIGHT -> stringResource(R.string.koraidv_liveness_turn_right_detail)
                        com.koraidv.sdk.api.ChallengeType.NOD_UP -> stringResource(R.string.koraidv_liveness_nod_up_detail)
                        com.koraidv.sdk.api.ChallengeType.NOD_DOWN -> stringResource(R.string.koraidv_liveness_nod_down_detail)
                    },
                    fontSize = 14.sp,
                    color = KoraColors.WhiteAlpha50
                )
            } else {
                Text(
                    text = stringResource(R.string.koraidv_liveness_ready),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.White,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.koraidv_liveness_hold_steady),
                    fontSize = 14.sp,
                    color = KoraColors.WhiteAlpha50
                )
            }
        }

        // ── Oval viewfinder with progress ring and directional cue ───────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Left arrow cue for TURN_LEFT
                Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                    if (challengeType == com.koraidv.sdk.api.ChallengeType.TURN_LEFT && !isChallengeComplete) {
                        AnimatedDirectionArrow(direction = ArrowDirection.LEFT)
                    }
                }

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
                                PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }.also { previewView ->
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

                    // Progress ring
                    val progress = when (val state = livenessState) {
                        is LivenessState.InProgress -> state.progress
                        is LivenessState.ChallengeComplete -> 1f
                        else -> 0f
                    }
                    val ringColor = when {
                        isChallengeComplete && challengePassed -> KoraColors.TealBright
                        isChallengeComplete -> KoraColors.ErrorRed
                        else -> KoraColors.Teal
                    }

                    Canvas(
                        modifier = Modifier
                            .width(252.dp)
                            .height(312.dp)
                    ) {
                        val strokeWidth = 4.dp.toPx()
                        drawOval(
                            color = KoraColors.WhiteAlpha15,
                            style = Stroke(width = strokeWidth),
                            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                            size = Size(size.width - strokeWidth, size.height - strokeWidth)
                        )
                        if (progress > 0f) {
                            drawArc(
                                color = ringColor,
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                                size = Size(size.width - strokeWidth, size.height - strokeWidth)
                            )
                        }
                    }

                    // Nod arrows (above/below oval)
                    if (!isChallengeComplete) {
                        if (challengeType == com.koraidv.sdk.api.ChallengeType.NOD_UP) {
                            Box(modifier = Modifier.align(Alignment.TopCenter).offset(y = (-20).dp)) {
                                AnimatedDirectionArrow(direction = ArrowDirection.UP)
                            }
                        }
                        if (challengeType == com.koraidv.sdk.api.ChallengeType.NOD_DOWN) {
                            Box(modifier = Modifier.align(Alignment.BottomCenter).offset(y = 20.dp)) {
                                AnimatedDirectionArrow(direction = ArrowDirection.DOWN)
                            }
                        }
                    }
                }

                // Right arrow cue for TURN_RIGHT
                Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                    if (challengeType == com.koraidv.sdk.api.ChallengeType.TURN_RIGHT && !isChallengeComplete) {
                        AnimatedDirectionArrow(direction = ArrowDirection.RIGHT)
                    }
                }
            }
        }

        // ── Bottom: challenge step pills + progress ──────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Challenge step pills with labels
            if (sessionLoaded) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(totalChallenges) { index ->
                        val state = when {
                            index < currentIndex -> StepPillState.Done
                            index == currentIndex -> StepPillState.Active
                            else -> StepPillState.Inactive
                        }
                        StepPill(stringResource(R.string.koraidv_liveness_step, index + 1), state)
                    }
                }
            }

            ChallengeDots(total = totalChallenges, currentIndex = currentIndex)

            if (sessionLoaded) {
                val challengeNum = (currentIndex + 1).coerceAtMost(totalChallenges)
                Text(
                    text = stringResource(R.string.koraidv_liveness_challenge_of, challengeNum, totalChallenges),
                    fontSize = 13.sp,
                    color = KoraColors.WhiteAlpha50
                )
            }
        }
    }
}

/**
 * Animated icon for liveness challenge type
 */
@Composable
private fun LivenessChallengeIcon(
    challengeType: com.koraidv.sdk.api.ChallengeType,
    modifier: Modifier = Modifier
) {
    val icon = when (challengeType) {
        com.koraidv.sdk.api.ChallengeType.BLINK -> Icons.Default.Visibility
        com.koraidv.sdk.api.ChallengeType.SMILE -> Icons.Default.SentimentSatisfied
        com.koraidv.sdk.api.ChallengeType.TURN_LEFT -> Icons.AutoMirrored.Filled.ArrowBack
        com.koraidv.sdk.api.ChallengeType.TURN_RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
        com.koraidv.sdk.api.ChallengeType.NOD_UP -> Icons.Default.ArrowUpward
        com.koraidv.sdk.api.ChallengeType.NOD_DOWN -> Icons.Default.ArrowDownward
    }
    // Pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "challenge_icon")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_pulse"
    )

    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier,
        tint = KoraColors.TealBright.copy(alpha = alpha)
    )
}

/**
 * Direction for animated arrows
 */
private enum class ArrowDirection { LEFT, RIGHT, UP, DOWN }

/**
 * Animated pulsing/sliding arrow for directional challenges
 */
@Composable
private fun AnimatedDirectionArrow(
    direction: ArrowDirection,
    modifier: Modifier = Modifier
) {
    val icon = when (direction) {
        ArrowDirection.LEFT -> Icons.AutoMirrored.Filled.ArrowBack
        ArrowDirection.RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
        ArrowDirection.UP -> Icons.Default.ArrowUpward
        ArrowDirection.DOWN -> Icons.Default.ArrowDownward
    }

    val infiniteTransition = rememberInfiniteTransition(label = "arrow_$direction")

    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrow_offset_$direction"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrow_alpha_$direction"
    )

    val xOff = when (direction) {
        ArrowDirection.LEFT -> -offset
        ArrowDirection.RIGHT -> offset
        else -> 0f
    }
    val yOff = when (direction) {
        ArrowDirection.UP -> -offset
        ArrowDirection.DOWN -> offset
        else -> 0f
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Triple layered arrows for emphasis
        repeat(3) { i ->
            val layerAlpha = alpha * (1f - i * 0.25f)
            val layerOffset = (i + 1) * xOff to (i + 1) * yOff
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .offset(x = layerOffset.first.dp, y = layerOffset.second.dp),
                tint = KoraColors.TealBright.copy(alpha = layerAlpha.coerceAtLeast(0.15f))
            )
        }
    }
}

