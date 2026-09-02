package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationStateTest {

    @Test
    fun `settings back returns to dashboard`() {
        val navigation = AppNavigationState()

        navigation.navigateTo(AppDestination.SETTINGS)
        assertEquals(AppDestination.SETTINGS.key, navigation.currentRoute.destinationKey)
        assertTrue(navigation.canGoBack)

        navigation.pop()
        assertEquals(AppDestination.DASHBOARD.key, navigation.currentRoute.destinationKey)
        assertFalse(navigation.canGoBack)
    }

    @Test
    fun `inbox conversation inbox dashboard path`() {
        val navigation = AppNavigationState()

        navigation.navigateTo(AppDestination.INBOX)
        navigation.openConversation(messageId = 42L, parent = AppDestination.INBOX)
        assertEquals(AppDestination.CONVERSATION.key, navigation.currentRoute.destinationKey)
        assertEquals(42L, navigation.currentRoute.conversationId)

        navigation.pop()
        assertEquals(AppDestination.INBOX.key, navigation.currentRoute.destinationKey)
        navigation.pop()
        assertEquals(AppDestination.DASHBOARD.key, navigation.currentRoute.destinationKey)
    }

    @Test
    fun `send back returns to dashboard without fake tab history`() {
        val navigation = AppNavigationState()

        navigation.navigateTo(AppDestination.SEND)
        navigation.navigateTo(AppDestination.SEND)
        assertEquals(listOf(AppDestination.DASHBOARD.key, AppDestination.SEND.key), navigation.backStack.map { it.destinationKey })

        navigation.pop()
        assertEquals(AppDestination.DASHBOARD.key, navigation.currentRoute.destinationKey)
    }
}
