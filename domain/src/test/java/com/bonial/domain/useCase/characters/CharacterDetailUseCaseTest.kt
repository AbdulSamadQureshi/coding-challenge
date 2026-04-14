package com.bonial.domain.useCase.characters

import app.cash.turbine.test
import com.bonial.domain.model.CharacterDetail
import com.bonial.domain.model.network.response.Request
import com.bonial.domain.repository.CharactersRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class CharacterDetailUseCaseTest {

    private val repository: CharactersRepository = mock()
    private val useCase = CharacterDetailUseCase(repository)

    @Test
    fun `invoke delegates to repository when id is a valid Int`(): Unit = runBlocking {
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
    fun `invoke emits InvalidId error without calling repository when id is missing`(): Unit = runBlocking {
        useCase(params = null).test {
            val error = awaitItem() as Request.Error
            assertThat(error.apiError?.code).isEqualTo("InvalidId")
            assertThat(error.apiError?.message).isEqualTo("Character id is missing or invalid.")
            awaitComplete()
        }
        verifyNoInteractions(repository)
    }

    @Test
    fun `invoke emits InvalidId error when params is wrong type`(): Unit = runBlocking {
        useCase(params = "not-an-int").test {
            val error = awaitItem() as Request.Error
            assertThat(error.apiError?.code).isEqualTo("InvalidId")
            awaitComplete()
        }
        verifyNoInteractions(repository)
    }
}
