package com.bonial.domain.useCase.characters

import com.bonial.domain.model.CharacterDetail
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class GetCharacterShareTextUseCaseTest {
    private val useCase = GetCharacterShareTextUseCase()

    private fun detail(
        name: String? = "Rick Sanchez",
        species: String? = "Human",
        status: String? = "Alive",
        imageUrl: String? = "https://rickandmortyapi.com/api/character/avatar/1.jpeg",
    ) = CharacterDetail(
        id = 1,
        name = name,
        status = status,
        species = species,
        gender = "Male",
        origin = "Earth",
        location = "Citadel of Ricks",
        imageUrl = imageUrl,
    )

    @Test
    fun `share text includes name, species, status, and image link when all are available`() =
        runBlocking {
            val result = useCase(detail())
            assertThat(result).isEqualTo(
                "Rick Sanchez · Human · Alive\nhttps://rickandmortyapi.com/api/character/avatar/1.jpeg",
            )
        }

    @Test
    fun `share text skips status when it is missing`() =
        runBlocking {
            val result = useCase(detail(status = null))
            assertThat(result).isEqualTo(
                "Rick Sanchez · Human\nhttps://rickandmortyapi.com/api/character/avatar/1.jpeg",
            )
        }

    @Test
    fun `share text skips species when it is missing`() =
        runBlocking {
            val result = useCase(detail(species = null))
            assertThat(result).isEqualTo(
                "Rick Sanchez · Alive\nhttps://rickandmortyapi.com/api/character/avatar/1.jpeg",
            )
        }

    @Test
    fun `share text has no image link when image url is missing`() =
        runBlocking {
            val result = useCase(detail(imageUrl = null))
            assertThat(result).isEqualTo("Rick Sanchez · Human · Alive")
            assertThat(result).doesNotContain("\n")
        }

    @Test
    fun `share text starts from species when name is missing`() =
        runBlocking {
            val result = useCase(detail(name = null))
            // name is null → appends "" then " · Human · Alive"
            assertThat(result).startsWith(" · Human · Alive")
        }

    @Test
    fun `share text is empty when all character details are missing`() =
        runBlocking {
            val result = useCase(detail(name = null, species = null, status = null, imageUrl = null))
            assertThat(result).isEmpty()
        }
}
