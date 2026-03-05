package com.koraidv.sdk

/**
 * SDK Configuration.
 *
 * @property apiKey Your API key (starts with ck_live_, ck_sandbox_, kora_live_, or kora_sandbox_)
 * @property tenantId Your tenant ID (UUID)
 * @property environment API environment (auto-detected from API key if not specified)
 * @property baseUrl Custom base URL override (e.g., for self-hosted or Cloud Run deployments)
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
    val baseUrl: String? = null,
    val documentTypes: List<DocumentType> = DocumentType.entries,
    val livenessMode: LivenessMode = LivenessMode.ACTIVE,
    val theme: KoraTheme = KoraTheme(),
    val locale: java.util.Locale = java.util.Locale.getDefault(),
    val timeout: Long = 600,
    val debugLogging: Boolean = false
) {
    init {
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }
        require(tenantId.isNotBlank()) { "tenantId must not be blank" }
    }

    /**
     * Resolved base URL: uses custom [baseUrl] if provided, otherwise falls back to [environment] default.
     */
    val resolvedBaseUrl: String
        get() = baseUrl ?: environment.baseUrl

    companion object {
        private fun detectEnvironment(apiKey: String): Environment {
            return if (apiKey.startsWith("ck_sandbox_") || apiKey.startsWith("kora_sandbox_")) {
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
    PRODUCTION("https://api.koraidv.com/api/v1"),
    SANDBOX("https://sandbox-api.koraidv.com/api/v1")
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
    val requiresBack: Boolean,
    val country: String
) {
    // US Documents
    US_DRIVERS_LICENSE("us_drivers_license", "Driver's License", false, true, "US"),
    US_STATE_ID("us_state_id", "State ID Card", false, true, "US"),
    US_GREEN_CARD("us_green_card", "Permanent Resident Card", true, true, "US"),

    // Passport (all countries)
    INTERNATIONAL_PASSPORT("international_passport", "Passport", true, false, "INTL"),

    // EU ID Cards
    EU_ID_GERMANY("eu_id_de", "National ID Card (Germany)", true, true, "DE"),
    EU_ID_FRANCE("eu_id_fr", "National ID Card (France)", true, true, "FR"),
    EU_ID_SPAIN("eu_id_es", "National ID Card (Spain)", true, true, "ES"),
    EU_ID_ITALY("eu_id_it", "National ID Card (Italy)", true, true, "IT"),

    // Africa
    GHANA_CARD("ghana_card", "Ghana Card", false, true, "GH"),
    GHANA_DRIVERS_LICENSE("gh_drivers_license", "Driver's License", false, true, "GH"),
    NIGERIA_NIN("ng_nin", "NIN Slip", false, false, "NG"),
    NIGERIA_DRIVERS_LICENSE("ng_drivers_license", "Driver's License", false, true, "NG"),
    KENYA_ID("ke_id", "National ID", false, true, "KE"),
    KENYA_DRIVERS_LICENSE("ke_drivers_license", "Driver's License", false, true, "KE"),
    SOUTH_AFRICA_ID("za_id", "Smart ID Card", false, true, "ZA"),
    SOUTH_AFRICA_DRIVERS_LICENSE("za_drivers_license", "Driver's License", false, true, "ZA"),

    // UK
    UK_DRIVERS_LICENSE("uk_drivers_license", "Driver's License", false, true, "GB"),

    // Canada
    CANADA_DRIVERS_LICENSE("ca_drivers_license", "Driver's License", false, true, "CA"),

    // India
    INDIA_DRIVERS_LICENSE("in_drivers_license", "Driver's License", false, true, "IN");

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
    val primaryColor: Long = 0xFF0D9488,      // Teal 600
    val backgroundColor: Long = 0xFFFFFFFF,    // White
    val surfaceColor: Long = 0xFFF8FAFC,       // Light gray
    val textColor: Long = 0xFF1E293B,          // Dark slate
    val secondaryTextColor: Long = 0xFF64748B, // Slate
    val errorColor: Long = 0xFFDC2626,         // Red
    val successColor: Long = 0xFF16A34A,       // Green
    val cornerRadius: Float = 12f,
    val fontFamily: String? = null
)
