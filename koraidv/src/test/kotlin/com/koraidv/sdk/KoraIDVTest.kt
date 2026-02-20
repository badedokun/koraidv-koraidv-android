package com.koraidv.sdk

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

class KoraIDVTest {

    @After
    fun tearDown() {
        KoraIDV.reset()
    }

    @Test
    fun `isConfigured returns false before configure`() {
        assertThat(KoraIDV.isConfigured).isFalse()
    }

    @Test
    fun `configure sets isConfigured to true`() {
        KoraIDV.configure(Configuration(apiKey = "ck_live_test", tenantId = "t"))
        assertThat(KoraIDV.isConfigured).isTrue()
    }

    @Test
    fun `reset clears configuration`() {
        KoraIDV.configure(Configuration(apiKey = "ck_live_test", tenantId = "t"))
        KoraIDV.reset()
        assertThat(KoraIDV.isConfigured).isFalse()
    }

    @Test
    fun `reset clears apiClient`() {
        KoraIDV.configure(Configuration(apiKey = "ck_live_test", tenantId = "t"))
        KoraIDV.reset()
        assertThat(KoraIDV.apiClient).isNull()
    }

    @Test
    fun `configure creates apiClient`() {
        KoraIDV.configure(Configuration(apiKey = "ck_live_test", tenantId = "t"))
        assertThat(KoraIDV.apiClient).isNotNull()
    }

    @Test(expected = KoraException.NotConfigured::class)
    fun `getConfiguration throws when not configured`() {
        KoraIDV.getConfiguration()
    }

    @Test
    fun `getConfiguration returns config after configure`() {
        val config = Configuration(apiKey = "ck_live_key", tenantId = "t-123")
        KoraIDV.configure(config)
        assertThat(KoraIDV.getConfiguration()).isEqualTo(config)
    }

    @Test
    fun `VERSION is 1_0_5`() {
        assertThat(KoraIDV.VERSION).isEqualTo("1.0.5")
    }

    @Test
    fun `configure is thread-safe - concurrent calls do not crash`() {
        val threads = (1..10).map { i ->
            Thread {
                KoraIDV.configure(
                    Configuration(apiKey = "ck_live_thread_$i", tenantId = "t-$i")
                )
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertThat(KoraIDV.isConfigured).isTrue()
    }

    @Test
    fun `reset is thread-safe - concurrent resets do not crash`() {
        KoraIDV.configure(Configuration(apiKey = "ck_live_test", tenantId = "t"))
        val threads = (1..10).map {
            Thread { KoraIDV.reset() }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertThat(KoraIDV.isConfigured).isFalse()
    }
}
