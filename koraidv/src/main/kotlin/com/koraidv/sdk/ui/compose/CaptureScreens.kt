package com.koraidv.sdk.ui.compose

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
 * Document capture screen with CameraManager, auto-capture, and review
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var qualityFeedback by remember { mutableStateOf<String?>(null) }
    var capturedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var autoCapturePending by remember { mutableStateOf(false) }

    // Auto-capture: when document is stable and quality is valid
    LaunchedEffect(autoCapturePending) {
        if (autoCapturePending && !isCapturing && cameraReady) {
            isCapturing = true
            cameraManager.capturePhoto { bytes ->
                if (bytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        val validation = qualityValidator.validateDocumentImage(bitmap)
                        if (validation.isValid) {
                            val stream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                            val jpegBytes = stream.toByteArray()
                            capturedImageBytes = jpegBytes
                            capturedBitmap = bitmap
                        } else {
                            qualityFeedback = validation.issues.firstOrNull()?.message
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

    // Review mode - show captured image
    val reviewBytes = capturedImageBytes
    val reviewBitmap = capturedBitmap
    if (reviewBytes != null && reviewBitmap != null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Review Photo") },
                    navigationIcon = {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.586f)
                ) {
                    Image(
                        bitmap = reviewBitmap.asImageBitmap(),
                        contentDescription = "Captured document",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Is the document clearly visible?",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            capturedImageBytes = null
                            capturedBitmap = null
                            documentScanner.resetStability()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Retake")
                    }
                    Button(
                        onClick = { onCaptured(reviewBytes) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Use This")
                    }
                }
            }
        }
        return
    }

    // Camera capture mode
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (side == DocumentSide.FRONT) "Front of Document" else "Back of Document")
                        Text(
                            text = documentDisplayName,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                // CameraX Preview via CameraManager
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.586f)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).also { previewView ->
                                    cameraManager.initialize(
                                        lifecycleOwner = lifecycleOwner,
                                        previewView = previewView,
                                        position = CameraPosition.BACK,
                                        onInitialized = { success ->
                                            cameraReady = success
                                            if (success) {
                                                cameraManager.setFrameAnalysisListener { imageProxy ->
                                                    // DocumentScanner takes ownership of imageProxy and closes it
                                                    @Suppress("OPT_IN_USAGE")
                                                    val detection = documentScanner.detectDocument(imageProxy)
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
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Document detection overlay
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val color = when {
                                documentReady -> Color(0xFF0D9488)
                                documentDetected -> Color(0xFF0D9488).copy(alpha = 0.5f)
                                else -> Color.White.copy(alpha = 0.5f)
                            }
                            val strokeWidth = if (documentDetected) 3.dp.toPx() else 2.dp.toPx()
                            val margin = 16.dp.toPx()

                            drawRoundRect(
                                color = color,
                                topLeft = Offset(margin, margin),
                                size = Size(size.width - margin * 2, size.height - margin * 2),
                                cornerRadius = CornerRadius(12.dp.toPx()),
                                style = Stroke(width = strokeWidth)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quality feedback (post-capture issues)
                val feedback = qualityFeedback
                if (feedback != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = feedback,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Capturing...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    val guidance = qualityGuidance
                    val statusText = when {
                        guidance != null -> guidance
                        documentReady -> "Hold steady - auto-capturing..."
                        documentDetected -> "Hold steady..."
                        else -> "Position document within the frame"
                    }
                    val statusColor = when {
                        guidance != null -> MaterialTheme.colorScheme.error
                        documentReady -> MaterialTheme.colorScheme.primary
                        documentDetected -> Color(0xFF0D9488)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor
                    )
                }
            }
        }
    }
}

/**
 * Selfie capture screen with CameraManager, real ML Kit face detection, and auto-capture
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var qualityFeedback by remember { mutableStateOf<String?>(null) }
    var capturedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Auto-capture when face is ready (detected, centered, sized, stable)
    LaunchedEffect(faceReady) {
        if (faceReady && !isCapturing && cameraReady && capturedImageBytes == null) {
            isCapturing = true
            cameraManager.capturePhoto { bytes ->
                if (bytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        // Mirror for front camera
                        val matrix = Matrix()
                        matrix.postScale(-1f, 1f)
                        val mirrored = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

                        // Post-capture quality validation with face info
                        val faceInfo = FaceDetectionInfo(
                            boundingBox = android.graphics.RectF(
                                0f, 0f,
                                mirrored.width.toFloat(), mirrored.height.toFloat()
                            ),
                            confidence = 0.95f
                        )
                        val validation = qualityValidator.validateSelfieImage(mirrored, faceInfo)
                        if (validation.isValid) {
                            val stream = ByteArrayOutputStream()
                            mirrored.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                            capturedImageBytes = stream.toByteArray()
                            capturedBitmap = mirrored
                        } else {
                            qualityFeedback = validation.issues.firstOrNull()?.message
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

    // Review mode
    val reviewBytes = capturedImageBytes
    val reviewBitmap = capturedBitmap
    if (reviewBytes != null && reviewBitmap != null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Review Selfie") },
                    navigationIcon = {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(modifier = Modifier.size(300.dp)) {
                    Image(
                        bitmap = reviewBitmap.asImageBitmap(),
                        contentDescription = "Captured selfie",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Is your face clearly visible?",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            capturedImageBytes = null
                            capturedBitmap = null
                            qualityFeedback = null
                            faceReady = false
                            faceScanner.resetStability()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Retake")
                    }
                    Button(
                        onClick = { onCaptured(reviewBytes) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Use This")
                    }
                }
            }
        }
        return
    }

    // Camera capture mode
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Take a Selfie") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                // CameraX Preview with front camera
                Card(modifier = Modifier.size(300.dp)) {
                    Box(modifier = Modifier.fillMaxSize()) {
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
                                                    // FaceScanner takes ownership of imageProxy and closes it
                                                    @Suppress("OPT_IN_USAGE")
                                                    val result = faceScanner.detectFace(imageProxy)
                                                    if (result != null) {
                                                        faceDetected = result.faceDetected
                                                        guidanceMessage = result.guidanceMessage
                                                        val ready = result.faceDetected &&
                                                                result.isCentered &&
                                                                result.isSizedCorrectly &&
                                                                result.isStable
                                                        faceReady = ready
                                                        if (!ready) {
                                                            qualityFeedback = null
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

                        // Face guide oval overlay
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val color = when {
                                faceReady -> Color(0xFF0D9488)
                                faceDetected -> Color(0xFF0D9488).copy(alpha = 0.5f)
                                else -> Color.White.copy(alpha = 0.5f)
                            }
                            val strokeWidth = if (faceDetected) 3.dp.toPx() else 2.dp.toPx()
                            val ovalWidth = size.width * 0.65f
                            val ovalHeight = size.height * 0.8f
                            val left = (size.width - ovalWidth) / 2
                            val top = (size.height - ovalHeight) / 2

                            drawOval(
                                color = color,
                                topLeft = Offset(left, top),
                                size = Size(ovalWidth, ovalHeight),
                                style = Stroke(width = strokeWidth)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val feedback = qualityFeedback
                if (feedback != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = feedback,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Capturing...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = guidanceMessage ?: "Hold steady - auto-capturing...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (faceReady) MaterialTheme.colorScheme.primary
                               else if (faceDetected) MaterialTheme.colorScheme.onSurface
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Liveness check screen with real ML Kit face detection
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    // Load liveness session from server
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
            // Fallback: create a local session for testing
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

    // Handle liveness completion
    LaunchedEffect(livenessState) {
        when (val state = livenessState) {
            is LivenessState.Complete -> {
                // Submit challenge results to server if we have a session manager
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Liveness Check") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (errorMessage != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                return@Scaffold
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                // Camera preview with face tracking
                Card(modifier = Modifier.size(300.dp)) {
                    Box(modifier = Modifier.fillMaxSize()) {
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

                        // Face guide oval
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val progress = when (val state = livenessState) {
                                is LivenessState.InProgress -> state.progress
                                is LivenessState.ChallengeComplete -> 1f
                                else -> 0f
                            }
                            val color = Color(0xFF0D9488).copy(alpha = 0.5f + progress * 0.5f)
                            val ovalWidth = size.width * 0.65f
                            val ovalHeight = size.height * 0.8f
                            val left = (size.width - ovalWidth) / 2
                            val top = (size.height - ovalHeight) / 2

                            drawOval(
                                color = color,
                                topLeft = Offset(left, top),
                                size = Size(ovalWidth, ovalHeight),
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Current challenge instruction
                when (val state = livenessState) {
                    is LivenessState.InProgress -> {
                        Text(
                            text = state.challenge.instruction,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                    is LivenessState.ChallengeComplete -> {
                        Icon(
                            imageVector = if (state.passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = if (state.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (state.passed) "Great!" else "Try again",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    is LivenessState.Idle -> {
                        if (!sessionLoaded) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Preparing liveness check...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    is LivenessState.Complete -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Liveness check complete!",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    is LivenessState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Challenge progress dots
                val session = livenessManager.currentChallenge
                if (sessionLoaded) {
                    val currentIndex = when (val state = livenessState) {
                        is LivenessState.InProgress -> state.challenge.order
                        is LivenessState.ChallengeComplete -> state.challenge.order + 1
                        is LivenessState.Complete -> Int.MAX_VALUE
                        else -> 0
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(3) { index ->
                            val color = when {
                                index < currentIndex -> MaterialTheme.colorScheme.primary
                                index == currentIndex -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            Surface(
                                modifier = Modifier.size(12.dp),
                                shape = MaterialTheme.shapes.small,
                                color = color
                            ) {}
                        }
                    }
                }
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
