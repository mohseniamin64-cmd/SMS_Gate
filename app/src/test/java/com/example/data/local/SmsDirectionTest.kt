package com.example.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsDirectionTest {
    @Test
    fun providerTypesHaveStrictDirections() {
        assertEquals(SmsDirection.INCOMING, SmsDirection.fromTelephonyType(1))
        assertEquals(SmsDirection.OUTGOING, SmsDirection.fromTelephonyType(2))
        assertEquals(SmsDirection.UNKNOWN, SmsDirection.fromTelephonyType(3))
    }
}
