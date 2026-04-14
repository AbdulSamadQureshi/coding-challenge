package com.bonial.utils

import app.cash.turbine.test
import com.bonial.domain.model.network.response.Request
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class NetworkHelperTest {

    @Test
    fun `safeApiCall should emit Loading then Success when api call is successful`() = runBlocking {
        // Given
        val expectedData = "Success Data"
        val apiCall: suspend () -> String = { expectedData }

        // When
        val flow = safeApiCall { apiCall() }

        // Then
        flow.test {
            assertThat(awaitItem()).isInstanceOf(Request.Loading::class.java)
            val success = awaitItem() as Request.Success
            assertThat(success.data).isEqualTo(expectedData)
            awaitComplete()
        }
    }

    @Test
    fun `safeApiCall should emit Loading then Error when api call throws IOException`() = runBlocking {
        // Given
        val apiCall: suspend () -> String = { throw IOException("No Internet") }

        // When
        val flow = safeApiCall { apiCall() }

        // Then
        flow.test {
            assertThat(awaitItem()).isInstanceOf(Request.Loading::class.java)
            val error = awaitItem() as Request.Error
            assertThat(error.apiError?.code).isEqualTo("NetworkError")
            assertThat(error.apiError?.message).isEqualTo("Check your internet connection and try again.")
            awaitComplete()
        }
    }

    @Test
    fun `safeApiCall should emit Loading then Error when api call throws HttpException`() = runBlocking {
        // Given
        val response = Response.error<String>(404, "".toResponseBody())
        val apiCall: suspend () -> String = { throw HttpException(response) }

        // When
        val flow = safeApiCall { apiCall() }

        // Then
        flow.test {
            assertThat(awaitItem()).isInstanceOf(Request.Loading::class.java)
            val error = awaitItem() as Request.Error
            assertThat(error.apiError?.code).isEqualTo("404")
            awaitComplete()
        }
    }

    @Test
    fun `safeApiCall should emit Loading then Error when api call throws unknown Exception`() = runBlocking {
        // Given
        val apiCall: suspend () -> String = { throw RuntimeException("Unknown error") }

        // When
        val flow = safeApiCall { apiCall() }

        // Then
        flow.test {
            assertThat(awaitItem()).isInstanceOf(Request.Loading::class.java)
            val error = awaitItem() as Request.Error
            assertThat(error.apiError?.code).isEqualTo("Unknown")
            assertThat(error.apiError?.message).isEqualTo("Unknown error")
            awaitComplete()
        }
    }

    @Test
    fun `HttpException maps 401 to session expired message`() = runBlocking {
        val response = Response.error<String>(401, "".toResponseBody())
        val flow = safeApiCall<String> { throw HttpException(response) }
        flow.test {
            awaitItem() // Loading
            val error = awaitItem() as Request.Error
            assertThat(error.apiError?.code).isEqualTo("401")
            assertThat(error.apiError?.message).isEqualTo("Your session has expired. Please sign in again.")
            awaitComplete()
        }
    }

    @Test
    fun `HttpException maps 500 to generic server message`() = runBlocking {
        val response = Response.error<String>(500, "".toResponseBody())
        val flow = safeApiCall<String> { throw HttpException(response) }
        flow.test {
            awaitItem() // Loading
            val error = awaitItem() as Request.Error
            assertThat(error.apiError?.code).isEqualTo("500")
            assertThat(error.apiError?.message)
                .isEqualTo("The server is having trouble right now. Please try again later.")
            awaitComplete()
        }
    }
}
