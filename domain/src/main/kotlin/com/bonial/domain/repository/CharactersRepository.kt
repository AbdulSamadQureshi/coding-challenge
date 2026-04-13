package com.bonial.domain.repository

import com.bonial.domain.model.network.response.CharacterDto
import com.bonial.domain.model.network.response.CharacterResponseDto
import com.bonial.domain.model.network.response.Request
import kotlinx.coroutines.flow.Flow

interface CharactersRepository {
    fun characters(page: Int): Flow<Request<CharacterResponseDto>>
    fun character(id: Int): Flow<Request<CharacterDto>>
}
