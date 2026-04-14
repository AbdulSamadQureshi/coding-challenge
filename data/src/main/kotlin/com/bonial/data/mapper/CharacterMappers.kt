package com.bonial.data.mapper

import com.bonial.domain.model.Character
import com.bonial.domain.model.CharacterDetail
import com.bonial.domain.model.network.response.CharacterDto
import com.bonial.domain.model.network.response.CharacterResponseDto
import com.bonial.domain.repository.CharactersPage

/**
 * DTO → domain mappers. Kept in the data layer so domain stays free of transport concerns.
 * File-level functions (not extension methods) to make them trivially unit-testable.
 */

internal fun CharacterDto.toDomain(): Character = Character(
    id = id,
    name = name,
    status = status,
    species = species,
    imageUrl = image,
)

internal fun CharacterDto.toDomainDetail(): CharacterDetail = CharacterDetail(
    id = id,
    name = name,
    status = status,
    species = species,
    gender = gender,
    origin = origin?.name,
    location = location?.name,
    imageUrl = image,
)

internal fun CharacterResponseDto.toDomainPage(): CharactersPage = CharactersPage(
    characters = results.orEmpty().map { it.toDomain() },
    totalPages = info?.pages ?: 1,
)
