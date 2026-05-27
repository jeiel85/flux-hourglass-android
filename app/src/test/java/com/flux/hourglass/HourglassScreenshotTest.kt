package com.flux.hourglass

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.flux.hourglass.ui.theme.HourglassTheme
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
class HourglassScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun setupScreen_captures() {
        composeTestRule.setContent {
            HourglassTheme {
                SetupScreen(
                    hours = 0,
                    minutes = 1,
                    seconds = 30,
                    onHoursChange = {},
                    onMinutesChange = {},
                    onSecondsChange = {},
                    onStart = {},
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/setup.png")
    }
}
