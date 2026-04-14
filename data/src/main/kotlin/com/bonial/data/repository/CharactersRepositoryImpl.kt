package com.bonial.data.repository

import com.bonial.data.mapper.toDomainDetail
import com.bonial.data.mapper.toDomainPage
import com.bonial.data.remote.service.CharactersApiService
import com.bonial.domain.model.CharacterDetail
import com.bonial.domain.model.network.response.Request
import com.bonial.domain.repository.CharactersPage
import com.bonial.domain.repository.CharactersRepository
import com.bonial.utils.safeApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CharactersRepositoryImpl @Inject constructor(
    private val apiService: CharactersApiService,
) : CharactersRepository {

    override fun characters(page: Int): Flow<Request<CharactersPage>> =
        safeApiCall { apiService.characters(page) }.map { request ->
            request.mapSuccess { it.toDomainPage() }
        }

    override fun character(id: Int): Flow<Request<CharacterDetail>> =
        safeApiCall { apiService.character(id) }.map { request ->
            request.mapSuccess { it.toDomainDetail() }
        }

    /**
     * Preserve Loading/Error while mapping the Success payload. Keeps call sites
     * readable and avoids repeating the three-branch `when` in every repository method.
     */
    private inline fun <T, R> Request<T>.mapSuccess(transform: (T) -> R): Request<R> = when (this) {
        is Request.Loading -> Request.Loading
        is Request.Error -> Request.Error(apiError)
        is Request.Success -> Request.Success(transform(data))
    }
}
