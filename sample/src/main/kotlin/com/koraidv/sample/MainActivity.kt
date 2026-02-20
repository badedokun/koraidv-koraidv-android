package com.koraidv.sample

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koraidv.sdk.*

/**
 * Sample app demonstrating KoraIDV SDK integration.
 *
 * Replace the API key and tenant ID below with your own credentials.
 */
class MainActivity : ComponentActivity() {

    // Register the verification launcher BEFORE onCreate
    private val verificationLauncher = registerForActivityResult(
        KoraIDV.VerificationContract()
    ) { result ->
        when (result) {
            is VerificationResult.Success -> {
                val v = result.verification
                Toast.makeText(
                    this,
                    "Verified: ${v.status.value} (ID: ${v.id})",
                    Toast.LENGTH_LONG
                ).show()
            }
            is VerificationResult.Failure -> {
                Toast.makeText(
                    this,
                    "Error: ${result.error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
            is VerificationResult.Cancelled -> {
                Toast.makeText(this, "Verification cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure the SDK once (typically done in Application.onCreate)
        if (!KoraIDV.isConfigured) {
            KoraIDV.configure(
                Configuration(
                    apiKey = "ck_sandbox_your_api_key_here",
                    tenantId = "your-tenant-uuid-here",
                    environment = Environment.SANDBOX,
                    theme = KoraTheme(
                        primaryColor = 0xFF0D9488,  // Teal
                        cornerRadius = 12f
                    ),
                    debugLogging = true
                )
            )
        }

        setContent {
            MaterialTheme {
                SampleScreen(
                    onStartVerification = { externalId, tier ->
                        verificationLauncher.launch(
                            VerificationRequest(
                                externalId = externalId,
                                tier = tier
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SampleScreen(
    onStartVerification: (externalId: String, tier: VerificationTier) -> Unit
) {
    var externalId by remember { mutableStateOf("user-123") }
    var selectedTier by remember { mutableStateOf(VerificationTier.STANDARD) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "KoraIDV Sample",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "v${KoraIDV.VERSION}",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = externalId,
            onValueChange = { externalId = it },
            label = { Text("External ID") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Verification Tier", fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VerificationTier.entries.forEach { tier ->
                FilterChip(
                    selected = tier == selectedTier,
                    onClick = { selectedTier = tier },
                    label = { Text(tier.value.replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { onStartVerification(externalId, selectedTier) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0D9488)
            )
        ) {
            Text("Start Verification", fontSize = 16.sp)
        }
    }
}
