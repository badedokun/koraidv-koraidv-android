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
    val debugLogging: Boolean = false,
    /**
     * Result page mode (REQ-005). In [ResultPageMode.SIMPLIFIED] the SDK
     * shows only Success / Failed / Review with no scores or per-check
     * metrics. Overrides the tenant-level `result_page_mode` setting.
     */
    val resultPageMode: ResultPageMode = ResultPageMode.DETAILED,
    /**
     * Optional per-outcome copy overrides for the simplified result page.
     * Any null field falls back to the SDK's built-in default text.
     */
    val customMessages: ResultPageMessages? = null,
    /**
     * REQ-003 · Render rich visual onboarding guides on the document-capture,
     * selfie, NFC, and liveness screens. Default OFF so production SDK
     * consumers opt in explicitly. The koraidv-demo app flips this to true
     * for end-to-end review before the production rollout.
     *
     * When false, the SDK falls back to the existing minimal icon + arrow
     * UI already in place on the liveness screen. No behavioural change
     * for existing integrations.
     */
    val showVisualGuides: Boolean = false
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
            return if (apiKey.startsWith("ck_sandbox_") || apiKey.startsWith("kora_sandbox_") || apiKey.startsWith("test_") || apiKey.startsWith("sk_sandbox_")) {
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
    PRODUCTION("https://api.korastratum.com/api/v1/idv"),
    SANDBOX("https://koraidv-identity-sandbox-626704085312.us-central1.run.app/api/v1")
}

/**
 * Controls how the end-user-facing result page is rendered (REQ-005).
 */
enum class ResultPageMode {
    /** Full breakdown with scores, per-check metrics, and risk band. */
    DETAILED,

    /** Only Success / Failed / Review with no scores or metrics. */
    SIMPLIFIED
}

/**
 * Optional per-outcome copy overrides for the simplified result page.
 * Any null field falls back to the SDK's built-in default text.
 */
data class ResultPageMessages(
    val successTitle: String? = null,
    val successMessage: String? = null,
    val failedTitle: String? = null,
    val failedMessage: String? = null,
    val reviewTitle: String? = null,
    val reviewMessage: String? = null
)

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
 *
 * Note: This enum is maintained for backward compatibility. The SDK now fetches
 * the full list of supported countries and document types dynamically from the API.
 * New document types added to the backend are automatically available without SDK updates.
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

    // Passport (covers all 197 ICAO-compliant countries)
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
    UK_BRP("uk_brp", "Biometric Residence Permit", true, true, "GB"),

    // Canada
    CANADA_DRIVERS_LICENSE("ca_drivers_license", "Driver's License", false, true, "CA"),
    CANADA_PR_CARD("ca_pr_card", "Permanent Resident Card", true, true, "CA"),
    CANADA_NATIONAL_ID("ca_national_id", "National Identity Card", true, false, "CA"),

    // India
    INDIA_DRIVERS_LICENSE("in_drivers_license", "Driver's License", false, true, "IN"),

    // Liberia
    LR_ID("lr_id", "National ID", false, true, "LR"),
    LR_DRIVERS_LICENSE("lr_drivers_license", "Driver's License", false, true, "LR"),
    LR_VOTERS_CARD("lr_voters_card", "Voter Registration Card", false, false, "LR"),

    // Sierra Leone
    SL_ID("sl_id", "National ID", false, true, "SL"),
    SL_DRIVERS_LICENSE("sl_drivers_license", "Driver's License", false, true, "SL"),
    SL_VOTERS_CARD("sl_voters_card", "Voter ID Card", false, false, "SL"),

    // Gambia
    GM_ID("gm_id", "National ID", false, true, "GM"),
    GM_DRIVERS_LICENSE("gm_drivers_license", "Driver's License", false, true, "GM"),

    // Nigeria (additional)
    NG_VOTERS_CARD("ng_voters_card", "Permanent Voter's Card (PVC)", false, false, "NG"),

    // EU/EEA Residence Permits
    DE_RP("de_rp", "Residence Permit", true, true, "DE"),
    FR_RP("fr_rp", "Residence Permit", true, true, "FR"),
    IT_RP("it_rp", "Residence Permit", true, true, "IT"),
    ES_RP("es_rp", "Residence Permit", true, true, "ES"),
    IE_RP("ie_rp", "Residence Permit", true, true, "IE"),
    PT_RP("pt_rp", "Residence Permit", true, true, "PT"),
    SE_RP("se_rp", "Residence Permit", true, true, "SE"),
    DK_RP("dk_rp", "Residence Permit", true, true, "DK"),
    NO_RP("no_rp", "Residence Permit", true, true, "NO"),
    FI_RP("fi_rp", "Residence Permit", true, true, "FI"),
    PL_RP("pl_rp", "Residence Permit", true, true, "PL");

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
