package com.bonial.domain.repository

import com.bonial.domain.model.Character
import com.bonial.domain.model.CharacterDetail
import com.bonial.domain.model.network.response.Request
import kotlinx.coroutines.flow.Flow

/**
 * A page of characters with the total number of pages reported by the upstream source.
 */
data class CharactersPage(
    val characters: List<Character>,
    val totalPages: Int,
)

/**
 * Repository returns domain models only. DTO → domain mapping is an implementation
 * detail of the data layer; consumers (use cases, ViewModels) never see transport types.
 */
interface CharactersRepository {
    fun characters(page: Int): Flow<Request<CharactersPage>>
    fun character(id: Int): Flow<Request<CharacterDetail>>
}
