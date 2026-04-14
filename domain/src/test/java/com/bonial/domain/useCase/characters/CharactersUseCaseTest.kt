package com.bonial.domain.useCase.characters

import app.cash.turbine.test
import com.bonial.domain.model.Character
import com.bonial.domain.model.network.response.Request
import com.bonial.domain.repository.CharactersPage
import com.bonial.domain.repository.CharactersRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CharactersUseCaseTest {

    private val repository: CharactersRepository = mock()
    private val useCase = CharactersUseCase(repository)

    @Test
    fun `invoke forwards page parameter to repository`(): Unit = runBlocking {
        val page = CharactersPage(characters = listOf(Character(1, "Rick", null, null, null)), totalPages = 1)
        whenever(repository.characters(3)).thenReturn(flowOf(Request.Success(page)))

        useCase(3).test {
            val success = awaitItem() as Request.Success
            assertThat(success.data.characters.first().name).isEqualTo("Rick")
            awaitComplete()
        }
        verify(repository).characters(3)
    }

    @Test
    fun `invoke falls back to page 1 when params is not an Int`(): Unit = runBlocking {
        val page = CharactersPage(characters = emptyList(), totalPages = 1)
        whenever(repository.characters(1)).thenReturn(flowOf(Request.Success(page)))

        useCase(params = null).test {
            awaitItem() // Success
            awaitComplete()
        }
        verify(repository).characters(1)
    }
}
