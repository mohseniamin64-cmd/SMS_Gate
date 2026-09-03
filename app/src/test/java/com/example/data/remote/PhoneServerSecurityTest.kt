package com.example.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneServerSecurityTest {
    @Test
    fun `accepts bearer and x api key credentials`() {
        val key = "local-phone-secret"

        assertTrue(PhoneServerSecurity.isAuthorized(mapOf("authorization" to "Bearer $key"), key))
        assertTrue(PhoneServerSecurity.isAuthorized(mapOf("x-api-key" to key), key))
    }

    @Test
    fun `rejects missing wrong and blank credentials`() {
        val key = "local-phone-secret"

        assertFalse(PhoneServerSecurity.isAuthorized(emptyMap(), key))
        assertFalse(PhoneServerSecurity.isAuthorized(mapOf("authorization" to "Bearer wrong"), key))
        assertFalse(PhoneServerSecurity.isAuthorized(mapOf("x-api-key" to key), ""))
    }

    @Test
    fun `generated keys are nonempty and distinct`() {
        val first = PhoneServerSecurity.generateApiKey()
        val second = PhoneServerSecurity.generateApiKey()

        assertTrue(first.length >= 40)
        assertTrue(first != second)
    }
}
