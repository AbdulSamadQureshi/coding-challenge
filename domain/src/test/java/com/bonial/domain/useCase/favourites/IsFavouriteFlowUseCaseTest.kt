package com.bonial.domain.useCase.favourites

import app.cash.turbine.test
import com.bonial.domain.repository.FavouritesRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class IsFavouriteFlowUseCaseTest {
    private val repository: FavouritesRepository = mock()
    private val useCase = IsFavouriteFlowUseCase(repository)

    @Test
    fun `invoke delegates to repository isFavouriteFlow with exact url`() =
        runBlocking {
            val url = "https://example.com/avatar.png"
            val flow = MutableStateFlow(false)
            whenever(repository.isFavouriteFlow(url)).thenReturn(flow)

            // Calling useCase(url) triggers the delegation — capture the result so we can
            // verify before collecting. test{} is the last expression (returns Unit).
            val resultFlow = useCase(url)
            verify(repository).isFavouriteFlow(url)
            resultFlow.test {
                assertThat(awaitItem()).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `flow emits updated value when favourite state changes`() =
        runBlocking {
            val url = "https://example.com/avatar.png"
            val flow = MutableStateFlow(false)
            whenever(repository.isFavouriteFlow(url)).thenReturn(flow)

            useCase(url).test {
                assertThat(awaitItem()).isFalse()

                flow.value = true
                assertThat(awaitItem()).isTrue()

                cancelAndIgnoreRemainingEvents()
            }
        }
}
