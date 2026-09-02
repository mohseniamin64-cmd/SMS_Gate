package com.example.data.repository

import com.example.data.local.SyncedSms

data class SmsTombstoneKey(val address: String, val date: Long, val fingerprint: String)

data class SmsProviderSnapshot(
    val messagesById: Map<Long, SyncedSms>,
    val isComplete: Boolean
)

data class SmsSyncPlan(
    val newMessages: List<SyncedSms>,
    val deletedMessages: List<SyncedSms>,
    val isValid: Boolean
)

internal fun SyncedSms.tombstoneKey(): SmsTombstoneKey = SmsTombstoneKey(address, date, getFingerprint())

internal object SmsSyncPlanner {
    fun plan(
        localMessages: Collection<SyncedSms>,
        providerSnapshot: SmsProviderSnapshot,
        tombstones: Set<SmsTombstoneKey>
    ): SmsSyncPlan {
        if (!providerSnapshot.isComplete) {
            return SmsSyncPlan(emptyList(), emptyList(), isValid = false)
        }

        val localIds = localMessages.asSequence().map { it.id }.toSet()
        val providerMessages = providerSnapshot.messagesById
        val newMessages = providerMessages.values.filter { message ->
            message.id !in localIds && message.tombstoneKey() !in tombstones
        }
        val deletedMessages = localMessages.filter { it.id !in providerMessages }
        return SmsSyncPlan(newMessages, deletedMessages, isValid = true)
    }
}
