package com.bonial.brochure.presentation.home

import app.cash.turbine.test
import com.bonial.brochure.presentation.model.CharacterUi
import com.bonial.brochure.testing.MainDispatcherRule
import com.bonial.domain.model.CharacterWithFavourite
import com.bonial.domain.model.CharactersWithFavouritePage
import com.bonial.domain.model.network.response.ApiError
import com.bonial.domain.model.network.response.Request
import com.bonial.domain.useCase.characters.CharactersParams
import com.bonial.domain.useCase.characters.GetEnrichedCharactersUseCase
import com.bonial.domain.useCase.favourites.ToggleFavouriteUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CharactersViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getEnrichedCharactersUseCase: GetEnrichedCharactersUseCase = mock()
    private val toggleFavourite: ToggleFavouriteUseCase = mock()

    // ─── helpers ──────────────────────────────────────────────────────────────

    private fun page(
        vararg characters: CharacterWithFavourite,
        totalPages: Int = 1,
    ) = CharactersWithFavouritePage(characters = characters.toList(), totalPages = totalPages)

    private fun character(
        id: Int,
        name: String = "Char$id",
    ) = CharacterWithFavourite(id, name, "Alive", "Human", "https://img/$id.png", false)

    /**
     * Stubs both CharactersParams(1) and CharactersParams(1, "") to return [result].
     * Must be called from within a coroutine (e.g. runTest) because invoke() is suspend.
     */
    private suspend fun stubInitialLoad(result: CharactersWithFavouritePage) {
        whenever(getEnrichedCharactersUseCase(CharactersParams(1))).thenReturn(flowOf(Request.Success(result)))
        whenever(getEnrichedCharactersUseCase(CharactersParams(1, ""))).thenReturn(flowOf(Request.Success(result)))
    }

    private fun viewModel(): CharactersViewModel = CharactersViewModel(getEnrichedCharactersUseCase, toggleFavourite)

    // ─── initial load ─────────────────────────────────────────────────────────

    @Test
    fun `initial load populates state and marks matching items as favourite`() =
        runTest {
            val rick = CharacterWithFavourite(1, "Rick", "Alive", "Human", "https://img/rick.png", true)
            val morty = CharacterWithFavourite(2, "Morty", "Alive", "Human", "https://img/morty.png", false)
            stubInitialLoad(page(rick, morty, totalPages = 5))

            viewModel().uiState.test {
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
    fun `api error sets error state and clears loading`() =
        runTest {
            val errorFlow = flowOf(Request.Error(ApiError("500", "Server exploded")))
            whenever(getEnrichedCharactersUseCase(CharactersParams(1))).thenReturn(errorFlow)
            whenever(getEnrichedCharactersUseCase(CharactersParams(1, ""))).thenReturn(errorFlow)

            val vm = viewModel()

            // Full-screen errors go into state.error (persistent), not into the effect channel
            // (which is reserved for one-shot events like share sheets and toasts).
            vm.uiState.test {
                var state = awaitItem()
                while (state.isLoading || state.isInitialLoading) state = awaitItem()

                assertThat(state.error).isEqualTo("Server exploded")
                assertThat(state.isLoading).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ─── search / debounce ────────────────────────────────────────────────────

    @Test
    fun `Search intent does NOT fire the API before 1 000 ms`() =
        runTest {
            stubInitialLoad(page())
            whenever(getEnrichedCharactersUseCase(CharactersParams(1, "Rick")))
                .thenReturn(flowOf(Request.Success(page(character(1, "Rick")))))

            val vm = viewModel()
            vm.sendIntent(CharactersIntent.Search("Rick"))
            advanceTimeBy(999) // Just under the debounce threshold — must NOT have fired yet.

            verify(getEnrichedCharactersUseCase, never()).invoke(CharactersParams(1, "Rick"))
        }

    @Test
    fun `Search intent fires the API after 1 000 ms debounce`() =
        runTest {
            stubInitialLoad(page())
            whenever(getEnrichedCharactersUseCase(CharactersParams(1, "Rick")))
                .thenReturn(flowOf(Request.Success(page(character(1, "Rick Sanchez")))))

            val vm = viewModel()
            vm.sendIntent(CharactersIntent.Search("Rick"))
            advanceTimeBy(1_001)

            vm.uiState.test {
                var state = awaitItem()
                while (state.searchQuery != "Rick" || state.isLoading) state = awaitItem()

                assertThat(state.searchQuery).isEqualTo("Rick")
                assertThat(state.characters.map(CharacterUi::name)).containsExactly("Rick Sanchez")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Search with empty query fires immediately without debounce`() =
        runTest {
            val fullPage = page(character(1, "Rick"), character(2, "Morty"))
            stubInitialLoad(fullPage)
            whenever(getEnrichedCharactersUseCase(CharactersParams(1, "Rick")))
                .thenReturn(flowOf(Request.Success(page(character(1, "Rick")))))

            val vm = viewModel()

            // Wait for initial load.
            vm.uiState.test {
                var state = awaitItem()
                while (state.characters.isEmpty() || state.isLoading) state = awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            // Apply search and wait for debounce.
            vm.sendIntent(CharactersIntent.Search("Rick"))
            advanceTimeBy(1_001)

            // Clear search — should fire immediately (0 ms debounce).
            vm.sendIntent(CharactersIntent.Search(""))
            advanceTimeBy(1) // tiny tick to let the coroutine run

            vm.uiState.test {
                var state = awaitItem()
                while (state.searchQuery.isNotEmpty() || state.isLoading) state = awaitItem()

                assertThat(state.searchQuery).isEmpty()
                assertThat(state.characters).hasSize(2)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ─── retry ────────────────────────────────────────────────────────────────

    @Test
    fun `LoadCharacters retry fires immediately without debounce`() =
        runTest {
            val errorFlow = flowOf(Request.Error(ApiError("500", "oops")))
            whenever(getEnrichedCharactersUseCase(CharactersParams(1))).thenReturn(errorFlow)
            whenever(getEnrichedCharactersUseCase(CharactersParams(1, ""))).thenReturn(errorFlow)

            val vm = viewModel()

            // Let the initial (failing) load settle.
            vm.uiState.test {
                var state = awaitItem()
                while (state.isLoading || state.isInitialLoading) state = awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            // Stub a successful retry response.
            whenever(getEnrichedCharactersUseCase(CharactersParams(1, "")))
                .thenReturn(flowOf(Request.Success(page(character(1)))))

            vm.sendIntent(CharactersIntent.LoadCharacters)
            advanceTimeBy(1) // Should fire with 0 ms delay (gen > 0).

            vm.uiState.test {
                var state = awaitItem()
                while (state.characters.isEmpty() || state.isLoading) state = awaitItem()

                assertThat(state.characters).hasSize(1)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ─── pagination ───────────────────────────────────────────────────────────

    @Test
    fun `LoadNextPage appends characters from the next page`() =
        runTest {
            val firstPage = page(character(1, "Rick"), character(2, "Morty"), totalPages = 2)
            stubInitialLoad(firstPage)
            whenever(getEnrichedCharactersUseCase(CharactersParams(2, "")))
                .thenReturn(flowOf(Request.Success(page(character(3, "Summer"), totalPages = 2))))

            val vm = viewModel()

            // Wait for page 1.
            vm.uiState.test {
                var state = awaitItem()
                while (state.characters.isEmpty() || state.isLoading) state = awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            vm.sendIntent(CharactersIntent.LoadNextPage)

            vm.uiState.test {
                var state = awaitItem()
                while (state.characters.size < 3 || state.isLoadingNextPage) state = awaitItem()

                assertThat(state.characters.map(CharacterUi::name))
                    .containsExactly("Rick", "Morty", "Summer")
                    .inOrder()
                assertThat(state.currentPage).isEqualTo(2)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `LoadNextPage is ignored when already on the last page`() =
        runTest {
            stubInitialLoad(page(totalPages = 1))

            val vm = viewModel()
            vm.sendIntent(CharactersIntent.LoadNextPage)

            verify(getEnrichedCharactersUseCase, atLeastOnce()).invoke(CharactersParams(1, ""))
            // Page 2 must never be requested.
            verify(getEnrichedCharactersUseCase, never()).invoke(CharactersParams(2, ""))
        }

    @Test
    fun `LoadNextPage is ignored while an initial load is still in flight`() =
        runTest {
            // Emit Loading but never Success so isLoading stays true.
            val neverCompletes = flow<Request<CharactersWithFavouritePage>> { emit(Request.Loading) }
            whenever(getEnrichedCharactersUseCase(CharactersParams(1))).thenReturn(neverCompletes)
            whenever(getEnrichedCharactersUseCase(CharactersParams(1, ""))).thenReturn(neverCompletes)

            val vm = viewModel()

            // Confirm loading state.
            vm.uiState.test {
                var state = awaitItem()
                while (!state.isLoading) state = awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            vm.sendIntent(CharactersIntent.LoadNextPage)

            verify(getEnrichedCharactersUseCase, never()).invoke(CharactersParams(2, ""))
        }

    @Test
    fun `Search cancels in-flight pagination so stale results do not arrive`() =
        runTest {
            val firstPage = page(character(1, "Rick"), character(2, "Morty"), totalPages = 3)
            stubInitialLoad(firstPage)

            // Page 2 returns a slow flow that never completes.
            val slowPage2 = flow<Request<CharactersWithFavouritePage>> { emit(Request.Loading) }
            whenever(getEnrichedCharactersUseCase(CharactersParams(2, ""))).thenReturn(slowPage2)

            val searchPage = page(character(10, "Summer"))
            whenever(getEnrichedCharactersUseCase(CharactersParams(1, "Summer")))
                .thenReturn(flowOf(Request.Success(searchPage)))

            val vm = viewModel()

            // Wait for initial load.
            vm.uiState.test {
                var state = awaitItem()
                while (state.characters.isEmpty() || state.isLoading) state = awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            // Kick off pagination (will hang on Loading).
            vm.sendIntent(CharactersIntent.LoadNextPage)

            // Immediately search — must cancel the pagination job.
            vm.sendIntent(CharactersIntent.Search("Summer"))
            advanceTimeBy(1_001)

            vm.uiState.test {
                var state = awaitItem()
                while (state.searchQuery != "Summer" || state.isLoading || state.isLoadingNextPage) state = awaitItem()

                // Only the search result must be present; page-2 characters must not appear.
                assertThat(state.characters.map(CharacterUi::name)).containsExactly("Summer")
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ─── error state distinctions ─────────────────────────────────────────────

    @Test
    fun `404 response is treated as empty results, not an error`() =
        runTest {
            val notFoundFlow = flowOf(Request.Error(ApiError("404", "Not found.")))
            whenever(getEnrichedCharactersUseCase(CharactersParams(1))).thenReturn(notFoundFlow)
            whenever(getEnrichedCharactersUseCase(CharactersParams(1, ""))).thenReturn(notFoundFlow)

            val vm = viewModel()

            vm.uiState.test {
                var state = awaitItem()
                while (state.isLoading || state.isInitialLoading) state = awaitItem()

                // 404 must never populate the error field (it means "no results").
                assertThat(state.error).isNull()
                assertThat(state.characters).isEmpty()
                assertThat(state.isInitialLoading).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `pagination failure sets paginationError, preserves loaded list, and leaves error null`() =
        runTest {
            val firstPage = page(character(1, "Rick"), character(2, "Morty"), totalPages = 2)
            stubInitialLoad(firstPage)
            whenever(getEnrichedCharactersUseCase(CharactersParams(2, "")))
                .thenReturn(flowOf(Request.Error(ApiError("500", "Server error"))))

            val vm = viewModel()

            // Wait for page 1.
            vm.uiState.test {
                var state = awaitItem()
                while (state.characters.isEmpty() || state.isLoading) state = awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            vm.sendIntent(CharactersIntent.LoadNextPage)

            vm.uiState.test {
                var state = awaitItem()
                while (state.isLoadingNextPage || (state.paginationError == null && state.characters.size < 2)) {
                    state = awaitItem()
                }

                // Pagination failure must NOT clear the existing list.
                assertThat(state.characters).hasSize(2)
                // Failure goes to paginationError, not to the full-screen error field.
                assertThat(state.paginationError).isNotNull()
                assertThat(state.error).isNull()
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ─── favourites ───────────────────────────────────────────────────────────

    @Test
    fun `ToggleFavourite ignores characters without an imageUrl`() =
        runTest {
            stubInitialLoad(page())

            val vm = viewModel()
            vm.sendIntent(
                CharactersIntent.ToggleFavourite(
                    CharacterUi(id = 99, name = null, status = null, species = null, imageUrl = null, isFavourite = false),
                ),
            )
            verify(toggleFavourite, never()).invoke(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        }
}
