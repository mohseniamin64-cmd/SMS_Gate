package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class PendingMessageResponse(
    val requestId: String?,
    val id: String?,
    val to: String?,
    val phoneNumber: String?,
    val body: String?,
    val messageBody: String?,
    val simSlot: Int?,
    val sim: Int?,
    val scheduledAt: Long?,
    val expiresAt: Long?
) {
    fun getFinalRequestId(): String = requestId ?: id ?: ""
    fun getFinalPhoneNumber(): String = to ?: phoneNumber ?: ""
    fun getFinalBody(): String = body ?: messageBody ?: ""
    fun getFinalSimSlot(): Int = simSlot ?: sim ?: -1
}

data class WebhookPayload(
    val event: String,
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

data class HealthResponse(
    val success: Boolean = false,
    val service: String? = null
)

data class CallLogSyncRequest(
    val deviceId: String,
    val calls: List<CallLogUploadPayload>
)

data class CallLogUploadPayload(
    val id: String,
    val number: String,
    val contactName: String?,
    val timestamp: Long,
    val durationSeconds: Long,
    val type: String
)

data class RejectedCallResponse(
    val id: String?,
    val reason: String
)

data class CallLogSyncResponse(
    val success: Boolean = false,
    val accepted: List<String> = emptyList(),
    val duplicates: List<String> = emptyList(),
    val rejected: List<RejectedCallResponse> = emptyList()
)

data class CallLogListResponse(
    val success: Boolean = false,
    val calls: List<CallLogUploadPayload> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0
)

interface SmsApiService {
    @GET
    suspend fun getHealth(
        @Url url: String,
        @Header("Authorization") apiKey: String
    ): HealthResponse

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

    @POST
    suspend fun syncCallLogs(
        @Url url: String,
        @Header("Authorization") apiKey: String,
        @Body payload: CallLogSyncRequest
    ): CallLogSyncResponse

    @GET
    suspend fun getCallLogs(
        @Url url: String,
        @Header("Authorization") apiKey: String,
        @Query("deviceId") deviceId: String,
        @Query("type") type: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): CallLogListResponse
}

object SmsApiClient {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // Bodies may contain message text or phone numbers.
        level = HttpLoggingInterceptor.Level.NONE
    }
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    fun getService(baseUrl: String): SmsApiService {
        val safeUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(safeUrl)
            .client(okHttpClient)
            .addConverterFactory(retrofit2.converter.moshi.MoshiConverterFactory.create(moshi))
            .build()
            .create(SmsApiService::class.java)
    }

    suspend fun sendWebhook(url: String, secret: String, payload: WebhookPayload): Boolean {
        if (url.isBlank()) return false
        return try {
            val jsonBody = moshi.adapter(WebhookPayload::class.java).toJson(payload)
            val timestamp = System.currentTimeMillis().toString()
            val requestBuilder = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody(jsonMediaType))
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Timestamp", timestamp)
            if (secret.isNotBlank()) {
                requestBuilder.addHeader(
                    "X-Signature",
                    calculateHmacSha256("$timestamp.$jsonBody", secret)
                )
            }
            okHttpClient.newCall(requestBuilder.build()).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private fun calculateHmacSha256(data: String, key: String): String {
        return try {
            val algorithm = "HmacSHA256"
            val hmac = Mac.getInstance(algorithm)
            hmac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), algorithm))
            hmac.doFinal(data.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
        } catch (_: Exception) {
            ""
        }
    }
}
