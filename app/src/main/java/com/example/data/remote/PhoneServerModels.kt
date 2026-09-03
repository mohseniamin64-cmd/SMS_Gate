package com.example.data.remote

import android.util.Base64
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
            val parts = value.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size != 2 || !parts[0].equals("Bearer", ignoreCase = true)) return false
            parts[1].trim()
        } ?: headers["x-api-key"]?.trim()
        if (supplied.isNullOrBlank()) return false
        return MessageDigest.isEqual(
            supplied.toByteArray(Charsets.UTF_8),
            expectedKey.toByteArray(Charsets.UTF_8)
        )
    }
}

object PhoneServerCors {
    const val ALLOW_METHODS = "GET, POST, OPTIONS"
    const val ALLOW_HEADERS = "Authorization, Content-Type, X-API-Key"

    fun allows(origin: String?, configuredOrigin: String): Boolean =
        !origin.isNullOrBlank() && configuredOrigin.isNotBlank() && origin == configuredOrigin

    fun normalizeOrigin(value: String): String? = runCatching {
        val uri = java.net.URI(value.trim())
        if ((uri.scheme != "http" && uri.scheme != "https") || uri.host.isNullOrBlank() ||
            uri.userInfo != null || uri.query != null || uri.fragment != null ||
            (uri.path != "" && uri.path != "/") || uri.port == 0 || uri.port < -1 || uri.port > 65_535
        ) null else value.trim().trimEnd('/')
    }.getOrNull()
}

object PhoneNetworkAddresses {
    fun localIpv4Addresses(context: Context? = null): List<String> = runCatching {
        val preferred = context?.let { localIpv4AddressesOnLan(it) }.orEmpty()
        if (preferred.isNotEmpty()) return@runCatching preferred
        NetworkInterface.getNetworkInterfaces().asSequence().toList()
            .flatMap { it.inetAddresses.asSequence().toList() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress && it.isSiteLocalAddress }
            .map { it.hostAddress }
            .distinct()
            .sorted()
    }.getOrDefault(emptyList())

    private fun localIpv4AddressesOnLan(context: Context): List<String> = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        manager.allNetworks.asSequence()
            .filter { network ->
                val capabilities = manager.getNetworkCapabilities(network) ?: return@filter false
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            }
            .flatMap { manager.getLinkProperties(it)?.linkAddresses.orEmpty().asSequence() }
            .mapNotNull { it.address as? Inet4Address }
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress && it.isSiteLocalAddress }
            .map { it.hostAddress }
            .distinct()
            .sorted()
    }.getOrDefault(emptyList())
}
