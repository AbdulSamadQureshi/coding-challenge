package com.bonial.domain.useCase.characters

import app.cash.turbine.test
import com.bonial.domain.model.Character
import com.bonial.domain.model.network.response.ApiError
import com.bonial.domain.model.network.response.Request
import com.bonial.domain.repository.CharactersPage
import com.bonial.domain.repository.CharactersRepository
import com.bonial.domain.repository.FavouritesRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GetEnrichedCharactersUseCaseTest {
    private val charactersRepository: CharactersRepository = mock()
    private val favouritesRepository: FavouritesRepository = mock()
    private val useCase = GetEnrichedCharactersUseCase(charactersRepository, favouritesRepository)

    private fun character(
        id: Int,
        imageUrl: String? = "https://img/$id.png",
    ) = Character(id, "Char$id", "Alive", "Human", imageUrl)

    private fun page(
        vararg chars: Character,
        totalPages: Int = 1,
    ) = CharactersPage(characters = chars.toList(), totalPages = totalPages)

    // ─── favourite enrichment ─────────────────────────────────────────────────

    @Test
    fun `character is shown as favourite when its image matches a saved favourite`() =
        runBlocking {
            val rick = character(1, "https://img/rick.png")
            whenever(charactersRepository.characters(1, null))
                .thenReturn(flowOf(Request.Success(page(rick))))
            whenever(favouritesRepository.getFavouriteCoverUrls())
                .thenReturn(flowOf(setOf("https://img/rick.png")))

            useCase(CharactersParams(1)).test {
                val result = (awaitItem() as Request.Success).data
                assertThat(result.characters.single().isFavourite).isTrue()
                awaitComplete()
            }
        }

    @Test
    fun `character is shown as not favourite when its image is not in the saved favourites`() =
        runBlocking {
            val morty = character(2, "https://img/morty.png")
            whenever(charactersRepository.characters(1, null))
                .thenReturn(flowOf(Request.Success(page(morty))))
            whenever(favouritesRepository.getFavouriteCoverUrls())
                .thenReturn(flowOf(setOf("https://img/rick.png")))

            useCase(CharactersParams(1)).test {
                val result = (awaitItem() as Request.Success).data
                assertThat(result.characters.single().isFavourite).isFalse()
                awaitComplete()
            }
        }

    @Test
    fun `character without an image is never shown as favourite`() =
        runBlocking {
            val noImage = character(3, imageUrl = null)
            whenever(charactersRepository.characters(1, null))
                .thenReturn(flowOf(Request.Success(page(noImage))))
            whenever(favouritesRepository.getFavouriteCoverUrls())
                .thenReturn(flowOf(setOf("https://img/rick.png", "https://img/morty.png")))

            useCase(CharactersParams(1)).test {
                val result = (awaitItem() as Request.Success).data
                assertThat(result.characters.single().isFavourite).isFalse()
                awaitComplete()
            }
        }

    @Test
    fun `each character in the list gets its own correct favourite status`() =
        runBlocking {
            val rick = character(1, "https://img/rick.png")
            val morty = character(2, "https://img/morty.png")
            val summer = character(3, "https://img/summer.png")
            whenever(charactersRepository.characters(1, null))
                .thenReturn(flowOf(Request.Success(page(rick, morty, summer))))
            whenever(favouritesRepository.getFavouriteCoverUrls())
                .thenReturn(flowOf(setOf("https://img/rick.png", "https://img/summer.png")))

            useCase(CharactersParams(1)).test {
                val chars = (awaitItem() as Request.Success).data.characters
                assertThat(chars.find { it.id == 1 }?.isFavourite).isTrue()
                assertThat(chars.find { it.id == 2 }?.isFavourite).isFalse()
                assertThat(chars.find { it.id == 3 }?.isFavourite).isTrue()
                awaitComplete()
            }
        }

    // ─── blank / null name sanitisation ──────────────────────────────────────

    @Test
    fun `searching with only spaces is treated as no search term`(): Unit =
        runBlocking {
            whenever(charactersRepository.characters(1, null))
                .thenReturn(flowOf(Request.Success(page())))
            whenever(favouritesRepository.getFavouriteCoverUrls())
                .thenReturn(flowOf(emptySet()))

            useCase(CharactersParams(1, name = "   ")).test {
                awaitItem()
                awaitComplete()
            }
            // Should call with null, not the blank string
            verify(charactersRepository).characters(1, null)
        }

    @Test
    fun `searching with a name passes it to the repository as-is`(): Unit =
        runBlocking {
            whenever(charactersRepository.characters(1, "Rick"))
                .thenReturn(flowOf(Request.Success(page(character(1)))))
            whenever(favouritesRepository.getFavouriteCoverUrls())
                .thenReturn(flowOf(emptySet()))

            useCase(CharactersParams(1, name = "Rick")).test {
                awaitItem()
                awaitComplete()
            }
            verify(charactersRepository).characters(1, "Rick")
        }

    // ─── pagination ───────────────────────────────────────────────────────────

    @Test
    fun `total page count from the server is passed through correctly`() =
        runBlocking {
            whenever(charactersRepository.characters(2, null))
                .thenReturn(flowOf(Request.Success(page(character(1), totalPages = 42))))
            whenever(favouritesRepository.getFavouriteCoverUrls())
                .thenReturn(flowOf(emptySet()))

            useCase(CharactersParams(2)).test {
                val result = (awaitItem() as Request.Success).data
                assertThat(result.totalPages).isEqualTo(42)
                awaitComplete()
            }
        }

    // ─── error passthrough ────────────────────────────────────────────────────

    @Test
    fun `server error is passed through to the caller unchanged`() =
        runBlocking {
            whenever(charactersRepository.characters(1, null))
                .thenReturn(flowOf(Request.Error(ApiError("500", "Server error"))))
            whenever(favouritesRepository.getFavouriteCoverUrls())
                .thenReturn(flowOf(emptySet()))

            useCase(CharactersParams(1)).test {
                val error = awaitItem() as Request.Error
                assertThat(error.apiError?.code).isEqualTo("500")
                awaitComplete()
            }
        }

    // ─── reactivity ───────────────────────────────────────────────────────────

    @Test
    fun `character favourite status updates automatically when favourites change`() =
        runBlocking {
            val rick = character(1, "https://img/rick.png")
            whenever(charactersRepository.characters(1, null))
                .thenReturn(flowOf(Request.Success(page(rick))))

            val favouritesFlow = MutableStateFlow(emptySet<String>())
            whenever(favouritesRepository.getFavouriteCoverUrls()).thenReturn(favouritesFlow)

            useCase(CharactersParams(1)).test {
                // First emission: not a favourite
                val first = (awaitItem() as Request.Success).data.characters.single()
                assertThat(first.isFavourite).isFalse()

                // Favourites updated — should re-emit
                favouritesFlow.value = setOf("https://img/rick.png")
                val second = (awaitItem() as Request.Success).data.characters.single()
                assertThat(second.isFavourite).isTrue()

                cancelAndIgnoreRemainingEvents()
            }
        }
}
