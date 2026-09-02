package com.example.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.concurrent.TimeUnit

// -------------------------------------------------------------------------
// DATA MODELS
// -------------------------------------------------------------------------

data class PendingMessageResponse(
    val requestId: String?,
    val id: String?, // fallback ID
    val to: String?,
    val phoneNumber: String?, // fallback
    val body: String?,
    val messageBody: String?, // fallback
    val simSlot: Int?,
    val sim: Int?, // fallback
    val scheduledAt: Long?,
    val expiresAt: Long?
) {
    fun getFinalRequestId(): String = requestId ?: id ?: "req_${System.currentTimeMillis()}_${(100..999).random()}"
    fun getFinalPhoneNumber(): String = to ?: phoneNumber ?: ""
    fun getFinalBody(): String = body ?: messageBody ?: ""
    fun getFinalSimSlot(): Int = simSlot ?: sim ?: -1
}

data class WebhookPayload(
    val event: String, // "sms_received", "sms_sent", "sms_delivered", "sms_failed", "sms_deleted"
    val deviceId: String,
    val requestId: String? = null,
    val from: String? = null,
    val to: String? = null,
    val body: String? = null,
    val timestamp: Long,
    val simSlot: Int? = null,
    val errorMessage: String? = null,
    val fingerprint: String? = null
)

// -------------------------------------------------------------------------
// RETROFIT API SERVICE
// -------------------------------------------------------------------------

interface SmsApiService {
    @GET
    suspend fun getPendingMessages(
        @Url url: String,
        @Header("Authorization") apiKey: String,
        @Query("deviceId") deviceId: String
    ): List<PendingMessageResponse>

    @POST
    suspend fun updateMessageStatus(
        @Url url: String,
        @Header("Authorization") apiKey: String,
        @Body payload: WebhookPayload
    ): retrofit2.Response<Unit>
}

// -------------------------------------------------------------------------
// API CLIENT & WEBHOOK SENDER
// -------------------------------------------------------------------------

object SmsApiClient {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    fun getService(baseUrl: String): SmsApiService {
        // Fallback safety for baseURL format
        val safeUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(safeUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SmsApiService::class.java)
    }

    // Direct Webhook Post Helper with HMAC Security (X-Signature and X-Timestamp)
    suspend fun sendWebhook(
        url: String,
        secret: String,
        payload: WebhookPayload
    ): Boolean {
        if (url.isBlank()) return false
        return try {
            val jsonAdapter = moshi.adapter(WebhookPayload::class.java)
            val jsonBody = jsonAdapter.toJson(payload)
            val timestamp = System.currentTimeMillis().toString()

            val requestBuilder = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody(jsonMediaType))
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Timestamp", timestamp)

            if (secret.isNotBlank()) {
                val signature = calculateHmacSha256(timestamp + "." + jsonBody, secret)
                requestBuilder.addHeader("X-Signature", signature)
            }

            val request = requestBuilder.build()
            okHttpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    // HMAC SHA256 Signature Helper
    private fun calculateHmacSha256(data: String, key: String): String {
        return try {
            val algorithm = "HmacSHA256"
            val sha256HMAC = Mac.getInstance(algorithm)
            val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), algorithm)
            sha256HMAC.init(secretKey)
            val hash = sha256HMAC.doFinal(data.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { byte -> "%02x".format(byte) }
        } catch (e: Exception) {
            ""
        }
    }
}
