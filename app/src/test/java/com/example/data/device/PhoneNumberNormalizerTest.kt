package com.example.data.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberNormalizerTest {
    @Test
    fun persianAndInternationalIranianFormsMatchLocally() {
        val canonical = "+989111111111"
        assertEquals(canonical, PhoneNumberNormalizer.normalize("۰۹۱۱۱۱۱۱۱۱۱"))
        assertEquals(canonical, PhoneNumberNormalizer.normalize("00989111111111"))
        assertTrue(PhoneNumberNormalizer.areEquivalent("09111111111", canonical))
    }

    @Test
    fun alphanumericSenderRemainsComparable() {
        assertEquals("bank sms", PhoneNumberNormalizer.normalize(" BANK   SMS "))
        assertTrue(PhoneNumberNormalizer.areEquivalent("Bank SMS", "bank sms"))
    }
}
