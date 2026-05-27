package com.koraidv.sdk

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConfigurationTest {

    // =====================================================================
    // Environment auto-detection
    // =====================================================================

    // v1.6.0 dropped the legacy ck_sandbox_ and kora_sandbox_ prefixes —
    // only sk_sandbox_ auto-detects SANDBOX now. Anything else falls
    // through to PRODUCTION. These tests pin that contract so a future
    // revert can't quietly resurrect the legacy detection.

    @Test
    fun `sandbox key with sk prefix detects SANDBOX environment`() {
        val config = Configuration(
            apiKey = "sk_sandbox_abc123",
            tenantId = "test-tenant"
        )
        assertThat(config.environment).isEqualTo(Environment.SANDBOX)
    }

    @Test
    fun `legacy ck_sandbox prefix no longer detects SANDBOX`() {
        val config = Configuration(
            apiKey = "ck_sandbox_abc123",
            tenantId = "test-tenant"
        )
        assertThat(config.environment).isEqualTo(Environment.PRODUCTION)
    }

    @Test
    fun `legacy kora_sandbox prefix no longer detects SANDBOX`() {
        val config = Configuration(
            apiKey = "kora_sandbox_abc123",
            tenantId = "test-tenant"
        )
        assertThat(config.environment).isEqualTo(Environment.PRODUCTION)
    }

    @Test
    fun `live key with ck prefix detects PRODUCTION environment`() {
        val config = Configuration(
            apiKey = "ck_live_abc123",
            tenantId = "test-tenant"
        )
        assertThat(config.environment).isEqualTo(Environment.PRODUCTION)
    }

    @Test
    fun `live key with kora prefix detects PRODUCTION environment`() {
        val config = Configuration(
            apiKey = "kora_live_abc123",
            tenantId = "test-tenant"
        )
        assertThat(config.environment).isEqualTo(Environment.PRODUCTION)
    }

    @Test
    fun `unknown key prefix defaults to PRODUCTION`() {
        val config = Configuration(
            apiKey = "unknown_key_abc123",
            tenantId = "test-tenant"
        )
        assertThat(config.environment).isEqualTo(Environment.PRODUCTION)
    }

    // =====================================================================
    // resolvedBaseUrl
    // =====================================================================

    @Test
    fun `resolvedBaseUrl uses custom baseUrl when provided`() {
        val config = Configuration(
            apiKey = "ck_live_abc123",
            tenantId = "test-tenant",
            baseUrl = "https://custom.api.com/v1"
        )
        assertThat(config.resolvedBaseUrl).isEqualTo("https://custom.api.com/v1")
    }

    @Test
    fun `resolvedBaseUrl falls back to environment URL when baseUrl is null`() {
        val config = Configuration(
            apiKey = "ck_live_abc123",
            tenantId = "test-tenant"
        )
        assertThat(config.resolvedBaseUrl).isEqualTo(Environment.PRODUCTION.baseUrl)
    }

    @Test
    fun `resolvedBaseUrl uses sandbox URL for sandbox key`() {
        val config = Configuration(
            apiKey = "sk_sandbox_abc123",
            tenantId = "test-tenant"
        )
        assertThat(config.resolvedBaseUrl).isEqualTo(Environment.SANDBOX.baseUrl)
    }

    // =====================================================================
    // Default values
    // =====================================================================

    @Test
    fun `default timeout is 600 seconds`() {
        val config = Configuration(apiKey = "ck_live_x", tenantId = "t")
        assertThat(config.timeout).isEqualTo(600L)
    }

    @Test
    fun `default livenessMode is ACTIVE`() {
        val config = Configuration(apiKey = "ck_live_x", tenantId = "t")
        assertThat(config.livenessMode).isEqualTo(LivenessMode.ACTIVE)
    }

    @Test
    fun `default debugLogging is false`() {
        val config = Configuration(apiKey = "ck_live_x", tenantId = "t")
        assertThat(config.debugLogging).isFalse()
    }

    @Test
    fun `default documentTypes includes all entries`() {
        val config = Configuration(apiKey = "ck_live_x", tenantId = "t")
        assertThat(config.documentTypes).hasSize(DocumentType.entries.size)
    }

    // =====================================================================
    // DocumentType enum
    // =====================================================================

    @Test
    fun `DocumentType fromCode finds US drivers license`() {
        assertThat(DocumentType.fromCode("us_drivers_license")).isEqualTo(DocumentType.US_DRIVERS_LICENSE)
    }

    @Test
    fun `DocumentType fromCode finds international passport`() {
        assertThat(DocumentType.fromCode("international_passport")).isEqualTo(DocumentType.INTERNATIONAL_PASSPORT)
    }

    @Test
    fun `DocumentType fromCode returns null for unknown code`() {
        assertThat(DocumentType.fromCode("unknown_doc_type")).isNull()
    }

    @Test
    fun `international passport has MRZ`() {
        assertThat(DocumentType.INTERNATIONAL_PASSPORT.hasMRZ).isTrue()
    }

    @Test
    fun `US drivers license does not require MRZ`() {
        assertThat(DocumentType.US_DRIVERS_LICENSE.hasMRZ).isFalse()
    }

    @Test
    fun `US drivers license requires back`() {
        assertThat(DocumentType.US_DRIVERS_LICENSE.requiresBack).isTrue()
    }

    @Test
    fun `international passport does not require back`() {
        assertThat(DocumentType.INTERNATIONAL_PASSPORT.requiresBack).isFalse()
    }

    // =====================================================================
    // Environment URLs
    // =====================================================================

    @Test
    fun `production URL is the unified Korastratum gateway`() {
        // Production lives at api.korastratum.com /api/v1/idv (gateway routes to
        // koraidv-identity in the koraidv project). Updated in v1.2.x — earlier
        // SDKs used the now-defunct api.koraidv.com hostname.
        assertThat(Environment.PRODUCTION.baseUrl)
            .isEqualTo("https://api.korastratum.com/api/v1/idv")
    }

    @Test
    fun `sandbox URL points at the orokii-platform sandbox Cloud Run service`() {
        // Sandbox identity-service moved out of the koraidv project to
        // orokii-platform (project number 626704085312). Iterating directly
        // on the Cloud Run URL until/unless a sandbox subdomain is wired in.
        assertThat(Environment.SANDBOX.baseUrl)
            .isEqualTo("https://koraidv-identity-sandbox-626704085312.us-central1.run.app/api/v1")
    }

    // =====================================================================
    // KoraTheme defaults
    // =====================================================================

    @Test
    fun `KoraTheme defaults have expected primary color`() {
        val theme = KoraTheme()
        assertThat(theme.primaryColor).isEqualTo(0xFF0D9488)
    }

    @Test
    fun `KoraTheme defaults have expected corner radius`() {
        val theme = KoraTheme()
        assertThat(theme.cornerRadius).isEqualTo(12f)
    }

    // =====================================================================
    // Custom configuration values
    // =====================================================================

    @Test
    fun `custom timeout value is stored`() {
        val config = Configuration(apiKey = "ck_live_x", tenantId = "t", timeout = 1200)
        assertThat(config.timeout).isEqualTo(1200)
    }

    @Test
    fun `custom livenessMode PASSIVE is stored`() {
        val config = Configuration(apiKey = "ck_live_x", tenantId = "t", livenessMode = LivenessMode.PASSIVE)
        assertThat(config.livenessMode).isEqualTo(LivenessMode.PASSIVE)
    }

    @Test
    fun `custom documentTypes subset is stored`() {
        val subset = listOf(DocumentType.INTERNATIONAL_PASSPORT, DocumentType.US_DRIVERS_LICENSE)
        val config = Configuration(apiKey = "ck_live_x", tenantId = "t", documentTypes = subset)
        assertThat(config.documentTypes).hasSize(2)
        assertThat(config.documentTypes).contains(DocumentType.INTERNATIONAL_PASSPORT)
    }

    @Test
    fun `debugLogging can be enabled`() {
        val config = Configuration(apiKey = "ck_live_x", tenantId = "t", debugLogging = true)
        assertThat(config.debugLogging).isTrue()
    }

    // =====================================================================
    // Validation
    // =====================================================================

    @Test(expected = IllegalArgumentException::class)
    fun `blank apiKey throws IllegalArgumentException`() {
        Configuration(apiKey = "", tenantId = "test-tenant")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `whitespace-only apiKey throws IllegalArgumentException`() {
        Configuration(apiKey = "   ", tenantId = "test-tenant")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank tenantId throws IllegalArgumentException`() {
        Configuration(apiKey = "ck_live_x", tenantId = "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `whitespace-only tenantId throws IllegalArgumentException`() {
        Configuration(apiKey = "ck_live_x", tenantId = "   ")
    }
}
