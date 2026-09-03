package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppNavigationComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `navigation state survives recomposition and returns settings to dashboard`() {
        composeTestRule.setContent { NavigationHarness() }

        composeTestRule.onNodeWithText("open settings").performClick()
        composeTestRule.onNodeWithText("settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("force recomposition").performClick()
        composeTestRule.onNodeWithText("settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("back").performClick()
        composeTestRule.onNodeWithText("dashboard").assertIsDisplayed()
    }

    @Composable
    private fun NavigationHarness() {
        var navigation by remember { mutableStateOf(AppNavigationState()) }
        var recompositionCount by remember { mutableStateOf(0) }
        Column {
            Text(AppDestination.from(navigation.currentKey).key)
            Button(onClick = { navigation = navigation.navigate(AppDestination.SETTINGS.key) }) { Text("open settings") }
            Button(onClick = { recompositionCount++ }) { Text("force recomposition") }
            Text("recompositions: $recompositionCount")
            Button(onClick = { navigation = navigation.back() }) { Text("back") }
        }
    }
}
