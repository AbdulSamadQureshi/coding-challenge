package com.bonial.domain.repository

import com.bonial.domain.mapper.toDomainDetail
import com.bonial.domain.mapper.toDomainPage
import com.bonial.domain.remote.service.CharactersApiService
import com.bonial.domain.utils.mapSuccess
import com.bonial.domain.utils.safeApiCall
import com.bonial.domain.model.CharacterDetail
import com.bonial.domain.model.network.response.Request
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CharactersRepositoryImpl @Inject constructor(
    private val apiService: CharactersApiService,
) : CharactersRepository {

    override fun characters(page: Int, name: String?): Flow<Request<CharactersPage>> =
        safeApiCall { apiService.characters(page, name) }.map { request ->
            request.mapSuccess { it.toDomainPage() }
        }

    override fun character(id: Int): Flow<Request<CharacterDetail>> =
        safeApiCall { apiService.character(id) }.map { request ->
            request.mapSuccess { it.toDomainDetail() }
        }
}
