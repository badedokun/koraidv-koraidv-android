package com.koraidv.sdk.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.koraidv.sdk.KoraException
import com.koraidv.sdk.Verification
import com.koraidv.sdk.VerificationStatus

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun ProcessingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Processing your verification...",
                style = MaterialTheme.typography.bodyLarge
            )
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
            text = "Something went wrong",
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

        error.recoverySuggestion?.let { suggestion ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = suggestion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
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

// Helper data class for result screen
private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
