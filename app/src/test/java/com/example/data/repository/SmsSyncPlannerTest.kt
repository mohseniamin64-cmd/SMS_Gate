package com.example.data.repository

import com.example.data.local.SyncedSms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSyncPlannerTest {
    private val fourMessages = listOf(
        message(1, "+989111111111", 1_000L, "one"),
        message(2, "+989111111111", 2_000L, "two"),
        message(3, "+989111111111", 3_000L, "three"),
        message(4, "+989111111111", 4_000L, "four")
    )

    @Test
    fun `four message round trip does not duplicate or resurrect deleted message`() {
        val firstSync = plan(emptyList(), fourMessages)
        assertEquals(fourMessages, firstSync.newMessages)
        assertTrue(firstSync.deletedMessages.isEmpty())

        val stableSync = plan(fourMessages, fourMessages)
        assertTrue(stableSync.newMessages.isEmpty())
        assertTrue(stableSync.deletedMessages.isEmpty())

        val remaining = fourMessages.filterNot { it.id == 2L }
        val deletedSync = plan(fourMessages, remaining)
        assertEquals(listOf(fourMessages[1]), deletedSync.deletedMessages)

        val reappearedWithNewProviderId = fourMessages[1].copy(id = 22L)
        val resurrectionAttempt = plan(
            local = remaining,
            provider = remaining + reappearedWithNewProviderId,
            tombstones = setOf(fourMessages[1].tombstoneKey())
        )
        assertTrue(resurrectionAttempt.newMessages.isEmpty())
        assertTrue(resurrectionAttempt.deletedMessages.isEmpty())
    }

    @Test
    fun `deleting a complete conversation detects every message including old messages`() {
        val oldMessage = message(
            10,
            "+989122222222",
            System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000,
            "old"
        )
        val localConversation = fourMessages + oldMessage
        val deletion = plan(localConversation, emptyList())
        assertEquals(localConversation, deletion.deletedMessages)
        assertTrue(deletion.newMessages.isEmpty())
    }

    @Test
    fun `incomplete provider snapshot cannot delete local messages`() {
        val plan = SmsSyncPlanner.plan(
            localMessages = fourMessages,
            providerSnapshot = SmsProviderSnapshot(emptyMap(), isComplete = false),
            tombstones = emptySet()
        )
        assertFalse(plan.isValid)
        assertTrue(plan.newMessages.isEmpty())
        assertTrue(plan.deletedMessages.isEmpty())
    }

    private fun plan(
        local: Collection<SyncedSms>,
        provider: Collection<SyncedSms>,
        tombstones: Set<SmsTombstoneKey> = emptySet()
    ): SmsSyncPlan = SmsSyncPlanner.plan(
        localMessages = local,
        providerSnapshot = SmsProviderSnapshot(provider.associateBy { it.id }, isComplete = true),
        tombstones = tombstones
    )

    private fun message(id: Long, address: String, date: Long, body: String) = SyncedSms(
        id = id,
        address = address,
        body = body,
        date = date,
        simSlot = 1
    )
}