package com.koraidv.sdk.api

import android.util.Log
import com.koraidv.sdk.Configuration
import com.koraidv.sdk.Environment
import com.koraidv.sdk.KoraIDV
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * API client for Kora IDV.
 */
internal class ApiClient(private val configuration: Configuration) {

    private val okHttpClient: OkHttpClient
    private val retrofit: Retrofit
    val apiService: ApiService

    init {
        okHttpClient = buildOkHttpClient()
        retrofit = buildRetrofit()
        apiService = retrofit.create(ApiService::class.java)
    }

    private fun buildOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor())

        // Certificate pinning for production API endpoints.
        // Only applied when using the default production URL (not custom baseUrl overrides).
        if (configuration.environment == Environment.PRODUCTION && configuration.baseUrl == null) {
            builder.certificatePinner(
                CertificatePinner.Builder()
                    // Pin the Let's Encrypt root and intermediate certificates used by api.koraidv.com.
                    // These pins MUST be updated before certificate rotation.
                    // To obtain current pins: openssl s_client -connect api.koraidv.com:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
                    .add("api.koraidv.com", "sha256/jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0=") // ISRG Root X1
                    .add("api.koraidv.com", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=") // ISRG Root X2 (backup)
                    .build()
            )
        }

        if (configuration.debugLogging) {
            // Use HEADERS level to avoid logging base64 image payloads (PII/biometric data)
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                Log.d("KoraIDV", message)
            }.apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    private fun authInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer ${configuration.apiKey}")
            .addHeader("X-Tenant-ID", configuration.tenantId)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "KoraIDV-Android/${KoraIDV.VERSION}")
            .build()
        chain.proceed(request)
    }

    private fun buildRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(configuration.resolvedBaseUrl + "/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
