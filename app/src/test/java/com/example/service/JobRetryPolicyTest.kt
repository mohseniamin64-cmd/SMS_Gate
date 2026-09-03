package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class JobRetryPolicyTest {

    @Test
    fun `retries on transient network and IO failures`() {
        assertTrue(JobRetryPolicy.shouldRetry(IOException("Connection reset")))
        assertTrue(JobRetryPolicy.shouldRetry(SocketTimeoutException("Timeout")))
    }

    @Test
    fun `retries on service start not allowed illegal state exceptions`() {
        assertTrue(JobRetryPolicy.shouldRetry(IllegalStateException("Not allowed to start service Intent")))
        assertTrue(JobRetryPolicy.shouldRetry(IllegalStateException("app in background cannot start foreground service")))
    }

    @Test
    fun `does not retry on fatal configuration or security errors`() {
        assertFalse(JobRetryPolicy.shouldRetry(IllegalArgumentException("Invalid parameter")))
        assertFalse(JobRetryPolicy.shouldRetry(SecurityException("Permission denied")))
        assertFalse(JobRetryPolicy.shouldRetry(NullPointerException("Missing object")))
    }

    @Test
    fun `computes exponential backoff with ceiling`() {
        assertEquals(10_000L, JobRetryPolicy.computeBackoffMillis(1))
        assertEquals(20_000L, JobRetryPolicy.computeBackoffMillis(2))
        assertEquals(40_000L, JobRetryPolicy.computeBackoffMillis(3))
        assertEquals(300_000L, JobRetryPolicy.computeBackoffMillis(10))
    }
}
