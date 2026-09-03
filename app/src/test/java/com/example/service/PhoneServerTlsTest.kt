package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class PhoneServerTlsTest {

    @Test
    fun `generates valid TLS certificate`() {
        val cert = PhoneServerTls.getOrCreateCertificate()
        assertNotNull(cert)
        assertEquals("RSA", cert.publicKey.algorithm)
        assertEquals("X.509", cert.type)
    }

    @Test
    fun `calculates SHA-256 fingerprint correctly`() {
        val cert = PhoneServerTls.getOrCreateCertificate()
        val fingerprint = PhoneServerTls.getCertificateFingerprint()

        assertTrue(fingerprint.isNotBlank())
        // SHA-256 has 32 bytes -> 32 pairs of hex digits separated by colons -> 32*2 + 31 = 95 characters
        assertEquals(95, fingerprint.length)
        assertTrue(fingerprint.contains(":"))

        val sha256 = MessageDigest.getInstance("SHA-256")
        val expectedBytes = sha256.digest(cert.encoded)
        val expectedFp = expectedBytes.joinToString(":") { "%02X".format(it) }
        assertEquals(expectedFp, fingerprint)
    }
}
