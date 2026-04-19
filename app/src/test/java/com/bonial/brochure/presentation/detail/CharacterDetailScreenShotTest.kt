package com.bonial.brochure.presentation.detail

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.bonial.brochure.presentation.home.ErrorMessage
import com.bonial.brochure.presentation.model.CharacterDetailUi
import com.bonial.brochure.presentation.theme.CloseLoopWalletTheme
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot tests for the character detail screen composables.
 *
 * Tests the three distinct states the detail screen can be in:
 * - loading shimmer (skeleton while network request is in flight)
 * - populated content (character data fully loaded)
 * - error state (network/server error with a retry button)
 *
 * Baselines are recorded with:  ./gradlew :app:recordRoborazziDebug
 * Verified in CI with:          ./gradlew :app:verifyRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-normal-long-notround-any-420dpi-keyshidden-nonav")
class CharacterDetailScreenShotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val roborazziOptions =
        RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
        )

    @Test
    fun shimmerLoading() {
        composeRule.setContent {
            CloseLoopWalletTheme(dynamicColor = false) {
                CharacterDetailShimmer()
            }
        }
        composeRule.onRoot().captureRoboImage(roborazziOptions = roborazziOptions)
    }

    @Test
    fun populatedContent() {
        composeRule.setContent {
            CloseLoopWalletTheme(dynamicColor = false) {
                CharacterDetailContent(
                    character =
                        CharacterDetailUi(
                            id = 1,
                            name = "Rick Sanchez",
                            status = "Alive",
                            species = "Human",
                            gender = "Male",
                            origin = "Earth (C-137)",
                            location = "Citadel of Ricks",
                            imageUrl = null,
                        ),
                )
            }
        }
        composeRule.onRoot().captureRoboImage(roborazziOptions = roborazziOptions)
    }

    @Test
    fun errorState() {
        composeRule.setContent {
            CloseLoopWalletTheme(dynamicColor = false) {
                ErrorMessage(
                    message = "The server is having trouble right now. Please try again later.",
                    onRetry = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage(roborazziOptions = roborazziOptions)
    }
}
