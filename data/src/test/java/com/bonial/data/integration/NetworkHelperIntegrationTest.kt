package com.bonial.data.integration

import app.cash.turbine.test
import com.bonial.domain.model.network.response.Request
import com.bonial.utils.safeApiCall
import com.bonial.utils.withRetry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

/**
 * Integration tests for [safeApiCall] and [withRetry].
 *
 * A real Retrofit service fires actual HTTP requests against a [MockWebServer]
 * so that [safeApiCall]'s Loading → Success/Error emission sequence and
 * [withRetry]'s back-off behaviour are verified end-to-end rather than with
 * mocked Throwables.
 */
class NetworkHelperIntegrationTest {

    private val mockWebServer = MockWebServer()
    private lateinit var pingService: PingService

    /** Minimal service used to trigger real HTTP calls through safeApiCall/withRetry. */
    private interface PingService {
        @GET("/ping")
        suspend fun ping(): String
    }

    @Before
    fun setUp() {
        mockWebServer.start()
        pingService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PingService::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // ── safeApiCall ───────────────────────────────────────────────────────────

    @Test
    fun `safeApiCall emits loading then success for a 200 response`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("\"ok\""))

        safeApiCall { pingService.ping() }.test {
            assertThat(awaitItem()).isInstanceOf(Request.Loading::class.java)

            val success = awaitItem() as Request.Success
            assertThat(success.data).isEqualTo("ok")

            awaitComplete()
        }
    }

    @Test
    fun `safeApiCall emits loading then error with code 404 for a not-found response`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        safeApiCall { pingService.ping() }.test {
            assertThat(awaitItem()).isInstanceOf(Request.Loading::class.java)

            val error = awaitItem() as Request.Error
            assertThat(error.apiError?.code).isEqualTo("404")
            assertThat(error.apiError?.message).isEqualTo("The requested resource was not found.")

            awaitComplete()
        }
    }

    @Test
    fun `safeApiCall emits loading then error with server message for a 500 response`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        safeApiCall { pingService.ping() }.test {
            awaitItem() // Loading

            val error = awaitItem() as Request.Error
            assertThat(error.apiError?.code).isEqualTo("500")
            assertThat(error.apiError?.message).isEqualTo(
                "The server is having trouble right now. Please try again later."
            )

            awaitComplete()
        }
    }

    @Test
    fun `safeApiCall emits loading then error with connectivity message when the server is unreachable`() =
        runBlocking {
            // Use an isolated server: start it to reserve a port, capture the URL, then shut it
            // down immediately so any request to that address results in an IOException.
            // The shared mockWebServer is left untouched so @After tearDown works normally.
            val isolatedServer = MockWebServer()
            isolatedServer.start()
            val isolatedService = Retrofit.Builder()
                .baseUrl(isolatedServer.url("/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PingService::class.java)
            isolatedServer.shutdown()

            safeApiCall { isolatedService.ping() }.test {
                awaitItem() // Loading

                val error = awaitItem() as Request.Error
                assertThat(error.apiError?.code).isEqualTo("NetworkError")
                assertThat(error.apiError?.message).isEqualTo(
                    "Check your internet connection and try again."
                )

                awaitComplete()
            }
        }

    // ── withRetry ─────────────────────────────────────────────────────────────

    @Test
    fun `withRetry retries on 429 and succeeds when the server eventually returns 200`() = runBlocking {
        // Two 429s followed by a 200.
        mockWebServer.enqueue(MockResponse().setResponseCode(429))
        mockWebServer.enqueue(MockResponse().setResponseCode(429))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("\"ok\""))

        val result = withRetry(
            maxAttempts = 5,
            delayProvider = { /* no-op in tests — don't actually sleep */ },
        ) { pingService.ping() }

        assertThat(result).isEqualTo("ok")
        assertThat(mockWebServer.requestCount).isEqualTo(3)
    }

    @Test
    fun `withRetry throws after exhausting max attempts on persistent 429 responses`() = runBlocking {
        repeat(3) { mockWebServer.enqueue(MockResponse().setResponseCode(429)) }

        val exception = runCatching {
            withRetry(
                maxAttempts = 3,
                delayProvider = {},
            ) { pingService.ping() }
        }.exceptionOrNull()

        assertThat(exception).isInstanceOf(HttpException::class.java)
        assertThat((exception as HttpException).code()).isEqualTo(429)
        assertThat(mockWebServer.requestCount).isEqualTo(3)
    }

    @Test
    fun `withRetry does not retry on non-retryable errors and throws immediately`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val exception = runCatching {
            withRetry(
                maxAttempts = 5,
                delayProvider = {},
            ) { pingService.ping() }
        }.exceptionOrNull()

        assertThat(exception).isInstanceOf(HttpException::class.java)
        assertThat((exception as HttpException).code()).isEqualTo(500)
        // Only one request should have been made — withRetry must not have retried.
        assertThat(mockWebServer.requestCount).isEqualTo(1)
    }
}
