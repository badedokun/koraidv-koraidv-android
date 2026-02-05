package com.koraidv.sdk.api

import com.koraidv.sdk.Configuration
import com.koraidv.sdk.KoraIDV
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
            .addInterceptor(retryInterceptor())

        if (configuration.debugLogging) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    private fun authInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Authorization", configuration.apiKey)
            .addHeader("X-Tenant-ID", configuration.tenantId)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "KoraIDV-Android/${KoraIDV.VERSION}")
            .build()
        chain.proceed(request)
    }

    private fun retryInterceptor(): Interceptor = Interceptor { chain ->
        var request = chain.request()
        var response = chain.proceed(request)
        var attempt = 0
        val maxRetries = 3

        while (!response.isSuccessful && shouldRetry(response.code) && attempt < maxRetries) {
            response.close()
            attempt++
            val delay = calculateDelay(attempt)
            Thread.sleep(delay)

            if (configuration.debugLogging) {
                println("[KoraIDV] Retrying request (attempt $attempt/$maxRetries)")
            }

            response = chain.proceed(request)
        }

        response
    }

    private fun shouldRetry(statusCode: Int): Boolean {
        return statusCode == 429 || statusCode in 500..599
    }

    private fun calculateDelay(attempt: Int): Long {
        val baseDelay = 1000L
        val delay = baseDelay * (1 shl (attempt - 1)) // Exponential backoff
        val jitter = (Math.random() * 500).toLong()
        return delay + jitter
    }

    private fun buildRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(configuration.environment.baseUrl + "/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
