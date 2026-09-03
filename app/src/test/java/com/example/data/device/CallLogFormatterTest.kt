package com.example.data.device

import android.provider.CallLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallLogFormatterTest {
    @Test
    fun durationIsReadableAndNonNegative() {
        assertEquals("0 ثانیه", CallLogFormatter.duration(-1))
        assertEquals("1 دقیقه و 5 ثانیه", CallLogFormatter.duration(65))
    }

    @Test
    fun callTypeMapsProviderValues() {
        assertEquals(CallType.INCOMING, CallType.fromProviderValue(CallLog.Calls.INCOMING_TYPE))
        assertEquals(CallType.OUTGOING, CallType.fromProviderValue(CallLog.Calls.OUTGOING_TYPE))
        assertEquals(CallType.MISSED, CallType.fromProviderValue(CallLog.Calls.MISSED_TYPE))
        assertNull(CallType.fromProviderValue(99))
    }

    @Test
    fun displayNameFallsBackToNumber() {
        val entry = CallLogEntry(1, "+989111111111", " ", 1_700_000_000_000, 0, CallType.MISSED)
        assertEquals("+989111111111", entry.displayName)
    }
}
