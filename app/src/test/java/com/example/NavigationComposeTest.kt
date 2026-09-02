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
class NavigationComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun internalPageBackReturnsToDashboard() {
        composeRule.setContent { NavigationTestSurface() }
        composeRule.onNodeWithText("ورودی").performClick()
        composeRule.onNodeWithText("صندوق ورودی").assertIsDisplayed()
        composeRule.onNodeWithText("بازگشت").performClick()
        composeRule.onNodeWithText("داشبورد").assertIsDisplayed()
    }
}

@Composable
private fun NavigationTestSurface() {
    var navigation by remember { mutableStateOf(AppNavigationState()) }
    Column {
        Text(AppDestination.from(navigation.currentKey).title)
        Button(onClick = { navigation = navigation.navigate(AppDestination.INBOX.key) }) {
            Text("ورودی")
        }
        Button(onClick = { navigation = navigation.back() }) {
            Text("بازگشت")
        }
    }
}
