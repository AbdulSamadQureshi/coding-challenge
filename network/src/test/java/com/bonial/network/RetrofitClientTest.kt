package com.bonial.network

import com.bonial.core.preferences.UserPreferencesDataStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.http.GET

class RetrofitClientTest {

    private val mockWebServer = MockWebServer()
    private val dataStore: UserPreferencesDataStore = mock()

    // Minimal Retrofit service used only to trigger an actual HTTP request
    // through the OkHttp interceptor chain.
    private interface PingService {
        @GET("/ping")
        suspend fun ping(): Any
    }

    @Before
    fun setUp() {
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `Authorization header is added when token is present`() {
        whenever(dataStore.accessTokenFlow).thenReturn(flowOf("test-token-123"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val client = RetrofitClient(
            baseUrl = mockWebServer.url("/").toString(),
            enableLogging = false,
            userPreferencesDataStore = dataStore,
        )
        val service = client.retrofit.create(PingService::class.java)

        // Fire the request — we don't care about the response body, only the headers.
        runCatching { kotlinx.coroutines.runBlocking { service.ping() } }

        val recorded = mockWebServer.takeRequest()
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer test-token-123")
    }

    @Test
    fun `Authorization header is omitted when token is null`() {
        whenever(dataStore.accessTokenFlow).thenReturn(flowOf(null))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val client = RetrofitClient(
            baseUrl = mockWebServer.url("/").toString(),
            enableLogging = false,
            userPreferencesDataStore = dataStore,
        )
        val service = client.retrofit.create(PingService::class.java)

        runCatching { kotlinx.coroutines.runBlocking { service.ping() } }

        val recorded = mockWebServer.takeRequest()
        assertThat(recorded.getHeader("Authorization")).isNull()
    }

    @Test
    fun `Authorization header is omitted when token is empty`() {
        whenever(dataStore.accessTokenFlow).thenReturn(flowOf(""))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val client = RetrofitClient(
            baseUrl = mockWebServer.url("/").toString(),
            enableLogging = false,
            userPreferencesDataStore = dataStore,
        )
        val service = client.retrofit.create(PingService::class.java)

        runCatching { kotlinx.coroutines.runBlocking { service.ping() } }

        val recorded = mockWebServer.takeRequest()
        assertThat(recorded.getHeader("Authorization")).isNull()
    }

    @Test
    fun `Content-Type and Platform headers are always present`() {
        whenever(dataStore.accessTokenFlow).thenReturn(flowOf(null))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val client = RetrofitClient(
            baseUrl = mockWebServer.url("/").toString(),
            enableLogging = false,
            userPreferencesDataStore = dataStore,
        )
        val service = client.retrofit.create(PingService::class.java)

        runCatching { kotlinx.coroutines.runBlocking { service.ping() } }

        val recorded = mockWebServer.takeRequest()
        assertThat(recorded.getHeader("Content-Type")).isEqualTo("application/json")
        assertThat(recorded.getHeader("Platform")).isEqualTo("Android")
    }

    @Test
    fun `logging interceptor is set to BODY when enableLogging is true`() {
        whenever(dataStore.accessTokenFlow).thenReturn(flowOf(null))

        val client = RetrofitClient(
            baseUrl = mockWebServer.url("/").toString(),
            enableLogging = true,
            userPreferencesDataStore = dataStore,
        )

        // Reach into the OkHttpClient interceptors and verify the logging level.
        val loggingInterceptor = client.retrofit.callFactory()
            .let { it as okhttp3.OkHttpClient }
            .interceptors
            .filterIsInstance<HttpLoggingInterceptor>()
            .firstOrNull()

        assertThat(loggingInterceptor).isNotNull()
        assertThat(loggingInterceptor!!.level).isEqualTo(HttpLoggingInterceptor.Level.BODY)
    }

    @Test
    fun `logging interceptor is set to NONE when enableLogging is false`() {
        whenever(dataStore.accessTokenFlow).thenReturn(flowOf(null))

        val client = RetrofitClient(
            baseUrl = mockWebServer.url("/").toString(),
            enableLogging = false,
            userPreferencesDataStore = dataStore,
        )

        val loggingInterceptor = client.retrofit.callFactory()
            .let { it as okhttp3.OkHttpClient }
            .interceptors
            .filterIsInstance<HttpLoggingInterceptor>()
            .firstOrNull()

        assertThat(loggingInterceptor).isNotNull()
        assertThat(loggingInterceptor!!.level).isEqualTo(HttpLoggingInterceptor.Level.NONE)
    }
}
