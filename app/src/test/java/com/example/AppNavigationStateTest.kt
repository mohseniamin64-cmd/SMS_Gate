package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationStateTest {
    @Test
    fun backLeavesDashboardAsFinalRoot() {
        val state = AppNavigationState()
            .navigate(AppDestination.INBOX.key)
            .navigate(AppDestination.CONVERSATION.key)
        assertEquals(AppDestination.CONVERSATION.key, state.currentKey)
        assertEquals(AppDestination.INBOX.key, state.back().currentKey)
        assertEquals(AppDestination.DASHBOARD.key, state.back().back().currentKey)
        assertEquals(AppDestination.DASHBOARD.key, state.back().back().back().currentKey)
    }

    @Test
    fun dashboardNavigationClearsInternalHistory() {
        val state = AppNavigationState(listOf("dashboard", "inbox", "conversation"))
            .navigateToDashboard()
        assertEquals(listOf("dashboard"), state.stack)
    }
}
