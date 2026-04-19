package com.bonial.brochure.presentation.home

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.bonial.brochure.presentation.model.CharacterUi
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
 * Screenshot tests for the characters list screen composables.
 *
 * Each test captures a distinct UI state so any accidental visual regression
 * (colour shifts, layout changes, missing UI elements) shows up as a pixel diff
 * in CI rather than a runtime crash at the user's device.
 *
 * Baselines are recorded with:  ./gradlew :app:recordRoborazziDebug
 * Verified in CI with:          ./gradlew :app:verifyRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-normal-long-notround-any-420dpi-keyshidden-nonav")
class CharactersScreenShotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val roborazziOptions =
        RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
        )

    @Test
    fun shimmerLoadingGrid() {
        composeRule.setContent {
            CloseLoopWalletTheme(dynamicColor = false) {
                CharactersLoadingGrid()
            }
        }
        composeRule.onRoot().captureRoboImage(roborazziOptions = roborazziOptions)
    }

    @Test
    fun populatedGrid() {
        val characters =
            listOf(
                CharacterUi(1, "Rick Sanchez", "Alive", "Human", null, false),
                CharacterUi(2, "Morty Smith", "Alive", "Human", null, false),
                CharacterUi(3, "Summer Smith", "Alive", "Human", null, true),
                CharacterUi(4, "Beth Smith", "Alive", "Human", null, false),
            )
        composeRule.setContent {
            CloseLoopWalletTheme(dynamicColor = false) {
                CharactersGrid(
                    characters = characters,
                    isLoadingNextPage = false,
                    lazyGridState = rememberLazyGridState(),
                    onCharacterClick = {},
                    onFavouriteClick = {},
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

    @Test
    fun emptyState() {
        composeRule.setContent {
            CloseLoopWalletTheme(dynamicColor = false) {
                EmptyState()
            }
        }
        composeRule.onRoot().captureRoboImage(roborazziOptions = roborazziOptions)
    }

    @Test
    fun emptySearchState() {
        composeRule.setContent {
            CloseLoopWalletTheme(dynamicColor = false) {
                EmptySearchState(query = "Pickle Rick")
            }
        }
        composeRule.onRoot().captureRoboImage(roborazziOptions = roborazziOptions)
    }

    @Test
    fun paginationErrorBanner() {
        val characters =
            listOf(
                CharacterUi(1, "Rick Sanchez", "Alive", "Human", null, false),
                CharacterUi(2, "Morty Smith", "Alive", "Human", null, false),
            )
        composeRule.setContent {
            CloseLoopWalletTheme(dynamicColor = false) {
                CharactersGrid(
                    characters = characters,
                    isLoadingNextPage = false,
                    lazyGridState = rememberLazyGridState(),
                    onCharacterClick = {},
                    onFavouriteClick = {},
                    paginationError = "The server is having trouble right now. Please try again later.",
                    onRetryNextPage = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage(roborazziOptions = roborazziOptions)
    }
}
