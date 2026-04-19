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
    fun `safeApiCall should emit Loading then Success when api call is successful`() =
        runBlocking {
            val flow = safeApiCall { "Success Data" }
            flow.test {
                assertThat(awaitItem()).isInstanceOf(Request.Loading::class.java)
                assertThat((awaitItem() as Request.Success).data).isEqualTo("Success Data")
                awaitComplete()
            }
        }

    @Test
    fun `safeApiCall should emit Loading then Error when api call throws IOException`() =
        runBlocking {
            val flow = safeApiCall<String> { throw IOException("No Internet") }
            flow.test {
                assertThat(awaitItem()).isInstanceOf(Request.Loading::class.java)
                val error = awaitItem() as Request.Error
                assertThat(error.apiError?.code).isEqualTo("NetworkError")
                assertThat(error.apiError?.message).isEqualTo("Check your internet connection and try again.")
                awaitComplete()
            }
        }

    @Test
    fun `safeApiCall should emit Loading then Error when api call throws HttpException`() =
        runBlocking {
            val response = Response.error<String>(404, "".toResponseBody())
            val flow = safeApiCall<String> { throw HttpException(response) }
            flow.test {
                assertThat(awaitItem()).isInstanceOf(Request.Loading::class.java)
                assertThat((awaitItem() as Request.Error).apiError?.code).isEqualTo("404")
                awaitComplete()
            }
        }

    @Test
    @Suppress("TooGenericExceptionThrown")
    fun `safeApiCall should emit Loading then Error when api call throws unknown Exception`() =
        runBlocking {
            val flow = safeApiCall<String> { throw RuntimeException("Unknown error") }
            flow.test {
                assertThat(awaitItem()).isInstanceOf(Request.Loading::class.java)
                val error = awaitItem() as Request.Error
                assertThat(error.apiError?.code).isEqualTo("Unknown")
                assertThat(error.apiError?.message).isEqualTo("Unknown error")
                awaitComplete()
            }
        }

    @Test
    fun `HttpException maps 401 to session expired message`() =
        runBlocking {
            val flow = safeApiCall<String> { throw HttpException(Response.error<String>(401, "".toResponseBody())) }
            flow.test {
                awaitItem() // Loading
                val error = awaitItem() as Request.Error
                assertThat(error.apiError?.code).isEqualTo("401")
                assertThat(error.apiError?.message).isEqualTo("Your session has expired. Please sign in again.")
                awaitComplete()
            }
        }

    @Test
    fun `HttpException maps 500 to generic server message`() =
        runBlocking {
            val flow = safeApiCall<String> { throw HttpException(Response.error<String>(500, "".toResponseBody())) }
            flow.test {
                awaitItem() // Loading
                val error = awaitItem() as Request.Error
                assertThat(error.apiError?.code).isEqualTo("500")
                assertThat(error.apiError?.message)
                    .isEqualTo("The server is having trouble right now. Please try again later.")
                awaitComplete()
            }
        }

    @Test
    fun `withRetry succeeds on the first attempt without retrying`() =
        runBlocking {
            var callCount = 0
            val result =
                withRetry(delayProvider = {}) {
                    callCount++
                    "ok"
                }
            assertThat(result).isEqualTo("ok")
            assertThat(callCount).isEqualTo(1)
        }

    @Test
    fun `withRetry retries on 429 and returns success when a later attempt succeeds`() =
        runBlocking {
            val r429 = Response.error<String>(429, "".toResponseBody())
            var callCount = 0
            val result =
                withRetry(maxAttempts = 3, delayProvider = {}) {
                    callCount++
                    if (callCount < 3) throw HttpException(r429)
                    "recovered"
                }
            assertThat(result).isEqualTo("recovered")
            assertThat(callCount).isEqualTo(3)
        }

    @Test
    fun `withRetry exhausts all attempts on persistent 429 and rethrows`() =
        runBlocking {
            val r429 = Response.error<String>(429, "".toResponseBody())
            var callCount = 0
            var caught: Throwable? = null
            try {
                withRetry(maxAttempts = 3, delayProvider = {}) {
                    callCount++
                    throw HttpException(r429)
                }
            } catch (t: Throwable) {
                caught = t
            }

            assertThat(caught).isInstanceOf(HttpException::class.java)
            assertThat((caught as HttpException).code()).isEqualTo(429)
            assertThat(callCount).isEqualTo(3)
        }

    @Test
    fun `withRetry does not retry on 404 — fails on the first attempt`() =
        runBlocking {
            val r404 = Response.error<String>(404, "".toResponseBody())
            var callCount = 0
            try {
                withRetry(maxAttempts = 3, delayProvider = {}) {
                    callCount++
                    throw HttpException(r404)
                }
            } catch (_: Throwable) {
            }

            assertThat(callCount).isEqualTo(1)
        }

    @Test
    fun `withRetry does not retry on IOException — fails immediately`() =
        runBlocking {
            var callCount = 0
            try {
                withRetry(maxAttempts = 3, delayProvider = {}) {
                    callCount++
                    throw IOException("offline")
                }
            } catch (_: Throwable) {
            }

            assertThat(callCount).isEqualTo(1)
        }

    @Test
    fun `withRetry uses a fixed delay between every attempt`() =
        runBlocking {
            val r429 = Response.error<String>(429, "".toResponseBody())
            val delays = mutableListOf<Long>()
            try {
                withRetry(maxAttempts = 3, retryDelayMs = 1_000L, delayProvider = { ms -> delays.add(ms) }) {
                    throw HttpException(r429)
                }
            } catch (_: Throwable) {
            }

            assertThat(delays).containsExactly(1_000L, 1_000L).inOrder()
        }

    @Test
    fun `isRateLimitError returns true only for HTTP 429`() {
        assertThat(HttpException(Response.error<Any>(429, "".toResponseBody())).isRateLimitError()).isTrue()
        assertThat(HttpException(Response.error<Any>(500, "".toResponseBody())).isRateLimitError()).isFalse()
        assertThat(IOException("no network").isRateLimitError()).isFalse()
    }
}
