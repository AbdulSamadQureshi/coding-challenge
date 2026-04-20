package com.bonial.domain.useCase.characters

import app.cash.turbine.test
import com.bonial.domain.model.CharacterDetail
import com.bonial.domain.model.network.response.ApiError
import com.bonial.domain.model.network.response.Request
import com.bonial.domain.repository.CharactersRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CharacterDetailUseCaseTest {
    private val repository: CharactersRepository = mock()
    private val useCase = CharacterDetailUseCase(repository)

    @Test
    fun `requesting a character by id fetches it from the repository`(): Unit =
        runBlocking {
            val detail = CharacterDetail(5, "Beth", "Alive", "Human", "Female", "Earth", "Home", null)
            whenever(repository.character(5)).thenReturn(flowOf(Request.Success(detail)))

            useCase(5).test {
                val success = awaitItem() as Request.Success
                assertThat(success.data.id).isEqualTo(5)
                awaitComplete()
            }
            verify(repository).character(5)
        }

    @Test
    fun `repository error is passed through to the caller`(): Unit =
        runBlocking {
            whenever(repository.character(99)).thenReturn(
                flowOf(Request.Error(ApiError("404", "Not found."))),
            )

            useCase(99).test {
                val error = awaitItem() as Request.Error
                assertThat(error.apiError?.code).isEqualTo("404")
                awaitComplete()
            }
        }
}
