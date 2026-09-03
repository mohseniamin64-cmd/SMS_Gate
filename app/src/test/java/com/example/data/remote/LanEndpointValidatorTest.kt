package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanEndpointValidatorTest {
    @Test
    fun ipAndPortAreNormalized() {
        val result = LanEndpointValidator.validate("192.168.1.10:5000")
        assertTrue(result.isValid)
        assertEquals("http://192.168.1.10:5000/", result.normalizedBaseUrl)
        assertEquals("192.168.1.10:5000", result.endpoint)
    }

    @Test
    fun invalidPortsAndQueryStringsAreRejected() {
        assertFalse(LanEndpointValidator.validate("192.168.1.10:0").isValid)
        assertFalse(LanEndpointValidator.validate("192.168.1.10:65536").isValid)
        assertFalse(LanEndpointValidator.validate("http://192.168.1.10:5000/?key=x").isValid)
    }

    @Test
    fun loopbackGivesPhoneWarning() {
        val result = LanEndpointValidator.validate("127.0.0.1:5000")
        assertTrue(result.isValid)
        assertNotNull(result.warning)
    }
}
