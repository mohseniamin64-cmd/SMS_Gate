package com.example.service

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import java.security.Signature
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.security.auth.x500.X500Principal

/**
 * Manages TLS configuration for the phone HTTP server.
 * Generates and stores the RSA private key and self-signed X.509 certificate
 * inside Android KeyStore so that the private key is never exported,
 * logged, or stored in plaintext.
 */
object PhoneServerTls {
    const val ALIAS = "phone_server_tls_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    @Volatile
    private var cachedFingerprint: String? = null

    private fun isAndroidKeyStoreAvailable(): Boolean {
        return runCatching {
            Security.getProvider(ANDROID_KEYSTORE) != null &&
                KeyStore.getInstance(ANDROID_KEYSTORE) != null
        }.getOrDefault(false)
    }

    /**
     * Creates an SSLServerSocket bound to the given port using a self-signed
     * certificate stored in Android KeyStore (or an in-memory fallback for JVM tests).
     * Returns a pair of the SSLServerSocket and its SHA-256 certificate fingerprint.
     */
    fun createSSLServerSocket(port: Int): Pair<SSLServerSocket, String> {
        val (cert, sslContext) = getOrCreateCertificateAndContext()
        val ssf = sslContext.serverSocketFactory
        val serverSocket = ssf.createServerSocket(port, 50) as SSLServerSocket
        // Enable standard modern TLS protocols
        val supported = serverSocket.supportedProtocols.toSet()
        val preferred = listOf("TLSv1.3", "TLSv1.2").filter { it in supported }
        if (preferred.isNotEmpty()) {
            serverSocket.enabledProtocols = preferred.toTypedArray()
        }
        val fingerprint = computeSha256Fingerprint(cert)
        cachedFingerprint = fingerprint
        return Pair(serverSocket, fingerprint)
    }

    /**
     * Returns the SHA-256 fingerprint of the current or generated server certificate.
     */
    fun getCertificateFingerprint(): String {
        cachedFingerprint?.let { return it }
        val (cert, _) = getOrCreateCertificateAndContext()
        val fp = computeSha256Fingerprint(cert)
        cachedFingerprint = fp
        return fp
    }

    /**
     * Retrieves the X.509 certificate used for TLS.
     */
    fun getOrCreateCertificate(): X509Certificate {
        return getOrCreateCertificateAndContext().first
    }

    private fun getOrCreateCertificateAndContext(): Pair<X509Certificate, SSLContext> {
        return if (isAndroidKeyStoreAvailable()) {
            getOrCreateAndroidKeyStoreCertificate()
        } else {
            getOrCreateJvmFallbackCertificate()
        }
    }

    private fun getOrCreateAndroidKeyStoreCertificate(): Pair<X509Certificate, SSLContext> {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(ALIAS)) {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                ANDROID_KEYSTORE
            )
            val now = System.currentTimeMillis()
            val spec = KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_DECRYPT
            )
                .setCertificateSubject(X500Principal("CN=PhoneSmsGateway, O=SMS Gate"))
                .setCertificateSerialNumber(BigInteger.valueOf(now))
                .setCertificateNotBefore(Date(now - 86_400_000L))
                .setCertificateNotAfter(Date(now + 10L * 365 * 86_400_000L))
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .build()
            kpg.initialize(spec)
            kpg.generateKeyPair()
        }
        val cert = keyStore.getCertificate(ALIAS) as X509Certificate
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, null)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, null, SecureRandom())
        return Pair(cert, sslContext)
    }

    @Volatile
    private var jvmFallbackPair: Pair<X509Certificate, SSLContext>? = null

    private fun getOrCreateJvmFallbackCertificate(): Pair<X509Certificate, SSLContext> {
        jvmFallbackPair?.let { return it }
        synchronized(this) {
            jvmFallbackPair?.let { return it }
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            val pair = kpg.generateKeyPair()
            val cert = generateJvmSelfSignedCertificate(pair)
            val ks = KeyStore.getInstance("PKCS12")
            ks.load(null, null)
            val pwd = "tls_jvm_test_pwd".toCharArray()
            ks.setKeyEntry(ALIAS, pair.private, pwd, arrayOf(cert))
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(ks, pwd)
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(kmf.keyManagers, null, SecureRandom())
            val res = Pair(cert, sslContext)
            jvmFallbackPair = res
            return res
        }
    }

    fun computeSha256Fingerprint(cert: Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    private fun generateJvmSelfSignedCertificate(pair: KeyPair): X509Certificate {
        // sha256WithRSAEncryption: 1.2.840.113549.1.1.11
        val sha256RsaOid = byteArrayOf(0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x0b)
        val sigAlg = DerWriter.seq(DerWriter.oid(sha256RsaOid), DerWriter.nullValue())
        // commonName OID: 2.5.4.3
        val cnOid = byteArrayOf(0x55, 0x04, 0x03)
        val name = DerWriter.seq(
            DerWriter.set(
                DerWriter.seq(DerWriter.oid(cnOid), DerWriter.utf8String("PhoneSmsGateway"))
            )
        )
        val validity = DerWriter.seq(
            DerWriter.utcTime("240101000000Z"),
            DerWriter.utcTime("491231235959Z")
        )
        val version = DerWriter.tagged(0xa0, DerWriter.integer(2)) // v3
        val serial = DerWriter.integer(System.currentTimeMillis())
        val tbs = DerWriter.seq(
            version,
            serial,
            sigAlg,
            name,
            validity,
            name,
            pair.public.encoded
        )
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(pair.private)
        signer.update(tbs)
        val sig = signer.sign()

        val certDer = DerWriter.seq(tbs, sigAlg, DerWriter.bitString(sig))
        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate
    }

    internal object DerWriter {
        fun writeLen(out: ByteArrayOutputStream, len: Int) {
            if (len < 128) {
                out.write(len)
            } else if (len < 256) {
                out.write(0x81)
                out.write(len)
            } else {
                out.write(0x82)
                out.write((len shr 8) and 0xFF)
                out.write(len and 0xFF)
            }
        }

        fun seq(vararg items: ByteArray): ByteArray {
            val len = items.sumOf { it.size }
            val out = ByteArrayOutputStream()
            out.write(0x30)
            writeLen(out, len)
            items.forEach { out.write(it) }
            return out.toByteArray()
        }

        fun set(vararg items: ByteArray): ByteArray {
            val len = items.sumOf { it.size }
            val out = ByteArrayOutputStream()
            out.write(0x31)
            writeLen(out, len)
            items.forEach { out.write(it) }
            return out.toByteArray()
        }

        fun tagged(tag: Int, value: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(tag)
            writeLen(out, value.size)
            out.write(value)
            return out.toByteArray()
        }

        fun integer(v: Long): ByteArray {
            val bytes = BigInteger.valueOf(v).toByteArray()
            val out = ByteArrayOutputStream()
            out.write(0x02)
            writeLen(out, bytes.size)
            out.write(bytes)
            return out.toByteArray()
        }

        fun oid(bytes: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(0x06)
            writeLen(out, bytes.size)
            out.write(bytes)
            return out.toByteArray()
        }

        fun nullValue(): ByteArray = byteArrayOf(0x05, 0x00)

        fun utf8String(s: String): ByteArray {
            val b = s.toByteArray(Charsets.UTF_8)
            val out = ByteArrayOutputStream()
            out.write(0x0C)
            writeLen(out, b.size)
            out.write(b)
            return out.toByteArray()
        }

        fun utcTime(s: String): ByteArray {
            val b = s.toByteArray(Charsets.US_ASCII)
            val out = ByteArrayOutputStream()
            out.write(0x17)
            writeLen(out, b.size)
            out.write(b)
            return out.toByteArray()
        }

        fun bitString(bytes: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(0x03)
            writeLen(out, bytes.size + 1)
            out.write(0x00)
            out.write(bytes)
            return out.toByteArray()
        }
    }
}
