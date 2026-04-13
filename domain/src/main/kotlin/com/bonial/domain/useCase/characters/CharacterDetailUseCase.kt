package com.bonial.domain.useCase.characters

import com.bonial.domain.model.CharacterDetail
import com.bonial.domain.model.network.response.Request
import com.bonial.domain.repository.CharactersRepository
import com.bonial.domain.useCase.BaseUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CharacterDetailUseCase @Inject constructor(
    private val repository: CharactersRepository,
) : BaseUseCase<Int, Flow<Request<CharacterDetail>>> {

    override suspend fun invoke(params: Any?): Flow<Request<CharacterDetail>> {
        val id = params as? Int ?: return flowOf(Request.Error(null))
        return repository.character(id).map { response ->
            when (response) {
                is Request.Loading -> Request.Loading
                is Request.Error -> Request.Error(response.apiError)
                is Request.Success -> {
                    val dto = response.data
                    Request.Success(
                        CharacterDetail(
                            id = dto.id,
                            name = dto.name,
                            status = dto.status,
                            species = dto.species,
                            gender = dto.gender,
                            origin = dto.origin?.name,
                            location = dto.location?.name,
                            imageUrl = dto.image,
                        ),
                    )
                }
            }
        }
    }
}
