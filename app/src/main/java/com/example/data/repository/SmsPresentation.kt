package com.example.data.repository

import com.example.data.device.ContactInfo
import com.example.data.device.PhoneNumberNormalizer
import com.example.data.local.SmsDirection
import com.example.data.local.SyncedSms

data class SmsConversation(
    val key: String,
    val address: String,
    val contactName: String?,
    val messages: List<SyncedSms>
) {
    val latestMessage: SyncedSms
        get() = messages.maxWith(compareBy<SyncedSms> { it.date }.thenBy { it.id })
}

object SmsConversationGrouper {
    fun group(
        messages: Collection<SyncedSms>,
        contacts: Collection<ContactInfo> = emptyList()
    ): List<SmsConversation> {
        val contactsByNumber = contacts
            .filter { it.displayName.isNotBlank() }
            .associateBy { it.normalizedPhone }

        return messages
            .filter { it.direction == SmsDirection.INCOMING.storageValue || it.direction == SmsDirection.OUTGOING.storageValue }
            .groupBy { PhoneNumberNormalizer.normalize(it.address).ifBlank { it.address.trim().lowercase() } }
            .map { (key, grouped) ->
                val ordered = grouped.sortedWith(compareByDescending<SyncedSms> { it.date }.thenByDescending { it.id })
                val first = ordered.first()
                SmsConversation(
                    key = key,
                    address = first.address,
                    contactName = contactsByNumber[key]?.displayName,
                    messages = ordered
                )
            }
            .sortedWith(compareByDescending<SmsConversation> { it.latestMessage.date }.thenBy { it.key })
    }
}
