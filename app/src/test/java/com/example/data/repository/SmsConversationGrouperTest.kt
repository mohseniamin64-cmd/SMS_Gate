package com.example.data.repository

import com.example.data.device.ContactInfo
import com.example.data.local.SmsDirection
import com.example.data.local.SyncedSms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsConversationGrouperTest {
    @Test
    fun messagesGroupByNormalizedAddressAndUseLocalContactName() {
        val messages = listOf(
            sms(1, "09111111111", 100, SmsDirection.INCOMING, "in"),
            sms(2, "+989111111111", 200, SmsDirection.OUTGOING, "out"),
            sms(3, "+989111111111", 300, SmsDirection.UNKNOWN, "ignored")
        )
        val contacts = listOf(ContactInfo(7, "امین", "+989111111111"))
        val groups = SmsConversationGrouper.group(messages, contacts)

        assertEquals(1, groups.size)
        assertEquals("امین", groups.single().contactName)
        assertEquals(2, groups.single().messages.size)
        assertTrue(groups.single().messages.none { it.body == "ignored" })
    }

    private fun sms(
        id: Long,
        address: String,
        date: Long,
        direction: SmsDirection,
        body: String
    ) = SyncedSms(id, address, body, date, -1, direction.storageValue)
}
