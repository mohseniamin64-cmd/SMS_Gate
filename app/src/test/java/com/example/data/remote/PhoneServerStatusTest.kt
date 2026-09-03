package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneServerStatusTest {
    @Test
    fun `status store exposes starting running failed and stopped states`() {
        PhoneServerStatusStore.starting(9090)
        assertFalse(PhoneServerStatusStore.state.value.running)
        assertEquals(9090, PhoneServerStatusStore.state.value.port)

        PhoneServerStatusStore.running(9090, listOf("192.168.1.20"))
        assertTrue(PhoneServerStatusStore.state.value.running)
        assertEquals(listOf("192.168.1.20"), PhoneServerStatusStore.state.value.addresses)

        PhoneServerStatusStore.failed(9090, "bind failed")
        assertFalse(PhoneServerStatusStore.state.value.running)
        assertEquals("bind failed", PhoneServerStatusStore.state.value.error)

        PhoneServerStatusStore.stopped(9090)
        assertFalse(PhoneServerStatusStore.state.value.running)
        assertEquals(emptyList<String>(), PhoneServerStatusStore.state.value.addresses)
        assertEquals(null, PhoneServerStatusStore.state.value.error)
    }
}
