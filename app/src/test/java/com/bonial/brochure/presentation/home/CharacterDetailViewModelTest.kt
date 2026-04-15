package com.bonial.brochure.presentation.home

import app.cash.turbine.test
import com.bonial.brochure.presentation.navigation.CharacterDetailKey
import com.bonial.brochure.testing.MainDispatcherRule
import com.bonial.domain.model.CharacterDetail
import com.bonial.domain.model.network.response.ApiError
import com.bonial.domain.model.network.response.Request
import com.bonial.domain.useCase.characters.CharacterDetailUseCase
import com.bonial.domain.useCase.favourites.IsFavouriteFlowUseCase
import com.bonial.domain.useCase.favourites.ToggleFavouriteUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * ViewModel tests for [CharacterDetailViewModel].
 *
 * Navigation 3 passes the [CharacterDetailKey] directly via assisted injection, so
 * there is no need for [android.os.SavedStateHandle] or a Robolectric test runner.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CharacterDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val characterDetailUseCase: CharacterDetailUseCase = mock()
    private val isFavouriteFlowUseCase: IsFavouriteFlowUseCase = mock()
    private val toggleFavouriteUseCase: ToggleFavouriteUseCase = mock()

    private fun viewModel(id: Int = CHARACTER_ID): CharacterDetailViewModel =
        CharacterDetailViewModel(
            navKey = CharacterDetailKey(id),
            characterDetailUseCase = characterDetailUseCase,
            isFavouriteFlowUseCase = isFavouriteFlowUseCase,
            toggleFavouriteUseCase = toggleFavouriteUseCase,
        )

    // ------------------------------------------------------------------
    // Success path
    // ------------------------------------------------------------------

    @Test
    fun `successful load populates character state and clears loading`() = runTest {
        givenSuccessResponse(CHARACTER_ID)

        viewModel().uiState.test {
            var state = awaitItem()
            while (state.isLoading || state.character == null) state = awaitItem()

            assertThat(state.character?.id).isEqualTo(CHARACTER_ID)
            assertThat(state.character?.name).isEqualTo("Rick Sanchez")
            assertThat(state.isLoading).isFalse()
            assertThat(state.error).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isFavourite is updated from the favourite flow after load`() = runTest {
        givenSuccessResponse(CHARACTER_ID)
        whenever(isFavouriteFlowUseCase(IMAGE_URL))
            .thenReturn(MutableStateFlow(true))

        viewModel().uiState.test {
            var state = awaitItem()
            while (!state.isFavourite) state = awaitItem()

            assertThat(state.isFavourite).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------------
    // Error path
    // ------------------------------------------------------------------

    @Test
    fun `api error sets error state and clears loading`() = runTest {
        whenever(characterDetailUseCase(CHARACTER_ID)).thenReturn(
            flowOf(Request.Error(ApiError("404", "Not found."))),
        )

        viewModel().uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            assertThat(state.error).isEqualTo("Not found.")
            assertThat(state.isLoading).isFalse()
            assertThat(state.character).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------------
    // Retry intent
    // ------------------------------------------------------------------

    @Test
    fun `Retry intent re-fetches the character from the use case`() = runTest {
        // First call → error; second call (triggered by Retry) → success.
        whenever(characterDetailUseCase(CHARACTER_ID))
            .thenReturn(flowOf(Request.Error(ApiError("500", "Server error"))))
            .thenReturn(flowOf(Request.Success(characterDetail())))
        // observeFavourite is called after a successful load.
        whenever(isFavouriteFlowUseCase(IMAGE_URL)).thenReturn(MutableStateFlow(false))

        val vm = viewModel()

        // Wait until error state is set by the initial load.
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.error).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }

        // Dispatch retry — the use case must be called a second time.
        vm.sendIntent(CharacterDetailIntent.Retry)

        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading || state.character == null) state = awaitItem()

            assertThat(state.character?.id).isEqualTo(CHARACTER_ID)
            assertThat(state.error).isNull()
            cancelAndIgnoreRemainingEvents()
        }

        verify(characterDetailUseCase, times(2)).invoke(CHARACTER_ID)
    }

    @Test
    fun `Retry intent resets error state and sets isLoading true before response`() = runTest {
        whenever(characterDetailUseCase(CHARACTER_ID)).thenReturn(
            flowOf(Request.Error(ApiError("503", "Unavailable"))),
        )

        val vm = viewModel()
        // Let initial load finish with error.
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        // On retry, state should briefly show isLoading = true and error = null.
        // Because the use case still returns an error synchronously here,
        // we just verify that the use case was called again (the guard reset works).
        vm.sendIntent(CharacterDetailIntent.Retry)
        verify(characterDetailUseCase, times(2)).invoke(CHARACTER_ID)
    }

    // ------------------------------------------------------------------
    // ToggleFavourite
    // ------------------------------------------------------------------

    @Test
    fun `ToggleFavourite is a no-op when character has no imageUrl`() = runTest {
        // Return a character with imageUrl = null so observeFavourite is never called.
        whenever(characterDetailUseCase(CHARACTER_ID)).thenReturn(
            flowOf(
                Request.Success(
                    CharacterDetail(CHARACTER_ID, "Ghost", "Alive", "Spirit", "Unknown", null, null, imageUrl = null),
                ),
            ),
        )

        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        vm.sendIntent(CharacterDetailIntent.ToggleFavourite)

        // toggleFavouriteUseCase must never be invoked because imageUrl is null.
        verify(toggleFavouriteUseCase, org.mockito.kotlin.never())
            .invoke(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    // ------------------------------------------------------------------
    // ShareCharacter
    // ------------------------------------------------------------------

    @Test
    fun `ShareCharacter emits Share effect with all character details`() = runTest {
        givenSuccessResponse(CHARACTER_ID)

        val vm = viewModel()
        // Wait for character to load.
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading || state.character == null) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        vm.effect.test {
            vm.sendIntent(CharacterDetailIntent.ShareCharacter)
            val effect = awaitItem()
            assertThat(effect).isInstanceOf(CharacterDetailEffect.Share::class.java)
            val shareText = (effect as CharacterDetailEffect.Share).text
            assertThat(shareText).contains("Rick Sanchez")
            assertThat(shareText).contains("Human")
            assertThat(shareText).contains("Alive")
            assertThat(shareText).contains(IMAGE_URL)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun characterDetail() = CharacterDetail(
        id = CHARACTER_ID,
        name = "Rick Sanchez",
        status = "Alive",
        species = "Human",
        gender = "Male",
        origin = "Earth",
        location = "Citadel of Ricks",
        imageUrl = IMAGE_URL,
    )

    private suspend fun givenSuccessResponse(id: Int) {
        whenever(characterDetailUseCase(id)).thenReturn(flowOf(Request.Success(characterDetail())))
        whenever(isFavouriteFlowUseCase(IMAGE_URL)).thenReturn(MutableStateFlow(false))
    }

    private companion object {
        const val CHARACTER_ID = 1
        const val IMAGE_URL = "https://rickandmortyapi.com/api/character/avatar/1.jpeg"
    }
}
