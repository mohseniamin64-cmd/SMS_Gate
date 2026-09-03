package com.example.receiver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayAutostartPolicyTest {
    @Test
    fun `android 15 uses persisted job before foreground service`() {
        assertFalse(GatewayAutostartPolicy.useJobFallback(34))
        assertTrue(GatewayAutostartPolicy.useJobFallback(35))
    }
}
