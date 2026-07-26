package com.koraidv.sample

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.koraidv.sdk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sample app demonstrating KoraIDV SDK integration.
 *
 * Configured against the BanffPay SANDBOX tenant to reproduce the v1.10.10 retest
 * defects on a real device — see project_banffpay_v11010_retest_defects.
 */
// BanffPay consolidated sandbox tenant.
private const val BANFFPAY_TENANT = "463ad2fc-daeb-43b6-9053-57c524dd362d"
// Throwaway sandbox key for 463ad2fc (identity now enforces API-key auth, so the
// backend-create helper must send it too, not just X-Tenant-ID). Revoke after testing.
private const val SANDBOX_KEY = "kora_1c75ee40170931913798a5affa1989266df157c6726fe0b31e25f5d637c664f4"

class MainActivity : ComponentActivity() {

    // Last backend-created verification id, remembered so you can RE-resume the same
    // (now PROCESSING) verification after cancelling — the "Approved 0%" repro.
    private val lastCreatedId = mutableStateOf("")

    // Start-mode launcher (SDK creates the verification at consent-accept).
    private val verificationLauncher = registerForActivityResult(
        KoraIDV.VerificationContract()
    ) { result -> handleResult(result) }

    // Resume-mode launcher — reproduces BanffPay's integration: the verification
    // is created server-side, then handed to resumeVerification(). This is the
    // flow where restart-after-completion reused a terminal verification id and
    // POSTed a document to it -> the /document 500 retry-storm crash.
    private val resumeLauncher = registerForActivityResult(
        KoraIDV.ResumeVerificationContract()
    ) { result -> handleResult(result) }

    private fun handleResult(result: VerificationResult) {
        when (result) {
            is VerificationResult.Success ->
                Toast.makeText(
                    this,
                    "Verified: ${result.verification.status.value} (ID: ${result.verification.id})",
                    Toast.LENGTH_LONG
                ).show()
            is VerificationResult.Failure ->
                Toast.makeText(this, "Error: ${result.error.message}", Toast.LENGTH_LONG).show()
            is VerificationResult.Cancelled ->
                Toast.makeText(this, "Verification cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure the SDK once (typically done in Application.onCreate).
        if (!KoraIDV.isConfigured) {
            KoraIDV.configure(
                Configuration(
                    // Real sandbox key for tenant 463ad2fc — required now that identity
                    // enforces API-key auth on /api/v1 (IDV_REQUIRE_API_KEY). Throwaway
                    // test key; revoke after testing.
                    apiKey = "kora_1c75ee40170931913798a5affa1989266df157c6726fe0b31e25f5d637c664f4",
                    tenantId = BANFFPAY_TENANT,
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
                            VerificationRequest(externalId = externalId, tier = tier)
                        )
                    },
                    onBackendCreateAndResume = { externalId, tier ->
                        backendCreateAndResume(externalId, tier)
                    },
                    onResumeById = { id ->
                        if (id.isNotBlank()) resumeLauncher.launch(id.trim())
                    },
                    lastCreatedId = lastCreatedId.value
                )
            }
        }
    }

    /**
     * Server-creates a verification (as BanffPay's backend does) and hands the id
     * to resumeVerification — the exact integration path under test. After you
     * complete it, cancel and restart the flow: the fix under test is that the
     * server now answers 409 (not 500) so the SDK does not retry-storm /document.
     */
    private fun backendCreateAndResume(externalId: String, tier: VerificationTier) {
        lifecycleScope.launch {
            val id = try {
                withContext(Dispatchers.IO) { backendCreate(externalId, tier.value) }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Backend-create failed: ${e.message}", Toast.LENGTH_LONG).show()
                return@launch
            }
            lastCreatedId.value = id  // remember for the "Resume this ID" repro
            Toast.makeText(this@MainActivity, "Resuming $id", Toast.LENGTH_SHORT).show()
            resumeLauncher.launch(id)
        }
    }

    private fun backendCreate(externalId: String, tier: String): String {
        val url = URL(Environment.SANDBOX.baseUrl + "/verifications")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Tenant-ID", BANFFPAY_TENANT)
            setRequestProperty("Authorization", "Bearer $SANDBOX_KEY")
        }
        conn.outputStream.use { out ->
            out.write(JSONObject(mapOf("externalId" to externalId, "tier" to tier)).toString().toByteArray())
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) throw RuntimeException("HTTP $code: $body")
        return JSONObject(body).getString("id")
    }
}

@Composable
private fun SampleScreen(
    onStartVerification: (externalId: String, tier: VerificationTier) -> Unit,
    onBackendCreateAndResume: (externalId: String, tier: VerificationTier) -> Unit,
    onResumeById: (id: String) -> Unit,
    lastCreatedId: String
) {
    var externalId by remember { mutableStateOf("pixel-test-001") }
    var selectedTier by remember { mutableStateOf(VerificationTier.STANDARD) }
    // Auto-fills with the last backend-created id; editable so you can paste a
    // known PROCESSING verification id to reproduce the "Approved 0%" render bug.
    var resumeId by remember(lastCreatedId) { mutableStateOf(lastCreatedId) }

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
            text = "v${KoraIDV.VERSION} · SANDBOX · BanffPay",
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
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
        ) {
            Text("Start Verification (start mode)", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { onBackendCreateAndResume(externalId, selectedTier) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
        ) {
            Text("Backend-create + Resume (repro)", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        Text("\"Approved 0%\" repro", fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Resume a verification that has a document but was never completed " +
                "(status PROCESSING). Current SDK renders it as Approved 0%.",
            fontSize = 12.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = resumeId,
            onValueChange = { resumeId = it },
            label = { Text("Verification ID to resume") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { onResumeById(resumeId) },
            enabled = resumeId.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDB2777))
        ) {
            Text("Resume this ID", fontSize = 16.sp)
        }
    }
}
