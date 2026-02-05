package com.koraidv.sdk

/**
 * SDK Configuration.
 *
 * @property apiKey Your API key (starts with ck_live_ or ck_sandbox_)
 * @property tenantId Your tenant ID (UUID)
 * @property environment API environment (auto-detected from API key if not specified)
 * @property documentTypes Allowed document types (default: all types)
 * @property livenessMode Liveness detection mode (default: active)
 * @property theme Custom theme for UI customization
 * @property locale Locale for localization (default: system locale)
 * @property timeout Session timeout in seconds (default: 600)
 * @property debugLogging Enable debug logging (default: false)
 */
data class Configuration(
    val apiKey: String,
    val tenantId: String,
    val environment: Environment = detectEnvironment(apiKey),
    val documentTypes: List<DocumentType> = DocumentType.entries,
    val livenessMode: LivenessMode = LivenessMode.ACTIVE,
    val theme: KoraTheme = KoraTheme(),
    val locale: java.util.Locale = java.util.Locale.getDefault(),
    val timeout: Long = 600,
    val debugLogging: Boolean = false
) {
    companion object {
        private fun detectEnvironment(apiKey: String): Environment {
            return if (apiKey.startsWith("ck_sandbox_")) {
                Environment.SANDBOX
            } else {
                Environment.PRODUCTION
            }
        }
    }
}

/**
 * API Environment.
 */
enum class Environment(val baseUrl: String) {
    PRODUCTION("https://koraidv-identity-kendyplisq-uc.a.run.app/api/v1"),
    SANDBOX("https://koraidv-identity-kendyplisq-uc.a.run.app/api/v1")
}

/**
 * Liveness detection mode.
 */
enum class LivenessMode {
    /**
     * Active liveness with challenge-response (blink, smile, turn).
     */
    ACTIVE,

    /**
     * Passive liveness (single selfie analysis).
     */
    PASSIVE
}

/**
 * Supported document types.
 */
enum class DocumentType(
    val code: String,
    val displayName: String,
    val hasMRZ: Boolean,
    val requiresBack: Boolean
) {
    // US Documents
    US_PASSPORT("us_passport", "US Passport", true, false),
    US_DRIVERS_LICENSE("us_drivers_license", "US Driver's License", false, true),
    US_STATE_ID("us_state_id", "US State ID", false, true),

    // International
    INTERNATIONAL_PASSPORT("international_passport", "International Passport", true, false),
    UK_PASSPORT("uk_passport", "UK Passport", true, false),

    // EU ID Cards
    EU_ID_GERMANY("eu_id_de", "German ID Card", true, true),
    EU_ID_FRANCE("eu_id_fr", "French ID Card", true, true),
    EU_ID_SPAIN("eu_id_es", "Spanish ID Card", true, true),
    EU_ID_ITALY("eu_id_it", "Italian ID Card", true, true),

    // Africa (Priority 2)
    GHANA_CARD("ghana_card", "Ghana Card", false, false),
    NIGERIA_NIN("ng_nin", "Nigeria NIN", false, false),
    KENYA_ID("ke_id", "Kenya ID", false, true),
    SOUTH_AFRICA_ID("za_id", "South Africa ID", false, false);

    companion object {
        fun fromCode(code: String): DocumentType? {
            return entries.find { it.code == code }
        }
    }
}

/**
 * Theme configuration for UI customization.
 */
data class KoraTheme(
    val primaryColor: Long = 0xFF2563EB,      // Blue
    val backgroundColor: Long = 0xFFFFFFFF,    // White
    val surfaceColor: Long = 0xFFF8FAFC,       // Light gray
    val textColor: Long = 0xFF1E293B,          // Dark slate
    val secondaryTextColor: Long = 0xFF64748B, // Slate
    val errorColor: Long = 0xFFDC2626,         // Red
    val successColor: Long = 0xFF16A34A,       // Green
    val cornerRadius: Float = 12f,
    val fontFamily: String? = null
)
