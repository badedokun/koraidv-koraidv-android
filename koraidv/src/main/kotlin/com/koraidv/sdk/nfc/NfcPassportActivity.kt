package com.koraidv.sdk.nfc

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koraidv.sdk.KoraIDV
import com.koraidv.sdk.KoraException
import com.koraidv.sdk.ui.theme.KoraIDVTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * NFC scanning state for the UI.
 */
internal sealed class NfcScanState {
    /** Waiting for the user to present their passport */
    data object WaitingForTag : NfcScanState()

    /** NFC adapter is disabled, prompt user to enable */
    data object NfcDisabled : NfcScanState()

    /** Reading data from the chip */
    data class Reading(val step: String, val progress: Float) : NfcScanState()

    /** Reading completed successfully */
    data class Success(val data: NfcPassportData) : NfcScanState()

    /** Reading failed with an error */
    data class Error(val message: String) : NfcScanState()
}

/**
 * Activity for NFC ePassport chip reading.
 *
 * Handles NFC tag discovery via foreground dispatch, reads passport data
 * using [NfcPassportReader], and returns [NfcPassportData] as the activity result.
 *
 * This activity is launched between document capture and selfie capture
 * when the verification tier is ENHANCED and the document has an MRZ.
 *
 * Usage:
 * ```kotlin
 * val launcher = registerForActivityResult(NfcPassportActivity.Contract()) { data ->
 *     // data is NfcPassportData? (null if skipped or failed)
 * }
 * launcher.launch(NfcPassportActivity.Input(documentNumber, dateOfBirth, dateOfExpiry))
 * ```
 */
class NfcPassportActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var bacKey: BACKey? = null
    private var scanState = mutableStateOf<NfcScanState>(NfcScanState.WaitingForTag)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val documentNumber = intent.getStringExtra(EXTRA_DOCUMENT_NUMBER)
        val dateOfBirth = intent.getStringExtra(EXTRA_DATE_OF_BIRTH)
        val dateOfExpiry = intent.getStringExtra(EXTRA_DATE_OF_EXPIRY)

        if (documentNumber == null || dateOfBirth == null || dateOfExpiry == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        bacKey = BACKey(
            documentNumber = documentNumber,
            dateOfBirth = dateOfBirth,
            dateOfExpiry = dateOfExpiry
        )

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            // Device does not have NFC
            scanState.value = NfcScanState.Error("NFC is not available on this device")
        } else if (nfcAdapter?.isEnabled == false) {
            scanState.value = NfcScanState.NfcDisabled
        }

        setContent {
            val config = try {
                KoraIDV.getConfiguration()
            } catch (_: KoraException) {
                finishWithError()
                return@setContent
            }
            KoraIDVTheme(theme = config.theme) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NfcScanScreen(
                        state = scanState.value,
                        onSkip = { finishSkipped() },
                        onRetry = { scanState.value = NfcScanState.WaitingForTag },
                        onDone = { data -> finishWithData(data) },
                        onEnableNfc = { openNfcSettings() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enableForegroundDispatch()
        // Re-check NFC status when returning from settings
        if (nfcAdapter != null && nfcAdapter?.isEnabled == true &&
            scanState.value is NfcScanState.NfcDisabled
        ) {
            scanState.value = NfcScanState.WaitingForTag
        }
    }

    override fun onPause() {
        super.onPause()
        disableForegroundDispatch()
    }

    @Suppress("DEPRECATION")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (scanState.value !is NfcScanState.WaitingForTag) return

        val tag: Tag? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }

        val isoDep = tag?.let { IsoDep.get(it) }
        if (isoDep == null) {
            scanState.value = NfcScanState.Error("This NFC tag is not an ePassport chip")
            return
        }

        readPassport(isoDep)
    }

    /**
     * Enable NFC foreground dispatch to intercept tag discovery.
     */
    private fun enableForegroundDispatch() {
        val adapter = nfcAdapter ?: return

        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= 31) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)

        val techFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        val techList = arrayOf(arrayOf(IsoDep::class.java.name))

        adapter.enableForegroundDispatch(this, pendingIntent, arrayOf(techFilter), techList)
    }

    /**
     * Disable NFC foreground dispatch.
     */
    private fun disableForegroundDispatch() {
        nfcAdapter?.disableForegroundDispatch(this)
    }

    /**
     * Read passport data from the discovered NFC tag.
     */
    private fun readPassport(isoDep: IsoDep) {
        val key = bacKey ?: return
        scanState.value = NfcScanState.Reading("Connecting to chip", 0f)

        kotlinx.coroutines.MainScope().launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    isoDep.timeout = NFC_TIMEOUT_MS
                    isoDep.connect()

                    val reader = NfcPassportReader(isoDep, key)
                    reader.readPassport { step, progress ->
                        kotlinx.coroutines.MainScope().launch {
                            scanState.value = NfcScanState.Reading(step, progress)
                        }
                    }
                }

                scanState.value = NfcScanState.Success(data)
            } catch (e: NfcReadException) {
                scanState.value = NfcScanState.Error(e.message ?: "Failed to read passport chip")
            } catch (e: android.nfc.TagLostException) {
                scanState.value = NfcScanState.Error(
                    "Connection lost. Keep the passport firmly against the device and try again."
                )
            } catch (e: java.io.IOException) {
                scanState.value = NfcScanState.Error(
                    "Communication error. Keep the passport against the device and try again."
                )
            } catch (e: Exception) {
                scanState.value = NfcScanState.Error(
                    e.message ?: "An unexpected error occurred"
                )
            } finally {
                try {
                    isoDep.close()
                } catch (_: Exception) { }
            }
        }
    }

    private fun finishWithData(data: NfcPassportData) {
        val resultIntent = Intent().apply {
            putExtra(EXTRA_RESULT_DOC_NUMBER, data.documentNumber)
            putExtra(EXTRA_RESULT_FIRST_NAME, data.firstName)
            putExtra(EXTRA_RESULT_LAST_NAME, data.lastName)
            putExtra(EXTRA_RESULT_DOB, data.dateOfBirth)
            putExtra(EXTRA_RESULT_EXPIRY, data.expirationDate)
            putExtra(EXTRA_RESULT_NATIONALITY, data.nationality)
            putExtra(EXTRA_RESULT_FACE_IMAGE, data.faceImageData)
            putExtra(EXTRA_RESULT_PASSIVE_AUTH, data.passiveAuthPassed)
            data.activeAuthPassed?.let { putExtra(EXTRA_RESULT_ACTIVE_AUTH, it) }
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun finishSkipped() {
        setResult(RESULT_SKIPPED)
        finish()
    }

    private fun finishWithError() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun openNfcSettings() {
        startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
    }

    companion object {
        // Input extras
        const val EXTRA_DOCUMENT_NUMBER = "nfc_document_number"
        const val EXTRA_DATE_OF_BIRTH = "nfc_date_of_birth"
        const val EXTRA_DATE_OF_EXPIRY = "nfc_date_of_expiry"

        // Result extras
        const val EXTRA_RESULT_DOC_NUMBER = "nfc_result_doc_number"
        const val EXTRA_RESULT_FIRST_NAME = "nfc_result_first_name"
        const val EXTRA_RESULT_LAST_NAME = "nfc_result_last_name"
        const val EXTRA_RESULT_DOB = "nfc_result_dob"
        const val EXTRA_RESULT_EXPIRY = "nfc_result_expiry"
        const val EXTRA_RESULT_NATIONALITY = "nfc_result_nationality"
        const val EXTRA_RESULT_FACE_IMAGE = "nfc_result_face_image"
        const val EXTRA_RESULT_PASSIVE_AUTH = "nfc_result_passive_auth"
        const val EXTRA_RESULT_ACTIVE_AUTH = "nfc_result_active_auth"

        /** Custom result code indicating the user chose to skip NFC */
        const val RESULT_SKIPPED = 42

        private const val NFC_TIMEOUT_MS = 10_000

        /**
         * Create an intent to launch the NFC passport reader.
         *
         * @param context Calling context
         * @param documentNumber Document number from MRZ
         * @param dateOfBirth Date of birth in YYMMDD format
         * @param dateOfExpiry Date of expiry in YYMMDD format
         */
        fun createIntent(
            context: Context,
            documentNumber: String,
            dateOfBirth: String,
            dateOfExpiry: String
        ): Intent {
            return Intent(context, NfcPassportActivity::class.java).apply {
                putExtra(EXTRA_DOCUMENT_NUMBER, documentNumber)
                putExtra(EXTRA_DATE_OF_BIRTH, dateOfBirth)
                putExtra(EXTRA_DATE_OF_EXPIRY, dateOfExpiry)
            }
        }

        /**
         * Parse [NfcPassportData] from an activity result intent.
         *
         * @param resultCode The result code from onActivityResult
         * @param data The result intent
         * @return [NfcPassportData] if successful, null if skipped or failed
         */
        fun parseResult(resultCode: Int, data: Intent?): NfcPassportData? {
            if (resultCode != Activity.RESULT_OK || data == null) return null

            return NfcPassportData(
                documentNumber = data.getStringExtra(EXTRA_RESULT_DOC_NUMBER) ?: return null,
                firstName = data.getStringExtra(EXTRA_RESULT_FIRST_NAME) ?: "",
                lastName = data.getStringExtra(EXTRA_RESULT_LAST_NAME) ?: "",
                dateOfBirth = data.getStringExtra(EXTRA_RESULT_DOB) ?: "",
                expirationDate = data.getStringExtra(EXTRA_RESULT_EXPIRY) ?: "",
                nationality = data.getStringExtra(EXTRA_RESULT_NATIONALITY) ?: "",
                faceImageData = data.getByteArrayExtra(EXTRA_RESULT_FACE_IMAGE),
                passiveAuthPassed = data.getBooleanExtra(EXTRA_RESULT_PASSIVE_AUTH, false),
                activeAuthPassed = if (data.hasExtra(EXTRA_RESULT_ACTIVE_AUTH)) {
                    data.getBooleanExtra(EXTRA_RESULT_ACTIVE_AUTH, false)
                } else {
                    null
                }
            )
        }

        /**
         * Check if NFC is available on this device.
         */
        fun isNfcAvailable(context: Context): Boolean {
            return NfcAdapter.getDefaultAdapter(context) != null
        }

        /**
         * Check if NFC is enabled on this device.
         */
        fun isNfcEnabled(context: Context): Boolean {
            return NfcAdapter.getDefaultAdapter(context)?.isEnabled == true
        }
    }
}

// ===== Compose UI =====

/**
 * Main NFC scanning screen with animated state transitions.
 */
@Composable
private fun NfcScanScreen(
    state: NfcScanState,
    onSkip: () -> Unit,
    onRetry: () -> Unit,
    onDone: (NfcPassportData) -> Unit,
    onEnableNfc: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar with skip button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            if (state is NfcScanState.WaitingForTag || state is NfcScanState.NfcDisabled) {
                TextButton(onClick = onSkip) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "nfc_scan_state"
        ) { currentState ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                when (currentState) {
                    is NfcScanState.WaitingForTag -> WaitingContent()
                    is NfcScanState.NfcDisabled -> NfcDisabledContent(onEnableNfc)
                    is NfcScanState.Reading -> ReadingContent(currentState.step, currentState.progress)
                    is NfcScanState.Success -> SuccessContent(currentState.data, onDone)
                    is NfcScanState.Error -> ErrorContent(currentState.message, onRetry, onSkip)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun WaitingContent() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Pulsing NFC icon
    Box(
        modifier = Modifier
            .size((100 * scale).dp)
            .clip(CircleShape)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.15f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Nfc,
            contentDescription = "NFC",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }

    Spacer(modifier = Modifier.height(32.dp))

    Text(
        text = "Scan Passport Chip",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "Hold the back of your phone against the passport's data page.\n" +
            "The NFC chip is usually near the photo.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp)
    )

    Spacer(modifier = Modifier.height(24.dp))

    // Instruction steps
    InstructionStep(
        icon = Icons.Filled.MenuBook,
        text = "Open passport to the photo page"
    )
    Spacer(modifier = Modifier.height(8.dp))
    InstructionStep(
        icon = Icons.Filled.PhoneAndroid,
        text = "Place phone flat on the passport"
    )
    Spacer(modifier = Modifier.height(8.dp))
    InstructionStep(
        icon = Icons.Filled.TouchApp,
        text = "Hold still until reading completes"
    )
}

@Composable
private fun InstructionStep(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun NfcDisabledContent(onEnableNfc: () -> Unit) {
    Icon(
        imageVector = Icons.Filled.NfcOff,
        contentDescription = "NFC Disabled",
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.error
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = "NFC is Disabled",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "Please enable NFC in your device settings to read the passport chip.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp)
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onEnableNfc,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Open NFC Settings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W600
        )
    }
}

@Composable
private fun ReadingContent(step: String, progress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "reading_progress"
    )

    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.size(80.dp),
            strokeWidth = 4.dp,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
        Icon(
            imageVector = Icons.Filled.Nfc,
            contentDescription = "Reading",
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }

    Spacer(modifier = Modifier.height(32.dp))

    Text(
        text = "Reading Passport Chip",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = step,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Keep the passport against your device",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.W600
    )
}

@Composable
private fun SuccessContent(data: NfcPassportData, onDone: (NfcPassportData) -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(Color(0xFF16A34A).copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = "Success",
            modifier = Modifier.size(48.dp),
            tint = Color(0xFF16A34A)
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = "Chip Read Successfully",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Show summary of read data
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            DataRow("Name", "${data.firstName} ${data.lastName}")
            DataRow("Document", data.documentNumber)
            DataRow("Nationality", data.nationality)
            DataRow(
                "Chip Verified",
                if (data.passiveAuthPassed) "Yes" else "Unverified"
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = { onDone(data) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            text = "Continue",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W600
        )
    }
}

@Composable
private fun DataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.W600
        )
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, onSkip: () -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = "Error",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = "Reading Failed",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp)
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onRetry,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Try Again",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W600
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
        onClick = onSkip,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .height(52.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text = "Skip Chip Reading",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W600,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Dummy NfcOff icon since it may not be in material icons extended.
 * Falls back to Nfc icon if NfcOff is not available.
 */
private val Icons.Filled.NfcOff: ImageVector
    get() = Icons.Filled.Nfc
