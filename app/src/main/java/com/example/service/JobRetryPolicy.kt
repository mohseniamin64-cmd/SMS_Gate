package com.example.service

/**
 * Encapsulates the retry policy for SmsGatewayJobService.
 * If foreground service execution is denied by Android 15 restrictions or if any
 * transient background processing error occurs, it advises JobScheduler to retry
 * with exponential backoff.
 */
object JobRetryPolicy {
    private const val INITIAL_BACKOFF_MS = 10_000L
    private const val MAX_BACKOFF_MS = 300_000L

    fun shouldRetry(throwable: Throwable): Boolean {
        return when (throwable) {
            is SecurityException,
            is IllegalArgumentException,
            is NullPointerException -> false
            else -> true
        }
    }

    fun computeBackoffMillis(attempt: Int): Long {
        if (attempt <= 1) return INITIAL_BACKOFF_MS
        val shift = (attempt - 1).coerceAtMost(30)
        val multiplier = 1L shl shift
        val backoff = INITIAL_BACKOFF_MS * multiplier
        return if (backoff > MAX_BACKOFF_MS || backoff < 0) MAX_BACKOFF_MS else backoff
    }
}
