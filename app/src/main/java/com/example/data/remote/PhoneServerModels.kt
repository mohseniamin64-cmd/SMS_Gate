package com.example.data.remote

import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.SecureRandom

data class PhoneServerStatus(
    val running: Boolean = false,
    val addresses: List<String> = emptyList(),
    val port: Int = 8080,
    val error: String? = null
) {
    val primaryEndpoint: String?
        get() = addresses.firstOrNull()?.let { "$it:$port" }
}

object PhoneServerStatusStore {
    private val _state = MutableStateFlow(PhoneServerStatus())
    val state: StateFlow<PhoneServerStatus> = _state.asStateFlow()

    fun starting(port: Int) {
        _state.value = PhoneServerStatus(running = false, port = port)
    }

    fun running(port: Int, addresses: List<String>) {
        _state.value = PhoneServerStatus(running = true, port = port, addresses = addresses)
    }

    fun stopped(port: Int = _state.value.port) {
        _state.value = PhoneServerStatus(port = port)
    }

    fun failed(port: Int, message: String) {
        _state.value = PhoneServerStatus(port = port, error = message)
    }
}

object PhoneServerSecurity {
    fun generateApiKey(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun isAuthorized(headers: Map<String, String>, expectedKey: String): Boolean {
        if (expectedKey.isBlank()) return false
        val supplied = headers["authorization"]?.let { value ->
            value.removePrefix("Bearer ").removePrefix("bearer ").trim()
        } ?: headers["x-api-key"]?.trim()
        if (supplied.isNullOrBlank()) return false
        return MessageDigest.isEqual(
            supplied.toByteArray(Charsets.UTF_8),
            expectedKey.toByteArray(Charsets.UTF_8)
        )
    }
}

object PhoneNetworkAddresses {
    fun localIpv4Addresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence().toList()
            .flatMap { it.inetAddresses.asSequence().toList() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress && it.isSiteLocalAddress }
            .map { it.hostAddress }
            .distinct()
            .sorted()
    }.getOrDefault(emptyList())
}
