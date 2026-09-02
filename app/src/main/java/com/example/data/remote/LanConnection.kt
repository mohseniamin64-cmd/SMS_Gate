package com.example.data.remote

import java.net.URI

data class LanValidation(
    val isValid: Boolean,
    val normalizedBaseUrl: String = "",
    val endpoint: String = "",
    val warning: String? = null,
    val errorMessage: String? = null
) {
    val normalizedUrl: String
        get() = normalizedBaseUrl
    val displayEndpoint: String
        get() = endpoint
    val error: String?
        get() = errorMessage
}

object LanEndpointValidator {
    fun validate(input: String): LanValidation {
        val raw = input.trim()
        if (raw.isBlank()) {
            return LanValidation(false, errorMessage = "آدرس سرور LAN وارد نشده است")
        }

        val candidate = if (raw.contains("://")) raw else "http://" + raw
        val uri = try {
            URI(candidate)
        } catch (_: Exception) {
            return LanValidation(
                false,
                errorMessage = "فرمت آدرس LAN معتبر نیست؛ نمونه: 192.168.1.10:5000"
            )
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return LanValidation(false, errorMessage = "فقط آدرس‌های http یا https قابل استفاده هستند")
        }
        if (!uri.userInfo.isNullOrBlank() || !uri.query.isNullOrBlank() || !uri.fragment.isNullOrBlank()) {
            return LanValidation(false, errorMessage = "آدرس LAN نباید نام کاربری، query یا fragment داشته باشد")
        }
        val host = uri.host?.trim().orEmpty()
        if (host.isBlank()) {
            return LanValidation(false, errorMessage = "IP یا نام میزبان سرور مشخص نشده است")
        }
        if (uri.port == 0 || uri.port < -1 || uri.port > 65_535) {
            return LanValidation(false, errorMessage = "شمارهٔ پورت باید بین 1 و 65535 باشد")
        }

        val port = if (uri.port == -1) {
            if (scheme == "https") 443 else 80
        } else {
            uri.port
        }
        val base = candidate.substringBefore('?').substringBefore('#')
        val normalized = if (base.endsWith('/')) base else base + "/"
        val displayHost = if (host.contains(':')) "[" + host + "]" else host
        val warning = if (
            host.equals("localhost", ignoreCase = true) ||
            host == "0.0.0.0" ||
            host.startsWith("127.")
        ) {
            "127.0.0.1 روی گوشی به خود گوشی اشاره می‌کند؛ IP واقعی رایانه در شبکهٔ LAN را وارد کنید."
        } else {
            null
        }
        return LanValidation(
            isValid = true,
            normalizedBaseUrl = normalized,
            endpoint = displayHost + ":" + port,
            warning = warning
        )
    }
}

sealed interface LanConnectionState {
    data object Disconnected : LanConnectionState
    data object Connecting : LanConnectionState
    data class Connected(val endpoint: String) : LanConnectionState
    data class Error(val message: String) : LanConnectionState
    data class Offline(val message: String) : LanConnectionState
}
