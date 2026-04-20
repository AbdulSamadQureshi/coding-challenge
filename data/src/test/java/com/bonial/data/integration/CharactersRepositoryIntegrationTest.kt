package com.bonial.data.integration

import app.cash.turbine.test
import com.bonial.data.remote.service.CharactersApiService
import com.bonial.data.repository.CharactersRepositoryImpl
import com.bonial.domain.model.network.response.Request
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Integration tests for [CharactersRepositoryImpl].
 *
 * Wires a real Retrofit + Gson + [CharactersRepositoryImpl] stack against a
 * [MockWebServer]. Every test verifies the full pipeline from raw HTTP bytes
 * to the domain model that leaves the repository — including DTO-to-domain
 * mapping and error propagation through [com.bonial.utils.safeApiCall].
 *
 * Unit tests for this class mock [CharactersApiService] and therefore cannot
 * catch: wrong JSON field names, missing null-safety on optional nested objects,
 * or incorrect HTTP error mapping end-to-end.
 */
class CharactersRepositoryIntegrationTest {
    private val mockWebServer = MockWebServer()
    private lateinit var repository: CharactersRepositoryImpl

    @Before
    fun setUp() {
        mockWebServer.start()
        val apiService =
            Retrofit
                .Builder()
                .baseUrl(mockWebServer.url("/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(CharactersApiService::class.java)
        repository = CharactersRepositoryImpl(apiService)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // ── characters() ─────────────────────────────────────────────────────────

    @Test
    fun `loading a character page emits loading then the mapped domain model`() =
        runBlocking {
            mockWebServer.enqueue(MockResponse().setBody(CHARACTERS_PAGE_JSON).setResponseCode(200))

            repository.characters(page = 1).test {
                assertThat(awaitItem()).isInstanceOf(Request.Loading::class.java)

                val success = awaitItem() as Request.Success
                assertThat(success.data.totalPages).isEqualTo(42)
                assertThat(success.data.characters).hasSize(2)

                val rick = success.data.characters[0]
                assertThat(rick.id).isEqualTo(1)
                assertThat(rick.name).isEqualTo("Rick Sanchez")
                assertThat(rick.status).isEqualTo("Alive")
                assertThat(rick.species).isEqualTo("Human")
                assertThat(rick.imageUrl).isEqualTo("https://rickandmortyapi.com/api/character/avatar/1.jpeg")

                awaitComplete()
            }
        }

    @Test
    fun `loading a page with null results produces an empty character list`() =
        runBlocking {
            mockWebServer.enqueue(MockResponse().setBody(CHARACTERS_NULL_RESULTS_JSON).setResponseCode(200))

            repository.characters(page = 1).test {
                awaitItem() // Loading

                val success = awaitItem() as Request.Success
                assertThat(success.data.characters).isEmpty()

                awaitComplete()
            }
        }

    @Test
    fun `character with null origin and location maps to null fields in the domain model`() =
        runBlocking {
            mockWebServer.enqueue(MockResponse().setBody(CHARACTERS_NULL_NESTED_JSON).setResponseCode(200))

            repository.characters(page = 1).test {
                awaitItem() // Loading

                val success = awaitItem() as Request.Success
                // characters() maps to Character (not CharacterDetail), which has no origin/location fields.
                // What we can verify: the character is deserialized without crashing despite null nested objects.
                assertThat(success.data.characters).hasSize(1)
                assertThat(success.data.characters[0].id).isEqualTo(5)

                awaitComplete()
            }
        }

    @Test
    fun `a 404 response emits loading then an error with code 404`() =
        runBlocking {
            mockWebServer.enqueue(MockResponse().setResponseCode(404))

            repository.characters(page = 1).test {
                assertThat(awaitItem()).isInstanceOf(Request.Loading::class.java)

                val error = awaitItem() as Request.Error
                assertThat(error.apiError?.code).isEqualTo("404")
                assertThat(error.apiError?.message).isEqualTo("The requested resource was not found.")

                awaitComplete()
            }
        }

    @Test
    fun `a 500 response emits loading then an error with a server error message`() =
        runBlocking {
            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            repository.characters(page = 1).test {
                awaitItem() // Loading

                val error = awaitItem() as Request.Error
                assertThat(error.apiError?.code).isEqualTo("500")
                assertThat(error.apiError?.message).isEqualTo(
                    "The server is having trouble right now. Please try again later.",
                )

                awaitComplete()
            }
        }

    // ── character(id) ─────────────────────────────────────────────────────────

    @Test
    fun `loading a character detail emits loading then the fully mapped domain model`() =
        runBlocking {
            mockWebServer.enqueue(MockResponse().setBody(CHARACTER_DETAIL_JSON).setResponseCode(200))

            repository.character(id = 1).test {
                assertThat(awaitItem()).isInstanceOf(Request.Loading::class.java)

                val success = awaitItem() as Request.Success
                with(success.data) {
                    assertThat(id).isEqualTo(1)
                    assertThat(name).isEqualTo("Rick Sanchez")
                    assertThat(status).isEqualTo("Alive")
                    assertThat(species).isEqualTo("Human")
                    assertThat(gender).isEqualTo("Male")
                    assertThat(origin).isEqualTo("Earth (C-137)")
                    assertThat(location).isEqualTo("Citadel of Ricks")
                    assertThat(imageUrl).isEqualTo("https://rickandmortyapi.com/api/character/avatar/1.jpeg")
                }

                awaitComplete()
            }
        }

    @Test
    fun `loading a character detail with null origin and location maps both to null`() =
        runBlocking {
            mockWebServer.enqueue(MockResponse().setBody(CHARACTER_NO_ORIGIN_JSON).setResponseCode(200))

            repository.character(id = 99).test {
                awaitItem() // Loading

                val success = awaitItem() as Request.Success
                assertThat(success.data.origin).isNull()
                assertThat(success.data.location).isNull()

                awaitComplete()
            }
        }

    @Test
    fun `a 404 on the detail endpoint emits loading then an error with code 404`() =
        runBlocking {
            mockWebServer.enqueue(MockResponse().setResponseCode(404))

            repository.character(id = 999).test {
                awaitItem() // Loading

                val error = awaitItem() as Request.Error
                assertThat(error.apiError?.code).isEqualTo("404")

                awaitComplete()
            }
        }

    // ── JSON fixtures ─────────────────────────────────────────────────────────

    companion object {
        private val CHARACTERS_PAGE_JSON =
            """
            {
              "info": { "count": 826, "pages": 42, "next": "https://rickandmortyapi.com/api/character?page=2", "prev": null },
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

        private val CHARACTERS_NULL_RESULTS_JSON =
            """
            {
              "info": { "count": 0, "pages": 0, "next": null, "prev": null },
              "results": null
            }
            """.trimIndent()

        private val CHARACTERS_NULL_NESTED_JSON =
            """
            {
              "info": { "count": 1, "pages": 1, "next": null, "prev": null },
              "results": [
                { "id": 5, "name": "Mr. Meeseeks", "status": "Alive", "species": "Meeseeks",
                  "gender": "Male", "image": null, "origin": null, "location": null }
              ]
            }
            """.trimIndent()

        private val CHARACTER_DETAIL_JSON =
            """
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

        private val CHARACTER_NO_ORIGIN_JSON =
            """
            { "id": 99, "name": "Unknown", "origin": null, "location": null }
            """.trimIndent()
    }
}
