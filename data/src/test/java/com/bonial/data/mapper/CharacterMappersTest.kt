package com.bonial.data.mapper

import com.bonial.domain.model.network.response.CharacterDto
import com.bonial.domain.model.network.response.CharacterResponseDto
import com.bonial.domain.model.network.response.LocationDto
import com.bonial.domain.model.network.response.OriginDto
import com.bonial.domain.model.network.response.PageInfoDto
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CharacterMappersTest {

    @Test
    fun `toDomain copies id, name, status, species and image`() {
        val dto = CharacterDto(
            id = 42,
            name = "Rick",
            status = "Alive",
            species = "Human",
            image = "https://img/rick.png",
        )

        val character = dto.toDomain()

        assertThat(character.id).isEqualTo(42)
        assertThat(character.name).isEqualTo("Rick")
        assertThat(character.status).isEqualTo("Alive")
        assertThat(character.species).isEqualTo("Human")
        assertThat(character.imageUrl).isEqualTo("https://img/rick.png")
    }

    @Test
    fun `toDomainDetail flattens origin and location to their name fields`() {
        val dto = CharacterDto(
            id = 7,
            name = "Morty",
            status = "Alive",
            species = "Human",
            gender = "Male",
            image = "https://img/morty.png",
            origin = OriginDto(name = "Earth (C-137)"),
            location = LocationDto(name = "Citadel"),
        )

        val detail = dto.toDomainDetail()

        assertThat(detail.origin).isEqualTo("Earth (C-137)")
        assertThat(detail.location).isEqualTo("Citadel")
        assertThat(detail.gender).isEqualTo("Male")
    }

    @Test
    fun `toDomainDetail maps null origin and location to null`() {
        val dto = CharacterDto(id = 1, origin = null, location = null)

        val detail = dto.toDomainDetail()

        assertThat(detail.origin).isNull()
        assertThat(detail.location).isNull()
    }

    @Test
    fun `toDomainPage maps results list and reads totalPages from info`() {
        val response = CharacterResponseDto(
            info = PageInfoDto(pages = 34),
            results = listOf(CharacterDto(id = 1), CharacterDto(id = 2)),
        )

        val page = response.toDomainPage()

        assertThat(page.characters).hasSize(2)
        assertThat(page.characters.map { it.id }).containsExactly(1, 2).inOrder()
        assertThat(page.totalPages).isEqualTo(34)
    }

    @Test
    fun `toDomainPage defaults to empty list and 1 page when info and results are null`() {
        val response = CharacterResponseDto(info = null, results = null)

        val page = response.toDomainPage()

        assertThat(page.characters).isEmpty()
        // Callers rely on totalPages >= 1 so pagination math doesn't underflow.
        assertThat(page.totalPages).isEqualTo(1)
    }
}
