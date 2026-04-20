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
    fun `requesting a page number passes it correctly to the repository`(): Unit =
        runBlocking {
            val page =
                CharactersPage(
                    characters = listOf(Character(1, "Rick", null, null, null)),
                    totalPages = 1,
                )
            whenever(repository.characters(3, null)).thenReturn(flowOf(Request.Success(page)))

            useCase(CharactersParams(3)).test {
                val success = awaitItem() as Request.Success
                assertThat(
                    success.data.characters
                        .first()
                        .name,
                ).isEqualTo("Rick")
                awaitComplete()
            }
            verify(repository).characters(3, null)
        }

    @Test
    fun `repository error is passed through to the caller`(): Unit =
        runBlocking {
            whenever(repository.characters(1, null)).thenReturn(
                flowOf(
                    Request.Error(
                        com.bonial.domain.model.network.response
                            .ApiError("NetworkError", "No connection."),
                    ),
                ),
            )

            useCase(CharactersParams(1)).test {
                val error = awaitItem() as Request.Error
                assertThat(error.apiError?.code).isEqualTo("NetworkError")
                awaitComplete()
            }
        }
}
