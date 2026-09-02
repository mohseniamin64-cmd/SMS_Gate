package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.example.data.local.SmsQueueItem
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun queueItemRow_rendersProperly() {
    val sampleItem = SmsQueueItem(
      requestId = "req_1",
      phoneNumber = "09123456789",
      messageBody = "تست پیامک فارسی",
      simSlot = 0,
      status = "PENDING"
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        QueueItemRow(item = sampleItem)
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("09123456789").assertIsDisplayed()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/queue_item.png")
  }
}

