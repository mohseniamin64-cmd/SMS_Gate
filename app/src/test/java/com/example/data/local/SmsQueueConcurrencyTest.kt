package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SmsQueueConcurrencyTest {
    @Test
    fun `transactional get or insert keeps one request under concurrency`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.smsQueueDao()
            val item = SmsQueueItem(
                requestId = "concurrent-request",
                phoneNumber = "+15551234567",
                messageBody = "test",
                simSlot = -1,
                status = "PENDING"
            )

            (1..20).map {
                async(Dispatchers.Default) { dao.getOrInsert(item) }
            }.awaitAll()

            assertEquals(1, dao.getQueueByStatus("PENDING").count { it.requestId == item.requestId })
        } finally {
            database.close()
        }
    }

    @Test
    fun `only one concurrent worker can claim a pending row`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.smsQueueDao()
            dao.insert(
                SmsQueueItem(
                    requestId = "claim-request",
                    phoneNumber = "+15551234567",
                    messageBody = "test",
                    simSlot = -1,
                    status = "PENDING"
                )
            )
            val id = dao.getQueueByStatus("PENDING").single().id
            val claims = (1..20).map {
                async(Dispatchers.Default) { dao.claimPending(id) }
            }.awaitAll().sum()

            assertEquals(1, claims)
        } finally {
            database.close()
        }
    }
}
