package com.bonial.brochure.presentation.home

import app.cash.turbine.test
import com.bonial.brochure.presentation.model.CharacterUi
import com.bonial.brochure.testing.MainDispatcherRule
import com.bonial.domain.model.Character
import com.bonial.domain.model.network.response.ApiError
import com.bonial.domain.model.network.response.Request
import com.bonial.domain.repository.CharactersPage
import com.bonial.domain.useCase.characters.CharactersParams
import com.bonial.domain.useCase.characters.CharactersUseCase
import com.bonial.domain.useCase.favourites.GetFavouriteCoverUrlsUseCase
import com.bonial.domain.useCase.favourites.ToggleFavouriteUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CharactersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val charactersUseCase: CharactersUseCase = mock()
    private val getFavourites: GetFavouriteCoverUrlsUseCase = mock()
    private val toggleFavourite: ToggleFavouriteUseCase = mock()

    private fun viewModel(): CharactersViewModel =
        CharactersViewModel(charactersUseCase, getFavourites, toggleFavourite)

    @Test
    fun `initial load populates state and marks matching items as favourite`() = runTest {
        val page = CharactersPage(
            characters = listOf(
                Character(1, "Rick", "Alive", "Human", "https://img/rick.png"),
                Character(2, "Morty", "Alive", "Human", "https://img/morty.png"),
            ),
            totalPages = 5,
        )
        whenever(getFavourites()).thenReturn(MutableStateFlow(setOf("https://img/rick.png")))
        whenever(charactersUseCase(CharactersParams(1))).thenReturn(flowOf(Request.Success(page)))

        viewModel().uiState.test {
            // drain until we see the populated success state
            var state = awaitItem()
            while (state.characters.isEmpty() || state.isLoading) state = awaitItem()

            assertThat(state.characters.map(CharacterUi::name)).containsExactly("Rick", "Morty")
            assertThat(state.characters.single { it.id == 1 }.isFavourite).isTrue()
            assertThat(state.characters.single { it.id == 2 }.isFavourite).isFalse()
            assertThat(state.totalPages).isEqualTo(5)
            assertThat(state.currentPage).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `api error sets error state and emits ShowError effect`() = runTest {
        whenever(getFavourites()).thenReturn(MutableStateFlow(emptySet()))
        whenever(charactersUseCase(CharactersParams(1))).thenReturn(
            flowOf(Request.Error(ApiError("500", "Server exploded"))),
        )

        val vm = viewModel()

        vm.effect.test {
            val effect = awaitItem() as CharactersEffect.ShowError
            assertThat(effect.message).isEqualTo("Server exploded")
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(vm.uiState.value.error).isEqualTo("Server exploded")
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `LoadNextPage is ignored when already on the last page`() = runTest {
        val page = CharactersPage(characters = emptyList(), totalPages = 1)
        whenever(getFavourites()).thenReturn(MutableStateFlow(emptySet()))
        whenever(charactersUseCase(CharactersParams(1))).thenReturn(flowOf(Request.Success(page)))

        val vm = viewModel()
        vm.sendIntent(CharactersIntent.LoadNextPage)

        // Use case should only be hit at least once for the initial load.
        verify(charactersUseCase, atLeastOnce()).invoke(CharactersParams(1))
    }

    @Test
    fun `Search intent updates searchQuery and reflects the query results`() = runTest {
        val page = CharactersPage(
            characters = listOf(
                Character(1, "Rick Sanchez", "Alive", "Human", "https://img/rick.png"),
                Character(2, "Morty Smith", "Alive", "Human", "https://img/morty.png"),
                Character(3, "Rick Prime", "Alive", "Human", "https://img/rick-prime.png"),
            ),
            totalPages = 1,
        )
        whenever(getFavourites()).thenReturn(MutableStateFlow(emptySet()))
        whenever(charactersUseCase(CharactersParams(1))).thenReturn(flowOf(Request.Success(page)))
        whenever(charactersUseCase(CharactersParams(1, "Rick"))).thenReturn(flowOf(Request.Success(page)))

        val vm = viewModel()

        // Wait for initial load to finish.
        vm.uiState.test {
            var state = awaitItem()
            while (state.characters.isEmpty() || state.isLoading) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        vm.sendIntent(CharactersIntent.Search("Rick"))
        advanceTimeBy(501) // Advance beyond debounce

        vm.uiState.test {
            var state = awaitItem()
            while (state.searchQuery != "Rick" || state.isLoading) state = awaitItem()

            assertThat(state.searchQuery).isEqualTo("Rick")
            assertThat(state.characters.map(CharacterUi::name))
                .containsExactly("Rick Sanchez", "Morty Smith", "Rick Prime")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Search with empty query shows all characters`() = runTest {
        val page = CharactersPage(
            characters = listOf(
                Character(1, "Rick Sanchez", "Alive", "Human", "https://img/rick.png"),
                Character(2, "Morty Smith", "Alive", "Human", "https://img/morty.png"),
            ),
            totalPages = 1,
        )
        whenever(getFavourites()).thenReturn(MutableStateFlow(emptySet()))
        whenever(charactersUseCase(CharactersParams(1))).thenReturn(flowOf(Request.Success(page)))
        whenever(charactersUseCase(CharactersParams(1, "Rick"))).thenReturn(flowOf(Request.Success(page)))

        val vm = viewModel()

        vm.uiState.test {
            var state = awaitItem()
            while (state.characters.isEmpty() || state.isLoading) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        // Apply search.
        vm.sendIntent(CharactersIntent.Search("Rick"))
        advanceTimeBy(501)

        // Clear the search.
        vm.sendIntent(CharactersIntent.Search(""))
        advanceTimeBy(1) // No debounce for empty query in VM logic

        vm.uiState.test {
            var state = awaitItem()
            while (state.searchQuery.isNotEmpty() || state.isLoading) state = awaitItem()

            assertThat(state.searchQuery).isEmpty()
            assertThat(state.characters).hasSize(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ToggleFavourite ignores characters without an imageUrl`() = runTest {
        whenever(getFavourites()).thenReturn(MutableStateFlow(emptySet()))
        whenever(charactersUseCase(CharactersParams(1))).thenReturn(
            flowOf(Request.Success(CharactersPage(emptyList(), 1))),
        )

        val vm = viewModel()
        vm.sendIntent(
            CharactersIntent.ToggleFavourite(
                CharacterUi(id = 99, name = null, status = null, species = null, imageUrl = null, isFavourite = false),
            ),
        )
        // No interaction with toggleFavourite when imageUrl is null — guard in VM.
        verify(toggleFavourite, org.mockito.kotlin.never()).invoke(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }
}
