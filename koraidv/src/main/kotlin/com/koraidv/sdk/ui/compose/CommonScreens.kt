package com.koraidv.sdk.ui.compose

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.koraidv.sdk.KoraException
import com.koraidv.sdk.Verification
import com.koraidv.sdk.VerificationStatus
import com.koraidv.sdk.ui.ProcessingStep

@Composable
fun LoadingScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .alpha(alpha),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Preparing verification...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ProcessingScreen(step: ProcessingStep = ProcessingStep.ANALYZING) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(step) {
        val targetProgress = when (step) {
            ProcessingStep.ANALYZING -> 0.3f
            ProcessingStep.CHECKING_QUALITY -> 0.6f
            ProcessingStep.FINALIZING -> 0.9f
        }
        progress.animateTo(
            targetValue = targetProgress,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = step.label,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            LinearProgressIndicator(
                progress = { progress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Step indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProcessingStep.entries.forEach { s ->
                    val isActive = s == step
                    val isComplete = s.ordinal < step.ordinal

                    if (isComplete) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(20.dp),
                            shape = MaterialTheme.shapes.small,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {}
                    }

                    if (s != ProcessingStep.entries.last()) {
                        HorizontalDivider(
                            modifier = Modifier.width(24.dp),
                            color = if (isComplete) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ResultScreen(
    verification: Verification,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Result icon
        val (icon, color, title, subtitle) = when (verification.status) {
            VerificationStatus.APPROVED -> Quadruple(
                Icons.Default.CheckCircle,
                MaterialTheme.colorScheme.primary,
                "Verification Successful",
                "Your identity has been successfully verified."
            )
            VerificationStatus.REJECTED -> Quadruple(
                Icons.Default.Cancel,
                MaterialTheme.colorScheme.error,
                "Verification Failed",
                "We couldn't verify your identity. Please try again or contact support."
            )
            VerificationStatus.REVIEW_REQUIRED -> Quadruple(
                Icons.Default.HourglassEmpty,
                MaterialTheme.colorScheme.tertiary,
                "Review Required",
                "Your verification requires manual review. We'll notify you of the result."
            )
            else -> Quadruple(
                Icons.Default.Info,
                MaterialTheme.colorScheme.secondary,
                "Processing",
                "Your verification is being processed."
            )
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = color
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Verified info card
        if (verification.status == VerificationStatus.APPROVED && verification.documentVerification != null) {
            Spacer(modifier = Modifier.height(32.dp))
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Badge,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Verified Information",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    val doc = verification.documentVerification
                    if (doc?.firstName != null && doc.lastName != null) {
                        InfoRow("Name", "${doc.firstName} ${doc.lastName}")
                    }
                    if (doc?.dateOfBirth != null) {
                        InfoRow("Date of Birth", doc.dateOfBirth)
                    }
                    if (doc?.documentNumber != null) {
                        InfoRow("Document", maskDocumentNumber(doc.documentNumber))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun maskDocumentNumber(number: String): String {
    if (number.length <= 4) return "****"
    val suffix = number.takeLast(4)
    val masked = "*".repeat(number.length - 4)
    return masked + suffix
}

@Composable
fun ErrorScreen(
    error: KoraException,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    val (errorTitle, errorGuidance) = getErrorDetails(error)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = errorTitle,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = error.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (errorGuidance != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = errorGuidance,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        error.recoverySuggestion?.let { suggestion ->
            if (suggestion != errorGuidance) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (canRetry) {
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Try Again")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}

private fun getErrorDetails(error: KoraException): Pair<String, String?> {
    return when (error) {
        is KoraException.QualityValidationFailed -> Pair(
            "Quality Issue",
            "Place the document on a flat, well-lit surface. Avoid shadows and glare. Hold your device steady."
        )
        is KoraException.FaceNotDetected -> Pair(
            "Face Not Detected",
            "Make sure your face is clearly visible. Use good lighting and look directly at the camera."
        )
        is KoraException.LivenessCheckFailed -> Pair(
            "Liveness Check Failed",
            "Follow the on-screen instructions carefully. Make sure your face is well-lit and clearly visible."
        )
        is KoraException.NoInternet -> Pair(
            "No Connection",
            "Check your Wi-Fi or cellular connection and try again."
        )
        is KoraException.Timeout -> Pair(
            "Request Timed Out",
            "The server took too long to respond. Please try again."
        )
        is KoraException.Unauthorized -> Pair(
            "Authentication Error",
            null
        )
        else -> Pair("Something went wrong", null)
    }
}

// Helper data class for result screen
private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
