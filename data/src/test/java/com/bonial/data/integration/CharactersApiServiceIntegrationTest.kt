package com.bonial.data.integration

import com.bonial.data.remote.service.CharactersApiService
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

/**
 * Integration tests for [CharactersApiService].
 *
 * A real Retrofit + Gson stack is wired against a [MockWebServer] so these tests
 * exercise the full HTTP → JSON → DTO pipeline without hitting the network.
 * They catch problems that pure unit tests cannot: wrong @SerializedName keys,
 * missing null-safety on optional fields, incorrect @GET/@Query/@Path annotations.
 */
class CharactersApiServiceIntegrationTest {

    private val mockWebServer = MockWebServer()
    private lateinit var apiService: CharactersApiService

    @Before
    fun setUp() {
        mockWebServer.start()
        apiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CharactersApiService::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `fetching a character page deserialises page info and character list correctly`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setBody(CHARACTERS_PAGE_JSON).setResponseCode(200))

        val response = apiService.characters(page = 1)

        assertThat(response.info?.count).isEqualTo(826)
        assertThat(response.info?.pages).isEqualTo(42)
        assertThat(response.info?.next).isEqualTo("https://rickandmortyapi.com/api/character?page=2")
        assertThat(response.info?.prev).isNull()
        assertThat(response.results).hasSize(2)

        val first = response.results!![0]
        assertThat(first.id).isEqualTo(1)
        assertThat(first.name).isEqualTo("Rick Sanchez")
        assertThat(first.status).isEqualTo("Alive")
        assertThat(first.species).isEqualTo("Human")
        assertThat(first.gender).isEqualTo("Male")
        assertThat(first.image).isEqualTo("https://rickandmortyapi.com/api/character/avatar/1.jpeg")
        assertThat(first.origin?.name).isEqualTo("Earth (C-137)")
        assertThat(first.location?.name).isEqualTo("Citadel of Ricks")
    }

    @Test
    fun `fetching a character page sends the correct page and name query parameters`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setBody(CHARACTERS_PAGE_JSON).setResponseCode(200))

        apiService.characters(page = 3, name = "Rick")

        val recorded = mockWebServer.takeRequest()
        assertThat(recorded.path).contains("page=3")
        assertThat(recorded.path).contains("name=Rick")
    }

    @Test
    fun `fetching a character page without a name omits the name query parameter`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setBody(CHARACTERS_PAGE_JSON).setResponseCode(200))

        apiService.characters(page = 1, name = null)

        val recorded = mockWebServer.takeRequest()
        assertThat(recorded.path).doesNotContain("name=")
    }

    @Test
    fun `fetching a single character by id deserialises all fields correctly`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setBody(CHARACTER_DETAIL_JSON).setResponseCode(200))

        val character = apiService.character(id = 1)

        assertThat(character.id).isEqualTo(1)
        assertThat(character.name).isEqualTo("Rick Sanchez")
        assertThat(character.status).isEqualTo("Alive")
        assertThat(character.species).isEqualTo("Human")
        assertThat(character.gender).isEqualTo("Male")
        assertThat(character.image).isEqualTo("https://rickandmortyapi.com/api/character/avatar/1.jpeg")
        assertThat(character.origin?.name).isEqualTo("Earth (C-137)")
        assertThat(character.location?.name).isEqualTo("Citadel of Ricks")
    }

    @Test
    fun `fetching a single character by id sends the id in the path`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setBody(CHARACTER_DETAIL_JSON).setResponseCode(200))

        apiService.character(id = 42)

        val recorded = mockWebServer.takeRequest()
        assertThat(recorded.path).isEqualTo("/character/42")
    }

    // ── Null-safety ───────────────────────────────────────────────────────────

    @Test
    fun `character with all optional fields absent deserialises without crashing`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setBody(CHARACTER_MINIMAL_JSON).setResponseCode(200))

        val character = apiService.character(id = 99)

        assertThat(character.id).isEqualTo(99)
        assertThat(character.name).isNull()
        assertThat(character.status).isNull()
        assertThat(character.species).isNull()
        assertThat(character.gender).isNull()
        assertThat(character.image).isNull()
        assertThat(character.origin).isNull()
        assertThat(character.location).isNull()
    }

    @Test
    fun `character page with null results array deserialises to a null results field`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setBody(CHARACTERS_NULL_RESULTS_JSON).setResponseCode(200))

        val response = apiService.characters(page = 1)

        assertThat(response.results).isNull()
    }

    // ── HTTP error codes ──────────────────────────────────────────────────────

    @Test
    fun `http 404 response throws an HttpException with code 404`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val exception = runCatching { apiService.characters(page = 1) }
            .exceptionOrNull()

        assertThat(exception).isInstanceOf(HttpException::class.java)
        assertThat((exception as HttpException).code()).isEqualTo(404)
    }

    @Test
    fun `http 500 response throws an HttpException with code 500`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val exception = runCatching { apiService.character(id = 1) }
            .exceptionOrNull()

        assertThat(exception).isInstanceOf(HttpException::class.java)
        assertThat((exception as HttpException).code()).isEqualTo(500)
    }

    // ── JSON fixtures ─────────────────────────────────────────────────────────

    companion object {
        private val CHARACTERS_PAGE_JSON = """
            {
              "info": {
                "count": 826,
                "pages": 42,
                "next": "https://rickandmortyapi.com/api/character?page=2",
                "prev": null
              },
              "results": [
                {
                  "id": 1,
                  "name": "Rick Sanchez",
                  "status": "Alive",
                  "species": "Human",
                  "gender": "Male",
                  "image": "https://rickandmortyapi.com/api/character/avatar/1.jpeg",
                  "origin": { "name": "Earth (C-137)" },
                  "location": { "name": "Citadel of Ricks" }
                },
                {
                  "id": 2,
                  "name": "Morty Smith",
                  "status": "Alive",
                  "species": "Human",
                  "gender": "Male",
                  "image": "https://rickandmortyapi.com/api/character/avatar/2.jpeg",
                  "origin": { "name": "unknown" },
                  "location": { "name": "Citadel of Ricks" }
                }
              ]
            }
        """.trimIndent()

        private val CHARACTER_DETAIL_JSON = """
            {
              "id": 1,
              "name": "Rick Sanchez",
              "status": "Alive",
              "species": "Human",
              "gender": "Male",
              "image": "https://rickandmortyapi.com/api/character/avatar/1.jpeg",
              "origin": { "name": "Earth (C-137)" },
              "location": { "name": "Citadel of Ricks" }
            }
        """.trimIndent()

        private val CHARACTER_MINIMAL_JSON = """
            { "id": 99 }
        """.trimIndent()

        private val CHARACTERS_NULL_RESULTS_JSON = """
            {
              "info": { "count": 0, "pages": 0, "next": null, "prev": null },
              "results": null
            }
        """.trimIndent()
    }
}
