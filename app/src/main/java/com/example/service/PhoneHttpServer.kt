package com.example.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.data.device.CallLogReader
import com.example.data.local.GatewaySettings
import com.example.data.remote.PhoneNetworkAddresses
import com.example.data.remote.PhoneServerSecurity
import com.example.data.remote.PhoneServerStatusStore
import com.example.data.repository.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Small dependency-free HTTP/1.1 server owned by SmsGatewayService.
 * It binds to all interfaces so a browser or desktop client can use the
 * phone's Wi-Fi/LAN address. Every application endpoint requires the phone
 * API key; the key is never returned by the server.
 */
class PhoneHttpServer(
    private val context: Context,
    private val repository: SmsRepository,
    private val scope: CoroutineScope
) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var activePort = 8080

    fun start(settings: GatewaySettings) {
        if (acceptJob?.isActive == true) return
        activePort = settings.phoneServerPort.coerceIn(MIN_PORT, MAX_PORT)
        PhoneServerStatusStore.starting(activePort)
        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                val socket = ServerSocket(activePort, 50)
                serverSocket = socket
                PhoneServerStatusStore.running(activePort, PhoneNetworkAddresses.localIpv4Addresses())
                while (true) {
                    val client = socket.accept()
                    launch { handle(client, settings.phoneServerApiKey) }
                }
            } catch (_: SocketException) {
                if (currentCoroutineContext().isActive) PhoneServerStatusStore.stopped(activePort)
            } catch (_: IOException) {
                PhoneServerStatusStore.failed(activePort, "پورت سرور گوشی قابل استفاده نیست")
            } catch (_: Exception) {
                PhoneServerStatusStore.failed(activePort, "راه‌اندازی سرور گوشی ناموفق بود")
            } finally {
                serverSocket = null
            }
        }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        PhoneServerStatusStore.stopped(activePort)
    }

    private suspend fun handle(socket: Socket, apiKey: String) {
        socket.use { client ->
            client.soTimeout = REQUEST_TIMEOUT_MS.toInt()
            try {
                val request = readRequest(client) ?: return
                if (request.method == "OPTIONS") {
                    writeResponse(client, 204, "")
                    return
                }
                if (!PhoneServerSecurity.isAuthorized(request.headers, apiKey)) {
                    writeResponse(client, 401, jsonError("احراز هویت لازم است"))
                    return
                }
                val response = route(request)
                writeResponse(client, response.status, response.body)
            } catch (_: Exception) {
                runCatching { writeResponse(client, 500, jsonError("خطای داخلی سرور")) }
            }
        }
    }

    private suspend fun route(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        when {
            request.method == "GET" && request.path == "/api/health" ->
                HttpResponse(200, statusJson())
            request.method == "GET" && request.path == "/api/status" ->
                HttpResponse(200, statusJson())
            request.method == "GET" && (request.path == "/api/sms/read" || request.path == "/api/sms") ->
                readSms(request)
            request.method == "POST" && request.path == "/api/sms/send" ->
                sendSms(request)
            request.method == "POST" && request.path == "/api/sms/sync" ->
                syncSms()
            request.method == "GET" && request.path == "/api/call-logs" ->
                readCallLogs()
            request.method == "POST" && request.path == "/api/call-logs/sync" ->
                readCallLogs()
            else -> HttpResponse(404, jsonError("مسیر API پیدا نشد"))
        }
    }

    private suspend fun readSms(request: HttpRequest): HttpResponse {
        val all = repository.syncedSmsDao.getAllSynced()
        val offset = request.query["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = request.query["limit"]?.toIntOrNull()?.coerceIn(1, MAX_PAGE_SIZE) ?: MAX_PAGE_SIZE
        val page = all.drop(offset).take(limit)
        val items = page.joinToString(",") { sms ->
            "{" +
                "\"id\":" + sms.id + "," +
                "\"address\":" + JSONObject.quote(sms.address) + "," +
                "\"body\":" + JSONObject.quote(sms.body) + "," +
                "\"date\":" + sms.date + "," +
                "\"simSlot\":" + sms.simSlot + "," +
                "\"direction\":" + JSONObject.quote(sms.direction) + "," +
                "\"fingerprint\":" + JSONObject.quote(sms.getFingerprint()) +
                "}"
        }
        return HttpResponse(200, "{\"success\":true,\"total\":${all.size},\"offset\":$offset,\"limit\":$limit,\"messages\":[$items]}")
    }

    private suspend fun sendSms(request: HttpRequest): HttpResponse {
        val payload = runCatching { JSONObject(request.body) }.getOrNull()
            ?: return HttpResponse(400, jsonError("بدنهٔ JSON معتبر نیست"))
        val to = payload.optString("to", payload.optString("phoneNumber")).trim()
        val body = payload.optString("body", payload.optString("messageBody")).trim()
        if (to.isBlank() || body.isBlank()) return HttpResponse(400, jsonError("شماره و متن پیامک الزامی است"))
        if (to.length > 64 || body.length > MAX_SMS_LENGTH) return HttpResponse(400, jsonError("طول دادهٔ پیامک مجاز نیست"))
        val requestId = payload.optString("requestId").trim()
        val simSlot = payload.optInt("simSlot", -1)
        val item = repository.enqueueSms(requestId, to, body, simSlot)
        repository.processOutgoingQueue()
        return HttpResponse(202, "{\"success\":true,\"requestId\":${JSONObject.quote(item.requestId)},\"status\":${JSONObject.quote(item.status)}}")
    }

    private suspend fun syncSms(): HttpResponse {
        val result = repository.syncInboxAndDetectDeletions()
        return HttpResponse(
            if (result.isSuccess) 200 else 503,
            "{\"success\":${result.isSuccess},\"message\":${JSONObject.quote(result.message)}}"
        )
    }

    private fun readCallLogs(): HttpResponse {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return HttpResponse(403, jsonError("مجوز گزارش تماس داده نشده است"))
        }
        val calls = runCatching { CallLogReader(context).read() }.getOrElse {
            return HttpResponse(503, jsonError("خواندن گزارش تماس انجام نشد"))
        }
        val items = calls.joinToString(",") { call ->
            "{" +
                "\"id\":" + call.id + "," +
                "\"number\":" + JSONObject.quote(call.number) + "," +
                "\"timestamp\":" + call.timestamp + "," +
                "\"durationSeconds\":" + call.durationSeconds + "," +
                "\"type\":" + JSONObject.quote(call.type.name) +
                "}"
        }
        return HttpResponse(200, "{\"success\":true,\"total\":${calls.size},\"calls\":[$items]}")
    }

    private suspend fun statusJson(): String {
        val settings = repository.settingsDao.getSettings() ?: GatewaySettings()
        val queued = repository.smsQueueDao.getQueueByStatus("PENDING").size
        val addresses = PhoneNetworkAddresses.localIpv4Addresses()
        val addressesJson = addresses.joinToString(",") { JSONObject.quote(it) }
        return "{\"success\":true,\"service\":\"sms-center-phone\",\"deviceId\":${JSONObject.quote(settings.deviceId)}," +
            "\"running\":true,\"port\":${settings.phoneServerPort},\"addresses\":[$addressesJson],\"pendingSms\":$queued}"
    }

    private fun readRequest(socket: Socket): HttpRequest? {
        val input = socket.getInputStream()
        val requestLine = readAsciiLine(input, MAX_HEADER_LINE) ?: return null
        val parts = requestLine.split(' ', limit = 3)
        if (parts.size != 3 || parts[0].length > 10) return null
        val target = parts[1]
        val path = target.substringBefore('?').ifBlank { "/" }
        val query = target.substringAfter('?', "").split('&')
            .filter { it.isNotBlank() }
            .associate { pair ->
                val key = URLDecoder.decode(pair.substringBefore('='), "UTF-8")
                val value = URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
                key to value
            }
        val headers = linkedMapOf<String, String>()
        var headerBytes = requestLine.length
        while (true) {
            val line = readAsciiLine(input, MAX_HEADER_LINE) ?: return null
            headerBytes += line.length
            if (headerBytes > MAX_HEADER_BYTES) return null
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator <= 0) return null
            headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
        }
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        if (contentLength < 0 || contentLength > MAX_BODY_BYTES) return null
        val bodyBytes = ByteArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val count = input.read(bodyBytes, read, contentLength - read)
            if (count <= 0) return null
            read += count
        }
        return HttpRequest(parts[0].uppercase(), path, query, headers, bodyBytes.toString(StandardCharsets.UTF_8))
    }

    private fun readAsciiLine(input: java.io.InputStream, maxLength: Int): String? {
        val bytes = java.io.ByteArrayOutputStream()
        while (bytes.size() <= maxLength) {
            val value = input.read()
            if (value < 0) return if (bytes.size() == 0) null else bytes.toString("ISO-8859-1")
            if (value == '\n'.code) {
                val result = bytes.toByteArray().toString(StandardCharsets.ISO_8859_1)
                return result.removeSuffix("\r")
            }
            bytes.write(value)
        }
        return null
    }

    private fun writeResponse(socket: Socket, status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            202 -> "Accepted"
            204 -> "No Content"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            503 -> "Service Unavailable"
            else -> "Internal Server Error"
        }
        val header = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Headers: Authorization, Content-Type, X-API-Key\r\n" +
            "Connection: close\r\n\r\n"
        BufferedOutputStream(socket.getOutputStream()).use { output ->
            output.write(header.toByteArray(StandardCharsets.ISO_8859_1))
            output.write(bytes)
            output.flush()
        }
    }

    private fun jsonError(message: String): String = "{\"success\":false,\"error\":${JSONObject.quote(message)}}"

    private data class HttpRequest(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>,
        val body: String
    )

    private data class HttpResponse(val status: Int, val body: String)

    companion object {
        private const val MIN_PORT = 1024
        private const val MAX_PORT = 65_535
        private const val MAX_PAGE_SIZE = 500
        private const val MAX_SMS_LENGTH = 10_000
        private const val MAX_HEADER_LINE = 8_192
        private const val MAX_HEADER_BYTES = 32_768
        private const val MAX_BODY_BYTES = 256 * 1024
        private const val REQUEST_TIMEOUT_MS = 15_000L
    }
}
