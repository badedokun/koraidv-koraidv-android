package com.koraidv.sdk.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.koraidv.sdk.DocumentType
import com.koraidv.sdk.api.DocumentSide
import com.koraidv.sdk.liveness.LivenessResult

/**
 * Document capture screen placeholder
 * In production, this would include CameraX preview and document detection overlay
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentCaptureScreen(
    documentType: DocumentType,
    side: DocumentSide,
    onCaptured: (ByteArray) -> Unit,
    onCancel: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (side == DocumentSide.FRONT) "Front of Document" else "Back of Document")
                        Text(
                            text = documentType.displayName,
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
                // Placeholder for camera preview
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.586f) // ID card aspect ratio
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Camera preview would appear here",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Position document within the frame",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Capture button (placeholder - would trigger actual capture)
                FilledIconButton(
                    onClick = {
                        // In production, this would capture from camera
                        // For now, simulate with empty data
                        onCaptured(ByteArray(0))
                    },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = "Capture",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

/**
 * Selfie capture screen placeholder
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelfieCaptureScreen(
    onCaptured: (ByteArray) -> Unit,
    onCancel: () -> Unit
) {
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
                // Placeholder for camera preview with face guide
                Card(
                    modifier = Modifier
                        .size(300.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Front camera preview",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Position your face in the oval",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(32.dp))

                FilledIconButton(
                    onClick = {
                        onCaptured(ByteArray(0))
                    },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = "Capture",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

/**
 * Liveness check screen placeholder
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivenessScreen(
    onComplete: (LivenessResult) -> Unit,
    onCancel: () -> Unit
) {
    var currentChallenge by remember { mutableIntStateOf(0) }
    val challenges = listOf("Blink", "Smile", "Turn Left")

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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                // Placeholder for camera preview
                Card(
                    modifier = Modifier
                        .size(300.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val icon = when (challenges.getOrNull(currentChallenge)) {
                                "Blink" -> Icons.Default.RemoveRedEye
                                "Smile" -> Icons.Default.SentimentSatisfied
                                "Turn Left" -> Icons.Default.KeyboardArrowLeft
                                else -> Icons.Default.Face
                            }

                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = challenges.getOrNull(currentChallenge) ?: "Complete",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Progress dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    challenges.forEachIndexed { index, _ ->
                        val color = when {
                            index < currentChallenge -> MaterialTheme.colorScheme.primary
                            index == currentChallenge -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        Surface(
                            modifier = Modifier.size(12.dp),
                            shape = MaterialTheme.shapes.small,
                            color = color
                        ) {}
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Simulate challenge completion
                Button(
                    onClick = {
                        if (currentChallenge < challenges.size - 1) {
                            currentChallenge++
                        } else {
                            // Complete liveness
                            onComplete(
                                LivenessResult(
                                    passed = true,
                                    challenges = emptyList(),
                                    sessionId = "mock-session"
                                )
                            )
                        }
                    }
                ) {
                    Text(if (currentChallenge < challenges.size - 1) "Next Challenge" else "Complete")
                }
            }
        }
    }
}
