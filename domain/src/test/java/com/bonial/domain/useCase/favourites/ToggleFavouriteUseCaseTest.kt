package com.bonial.domain.useCase.favourites

import com.bonial.domain.repository.FavouritesRepository
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

class ToggleFavouriteUseCaseTest {
    private val repository: FavouritesRepository = mock()
    private lateinit var useCase: ToggleFavouriteUseCase

    @Before
    fun setUp() {
        useCase = ToggleFavouriteUseCase(repository)
    }

    @Test
    fun `tapping favourite on an unfavourited character saves it`() =
        runBlocking {
            useCase("https://example.com/cover.jpg", isFavourite = false)
            verify(repository).addFavourite("https://example.com/cover.jpg")
            verifyNoMoreInteractions(repository)
        }

    @Test
    fun `tapping favourite on a saved character removes it`() =
        runBlocking {
            useCase("https://example.com/cover.jpg", isFavourite = true)
            verify(repository).removeFavourite("https://example.com/cover.jpg")
            verifyNoMoreInteractions(repository)
        }
}
