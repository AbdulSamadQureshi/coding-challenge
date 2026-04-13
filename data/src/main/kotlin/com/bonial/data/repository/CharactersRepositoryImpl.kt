package com.bonial.data.repository

import com.bonial.domain.model.network.response.CharacterDto
import com.bonial.domain.model.network.response.CharacterResponseDto
import com.bonial.domain.model.network.response.Request
import com.bonial.domain.repository.CharactersRepository
import com.bonial.data.remote.service.CharactersApiService
import com.bonial.utils.safeApiCall
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CharactersRepositoryImpl @Inject constructor(
    private val apiService: CharactersApiService,
) : CharactersRepository {

    override fun characters(page: Int): Flow<Request<CharacterResponseDto>> =
        safeApiCall { apiService.characters(page) }

    override fun character(id: Int): Flow<Request<CharacterDto>> =
        safeApiCall { apiService.character(id) }
}
